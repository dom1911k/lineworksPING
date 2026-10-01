package com.dominic.lineworksping

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

/** Handles the "Pause 20 min" / "Pause 1 hour" actions on a ping notification. */
class SnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)
        if (minutes <= 0) return

        Snooze.pause(context, minutes)

        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (notifId != -1) {
            try {
                NotificationManagerCompat.from(context).cancel(notifId)
            } catch (e: Exception) {
                // ignore
            }
        }

        val until = SettingsStore(context).snoozeUntil
        Toast.makeText(
            context,
            context.getString(R.string.snoozed_toast, Snooze.endTime(context, until)),
            Toast.LENGTH_LONG
        ).show()
    }

    companion object {
        const val EXTRA_MINUTES = "extra_minutes"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }
}
