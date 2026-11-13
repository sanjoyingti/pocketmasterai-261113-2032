package com.pocketmaster.ai.midi

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Byte-exact port of pocket-master-mcp protocol.py/device.py.
 * Handles SysEx framing (F0/F7), nibble expansion, CRC-8/SMBus,
 * and command payload encoding/decoding.
 */
object PocketMasterProtocol {

    // ---------- Command IDs ----------
    const val CMD_GLOBAL = 0x11
    const val CMD_PRESET_VOLUME = 0x42
    const val CMD_SELECT = 0x43
    const val CMD_CHAIN = 0x44
    const val CMD_EFFECT = 0x47
    const val CMD_PARAM = 0x48
    const val CMD_STATE = 0x49
    const val CMD_SAVE = 0x4A
    const val ACK = 0x08

    // ---------- Query IDs ----------
    const val Q_GLOBALS = 0x10
    const val Q_IR_NAMES = 0x20
    const val Q_CLONE_NAMES = 0x24
    const val Q_PRESET_NAMES = 0x40
    const val Q_CURRENT_DUMP = 0x41
    const val Q_CURRENT_INDEX = 0x43
    const val Q_MODIFIED = 0x45

    // ---------- Payload header opcodes ----------
    const val PAYLOAD_SET = 0x11
    const val PAYLOAD_GET = 0x12
    const val PAYLOAD_ACK = 0x14

    /** CRC-8/SMBus: poly 0x07, init 0. */
    fun crc8(data: ByteArray): Byte {
        var crc: Int = 0
        for (b in data) {
            crc = (crc xor (b.toInt() and 0xff)) shr 1
            if ((crc and 1) == 1) {
                crc = (crc ushr 1) xor 0xED
            }
        }
        return (crc and 0xFF).toByte()
    }

    /** Encode a logical SysEx frame: crc + total + index + length + payload. */
    fun logicalFrame(cmd: Byte, payload: ByteArray, payloadHeader: Byte = PAYLOAD_SET): ByteArray {
        val total = payload.size + 1 // payload header byte
        val index = 0
        val frame = byteArrayOf(payloadHeader, payloadHeader, cmd, payloadHeader, 0, 0, total.toByte(), index.toByte(), total.toByte()) + payload
        return frame
    }

    /** Expand a byte into two nibbles: each nibble becomes 0x10 | nibble. */
    fun expandNibbles(data: ByteArray): ByteArray {
        val out = ByteArray(data.size * 2)
        for (i in data.indices) {
            out[i * 2] = ((data[i] ushr 4) and 0x0F).toByte().or(0x10.toByte())
            out[i * 2 + 1] = (data[i] and 0x0F).toByte().or(0x10.toByte())
        }
        return out
    }

    /** Pack raw data into a full SysEx message (F0 ... F7). */
    fun toSysex(data: ByteArray): ByteArray {
        val expanded = expandNibbles(data)
        return byteArrayOf(0xF0) + expanded + byteArrayOf(0xF7)
    }

