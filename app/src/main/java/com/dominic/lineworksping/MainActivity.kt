package com.dominic.lineworksping

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.dominic.lineworksping.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsStore

    private val soundPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            settings.soundUri = uri?.toString()
            Notifier.ensureChannel(this, settings)
            updateSoundLabel()
        }
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* status refreshed in onResume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsStore(this)
        Notifier.ensureChannel(this, settings)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.switchEnabled.setOnCheckedChangeListener { _, v -> settings.enabled = v }
        binding.switchMention.setOnCheckedChangeListener { _, v -> settings.mentionEnabled = v }
        binding.switchRequireAt.setOnCheckedChangeListener { _, v -> settings.requireAtSymbol = v }
        binding.switchDm.setOnCheckedChangeListener { _, v -> settings.dmImportant = v }
        binding.switchLogAll.setOnCheckedChangeListener { _, v -> settings.logAllApps = v }
        binding.switchBypassDnd.setOnCheckedChangeListener { _, v ->
            settings.bypassDnd = v
            Notifier.ensureChannel(this, settings)
            updateDndStatus()
        }

        binding.btnNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.btnPickApps.setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }
        binding.btnRecent.setOnClickListener {
            startActivity(Intent(this, RecentActivity::class.java))
        }
        binding.btnPickSound.setOnClickListener { openSoundPicker() }
        binding.btnTestSound.setOnClickListener { sendTestPing() }
        binding.btnNotifSettings.setOnClickListener { openAppNotificationSettings() }
        binding.btnDndAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        binding.btnCheckUpdate.setOnClickListener { checkForUpdate() }
    }

    override fun onResume() {
        super.onResume()
        binding.switchEnabled.isChecked = settings.enabled
        binding.switchMention.isChecked = settings.mentionEnabled
        binding.switchRequireAt.isChecked = settings.requireAtSymbol
        binding.switchDm.isChecked = settings.dmImportant
        binding.switchLogAll.isChecked = settings.logAllApps
        binding.switchBypassDnd.isChecked = settings.bypassDnd
        binding.editKeywords.setText(settings.keywordsRaw)
        Notifier.ensureChannel(this, settings)
        updateSoundLabel()
        updateAppsLabel()
        updateAccessStatus()
        updateNotifStatus()
        updateDndStatus()
        binding.textVersion.text = getString(R.string.current_version, BuildConfig.VERSION_NAME)
    }

    override fun onPause() {
        super.onPause()
        settings.keywordsRaw = binding.editKeywords.text?.toString().orEmpty()
    }

    // ---- Sound ----

    private fun openSoundPicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.pick_sound))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            val current = settings.soundUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current)
        }
        soundPicker.launch(intent)
    }

    private fun sendTestPing() {
        val shown = Notifier.notifyImportant(
            this, settings,
            getString(R.string.test_title),
            getString(R.string.test_body)
        )
        if (!shown) {
            Toast.makeText(this, R.string.test_blocked, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateSoundLabel() {
        val uri = PingPlayer.resolveUri(settings)
        val title = try {
            RingtoneManager.getRingtone(this, uri)?.getTitle(this)
        } catch (e: Exception) {
            null
        }
        binding.textSound.text = getString(R.string.current_sound, title ?: getString(R.string.default_sound))
    }

    // ---- Status helpers ----

    private fun updateAppsLabel() {
        val count = settings.monitoredPackages.size
        binding.textApps.text = resources.getQuantityString(R.plurals.monitored_apps, count, count)
    }

    private fun updateAccessStatus() {
        val granted = isNotificationAccessGranted()
        binding.textAccessStatus.text = getString(
            if (granted) R.string.access_granted else R.string.access_not_granted
        )
    }

    private fun updateNotifStatus() {
        val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        binding.textNotifStatus.text = getString(
            if (enabled) R.string.notif_enabled else R.string.notif_disabled
        )
    }

    private fun updateDndStatus() {
        val nm = getSystemService(NotificationManager::class.java)
        val granted = nm.isNotificationPolicyAccessGranted
        binding.textDndStatus.text = getString(
            when {
                !settings.bypassDnd -> R.string.dnd_off
                granted -> R.string.dnd_granted
                else -> R.string.dnd_needed
            }
        )
    }

    private fun isNotificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        val me = ComponentName(this, PingNotificationListenerService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(intent)
    }

    // ---- In-app update ----

    private fun checkForUpdate() {
        binding.btnCheckUpdate.isEnabled = false
        Toast.makeText(this, R.string.checking_updates, Toast.LENGTH_SHORT).show()
        Thread {
            val result = UpdateManager.check()
            runOnUiThread {
                binding.btnCheckUpdate.isEnabled = true
                when (result) {
                    is UpdateManager.CheckResult.UpToDate ->
                        Toast.makeText(this, R.string.up_to_date, Toast.LENGTH_LONG).show()
                    is UpdateManager.CheckResult.Failed ->
                        AlertDialog.Builder(this)
                            .setTitle(R.string.update_failed_title)
                            .setMessage(result.message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    is UpdateManager.CheckResult.UpdateAvailable ->
                        promptInstall(result.latest)
                }
            }
        }.start()
    }

    private fun promptInstall(latest: UpdateManager.Latest) {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_available_title)
            .setMessage(getString(R.string.update_available_body, latest.versionName, BuildConfig.VERSION_NAME))
            .setPositiveButton(R.string.update_now) { _, _ -> ensureInstallPermissionThenDownload(latest) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun ensureInstallPermissionThenDownload(latest: UpdateManager.Latest) {
        if (!packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.allow_install_title)
                .setMessage(R.string.allow_install_body)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        downloadAndInstall(latest)
    }

    private fun downloadAndInstall(latest: UpdateManager.Latest) {
        binding.btnCheckUpdate.isEnabled = false
        Toast.makeText(this, R.string.downloading_update, Toast.LENGTH_SHORT).show()
        Thread {
            val file = UpdateManager.download(this, latest.apkUrl)
            runOnUiThread {
                binding.btnCheckUpdate.isEnabled = true
                if (file == null) {
                    Toast.makeText(this, R.string.download_failed, Toast.LENGTH_LONG).show()
                } else {
                    installApk(file)
                }
            }
        }.start()
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.install_failed, Toast.LENGTH_LONG).show()
        }
    }
}
