package com.creativitism.appredirector

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.animation.LinearInterpolator
import androidx.activity.OnBackPressedCallback
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
    private lateinit var audio: AlarmAudioController

    private var countDownTimer: CountDownTimer? = null
    private var pulseAnimator: ValueAnimator? = null
    private var blockedItem: String? = null
    private var displayName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        current = this

        allowanceManager = TemporaryAllowanceManager(this)
        redirectionManager = RedirectionManager(this)
        policyStore = BlockedItemPolicyStore(this)
        interventionLog = InterventionLog(this)
        audio = AlarmAudioController(this)

        startPulse()

        binding.alarmEscape.setOnClickListener { escape() }
        binding.alarmGiveIn.setOnClickListener { giveIn() }

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
        binding.alarmGiveIn.text = if (policy.confessionEnabled) {
            // Task 15 wires the actual confession flow; for now this is a disabled placeholder.
            getString(R.string.alarm_givein_confession_placeholder)
        } else {
            getString(R.string.alarm_givein_strict)
        }
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
                if (!policyStore.get(item).confessionEnabled) {
                    binding.alarmGiveIn.isEnabled = true
                }
            }
        }.start()
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
        val target = redirectionManager.getRandomCreativityBoostingItem()
        if (target != null) {
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
        audio.stop()
        allowanceManager.grantStrict(item)
        val now = System.currentTimeMillis()
        interventionLog.append(InterventionEvent.strictGiveIn(now, item, displayName))
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
        audio.stop()
        isShowing = false
        super.onPause()
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
        countDownTimer?.cancel()
        pulseAnimator?.cancel()
        audio.stop()
    }
}
