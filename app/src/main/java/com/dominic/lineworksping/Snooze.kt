package com.dominic.lineworksping

import android.content.Context
import android.text.format.DateFormat
import java.util.Date

/** Temporary "pause pings" that expires by itself, so it can't be forgotten. */
object Snooze {

    const val MINUTES_SHORT = 20
    const val MINUTES_LONG = 60

    fun pause(context: Context, minutes: Int) {
        SettingsStore(context).snoozeUntil = System.currentTimeMillis() + minutes * 60_000L
    }

    fun clear(context: Context) {
        SettingsStore(context).snoozeUntil = 0L
    }

    /** Local clock time the snooze ends, e.g. "14:35". */
    fun endTime(context: Context, until: Long): String =
        DateFormat.getTimeFormat(context).format(Date(until))
}
