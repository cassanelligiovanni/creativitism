package com.creativitism.appredirector

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.creativitism.appredirector.databinding.ActivityAlarmBinding
import kotlin.math.ceil

/**
 * Full-screen fire alarm shown when the user opens a blocked app. Loud, opaque,
 * and impossible to ignore: a klaxon plays, the screen pulses red, and only
 * "Escape" (redirect to something creative) or waiting out a give-in path gets
 * the user off this screen. Leaving without a grant (back/home) buys nothing —
 * the alarm silences but the app stays blocked.
 */
class AlarmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BLOCKED_ITEM = "BLOCKED_ITEM"
        const val EXTRA_DISPLAY_NAME = "DISPLAY_NAME"
        private const val TICK_MILLIS = 100L

        // One full dark<->red cycle; well under the 2.5 Hz (400ms) flash-safety cap.
        private const val PULSE_HALF_CYCLE_MILLIS = 450L

        /**
         * True while the alarm is in the foreground. The monitoring service uses this
         * to avoid relaunching us (which would restart the countdown) and to know an
         * alarm is currently live.
         */
        @Volatile
        var isShowing: Boolean = false
            private set

        @Volatile
        private var current: AlarmActivity? = null

        /**
         * Mid-alarm dormancy: finish without granting and stop audio.
         * Must be invoked on the main thread.
         */
        internal fun dismissIfShowingForDormant() {
            current?.dismissForDormant()
        }
    }

    private lateinit var binding: ActivityAlarmBinding
    private lateinit var allowanceManager: TemporaryAllowanceManager
    private lateinit var redirectionManager: RedirectionManager
    private lateinit var policyStore: BlockedItemPolicyStore
    private lateinit var interventionLog: InterventionLog
    private lateinit var visitTracker: VisitTracker
    private lateinit var settingsStore: AppSettingsStore
    private lateinit var audio: AlarmAudioController

    private var countDownTimer: CountDownTimer? = null
    private var pulseAnimator: ValueAnimator? = null
    private var blockedItem: String? = null
    private var displayName: String = ""

    private var expectedConfession: String? = null
    private var confessionMatched: Boolean = false
    private var micDisclosureShown: Boolean = false
    private var speechHelper: ConfessionSpeechHelper? = null

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            // Permission sheet already stopped audio via onPause; keep silent for listen.
            startSpeechListening()
        } else {
            fallBackToTypedOnly()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        current = this

        allowanceManager = TemporaryAllowanceManager(this)
        redirectionManager = RedirectionManager(this)
        policyStore = BlockedItemPolicyStore(this)
        interventionLog = InterventionLog(this)
        visitTracker = VisitTracker(this)
        settingsStore = AppSettingsStore(this)
        audio = AlarmAudioController(this)

        startPulse()

        binding.alarmEscape.setOnClickListener { escape() }
        binding.alarmGiveIn.setOnClickListener { giveIn() }
        binding.alarmConfessionMic.setOnClickListener { onMicClicked() }
        binding.alarmConfessionInput.addTextChangedListener(confessionTextWatcher)
        binding.alarmConfessionInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                evaluateConfession(binding.alarmConfessionInput.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }

        // Back must not drop the user into the blocked app: send them home instead.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
                finish()
            }
        })

        handleIntent(intent)
    }

    /** Schedule flipped dormant while alarm was showing — leave without a grant. */
    private fun dismissForDormant() {
        countDownTimer?.cancel()
        pulseAnimator?.cancel()
        speechHelper?.stopListening()
        audio.stop()
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val item = intent.getStringExtra(EXTRA_BLOCKED_ITEM)
        if (item == null) {
            finish()
            return
        }
        blockedItem = item
        displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: item
        binding.alarmSubtitle.text = getString(R.string.alarm_subtitle, displayName)

        resetConfessionUi()

        interventionLog.append(
            InterventionEvent(
                timestamp = System.currentTimeMillis(),
                itemId = item,
                displayLabel = displayName,
                kind = InterventionKind.ALARM_FIRED,
            ),
        )

        setUpGiveIn(item)
        startCountdown(item)
    }

    private fun setUpGiveIn(item: String) {
        val policy = policyStore.get(item)
        binding.alarmGiveIn.isEnabled = false
        binding.alarmGiveIn.text = giveInLabel(policy.enforcementMode)
    }

    private fun giveInLabel(mode: EnforcementMode): String = when (mode) {
        EnforcementMode.STRICT -> getString(R.string.alarm_givein_strict)
        EnforcementMode.ONCE_PER_VISIT -> getString(R.string.alarm_givein_visit)
    }

    private fun startCountdown(item: String) {
        countDownTimer?.cancel()
        audio.start(AllowancePolicy.WAIT_MILLIS)
        countDownTimer = object : CountDownTimer(AllowancePolicy.WAIT_MILLIS, TICK_MILLIS) {
            override fun onTick(millisUntilFinished: Long) {
                binding.alarmCountdown.text = ceil(millisUntilFinished / 1000.0).toInt().toString()
            }

            override fun onFinish() {
                binding.alarmCountdown.text = "0"
                val policy = policyStore.get(item)
                if (policy.confessionEnabled) {
                    showConfessionUi()
                } else {
                    binding.alarmGiveIn.isEnabled = true
                }
            }
        }.start()
    }

    private fun resetConfessionUi() {
        speechHelper?.destroy()
        speechHelper = null
        expectedConfession = null
        confessionMatched = false
        micDisclosureShown = false
        binding.alarmConfessionPanel.visibility = View.GONE
        binding.alarmCountdown.visibility = View.VISIBLE
        binding.alarmConfessionInput.setText("")
        binding.alarmConfessionStatus.text = ""
        binding.alarmConfessionMic.visibility = View.VISIBLE
        binding.alarmConfessionMic.isEnabled = true
    }

    private fun showConfessionUi() {
        binding.alarmCountdown.visibility = View.GONE
        binding.alarmGiveIn.isEnabled = false

        val creativeIds = redirectionManager.getCreativityBoostingItems().toList()
        val creativeDisplay = ConfessionSentence.pickCreativeDisplay(
            settingsStore.lastCreativeItem,
            creativeIds,
        ) { id -> resolveItemDisplayName(id) }
        val sentence = ConfessionSentence.build(displayName, creativeDisplay)
        expectedConfession = sentence

        binding.alarmConfessionSentence.text = sentence
        binding.alarmConfessionStatus.text = ""
        binding.alarmConfessionPanel.visibility = View.VISIBLE

        ensureSpeechHelper()
        updateMicVisibility()
    }

    private fun resolveItemDisplayName(id: String): String {
        return try {
            val info = packageManager.getApplicationInfo(id, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            id
        }
    }

    private fun ensureSpeechHelper() {
        if (speechHelper != null) return
        speechHelper = ConfessionSpeechHelper(
            this,
            object : ConfessionSpeechHelper.Callback {
                override fun onSpeechResult(text: String) {
                    evaluateConfession(text, fromSpeech = true)
                }

                override fun onSpeechError(forceTypedFallback: Boolean) {
                    if (forceTypedFallback) {
                        fallBackToTypedOnly()
                    } else if (!confessionMatched) {
                        binding.alarmConfessionStatus.text = getString(R.string.confession_retry)
                    }
                }

                override fun onListeningChanged(listening: Boolean) {
                    binding.alarmConfessionMic.isEnabled =
                        !listening && speechHelper?.isSpeechUsable == true
                    binding.alarmConfessionMic.alpha = if (listening) 0.5f else 1f
                }
            },
        )
    }

    private fun updateMicVisibility() {
        val usable = speechHelper?.isSpeechUsable == true
        binding.alarmConfessionMic.visibility = if (usable) View.VISIBLE else View.GONE
    }

    private fun fallBackToTypedOnly() {
        binding.alarmConfessionMic.visibility = View.GONE
        binding.alarmConfessionMic.isEnabled = false
        if (!confessionMatched) {
            binding.alarmConfessionStatus.text = ""
        }
    }

    private fun onMicClicked() {
        if (confessionMatched) return
        ensureSpeechHelper()
        if (speechHelper?.isSpeechUsable != true) {
            fallBackToTypedOnly()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startSpeechListening()
            return
        }
        // Silence before disclosure/permission so speak paths match (permission sheet
        // also stops via onPause; already-granted never pauses and must stop here).
        silenceAlarmForSpeech()
        if (!micDisclosureShown) {
            showMicDisclosure()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /**
     * Stop klaxon + vibe so SpeechRecognizer does not hear the alarm.
     * Do not restart after listen — silence until escape/give-in is OK.
     */
    private fun silenceAlarmForSpeech() {
        audio.stop()
    }

    private fun startSpeechListening() {
        silenceAlarmForSpeech()
        speechHelper?.startListening()
    }

    private fun showMicDisclosure() {
        micDisclosureShown = true
        AlertDialog.Builder(this)
            .setTitle(R.string.confession_mic_disclosure_title)
            .setMessage(R.string.confession_mic_disclosure)
            .setPositiveButton(R.string.confession_mic_disclosure_continue) { _, _ ->
                silenceAlarmForSpeech()
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            .setNegativeButton(R.string.confession_mic_use_typed) { _, _ ->
                fallBackToTypedOnly()
            }
            .setCancelable(true)
            .setOnCancelListener { fallBackToTypedOnly() }
            .show()
    }

    private val confessionTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            evaluateConfession(s?.toString().orEmpty())
        }
    }

    private fun evaluateConfession(candidate: String, fromSpeech: Boolean = false) {
        val expected = expectedConfession ?: return
        if (candidate.isBlank()) {
            if (confessionMatched) {
                confessionMatched = false
                binding.alarmGiveIn.isEnabled = false
                binding.alarmConfessionStatus.text = ""
            }
            return
        }
        if (ConfessionNormalizer.matches(candidate, expected)) {
            confessionMatched = true
            binding.alarmGiveIn.isEnabled = true
            binding.alarmConfessionStatus.text = getString(R.string.confession_matched)
            speechHelper?.stopListening()
        } else {
            if (confessionMatched) {
                confessionMatched = false
                binding.alarmGiveIn.isEnabled = false
            }
            // Speech mismatches always retry; typed shows retry once the attempt is substantial.
            if (fromSpeech || candidate.length >= expected.length / 2) {
                binding.alarmConfessionStatus.text = getString(R.string.confession_retry)
            } else {
                binding.alarmConfessionStatus.text = ""
            }
        }
    }

    private fun startPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofObject(
            ArgbEvaluator(),
            ContextCompat.getColor(this, R.color.cv_alarm_bg),
            ContextCompat.getColor(this, R.color.cv_alarm_red),
        ).apply {
            duration = PULSE_HALF_CYCLE_MILLIS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { binding.root.setBackgroundColor(it.animatedValue as Int) }
        }
        pulseAnimator?.start()
    }

    private fun escape() {
        audio.stop()
        speechHelper?.stopListening()
        val item = blockedItem
        if (item != null) {
            interventionLog.append(
                InterventionEvent(
                    timestamp = System.currentTimeMillis(),
                    itemId = item,
                    displayLabel = displayName,
                    kind = InterventionKind.ESCAPED,
                ),
            )
        }
        val target = escapeTarget(settingsStore, redirectionManager)
        if (target != null) {
            settingsStore.lastCreativeItem = target
            val proxyIntent = Intent(this, RedirectProxyActivity::class.java).apply {
                putExtra("TARGET_ITEM", target)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(proxyIntent)
        } else {
            goHome()
        }
        finish()
    }

    private fun giveIn() {
        val item = blockedItem ?: return
        val policy = policyStore.get(item)
        if (policy.confessionEnabled && !confessionMatched) return
        audio.stop()
        speechHelper?.stopListening()
        val now = System.currentTimeMillis()
        when (policy.enforcementMode) {
            EnforcementMode.STRICT -> {
                allowanceManager.grantStrict(item, now)
                interventionLog.append(InterventionEvent.strictGiveIn(now, item, displayName))
            }
            EnforcementMode.ONCE_PER_VISIT -> {
                allowanceManager.grantVisit(item)
                visitTracker.touch(item, now)
                visitTracker.markVisitGrantStarted(item, now)
                interventionLog.append(InterventionEvent.visitDeferredGiveIn(now, item, displayName))
            }
        }
        interventionLog.append(
            InterventionEvent(
                timestamp = now,
                itemId = item,
                displayLabel = displayName,
                kind = InterventionKind.ALLOWANCE_GRANTED,
            ),
        )
        if (itemLooksLikeDomain(item)) {
            // Prefer finish() so Chrome underneath resurfaces; relaunch if needed.
            packageManager.getLaunchIntentForPackage("com.android.chrome")?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(it)
            }
        } else {
            packageManager.getLaunchIntentForPackage(item)?.let {
                startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        finish()
    }

    /**
     * Website entries are host-like ids (e.g. "instagram.com") and do not resolve to
     * an installed-package launcher; app entries do.
     */
    private fun itemLooksLikeDomain(item: String): Boolean =
        item.contains('.') && packageManager.getLaunchIntentForPackage(item) == null

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(homeIntent)
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
    }

    override fun onPause() {
        // Stop audio before flipping isShowing so a rapid re-launch (service polling
        // sees us no longer showing) can never overlap two klaxon/vibration players.
        speechHelper?.stopListening()
        audio.stop()
        isShowing = false
        super.onPause()
    }

    override fun onDestroy() {
        if (current === this) current = null
        speechHelper?.destroy()
        speechHelper = null
        super.onDestroy()
        countDownTimer?.cancel()
        pulseAnimator?.cancel()
        audio.stop()
    }
}
