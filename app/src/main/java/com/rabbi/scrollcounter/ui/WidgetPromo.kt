package com.rabbi.scrollcounter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbi.scrollcounter.face.Mood
import com.rabbi.scrollcounter.ui.theme.Brand
import com.rabbi.scrollcounter.widget.WidgetArt
import com.rabbi.scrollcounter.widget.WidgetState
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** The real widget artwork, drawn live in the app (twinkling, breathing, blinking). */
@Composable
fun WidgetPreview(state: WidgetState, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "widget")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "phase")
    val eyes = remember { com.rabbi.scrollcounter.face.EyeLife() }
    var blink by remember { mutableStateOf(0f) }
    var gx by remember { mutableStateOf(0f) }
    var gy by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameMillis { t ->
                eyes.update(t); blink = eyes.blink; gx = eyes.gazeX; gy = eyes.gazeY
            }
        }
    }
    val density = LocalDensity.current.density
    Canvas(modifier) {
        drawIntoCanvas { WidgetArt.draw(it.nativeCanvas, size.width, size.height, density, state, phase, blink, gx, gy) }
    }
}

private data class Demo(val count: Int, val mood: Mood, val streak: Int)

private val DEMO = listOf(
    Demo(18, Mood.FRESH, 3), Demo(37, Mood.OKAY, 3), Demo(62, Mood.TIRED, 3),
    Demo(88, Mood.DRAINED, 3), Demo(104, Mood.EXHAUSTED, 3),
)

private fun Mood.glow(): Color = when (this) {
    Mood.FRESH -> Color(0xFF34D399)
    Mood.OKAY -> Color(0xFF38BDF8)
    Mood.TIRED -> Color(0xFFF59E0B)
    Mood.DRAINED -> Color(0xFFFB7185)
    Mood.EXHAUSTED -> Color(0xFFEF4444)
}

/**
 * Full-screen "Add the widget" page shown after setup (and from the Add widget buttons):
 * a phone home screen where the real widget drops in and lives through a whole day — fresh,
 * calm, tired, drained, exhausted — so you see exactly what you're adding.
 */
@Composable
fun WidgetPromoScreen(placed: Boolean, onAdd: () -> Unit, onClose: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    BackHandler(onBack = onClose)

    // Day-in-the-life demo loop
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(1300)
        while (true) {
            delay(1900)
            step = (step + 1) % DEMO.size
        }
    }
    val demo = DEMO[step]
    val count by animateIntAsState(demo.count, tween(if (step == 0) 500 else 900, easing = FastOutSlowInEasing), label = "count")

    // Entrance choreography
    val enter = remember { Animatable(0f) }
    val drop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        drop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }

    // Success: celebrate, then leave
    LaunchedEffect(placed) {
        if (placed) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(1700)
            onClose()
        }
    }

    val t = rememberInfiniteTransition(label = "promo")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "drift")
    val shimmer by t.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "shimmer")
    val glow = demo.mood.glow()

    Box(
        Modifier.fillMaxSize().background(Color(0xFF060B0A))
            .drawWithContent {
                // Aurora: two soft blobs that drift and take the widget's mood colour.
                val w = size.width; val h = size.height
                val a = (drift * 2 * PI).toFloat()
                drawCircle(
                    Brush.radialGradient(listOf(glow.copy(alpha = 0.30f), Color.Transparent), Offset(w * (0.3f + 0.12f * sin(a)), h * 0.42f), w * 0.85f),
                    radius = w * 0.85f, center = Offset(w * (0.3f + 0.12f * sin(a)), h * 0.42f)
                )
                drawCircle(
                    Brush.radialGradient(listOf(Brand.Gold.copy(alpha = 0.10f), Color.Transparent), Offset(w * 0.85f, h * (0.2f + 0.05f * sin(a + 1f))), w * 0.6f),
                    radius = w * 0.6f, center = Offset(w * 0.85f, h * (0.2f + 0.05f * sin(a + 1f)))
                )
                // fine floating specks
                for (i in 0 until 26) {
                    val sx = ((i * 0.618f + drift * (0.2f + (i % 3) * 0.1f)) % 1f) * w
                    val sy = ((i * 0.37f + 0.13f * i - drift * 0.3f) % 1f + 1f) % 1f * h
                    drawCircle(Color.White.copy(alpha = 0.05f + (i % 4) * 0.03f), radius = (1 + i % 3).dp.toPx() * 0.6f, center = Offset(sx, sy))
                }
                drawContent()
            }
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))
            // Chip
            Text(
                "HOME SCREEN WIDGET",
                color = Brand.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp,
                modifier = Modifier.alpha(enter.value).clip(RoundedCornerShape(50))
                    .background(Brand.Gold.copy(alpha = 0.12f))
                    .border(1.dp, Brand.Gold.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                buildAnnotatedString {
                    append("Your reels count,\n")
                    withStyle(SpanStyle(brush = Brush.linearGradient(listOf(Color(0xFFFFE08A), Brand.Gold, Color(0xFFFF9F43))))) {
                        append("one glance away")
                    }
                },
                color = Color.White, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center, letterSpacing = (-0.6).sp,
                modifier = Modifier.graphicsLayer { alpha = enter.value; translationY = (1 - enter.value) * 30f }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Live count, a buddy that gets tired as you scroll, and your streak — without opening the app.",
                color = Color.White.copy(alpha = 0.62f), fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                modifier = Modifier.alpha(enter.value).padding(horizontal = 8.dp)
            )

            // ---- phone home screen ----
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                HomeScreenMock(
                    glow = glow,
                    modifier = Modifier.graphicsLayer {
                        alpha = enter.value
                        translationY = (1 - enter.value) * 80f
                    }
                ) { side ->
                    // The widget drops onto the home screen with a springy bounce.
                    Box(
                        Modifier.size(side).graphicsLayer {
                            val d = drop.value
                            scaleX = 0.55f + 0.45f * d; scaleY = 0.55f + 0.45f * d
                            alpha = d.coerceIn(0f, 1f)
                            translationY = (1f - d) * -140f
                        }
                    ) {
                        Crossfade(step, animationSpec = tween(650), label = "mood") { s ->
                            val d = DEMO[s]
                            WidgetPreview(
                                WidgetState(if (s == step) count else d.count, 100, d.streak, forcedMood = d.mood),
                                Modifier.size(side)
                            )
                        }
                    }
                }
            }

            // ---- feature chips ----
            Row(Modifier.alpha(enter.value), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureChip("⚡", "Live count")
                FeatureChip("🙂", "Mood buddy")
                FeatureChip("🔥", "Streaks")
            }
            Spacer(Modifier.height(16.dp))

            // ---- buttons ----
            GoldButton(if (placed) "Added!" else "Add widget", shimmer, placed) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (!placed) onAdd()
            }
            Text(
                "Not now",
                color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onClose).padding(horizontal = 24.dp, vertical = 12.dp)
            )
            Spacer(Modifier.height(4.dp))
        }

        // ---- success overlay ----
        AnimatedVisibility(placed, enter = fadeIn(tween(250)), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(placed, enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))) {
                        Box(
                            Modifier.size(96.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF0EA5E9)))),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(56.dp)) }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text("Widget added!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Text("Check your home screen", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            }
        }
    }
}

