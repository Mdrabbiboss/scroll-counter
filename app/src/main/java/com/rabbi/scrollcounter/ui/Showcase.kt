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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Share
import androidx.compose.animation.core.FastOutSlowInEasing
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

private val Handles = listOf("@sunset.diaries", "@ocean.daily", "@citylights", "@into.the.woods", "@dune.walker", "@peak.hunter")
private val Captions get() = listOf(tr("Golden hour never misses"), tr("Sound of the waves"), tr("This city never sleeps"), tr("Lost in the green"), tr("Endless sand"), tr("Above the clouds"))
private val Likes = listOf("24.1K", "8,302", "112K", "3,954", "41.7K", "9,880")

/** One illustrated "reel": a little landscape scene, so the feed reads as real content. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScene(kind: Int, top: Float, w: Float, h: Float) {
    fun rect(c1: Color, c2: Color, y0: Float, y1: Float) =
        drawRect(Brush.verticalGradient(listOf(c1, c2), top + y0 * h, top + y1 * h), Offset(0f, top + y0 * h), Size(w, (y1 - y0) * h))
    fun poly(c: Color, vararg pts: Float) {
        val path = androidx.compose.ui.graphics.Path()
        path.moveTo(pts[0] * w, top + pts[1] * h)
        var i = 2
        while (i < pts.size) { path.lineTo(pts[i] * w, top + pts[i + 1] * h); i += 2 }
        path.close()
        drawPath(path, c)
    }
    when (kind) {
        0 -> { // Sunset over mountains
            rect(Color(0xFFFF8A4C), Color(0xFF9D4EDD), 0f, 1f)
            drawCircle(Color(0xFFFFE08A), w * 0.17f, Offset(w * 0.5f, top + h * 0.5f))
            poly(Color(0xFF5B2A86), 0f, 0.62f, 0.25f, 0.45f, 0.45f, 0.6f, 0.68f, 0.4f, 1f, 0.6f, 1f, 1f, 0f, 1f)
            poly(Color(0xFF2E1450), 0f, 0.78f, 0.3f, 0.6f, 0.6f, 0.8f, 0.85f, 0.65f, 1f, 0.75f, 1f, 1f, 0f, 1f)
        }
        1 -> { // Ocean
            rect(Color(0xFF7DD3FC), Color(0xFFE0F2FE), 0f, 0.5f)
            rect(Color(0xFF0EA5E9), Color(0xFF0C4A6E), 0.5f, 1f)
            drawCircle(Color(0xFFFFF7D6), w * 0.1f, Offset(w * 0.75f, top + h * 0.2f))
            for (k in 0..4) drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(w * (0.1f + 0.17f * k), top + h * (0.58f + 0.07f * (k % 3))), Size(w * 0.18f, h * 0.012f), CornerRadius(10f))
        }
        2 -> { // Night city
            rect(Color(0xFF0B1026), Color(0xFF2A1B5C), 0f, 1f)
            drawCircle(Color(0xFFF5F3CE), w * 0.08f, Offset(w * 0.78f, top + h * 0.16f))
            for (k in 0..14) drawCircle(Color.White.copy(alpha = 0.7f), 2.5f, Offset(w * ((k * 37) % 100) / 100f, top + h * (0.05f + ((k * 13) % 30) / 100f)))
            val bw = w / 6
            for (k in 0..5) {
                val bh = h * (0.32f + ((k * 29) % 25) / 100f)
                drawRect(Color(0xFF151A3A), Offset(k * bw, top + h - bh), Size(bw - 4f, bh))
                for (row in 0..6) for (col in 0..1)
                    if ((row + col + k) % 3 != 0) drawRect(Color(0xFFFFD166), Offset(k * bw + 8f + col * bw * 0.4f, top + h - bh + 14f + row * bh * 0.12f), Size(bw * 0.16f, bh * 0.05f))
            }
        }
        3 -> { // Forest
            rect(Color(0xFFBBF7D0), Color(0xFF14532D), 0f, 1f)
            for (k in 0..6) {
                val x = w * (k / 6f)
                val base = 0.95f - (k % 2) * 0.08f
                poly(Color(0xFF166534).copy(alpha = 0.9f), x / w - 0.12f, base, x / w, base - 0.42f, x / w + 0.12f, base)
            }
            rect(Color.White.copy(alpha = 0.0f), Color.White.copy(alpha = 0.25f), 0.55f, 0.75f)
        }
        4 -> { // Desert
            rect(Color(0xFF5EEAD4), Color(0xFFFDE68A), 0f, 0.55f)
            drawCircle(Color(0xFFFFFBEB), w * 0.12f, Offset(w * 0.3f, top + h * 0.22f))
            poly(Color(0xFFF59E0B), 0f, 0.6f, 0.4f, 0.5f, 1f, 0.62f, 1f, 1f, 0f, 1f)
            poly(Color(0xFFD97706), 0f, 0.78f, 0.55f, 0.66f, 1f, 0.8f, 1f, 1f, 0f, 1f)
        }
        else -> { // Snowy peaks
            rect(Color(0xFF38BDF8), Color(0xFFE0F2FE), 0f, 1f)
            poly(Color(0xFF64748B), 0f, 0.8f, 0.3f, 0.35f, 0.55f, 0.7f, 0.78f, 0.42f, 1f, 0.75f, 1f, 1f, 0f, 1f)
            poly(Color.White, 0.22f, 0.47f, 0.3f, 0.35f, 0.38f, 0.47f)
            poly(Color.White, 0.71f, 0.52f, 0.78f, 0.42f, 0.85f, 0.52f)
            rect(Color(0xFFF1F5F9), Color.White, 0.85f, 1f)
        }
    }
}

/**
 * Welcome showcase, told in three beats so it's easy to follow:
 *  1. Slow: each swipe moves to the next reel and the counter goes +1 (a finger shows the swipe).
 *  2. Faster and faster: the count races up and the buddy gets more and more tired.
 *  3. 1000: the counter jumps to the middle, turns red, and the buddy is dizzy.
 */
