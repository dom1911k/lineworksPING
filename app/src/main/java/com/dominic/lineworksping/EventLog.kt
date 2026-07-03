package com.dominic.lineworksping

/**
 * Small in-memory ring buffer of the notifications the listener has seen and how
 * they were classified. Shown on the "Recent activity" screen to help diagnose
 * why pings do or don't fire. Lives for the lifetime of the app process (shared
 * between the listener service and the UI, which run in the same process).
 */
object EventLog {

    data class Entry(
        val time: Long,
        val pkg: String,
        val label: String,
        val text: String,
        val decision: String,
        val pinged: Boolean
    )

    private const val MAX = 80
    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun add(entry: Entry) {
        entries.addFirst(entry)
        while (entries.size > MAX) entries.removeLast()
    }

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()
}
