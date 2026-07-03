package com.dominic.lineworksping

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Listens to notifications posted by other apps. For each notification from a
 * monitored app, it decides whether the message is important and, if so, posts
 * a heads-up alert. Requires the user to grant "Notification access" in Settings.
 */
class PingNotificationListenerService : NotificationListenerService() {

    private lateinit var settings: SettingsStore
    private lateinit var classifier: NotificationClassifier

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        classifier = NotificationClassifier(settings)
        Notifier.ensureChannel(this, settings)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        logNote(getString(R.string.log_listener_connected))
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        logNote(getString(R.string.log_listener_disconnected))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        try {
            if (!settings.enabled) return

            val pkg = sbn.packageName ?: return
            if (pkg == packageName) return

            val monitored = pkg in settings.monitoredPackages
            if (!monitored && !settings.logAllApps) return

            val isSummary = sbn.notification != null &&
                sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

            val reason = if (monitored && !isSummary) {
                classifier.classify(sbn)
            } else {
                PingReason.NONE
            }

            val (title, text) = extractText(sbn)
            var pinged = false
            if (monitored && !isSummary && reason != PingReason.NONE) {
                pinged = Notifier.notifyImportant(this, settings, title, text)
                Log.d(TAG, "Important notification ($reason) from $pkg, shown=$pinged")
            }

            val decision = when {
                !monitored -> getString(R.string.decision_not_monitored)
                isSummary -> getString(R.string.decision_group_summary)
                reason == PingReason.DIRECT_MESSAGE -> getString(R.string.decision_ping_dm)
                reason == PingReason.MENTION -> getString(R.string.decision_ping_mention)
                else -> getString(R.string.decision_normal)
            }
            EventLog.add(
                EventLog.Entry(
                    time = System.currentTimeMillis(),
                    pkg = pkg,
                    label = appLabel(pkg),
                    text = buildString {
                        if (title.isNotBlank()) append(title)
                        if (text.isNotBlank()) {
                            if (isNotEmpty()) append(" — ")
                            append(text)
                        }
                    }.take(140),
                    decision = decision,
                    pinged = pinged
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process notification", e)
        }
    }

    private fun extractText(sbn: StatusBarNotification): Pair<String, String> {
        val extras = sbn.notification?.extras ?: return "" to ""
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        return title to text
    }

    private fun appLabel(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun logNote(note: String) {
        EventLog.add(
            EventLog.Entry(
                time = System.currentTimeMillis(),
                pkg = "",
                label = "",
                text = note,
                decision = "",
                pinged = false
            )
        )
    }

    companion object {
        private const val TAG = "LineWorksPing"
    }
}
