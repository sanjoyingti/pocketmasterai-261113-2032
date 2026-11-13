package com.pocketmaster.ai.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PedalboardDark = darkColorScheme(
    primary = Color(0xFFFFB741),   // amber accent
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF442B00),
    onPrimaryContainer = Color(0xFFFFD59A),
    secondary = Color(0xFFFFB741),
    secondaryContainer = Color(0xFF442B00),
    tertiary = Color(0xFF80CBC4),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000)
)

/** Status colors for block bypass states. */
val BypassOff = Color(0xFFCF6679)
val BypassOn = Color(0xFF81C784)

@Composable
fun PocketMasterTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) PedalboardDark else darkColorScheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private val Typography = androidx.compose.material3.Typography()
