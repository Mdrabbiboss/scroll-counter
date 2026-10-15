package com.rabbi.scrollcounter.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import com.rabbi.scrollcounter.data.TrackedApp
import com.rabbi.scrollcounter.data.TrackedApps
import java.util.Locale

// ---------- Containers ----------

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val colors = CardDefaults.cardColors(containerColor = container)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(18.dp), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(18.dp), content = content)
        }
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
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.13f)),
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

/** Rounded-square brand tile with the app's initial, like a mini app icon. */
@Composable
fun AppIcon(app: TrackedApp?, size: Int = 40) {
    val bg = Color(app?.color ?: 0xFF888888)
    val fg = Color(app?.onColor ?: 0xFFFFFFFF)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.3f).dp)).background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape((size * 0.3f).dp)),
        contentAlignment = Alignment.Center
    ) {
        Text((app?.name ?: "?").take(1), color = fg, fontWeight = FontWeight.Bold, fontSize = (size * 0.42f).sp)
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
        (if (onClick != null) base.clickable(onClick = onClick) else base).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(iconTint.copy(alpha = 0.12f)),
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
        modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
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
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedIconColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        )
    )
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
