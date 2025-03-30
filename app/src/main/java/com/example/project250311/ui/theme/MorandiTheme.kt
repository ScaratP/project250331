package com.example.project250311.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 1. 定義 Morandi 色系
val MorandiPrimary = Color(0xFFB7AFA3)
val MorandiOnPrimary = Color(0xFF3E3E3E)
val MorandiSecondary = Color(0xFFD8CFC4)
val MorandiOnSecondary = Color(0xFF3E3E3E)
val MorandiBackground = Color(0xFFF5F5F5)
val MorandiOnBackground = Color(0xFF2D2D2D)
val MorandiSurface = Color(0xFFE5E1DC)
val MorandiOnSurface = Color(0xFF2D2D2D)

// 2. Morandi ColorScheme
val MorandiColorScheme = lightColorScheme(
    primary = MorandiPrimary,
    onPrimary = MorandiOnPrimary,
    secondary = MorandiSecondary,
    onSecondary = MorandiOnSecondary,
    background = MorandiBackground,
    onBackground = MorandiOnBackground,
    surface = MorandiSurface,
    onSurface = MorandiOnSurface
)

// 3. 套用到 Theme
@Composable
fun MorandiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MorandiColorScheme,
        typography = Typography(),
        content = content
    )
}