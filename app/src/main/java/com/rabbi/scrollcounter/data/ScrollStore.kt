package com.rabbi.scrollcounter.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

data class AppStat(val key: String, val scrolls: Int, val meters: Float)

data class DayStats(val date: LocalDate, val apps: List<AppStat>) {
    val totalScrolls: Int get() = apps.sumOf { it.scrolls }
    val totalMeters: Float get() = apps.fold(0f) { acc, s -> acc + s.meters }
    val topApp: AppStat? get() = apps.maxByOrNull { it.scrolls }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Local, on-device storage. The accessibility service and the UI live in the same
 * process, so they share this singleton and the UI updates live through [changes].
 *
 * Keys:
 *  "c|2026-10-15|facebook" -> scrolls
 *  "d|2026-10-15|facebook" -> meters
 *  "h|2026-10-15|20"       -> scrolls during 20:00–20:59 (all apps)
 */
class ScrollStore private constructor(context: Context) {

    private val stats = context.getSharedPreferences("scroll_stats", Context.MODE_PRIVATE)
    private val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _changes = MutableStateFlow(0L)
    /** Ticks whenever stats or settings change, so Compose can re-read. */
    val changes: StateFlow<Long> = _changes

    private val appContext = context.applicationContext
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val widgetRefresh = Runnable { com.rabbi.scrollcounter.widget.ScrollWidget.refreshAll(appContext) }

    private fun bump() {
        _changes.value = _changes.value + 1
        // Coalesce widget updates so fast scrolling doesn't redraw the widget every frame.
        main.removeCallbacks(widgetRefresh)
        main.postDelayed(widgetRefresh, 700)
    }

    // ---------------- Stats ----------------

    @Synchronized
    fun addScroll(appKey: String) {
        val d = LocalDate.now().toString()
        val c = "c|$d|$appKey"
        val h = "h|$d|${LocalTime.now().hour}"
        stats.edit()
            .putInt(c, stats.getInt(c, 0) + 1)
            .putInt(h, stats.getInt(h, 0) + 1)
            .apply()
        bump()
    }

    @Synchronized
    fun addDistance(appKey: String, meters: Float) {
        if (meters <= 0f) return
        val k = "d|${LocalDate.now()}|$appKey"
        stats.edit().putFloat(k, stats.getFloat(k, 0f) + meters).apply()
        bump()
    }

    fun dayStats(date: LocalDate): DayStats {
        val d = date.toString()
        val apps = TrackedApps.all.mapNotNull { app ->
            val c = stats.getInt("c|$d|${app.key}", 0)
            if (c == 0) null else AppStat(app.key, c, stats.getFloat("d|$d|${app.key}", 0f))
        }
        return DayStats(date, apps)
    }

    /** Today's scrolls in one app. */
    fun todayCount(appKey: String): Int = stats.getInt("c|${LocalDate.now()}|$appKey", 0)

    /** Today's scrolls across all apps. */
    fun todayTotal(): Int {
        val d = LocalDate.now().toString()
        return TrackedApps.all.sumOf { stats.getInt("c|$d|${it.key}", 0) }
    }

    /** Scrolls per hour (index 0..23) for [date]. */
    fun hourly(date: LocalDate): IntArray {
        val d = date.toString()
        return IntArray(24) { stats.getInt("h|$d|$it", 0) }
    }

    /** Oldest first, ending today. */
    fun lastDays(n: Int): List<DayStats> {
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { dayStats(today.minusDays(it.toLong())) }
    }

    /** Consecutive finished days (yesterday backwards) that stayed under the daily limit. */
    fun streak(): Int {
        val first = firstDay
        val limit = dailyLimit
        var day = LocalDate.now().minusDays(1)
        var n = 0
        while (!day.isBefore(first) && n < 365) {
            if (dayStats(day).totalScrolls < limit) n++ else break
            day = day.minusDays(1)
        }
        return n
    }

    fun clearDay(date: LocalDate) {
        val d = date.toString()
        val editor = stats.edit()
        stats.all.keys.filter { it.split('|').getOrNull(1) == d }.forEach { editor.remove(it) }
        editor.apply()
        if (date == LocalDate.now()) {
            lastLimitAlert = null
            lastWarnAlert = null
        }
        bump()
    }

    fun clearAll() {
        stats.edit().clear().apply()
        lastLimitAlert = null
        lastWarnAlert = null
        settings.edit().putString(KEY_FIRST_DAY, LocalDate.now().toString()).apply()
        bump()
    }

