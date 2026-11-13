package com.pocketmaster.ai.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketmaster.ai.AppContainer
import com.pocketmaster.ai.ui.screens.ai.AgentChatScreen
import com.pocketmaster.ai.ui.screens.chain.PedalChainScreen
import com.pocketmaster.ai.ui.screens.presets.PresetsScreen
import com.pocketmaster.ai.ui.screens.settings.AiSettingsScreen

/**
 * Main app host: 4-tab bottom bar
 * Pedal Chain | Presets | AI Agent | Settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketMasterNavigation(container: AppContainer) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        TabItem("Pedal Chain", Icons.Default.KeyboardVoice, 0),
        TabItem("Presets", Icons.Default.Folder, 1),
        TabItem("AI Agent", Icons.Default.Chat, 2),
        TabItem("Settings", Icons.Default.Settings, 3)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab.index,
                        onClick = { selectedTab = tab.index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontSize = 12.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> PedalChainScreen(container)
                1 -> PresetsScreen(container)
                2 -> AgentChatScreen(container)
                3 -> AiSettingsScreen(container)
            }
        }
    }
}

data class TabItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val index: Int)
