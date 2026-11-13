package com.pocketmaster.ai.ui.screens.chain

import androidx.compose.animation.core.rememberAnimatedFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
 * Pedal Chain screen:
 * - horizontal scroll of 9 block cards
 * - bypass toggle per block
 * - model dropdown + parameter slider/knob row
 * - Apply to device + Disconnect button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedalChainScreen(container: AppContainer) {
    val deviceMapRepo = container.deviceMapRepo
    val chainOrder = remember { mutableStateListOf(0, 1, 2, 3, 4, 5, 6, 7, 8) }
    var showApplyDialog by remember { mutableStateOf(false) }

    // Simulated live device state (in a real build this flows from the device)
    val blockState = remember { mutableStateListOf(
        BlockState("COMP", true, 0, mutableStateListOf(Parameter("Gain", 0.5f, 0f, 1f)), 0),
        BlockState("DRV", true, 0, mutableStateListOf(Parameter("Drive", 0.6f, 0f, 1f)), 0),
        BlockState("AMP", true, 0, mutableStateListOf(Parameter("Gain", 0.55f, 0f, 1f), Parameter("Bass", 0.5f, 0f, 1f), Parameter("Mid", 0.5f, 0f, 1f), Parameter("Treble", 0.5f, 0f, 1f), Parameter("Presence", 0.5f, 0f, 1f)), 0),
        BlockState("CAB/IR", true, 0, emptyList(), 0),
        BlockState("EQ", true, 0, mutableStateListOf(Parameter("Bass", 0.5f, 0f, 1f), Parameter("Mid", 0.5f, 0f, 1f), Parameter("Treble", 0.5f, 0f, 1f)), 0),
        BlockState("MOD", true, 0, mutableStateListOf(Parameter("Rate", 0.5f, 0f, 1f), Parameter("Depth", 0.5f, 0f, 1f)), 0),
        BlockState("DLY", true, 0, mutableStateListOf(Parameter("Time", 0.5f, 0f, 1f), Parameter("FBack", 0.5f, 0f, 1f)), 0),
        BlockState("RVB", true, 0, mutableStateListOf(Parameter("Decay", 0.5f, 0f, 1f)), 0),
        BlockState("MASTER", true, 0, mutableStateListOf(Parameter("Vol", 0.5f, 0f, 1f)), 0),
    )}

    // Simulate periodic device read
    LaunchedEffect(Unit) {
        // In a real app: poll the device and update blockState / chainOrder
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pedal Chain") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showApplyDialog = true },
                text = { Text("Apply") },
                icon = { Icon(Icons.Default.Save, "Save changes") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .horizontalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { /* disconnect */ }) {
                    Text("Disconnect")
                }
            }
            chainOrder.forEach { pos ->
                BlockCard(
                    pos = pos,
                    block = blockState[pos],
                    onToggle = { blockState[pos] = blockState[pos].copy(on = !blockState[pos].on) },
                    onModelSelect = { /* open model dropdown */ },
                    onParamChange = { param, value ->
                        blockState[pos] = blockState[pos].copy(params = blockState[pos].params.map {
                            if (it.name == param) it.copy(value = value) else it
                        })
                    },
                    modifier = Modifier
                        .width(130.dp)
                        .height(210.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { /* drag handle */ }
                )
            }
        }
    }
}

@Composable
private fun BlockCard(
    pos: Int,
    block: BlockState,
    onToggle: () -> Unit,
    onModelSelect: (String) -> Unit,
    onParamChange: (String, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = block.on, onCheckedChange = { onToggle() })
            Text(
                text = "${pos}: ${block.name}",
                fontSize = 12.sp,
                color = if (block.on) MaterialTheme.colorScheme.primary else BypassOff
            )
        }
        Text(text = "Model", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = "US Tweed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(block.params.size) { index ->
                val p = block.params[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(p.name, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = p.value,
                        onValueChange = { onParamChange(p.name, it) },
                        valueRange = p.range,
                        modifier = Modifier.width(90.dp)
                    )
                }
            }
        }
    }
}

data class BlockState(
    val name: String,
    val on: Boolean,
    val modelCode: Int,
    val params: List<Parameter>,
    val volume: Float
)

data class Parameter(
    val name: String,
    var value: Float,
    val min: Float = 0f,
    val max: Float = 1f
) {
    val range get() = min..max
}
