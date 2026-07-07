package com.dominic.lineworksping

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * A Quick Settings tile that toggles the app on/off straight from the
 * notification shade — e.g. tap it to pause pings while you're at your desk.
 * It reflects and controls the same master "enabled" setting the listener uses.
 */
class PingTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val settings = SettingsStore(this)
        settings.enabled = !settings.enabled
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val enabled = SettingsStore(this).enabled
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (enabled) R.string.tile_on else R.string.tile_off)
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_ping)
        tile.updateTile()
    }
}
