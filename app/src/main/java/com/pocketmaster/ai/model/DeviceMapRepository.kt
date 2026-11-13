package com.pocketmaster.ai.model

import android.content.Context
import com.pocketmaster.ai.midi.PocketMasterProtocol
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses and validates against src/pocket_master_mcp/data/device_map.json (native Kotlin only).
 * Supports code -> Model lookup, name + module lookup, and per-parameter
 * range/type/enum validation with descriptive error messages.
 */
class DeviceMapRepository private constructor(private val context: Context) {

    private val models: MutableMap<String, Model> = mutableMapOf()
    private val codeIndex: MutableMap<Int, Model> = mutableMapOf()
    private val nameModuleIndex: MutableMap<String, MutableSet<String>> = mutableMapOf()
    private val blockIndex: MutableMap<String, Int> = mutableMapOf()

    companion object {
        fun create(context: Context): DeviceMapRepository {
            val repo = DeviceMapRepository(context)
            return repo
        }
    }

    private fun loadDeviceMap() {
        val jsonText = runCatching {
            context.assets.open("device_map.json").bufferedReader().use { it.readText() }
        }.getOrElse { return }
        val root = try {
            Json { ignoreUnknownKeys = true }.parseToJsonElement(jsonText).jsonObject
        } catch (e: Exception) {
            return
        }

        root["models"]?.jsonArray?.forEach { modelElem ->
            val model = modelElem.jsonObject.toModel()
            model.code?.let { code ->
                try {
                    codeIndex[code.toInt()] = model
                } catch (e: Exception) {
                    // skip invalid code
                }
            }
            models[model.name] = model
            model.block?.let { blockIndex[it] = model.block.toIntOrNull() ?: 0 }
            nameModuleIndex.getOrPut(model.name) { mutableSetOf() }.add(model.module ?: "")
        }
    }

    private fun kotlinx.serialization.json.JsonObject.toModel(): Model {
        val name = this["name"]?.jsonPrimitive?.content
        val code = this["code"]?.jsonPrimitive?.content
        val group = this["group"]?.jsonPrimitive?.content
        val module = this["module"]?.jsonPrimitive?.content
        val block = this["block"]?.jsonPrimitive?.content
        val onOff = this["onOff"]?.jsonPrimitive?.boolean
        val params = this["params"]?.jsonArray?.mapNotNull { it.jsonObject.toParameter() }.orEmpty()
        val modes = this["modes"]?.jsonObject
        return Model(name, code, group, module, block, onOff, params, modes)
    }

    private fun kotlinx.serialization.json.JsonObject.toParameter(): Parameter? {
        val key = this["key"]?.jsonPrimitive?.content ?: return null
        val type = this["type"]?.jsonPrimitive?.content ?: return null
        val min = this["min"]?.jsonPrimitive?.double
        val max = this["max"]?.jsonPrimitive?.double
        val step = this["step"]?.jsonPrimitive?.double
        val default = this["default"]?.jsonPrimitive?.double
        val enum = this["enum"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        return Parameter(key, type, min, max, step, default, enum)
    }

    /** Lookup a model by exact model code (category << 24 | id). */
    fun lookup(code: Int): Model? = codeIndex[code]

    /** Lookup a model by name + module. */
    fun lookup(name: String, module: String?): Model? =
        models.firstOrNull { it.value.name == name && (module == null || it.value.module == module) }

    /** Get model at a chain position. */
    fun getBlockModel(pos: Int): Model? = models.values.firstOrNull { it.block == pos }

    /** Validate a parameter value for a model; returns an error message or null. */
    fun validateParam(modelName: String, paramKey: String, value: Double): String? {
        val model = models[name] ?: return "Unknown model: $modelName"
        val param = model.params.find { it.key == paramKey } ?: return "Unknown param $paramKey"

        when (param.type) {
            "int", "integer" -> {
                val vi = value.toInt()
                if (vi != value) return "$paramKey must be an integer"
                if (param.min != null && vi < param.min.toInt()) return "$paramKey $vi below min ${param.min.toInt()}"
                if (param.max != null && vi > param.max.toInt()) return "$paramKey $vi above max ${param.max.toInt()}"
            }
            "float", "number" -> {
                if (param.min != null && value < param.min!!) return "$paramKey $value below min ${param.min}"
                if (param.max != null && value > param.max!!) return "$paramKey $value above max ${param.max}"
            }
            "enum" -> {
                if (param.enum == null) return "$paramKey enum not defined"
                if (!param.enum.contains(value.toString())) return "$paramKey value must be one of ${param.enum.joinToString()}"
            }
        }
        return null
    }

    /** Clamp a value into the model param range. */
    fun clampValue(modelName: String, paramKey: String, value: Double): Double {
        val model = models[name] ?: return value
        val param = model.params.find { it.key == paramKey } ?: return value
        var v = value
        if (param.min != null) v = maxOf(v, param.min!!)
        if (param.max != null) v = minOf(v, param.max!!)
        return v
    }

    /** Get parameter keys for a model. */
    fun paramKeys(modelName: String): List<String> = models[name]?.params?.map { it.key }.orEmpty()

    /** Get parameter step for a model (quantization). */
    fun paramStep(modelName: String, paramKey: String): Double? =
        models[name]?.params?.find { it.key == paramKey }?.step

    /** All model names. */
    fun modelNames(): List<String> = models.keys.toList()

    /** Validate chain for fixed DRV-AMP-IR-EQ block (indices 2-5). */
    fun validateChain(chain: IntArray): Boolean = PocketMasterProtocol.validateChain(chain)

    data class Model(
        val name: String?,
        val code: String?,
        val group: String?,
        val module: String?,
        val block: String?,
        val onOff: Boolean?,
        val params: List<Parameter>,
        val modes: Map<String, String>?
    )

    data class Parameter(
        val key: String,
        val type: String,
        val min: Double?,
        val max: Double?,
        val step: Double?,
        val default: Double?,
        val enum: List<String>?
    )
}

private inline fun <T> Iterable<T>.firstOrNull(predicate: (T) -> Boolean): T? =
    filter(predicate).firstOrNull()
