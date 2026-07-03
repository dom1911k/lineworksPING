package com.dominic.lineworksping

import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.appcompat.app.AppCompatActivity
import com.dominic.lineworksping.databinding.ActivityAlertBinding

/**
 * A deliberately loud, full-screen alert: a color-cycling background, pulsing
 * text, and a strong vibration. Shown over the lock screen via a notification's
 * full-screen intent so an important message is impossible to miss.
 */
class AlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertBinding
    private var colorAnimator: ValueAnimator? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        binding.alertTitle.text = title.ifBlank { getString(R.string.app_name) }
        binding.alertText.text = text
        binding.alertText.visibility = if (text.isBlank()) View.GONE else View.VISIBLE

        binding.btnDismiss.setOnClickListener { finish() }

        startColorCycle()
        pulse(binding.alertTitle)
        startVibration()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        binding.alertTitle.text = intent.getStringExtra(EXTRA_TITLE)?.ifBlank { getString(R.string.app_name) }
            ?: getString(R.string.app_name)
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        binding.alertText.text = text
        binding.alertText.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startColorCycle() {
        colorAnimator = ValueAnimator.ofArgb(
            0xFFFF1744.toInt(), // red
            0xFFFF9100.toInt(), // orange
            0xFFFFEA00.toInt(), // yellow
            0xFF00E676.toInt(), // green
            0xFF00B0FF.toInt(), // blue
            0xFFD500F9.toInt(), // magenta
            0xFFFF1744.toInt()  // back to red for a seamless loop
        ).apply {
            duration = 2500
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { binding.root.setBackgroundColor(it.animatedValue as Int) }
            start()
        }
    }

    private fun pulse(view: View) {
        val scale = ScaleAnimation(
            1f, 1.12f, 1f, 1.12f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 450
            repeatCount = Animation.INFINITE
            repeatMode = Animation.REVERSE
        }
        view.startAnimation(scale)
    }

    private fun startVibration() {
        vibrator = getSystemService(Vibrator::class.java) ?: return
        val pattern = longArrayOf(0, 400, 250, 400, 250, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        colorAnimator?.cancel()
        vibrator?.cancel()
        binding.alertTitle.clearAnimation()
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
    }
}
