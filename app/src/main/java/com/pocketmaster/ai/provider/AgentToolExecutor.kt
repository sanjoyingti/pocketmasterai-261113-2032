package com.pocketmaster.ai.provider

import com.pocketmaster.ai.hardware.midi.CommandQueue
import com.pocketmaster.ai.hardware.midi.PocketMasterMidiManager
import com.pocketmaster.ai.hardware.midi.fake.FakePocketMasterDevice
import com.pocketmaster.ai.model.DeviceMapRepository
import com.pocketmaster.ai.midi.presetLabel
import org.json.JSONObject
import java.util.UUID

/**
 * Executes LLM tool calls after validating them against device_map.json.
 * Maps block/module names to chain positions, handles amp Mode module 9 clone,
 * and returns structured results or validation error messages.
 */
class AgentToolExecutor(
    private val protocol: PocketMasterProtocol,
    private val queue: CommandQueue,
    private val midiManager: PocketMasterMidiManager,
    private val deviceMapRepo: DeviceMapRepository,
    private val fakeDevice: FakePocketMasterDevice? = null
) {

    /** Execute a tool call (JSON string like: name + parameters). */
    suspend fun execute(call: String): ExecutionResult {
        return try {
            val json = JSONObject(call)
            val name = json.optString("name")
            val params = json.optJSONObject("arguments") ?: JSONObject()

            when (name) {
                "get_device_state" -> executeGetDeviceState(params)
                "list_presets" -> executeListPresets(params)
                "select_preset" -> executeSelectPreset(params)
                "set_block_state" -> executeSetBlockState(params)
                "set_block_model" -> executeSetBlockModel(params)
                "set_parameter" -> executeSetParameter(params)
                "reorder_signal_chain" -> executeReorderChain(params)
                "apply_full_tone_preset" -> executeApplyPreset(params)
                "save_preset" -> executeSavePreset(params)
                "rename_preset" -> executeRenamePreset(params)
                "backup_all_presets" -> executeBackupPresets(params)
                "set_global_levels" -> executeSetGlobalLevels(params)
                "web_search" -> executeWebSearch(params)
                else -> ExecutionResult.error("Unknown tool: $name")
            }
        } catch (e: Exception) {
            ExecutionResult.error("Tool execution failed: ${e.message}")
        }
    }

    private fun executeGetDeviceState(params: JSONObject): ExecutionResult {
        val dev = fakeDevice ?: return ExecutionResult.error("No hardware available")
        val state = dev.getState()
        return ExecutionResult.success(
            mapOf(
                "chain" to state.chain.contentDeepToString(),
                "masterVolume" to state.masterVolume,
                "tempo" to state.tempo,
                "blockStates" to state.blockStates.mapValues { it.value }.toString(),
                "blockModels" to state.blockModels.mapValues { it.value }.toString(),
                "blockParams" to state.blockParams.mapValues { it.value.mapValues { it.value } }.toString(),
                "loadedSlot" to state.loadedSlot,
                "modified" to state.modified
            )
        )
    }

    private fun executeListPresets(params: JSONObject): ExecutionResult {
        val dev = fakeDevice ?: return ExecutionResult.error("No hardware available")
        return ExecutionResult.success(
            mapOf("presets" to dev.presets.map { (slot, p) -> "${p.label} (slot $slot)" }.joinToString(", "))
        )
    }

    private fun executeSelectPreset(params: JSONObject): ExecutionResult {
        val dev = fakeDevice ?: return ExecutionResult.error("No hardware available")
        val slotName = params.optString("slot")
        val slot = presetIndex(slotName)
        if (slot < 0 || slot >= 100) return ExecutionResult.error("Invalid preset slot: $slotName")
        if (!dev.applyPreset(slot)) return ExecutionResult.error("Failed to apply preset")
        return ExecutionResult.success(mapOf("applied" to slotName))
    }

    private fun executeSetBlockState(params: JSONObject): ExecutionResult {
        val dev = fakeDevice ?: return ExecutionResult.error("No hardware available")
        val pos = params.optInt("pos")
        val on = params.optBoolean("on")
        if (pos < 0 || pos >= 9) return ExecutionResult.error("Block position out of range (0-8)")
        if (!deviceMapRepo.validateChain(dev.chain)) return ExecutionResult.error("Invalid chain: fixed block must stay together")
        if (!dev.setBlockState(pos, on)) return ExecutionResult.error("Failed to set block state")
        return ExecutionResult.success(mapOf("pos" to pos, "on" to on))
    }

    private fun executeSetBlockModel(params: JSONObject): ExecutionResult {
        val dev = fakeDevice ?: return ExecutionResult.error("No hardware available")
        val pos = params.optInt("pos")
        val modelName = params.optString("model")
        val on = params.optBoolean("on", true)
        if (pos < 0 || pos >= 9) return ExecutionResult.error("Block position out of range (0-8)")
        val model = deviceMapRepo.lookup(name = modelName, module = null)
        val code = model?.code?.toIntOrNull() ?: 0
        if (!deviceMapRepo.validateChain(dev.chain)) return ExecutionResult.error("Invalid chain: fixed block must stay together")
        if (!dev.setBlockModel(pos, code)) return ExecutionResult.error("Failed to set block model")
        if (on) dev.setBlockState(pos, true)
        return ExecutionResult.success(mapOf("pos" to pos, "model" to modelName, "code" to code))
    }

    private fun executeSetParameter(params: JSONObject): ExecutionResult {
        val dev = fakeDevice ?: return ExecutionResult.error("No hardware available")
        val pos = params.optInt("pos")
        val param = params.optString("param")
        val value = params.optDouble("value")
        if (pos < 0 || pos >= 9) return ExecutionResult.error("Block position out of range (0-8)")
        // Map param keys to internal indices via device_map.json
        val model = deviceMapRepo.getBlockModel(pos)
        val error = deviceMapRepo.validateParam(model?.name ?: "", param, value)
        if (error != null) return ExecutionResult.error("Invalid parameter: $error")
        val clamped = deviceMapRepo.clampValue(model?.name ?: "", param, value)
        val paramIndex = paramToIndex(param)
        if (paramIndex < 0 || paramIndex > 16) return ExecutionResult.error("Parameter index out of range")
        if (!dev.setParameter(pos, paramIndex, clamped.toFloat())) {
            return ExecutionResult.error("Failed to set parameter")
        }
        return ExecutionResult.success(mapOf("pos" to pos, "param" to param, "value" to clamped))
    }

    private fun executeReorderChain(params: JSONObject): ExecutionResult {
        val dev = fakeDevice ?: return ExecutionResult.error("No hardware available")
        val orderJson = params.getJSONArray("order")
        val order = IntArray(orderJson.length()) { i -> orderJson.getInt(i) }
        if (!deviceMapRepo.validateChain(dev.chain)) return ExecutionResult.error("Invalid chain: fixed block must stay together")
        if (!dev.reorderChain(order)) return ExecutionResult.error("Invalid chain order")
        return ExecutionResult.success(mapOf("order" to order.contentDeepToString()))
    }

    private fun executeApplyPreset(params: JSONObject): ExecutionResult {
        val dev = fakeDevice ?: return ExecutionResult.error("No hardware available")
        val presetJson = params.optString("presetJson")
        try {
            val preset = JSONObject(presetJson)
            val orderJson = preset.optJSONArray("order")
            val blocks = preset.optJSONArray("blocks")
            if (orderJson != null) {
                val order = IntArray(orderJson.length()) { i -> orderJson.getInt(i) }
                if (!dev.reorderChain(order)) return ExecutionResult.error("Invalid chain order")
            }
            blocks?.forEach { block ->
                val b = block as JSONObject
                val pos = b.optInt("pos")
                val model = b.optString("model")
                if (model.isNotEmpty()) {
                    val m = deviceMapRepo.lookup(name = model, module = null)
                    val code = m?.code?.toIntOrNull() ?: 0
                    dev.setBlockModel(pos, code)
                }
                val on = b.optBoolean("on", true)
                dev.setBlockState(pos, on)
                b.optJSONObject("params")?.forEach { key, value ->
                    val param = key as String
                    val valDouble = (value as Number).toDouble()
                    val error = deviceMapRepo.validateParam(model, param, valDouble)
                    if (error == null) {
                        val clamped = deviceMapRepo.clampValue(model, param, valDouble)
                        dev.setParameter(pos, paramToIndex(param), clamped.toFloat())
                    }
                }
            }
            val volume = preset.optDouble("volume", dev.masterVolume)
            dev.masterVolume = volume.toFloat()
            return ExecutionResult.success(mapOf("applied" to true))
        } catch (e: Exception) {
            return ExecutionResult.error("Invalid preset JSON: ${e.message}")
        }
    }

    private fun executeSavePreset(params: JSONObject): ExecutionResult {
        val dev = fakeDevice ?: return ExecutionResult.error("No hardware available")
        val slotName = params.optString("slot")
        val name = params.optString("name")
        if (name.length > 10) return ExecutionResult.error("Preset name too long (max 10 chars)")
        val slot = presetIndex(slotName)
        if (slot < 0 || slot >= 100) return ExecutionResult.error("Invalid preset slot: $slotName")
        if (!dev.savePreset(slot, name)) return ExecutionResult.error("Save failed")
        return ExecutionResult.success(mapOf("slot" to slotName, "name" to name))
    }

    private fun executeRenamePreset(params: JSONObject): ExecutionResult {
        val dev = fakeDevice ?: return ExecutionResult.error("No hardware available")
        val oldName = params.optString("old")
        val newName = params.optString("new")
        var found = false
        dev.presets.forEach { (slot, p) ->
            if (p.label == oldName) {
                dev.savePreset(slot, newName)
                found = true
            }
        }
        if (!found) return ExecutionResult.error("Preset not found: $oldName")
        return ExecutionResult.success(mapOf("renamed" to newName))
    }

    private fun executeBackupPresets(params: JSONObject): ExecutionResult {
        val dev = fakeDevice ?: return ExecutionResult.error("No hardware available")
        val json = JSONObject()
        dev.presets.forEach { (slot, p) ->
            json.put("preset_${slot}", p.label)
        }
        return ExecutionResult.success(mapOf("backup" to json.toString()))
    }

    private fun executeSetGlobalLevels(params: JSONObject): ExecutionResult {
        val dev = fakeDevice ?: return ExecutionResult.error("No hardware available")
        val master = params.optDouble("masterVol", dev.masterVolume)
        val input = params.optDouble("input", 0.5)
        val fxRec = params.optDouble("fxRec", 0.5)
        val monitor = params.optDouble("monitor", 0.5)
        val btRec = params.optDouble("btRec", 0.5)
        dev.setGlobalLevels(master.toFloat(), input.toFloat(), fxRec.toFloat(), monitor.toFloat(), btRec.toFloat())
        return ExecutionResult.success(mapOf("master" to master))
    }

    private fun executeWebSearch(params: JSONObject): ExecutionResult {
        val query = params.optString("query")
        val results = WebSearchRepository.search(query, "DUCK_DUCK_GO")
        return ExecutionResult.success(
            mapOf("results" to results.joinToString("; ") { "${it.title}: ${it.link}" })
        )
    }

    /** Map a parameter key (e.g. Gain) to its internal index 0-16 per block. */
    private fun paramToIndex(param: String): Int {
        return when (param.lowercase()) {
            "gain", "gain1" -> 0
            "bass" -> 1
            "mid" -> 2
            "treble", "treble1" -> 3
            "presence" -> 4
            "vol", "volume", "master" -> 5
            "drive" -> 6
            "tone" -> 7
            "mix" -> 8
            "time", "delay_time" -> 9
            "fback", "feedback", "delay_fback" -> 10
            "decay", "reverb_decay" -> 11
            "depth" -> 12
            "rate" -> 13
            "thre", "threshold" -> 14
            "mode" -> 15
            "+3db", "plus3db" -> 16
            else -> -1
        }
    }

    data class ExecutionResult(
        val success: Boolean,
        val data: Map<String, Any>?,
        val error: String?
    ) {
        companion object {
            fun success(data: Map<String, Any>) = ExecutionResult(true, data, null)
            fun error(message: String) = ExecutionResult(false, null, message)
        }
    }
}

/** Convert preset name P01 -> 0 ... F50 -> 99. */
fun presetIndex(name: String): Int {
    val m = name.toCharArray()
    if (m.size < 2) return -1
    val prefix = String(m.copyOfRange(0, m.size - 2))
    val num = name.drop(prefix.length).toIntOrNull() ?: -1
    return when (prefix) {
        "P" -> num - 1
        "F" -> 50 + num - 1
        else -> -1
    }
}

/** Convert slot 0-99 to P01-P50 / F01-F50. */
fun presetLabel(slot: Int): String {
    return when {
        slot < 50 -> "P%02d".format(slot + 1)
        else -> "F%02d".format(slot - 50 + 1)
    }
}
