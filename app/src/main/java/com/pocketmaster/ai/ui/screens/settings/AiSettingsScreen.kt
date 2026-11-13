package com.pocketmaster.ai.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketmaster.ai.AppContainer

/**
 * AI Settings screen with tabs:
 * - Connection: device name, reconnect toggle
 * - AI Provider: Google Sign-In, Gemini model dropdown, Gemini API key, custom endpoint toggle, base URL, API key, Fetch Models, manual model, model list
 * - Web Search: DDG default toggle, Google CSE add
 * - Tone Rules: strict validation, range clamp
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(container: AppContainer) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Connection", "AI Provider", "Web Search", "Tone Rules")

    val providerType by container.settings.providerType.collectAsState("GEMINI")
    val geminiModel by container.settings.geminiModel.collectAsState("gemini-2.5-flash")
    val apiKey by container.settings.apiKey.collectAsState("")
    val baseUrl by container.settings.baseUrl.collectAsState("")
    val searchProvider by container.settings.searchProvider.collectAsState("DUCK_DUCK_GO")
    val googleCseId by container.settings.googleCseId.collectAsState("")
    val googleCseApiKey by container.settings.googleCseApiKey.collectAsState("")
    val strictValidation by container.settings.strictValidation.collectAsState(true)
    val rangeClamp by container.settings.rangeClamp.collectAsState(true)

    var showModelDropdown by remember { mutableStateOf(false) }
    val modelList = remember {
        listOf("deepseek-4.1-flash", "deepseek-chat", "deepseek-reasoner", "gemini-2.5-flash", "gemini-2.5-pro")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tabs.indexOf(tab),
                        onClick = { selectedTab = tabs.indexOf(tab) },
                        text = { Text(tab, fontSize = 13.sp) }
                    )
                }
            }
            when (selectedTab) {
                0 -> ConnectionTab()
                1 -> ProviderTab(
                    providerType = providerType,
                    geminiModel = geminiModel,
                    onGeminiModelChange = { /* save later */ },
                    apiKey = apiKey,
                    onApiKeyChange = { /* save later */ },
                    baseUrl = baseUrl,
                    onBaseUrlChange = { /* save later */ },
                    showModelDropdown = showModelDropdown,
                    onShowModelDropdownChange = { showModelDropdown = it },
                    modelList = modelList
                )
                2 -> WebSearchTab(
                    searchProvider = searchProvider,
                    onProviderChange = {},
                    googleCseId = googleCseId,
                    onCseIdChange = {},
                    googleCseApiKey = googleCseApiKey,
                    onCseApiKeyChange = {}
                )
                3 -> ToneRulesTab(
                    strictValidation = strictValidation,
                    onStrictChange = {},
                    rangeClamp = rangeClamp,
                    onRangeChange = {}
                )
            }
        }
    }
}

@Composable
private fun ConnectionTab() {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Device: none (connect USB OTG)", fontSize = 15.sp)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = true, onCheckedChange = {})
                Spacer(modifier = Modifier.width(8.dp))
                Text("Auto-reconnect on disconnect (2x retry, 500ms backoff)", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ProviderTab(
    providerType: String,
    geminiModel: String,
    onGeminiModelChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    showModelDropdown: Boolean,
    onShowModelDropdownChange: (Boolean) -> Unit,
    modelList: List<String>
) {
    var useCustomEndpoint by remember { mutableStateOf(providerType == "CUSTOM") }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Authentication mode", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        item {
            Row {
                RadioButton(selected = !useCustomEndpoint, onClick = { useCustomEndpoint = false })
                Text("Google Sign-In (Gemini OAuth)", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(selected = useCustomEndpoint, onClick = { useCustomEndpoint = true })
                Text("Custom OpenAI-compatible endpoint", fontSize = 14.sp)
            }
        }
        if (!useCustomEndpoint) {
            item {
                Button(onClick = { /* Google Sign-In */ }) {
                    Text("Sign in with Google")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Google OAuth Client ID needed in Google Cloud Console", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Text("Gemini model", fontSize = 15.sp)
                OutlinedTextField(
                    value = geminiModel,
                    onValueChange = onGeminiModelChange,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Gemini API key (fallback if OAuth fails)", fontSize = 15.sp)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter Gemini API key") }
                )
            }
        } else {
            item {
                Text("Base URL", fontSize = 15.sp)
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://api.deepseek.com/v1") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("API key", fontSize = 15.sp)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("API key for custom endpoint") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { /* fetch models */ }) { Text("Fetch Models") }
                Spacer(modifier = Modifier.height(8.dp))
                if (showModelDropdown) {
                    DropdownMenu(
                        expanded = showModelDropdown,
                        onDismissRequest = { onShowModelDropdownChange(false) }
                    ) {
                        modelList.forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = { /* select */ })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebSearchTab(
    searchProvider: String,
    onProviderChange: (String) -> Unit,
    googleCseId: String,
    onCseIdChange: (String) -> Unit,
    googleCseApiKey: String,
    onCseApiKeyChange: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = searchProvider == "DUCK_DUCK_GO", onClick = { onProviderChange("DUCK_DUCK_GO") })
                Text("DuckDuckGo (default, no key)", fontSize = 14.sp)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = searchProvider == "GOOGLE_CSE", onClick = { onProviderChange("GOOGLE_CSE") })
                Text("Google Custom Search", fontSize = 14.sp)
            }
        }
        item {
            OutlinedTextField(
                value = googleCseId,
                onValueChange = onCseIdChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Google CSE ID") }
            )
        }
        item {
            OutlinedTextField(
                value = googleCseApiKey,
                onValueChange = onCseApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Google CSE API key") }
            )
        }
    }
}

@Composable
private fun ToneRulesTab(
    strictValidation: Boolean,
    onStrictChange: (Boolean) -> Unit,
    rangeClamp: Boolean,
    onRangeChange: (Boolean) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = strictValidation, onCheckedChange = onStrictChange)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Strict validation: reject any model/param not in device_map.json", fontSize = 14.sp)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = rangeClamp, onCheckedChange = onRangeChange)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Range clamp: force parameters into device_map.json min/max", fontSize = 14.sp)
            }
        }
    }
}
