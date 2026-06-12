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
 *  - Direct message: the notification is a 1:1 conversation (not a group). On
 *    API 28+ this is read reliably from MessagingStyle's group flag; otherwise
 *    we fall back to a best-effort heuristic.
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

        if (settings.dmImportant && isDirectMessage(n, extras)) {
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

    private fun isDirectMessage(n: Notification, extras: Bundle): Boolean {
        // Most modern messaging apps (incl. LINE WORKS) use MessagingStyle, which
        // exposes whether the conversation is a group.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            extras.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)
        ) {
            return !extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)
        }

        // Fallback: a message-category notification with no separate conversation
        // (group) title is most likely a 1:1 chat.
        val isMessage = n.category == Notification.CATEGORY_MESSAGE
        val convoTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
        return isMessage && convoTitle.isNullOrBlank()
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
