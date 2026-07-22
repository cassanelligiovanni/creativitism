package com.creativitism.appredirector

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.creativitism.appredirector.databinding.ActivityBlockedInterstitialBinding
import kotlin.math.ceil

/**
 * Full-screen pause shown when the user opens a blocked app. A 10-second countdown
 * must elapse before "Use it for 5 minutes" unlocks; the primary action redirects
 * to a creativity-boosting item instead.
 */
class BlockedInterstitialActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "BLOCKED_PACKAGE"
        private const val TICK_MILLIS = 100L

        /**
         * True while an interstitial is in the foreground. Usage stats keep reporting
         * the blocked app for a few seconds after we cover it, so the service uses
         * this to avoid relaunching us (which would restart the countdown).
         */
        @Volatile
        var isShowing: Boolean = false
            private set
    }

    private lateinit var binding: ActivityBlockedInterstitialBinding
    private lateinit var allowanceManager: TemporaryAllowanceManager
    private lateinit var redirectionManager: RedirectionManager
    private var countDownTimer: CountDownTimer? = null
    private var blockedPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedInterstitialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        allowanceManager = TemporaryAllowanceManager(this)
        redirectionManager = RedirectionManager(this)

        binding.redirectButton.setOnClickListener { redirectSomewhereBetter() }
        binding.allowButton.setOnClickListener { grantAllowanceAndReturn() }

        // Back should not drop the user into the blocked app: send them home instead.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
                finish()
            }
        })

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
        if (pkg == null) {
            finish()
            return
        }
        blockedPackage = pkg
        showBlockedApp(pkg)
        startCountdown()
    }

    private fun showBlockedApp(pkg: String) {
        try {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            binding.blockedSubtitle.text =
                getString(R.string.interstitial_subtitle, packageManager.getApplicationLabel(appInfo))
        } catch (e: PackageManager.NameNotFoundException) {
            binding.blockedSubtitle.text = getString(R.string.interstitial_subtitle_generic)
        }
    }

    private fun startCountdown() {
        countDownTimer?.cancel()
        binding.allowButton.isEnabled = false
        binding.countdownRing.max = AllowancePolicy.WAIT_MILLIS.toInt()
        countDownTimer = object : CountDownTimer(AllowancePolicy.WAIT_MILLIS, TICK_MILLIS) {
            override fun onTick(millisUntilFinished: Long) {
                binding.countdownRing.progress = (AllowancePolicy.WAIT_MILLIS - millisUntilFinished).toInt()
                binding.countdownText.text =
                    ceil(millisUntilFinished / 1000.0).toInt().toString()
            }

            override fun onFinish() {
                binding.countdownRing.progress = AllowancePolicy.WAIT_MILLIS.toInt()
                binding.countdownText.text = getString(R.string.tilde)
                binding.allowButton.isEnabled = true
            }
        }.start()
    }

    private fun grantAllowanceAndReturn() {
        val pkg = blockedPackage ?: return
        allowanceManager.grant(pkg)
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
        finish()
    }

    private fun redirectSomewhereBetter() {
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
        super.onPause()
        isShowing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
