package com.dominic.lineworksping

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri

/** Plays the single "important" alert sound, with a short cooldown to avoid spam. */
object PingPlayer {

    private const val COOLDOWN_MS = 1500L

    @Volatile
    private var lastPlayed = 0L

    /** The user's chosen sound, or the system default notification sound. */
    fun resolveUri(settings: SettingsStore): Uri {
        val saved = settings.soundUri
        return if (!saved.isNullOrEmpty()) {
            Uri.parse(saved)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    fun play(context: Context, uri: Uri, respectCooldown: Boolean = true) {
        val now = System.currentTimeMillis()
        if (respectCooldown && now - lastPlayed < COOLDOWN_MS) return
        lastPlayed = now
        try {
            val ringtone: Ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                ?: return
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.play()
        } catch (e: Exception) {
            // A bad/old sound URI shouldn't crash the listener; just stay quiet.
        }
    }
}
