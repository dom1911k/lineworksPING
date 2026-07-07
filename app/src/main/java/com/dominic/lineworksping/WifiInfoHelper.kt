package com.dominic.lineworksping

import android.content.Context
import android.net.wifi.WifiManager

/** Reads the currently-connected Wi-Fi network name (SSID), if permitted. */
object WifiInfoHelper {

    /**
     * The connected SSID, or null if not on Wi-Fi or the name can't be read
     * (Android hides the SSID unless location permission is granted). Callers
     * should treat null as "unknown" and NOT suppress on it (fail open).
     */
    fun currentSsid(context: Context): String? {
        return try {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            @Suppress("DEPRECATION")
            val raw = wm.connectionInfo?.ssid ?: return null
            val ssid = raw.trim('"')
            if (ssid.isBlank() || ssid.equals("<unknown ssid>", ignoreCase = true) || ssid == "0x") {
                null
            } else {
                ssid
            }
        } catch (e: Exception) {
            null
        }
    }
}
