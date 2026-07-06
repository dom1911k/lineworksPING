package com.dominic.lineworksping

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification

/** Why a notification was considered important (or not). */
enum class PingReason { NONE, DIRECT_MESSAGE, MENTION }

/**
 * Decides whether an incoming notification is "important" enough to ping.
 *
 * Two signals are used:
 *  - Mention: any of the user's keywords appears in the notification text.
 *  - Direct message: the notification is a 1:1 conversation (not a group). Read
 *    from MessagingStyle's group flag when present; for LINE WORKS (which doesn't
 *    set it) we compare the conversation title against the sender in the text.
 */
class NotificationClassifier(private val settings: SettingsStore) {

    fun classify(sbn: StatusBarNotification): PingReason {
        val n = sbn.notification ?: return PingReason.NONE
        val extras = n.extras ?: return PingReason.NONE

        val blob = buildString {
            appendLine(extras.getCharSequence(Notification.EXTRA_TITLE))
            appendLine(extras.getCharSequence(Notification.EXTRA_TEXT))
            appendLine(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            appendLine(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
            appendLine(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))
            appendLine(messagesText(extras))
        }

        // A mention is important whether it arrives in a DM or a group chat.
        if (settings.mentionEnabled && matchesMention(blob)) {
            return PingReason.MENTION
        }

        if (settings.dmImportant && isDirectMessage(extras)) {
            return PingReason.DIRECT_MESSAGE
        }

        return PingReason.NONE
    }

    private fun matchesMention(blob: String): Boolean {
        val haystack = blob.lowercase()
        return settings.keywords.any { kw ->
            val needle = if (settings.requireAtSymbol) "@$kw" else kw
            haystack.contains(needle.lowercase())
        }
    }

    private fun isDirectMessage(extras: Bundle): Boolean {
        // Reliable when the app uses MessagingStyle, which exposes the group flag.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)
        ) {
            return !extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        }

        // LINE WORKS uses BigTextStyle and never sets the group flag. It puts the
        // CONVERSATION name in the title (e.g. "[Message] Kai Brieske") and
        // "<sender> : <message>" in the text (e.g. "Kai Brieske : hi").
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()

        // A real chat message always has a "<sender> :" prefix. System/bot notices
        // (e.g. "… has been added to the members list.") do not, so if we can't
        // parse a sender it isn't a person-to-person message — never a DM.
        val sender = senderOf(text)
        if (sender.isBlank()) return false

        // In a 1:1 chat the conversation name (title) IS the sender; in a group
        // it's the group name, which differs from the sender.
        val conversationName = stripLabelPrefix(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        )
        return conversationName.equals(sender, ignoreCase = true)
    }

    /** Removes a leading bracketed label like "[Message] " from a title. */
    private fun stripLabelPrefix(title: String): String =
        title.replace(Regex("^\\s*\\[[^\\]]*\\]\\s*"), "").trim()

    /** The sender before the first ':' in "<sender> : <message>", or "" if none. */
    private fun senderOf(text: String): String {
        val idx = text.indexOfFirst { it == ':' || it == '：' }
        return if (idx > 0) text.substring(0, idx).trim() else ""
    }

    /** Flattens MessagingStyle messages (sender + text) into a searchable string. */
    private fun messagesText(extras: Bundle): String {
        return try {
            val msgs = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return ""
            buildString {
                for (p in msgs) {
                    val b = p as? Bundle ?: continue
                    b.getCharSequence("sender")?.let { append(it).append(": ") }
                    b.getCharSequence("text")?.let { append(it).append('\n') }
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
}
