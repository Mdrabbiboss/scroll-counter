package com.rabbi.scrollcounter.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Check
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.HourglassTop
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
import androidx.compose.runtime.setValue
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
    onShareDay: () -> Unit = {},
    onShareBadge: (com.rabbi.scrollcounter.data.Badges.Badge) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    var showBadges by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showBadges) BadgesSheet(store, tick, onShareBadge = { showBadges = false; onShareBadge(it) }, onDismiss = { showBadges = false })
    val now = LocalDate.now()
    val today = remember(tick) { store.dayStats(now) }
    val hours = remember(tick) { store.hourly(now) }
    // Counted against the limit: after a squat fresh start only the reels since then.
    val usedTotal = remember(tick) { store.todayUsed() }
    val usedApps = remember(tick) { store.todayUsedApps(today.apps) }
    val limit = remember(tick) { store.dailyLimit }
    val todayDate = rememberToday()
    val limitLocked = remember(tick, todayDate) { store.limitLockedToday }
    val buddySounds = remember(tick) { store.buddySounds }
    val paused = remember(tick) { store.paused }
    val streak = remember(tick) { store.streak() }
    val peak = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }
    val greeting = when (LocalTime.now().hour) { in 5..11 -> tr("Good morning"); in 12..16 -> tr("Good afternoon"); else -> tr("Good evening") }

    // Pull down to reload: re-reads everything (counts, backup copies, tracking status, widget).
    PullToReload(onRefresh) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(greeting, now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))) {
                when {
                    !status.trackerOn -> StatusChip(tr("Off"), MaterialTheme.colorScheme.error)
                    paused -> StatusChip(tr("Paused"), Brand.Warn)
                    else -> StatusChip(tr("Tracking"), Brand.Good)
                }
            }
        }
        if (!status.trackerOn) item { SetupBanner(onEnableTracking) }
        else if (paused) item { PausedBanner { store.paused = false } }

        item { HeroCard(usedTotal, limit, streak, usedApps, onEditLimit, limitLocked, buddySounds) }
        if (status.trackerOn && !status.widgetPlaced) item { WidgetBanner(usedTotal, limit, onAddWidget) }
        item { BadgesCard(store, tick, onOpen = { showBadges = true }, onShare = onShareDay) }

        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.Straighten, tr("Distance"), formatDistance(today.totalMeters), distanceComparison(today.totalMeters), Modifier.weight(1f))
                StatTile(
                    Icons.Rounded.EmojiEvents, tr("Streak"),
                    if (streak == 1) tr("1 day") else tr("%1\$s days", streak),
                    tr("under your limit"), Modifier.weight(1f), tint = Brand.Warn
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    Icons.Rounded.Schedule, tr("Peak hour"),
                    peak?.let { formatHour(it) } ?: "—",
                    peak?.let { tr("%1\$s reels", hours[it]) } ?: tr("No data yet"), Modifier.weight(1f), tint = MaterialTheme.colorScheme.secondary
                )
                val top = today.topApp
                StatTile(
                    Icons.Rounded.TouchApp, tr("Most scrolled"),
                    top?.let { TrackedApps.forKey(it.key)?.name } ?: "—",
                    top?.let { tr("%1\$s reels", it.scrolls) } ?: tr("Nothing yet"), Modifier.weight(1f), tint = Color(0xFFE1306C)
                )
            }
        }

        item {
            // Time spent on reels today, and how long one reel holds you on average.
            val secs = today.totalSeconds
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    Icons.Rounded.Timer, tr("Watch time"),
                    if (secs == 0) tr("0 min") else formatDuration(secs),
                    tr("on reels today"), Modifier.weight(1f), tint = Color(0xFF38BDF8)
                )
                StatTile(
                    Icons.Rounded.HourglassTop, tr("Per reel"),
                    if (today.totalScrolls == 0 || secs == 0) "—" else formatDuration((secs / today.totalScrolls).coerceAtLeast(1)),
                    tr("average time"), Modifier.weight(1f), tint = Brand.Gold
                )
            }
        }

        item {
            PremiumCard {
                Text(tr("Activity by hour"), style = MaterialTheme.typography.titleMedium)
                Text(tr("When you scroll the most today"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                HourlyChart(hours, highlightHour = peak)
            }
        }

        item {
            PremiumCard {
                Text(tr("By app"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                if (today.apps.isEmpty()) {
                    EmptyNote(
                        if (status.trackerOn) tr("No scrolling yet today. Open Facebook, TikTok, Instagram or YouTube and it shows up here.")
                        else tr("Turn on tracking to start counting.")
                    )
                } else {
                    val max = today.apps.maxOf { it.scrolls }
                    today.apps.sortedByDescending { it.scrolls }.forEach { AppStatRow(it, max, today.totalScrolls) }
                }
            }
        }
    }    }

}

@Composable
private fun HeroCard(count: Int, limit: Int, streak: Int, apps: List<com.rabbi.scrollcounter.data.AppStat>, onEditLimit: () -> Unit, limitLocked: Boolean = false, soundsEnabled: Boolean = true) {
    val ratio = if (limit > 0) count.toFloat() / limit else 0f
    val progress by animateFloatAsState(ratio.coerceIn(0f, 1f), tween(900), label = "ring")
    val shown by animateIntAsState(count, tween(700), label = "count")
    val mood = com.rabbi.scrollcounter.face.Mood.of(count, limit)
    val state = com.rabbi.scrollcounter.widget.WidgetState(count, limit, streak)
    val ringState = state.copy(total = (progress * limit).toInt().coerceAtLeast(if (count > 0) 1 else 0))
    val message = when {
        count >= limit -> tr("Limit reached — time for a real-world break.")
        ratio >= 0.8f -> tr("Almost there — only %1\$s reels left.", limit - count)
        count == 0 -> tr("A clean slate. Keep it mindful today.")
        else -> tr("%1\$s reels left in today's limit.", limit - count)
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
                    Text(if (streak == 1) tr("1 day") else tr("%1\$s days", streak), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(10.dp))

            // The glowing ring with today's count
            val ringSize = 184.dp
            Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawIntoCanvas {
                        com.rabbi.scrollcounter.widget.WidgetArt.drawRing(it.nativeCanvas, 0f, 0f, size.minDimension, ringState, density, phase, headDot = false)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$shown",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 48.sp, lineHeight = 52.sp,
                            shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(0f, 6f), 18f)
                        ),
                        color = Color.White, fontWeight = FontWeight.Black
                    )
                    Text(tr("of %1\$s reels today", limit), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                }
                // A tiny animated buddy rides the tip of the ring (same geometry as WidgetArt.ring).
                val stroke = ringSize.value * 0.095f
                val radius = ringSize.value / 2f - (stroke / 2f + 2f)
                val sweep = (progress * 360f).coerceAtLeast(if (progress > 0f) 6f else 0f)
                val ang = Math.toRadians((-90f + sweep).toDouble())
                AnimatedFace(
                    mood, 28.dp, bounceKey = count, still = true,
                    modifier = Modifier.offset(
                        x = (kotlin.math.cos(ang) * radius).toFloat().dp,
                        y = (kotlin.math.sin(ang) * radius).toFloat().dp
                    )
                )
            }
            // Where today's reels came from: app icons with their counts, in one glass pill.
            if (apps.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.25f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    apps.sortedByDescending { it.scrolls }.take(3).forEachIndexed { i, a ->
                        if (i > 0) {
                            Spacer(Modifier.width(12.dp))
                            Box(Modifier.width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.18f)))
                            Spacer(Modifier.width(12.dp))
                        }
                        AppIcon(TrackedApps.forKey(a.key), size = 26)
                        Spacer(Modifier.width(8.dp))
                        Text("${a.scrolls}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            // Buddy + message
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // The buddy is a little pet: it makes a mood sound now and then, and when tapped.
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
                val soundsOn = soundsEnabled
                var perf by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.rabbi.scrollcounter.face.BuddyVoice.Performance?>(null) }
                androidx.compose.runtime.LaunchedEffect(mood, soundsOn) {
                    if (!soundsOn) return@LaunchedEffect
                    kotlinx.coroutines.delay(3_500)
                    while (true) {
                        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
                            com.rabbi.scrollcounter.face.BuddyVoice.speak(ctx, mood) { perf = it }
                        kotlinx.coroutines.delay(com.rabbi.scrollcounter.face.BuddyVoice.idleDelayMs(mood))
                    }
                }
                val petSrc = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                var pets by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
                AnimatedFace(
                    mood, 84.dp, bounceKey = if (soundsOn) count else count + pets * 100_000,
                    modifier = Modifier.clickable(interactionSource = petSrc, indication = null) {
                        pets++
                        if (soundsOn) com.rabbi.scrollcounter.face.BuddyVoice.speak(ctx, mood) { perf = it }
                    },
                    performance = perf
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(mood.label), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
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
                    Text(tr("DAILY LIMIT"), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f), letterSpacing = 1.2.sp)
                    Text(tr("%1\$s reels", limit), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(tr("%1\$s%% used", (ratio * 100).toInt().coerceAtMost(999)), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFFFE6A3), Color(0xFFFFC857), Color(0xFFFF9F43))))
                        .padding(horizontal = 16.dp, vertical = if (limitLocked) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (limitLocked) Icons.Rounded.Lock else Icons.Rounded.Edit, null, tint = Color(0xFF4A2A00), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    if (limitLocked) Column {
                        Text(tr("Locked today"), color = Color(0xFF4A2A00), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                        UnlockCountdown(
                            MaterialTheme.typography.labelMedium, Color(0xFF4A2A00).copy(alpha = 0.8f),
                            prefix = tr("Unlocks in "), weight = FontWeight.SemiBold
                        )
                    } else Text(tr("Edit limit"), color = Color(0xFF4A2A00), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
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
                Text(tr("Put your count on the home screen"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(tr("See today's reels without opening the app"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
            Spacer(Modifier.width(8.dp))
            PillButton(tr("Add"), gold = true, onClick = onAdd)
        }
    }
}

@Composable
private fun SetupBanner(onEnable: () -> Unit) {
    PremiumCard(container = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)) {
        Text(tr("Turn on scroll tracking"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(4.dp))
        Text(
            tr("Allow Scroll Counter in Accessibility settings so it can count the Reels and Shorts you watch. It never reads your messages."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onEnable) { Text(tr("Enable tracking")) }
    }
}

@Composable
private fun PausedBanner(onResume: () -> Unit) {
    PremiumCard(container = Brand.Warn.copy(alpha = 0.14f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PauseCircle, null, tint = Brand.Warn)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Tracking is paused"), style = MaterialTheme.typography.titleMedium)
                Text(tr("Reels aren't being counted right now."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onResume, colors = ButtonDefaults.buttonColors(containerColor = Brand.Warn, contentColor = Color(0xFF2A1A00))) { Text(tr("Resume")) }
        }
    }
}

/**
 * Pull-to-reload with its own animated badge: a frosted gold-rimmed disc whose arc fills and whose
 * arrow winds round as you pull, clicks (haptic) when it's ready, spins with a sweeping comet arc
 * while reloading, then pops a green tick and glides away.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PullToReload(onRefresh: () -> Unit, content: @Composable () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var refreshing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var done by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (refreshing) return@PullToRefreshBox
            refreshing = true
            scope.launch {
                runCatching { onRefresh() }
                kotlinx.coroutines.delay(950)       // long enough to see it working
                done = true
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                kotlinx.coroutines.delay(550)
                refreshing = false
                kotlinx.coroutines.delay(300)
                done = false
            }
        },
        state = state,
        modifier = Modifier.fillMaxSize(),
        indicator = { ReloadBadge(state.distanceFraction, refreshing, done, Modifier.align(Alignment.TopCenter)) }
    ) { content() }

    // A light click the moment the pull is far enough to reload.
    val armed = state.distanceFraction >= 1f && !refreshing
    androidx.compose.runtime.LaunchedEffect(armed) {
        if (armed) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
    }
}

@Composable
private fun ReloadBadge(pull: Float, refreshing: Boolean, done: Boolean, modifier: Modifier) {
    val pos by animateFloatAsState(
        if (refreshing) 1f else pull.coerceIn(0f, 1.6f),
        androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 900f), label = "pos"
    )
    if (pos <= 0.01f && !refreshing) return
    val inf = androidx.compose.animation.core.rememberInfiniteTransition(label = "reload")
    val spin by inf.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(850, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    val breathe by inf.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse), label = "breathe")
    val tick by animateFloatAsState(if (done) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.42f, stiffness = 420f), label = "tick")
    val armed = pull >= 1f || refreshing
    val armScale by animateFloatAsState(if (armed) 1.08f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.4f, stiffness = 500f), label = "arm")
    val gold = Color(0xFFF8C33A)
    val orange = Color(0xFFFF9F43)
    val green = Brand.Good
    Box(
        modifier
            .graphicsLayerCompat(pos, armScale)
            .size(50.dp)
            .androidxShadow()
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1F2427), Color(0xFF0C0F11))))
            .border(1.dp, Brush.verticalGradient(listOf(gold.copy(alpha = 0.75f), gold.copy(alpha = 0.12f))), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(38.dp)) {
            val w = 3.dp.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawCircle(Color.White.copy(alpha = 0.08f), radius = size.minDimension / 2 - inset, style = Stroke(w))
            val brush = Brush.sweepGradient(listOf(gold, orange, gold))
            when {
                done -> drawArc(green, -90f, 360f * tick.coerceIn(0f, 1f), false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                refreshing -> rotate(spin) {
                    drawArc(brush, 0f, 70f + 130f * breathe, false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                }
                else -> drawArc(brush, -90f, 330f * pull.coerceIn(0f, 1f), false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        val iconRot = if (refreshing) spin * 1.0f else pull.coerceIn(0f, 1.5f) * 300f
        if (tick < 0.5f) Icon(
            androidx.compose.material.icons.Icons.Rounded.Refresh, contentDescription = tr("Reload"),
            tint = if (armed) gold else gold.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp).rotate(iconRot)
                .androidxScale(1f - tick * 1.6f)
        )
        else Icon(
            androidx.compose.material.icons.Icons.Rounded.Check, contentDescription = null, tint = green,
            modifier = Modifier.size(24.dp).androidxScale(tick)
        )
    }
}

private fun Modifier.graphicsLayerCompat(pos: Float, scale: Float): Modifier = this.then(
    Modifier.graphicsLayer {
        val travel = 78.dp.toPx()
        translationY = pos * travel - 50.dp.toPx() + 8.dp.toPx() * pos
        alpha = (pos * 2.2f).coerceIn(0f, 1f)
        val s = (0.55f + 0.45f * pos.coerceAtMost(1f)) * scale
        scaleX = s; scaleY = s
    }
)

private fun Modifier.androidxScale(s: Float): Modifier = this.then(Modifier.graphicsLayer { scaleX = s; scaleY = s })

private fun Modifier.androidxShadow(): Modifier = this.then(
    Modifier.shadow(10.dp, androidx.compose.foundation.shape.CircleShape, ambientColor = Color(0xFFF8C33A), spotColor = Color(0xFFF8C33A))
)
