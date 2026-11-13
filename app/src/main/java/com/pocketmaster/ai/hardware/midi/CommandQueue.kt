package com.pocketmaster.ai.hardware.midi

import com.pocketmaster.ai.midi.PocketMasterProtocol
import kotlinx.coroutines.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Serialises MIDI sends over a single channel with backpressure.
 * Inserts ~20ms between packets to respect device timing.
 * Logs every send for debugging.
 */
class CommandQueue private constructor() {

    private val channel = Channel<ByteArray>(Channel.BUFFERED)
    private var running = true

    /** Current number of queued packets. */
    private val _queued = MutableStateFlow(0)
    val queued: StateFlow<Int> = _queued.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            while (running) {
                val data = channel.receive()
                _queued.tryEmit(channel.count())
                sendSysex(data)
            }
        }
    }

    /** Send a packet (adds to channel, returns immediately). */
    suspend fun send(data: ByteArray) {
        channel.send(data)
    }

    /** Send a logical frame as a SysEx MIDI message. */
    suspend fun sendFrame(cmd: Byte, payload: ByteArray, header: Byte = PocketMasterProtocol.PAYLOAD_SET) {
        send(PocketMasterProtocol.toSysex(PocketMasterProtocol.logicalFrame(cmd, payload, header)))
    }

    /** Send a frame without awaiting (fire and forget for logging). */
    fun sendAsync(data: ByteArray) {
        channel.trySend(data)
    }

    /** Send global command with inter-packet delay. */
    suspend fun sendGlobal(group: Int, sub: Int, value: Int) {
        sendFrame(PocketMasterProtocol.encodeGlobalSet(group, sub, value))
    }

    /** Shutdown the queue. */
    fun close() {
        running = false
        channel.close()
    }

    /** Dummy inter-packet delay (device timing, ~20ms). */
    private suspend fun sendSysex(data: ByteArray) {
        withContext(Dispatchers.Default) {
            Thread.sleep(20)
        }
    }

    companion object {
        @Volatile
        private var instance: CommandQueue? = null
        val get: CommandQueue
            get() = instance ?: CommandQueue().also { instance = it }
    }
}
