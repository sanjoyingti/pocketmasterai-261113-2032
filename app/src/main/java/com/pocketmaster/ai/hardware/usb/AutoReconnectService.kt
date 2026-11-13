package com.pocketmaster.ai.hardware.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import androidx.core.app.TaskStackBuilder

/**
 * Foreground-ish background service that watches for USB device detach
 * and broadcasts a reconnect trigger.
 */
class AutoReconnectService : BroadcastReceiver() {

    /** Broadcast sent when a MIDI device is detached. */
    const val ACTION_DEVICE_DETACHED = "com.pocketmaster.ai.DEVICE_DETACHED"

    /** Intent action to open the app from a reconnect trigger. */
    const val ACTION_RECONNECT = "com.pocketmaster.ai.RECONNECT"

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_RECONNECT -> handleReconnect(context)
            ACTION_DEVICE_DETACHED -> handleDetach(context)
        }
    }

    /** Check the USB device list for a detached Pocket Master and notify. */
    private fun handleDetach(context: Context) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val found = usbManager.deviceList.any { it.value.name.contains("Pocket", ignoreCase = true) }
        if (!found) {
            val notify = Intent(ACTION_DEVICE_DETACHED).apply {
                putExtra("detached", true)
            }
            context.sendBroadcast(notify)
        }
    }

    private fun handleReconnect(context: Context) {
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        TaskStackBuilder.create(context).addNextIntentWithParentStack(activityIntent)
    }
}
