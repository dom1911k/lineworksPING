package com.dominic.lineworksping

import android.content.ComponentName
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dominic.lineworksping.databinding.ActivityMainBinding

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
            updateSoundLabel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = SettingsStore(this)

        binding.switchEnabled.setOnCheckedChangeListener { _, v -> settings.enabled = v }
        binding.switchMention.setOnCheckedChangeListener { _, v -> settings.mentionEnabled = v }
        binding.switchRequireAt.setOnCheckedChangeListener { _, v -> settings.requireAtSymbol = v }
        binding.switchDm.setOnCheckedChangeListener { _, v -> settings.dmImportant = v }

        binding.btnNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        binding.btnPickApps.setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }
        binding.btnPickSound.setOnClickListener { openSoundPicker() }
        binding.btnTestSound.setOnClickListener {
            PingPlayer.play(this, PingPlayer.resolveUri(settings), respectCooldown = false)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-load from prefs each time so changes made elsewhere are reflected,
        // and the access status refreshes after returning from system settings.
        binding.switchEnabled.isChecked = settings.enabled
        binding.switchMention.isChecked = settings.mentionEnabled
        binding.switchRequireAt.isChecked = settings.requireAtSymbol
        binding.switchDm.isChecked = settings.dmImportant
        binding.editKeywords.setText(settings.keywordsRaw)
        updateSoundLabel()
        updateAppsLabel()
        updateAccessStatus()
    }

    override fun onPause() {
        super.onPause()
        settings.keywordsRaw = binding.editKeywords.text?.toString().orEmpty()
    }

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

    private fun updateAccessStatus() {
        val granted = isNotificationAccessGranted()
        binding.textAccessStatus.text = getString(
            if (granted) R.string.access_granted else R.string.access_not_granted
        )
    }

    private fun isNotificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        val me = ComponentName(this, PingNotificationListenerService::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}
