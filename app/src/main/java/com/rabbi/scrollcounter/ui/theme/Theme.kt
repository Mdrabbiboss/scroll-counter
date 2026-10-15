package com.rabbi.scrollcounter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Light = lightColorScheme(
    primary = Color(0xFF0E8C7F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F4EE),
    onPrimaryContainer = Color(0xFF053B35),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    background = Color(0xFFF2F6F5),
    onBackground = Color(0xFF0F1A19),
    surface = Color.White,
    onSurface = Color(0xFF0F1A19),
    surfaceVariant = Color(0xFFE6EEEC),
    onSurfaceVariant = Color(0xFF5B6B68),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    outline = Color(0xFFB9C8C4),
    outlineVariant = Color(0xFFE0E9E7),
    error = Color(0xFFE5484D),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF3DDCC4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0E4A43),
    onPrimaryContainer = Color(0xFFC9F7EE),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF00283D),
    background = Color(0xFF071110),
    onBackground = Color(0xFFE6F0EE),
    surface = Color(0xFF0F1A19),
    onSurface = Color(0xFFE6F0EE),
    surfaceVariant = Color(0xFF1A2826),
    onSurfaceVariant = Color(0xFF93A8A4),
    surfaceContainer = Color(0xFF0F1A19),
    surfaceContainerHigh = Color(0xFF152321),
    outline = Color(0xFF3A4C48),
    outlineVariant = Color(0xFF1D2B29),
    error = Color(0xFFFF7A7A),
)

/** Fixed brand colors that look the same in light and dark. Calm ocean teal with a mint→sky ring. */
object Brand {
    val HeroTop = Color(0xFF052A26)
    val HeroBottom = Color(0xFF0D6A5F)
    val hero = Brush.linearGradient(listOf(HeroTop, HeroBottom))
    val Glow = Color(0xFF5EEAD4)
    val RingStart = Color(0xFF6EE7D2)
    val RingEnd = Color(0xFF7DD3FC)
    val Warn = Color(0xFFF5A524)
    val Over = Color(0xFFFF7A7A)
    val Good = Color(0xFF2FBF71)
}

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-2).sp),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    )
}

@Composable
fun ScrollCounterTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        content = content
    )
}
