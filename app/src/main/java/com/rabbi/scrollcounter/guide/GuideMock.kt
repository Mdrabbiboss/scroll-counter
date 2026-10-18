package com.rabbi.scrollcounter.guide

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.rabbi.scrollcounter.ui.tr
import kotlin.math.min
import kotlin.math.sin

// Design size of the little phone screen; it's scaled to whatever space it gets (the PiP window).
private const val W = 270f
private const val H = 480f
private const val BAR_TOP = 392f            // caption panel starts here
private const val CONTENT_TOP = 64f
private const val ROW_H = 46f
private const val GROUP_GAP = 10f
private const val HEADER_H = 24f
private const val APP_HEADER_H = 92f

/** Each scene's length: the hand comes in, taps, the result shows, then the next scene. */
internal const val SCENE_MS = 3000L

private class Palette(dark: Boolean, os: Os) {
    val bg = if (dark) (if (os == Os.SAMSUNG) Color(0xFF000000) else Color(0xFF111316)) else Color(0xFFF3F4F6)
    val card = if (dark) Color(0xFF1C1D20) else Color.White
    val text = if (dark) Color(0xFFF1F1F1) else Color(0xFF15171A)
    val sub = if (dark) Color(0xFF9AA0A6) else Color(0xFF6B7078)
    val header = if (dark) Color(0xFF8AB4F8) else Color(0xFF3567D6)
    val dialog = if (dark) Color(0xFF26272B) else Color.White
    val toggleOff = if (dark) Color(0xFF5B5E63) else Color(0xFFBFC3C8)
}

/** Where a row sits on the design canvas. */
private fun rowRects(screen: MScreen, skin: Skin): List<List<Rect>> {
    var y = CONTENT_TOP + if (screen.appHeader) APP_HEADER_H else 0f
    val side = if (skin.cards) 10f else 0f
    return screen.groups.map { g ->
        if (g.header != null) y += HEADER_H
        val rects = g.rows.map { Rect(side, y, W - side, y + ROW_H).also { y += ROW_H } }
        y += GROUP_GAP
        rects
    }
}

private val dotsRect = Rect(W - 40f, 18f, W - 6f, 54f)
private val menuRect = Rect(W - 180f, 46f, W - 8f, 92f)

/** The dialog card's rect and its buttons' rects. */
private fun dialogRects(d: Overlay.Dialog): Pair<Rect, List<Rect>> {
    val h = 64f + (if (d.body != null) 48f else 0f) + 46f
    val top = BAR_TOP - 12f - h
    val card = Rect(12f, top, W - 12f, BAR_TOP - 12f)
    val n = d.buttons.size
    val bw = (card.width - 16f) / n
    val btns = (0 until n).map { i -> Rect(card.left + 8f + i * bw, card.bottom - 44f, card.left + 8f + (i + 1) * bw, card.bottom - 6f) }
    return card to btns
}

private fun targetRect(scene: Scene, skin: Skin): Rect? = when (val t = scene.target) {
    is Target.Row -> rowRects(scene.screen, skin).getOrNull(t.group)?.getOrNull(t.row)?.let { r ->
        if (scene.screen.groups[t.group].rows[t.row].toggle != null) Rect(r.right - 64f, r.top + 6f, r.right - 6f, r.bottom - 6f) else r
    }
    Target.Dots -> dotsRect
    Target.InOverlay -> when (val o = scene.overlay) {
        is Overlay.Dialog -> dialogRects(o).second.getOrNull(o.target)
        is Overlay.Menu -> menuRect
        else -> null
    }
    null -> null
}

