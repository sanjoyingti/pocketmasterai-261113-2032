package com.pocketmaster.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pocketmaster.ai.ui.navigation.PocketMasterNavigation

@Composable
fun PocketMasterApp(container: AppContainer, modifier: Modifier = Modifier) {
    PocketMasterNavigation(container = container)
}
