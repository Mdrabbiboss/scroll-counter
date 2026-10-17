package com.rabbi.scrollcounter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rabbi.scrollcounter.data.DayStats
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.ui.theme.Brand
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DayFmt: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEE, d MMM")

@Composable
fun HistoryScreen(store: ScrollStore, tick: Long) {
    var range by rememberSaveable { mutableIntStateOf(0) } // 0 = 7 days, 1 = 30 days
    val n = if (range == 0) 7 else 30
    val days = remember(tick, n) { store.lastDays(n) }
    val limit = remember(tick) { store.dailyLimit }
    val today = LocalDate.now()
    var selected by remember(n) { mutableIntStateOf(days.lastIndex) }
    var sheetDay by remember { mutableStateOf<LocalDate?>(null) }

    val firstDay = remember(tick) { store.firstDay }
    val tracked = days.filter { !it.date.isBefore(firstDay) }
    val total = days.sumOf { it.totalScrolls }
    val meters = days.fold(0f) { a, d -> a + d.totalMeters }
    val avg = if (tracked.isNotEmpty()) total / tracked.size else 0
    val under = tracked.count { it.totalScrolls < limit }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader(tr("History"), tr("Your scrolling over time")) }
        item { SegmentedToggle(listOf(tr("7 days"), tr("30 days")), range, { range = it }) }
        item {
            PremiumCard {
                val sel = days.getOrNull(selected) ?: days.last()
                Text(
                    if (sel.date == today) tr("Today") else sel.date.format(DayFmt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${sel.totalScrolls}", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        tr("  reels · %1\$s", formatDistance(sel.totalMeters)) + if (sel.totalSeconds > 0) " · ${formatDuration(sel.totalSeconds)}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                BarChart(days, limit, selected) { selected = it }
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Tap a bar to see that day. Dashed line = limit (%1\$s).", limit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.Insights, tr("Daily avg"), "$avg", tr("reels per day"), Modifier.weight(1f))
                StatTile(Icons.Rounded.CheckCircle, tr("On target"), "$under/${tracked.size}", tr("days under limit"), Modifier.weight(1f), tint = Brand.Good)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.TrendingUp, tr("Total"), "$total", tr("reels in %1\$s days", n), Modifier.weight(1f), tint = MaterialTheme.colorScheme.secondary)
                StatTile(Icons.Rounded.Straighten, tr("Distance"), formatDistance(meters), distanceComparison(meters), Modifier.weight(1f), tint = Brand.Warn)
            }
        }
        item {
            val secs = days.sumOf { it.totalSeconds }
            val daysWithTime = tracked.size.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.Timer, tr("Watch time"), if (secs == 0) tr("0 min") else formatDuration(secs), tr("on reels in %1\$s days", n), Modifier.weight(1f), tint = androidx.compose.ui.graphics.Color(0xFF38BDF8))
                StatTile(Icons.Rounded.HourglassTop, tr("Daily time"), if (secs == 0) tr("0 min") else formatDuration((secs / daysWithTime).coerceAtLeast(1)), tr("average per day"), Modifier.weight(1f), tint = Brand.Gold)
            }
        }
        item {
            PremiumCard {
                Text(tr("Day by day"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                days.reversed().forEachIndexed { i, d ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DayRow(d, limit, d.date == today) { sheetDay = d.date }
                }
            }
        }
    }

    sheetDay?.let { date ->
        DaySheet(store, tick, date, limit) { sheetDay = null }
    }
}

@Composable
private fun BarChart(days: List<DayStats>, limit: Int, selected: Int, onSelect: (Int) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val faded = primary.copy(alpha = 0.30f)
    val overC = MaterialTheme.colorScheme.error
    val overFaded = overC.copy(alpha = 0.40f)
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val today = LocalDate.now()
    val maxValue = maxOf(limit, days.maxOfOrNull { it.totalScrolls } ?: 0, 1).toFloat() * 1.12f

    Canvas(
        Modifier.fillMaxWidth().height(170.dp).pointerInput(days.size) {
            detectTapGestures { pos ->
                val i = (pos.x / (size.width.toFloat() / days.size)).toInt().coerceIn(0, days.lastIndex)
                onSelect(i)
            }
        }
    ) {
        val slot = size.width / days.size
        val barW = slot * if (days.size > 10) 0.62f else 0.5f
        days.forEachIndexed { i, d ->
            val h = maxOf((d.totalScrolls / maxValue) * size.height, 4.dp.toPx())
            val isSel = i == selected
            val color = if (d.totalScrolls >= limit) (if (isSel) overC else overFaded) else (if (isSel) primary else faded)
            drawRoundRect(
                color = color,
                topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 3, barW / 3)
            )
        }
        val y = size.height - (limit / maxValue) * size.height
        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        if (days.size <= 10) {
            days.forEach { d ->
                Text(
                    if (d.date == today) tr("Today") else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val f = DateTimeFormatter.ofPattern("d MMM")
            Text(days.first().date.format(f), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(days[days.size / 2].date.format(f), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("Today"), Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayRow(day: DayStats, limit: Int, isToday: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (isToday) tr("Today") else day.date.format(DayFmt), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (day.totalScrolls == 0) tr("No reels") else formatDistance(day.totalMeters) + (if (day.totalSeconds > 0) " · " + formatDuration(day.totalSeconds) else "") + (day.topApp?.let { tr(" · most on ") + (com.rabbi.scrollcounter.data.TrackedApps.forKey(it.key)?.name ?: "") } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "${day.totalScrolls}",
            style = MaterialTheme.typography.titleMedium,
            color = if (day.totalScrolls >= limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(store: ScrollStore, tick: Long, date: LocalDate, limit: Int, onDismiss: () -> Unit) {
    val day = remember(tick, date) { store.dayStats(date) }
    val hours = remember(tick, date) { store.hourly(date) }
    val peak = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)
        ) {
            Text(
                if (date == LocalDate.now()) tr("Today") else date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${day.totalScrolls}", style = MaterialTheme.typography.displayMedium,
                    color = if (day.totalScrolls >= limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text(tr("  / %1\$s reels", limit), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
            }
            Text(
                "${formatDistance(day.totalMeters)} · ${distanceComparison(day.totalMeters)}" + (peak?.let { tr(" · busiest at %1\$s", formatHour(it)) } ?: ""),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            HourlyChart(hours, highlightHour = peak)
            Spacer(Modifier.height(18.dp))
            if (day.apps.isEmpty()) EmptyNote(tr("No scrolling was tracked on this day."))
            else {
                val max = day.apps.maxOf { it.scrolls }
                day.apps.sortedByDescending { it.scrolls }.forEach { AppStatRow(it, max, day.totalScrolls) }
            }
        }
    }
}
