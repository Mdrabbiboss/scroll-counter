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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloat
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

        item { HeroCard(today.totalScrolls, limit, streak, onEditLimit) }
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
                    peak?.let { "${hours[it]} reels" } ?: "No data yet", Modifier.weight(1f), tint = MaterialTheme.colorScheme.secondary
                )
                val top = today.topApp
                StatTile(
                    Icons.Rounded.TouchApp, "Most scrolled",
                    top?.let { TrackedApps.forKey(it.key)?.name } ?: "—",
                    top?.let { "${it.scrolls} reels" } ?: "Nothing yet", Modifier.weight(1f), tint = Color(0xFFE1306C)
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
private fun HeroCard(count: Int, limit: Int, streak: Int, onEditLimit: () -> Unit) {
    val ratio = if (limit > 0) count.toFloat() / limit else 0f
    val progress by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(900), label = "ring")
    val shown by animateIntAsState(count, tween(700), label = "count")
    val mood = com.rabbi.scrollcounter.face.Mood.of(count, limit)
    val state = com.rabbi.scrollcounter.widget.WidgetState(count, limit, streak)
    val ringState = state.copy(total = (progress * limit).toInt().coerceAtLeast(if (count > 0) 1 else 0))
    val message = when {
        count >= limit -> "Limit reached — time for a real-world break."
        ratio >= 0.8f -> "Almost there — only ${limit - count} reels left."
        count == 0 -> "A clean slate. Keep it mindful today."
        else -> "${limit - count} reels left in today's limit."
    }
    // Calm, still background — no moving sparkles behind the numbers.
    val phase = 0f
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    Box(
        Modifier.fillMaxWidth()
            .drawBehind {
                drawIntoCanvas {
                    com.rabbi.scrollcounter.widget.WidgetArt.drawScene(
                        it.nativeCanvas, size.width, size.height, density, state, phase, 30.dp.toPx()
                    )
                }
            }
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Top row: mood headline + streak
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    com.rabbi.scrollcounter.widget.WidgetArt.headline(state),
                    color = Color.White, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppGlyph(com.rabbi.scrollcounter.face.GlyphArt.Glyph.FLAME, 18.dp, dim = streak == 0)
                    Spacer(Modifier.width(6.dp))
                    Text(if (streak == 1) "1 day" else "$streak days", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))

            // The glowing ring with today's count
            Box(Modifier.size(224.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawIntoCanvas {
                        com.rabbi.scrollcounter.widget.WidgetArt.drawRing(it.nativeCanvas, 0f, 0f, size.minDimension, ringState, density, phase)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$shown",
                        style = MaterialTheme.typography.displayLarge.copy(
                            shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(0f, 6f), 18f)
                        ),
                        color = Color.White, fontWeight = FontWeight.Black
                    )
                    Text("of $limit reels today", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                }
            }
            Spacer(Modifier.height(10.dp))

            // Buddy + message
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AnimatedFace(mood, 58.dp, bounceKey = count)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(mood.label, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f))
                }
            }
            Spacer(Modifier.height(16.dp))

            // Daily limit + a clear "Edit limit" button
            val src = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Row(
                Modifier.fillMaxWidth().pressScale(src).clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.22f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp))
                    .clickable(interactionSource = src, indication = null, onClick = onEditLimit)
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("DAILY LIMIT", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f), letterSpacing = 1.2.sp)
                    Text("$limit reels", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${(ratio * 100).toInt().coerceAtMost(999)}% used", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFFFE6A3), Color(0xFFFFC857), Color(0xFFFF9F43))))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Edit, null, tint = Color(0xFF4A2A00), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit limit", color = Color(0xFF4A2A00), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
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
                Text("See today's reels without opening the app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
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
            "Allow Scroll Counter in Accessibility settings so it can count the Reels and Shorts you watch. It never reads your messages.",
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
                Text("Reels aren't being counted right now.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onResume, colors = ButtonDefaults.buttonColors(containerColor = Brand.Warn, contentColor = Color(0xFF2A1A00))) { Text("Resume") }
        }
    }
}
