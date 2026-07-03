package com.dominic.lineworksping

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer APK and downloads it. Works without any
 * credentials because the repository is public.
 *
 * The published APK asset is named so its versionCode is the number right before
 * ".apk" (e.g. lineworks-ping-1.0.7-7.apk -> versionCode 7). That is compared
 * against this build's BuildConfig.VERSION_CODE.
 */
object UpdateManager {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/dom1911k/lineworksPING/releases/latest"
    private const val TIMEOUT_MS = 15000
    private val APK_CODE_REGEX = Regex("""(\d+)\.apk$""", RegexOption.IGNORE_CASE)

    data class Latest(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String
    )

    sealed class CheckResult {
        data class UpdateAvailable(val latest: Latest) : CheckResult()
        object UpToDate : CheckResult()
        data class Failed(val message: String) : CheckResult()
    }

    /** Blocking network call — run off the main thread. */
    fun check(): CheckResult {
        return try {
            val json = httpGet(LATEST_RELEASE_URL) ?: return CheckResult.Failed("No response from GitHub")
            val obj = JSONObject(json)
            val tag = obj.optString("tag_name", "")
            val assets = obj.optJSONArray("assets") ?: return CheckResult.Failed("No release assets found")

            var best: Latest? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                val code = APK_CODE_REGEX.find(name)?.groupValues?.get(1)?.toIntOrNull()
                if (code != null && url.isNotEmpty()) {
                    if (best == null || code > best!!.versionCode) {
                        best = Latest(code, tag.ifEmpty { "v$code" }, url)
                    }
                }
            }

            val latest = best ?: return CheckResult.Failed("No APK found in the latest release")
            if (latest.versionCode > BuildConfig.VERSION_CODE) {
                CheckResult.UpdateAvailable(latest)
            } else {
                CheckResult.UpToDate
            }
        } catch (e: Exception) {
            CheckResult.Failed(e.message ?: "Update check failed")
        }
    }

    /** Downloads the APK to the cache and returns the file, or null on failure. */
    fun download(context: Context, apkUrl: String): File? {
        return try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Clear old downloads so the installer never picks up a stale file.
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "update.apk")

            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "LineWorksPing")
                setRequestProperty("Accept", "application/octet-stream")
            }
            conn.inputStream.use { input ->
                out.outputStream.use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            if (out.length() > 0) out else null
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGet(urlString: String): String? {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", "LineWorksPing")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
