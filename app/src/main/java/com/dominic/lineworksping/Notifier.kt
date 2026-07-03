package com.dominic.lineworksping

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts the "important" alert as a real, high-importance notification so it
 * shows an on-screen heads-up pop-up, plays the chosen sound, vibrates, and can
 * bypass Do Not Disturb.
 *
 * A notification channel's sound / DND behaviour is fixed at creation time and
 * cannot be edited afterwards, so the channel id is derived from the current
 * (sound, bypassDnd) settings. Changing either creates a fresh channel and the
 * stale ones are removed.
 */
object Notifier {

    private const val CHANNEL_PREFIX = "ping_v2_"
    private val idGenerator = AtomicInteger(2000)
    private val vibration = longArrayOf(0, 250, 150, 250)

    /** Creates (if needed) and returns the id of the channel matching current settings. */
    fun ensureChannel(context: Context, settings: SettingsStore): String {
        val nm = context.getSystemService(NotificationManager::class.java)
        val soundUri = PingPlayer.resolveUri(settings)
        // A channel's DND-bypass is fixed at creation and only "sticks" if DND access
        // is already granted, so the granted state is part of the channel identity:
        // flipping access on later yields a new channel that can actually bypass.
        val bypass = settings.bypassDnd && nm.isNotificationPolicyAccessGranted
        val channelId = CHANNEL_PREFIX + Integer.toHexString(("$soundUri|$bypass").hashCode())

        if (nm.getNotificationChannel(channelId) == null) {
            // Drop any earlier channels of ours so the system settings list stays clean.
            nm.notificationChannels
                .filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != channelId }
                .forEach { nm.deleteNotificationChannel(it.id) }

            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_desc)
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(soundUri, attrs)
                enableVibration(true)
                vibrationPattern = vibration
                enableLights(true)
                // Only honoured if the app has been granted Do Not Disturb access.
                setBypassDnd(bypass)
            }
            nm.createNotificationChannel(channel)
        }
        return channelId
    }

    /** Posts an important-message heads-up notification. Returns true if it was shown. */
    fun notifyImportant(context: Context, settings: SettingsStore, title: String, text: String): Boolean {
        val channelId = ensureChannel(context, settings)

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_ping)
            .setContentTitle(title.ifBlank { context.getString(R.string.app_name) })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Pre-Android-8 devices ignore channels; set these directly so they still pop.
            .setSound(PingPlayer.resolveUri(settings))
            .setVibrate(vibration)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)

        return try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            NotificationManagerCompat.from(context).notify(idGenerator.incrementAndGet(), builder.build())
            true
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted (Android 13+); nothing we can do here.
            false
        }
    }
}