/** A stylised phone home screen whose top-left 2×2 slot holds [widget]. Bottom fades out. */
@Composable
private fun HomeScreenMock(glow: Color, modifier: Modifier = Modifier, widget: @Composable (side: Dp) -> Unit) {
    val phoneW = 300.dp
    val cell = 56.dp
    val gap = 12.dp
    Box(
        modifier.width(phoneW).heightIn(max = 390.dp).fillMaxHeight()
            .drawWithContent {
                drawContent()
                // fade the bottom of the phone into the page
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF060B0A)), startY = size.height * 0.62f, endY = size.height),
                )
            }
    ) {
        Box(
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp))
                .background(Color(0xFF0B1211))
                .border(5.dp, Color(0xFF26302E), RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp))
                .padding(5.dp)
                .clip(RoundedCornerShape(topStart = 39.dp, topEnd = 39.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1B1F3A), Color(0xFF0E1428), glow.copy(alpha = 0.25f).compositeOver(Color(0xFF0B1020)))
                    )
                )
        ) {
            // status bar + camera pill
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("9:41", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(width = 16.dp, height = 8.dp).clip(RoundedCornerShape(2.dp)).border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp)))
            }
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                    .size(76.dp, 18.dp).clip(RoundedCornerShape(50)).background(Color.Black)
            )

            // grid: 4 columns, widget spans 2×2 at top-left
            val tints = listOf(
                Color(0xFF6366F1), Color(0xFF22C55E), Color(0xFFF97316), Color(0xFFEC4899),
                Color(0xFF06B6D4), Color(0xFFEAB308), Color(0xFF8B5CF6), Color(0xFFEF4444),
                Color(0xFF14B8A6), Color(0xFF3B82F6), Color(0xFFF43F5E), Color(0xFF84CC16),
            )
            Column(Modifier.padding(top = 46.dp).align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    widget(cell * 2 + gap)
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { MockIcon(cell, tints[0]); MockIcon(cell, tints[1]) }
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) { MockIcon(cell, tints[2]); MockIcon(cell, tints[3]) }
                    }
                }
                repeat(2) { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) { repeat(4) { MockIcon(cell, tints[4 + r * 4 + it]) } }
                }
            }
        }
    }
}

@Composable
private fun MockIcon(size: Dp, tint: Color) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.22f))))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(size * 0.3f))
    )
}

@Composable
private fun FeatureChip(emoji: String, label: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Big warm-gold button with a light sweep passing across it. */
@Composable
private fun GoldButton(text: String, shimmer: Float, done: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxWidth().height(58.dp)
            .pressScale(src)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (done) Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF10B981)))
                else Brush.linearGradient(listOf(Color(0xFFFFE6A3), Color(0xFFFFC857), Color(0xFFFF9F43)))
            )
            .drawWithContent {
                drawContent()
                if (!done) {
                    val x = size.width * shimmer
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                            start = Offset(x - size.width * 0.18f, 0f), end = Offset(x + size.width * 0.18f, size.height)
                        )
                    )
                }
            }
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (done) Icons.Rounded.Check else Icons.Rounded.AddToHomeScreen, null, tint = if (done) Color.White else Color(0xFF4A2A00), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, color = if (done) Color.White else Color(0xFF4A2A00), fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
    }
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}
