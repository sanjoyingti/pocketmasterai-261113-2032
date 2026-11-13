package com.pocketmaster.ai.ui.screens.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketmaster.ai.AppContainer

/**
 * Presets screen:
 * - 100-slot grid (P01-P50 user, F01-F50 factory)
 * - selected slot preview with apply/save/rename/import/export
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(container: AppContainer) {
    val saved = remember { mutableStateMapOf<String, String>() }
    var selectedSlot by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Presets") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            if (selectedSlot != null) {
                ExtendedFloatingActionButton(
                    onClick = { showSaveDialog = true },
                    text = { Text("Save") },
                    icon = { androidx.compose.material.icons.Icons.Default.Save }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text("User presets (P01-P50)", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(100) { index ->
                    val slotName = com.pocketmaster.ai.provider.presetLabel(index)
                    val label = saved[slotName] ?: slotName
                    PresetSlot(
                        name = slotName,
                        label = label,
                        selected = selectedSlot == slotName,
                        onClick = { selectedSlot = slotName }
                    )
                }
            }
            if (selectedSlot != null) {
                Spacer(modifier = Modifier.height(16.dp))
                SelectedSlotView(slot = selectedSlot!!, saved = saved, container = container)
            }
        }
    }
}

@Composable
private fun PresetSlot(
    name: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, fontSize = 13.sp, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(name, fontSize = 10.sp, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SelectedSlotView(slot: String, saved: Map<String, String>, container: AppContainer) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Selected: $slot", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Text("Label: ${saved[slot] ?: slot}", fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Button(onClick = {}) { Text("Apply to Pedal") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {}) { Text("Rename") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Button(onClick = {}) { Text("Export JSON") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {}) { Text("Import JSON") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {}) { Text("Backup All") }
    }
}