private fun ease(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

/**
 * The animated guide: a copy of this phone's Settings screen with a hand that taps what to tap,
 * and the step's caption underneath. [scenes] loop; [startAt] restarts them from the first.
 */
@Composable
internal fun GuideMock(skin: Skin, scenes: List<Scene>, dark: Boolean, startAt: Long, modifier: Modifier = Modifier) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) androidx.compose.runtime.withFrameMillis { value = System.currentTimeMillis() }
    }
    val elapsed = (now - startAt).coerceAtLeast(0L)
    val index = ((elapsed / SCENE_MS) % scenes.size).toInt()
    val t = (elapsed % SCENE_MS).toFloat()
    val scene = scenes[index]
    val pal = remember(dark, skin.os) { Palette(dark, skin.os) }
    val context = LocalContext.current
    val appIcon: ImageBitmap? = remember {
        runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val scale = min(maxWidth.value / W, maxHeight.value / H)
        val outer = LocalDensity.current
        // Draw in design units: 1 "dp" here = scale real dp, text included (font scale ignored).
        CompositionLocalProvider(LocalDensity provides Density(outer.density * scale, 1f)) {
            Box(Modifier.size(W.dp, H.dp).clip(RoundedCornerShape(18.dp)).background(pal.bg)) {
                val pressAt = 1100f
                val flipped = (scene.target as? Target.Row)?.flips == true && t > pressAt + 200f
                Screen(scene.screen, skin, pal, appIcon, flipped, ((t - pressAt - 200f) / 250f).coerceIn(0f, 1f))
                scene.overlay?.let { DrawOverlay(it, pal, skin, ease(t / 250f)) }

                // Glow around what to tap, then the hand.
                val target = targetRect(scene, skin)
                if (target != null) {
                    val pulse = if (t < pressAt) 0.55f + 0.45f * sin(t / 140f).let { it * it } else 0f
                    if (pulse > 0f) Box(
                        Modifier.offset(target.left.dp - 2.dp, target.top.dp - 2.dp)
                            .size(target.width.dp + 4.dp, target.height.dp + 4.dp)
                            .border(2.dp, skin.accent.copy(alpha = pulse), RoundedCornerShape(12.dp))
                    )
                    // Tap ripple.
                    val rp = ((t - pressAt) / 500f).coerceIn(0f, 1f)
                    if (t > pressAt && rp < 1f) {
                        val r = 8f + 26f * rp
                        Box(
                            Modifier.offset((target.center.x - r).dp, (target.center.y - r).dp).size((2 * r).dp)
                                .clip(CircleShape).background(Color.White.copy(alpha = 0.45f * (1f - rp)))
                        )
                    }
                    val move = ease((t - 300f) / 700f)
                    val sx = (target.center.x + 46f).coerceAtMost(W - 30f)
                    val sy = (target.center.y + 120f).coerceAtMost(BAR_TOP + 40f)
                    val hx = sx + (target.center.x - sx) * move
                    val hy = sy + (target.center.y - sy) * move
                    val press = if (t in pressAt..(pressAt + 250f)) 0.86f else 1f
                    val handAlpha = (min(t / 250f, (SCENE_MS - t) / 300f)).coerceIn(0f, 1f)
                    Icon(
                        Icons.Rounded.TouchApp, null, tint = Color.White,
                        modifier = Modifier.offset((hx - 15f).dp, (hy - 6f).dp).size(38.dp)
                            .graphicsLayer { scaleX = press; scaleY = press; alpha = handAlpha; shadowElevation = 6f }
                    )
                }
                CaptionBar(index + 1, scene.caption, skin)
            }
        }
    }
}

