package com.groovio.app.ui.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings

/**
 * Opens the system audio output picker (speaker, Bluetooth, etc.).
 * Android 14+ has a system output switcher; older versions fall back to
 * Bluetooth settings, where headphones and speakers are connected.
 */
fun openOutputSwitcher(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        if (MediaRouter2.getInstance(context).showSystemOutputSwitcher()) return
    }
    try {
        context.startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
    }
}
