package com.janderson.borajuntos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BoraColors = lightColorScheme(
    primary = Color(0xFF1565C0), secondary = Color(0xFFBF360C),
    tertiary = Color(0xFFFF7043), background = Color(0xFFF5F7FA),
    surface = Color.White, onBackground = Color(0xFF202B38),
    onSurface = Color(0xFF202B38), onSurfaceVariant = Color(0xFF536171)
)

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BoraColors, typography = Typography, content = content)
}