@Composable
private fun Screen(screen: MScreen, skin: Skin, pal: Palette, appIcon: ImageBitmap?, flipped: Boolean, flipProgress: Float) {
    // Top bar.
    Row(Modifier.offset(0.dp, 18.dp).fillMaxWidth().height(36.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = pal.text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(screen.title, color = pal.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.MoreVert, null, tint = pal.text, modifier = Modifier.size(20.dp))
    }
    if (screen.appHeader) {
        Column(Modifier.offset(0.dp, CONTENT_TOP.dp).fillMaxWidth().height(APP_HEADER_H.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(appIcon, 48f)
            Spacer(Modifier.height(6.dp))
            Text("Scroll Counter", color = pal.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
    val rects = rowRects(screen, skin)
    screen.groups.forEachIndexed { gi, g ->
        val rs = rects[gi]
        if (rs.isEmpty()) return@forEachIndexed
        if (g.header != null) Text(
            g.header, color = pal.header, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.offset(16.dp, (rs.first().top - HEADER_H + 5f).dp)
        )
        if (skin.cards) Box(
            Modifier.offset(rs.first().left.dp, rs.first().top.dp)
                .size(rs.first().width.dp, (rs.last().bottom - rs.first().top).dp)
                .clip(RoundedCornerShape(skin.radius.dp)).background(pal.card)
        )
        g.rows.forEachIndexed { ri, row ->
            val r = rs[ri]
            val on = row.toggle == true || (row.toggle == false && flipped && ri == 0 && gi == 0)
            val label = if (skin.os == Os.SAMSUNG && gi == 0 && ri == 0 && row.toggle != null) (if (on) "On" else row.title) else row.title
            Row(
                Modifier.offset(r.left.dp, r.top.dp).size(r.width.dp, r.height.dp).padding(horizontal = 14.dp)
                    .graphicsLayer { alpha = if (row.dim) 0.45f else 1f },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (row.app) { AppIcon(appIcon, 26f); Spacer(Modifier.width(10.dp)) }
                else if (row.icon != null) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(row.iconColor), contentAlignment = Alignment.Center) {
                        Icon(row.icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(label, color = pal.text, fontSize = 13.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (row.toggle != null && gi == 0 && ri == 0) FontWeight.SemiBold else FontWeight.Normal)
                    if (row.sub != null) Text(row.sub, color = if (skin.os == Os.SAMSUNG && !row.dim && row.sub.endsWith("apps")) skin.accent else pal.sub,
                        fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (row.toggle != null) {
                    val p = if (row.toggle == true) 1f else if (gi == 0 && ri == 0) flipProgress else 0f
                    Toggle(p, skin.accent, pal.toggleOff)
                } else if (!skin.cards && !row.app && row.sub == null) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = pal.sub, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?, size: Float) {
    if (icon != null) Image(icon, null, Modifier.size(size.dp).clip(CircleShape))
    else Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFF8C33A)))
}

@Composable
private fun Toggle(p: Float, accent: Color, off: Color) {
    val track = androidx.compose.ui.graphics.lerp(off, accent, p)
    Box(Modifier.size(40.dp, 22.dp).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.offset((3f + 18f * p).dp, 3.dp).size(16.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun DrawOverlay(o: Overlay, pal: Palette, skin: Skin, appear: Float) {
    when (o) {
        is Overlay.Dialog -> {
            Box(Modifier.size(W.dp, H.dp).background(Color.Black.copy(alpha = 0.45f * appear)))
            val (card, btns) = dialogRects(o)
            Column(
                Modifier.offset(card.left.dp, (card.top + 16f * (1f - appear)).dp).size(card.width.dp, card.height.dp)
                    .graphicsLayer { alpha = appear }.shadow(8.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp)).background(pal.dialog).padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(o.title, color = pal.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                if (o.body != null) { Spacer(Modifier.height(6.dp)); Text(o.body, color = pal.sub, fontSize = 10.5.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 3) }
            }
            btns.forEachIndexed { i, b ->
                Box(Modifier.offset(b.left.dp, b.top.dp).size(b.width.dp, b.height.dp).graphicsLayer { alpha = appear }, contentAlignment = Alignment.Center) {
                    Text(o.buttons[i], color = if (i == o.target) skin.accent else pal.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        is Overlay.Menu -> Box(
            Modifier.offset(menuRect.left.dp, menuRect.top.dp).size(menuRect.width.dp, menuRect.height.dp)
                .graphicsLayer { alpha = appear }.shadow(8.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(pal.dialog)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) { Text(o.items.first(), color = pal.text, fontSize = 13.sp) }
        Overlay.Biometric -> {
            Box(Modifier.size(W.dp, H.dp).background(Color.Black.copy(alpha = 0.45f * appear)))
            Column(
                Modifier.offset(12.dp, 226.dp).size((W - 24f).dp, 154.dp).graphicsLayer { alpha = appear }
                    .clip(RoundedCornerShape(22.dp)).background(pal.dialog).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text("Confirm it's you", color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                val beat = 0.9f + 0.1f * sin(System.currentTimeMillis() / 180.0).toFloat()
                Icon(Icons.Rounded.Fingerprint, null, tint = skin.accent, modifier = Modifier.size(48.dp).graphicsLayer { scaleX = beat; scaleY = beat })
                Spacer(Modifier.height(8.dp))
                Text("Use PIN", color = skin.accent, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CaptionBar(step: Int, caption: String, skin: Skin) {
    Column(
        Modifier.offset(0.dp, BAR_TOP.dp).size(W.dp, (H - BAR_TOP).dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF231A0E), Color(0xFF120D06))))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFF8C33A)).padding(horizontal = 10.dp, vertical = 2.dp)
        ) { Text(tr("Step %1\$s", step), color = Color(0xFF4A2A00), fontSize = 11.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(6.dp))
        Text(caption, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
