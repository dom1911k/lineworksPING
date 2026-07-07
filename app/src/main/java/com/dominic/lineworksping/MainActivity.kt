package com.dominic.lineworksping

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.dominic.lineworksping.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsStore

    /** One row of the "Setup status" checklist. */
    private class Requirement(
        val title: String,
        val granted: Boolean,
        val fix: (() -> Unit)?
    )

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
    ) { renderStatus() }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateWifiStatus(); renderStatus() }

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
        binding.switchSilenceCharging.setOnCheckedChangeListener { _, v -> settings.silenceWhileCharging = v }
        binding.switchReadAloud.setOnCheckedChangeListener { _, v -> settings.readAloud = v }
        binding.switchOfficeWifi.setOnCheckedChangeListener { _, v ->
            settings.silenceOnOfficeWifi = v
            if (v) ensureLocationPermission()
            updateWifiStatus()
            renderStatus()
        }
        binding.switchMention.setOnCheckedChangeListener { _, v -> settings.mentionEnabled = v }
        binding.switchRequireAt.setOnCheckedChangeListener { _, v -> settings.requireAtSymbol = v }
        binding.switchDm.setOnCheckedChangeListener { _, v -> settings.dmImportant = v }
        binding.switchLogAll.setOnCheckedChangeListener { _, v -> settings.logAllApps = v }
        binding.switchAlertVibrate.setOnCheckedChangeListener { _, v -> settings.alertVibrate = v }
        binding.switchAlertPulse.setOnCheckedChangeListener { _, v -> settings.alertPulse = v }
        binding.switchFullScreen.setOnCheckedChangeListener { _, v ->
            settings.fullScreenAlert = v
            renderStatus()
        }
        binding.switchBypassDnd.setOnCheckedChangeListener { _, v ->
            settings.bypassDnd = v
            Notifier.ensureChannel(this, settings)
            renderStatus()
        }
        setupAlertSpinners()

        binding.btnAddTile.setOnClickListener { addQuickTile() }
        binding.btnPickApps.setOnClickListener { startActivity(Intent(this, AppPickerActivity::class.java)) }
        binding.btnRecent.setOnClickListener { startActivity(Intent(this, RecentActivity::class.java)) }
        binding.btnPickSound.setOnClickListener { openSoundPicker() }
        binding.btnTestSound.setOnClickListener { sendTestPing() }
        binding.btnPreviewAlert.setOnClickListener { previewAlert() }
        binding.btnUseWifi.setOnClickListener { useCurrentWifi() }
        binding.btnCheckUpdate.setOnClickListener { checkForUpdate() }
    }

    override fun onResume() {
        super.onResume()
        binding.switchEnabled.isChecked = settings.enabled
        binding.switchSilenceCharging.isChecked = settings.silenceWhileCharging
        binding.switchReadAloud.isChecked = settings.readAloud
        binding.switchOfficeWifi.isChecked = settings.silenceOnOfficeWifi
        binding.editOfficeSsids.setText(settings.officeSsidsRaw)
        binding.switchMention.isChecked = settings.mentionEnabled
        binding.switchRequireAt.isChecked = settings.requireAtSymbol
        binding.switchDm.isChecked = settings.dmImportant
        binding.switchLogAll.isChecked = settings.logAllApps
        binding.switchBypassDnd.isChecked = settings.bypassDnd
        binding.switchFullScreen.isChecked = settings.fullScreenAlert
        binding.switchAlertVibrate.isChecked = settings.alertVibrate
        binding.switchAlertPulse.isChecked = settings.alertPulse
        binding.spinnerColor.setSelection(settings.alertColor)
        binding.spinnerSpeed.setSelection(settings.alertSpeed)
        binding.spinnerTextSize.setSelection(settings.alertTextSize)
        binding.editKeywords.setText(settings.keywordsRaw)
        Notifier.ensureChannel(this, settings)
        updateSoundLabel()
        updateAppsLabel()
        updateWifiStatus()
        updateHealthStatus()
        renderStatus()
        binding.textVersion.text = getString(R.string.current_version, BuildConfig.VERSION_NAME)
    }

    override fun onPause() {
        super.onPause()
        settings.keywordsRaw = binding.editKeywords.text?.toString().orEmpty()
        settings.officeSsidsRaw = binding.editOfficeSsids.text?.toString().orEmpty()
    }

    // ---- Setup status dashboard ----

    private fun renderStatus() {
        val reqs = buildRequirements()
        val missing = reqs.count { !it.granted }
        binding.statusSummary.text = if (missing == 0) {
            getString(R.string.status_all_set)
        } else {
            resources.getQuantityString(R.plurals.status_needs, missing, missing)
        }
        binding.statusSummary.setTextColor(if (missing == 0) COLOR_OK else COLOR_WARN)

        binding.statusContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (r in reqs) {
            val row = inflater.inflate(R.layout.item_status, binding.statusContainer, false)
            val icon = row.findViewById<TextView>(R.id.statusIcon)
            icon.text = if (r.granted) "✓" else "!"
            icon.setTextColor(if (r.granted) COLOR_OK else COLOR_WARN)
            row.findViewById<TextView>(R.id.statusLabel).text = r.title
            val fix = row.findViewById<MaterialButton>(R.id.statusFix)
            if (!r.granted && r.fix != null) {
                fix.visibility = View.VISIBLE
                fix.setOnClickListener { r.fix.invoke() }
            } else {
                fix.visibility = View.GONE
            }
            binding.statusContainer.addView(row)
        }
    }

    private fun buildRequirements(): List<Requirement> {
        val nm = getSystemService(NotificationManager::class.java)
        val list = mutableListOf<Requirement>()

        list += Requirement(getString(R.string.req_access), isNotificationAccessGranted()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        list += Requirement(
            getString(R.string.req_popup),
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        ) { openAppNotificationSettings() }
        list += Requirement(getString(R.string.req_battery), isBatteryOptimizationIgnored()) {
            requestIgnoreBattery()
        }

        if (settings.fullScreenAlert) {
            if (Build.VERSION.SDK_INT >= 34) {
                list += Requirement(getString(R.string.req_fsi), nm.canUseFullScreenIntent()) {
                    openFullScreenIntentSettings()
                }
            }
            list += Requirement(getString(R.string.req_overlay), Settings.canDrawOverlays(this)) {
                openOverlaySettings()
            }
        }
        if (settings.bypassDnd) {
            list += Requirement(getString(R.string.req_dnd), nm.isNotificationPolicyAccessGranted) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }
        if (settings.silenceOnOfficeWifi) {
            val granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            list += Requirement(getString(R.string.req_location), granted) { ensureLocationPermission() }
        }
        return list
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBattery() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        val me = ComponentName(this, PingNotificationListenerService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    private fun openAppNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (e: Exception) {
                openAppNotificationSettings()
            }
        }
    }

    // ---- Listener health ----

    private fun updateHealthStatus() {
        val connected = isNotificationAccessGranted()
        val last = settings.lastEventTime
        val lastStr = if (last == 0L) {
            getString(R.string.health_never)
        } else {
            DateUtils.getRelativeTimeSpanString(
                last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
            ).toString()
        }
        binding.textHealth.text = getString(
            if (connected) R.string.health_ok else R.string.health_bad, lastStr
        )
    }

    // ---- Office Wi-Fi ----

    private fun ensureLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun useCurrentWifi() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ensureLocationPermission()
            return
        }
        val ssid = WifiInfoHelper.currentSsid(this)
        if (ssid == null) {
            Toast.makeText(this, R.string.wifi_read_fail, Toast.LENGTH_LONG).show()
            return
        }
        val existing = binding.editOfficeSsids.text?.toString().orEmpty()
            .split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (existing.none { it.equals(ssid, ignoreCase = true) }) existing.add(ssid)
        binding.editOfficeSsids.setText(existing.joinToString(", "))
        settings.officeSsidsRaw = binding.editOfficeSsids.text?.toString().orEmpty()
        Toast.makeText(this, getString(R.string.wifi_added, ssid), Toast.LENGTH_SHORT).show()
    }

    private fun updateWifiStatus() {
        val ssid = WifiInfoHelper.currentSsid(this)
        binding.textWifiStatus.text = when {
            !settings.silenceOnOfficeWifi -> getString(R.string.wifi_off)
            ssid != null -> getString(R.string.wifi_current, ssid)
            else -> getString(R.string.wifi_unknown)
        }
    }

    // ---- Sound / alert ----

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
            this, settings, getString(R.string.test_title), getString(R.string.test_body)
        )
        if (settings.fullScreenAlert) {
            startActivity(
                Intent(this, AlertActivity::class.java)
                    .putExtra(AlertActivity.EXTRA_TITLE, getString(R.string.test_title))
                    .putExtra(AlertActivity.EXTRA_TEXT, getString(R.string.test_body))
            )
        }
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

    private fun updateAppsLabel() {
        val count = settings.monitoredPackages.size
        binding.textApps.text = resources.getQuantityString(R.plurals.monitored_apps, count, count)
    }

    private fun setupAlertSpinners() {
        bindSpinner(binding.spinnerColor, R.array.alert_colors) { settings.alertColor = it }
        bindSpinner(binding.spinnerSpeed, R.array.alert_speeds) { settings.alertSpeed = it }
        bindSpinner(binding.spinnerTextSize, R.array.alert_text_sizes) { settings.alertTextSize = it }
    }

    private fun bindSpinner(spinner: Spinner, arrayRes: Int, onSelected: (Int) -> Unit) {
        val adapter = ArrayAdapter.createFromResource(
            this, arrayRes, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                onSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun previewAlert() {
        startActivity(
            Intent(this, AlertActivity::class.java)
                .putExtra(AlertActivity.EXTRA_TITLE, getString(R.string.test_title))
                .putExtra(AlertActivity.EXTRA_TEXT, getString(R.string.test_body))
        )
    }

    private fun addQuickTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBar = getSystemService(StatusBarManager::class.java)
            statusBar.requestAddTileService(
                ComponentName(this, PingTileService::class.java),
                getString(R.string.app_name),
                Icon.createWithResource(this, R.drawable.ic_stat_ping),
                { it.run() },
                { }
            )
        } else {
            Toast.makeText(this, R.string.add_tile_manual, Toast.LENGTH_LONG).show()
        }
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

    companion object {
        private const val COLOR_OK = 0xFF2E7D32.toInt()
        private const val COLOR_WARN = 0xFFEF6C00.toInt()
    }
}
