package com.rabbi.scrollcounter.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/** The current time, updated every second (aligned to the full second). */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        delay(1000L - System.currentTimeMillis() % 1000L + 5L)
        value = LocalDateTime.now()
    }
}

/** Today's date, changing exactly at midnight (so a locked limit unlocks on its own). */
@Composable
fun rememberToday(): LocalDate {
    val now by rememberNow()
    val today by remember { derivedStateOf { now.toLocalDate() } }
    return today
}

/** Seconds left until the limit unlocks (local midnight). */
fun secondsToUnlock(now: LocalDateTime): Long =
    Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).seconds.coerceAtLeast(0)

/** 22:45:09 */
fun formatCountdown(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** 22h 45m (for short subtitles) */
fun formatCountdownShort(seconds: Long): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60
    return if (h > 0) tr("%1\$sh %2\$sm", h, m) else tr("%1\$sm", m.coerceAtLeast(1))
}

/** A live, ticking "HH:MM:SS" until the limit unlocks. Recomposes only itself every second. */
@Composable
fun UnlockCountdown(style: TextStyle, color: Color, modifier: Modifier = Modifier, prefix: String = "", weight: FontWeight = FontWeight.Bold) {
    val now by rememberNow()
    Text(
        prefix + formatCountdown(secondsToUnlock(now)),
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color, fontWeight = weight, modifier = modifier
    )
}