@Composable
fun WelcomeShowcase(modifier: Modifier = Modifier) {
    var posts by remember { mutableFloatStateOf(0f) }
    var count by remember { mutableIntStateOf(0) }
    var fin by remember { mutableFloatStateOf(0f) }
    var fade by remember { mutableFloatStateOf(1f) }
    var finger by remember { mutableFloatStateOf(-1f) }   // -1 hidden, 0..1 swipe progress
    var beat by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            posts = 0f; count = 0; fin = 0f; fade = 1f; beat = 0
            Animatable(0f).animateTo(1f, tween(350)) { fade = value }
            delay(500)
            // Beat 1: six deliberate swipes
            repeat(6) {
                val start = posts
                Animatable(0f).animateTo(1f, tween(420, easing = FastOutSlowInEasing)) {
                    finger = value
                    posts = start + value
                }
                finger = -1f
                count += 1
                delay(520)
            }
            // Beat 2: speeding up
            beat = 1
            val startPosts = posts
            Animatable(0f).animateTo(1f, tween(5200, easing = LinearEasing)) {
                val t = value
                posts = startPosts + 90f * t * t
                count = (6 + 994 * t.toDouble().pow(2.4)).roundToInt()
            }
            count = 1000
            // Beat 3: the big red 1000
            beat = 2
            Animatable(0f).animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 220f)) { fin = value }
            delay(2300)
            Animatable(1f).animateTo(0f, tween(450)) { fade = value }
        }
    }

    val mood = Mood.of(count, 1000)
    val phoneW = 250.dp
    val phoneH = 500.dp
    val current = (posts.roundToInt() % Handles.size + Handles.size) % Handles.size

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(340.dp).background(
                    Brush.radialGradient(listOf(Color(mood.accent).copy(alpha = 0.32f), Color.Transparent))
                )
            )
            PhoneFrame(phoneW, phoneH) {
                Canvas(Modifier.fillMaxSize().alpha(fade)) {
                    val h = size.height
                    val w = size.width
                    val first = floor(posts).toInt()
                    for (k in 0..1) {
                        val i = first + k
                        val top = (i - posts) * h
                        drawScene(((i % 6) + 6) % 6, top, w, h)
                    }
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent), 0f, h * 0.22f))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), h * 0.62f, h))
                    // Finger swipe hint
                    if (finger >= 0f) {
                        val fy = h * (0.78f - 0.42f * finger)
                        drawCircle(Color.White.copy(alpha = 0.35f * (1 - finger * 0.5f)), w * 0.1f, Offset(w * 0.5f, fy))
                        drawCircle(Color.White.copy(alpha = 0.9f), w * 0.055f, Offset(w * 0.5f, fy))
                    }
                }
                // Reel UI: actions on the right, author and caption bottom-left
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 70.dp).alpha(fade),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ReelAction(Icons.Rounded.Favorite, Likes[current])
                    ReelAction(Icons.Rounded.ChatBubble, "${(current + 3) * 127}")
                    ReelAction(Icons.Rounded.Share, tr("Share"))
                }
                Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 22.dp, end = 60.dp).alpha(fade)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFF472B6), Color(0xFFF59E0B)))))
                        Spacer(Modifier.width(6.dp))
                        Text(Handles[current], color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(Captions[current], color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp)
                }
                // Counter pill: slides from the top to the centre, grows and turns red at 1000
                val pillY = (34 + (phoneH.value / 2 - 64) * fin).dp
                val pillColor = lerp(Color(0xE6062A26), Color(0xFFE5484D), fin.coerceIn(0f, 1f))
                Row(
                    Modifier.align(Alignment.TopCenter).offset(y = pillY).alpha(fade).scale(1f + 0.8f * fin)
                        .clip(RoundedCornerShape(50)).background(pillColor)
                        .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(50))
                        .padding(start = 4.dp, end = 14.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedFace(mood, 30.dp, bounceKey = if (beat == 0) count else null)
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
        Spacer(Modifier.height(14.dp))
        // Narration chip so it's clear what's happening
        val caption = when (beat) {
            0 -> tr("Every new reel  =  +1")
            1 -> tr("It adds up fast…  %1\$s", tr(mood.label).lowercase())
            else -> tr("1000 reels. Time for a break!")
        }
        androidx.compose.animation.AnimatedContent(targetState = caption, label = "caption") { c ->
            Text(
                c,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (beat == 2) Color(0xFFE5484D).copy(alpha = 0.18f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                color = if (beat == 2) Color(0xFFE5484D) else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ReelAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Real icons of the supported apps installed on this phone (falls back to the first few). */
@Composable
fun SupportedAppsRow() {
    val context = LocalContext.current
    val apps = remember {
        val installed = TrackedApps.installedApps(context)
        (installed.ifEmpty { TrackedApps.known }).take(6)
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
            ) { Text(tr("Step %1\$s", scene + 1), color = Brand.GoldDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GuideStep(1, tr("Tap \"Scroll Counter\" (under Downloaded / Installed apps)"), scene == 0)
            GuideStep(2, tr("Turn on \"Use Scroll Counter\""), scene == 1)
            GuideStep(3, tr("Tap \"Allow\""), scene == 2)
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
        Text(tr("‹  Accessibility"), color = SetText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Text(tr("DOWNLOADED APPS"), color = SetMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        MiniRow(tr("Accessibility Menu"), tr("Off"), false)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                .background(Brand.Gold.copy(alpha = 0.22f * hl))
                .border(1.dp, Brand.Gold.copy(alpha = hl), RoundedCornerShape(8.dp))
        ) { MiniRow("Scroll Counter", tr("Off"), true) }
        MiniRow(tr("Select to Speak"), tr("Off"), false)
        MiniRow(tr("TalkBack"), tr("Off"), false)
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
            Text(tr("Use Scroll Counter"), color = SetText, fontSize = 10.sp, modifier = Modifier.weight(1f))
            MiniSwitch(toggle)
        }
        Spacer(Modifier.height(10.dp))
        Text(tr("Options"), color = SetMuted, fontSize = 8.sp)
        MiniRow(tr("Scroll Counter shortcut"), tr("Off"), false)
        MiniRow(tr("Settings"), "", false)
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
            tr("Allow Scroll Counter to have full control of your device?"),
            color = SetText, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF3A4442)).padding(horizontal = 12.dp, vertical = 5.dp)
            ) { Text(tr("Deny"), color = SetText, fontSize = 9.sp) }
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(lerp(Color(0xFF0E8C7F), Brand.Gold, hl))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) { Text(tr("Allow"), color = if (hl > 0.5f) Brand.GoldDeep else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
