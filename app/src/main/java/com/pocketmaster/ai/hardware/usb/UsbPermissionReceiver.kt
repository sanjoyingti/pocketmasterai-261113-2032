package com.pocketmaster.ai.hardware.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager

/**
 * Receives USB permission results and device attach/detach broadcasts.
 * Sends permission result so the app can open the Pocket Master port.
 */
object UsbPermissionReceiver : BroadcastReceiver() {

    /** Broadcast for successful permission grant. */
    const val ACTION_PERMISSION_GRANTED = "com.pocketmaster.ai.PERMISSION_GRANTED"

    /** Broadcast for device detach (triggers reconnect). */
    const val ACTION_DEVICE_DETACHED = "com.pocketmaster.ai.DEVICE_DETACHED"

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            UsbManager.ACTION_USB_PERMISSION -> {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val action = Intent(ACTION_PERMISSION_GRANTED).apply {
                    putExtra("granted", granted)
                }
                context.sendBroadcast(action)
            }
            AutoReconnectService.ACTION_DEVICE_DETACHED -> {
                // Reconnect logic handled by PocketMasterMidiManager
            }
        }
    }
}
