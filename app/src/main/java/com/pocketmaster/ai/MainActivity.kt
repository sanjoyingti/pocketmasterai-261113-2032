package com.pocketmaster.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.pocketmaster.ai.ui.theme.PocketMasterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContainer = (application as PocketMasterApplication).container
        setContent {
            PocketMasterTheme {
                MaterialTheme {
                    PocketMasterApp(container = appContainer)
                }
            }
        }
    }
}
