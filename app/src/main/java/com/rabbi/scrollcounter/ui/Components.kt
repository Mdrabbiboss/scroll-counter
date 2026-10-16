package com.rabbi.scrollcounter.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbi.scrollcounter.data.AppStat
import com.rabbi.scrollcounter.ui.theme.Brand
import com.rabbi.scrollcounter.data.TrackedApp
import com.rabbi.scrollcounter.data.TrackedApps
import java.util.Locale

// ---------- Containers ----------

@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Frosted-glass card: a soft translucent sheen over the colored backdrop, a bright top edge, readable text. */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    container: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = isDarkTheme()
    val shape = RoundedCornerShape(26.dp)
    val fill: Brush = if (container != null) SolidColor(container) else if (dark)
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f)))
    else
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.46f)),
            start = Offset.Zero, end = Offset(900f, 900f)
        )
    val edge = if (dark)
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f)))
    else
        Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.55f), Color(0x330E8C7F)))

    var m = modifier.fillMaxWidth()
        .shadow(if (dark) 0.dp else 18.dp, shape, ambientColor = Color(0x1A0E5F57), spotColor = Color(0x330E5F57))
        .clip(shape)
        .background(fill)
        .border(1.dp, edge, shape)
    if (onClick != null) {
        val src = remember { MutableInteractionSource() }
        m = Modifier.pressScale(src).then(m).clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(m.padding(18.dp), content = content)
    }
}

/** Colored light blobs behind everything, so the translucent cards read as frosted glass. */
fun Modifier.glassBackdrop(dark: Boolean, accent: Color = Color(0xFF14B8A6)): Modifier = this.drawBehind {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.verticalGradient(
            if (dark) listOf(Color(0xFF070C0D), Color(0xFF05080A), Color(0xFF030506))
            else listOf(Color(0xFFF4F8F7), Color(0xFFF8FAFA), Color(0xFFF1F5F8))
        )
    )
    fun blob(cx: Float, cy: Float, r: Float, c: Color) =
        drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), r), r, Offset(cx, cy))
    if (dark) {
        // Aurora in today's mood colour, with a warm gold counter-light — matches the widget.
        blob(w * 0.05f, h * 0.0f, w * 1.15f, accent.copy(alpha = 0.40f))
        blob(w * 1.05f, h * 0.28f, w * 0.85f, Color(0xFF6366F1).copy(alpha = 0.16f))
        blob(w * 0.0f, h * 0.66f, w * 0.85f, accent.copy(alpha = 0.16f))
        blob(w * 1.0f, h * 0.95f, w * 0.80f, Color(0xFFF8C33A).copy(alpha = 0.08f))
    } else {
        blob(w * -0.05f, h * 0.0f, w * 1.0f, accent.copy(alpha = 0.34f))
        blob(w * 1.05f, h * 0.22f, w * 0.85f, Color(0xFF818CF8).copy(alpha = 0.20f))
        blob(w * 0.0f, h * 0.62f, w * 0.75f, Color(0xFFFBBF24).copy(alpha = 0.14f))
        blob(w * 1.0f, h * 0.9f, w * 0.9f, accent.copy(alpha = 0.22f))
    }
}

@Composable
fun ScreenHeader(title: String, eyebrow: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp)
    )
}

@Composable
fun StatusChip(text: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

// ---------- Tiles & rows ----------

@Composable
fun StatTile(icon: ImageVector, label: String, value: String, hint: String?, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    PremiumCard(modifier.fillMaxHeight()) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0.10f))))
                .border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** The app's real launcher icon when it's installed, otherwise a brand-colored letter tile. */
@Composable
fun AppIcon(app: TrackedApp?, size: Int = 40) {
    val context = LocalContext.current
    val icon = remember(app?.key) { app?.let { AppIconCache.get(context, it) } }
    val shape = RoundedCornerShape((size * 0.28f).dp)
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = app?.name,
            modifier = Modifier.size(size.dp).clip(shape)
        )
        return
    }
    val bg = Color(app?.color ?: 0xFF888888)
    val fg = Color(app?.onColor ?: 0xFFFFFFFF)
    Box(
        Modifier.size(size.dp).clip(shape).background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
        contentAlignment = Alignment.Center
    ) {
        Text((app?.name ?: "?").take(1), color = fg, fontWeight = FontWeight.Bold, fontSize = (size * 0.42f).sp)
    }
}

/** Loads each installed app's icon once from the phone and keeps it in memory. */
object AppIconCache {
    private val cache = HashMap<String, ImageBitmap?>()

    fun get(context: android.content.Context, app: TrackedApp): ImageBitmap? = synchronized(cache) {
        cache.getOrPut(app.key) {
            val pm = context.packageManager
            app.packages.firstNotNullOfOrNull { pkg ->
                runCatching { pm.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap() }.getOrNull()
            }
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Int = 8) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "bar")
    Box(
        modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(Modifier.fillMaxWidth(f).height(height.dp).clip(RoundedCornerShape(50)).background(color))
    }
}

/** Bars use the brand color, except near-black brands which use the theme's text color so they show in dark mode. */
@Composable
fun appBarColor(app: TrackedApp?): Color = when {
    app == null -> MaterialTheme.colorScheme.primary
    app.color == 0xFF111111 -> MaterialTheme.colorScheme.onSurface
    else -> Color(app.color)
}