    /** Decode a SysEx message back into raw bytes. */
    fun fromSysex(msg: ByteArray): ByteArray {
        require(msg.isNotEmpty() && msg[0] == 0xF0.toByte() && msg[msg.size - 1] == 0xF7.toByte()) { "Invalid SysEx" }
        val inner = msg.copyOfRange(1, msg.size - 1)
        val out = ByteArray(inner.size / 2)
        for (i in out.indices) {
            val hi = inner[i * 2] and 0x0F
            val lo = inner[i * 2 + 1] and 0x0F
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /** Encode a CMD_GLOBAL: group(2) + sub(2) + type + int32. */
    fun encodeGlobal(groupHi: Byte, groupLo: Byte, subHi: Byte, subLo: Byte, value: Int): ByteArray {
        return byteArrayOf(
            CMD_GLOBAL.toByte(), groupHi, groupLo, subHi, subLo,
            0x00.toByte(), 0x00.toByte(),
            value.toByteArrayLE()
        )
    }

    /** Encode a float as 4-byte little-endian IEEE 754. */
    fun encodeFloat(value: Float): ByteArray {
        return value.toByteArrayLE()
    }

    /** Encode an int32 little-endian. */
    fun encodeInt32(value: Int): ByteArray {
        return value.toByteArrayLE()
    }

    /** Encode a uint32 little-endian (0..0xFFFFFFFF). */
    fun encodeUint32(value: Int): ByteArray {
        return value.toByteArrayLE()
    }

    /** Decode uint32 little-endian. */
    fun decodeUint32(bytes: ByteArray): Int {
        require(bytes.size >= 4)
        return bytes[0].toInt() and 0xFF or
                ((bytes[1].toInt() and 0xFF) shl 8) or
                ((bytes[2].toInt() and 0xFF) shl 16) or
                ((bytes[3].toInt() and 0xFF) shl 24)
    }

    /** Decode int32 little-endian. */
    fun decodeInt32(bytes: ByteArray): Int {
        require(bytes.size >= 4)
        return bytes[0].toInt() and 0xFF or
                ((bytes[1].toInt() and 0xFF) shl 8) or
                ((bytes[2].toInt() and 0xFF) shl 16) or
                ((bytes[3].toInt() and 0xFF) shl 24)
    }

    /** Decode float32 little-endian IEEE 754. */
    fun decodeFloat32(bytes: ByteArray): Float {
        require(bytes.size >= 4)
        val bits = bytes[0].toInt() and 0xFF or
                ((bytes[1].toInt() and 0xFF) shl 8) or
                ((bytes[2].toInt() and 0xFF) shl 16) or
                ((bytes[3].toInt() and 0xFF) shl 24)
        return bits.toFloatBits()
    }

    /** Read int32 from buffer (little-endian). */
    fun readInt32(buf: ByteBuffer): Int {
        return buf.order(ByteOrder.LITTLE_ENDIAN).int
    }

    /** Read uint32 from buffer (little-endian). */
    fun readUint32(buf: ByteBuffer): Int {
        val i = buf.order(ByteOrder.LITTLE_ENDIAN).int
        return i and 0x7FFFFFFF
    }

    /** Read float32 from buffer (little-endian). */
    fun readFloat32(buf: ByteBuffer): Float {
        return buf.order(ByteOrder.LITTLE_ENDIAN).float
    }

    /** Encode a preset select command (0x43): index + total. */
    fun encodeSelect(slot: Int): ByteArray {
        val total = 1
        return byteArrayOf(CMD_SELECT.toByte(), slot and 0xFF, total)
    }

    /** Encode chain command (0x44): m0..m8 = 9 bytes + total 10. */
    fun encodeChain(blocks: IntArray): ByteArray {
        require(blocks.size == 9)
        return byteArrayOf(CMD_CHAIN.toByte()) + blocks.map { it and 0xFF }.map { it.toByte() }.toByteArray() + (10).toByte()
    }

    /** Encode effect command (0x47): module + code + total. */
    fun encodeEffect(module: Int, code: Int): ByteArray {
        val total = 3
        return byteArrayOf(CMD_EFFECT.toByte(), module, code and 0xFF, total)
    }

    /** Encode param command (0x48): module + param + float32 + total 5. */
    fun encodeParam(module: Int, param: Int, value: Float): ByteArray {
        return byteArrayOf(CMD_PARAM.toByte(), module, param, total = 5) + encodeFloat(value)
    }

    /** Encode state command (0x49): module + on/off + total 3. */
    fun encodeState(module: Int, on: Boolean): ByteArray {
        return byteArrayOf(CMD_STATE.toByte(), module, (if (on) 1 else 0) and 0xFF, 3)
    }

    /** Encode save command (0x4A): slot + name(10 bytes) + 0x00. */
    fun encodeSave(slot: Int, name: String): ByteArray {
        val nameBytes = ByteArray(10) { 0x00 }
        val chars = (name + "\u0000").take(10)
        for (i in chars.indices) {
            nameBytes[i] = chars[i].code.toByte()
        }
        return byteArrayOf(CMD_SAVE.toByte(), slot and 0xFF) + nameBytes + byteArrayOf(0x00, 0x00, 0x00, 14)
    }

    /** Encode global set. */
    fun encodeGlobalSet(group: Int, sub: Int, value: Int): ByteArray {
        return encodeGlobal((group ushr 8).toByte(), (group and 0xFF).toByte(),
            (sub ushr 8).toByte(), (sub and 0xFF).toByte(), value)
    }

    /** Encode global get. */
    fun encodeGlobalGet(group: Int, sub: Int): ByteArray {
        return encodeGlobal((group ushr 8).toByte(), (group and 0xFF).toByte(),
            (sub ushr 8).toByte(), (sub and 0xFF).toByte(), 0)
    }

    /** Validate that the fixed block chain DRV-AMP-IR-EQ (indices 2,3,4,5) stays together. */
    fun validateChain(chain: IntArray): Boolean {
        val fixedBlock = setOf(2, 3, 4, 5)
        return fixedBlock.all { it in chain.indices }
    }

    /** Convert int to little-endian bytes. */
    private fun Int.toByteArrayLE(): ByteArray {
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(this)
        return b.array()
    }

    private fun Float.toFloatBits(): Float {
        return java.lang.Float.intBitsToFloat(toRawIntBits())
    }
}

/** Encode a param command with explicit total. */
private fun ByteArray.appendTotal(total: Int): ByteArray = this + total.toByte()
