package com.dominic.lineworksping

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin wrapper over SharedPreferences holding all user-configurable settings.
 * Every getter reads live from prefs, so the listener service always sees the
 * latest values without needing to be restarted.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Master on/off for the whole app. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Package names of the apps whose notifications we react to. */
    var monitoredPackages: Set<String>
        get() = prefs.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_PACKAGES, v).apply()

    /** Raw multi-line / comma-separated keyword text as typed by the user. */
    var keywordsRaw: String
        get() = prefs.getString(KEY_KEYWORDS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_KEYWORDS, v).apply()

    /** Parsed, trimmed, non-empty keywords used for @mention matching. */
    val keywords: List<String>
        get() = keywordsRaw.split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** Whether an @mention of one of your keywords counts as important. */
    var mentionEnabled: Boolean
        get() = prefs.getBoolean(KEY_MENTION, true)
        set(v) = prefs.edit().putBoolean(KEY_MENTION, v).apply()

    /** If true, a keyword only matches when written as "@keyword". */
    var requireAtSymbol: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE_AT, false)
        set(v) = prefs.edit().putBoolean(KEY_REQUIRE_AT, v).apply()

    /** Whether a direct message (1:1 chat) counts as important. */
    var dmImportant: Boolean
        get() = prefs.getBoolean(KEY_DM, true)
        set(v) = prefs.edit().putBoolean(KEY_DM, v).apply()

    /** URI of the chosen "important" sound, or null to use the system default. */
    var soundUri: String?
        get() = prefs.getString(KEY_SOUND, null)
        set(v) = prefs.edit().putString(KEY_SOUND, v).apply()

    /** Whether important pings should pierce Do Not Disturb. */
    var bypassDnd: Boolean
        get() = prefs.getBoolean(KEY_BYPASS_DND, true)
        set(v) = prefs.edit().putBoolean(KEY_BYPASS_DND, v).apply()

    /** Troubleshooting: record every notification (not just monitored apps) in the log. */
    var logAllApps: Boolean
        get() = prefs.getBoolean(KEY_LOG_ALL, false)
        set(v) = prefs.edit().putBoolean(KEY_LOG_ALL, v).apply()

    /** Whether to also show the full-screen colourful takeover alert. */
    var fullScreenAlert: Boolean
        get() = prefs.getBoolean(KEY_FULLSCREEN, true)
        set(v) = prefs.edit().putBoolean(KEY_FULLSCREEN, v).apply()

    companion object {
        private const val PREFS = "lineworks_ping_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PACKAGES = "packages"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_MENTION = "mention_enabled"
        private const val KEY_REQUIRE_AT = "require_at"
        private const val KEY_DM = "dm_important"
        private const val KEY_SOUND = "sound_uri"
        private const val KEY_BYPASS_DND = "bypass_dnd"
        private const val KEY_LOG_ALL = "log_all_apps"
        private const val KEY_FULLSCREEN = "fullscreen_alert"
    }
}
