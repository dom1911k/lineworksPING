package com.dominic.lineworksping

import android.app.Notification
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.time.LocalDate

/**
 * Listens to notifications posted by other apps. For each notification from a
 * monitored app, it decides whether the message is important and, if so, posts
 * a heads-up alert. Requires the user to grant "Notification access" in Settings.
 */
class PingNotificationListenerService : NotificationListenerService() {

    private lateinit var settings: SettingsStore
    private lateinit var classifier: NotificationClassifier
    private var tts: TtsSpeaker? = null

    /** Recent sender+text signatures -> time, to drop duplicate re-posts. */
    private val recentPings = HashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        classifier = NotificationClassifier(settings)
        tts = TtsSpeaker(this)
        Notifier.ensureChannel(this, settings)
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown()
        tts = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        settings.listenerConnected = true
        logNote(getString(R.string.log_listener_connected))
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        settings.listenerConnected = false
        logNote(getString(R.string.log_listener_disconnected))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        try {
            // Liveness marker (updated even when disabled) for the health indicator.
            settings.lastEventTime = System.currentTimeMillis()
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

            val important = monitored && !isSummary && reason != PingReason.NONE
            // Apps often re-post/update the same notification, firing this twice.
            // Ignore a repeat of the same sender+text within a short window.
            val duplicate = important && isDuplicate(pkg, title, text)

            // Count each important message once (skip duplicate re-posts).
            if (important && !duplicate) {
                settings.recordImportant(reason == PingReason.MENTION, LocalDate.now().toEpochDay())
            }

            // Auto-silence conditions.
            val suppressedByCharging = settings.silenceWhileCharging && isPluggedIn()
            val suppressedByWifi = settings.silenceOnOfficeWifi && onOfficeWifi()
            val suppressed = suppressedByCharging || suppressedByWifi

            var pinged = false
            if (important && !duplicate && !suppressed) {
                pinged = Notifier.notifyImportant(
                    this, settings, title, text, pkg, sbn.notification?.contentIntent
                )
                Log.d(TAG, "Important notification ($reason) from $pkg, shown=$pinged")
                // The notification's full-screen intent only fires when the screen is
                // off/locked. With "Display over other apps" granted we can launch the
                // takeover directly, so it shows even while the phone is in use.
                if (settings.fullScreenAlert && Settings.canDrawOverlays(this)) {
                    launchFullScreenAlert(title, text)
                }
                if (settings.readAloud) {
                    tts?.speak(buildSpokenText(reason, title, text))
                }
            }

            val decision = when {
                !monitored -> getString(R.string.decision_not_monitored)
                isSummary -> getString(R.string.decision_group_summary)
                reason == PingReason.NONE -> getString(R.string.decision_normal)
                duplicate -> getString(R.string.decision_duplicate)
                suppressedByCharging -> getString(R.string.decision_charging)
                suppressedByWifi -> getString(R.string.decision_wifi)
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
                    pinged = pinged,
                    detail = buildDetail(sbn, decision)
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

    /** A raw dump of the notification's key fields, used to diagnose DM vs group. */
    private fun buildDetail(sbn: StatusBarNotification, decision: String): String {
        val e = sbn.notification?.extras ?: return ""
        val hasGroupFlag = e.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)
        val isGroup = e.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        return buildString {
            appendLine("package: ${sbn.packageName}")
            appendLine("decision: $decision")
            appendLine("category: ${sbn.notification?.category}")
            appendLine("template: ${e.getString(Notification.EXTRA_TEMPLATE)}")
            appendLine("isGroupConversation: ${if (hasGroupFlag) isGroup.toString() else "(not set)"}")
            appendLine("hasMessagingStyle: ${e.containsKey(Notification.EXTRA_MESSAGES)}")
            appendLine("title: ${e.getCharSequence(Notification.EXTRA_TITLE)}")
            appendLine("text: ${e.getCharSequence(Notification.EXTRA_TEXT)}")
            appendLine("bigText: ${e.getCharSequence(Notification.EXTRA_BIG_TEXT)}")
            appendLine("subText: ${e.getCharSequence(Notification.EXTRA_SUB_TEXT)}")
            appendLine("conversationTitle: ${e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)}")
        }.trimEnd()
    }

    /** True if this exact sender+text was already pinged within the dedupe window. */
    private fun isDuplicate(pkg: String, title: String, text: String): Boolean {
        val signature = "$pkg$title$text"
        val now = System.currentTimeMillis()
        val previous = recentPings[signature]
        val duplicate = previous != null && (now - previous) < DEDUPE_WINDOW_MS
        if (!duplicate) recentPings[signature] = now
        if (recentPings.size > 200) {
            recentPings.entries.removeAll { now - it.value > 60_000L }
        }
        return duplicate
    }

    /** True if connected to a configured office Wi-Fi (fails open if SSID unknown). */
    private fun onOfficeWifi(): Boolean {
        val ssid = WifiInfoHelper.currentSsid(this) ?: return false
        return settings.officeSsids.any { it.equals(ssid, ignoreCase = true) }
    }

    /** Builds a natural spoken sentence for text-to-speech. */
    private fun buildSpokenText(reason: PingReason, title: String, text: String): String {
        val idx = text.indexOfFirst { it == ':' || it == '：' }
        val sender = if (idx > 0) text.substring(0, idx).trim() else title
        val body = if (idx > 0) text.substring(idx + 1).trim() else text
        val lead = if (reason == PingReason.MENTION) {
            getString(R.string.tts_mention, sender)
        } else {
            getString(R.string.tts_dm, sender)
        }
        return "$lead. $body"
    }

    /** True if the phone is currently plugged into any charger (AC/USB/wireless). */
    private fun isPluggedIn(): Boolean {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged != 0
    }

    private fun launchFullScreenAlert(title: String, text: String) {
        try {
            startActivity(
                Intent(this, AlertActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(AlertActivity.EXTRA_TITLE, title)
                    .putExtra(AlertActivity.EXTRA_TEXT, text)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch full-screen alert", e)
        }
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
        private const val DEDUPE_WINDOW_MS = 8_000L
    }
}
