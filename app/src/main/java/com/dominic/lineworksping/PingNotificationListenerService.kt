package com.dominic.lineworksping

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Listens to notifications posted by other apps. For each notification from a
 * monitored app, it decides whether the message is important and, if so, plays
 * the alert sound. Requires the user to grant "Notification access" in Settings.
 */
class PingNotificationListenerService : NotificationListenerService() {

    private lateinit var settings: SettingsStore
    private lateinit var classifier: NotificationClassifier

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        classifier = NotificationClassifier(settings)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        try {
            if (!settings.enabled) return

            val pkg = sbn.packageName ?: return
            if (pkg == packageName) return
            if (pkg !in settings.monitoredPackages) return

            // Skip the silent "group summary" wrapper notifications.
            if (sbn.notification != null &&
                sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
            ) return

            val reason = classifier.classify(sbn)
            if (reason != PingReason.NONE) {
                Log.d(TAG, "Important notification ($reason) from $pkg")
                PingPlayer.play(this, PingPlayer.resolveUri(settings))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process notification", e)
        }
    }

    companion object {
        private const val TAG = "LineWorksPing"
    }
}
