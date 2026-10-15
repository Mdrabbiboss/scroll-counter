package com.rabbi.scrollcounter.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbi.scrollcounter.data.TrackedApps
import com.rabbi.scrollcounter.face.FaceRenderer
import com.rabbi.scrollcounter.face.Mood
import com.rabbi.scrollcounter.ui.theme.Brand
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Phone mockup
// ---------------------------------------------------------------------------------------------

@Composable
fun PhoneFrame(width: Dp, height: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.size(width, height)
            .clip(RoundedCornerShape(width * 0.16f))
            .background(Color(0xFF0A0F0E))
            .border(5.dp, Color(0xFF26302E), RoundedCornerShape(width * 0.16f))
            .padding(5.dp)
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(width * 0.135f))) {
            content()
            // Camera pill
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    .size(width * 0.26f, 12.dp).clip(RoundedCornerShape(50)).background(Color.Black)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Welcome showcase: a feed flies by while the mascot counter climbs to 1000 and gets exhausted
// ---------------------------------------------------------------------------------------------

private val FeedColors = listOf(
    Color(0xFF0EA5E9) to Color(0xFF1E3A8A),
    Color(0xFFF97316) to Color(0xFF7C2D12),
    Color(0xFF22C55E) to Color(0xFF14532D),
    Color(0xFFE11D48) to Color(0xFF4C0519),
    Color(0xFFA855F7) to Color(0xFF3B0764),
    Color(0xFFEAB308) to Color(0xFF713F12),
    Color(0xFF14B8A6) to Color(0xFF134E4A),
)

@Composable
fun WelcomeShowcase(modifier: Modifier = Modifier) {
    var t by remember { mutableFloatStateOf(0f) }      // 0..1 scrolling run
    var fin by remember { mutableFloatStateOf(0f) }    // 0..1 finale (big red pill)
    var fade by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            t = 0f; fin = 0f; fade = 1f
            delay(400)
            Animatable(0f).animateTo(1f, tween(6200, easing = LinearEasing)) { t = value }
            Animatable(0f).animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 260f)) { fin = value }
            delay(1600)
            Animatable(1f).animateTo(0f, tween(450)) { fade = value }
        }
    }

    val count = (1000 * t.toDouble().pow(2.3)).roundToInt()
    val posts = 70f * t.toDouble().pow(1.9).toFloat()
    val mood = Mood.of(count, 1000)
    val phoneW = 220.dp
    val phoneH = 440.dp

    Box(modifier, contentAlignment = Alignment.Center) {
        // Soft glow behind the phone
        Box(
            Modifier.size(320.dp).background(
                Brush.radialGradient(listOf(Color(mood.accent).copy(alpha = 0.28f), Color.Transparent))
            )
        )
        PhoneFrame(phoneW, phoneH) {
            Canvas(Modifier.fillMaxSize().alpha(fade)) {
                val h = size.height
                val w = size.width
                val first = floor(posts).toInt()
                for (k in 0..2) {
                    val i = first + k
                    val top = (i - posts) * h
                    val (c1, c2) = FeedColors[((i % FeedColors.size) + FeedColors.size) % FeedColors.size]
                    drawRect(Brush.verticalGradient(listOf(c1, c2), top, top + h), Offset(0f, top), Size(w, h))
                    // "Subject" of the post
                    drawCircle(Color.White.copy(alpha = 0.16f), w * 0.32f, Offset(w * 0.5f, top + h * 0.42f))
                    drawCircle(Color.White.copy(alpha = 0.10f), w * 0.55f, Offset(w * 0.15f, top + h * 0.85f))
                    // Side action buttons
                    for (j in 0..3) drawCircle(Color.White.copy(alpha = 0.85f), w * 0.045f, Offset(w * 0.9f, top + h * (0.52f + j * 0.08f)))
                    // Caption
                    drawCircle(Color.White, w * 0.05f, Offset(w * 0.11f, top + h * 0.82f))
                    drawRoundRect(Color.White.copy(alpha = 0.9f), Offset(w * 0.2f, top + h * 0.805f), Size(w * 0.4f, h * 0.025f), CornerRadius(20f))
                    drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(w * 0.06f, top + h * 0.86f), Size(w * 0.6f, h * 0.02f), CornerRadius(20f))
                }
                // Top and bottom shade so the pill reads clearly
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), 0f, h * 0.25f))
            }
            // Counter pill: slides from the top to the centre, grows and turns red at 1000
            val pillY = (36 + (phoneH.value / 2 - 60) * fin).dp
            val pillColor = lerp(Color(0xE6062A26), Color(0xFFE5484D), fin.coerceIn(0f, 1f))
            Row(
                Modifier.align(Alignment.TopCenter).offset(y = pillY).alpha(fade).scale(1f + 0.75f * fin)
                    .clip(RoundedCornerShape(50)).background(pillColor)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                    .padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(26.dp)) {
                    drawIntoCanvas { FaceRenderer.draw(it.nativeCanvas, size.width / 2, size.height / 2, size.minDimension / 2 * 0.96f, mood) }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "$count",
                    color = if (fin > 0.3f) Color.White else Brand.Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
    }
}

