package com.rabbi.scrollcounter.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
    secondaryContainer = Color(0xFFD3F4EE),
    onSecondaryContainer = Color(0xFF053B35),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6EEFB),
    onTertiaryContainer = Color(0xFF032A40),
    surfaceTint = Color(0xFF0E8C7F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7FAF9),
    surfaceContainerHighest = Color(0xFFE6EEEC),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDCE5E3),
    inversePrimary = Color(0xFF3DDCC4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF3DDCC4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0E4A43),
    onPrimaryContainer = Color(0xFFC9F7EE),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF00283D),
    background = Color(0xFF05080A),
    onBackground = Color(0xFFE6F0EE),
    surface = Color(0xFF0C1314),
    onSurface = Color(0xFFEFF5F4),
    surfaceVariant = Color(0xFF1A2826),
    onSurfaceVariant = Color(0xFFA3B4B1),
    surfaceContainer = Color(0xFF0F1A19),
    surfaceContainerHigh = Color(0xFF152321),
    outline = Color(0xFF3A4C48),
    outlineVariant = Color(0xFF1D2B29),
    error = Color(0xFFFF7A7A),
    secondaryContainer = Color(0xFF0E4A43),
    onSecondaryContainer = Color(0xFFC9F7EE),
    tertiary = Color(0xFF7DD3FC),
    onTertiary = Color(0xFF00283D),
    tertiaryContainer = Color(0xFF0B3A52),
    onTertiaryContainer = Color(0xFFD6EEFB),
    surfaceTint = Color(0xFF3DDCC4),
    surfaceContainerLowest = Color(0xFF050D0C),
    surfaceContainerLow = Color(0xFF0C1716),
    surfaceContainerHighest = Color(0xFF1C2A28),
    surfaceBright = Color(0xFF1C2A28),
    surfaceDim = Color(0xFF071110),
    inversePrimary = Color(0xFF0E8C7F),
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
    val Gold = Color(0xFFF8C33A)
    val GoldDeep = Color(0xFF5C3D00)
}

private val AppTypography = Typography().run {
    val num = AppFonts.Numbers
    val head = AppFonts.Headline
    val ui = AppFonts.Ui
    val read = AppFonts.Reading
    val noFake = androidx.compose.ui.text.font.FontSynthesis.None
    copy(
        // Big numbers — Anak Manja
        displayLarge = displayLarge.copy(fontFamily = num, fontWeight = FontWeight.Normal, fontSynthesis = noFake, letterSpacing = 0.sp),
        displayMedium = displayMedium.copy(fontFamily = num, fontWeight = FontWeight.Normal, fontSynthesis = noFake, letterSpacing = 0.sp),
        displaySmall = displaySmall.copy(fontFamily = num, fontWeight = FontWeight.Normal, fontSynthesis = noFake),
        // Headlines & titles — Matcha Style
        headlineLarge = headlineLarge.copy(fontFamily = head, fontWeight = FontWeight.Normal, fontSynthesis = noFake),
        headlineMedium = headlineMedium.copy(fontFamily = head, fontWeight = FontWeight.Normal, fontSynthesis = noFake, letterSpacing = 0.5.sp),
        headlineSmall = headlineSmall.copy(fontFamily = head, fontWeight = FontWeight.Normal, fontSynthesis = noFake, letterSpacing = 0.5.sp),
        titleLarge = titleLarge.copy(fontFamily = head, fontWeight = FontWeight.Normal, fontSynthesis = noFake, letterSpacing = 0.5.sp),
        // UI text — Blank Place
        titleMedium = titleMedium.copy(fontFamily = ui, fontWeight = FontWeight.Normal, fontSynthesis = noFake),
        titleSmall = titleSmall.copy(fontFamily = ui, fontWeight = FontWeight.Normal, fontSynthesis = noFake),
        bodyLarge = bodyLarge.copy(fontFamily = ui, fontWeight = FontWeight.Normal, fontSynthesis = noFake),
        bodyMedium = bodyMedium.copy(fontFamily = ui, fontWeight = FontWeight.Normal, fontSynthesis = noFake),
        labelLarge = labelLarge.copy(fontFamily = ui, fontWeight = FontWeight.Normal, fontSynthesis = noFake),
        labelMedium = labelMedium.copy(fontFamily = ui, fontWeight = FontWeight.Normal, fontSynthesis = noFake, letterSpacing = 1.sp),
        labelSmall = labelSmall.copy(fontFamily = ui, fontWeight = FontWeight.Normal, fontSynthesis = noFake, letterSpacing = 0.8.sp),
        // Small print & long explanations — Inter
        bodySmall = bodySmall.copy(fontFamily = read),
    )
}

@Composable
fun ScrollCounterTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) Dark else Light
    MaterialTheme(colorScheme = colors, typography = AppTypography) {
        // Glass surfaces are translucent, so Material can't infer a text color from them.
        // Make every Text default to the readable foreground for this theme, in the app font.
        CompositionLocalProvider(
            LocalContentColor provides colors.onBackground,
            androidx.compose.material3.LocalTextStyle provides AppTypography.bodyLarge,
            content = content
        )
    }
}
