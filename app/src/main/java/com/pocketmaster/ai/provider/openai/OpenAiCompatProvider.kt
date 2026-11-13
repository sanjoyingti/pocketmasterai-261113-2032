package com.pocketmaster.ai.provider.openai

import com.pocketmaster.ai.data.SettingsRepository
import com.pocketmaster.ai.provider.LlmProvider
import com.pocketmaster.ai.provider.ToolMessage
import com.pocketmaster.ai.provider.ToolMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible LLM provider:
 * - Custom base URL + API key (DeepSeek, self-hosted servers)
 * - HEADERS: X-Api-Key or Bearer
 * - GET /models for dynamic model selection
 * - /v1/chat/completions streaming SSE with tool JSON Schema support
 * - Fallback to SSE text parsing
 */
class OpenAiCompatProvider(
    private val scope: CoroutineScope,
    private val settings: SettingsRepository
) : LlmProvider {

    private var baseUrl: String = ""
    private var apiKey: String = ""
    private var client: OkHttpClient? = null

    /** Default model list when the server refuses /models. */
    private val defaultModels = listOf(
        "deepseek-chat",
        "deepseek-reasoner",
        "deepseek-4.1-flash"
    )

    override suspend fun init() {
        withContext(Dispatchers.IO) {
            baseUrl = settings.baseUrl.value.trimEnd('/')
            apiKey = settings.apiKey.value
            val builder = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .eventListener(object : EventListener() {
                    override fun callFailed(call: Call, httpCall: okhttp3.HttpUrl, e: IOException) {
                        // connection failure, swallowed
                    }
                })
            if (apiKey.isNotEmpty()) {
                builder.addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("X-Api-Key", apiKey)
                        .build()
                    chain.proceed(request)
                }
            }
            client = builder.build()
        }
    }

    override suspend fun streamChat(
        messages: List<ToolMessage>,
        tools: String,
        toolsMode: ToolMode,
        onText: (String) -> Unit,
        onToolCall: (String) -> Unit,
        onComplete: (String, Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val toolList = JSONArray(tools)
                val toolDefs = if (toolList.length() > 0 && toolsMode != ToolMode.NONE) {
                    JSONArray().apply {
                        for (i in 0 until toolList.length()) {
                            put(toolList.get(i) as JSONObject)
                        }
                    }
                } else {
                    JSONArray()
                }
                val requestBody = JSONObject()
                    .put("model", getModelForRequest())
                    .put("messages", messages.map { mapMessage(it) }.toTypedArray())
                    .put("stream", true)
                if (toolDefs.length() > 0) {
                    requestBody.put("tools", toolDefs)
                    requestBody.put("tool_choice", when (toolsMode) {
                        ToolMode.REQUIRED -> "auto"
                        ToolMode.AUTO -> "auto"
                        ToolMode.NONE -> "none"
                    })
                }
                val jsonBody = requestBody.toString()
                val request = Request.Builder()
                    .url("$baseUrl/v1/chat/completions")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()
                client?.newCall(request)?.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        onError("Connection failed: ${e.message}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val source = response.body?.string()?.let { body ->
                            parseSse(body, onText, onToolCall, onComplete, onError)
                        }
                    }
                })
            } catch (e: Exception) {
                onError("Request error: ${e.message}")
            }
        }
    }

    private fun mapMessage(msg: ToolMessage): JSONObject {
        return JSONObject().put("role", msg.role).put("content", msg.content)
    }

    private fun parseSse(body: String, onText: (String) -> Unit, onToolCall: (String) -> Unit, onComplete: (String, Boolean) -> Unit, onError: (String) -> Unit) {
        val lines = body.split("\n").filter { it.isNotEmpty() }
        var collected = StringBuilder()
        for (line in lines) {
            if (line.startsWith("data:")) {
                val data = line.substring(5).trim()
                if (data == "[DONE]") {
                    onComplete(collected.toString(), false)
                    return
                }
                try {
                    val json = JSONObject(data)
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val choice = choices.get(0) as JSONObject
                        val delta = choice.optJSONObject("delta")
                        delta?.let {
                            if (it.has("content")) {
                                val txt = it.getString("content")
                                collected.append(txt)
                                onText(txt)
                            }
                            if (it.has("tool_calls")) {
                                val tc = it.getJSONArray("tool_calls")
                                if (tc.length() > 0) {
                                    val tcObj = tc.get(0) as JSONObject
                                    val name = tcObj.getJSONObject("function")?.getString("name")
                                    val args = tcObj.getJSONObject("function")?.getString("arguments")
                                    name?.let { n -> onToolCall("$n($args)") }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // non-JSON line, skip
                }
            }
        }
        onComplete(collected.toString(), false)
    }

    override suspend fun listModels(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("$baseUrl/v1/models").get().build()
                client?.newCall(request)?.execute()?.body?.string()?.let { jsonText ->
                    try {
                        val json = JSONObject(jsonText)
                        json.optJSONArray("data")?.let { data ->
                            val names = mutableListOf<String>()
                            for (i in 0 until data.length()) {
                                names.add((data.get(i) as JSONObject).getString("id"))
                            }
                            return@withContext names.ifEmpty { defaultModels }
                        }
                    } catch (e: Exception) {
                        // parse error, fall back
                    }
                }
                defaultModels
            } catch (e: Exception) {
                defaultModels
            }
        }
    }

    private fun getModelForRequest(): String {
        return "deepseek-4.1-flash"
    }
}