/** Real icons of the supported apps installed on this phone (falls back to the first few). */
@Composable
fun SupportedAppsRow() {
    val context = LocalContext.current
    val apps = remember {
        val installed = TrackedApps.all.filter { TrackedApps.isInstalled(context, it) }
        (installed.ifEmpty { TrackedApps.all }).take(6)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        apps.forEach { AppIcon(it, size = 30) }
    }
}

// ---------------------------------------------------------------------------------------------
// Accessibility tutorial: a little phone shows exactly what to tap in Settings
// ---------------------------------------------------------------------------------------------

private val SetBg = Color(0xFF0F1413)
private val SetText = Color(0xFFE8EEED)
private val SetMuted = Color(0xFF8D9A97)

@Composable
fun AccessibilityGuide(modifier: Modifier = Modifier) {
    var scene by remember { mutableIntStateOf(0) }
    var px by remember { mutableFloatStateOf(0.8f) }
    var py by remember { mutableFloatStateOf(0.95f) }
    var tap by remember { mutableFloatStateOf(0f) }
    var hl by remember { mutableFloatStateOf(0f) }
    var toggle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        suspend fun moveTo(x: Float, y: Float, ms: Int = 850) {
            val sx = px; val sy = py
            Animatable(0f).animateTo(1f, tween(ms)) { px = sx + (x - sx) * value; py = sy + (y - sy) * value }
        }
        suspend fun press() {
            Animatable(0f).animateTo(1f, tween(380)) { tap = value }
            tap = 0f
        }
        while (true) {
            // Step 1: tap the app in the list
            scene = 0; toggle = 0f; hl = 0f; px = 0.8f; py = 1.0f
            delay(300)
            moveTo(0.45f, 0.42f)
            hl = 1f; press(); delay(450)
            // Step 2: switch it on
            scene = 1; hl = 0f; px = 0.55f; py = 0.85f
            delay(250)
            moveTo(0.80f, 0.255f)
            press(); Animatable(0f).animateTo(1f, tween(280)) { toggle = value }
            delay(600)
            // Step 3: allow
            scene = 2; px = 0.5f; py = 0.95f
            delay(250)
            moveTo(0.70f, 0.60f)
            hl = 1f; press(); delay(1100)
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PhoneFrame(170.dp, 300.dp) {
            Box(Modifier.fillMaxSize().background(SetBg).padding(top = 26.dp, start = 10.dp, end = 10.dp)) {
                when (scene) {
                    0 -> SceneList(hl)
                    else -> SceneApp(toggle)
                }
                if (scene == 2) SceneDialog(hl)
            }
            // Finger + tap ripple
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize()) {
                    if (tap > 0f) {
                        val c = Offset(size.width * px, size.height * py)
                        drawCircle(Brand.Gold.copy(alpha = 0.45f * (1 - tap)), 10.dp.toPx() + 18.dp.toPx() * tap, c)
                        drawCircle(Brand.Gold, 10.dp.toPx() + 18.dp.toPx() * tap, c, style = Stroke(2.dp.toPx()), alpha = 1 - tap)
                    }
                }
                Icon(
                    Icons.Rounded.TouchApp, null, tint = Color.White,
                    modifier = Modifier.offset(x = 160.dp * px - 11.dp, y = 290.dp * py - 3.dp).size(30.dp)
                )
            }
            // Step chip
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(50)).background(Brand.Gold).padding(horizontal = 12.dp, vertical = 4.dp)
            ) { Text("Step ${scene + 1}", color = Brand.GoldDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GuideStep(1, "Tap \"Scroll Counter\" (under Downloaded / Installed apps)", scene == 0)
            GuideStep(2, "Turn on \"Use Scroll Counter\"", scene == 1)
            GuideStep(3, "Tap \"Allow\"", scene == 2)
        }
    }
}

