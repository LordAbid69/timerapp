package com.example.timer.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Pal {
    val Bg = Color(0xFF000000)
    val Card = Color(0xFF1C1C1E)
    val Gray = Color(0xFF333336)
    val Dim = Color(0xFF8E8E93)
    val Orange = Color(0xFFFF9F0A)
    val OrangeBg = Color(0xFF3D2800)
    val Green = Color(0xFF30D158)
    val GreenBg = Color(0xFF0D3B1A)
    val Red = Color(0xFFFF453A)
}

@Composable
fun TimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Pal.Orange, background = Pal.Bg, surface = Pal.Card),
        content = content
    )
}
