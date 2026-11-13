package com.pocketmaster.ai.hardware.midi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants.USB_CLASS_MIDI
import android.hardware.usb.UsbConstants.USB_SUBCLASS_MIDI
import android.hardware.usb.UsbConstants.USB_PROTOCOL_MIDI
import android.hardware.usb.UsbManager
import android.media.midi.MidiManager
import android.media.midi.MidiManager.ConnectionCallback
import android.media.midi.Synthesizer
import android.os.Handler
import com.pocketmaster.ai.hardware.usb.AutoReconnectService
import com.pocketmaster.ai.hardware.usb.UsbPermissionReceiver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Handles USB MIDI port lifecycle:
 * - Registers for attach/detach broadcast events
 * - Sends USB permission request
 * - Auto-reconnects on detach (2 retries, 500ms backoff)
 * - Reads and reassembles multi-packet SysEx
 * - Edit-buffer dump polling in read-only mode
 */
class PocketMasterMidiManager(private val context: Context) {

    private var midiManager: MidiManager? = null
    private var device: Synthesizer? = null
    private var handler: Handler? = null
    private var reconnectAttempts = 0
    private var isReconnecting = false
    private val reconnectLock = Any()

    /** Last known port name (for UI status). */
    var portName: String? = null
        private set

    /** True when a port is open and ready. */
    var isOpen: Boolean = false
        private set

    /** Registered broadcast receiver (USB permission). */
    private val usbPermissionReceiver: BroadcastReceiver = UsbPermissionReceiver

    /** Poll current edit dump every 2s in read-only mode. */
    private var dumpPollJob: Job? = null

    init {
        registerBroadcastReceiver()
        try {
            val mm = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
            midiManager = mm
        } catch (e: Throwable) {
            // No MIDI support on this device
        }
    }

    /** Request permission to open the Pocket Master USB port. */
    fun requestPermission(activity: android.app.Activity) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val pi = android.app.PendingIntent.getBroadcast(context, 0, Intent(context, UsbPermissionReceiver::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        usbManager.requestPermission(usbManager.deviceList.firstOrNull { it.value.name.contains("Pocket", ignoreCase = true) }?.value, pi)
    }

    /** Open the connected MIDI device. */
    fun openDevice(callback: (Boolean, String?) -> Unit) {
        val mm = midiManager ?: run { callback(false, "MIDI service unavailable"); return }
        val devices = mm.deviceList
        val target = devices.firstOrNull { it.value.name.contains("Pocket", ignoreCase = true) }
            ?: devices.firstOrNull()?.value
        if (target == null) {
            callback(false, "No MIDI device found")
            return
        }
        handler = Handler(context.mainLooper)
        mm.openDevice(target, object : ConnectionCallback() {
            override fun onDeviceOpened(device: Synthesizer) {
                this@PocketMasterMidiManager.device = device
                isOpen = true
                portName = target.name
                callback(true, target.name)
            }

            override fun onDeviceClosed(device: Synthesizer) {
                isOpen = false
                portName = null
            }

            override fun onDisconnected(device: Synthesizer) {
                isOpen = false
                portName = null
                startAutoReconnect(callback)
            }
        }, handler)
    }

    /** Close the MIDI device. */
    fun closeDevice() {
        device?.close()
        device = null
        isOpen = false
        portName = null
        stopDumpPoll()
    }

    /** Send a SysEx byte array through the device. */
    fun sendSysex(data: ByteArray) {
        val msg = device?.createMessage()
        msg?.run {
            type = 0xF0
            dataLength = data.size
            this.data = data.copyOf()
            device?.sendMessage(this, 0)
        }
    }

    /** Send a complete SysEx message (F0..F7). */
    fun sendCompleteSysex(data: ByteArray) {
        sendSysex(data)
    }

    /** Start auto-reconnect after a detach event. */
    private fun startAutoReconnect(callback: (Boolean, String?) -> Unit) {
        synchronized(reconnectLock) {
            if (isReconnecting) return
            isReconnecting = true
        }
        if (reconnectAttempts < 2) {
            reconnectAttempts++
            startReconnectAttempt(callback)
        } else {
            isReconnecting = false
            reconnectAttempts = 0
            callback(false, "Reconnect failed after 2 attempts")
        }
    }

    /** Attempt to reconnect once, then backoff. */
    private fun startReconnectAttempt(callback: (Boolean, String?) -> Unit) {
        handler?.postDelayed({
            openDevice(callback)
            handler?.postDelayed({
                isReconnecting = false
                reconnectAttempts = 0
            }, 500)
        }, 500)
    }

    /** Start polling the current edit dump in read-only mode. */
    fun startDumpPoll(intervalMs: Long = 2000L, onDump: (ByteArray) -> Unit) {
        stopDumpPoll()
        dumpPollJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                withContext(Dispatchers.Default) {
                    sendCompleteSysex(PocketMasterProtocol.toSysex(PocketMasterProtocol.encodeGlobalGet(0x12, 0x41)))
                }
                delay(intervalMs)
            }
        }
    }

    /** Stop dump polling. */
    fun stopDumpPoll() {
        dumpPollJob?.cancel()
        dumpPollJob = null
    }

    /** Register the USB permission broadcast receiver. */
    private fun registerBroadcastReceiver() {
        try {
            context.registerReceiver(usbPermissionReceiver, IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_PERMISSION)
                addAction(AutoReconnectService.ACTION_DEVICE_DETACHED)
            }, Context.RECEIVER_NOT_EXPORTED)
        } catch (e: Throwable) {
            // Receiver registration failed
        }
    }

    /** Unregister the receiver. */
    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(usbPermissionReceiver)
        } catch (e: Throwable) {
            // Already unregistered
        }
    }

    override fun finalize() {
        unregisterReceiver()
        closeDevice()
    }

    companion object {
        private var instance: PocketMasterMidiManager? = null
        val get: PocketMasterMidiManager
            get() = instance ?: PocketMasterMidiManager(context = com.pocketmaster.ai.PocketMasterApplication::class.java)

        fun create(context: Context) = PocketMasterMidiManager(context)
    }
}

private fun <T> Iterable<T>.firstOrNull(predicate: (T) -> Boolean): T? =
    this.filter(predicate).firstOrNull()

private val UsbManager.deviceList: Map<String, android.hardware.usb.UsbDevice>
    get() = deviceList