@Composable
private fun GuideStep(n: Int, text: String, active: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (active) Brand.Gold.copy(alpha = 0.16f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (active) Brand.Gold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Text("$n", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (active) Brand.GoldDeep else MaterialTheme.colorScheme.onSurface) }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SceneList(hl: Float) {
    Column(Modifier.fillMaxWidth()) {
        Text("‹  Accessibility", color = SetText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Text("DOWNLOADED APPS", color = SetMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        MiniRow("Accessibility Menu", "Off", false)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(Brand.Gold.copy(alpha = 0.22f * hl))
                .border(1.dp, Brand.Gold.copy(alpha = hl), RoundedCornerShape(8.dp))
        ) { MiniRow("Scroll Counter", "Off", true) }
        MiniRow("Select to Speak", "Off", false)
        MiniRow("TalkBack", "Off", false)
    }
}

@Composable
private fun MiniRow(title: String, sub: String, logo: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (logo) { AppLogo(16.dp); Spacer(Modifier.width(6.dp)) }
        Column {
            Text(title, color = SetText, fontSize = 10.sp)
            Text(sub, color = SetMuted, fontSize = 8.sp)
        }
    }
}

@Composable
private fun SceneApp(toggle: Float) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹  ", color = SetText, fontSize = 12.sp)
            AppLogo(16.dp)
            Text("  Scroll Counter", color = SetText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C2523)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Use Scroll Counter", color = SetText, fontSize = 10.sp, modifier = Modifier.weight(1f))
            MiniSwitch(toggle)
        }
        Spacer(Modifier.height(10.dp))
        Text("Options", color = SetMuted, fontSize = 8.sp)
        MiniRow("Scroll Counter shortcut", "Off", false)
        MiniRow("Settings", "", false)
    }
}

@Composable
private fun MiniSwitch(on: Float) {
    Box(
        Modifier.size(28.dp, 16.dp).clip(RoundedCornerShape(50))
            .background(lerp(Color(0xFF3A4442), Brand.Gold, on))
    ) {
        Box(
            Modifier.offset(x = (2 + 12 * on).dp, y = 2.dp).size(12.dp).clip(CircleShape).background(Color.White)
        )
    }
}

@Composable
private fun BoxScope.SceneDialog(hl: Float) {
    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)))
    Column(
        Modifier.align(Alignment.Center).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF232B29)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Allow Scroll Counter to have full control of your device?",
            color = SetText, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF3A4442)).padding(horizontal = 12.dp, vertical = 5.dp)
            ) { Text("Deny", color = SetText, fontSize = 9.sp) }
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(lerp(Color(0xFF0E8C7F), Brand.Gold, hl))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) { Text("Allow", color = if (hl > 0.5f) Brand.GoldDeep else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
