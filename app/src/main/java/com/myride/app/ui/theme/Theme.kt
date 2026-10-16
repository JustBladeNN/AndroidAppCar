package com.myride.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MyRideColors = darkColorScheme(
    primary = Color(0xFF55A7FF),
    onPrimary = Color(0xFF06101B),
    secondary = Color(0xFF9DCDFF),
    background = Color(0xFF0A0D12),
    surface = Color(0xFF121720),
    surfaceVariant = Color(0xFF1B222D),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF9CAABA),
    error = Color(0xFFFF7676),
)

@Composable
fun MyRideTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MyRideColors, content = content)
}
