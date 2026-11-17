package com.tabony.privacycurtain

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class PrivacyTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val serviceIntent = Intent(this, PrivacyCurtainService::class.java)
        if (PrivacyCurtainService.isRunning) {
            stopService(serviceIntent)
            PrivacyCurtainService.isRunning = false
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            PrivacyCurtainService.isRunning = true
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        if (PrivacyCurtainService.isRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "الستار: مفعل"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "ستار الخصوصية"
        }
        tile.updateTile()
    }
}
