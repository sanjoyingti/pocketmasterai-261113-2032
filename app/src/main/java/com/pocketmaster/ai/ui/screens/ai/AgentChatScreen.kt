package com.pocketmaster.ai.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketmaster.ai.AppContainer
import com.pocketmaster.ai.provider.AgentOrchestrator
import com.pocketmaster.ai.provider.ChatMessageUi

/**
 * Scope holder so the orchestrator can launch coroutines — defined before use.
 */
private val orchestratorScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)

/**
 * AI Agent chat screen:
 * - streaming markdown messages (user / assistant)
 * - tool execution status pill chips
 * - Undo Tone button
 * - provider status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(container: AppContainer) {
    val orchestrator = remember {
        AgentOrchestrator(
            container.llmProvider,
            container.toolExecutor,
            container.chatDao,
            container.deviceMapRepo,
            container.fakeDevice,
            orchestratorScope
        )
    }
    var message by remember { mutableStateOf("") }
    var messages by remember { mutableStateListOf<ChatMessageUi>() }

    LaunchedEffect(Unit) {
        orchestrator.startSession("Tone session 1")
    }

    val uiState by orchestrator.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Tone Agent") },
                colors = TopAppBarDefaults.topAppBarColors(container = MaterialTheme.colorScheme)
            )
        },
        floatingActionButton = {
            if (uiState.hasSnapshot) {
                ExtendedFloatingActionButton(
                    onClick = {
                        orchestrator.undoTone()
                    },
                    text = { Text("Undo Tone") },
                    icon = { androidx.compose.material.icons.Icons.Default.Undo },
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { m ->
                    MessageBubble(m)
                }
            }
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Describe a tone: 'Sultans of Swing clean'") },
                    singleLine = true
                )
                Button(onClick = {
                    if (message.isNotBlank()) {
                        messages.add(ChatMessageUi(role = "user", content = message))
                        orchestrator.sendMessage(message, onMessage = { newMsg ->
                            messages.add(newMsg)
                        })
                        message = ""
                    }
                }) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(m: ChatMessageUi) {
    val isUser = m.role == "user"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isUser) Modifier.align(Alignment.End) else Modifier.align(Alignment.Start)),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = m.content.replace("*", ""),
                modifier = Modifier.padding(12.dp),
                fontSize = 13.sp,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
        if (m.toolStatus.isNotBlank()) {
            ToolStatusPill(status = m.toolStatus)
        }
    }
}

@Composable
private fun ToolStatusPill(status: String) {
    OutlinedCard(
        modifier = Modifier.padding(top = 4.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(6.dp, 10.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