    /** Keep only the last [days] days of history. */
    fun prune(days: Int = HISTORY_DAYS) {
        val cutoff = LocalDate.now().minusDays(days.toLong())
        val editor = stats.edit()
        stats.all.keys.forEach { key ->
            val date = runCatching { LocalDate.parse(key.split('|')[1]) }.getOrNull()
            if (date == null || date.isBefore(cutoff)) editor.remove(key)
        }
        editor.apply()
    }

    /** CSV of the whole stored history: date,app,scrolls,meters */
    fun exportCsv(): String {
        val sb = StringBuilder("date,app,reels,distance_m\n")
        lastDays(HISTORY_DAYS).forEach { day ->
            day.apps.forEach { s ->
                val name = TrackedApps.forKey(s.key)?.name ?: s.key
                sb.append(day.date).append(',').append(name).append(',')
                    .append(s.scrolls).append(',')
                    .append(String.format(Locale.US, "%.1f", s.meters)).append('\n')
            }
        }
        return sb.toString()
    }

    // ---------------- Settings ----------------

    var dailyLimit: Int
        get() = settings.getInt("daily_limit", 500).coerceIn(10, 2000)
        set(value) { settings.edit().putInt("daily_limit", value).apply(); bump() }

    var paused: Boolean
        get() = settings.getBoolean("paused", false)
        set(value) { settings.edit().putBoolean("paused", value).apply(); bump() }

    var limitAlertsOn: Boolean
        get() = settings.getBoolean("limit_alerts", true)
        set(value) { settings.edit().putBoolean("limit_alerts", value).apply(); bump() }

    var warnAlertsOn: Boolean
        get() = settings.getBoolean("warn_alerts", true)
        set(value) { settings.edit().putBoolean("warn_alerts", value).apply(); bump() }

    var onboarded: Boolean
        get() = settings.getBoolean("onboarded", false)
        set(value) { settings.edit().putBoolean("onboarded", value).apply(); bump() }

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(settings.getString("theme", null) ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) { settings.edit().putString("theme", value.name).apply(); bump() }

    /** We asked once to add the widget automatically. */
    var widgetPrompted: Boolean
        get() = settings.getBoolean("widget_prompted", false)
        set(value) { settings.edit().putBoolean("widget_prompted", value).apply() }

    /** Floating check-in card every few scrolls. */
    var bubbleOn: Boolean
        get() = settings.getBoolean("bubble_on", true)
        set(value) { settings.edit().putBoolean("bubble_on", value).apply(); bump() }

    /** Send the user out of social apps once the daily limit is reached. */
    var blockOn: Boolean
        get() = settings.getBoolean("block_on", true)
        set(value) { settings.edit().putBoolean("block_on", value).apply(); bump() }

    /** End time (epoch ms) of a "5 more minutes" break from blocking. */
    var graceUntil: Long
        get() = settings.getLong("grace_until", 0L)
        set(value) { settings.edit().putLong("grace_until", value).apply(); bump() }

    /** Date the one-per-day "5 more minutes" was used. */
    var graceDate: String?
        get() = settings.getString("grace_date", null)
        set(value) { settings.edit().putString("grace_date", value).apply() }

    val graceAvailableToday: Boolean get() = graceDate != LocalDate.now().toString()
    val inGrace: Boolean get() = System.currentTimeMillis() < graceUntil

    fun isAppEnabled(key: String): Boolean = settings.getBoolean("app_$key", true)

    fun setAppEnabled(key: String, enabled: Boolean) {
        settings.edit().putBoolean("app_$key", enabled).apply()
        bump()
    }

    /** First day the app was used; history and streaks never reach before it. */
    val firstDay: LocalDate
        get() {
            val saved = settings.getString(KEY_FIRST_DAY, null)
            if (saved != null) return runCatching { LocalDate.parse(saved) }.getOrDefault(LocalDate.now())
            val today = LocalDate.now()
            settings.edit().putString(KEY_FIRST_DAY, today.toString()).apply()
            return today
        }

    /** Dates (yyyy-MM-dd) on which each alert was last shown, so each fires once per day. */
    var lastLimitAlert: String?
        get() = settings.getString("last_limit_alert", null)
        set(value) { settings.edit().putString("last_limit_alert", value).apply() }

    var lastWarnAlert: String?
        get() = settings.getString("last_warn_alert", null)
        set(value) { settings.edit().putString("last_warn_alert", value).apply() }

    companion object {
        const val HISTORY_DAYS = 60
        private const val KEY_FIRST_DAY = "first_day"

        @Volatile
        private var instance: ScrollStore? = null

        fun get(context: Context): ScrollStore =
            instance ?: synchronized(this) {
                instance ?: ScrollStore(context.applicationContext).also { instance = it }
            }
    }
}
