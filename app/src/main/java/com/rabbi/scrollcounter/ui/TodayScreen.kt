package com.rabbi.scrollcounter.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.data.TrackedApps
import com.rabbi.scrollcounter.tracker.DeviceStatus
import com.rabbi.scrollcounter.ui.theme.Brand
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    store: ScrollStore,
    tick: Long,
    status: DeviceStatus,
    onEnableTracking: () -> Unit,
    onEditLimit: () -> Unit,
    onAddWidget: () -> Unit = {},
) {
    val now = LocalDate.now()
    val today = remember(tick) { store.dayStats(now) }
    val hours = remember(tick) { store.hourly(now) }
    val limit = remember(tick) { store.dailyLimit }
    val paused = remember(tick) { store.paused }
    val streak = remember(tick) { store.streak() }
    val peak = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }
    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(greeting, now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))) {
                when {
                    !status.trackerOn -> StatusChip("Off", MaterialTheme.colorScheme.error)
                    paused -> StatusChip("Paused", Brand.Warn)
                    else -> StatusChip("Tracking", Brand.Good)
                }
            }
        }
        if (!status.trackerOn) item { SetupBanner(onEnableTracking) }
        else if (paused) item { PausedBanner { store.paused = false } }

        item { HeroCard(today.totalScrolls, limit, onEditLimit) }
        if (status.trackerOn && !status.widgetPlaced) item { WidgetBanner(today.totalScrolls, limit, onAddWidget) }

        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.Straighten, "Distance", formatDistance(today.totalMeters), distanceComparison(today.totalMeters), Modifier.weight(1f))
                StatTile(
                    Icons.Rounded.EmojiEvents, "Streak",
                    if (streak == 1) "1 day" else "$streak days",
                    "under your limit", Modifier.weight(1f), tint = Brand.Warn
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    Icons.Rounded.Schedule, "Peak hour",
                    peak?.let { formatHour(it) } ?: "—",
                    peak?.let { "${hours[it]} scrolls" } ?: "No data yet", Modifier.weight(1f), tint = MaterialTheme.colorScheme.secondary
                )
                val top = today.topApp
                StatTile(
                    Icons.Rounded.TouchApp, "Most scrolled",
                    top?.let { TrackedApps.forKey(it.key)?.name } ?: "—",
                    top?.let { "${it.scrolls} scrolls" } ?: "Nothing yet", Modifier.weight(1f), tint = Color(0xFFE1306C)
                )
            }
        }

        item {
            PremiumCard {
                Text("Activity by hour", style = MaterialTheme.typography.titleMedium)
                Text("When you scroll the most today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                HourlyChart(hours, highlightHour = peak)
            }
        }

        item {
            PremiumCard {
                Text("By app", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                if (today.apps.isEmpty()) {
                    EmptyNote(
                        if (status.trackerOn) "No scrolling yet today. Open Facebook, TikTok, Instagram or YouTube and it shows up here."
                        else "Turn on tracking to start counting."
                    )
                } else {
                    val max = today.apps.maxOf { it.scrolls }
                    today.apps.sortedByDescending { it.scrolls }.forEach { AppStatRow(it, max, today.totalScrolls) }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(count: Int, limit: Int, onEditLimit: () -> Unit) {
    val ratio = if (limit > 0) count.toFloat() / limit else 0f
    val progress by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(900), label = "ring")
    val over = count >= limit
    val brush = when {
        over -> Brush.sweepGradient(listOf(Brand.Over, Brand.Over))
        ratio >= 0.8f -> Brush.sweepGradient(listOf(Brand.Warn, Brand.Over))
        else -> Brush.sweepGradient(listOf(Brand.RingStart, Brand.RingEnd))
    }
    val message = when {
        over -> "Limit reached. Time for a real-world break."
        ratio >= 0.8f -> "Almost there — ${limit - count} scrolls left."
        count == 0 -> "A clean slate. Let's keep it mindful."
        else -> "${limit - count} scrolls left today."
    }

    val shown by animateIntAsState(count, tween(700), label = "count")
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brand.hero)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f))), RoundedCornerShape(28.dp))
            .drawBehind {
                // Soft light glows for depth.
                val r1 = size.width * 0.75f
                val c1 = androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.02f)
                drawCircle(Brush.radialGradient(listOf(Brand.Glow.copy(alpha = 0.32f), Color.Transparent), c1, r1), r1, c1)
                val r2 = size.width * 0.6f
                val c2 = androidx.compose.ui.geometry.Offset(0f, size.height)
                drawCircle(Brush.radialGradient(listOf(Brand.RingEnd.copy(alpha = 0.22f), Color.Transparent), c2, r2), r2, c2)
            }
    ) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(212.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().rotate(-90f)) {
                    val stroke = 16.dp.toPx()
                    val tl = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
                    val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    drawArc(Color.White.copy(alpha = 0.10f), 0f, 360f, false, tl, arc, style = Stroke(stroke))
                    if (progress > 0f) {
                        drawArc(brush, 0f, 360f * progress, false, tl, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$shown", style = MaterialTheme.typography.displayLarge, color = Color.White)
                    Text("SCROLLS TODAY", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.height(14.dp))
            val mood = com.rabbi.scrollcounter.face.Mood.of(count, limit)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedFace(mood, 40.dp, bounceKey = count)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(mood.label, style = MaterialTheme.typography.labelLarge, color = Color(mood.face), fontWeight = FontWeight.SemiBold)
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.10f))
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("DAILY LIMIT", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                    Text("$limit scrolls · ${(ratio * 100).toInt()}% used", style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onEditLimit) {
                    Icon(Icons.Rounded.Edit, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Change", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun WidgetBanner(count: Int, limit: Int, onAdd: () -> Unit) {
    val mood = com.rabbi.scrollcounter.face.Mood.of(count, limit)
    PremiumCard(container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f), onClick = onAdd) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Mini preview of the widget
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(Brand.hero),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(34.dp)) {
                    drawIntoCanvas {
                        com.rabbi.scrollcounter.face.FaceRenderer.draw(it.nativeCanvas, size.width / 2, size.height / 2, size.minDimension / 2 * 0.96f, mood)
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Put your count on the home screen", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("See today's scrolls without opening the app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
            Spacer(Modifier.width(8.dp))
            PillButton("Add", gold = true, onClick = onAdd)
        }
    }
}

@Composable
private fun SetupBanner(onEnable: () -> Unit) {
    PremiumCard(container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)) {
        Text("Turn on scroll tracking", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(4.dp))
        Text(
            "Allow Scroll Counter in Accessibility settings so it can count scrolls in your social apps. Nothing on your screen is read.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onEnable) { Text("Enable tracking") }
    }
}

@Composable
private fun PausedBanner(onResume: () -> Unit) {
    PremiumCard(container = Brand.Warn.copy(alpha = 0.14f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PauseCircle, null, tint = Brand.Warn)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Tracking is paused", style = MaterialTheme.typography.titleMedium)
                Text("Scrolls aren't being counted right now.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onResume, colors = ButtonDefaults.buttonColors(containerColor = Brand.Warn, contentColor = Color(0xFF2A1A00))) { Text("Resume") }
        }
    }
}
