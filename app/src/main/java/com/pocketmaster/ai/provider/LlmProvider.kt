package com.pocketmaster.ai.provider

/**
 * Unified contract for any LLM backend (Gemini OAuth, Gemini API key,
 * or OpenAI-compatible custom endpoints such as DeepSeek).
 * All tool-calling and streaming is abstracted behind one interface so the
 * AgentOrchestrator never cares which backend is in use.
 */
interface LlmProvider {

    /** Initialize the provider (e.g. refresh OAuth tokens, validate API key). */
    suspend fun init()

    /** Stream a chat response.
     *  - messages: full conversation history
     *  - tools: JSON Schema tool definitions the model may call
     *  - toolsMode: AUTO | REQUIRED | NONE
     *  - onText: incremental raw text fragments
     *  - onToolCall: a tool the model wants to invoke
     *  - onComplete: final accumulated text + whether a tool was called
     *  - onError: any failure
     */
    suspend fun streamChat(
        messages: List<ToolMessage>,
        tools: String,
        toolsMode: ToolMode,
        onText: (String) -> Unit,
        onToolCall: (String) -> Unit,
        onComplete: (String, Boolean) -> Unit,
        onError: (String) -> Unit
    )

    /** Supported models for the UI model selector. */
    suspend fun listModels(): List<String>
}

enum class ToolMode { AUTO, REQUIRED, NONE }

data class ToolMessage(
    val role: String,
    val content: String
)
