package com.pocketmaster.ai.hardware.midi.fake

import com.pocketmaster.ai.midi.PocketMasterProtocol
import com.pocketmaster.ai.midi.presetIndex
import com.pocketmaster.ai.midi.presetLabel
import kotlin.collections.forEach
import kotlin.collections.get
import kotlin.collections.getKeys
import kotlin.collections.indexOf
import kotlin.collections.isEmpty
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import kotlin.collections.set
import kotlin.math.min

/**
 * In-memory Pocket Master stand-in for unit tests.
 * Ported from tests/fake_device.py. Does NOT touch real hardware.
 */
class FakePocketMasterDevice {

    /** Nine-slot signal chain (block indices 0-8). */
    var chain: IntArray = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8)

    /** Global master volume (0.0-1.0). */
    var masterVolume = 0.5f

    /** Tempo (BPM). */
    var tempo = 120f

    /** Per-block state: (blockIndex, slot) -> on/off. */
    val blockStates: MutableMap<Int, Boolean> = mutableMapOf()

    /** Per-block model: (blockIndex, slot) -> modelCode. */
    val blockModels: MutableMap<Int, Int> = mutableMapOf()

    /** Per-block params: (blockIndex, slot, paramIndex) -> value. */
    val blockParams: MutableMap<Int, MutableMap<Int, MutableMap<Int, Float>>> = mutableMapOf()

    /** Presets: slot -> preset data. */
    val presets: MutableMap<Int, Preset> = mutableMapOf()

    /** Loaded preset slot (0-99). */
    var loadedSlot: Int = 0

    /** Edit buffer dirty flag. */
    var dirty: Boolean = false

    var name: String = "Fake Pocket Master"

    /** All 100 preset names (P01..P50, F01..F50). */
    val presetNames: List<String> = (1..50).map { "P%02d".format(it) } +
        (1..50).map { "F%02d".format(it) }

    var modified: Boolean = false

    /** Reset to factory defaults. */
    fun reset() {
        chain = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
        masterVolume = 0.5f
        tempo = 120f
        blockStates.clear()
        blockModels.clear()
        blockParams.clear()
        presets.clear()
        loadedSlot = 0
        dirty = false
        modified = false
    }

    /** Build the edit-buffer SysEx dump payload from current state. */
    fun buildEditDump(): ByteArray {
        val frames = mutableListOf<ByteArray>()

        // Header 0x00ff
        frames.add(byteArrayOf(0x00, 0xff.toByte()))

        // 0x3001 states mask: one byte per block
        val statesData = (0..8).map { blockStates.getOrDefault(it, true).toInt().toByte().toByteArray() }.flatten().toByteArray()
        frames.add(buildRecord(0x3001, statesData))

        // 0x3002 chain ids (9 bytes)
        val chainData = chain.map { it.toByte().toByteArray() }.flatten().toByteArray()
        frames.add(buildRecord(0x3002, chainData))

        // 0x3003 effect codes per block
        val codesData = IntArray(9) { blockModels.getOrDefault(it, 0) }.map { it.toByte().toByteArray() }.flatten().toByteArray()
        frames.add(buildRecord(0x3003, codesData))

        // 0x3004 params: 10 x 8 float32, stride 32 bytes
        val paramsData = mutableListOf<Byte>()
        for (i in 0 until 80) {
            val b = i / 8
            val p = i % 8
            val value = blockParams.getOrDefault(b, emptyMap()).getOrDefault(p, 0f)
            paramsData.addAll(value.toFloatBits())
        }
        frames.add(buildRecord(0x3004, paramsData.toByteArray()))

        // 0x2001 volume
        frames.add(buildRecord(0x2001, masterVolume.toFloatBits().toByteArray()))

        // 0x2002 tempo (unconfirmed)
        frames.add(buildRecord(0x2002, tempo.toFloatBits().toByteArray()))

        return encodeSysEx(frames)
    }

    private fun buildRecord(tag: Int, data: ByteArray): ByteArray {
        val tagHi = (tag ushr 8).toByte()
        val tagLo = (tag and 0xff).toByte()
        val len = data.size
        return byteArrayOf(tagHi, tagLo, len.toByte()) + data
    }

    private fun encodeSysEx(frames: List<ByteArray>): ByteArray {
        val payload = frames.flatten()
        val frameList = mutableListOf<ByteArray>()

        val cmd = 0x12
        val crc = PocketMasterProtocol.crc8(payload)

        frameList.add(byteArrayOf(0x12, (payload.size and 0xff).toByte(), crc))

        return byteArrayOf(0xf0) + frameList.flatten() + byteArrayOf(0xf7)
    }

    /** Apply a preset slot (0-99) into the edit buffer. */
    fun applyPreset(slot: Int): Boolean {
        val preset = presets[slot] ?: return false
        chain = preset.chain.toMutableList().reversed().take(9).reversed().toIntArray()
        masterVolume = preset.volume
        tempo = preset.tempo
        blockStates.clear()
        blockModels.clear()
        blockParams.clear()
        preset.blockStates?.forEach { (b, on) -> blockStates[b] = on }
        preset.blockModels?.forEach { (b, m) -> blockModels[b] = m }
        preset.blockParams?.forEach { (b, params) ->
            blockParams.getOrPut(b) { mutableMapOf() }
                .putAll(params)
        }
        loadedSlot = slot
        modified = true
        return true
    }

    /** Save current edit buffer into a preset slot. */
    fun savePreset(slot: Int, name: String): Boolean {
        if (slot < 0 || slot >= 100) return false
        presets[slot] = Preset(
            slot = slot,
            label = name,
            chain = chain.copyOf(),
            volume = masterVolume,
            tempo = tempo,
            blockStates = blockStates.toMap(),
            blockModels = blockModels.toMap(),
            blockParams = blockParams.mapValues { it.value.toMap() }
        )
        dirty = true
        modified = true
        return true
    }

    /** Set block model at chain position. */
    fun setBlockModel(pos: Int, modelCode: Int): Boolean {
        if (pos < 0 || pos >= 9) return false
        blockModels[pos] = modelCode
        modified = true
        return true
    }

    /** Set block on/off. */
    fun setBlockState(pos: Int, on: Boolean): Boolean {
        if (pos < 0 || pos >= 9) return false
        blockStates[pos] = on
        modified = true
        return true
    }

    /** Set parameter. */
    fun setParameter(pos: Int, paramIndex: Int, value: Float): Boolean {
        if (pos < 0 || pos >= 9) return false
        blockParams.getOrPut(pos) { mutableMapOf() }[paramIndex] = value
        modified = true
        return true
    }

    /** Reorder signal chain. */
    fun reorderChain(order: IntArray): Boolean {
        if (order.size != 9) return false
        val sorted = order.sorted()
        if (sorted.indices.map { it }.toList() != sorted) return false
        chain = order.copyOf()
        modified = true
        return true
    }

    /** Set global levels. */
    fun setGlobalLevels(master: Float, input: Float, fxRec: Float, monitor: Float, btRec: Float) {
        masterVolume = master
        // input/monitor/btRec stored in device but kept simple here
        modified = true
    }

    /** Get current device state as structured data. */
    fun getState(): DeviceState {
        return DeviceState(
            chain = chain.copyOf(),
            masterVolume = masterVolume,
            tempo = tempo,
            blockStates = blockStates.toMap(),
            blockModels = blockModels.toMap(),
            blockParams = blockParams.mapValues { it.value.toMap() },
            loadedSlot = loadedSlot,
            modified = modified
        )
    }

    /** Convert float to 4 bytes (little-endian IEEE 754). */
    private fun Float.toFloatBits(): ByteArray {
        val bits = toRawIntBits().toLong() and 0xffffffffL
        return byteArrayOf(
            (bits and 0xff).toByte(),
            ((bits ushr 8) and 0xff).toByte(),
            ((bits ushr 16) and 0xff).toByte(),
            ((bits ushr 24) and 0xff).toByte()
        )
    }

    data class Preset(
        val slot: Int,
        val label: String,
        val chain: IntArray,
        val volume: Float,
        val tempo: Float,
        val blockStates: Map<Int, Boolean>?,
        val blockModels: Map<Int, Int>?,
        val blockParams: Map<Int, Map<Int, Float>>?
    )

    data class DeviceState(
        val chain: IntArray,
        val masterVolume: Float,
        val tempo: Float,
        val blockStates: Map<Int, Boolean>,
        val blockModels: Map<Int, Int>,
        val blockParams: Map<Int, Map<Int, Float>>,
        val loadedSlot: Int,
        val modified: Boolean
    )
}
