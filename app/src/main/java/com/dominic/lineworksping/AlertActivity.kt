package com.dominic.lineworksping

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import androidx.appcompat.app.AppCompatActivity
import com.dominic.lineworksping.databinding.ActivityAlertBinding

/**
 * A deliberately loud, full-screen alert whose look is driven by the user's
 * settings: colour style, cycling speed, text size, vibration, and pulsing.
 * Shown over the lock screen via a notification's full-screen intent.
 */
class AlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertBinding
    private lateinit var settings: SettingsStore
    private var colorAnimator: ValueAnimator? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        showOverLockScreen()
        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindContent(intent)
        applyTextSize()
        binding.btnDismiss.setOnClickListener { finish() }

        startColorCycle()
        if (settings.alertPulse) pulse(binding.alertTitle)
        if (settings.alertVibrate) startVibration()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bindContent(intent)
    }

    private fun bindContent(intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        binding.alertTitle.text = title.ifBlank { getString(R.string.app_name) }
        binding.alertText.text = text
        binding.alertText.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun applyTextSize() {
        val (titleSp, bodySp) = when (settings.alertTextSize) {
            1 -> 44f to 27f
            2 -> 54f to 33f
            else -> 34f to 22f
        }
        binding.alertTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSp)
        binding.alertText.setTextSize(TypedValue.COMPLEX_UNIT_SP, bodySp)
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
        val colors: IntArray? = when (settings.alertColor) {
            1 -> intArrayOf( // warm
                0xFFFF1744.toInt(), 0xFFFF6D00.toInt(), 0xFFFFEA00.toInt(),
                0xFFFF3D00.toInt(), 0xFFFF1744.toInt()
            )
            2 -> intArrayOf( // cool
                0xFF2979FF.toInt(), 0xFF00E5FF.toInt(), 0xFF00E676.toInt(),
                0xFFD500F9.toInt(), 0xFF2979FF.toInt()
            )
            3 -> null // solid red, no cycling
            else -> intArrayOf( // rainbow
                0xFFFF1744.toInt(), 0xFFFF9100.toInt(), 0xFFFFEA00.toInt(),
                0xFF00E676.toInt(), 0xFF00B0FF.toInt(), 0xFFD500F9.toInt(),
                0xFFFF1744.toInt()
            )
        }

        if (colors == null) {
            binding.root.setBackgroundColor(0xFFFF1744.toInt())
            return
        }

        val duration = when (settings.alertSpeed) {
            0 -> 4200L
            2 -> 1100L
            else -> 2500L
        }
        colorAnimator = ValueAnimator.ofArgb(*colors).apply {
            this.duration = duration
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