@Composable
fun AppStatRow(stat: AppStat, maxScrolls: Int, total: Int) {
    val app = TrackedApps.forKey(stat.key)
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app?.name ?: stat.key, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${stat.scrolls}", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(7.dp))
            ProgressBar(if (maxScrolls > 0) stat.scrolls.toFloat() / maxScrolls else 0f, appBarColor(app))
            Spacer(Modifier.height(5.dp))
            Row {
                Text(formatDistance(stat.meters), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                val pct = if (total > 0) (stat.scrolls * 100f / total).toInt() else 0
                Text("$pct%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A tappable settings row: icon, title, subtitle, and a trailing control or chevron. */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val base = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
    Row(
        (if (onClick != null) {
            val src = remember { MutableInteractionSource() }
            Modifier.pressScale(src).then(base).clickable(interactionSource = src, indication = LocalIndication.current, onClick = onClick)
        } else base).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(iconTint.copy(alpha = 0.30f), iconTint.copy(alpha = 0.10f))))
                .border(1.dp, iconTint.copy(alpha = 0.30f), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        }
        when {
            trailing != null -> { Spacer(Modifier.width(8.dp)); trailing() }
            onClick != null -> Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pill-shaped single-choice toggle (e.g. 7 days / 30 days). */
@Composable
fun SegmentedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(50))
            .background(if (isDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.45f))
            .border(1.dp, if (isDarkTheme()) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (on) (if (isDarkTheme()) Color.White.copy(alpha = 0.16f) else Color.White) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

/** 24 bars, one per hour of the day. */
@Composable
fun HourlyChart(hours: IntArray, modifier: Modifier = Modifier, highlightHour: Int? = null) {
    val maxV = (hours.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
    val base = MaterialTheme.colorScheme.primary
    val dim = base.copy(alpha = 0.28f)
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val slot = size.width / 24f
            val w = slot * 0.62f
            hours.forEachIndexed { i, v ->
                val h = if (v == 0) 3.dp.toPx() else (v / maxV) * size.height
                drawRoundRect(
                    color = when { v == 0 -> empty; i == highlightHour -> base; else -> dim },
                    topLeft = Offset(i * slot + (slot - w) / 2, size.height - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(w / 2, w / 2)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("12a", "6a", "12p", "6p").forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
fun FullCenter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/**
 * Premium toggle: animated thumb that grows when on, a check / cross icon inside the thumb,
 * brand-colored track and a light haptic tick on every change.
 */
@Composable
fun PremiumSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onCheckedChange(it)
        },
        thumbContent = {
            Icon(
                if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize)
            )
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Brand.Gold,
            checkedIconColor = Brand.GoldDeep,
            checkedBorderColor = Brand.GoldDeep.copy(alpha = 0.35f),
            uncheckedThumbColor = Color.White.copy(alpha = 0.85f),
            uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            uncheckedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
        )
    )
}

/** Clear, solid action button used inside setting rows (Manage, Test, Allow, Enable). */
@Composable
fun PillButton(text: String, icon: ImageVector? = null, gold: Boolean = false, onClick: () -> Unit) {
    val bg = if (gold) Brand.Gold else MaterialTheme.colorScheme.primary
    val fg = if (gold) Brand.GoldDeep else MaterialTheme.colorScheme.onPrimary
    androidx.compose.material3.Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        elevation = androidx.compose.material3.ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, fontWeight = FontWeight.Bold)
    }
}

/** Gently shrinks the element while it's pressed, then springs back — a tactile, premium feel. */
@Composable
fun Modifier.pressScale(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "press"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

// ---------- Formatting ----------

fun formatDistance(meters: Float): String =
    if (meters < 1000f) String.format(Locale.US, "%.0f m", meters)
    else String.format(Locale.US, "%.2f km", meters / 1000f)

/** A relatable comparison for the distance scrolled. */
fun distanceComparison(meters: Float): String = when {
    meters < 1f -> "Nothing scrolled yet"
    meters < 330f -> "≈ ${(meters / 3f).toInt().coerceAtLeast(1)} building floors"
    meters < 8849f -> String.format(Locale.US, "≈ %.1f Eiffel Towers", meters / 330f)
    else -> String.format(Locale.US, "≈ %.2f× Mount Everest", meters / 8849f)
}

fun formatHour(h: Int): String = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}

/** Scroll Counter's own drawn icons (no system emoji): fire, sparkle, bolt, check, or the buddy. */
@Composable
fun AppGlyph(glyph: com.rabbi.scrollcounter.face.GlyphArt.Glyph?, size: androidx.compose.ui.unit.Dp, dim: Boolean = false, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        drawIntoCanvas {
            if (glyph == null) com.rabbi.scrollcounter.face.FaceRenderer.draw(
                it.nativeCanvas, this.size.width / 2, this.size.height / 2, this.size.minDimension / 2 * 0.92f,
                com.rabbi.scrollcounter.face.Mood.FRESH
            ) else com.rabbi.scrollcounter.face.GlyphArt.draw(it.nativeCanvas, glyph, 0f, 0f, this.size.minDimension, dim)
        }
    }
}
