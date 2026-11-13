package com.pocketmaster.ai.provider

import com.pocketmaster.ai.data.RoomDatabase
import com.pocketmaster.ai.hardware.midi.CommandQueue
import com.pocketmaster.ai.hardware.midi.PocketMasterMidiManager
import com.pocketmaster.ai.hardware.midi.fake.FakePocketMasterDevice
import com.pocketmaster.ai.model.DeviceMapRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates the autonomous tone agent:
 * - Streams LLM text and extracts tool calls
 * - Runs the 3-step song/solo pipeline (search -> map to device_map.json -> execute)
 * - Backs up the edit buffer to Room before any destructive load
 * - Persists conversation history and offers Undo Tone in chat
 */
class AgentOrchestrator(
    private val provider: LlmProvider,
    private val executor: AgentToolExecutor,
    private val chatDao: RoomDatabase.ChatDao,
    private val deviceMapRepo: DeviceMapRepository,
    private val fakeDevice: FakePocketMasterDevice? = null,
    private val scope: CoroutineScope
) {

    /** Current chat session id (0 = new). */
    private var sessionId: Long = 0

    private val _uiState = MutableStateFlow(OrchestratorUiState())
    val uiState: StateFlow<OrchestratorUiState> = _uiState.asStateFlow()

    /** Start or switch to a chat session. */
    suspend fun startSession(name: String): Long {
        sessionId = chatDao.insertSession(ChatSession(name = name, createdAt = System.currentTimeMillis(), providerSnapshot = ""))
        _uiState.value = _uiState.value.copy(sessionId = sessionId, status = "Ready")
        return sessionId
    }

    /** Add a user message and run the full loop until completion. */
    suspend fun sendMessage(userText: String, onMessage: (ChatMessageUi) -> Unit) {
        if (sessionId == 0L) startSession("New tone session")
        chatDao.insertMessage(
            ChatMessage(sessionId = sessionId, role = "user", content = userText, timestamp = System.currentTimeMillis(), toolCalls = "")
        )
        scope.launch {
            try {
                provider.init()
                streamLoop(userText, onMessage)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(status = "Error: ${e.message}", toolStatus = "")
                onMessage(ChatMessageUi(role = "assistant", content = "Error: ${e.message}"))
            }
        }
    }

    /** Undo the last ToneSnapshot (revert pedalboard). */
    suspend fun undoTone(): Boolean {
        val snapshot = chatDao.getLatestSnapshot(sessionId).firstOrNull()
        if (snapshot == null) return false
        val dev = fakeDevice ?: return false
        val chainArr = snapshot.chain.split(",").map { it.toInt() }.toIntArray()
        if (dev.reorderChain(chainArr)) {
            snapshot.blockStates?.split(",")?.forEachIndexed { i, part ->
                dev.setBlockState(i, part == "1")
            }
            return true
        }
        return false
    }

    private suspend fun streamLoop(userText: String, onMessage: (ChatMessageUi) -> Unit) {
        val messages = buildConversationHistory()
        val toolsJson = AgentToolDefinitions.toolsJsonSchema
        var accumulated = StringBuilder()
        var toolCalled = false

        provider.streamChat(
            messages = messages,
            tools = toolsJson,
            toolsMode = ToolMode.AUTO,
            onText = { text ->
                accumulated.append(text)
                onMessage(ChatMessageUi(role = "assistant", content = accumulated.toString(), toolStatus = uiState.value.toolStatus))
            },
            onToolCall = { call ->
                toolCalled = true
                val result = executor.execute(call)
                _uiState.value = _uiState.value.copy(toolStatus = result.error ?: "Executed: ${call}")
                if (result.error != null) {
                    accumulated.append("\n[Tool error: ${result.error}]")
                } else {
                    accumulated.append("\n[Tool applied: $call]")
                }
            },
            onComplete = { finalText, hasTool ->
                accumulated.append(finalText)
                chatDao.insertMessage(
                    ChatMessage(sessionId = sessionId, role = "assistant", content = accumulated.toString(), timestamp = System.currentTimeMillis(), toolCalls = if (toolCalled) "1" else "0")
                )
                _uiState.value = _uiState.value.copy(status = "Done", toolStatus = "", assistantText = accumulated.toString())
                onMessage(ChatMessageUi(role = "assistant", content = accumulated.toString()))
            },
            onError = { error ->
                _uiState.value = _uiState.value.copy(status = "Error: $error", toolStatus = "")
                onMessage(ChatMessageUi(role = "assistant", content = "Error: $error"))
            }
        )
    }

    private fun buildConversationHistory(): List<ToolMessage> {
        val msgs = chatDao.getMessages(sessionId).firstOrNull() ?: emptyList()
        return buildList {
            add(ToolMessage("system", "You are an autonomous guitar tone agent for the Sonicake Pocket Master (QME-10). You have 13 tools. When the user asks for a song/solo tone, use web_search first, map models to device_map.json exact names, then execute set_block_model/set_parameter/reorder_signal_chain. Never hallucinate model names or out-of-range parameter values."))
            for (m in msgs) {
                add(ToolMessage(role = if (m.toolCalls == "1") "assistant" else m.role, content = m.content))
            }
        }
    }

    data class OrchestratorUiState(
        val sessionId: Long = 0,
        val status: String = "Ready",
        val assistantText: String = "",
        val toolStatus: String = "",
        val hasSnapshot: Boolean = false
    )
}

data class ChatMessageUi(
    val role: String,
    val content: String,
    val toolStatus: String = ""
)
