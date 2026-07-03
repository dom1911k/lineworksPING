package com.dominic.lineworksping

import android.media.RingtoneManager
import android.net.Uri

/** Resolves which sound the important-ping notification channel should use. */
object PingPlayer {

    /** The user's chosen sound, or the system default notification sound. */
    fun resolveUri(settings: SettingsStore): Uri {
        val saved = settings.soundUri
        return if (!saved.isNullOrEmpty()) {
            Uri.parse(saved)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }
}
