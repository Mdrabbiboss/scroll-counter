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
    /** Device-only state that must NOT come back from a backup (setup done, device admin on…). */
    private val local = context.getSharedPreferences("local", Context.MODE_PRIVATE)
    private val backup = android.app.backup.BackupManager(context.applicationContext)

    init {
        // One-time move of device-only flags out of the backed-up settings file.
        if (!local.getBoolean("migrated", false)) {
            local.edit()
                .putBoolean("onboarded", settings.getBoolean("onboarded", false))
                .putBoolean("widget_prompted_v2", settings.getBoolean("widget_prompted_v2", false))
                .putBoolean("uninstall_guard", settings.getBoolean("uninstall_guard", false))
                .putBoolean("migrated", true)
                .apply()
            settings.edit().remove("onboarded").remove("widget_prompted_v2").remove("uninstall_guard").apply()
        }
    }

    private val _changes = MutableStateFlow(0L)
    /** Ticks whenever stats or settings change, so Compose can re-read. */
    val changes: StateFlow<Long> = _changes

    private val appContext = context.applicationContext
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        // Bring back counts from the durable copy (after a reinstall or "clear data").
        restoreDurable()
        TrackedApps.attach(appContext)
        com.rabbi.scrollcounter.ui.theme.AppFonts.load(appContext)
    }
    private val widgetRefresh = Runnable {
        com.rabbi.scrollcounter.widget.ScrollWidget.refreshAll(appContext)
    }

    private fun bump() {
        _changes.value = _changes.value + 1
        // Ask Android to back the counts up soon, so reinstalling the app keeps today's reels,
        // and keep a copy in Block Store, which survives uninstalling or clearing the app.
        runCatching { backup.dataChanged() }
        DurableStore.scheduleSave(appContext) { durableSnapshot() }
        // Coalesce widget updates so fast scrolling doesn't redraw the widget every frame.
        main.removeCallbacks(widgetRefresh)
        main.postDelayed(widgetRefresh, 700)
    }

    // ---------------- Durable copy (survives uninstall / clear data) ----------------

    /**
     * Pull counts back from every saved copy and merge them in (never lowering anything), then
     * write the merged result back so all copies agree. Safe to call often (e.g. on resume,
     * right after the user allows storage access).
     */
    fun restoreDurable() {
        DurableStore.restore(appContext) { text -> if (text != null) runCatching { mergeDurable(text) } }
        main.postDelayed({ DurableStore.scheduleSave(appContext) { durableSnapshot() } }, 1500)
    }

    /** Counts of the last [HISTORY_DAYS] days as compact lines, oldest first. Header: v1|firstDay|limit. */
    @Synchronized
    fun durableSnapshot(): String {
        val cutoff = LocalDate.now().minusDays(HISTORY_DAYS.toLong())
        val rows = stats.all.mapNotNull { (k, v) ->
            val parts = k.split('|')
            if (parts.size != 3) return@mapNotNull null
            val date = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return@mapNotNull null
            if (date.isBefore(cutoff)) return@mapNotNull null
            val value = when (v) { is Int -> v.toString(); is Float -> String.format(Locale.US, "%.1f", v); else -> return@mapNotNull null }
            Triple(date, k, value)
        }.sortedBy { it.first }
        val sb = StringBuilder("v1|${firstDay}|${dailyLimit}|${settings.getString(KEY_LIMIT_DAY, null) ?: "-"}")
        rows.forEach { sb.append('\n').append(it.second).append('|').append(it.third) }
        return sb.toString()
    }

    /** Merge a durable copy back in, never lowering anything that's already here. */
    @Synchronized
    fun mergeDurable(text: String) {
        val lines = text.lines()
        val head = lines.firstOrNull()?.split('|') ?: return
        if (head.firstOrNull() != "v1") return
        val editor = stats.edit()
        var changed = false
        for (line in lines.drop(1)) {
            val p = line.split('|')
            if (p.size != 4) continue
            val key = "${p[0]}|${p[1]}|${p[2]}"
            when (p[0]) {
                "c", "h" -> {
                    val v = p[3].toIntOrNull() ?: continue
                    if (v > stats.getInt(key, 0)) { editor.putInt(key, v); changed = true }
                }
                "d" -> {
                    val v = p[3].toFloatOrNull() ?: continue
                    if (v > stats.getFloat(key, 0f)) { editor.putFloat(key, v); changed = true }
                }
            }
        }
        editor.apply()
        // Keep the earliest first day (streaks and history), and the limit if none is set here.
        head.getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { saved ->
            if (saved.isBefore(firstDay)) { settings.edit().putString(KEY_FIRST_DAY, saved.toString()).apply(); changed = true }
        }
        // The limit (and the day it was last changed) follow the newest copy, so clearing data
        // can't unlock a limit that was already changed today.
        val savedLimit = head.getOrNull(2)?.toIntOrNull()
        val savedDay = head.getOrNull(3)?.takeIf { it != "-" }
        val localDay = settings.getString(KEY_LIMIT_DAY, null)
        if (savedLimit != null) {
            val takeIt = if (savedDay != null) localDay == null || savedDay >= localDay
                         else !settings.contains("daily_limit")
            if (takeIt && (savedLimit != settings.getInt("daily_limit", -1) || savedDay != localDay)) {
                settings.edit().putInt("daily_limit", savedLimit.coerceIn(10, MAX_LIMIT))
                    .apply { if (savedDay != null) putString(KEY_LIMIT_DAY, savedDay) }.apply()
                changed = true
            }
        }
        if (changed) bump()
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

    fun dayStats(date: LocalDate): DayStats = dayStats(date, stats.all)

    /** Reads every app that has data on [date] — catalogue apps and any other app alike. */
    private fun dayStats(date: LocalDate, all: Map<String, *>): DayStats {
        val prefix = "c|$date|"
        val apps = all.mapNotNull { (k, v) ->
            if (!k.startsWith(prefix)) return@mapNotNull null
            val c = (v as? Int) ?: 0
            if (c == 0) return@mapNotNull null
            val key = k.substring(prefix.length)
            AppStat(key, c, (all["d|$date|$key"] as? Float) ?: 0f)
        }.sortedByDescending { it.scrolls }
        return DayStats(date, apps)
    }

    /** Every app key that has ever had reels counted (still within history). */
    fun keysWithData(): Set<String> =
        stats.all.keys.filter { it.startsWith("c|") }.mapNotNull { it.split('|').getOrNull(2) }.toSet()

    /** Today's scrolls in one app. */
    fun todayCount(appKey: String): Int = stats.getInt("c|${LocalDate.now()}|$appKey", 0)

    /** Today's scrolls across all apps. */
    fun todayTotal(): Int {
        // The hourly buckets hold every counted reel, so their sum is today's total (24 cheap reads).
        val d = LocalDate.now().toString()
        var sum = 0
        for (h in 0 until 24) sum += stats.getInt("h|$d|$h", 0)
        return sum
    }

    /** Scrolls per hour (index 0..23) for [date]. */
    fun hourly(date: LocalDate): IntArray {
        val d = date.toString()
        return IntArray(24) { stats.getInt("h|$d|$it", 0) }
    }

    /** Oldest first, ending today. */
    fun lastDays(n: Int): List<DayStats> {
        val today = LocalDate.now()
        val all = stats.all
        return (n - 1 downTo 0).map { dayStats(today.minusDays(it.toLong()), all) }
    }

    /** Consecutive finished days (yesterday backwards) that stayed under the daily limit. */
    fun streak(): Int {
        val first = firstDay
        val limit = dailyLimit
        var day = LocalDate.now().minusDays(1)
        var n = 0
        val all = stats.all
        while (!day.isBefore(first) && n < 365) {
            if (dayStats(day, all).totalScrolls < limit) n++ else break
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

    /** Deletes past days. Today's count is kept, so deleting history can't reset today's limit. */
    fun clearAll() {
        val today = LocalDate.now().toString()
        val editor = stats.edit()
        stats.all.keys.filter { it.split('|').getOrNull(1) != today }.forEach { editor.remove(it) }
        editor.apply()
        settings.edit().putString(KEY_FIRST_DAY, today).apply()
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

    /** The daily limit (10–[MAX_LIMIT]). Setting it locks it for the rest of the day. */
    var dailyLimit: Int
        get() = settings.getInt("daily_limit", MAX_LIMIT).coerceIn(10, MAX_LIMIT)
        set(value) {
            settings.edit()
                .putInt("daily_limit", value.coerceIn(10, MAX_LIMIT))
                .putString(KEY_LIMIT_DAY, LocalDate.now().toString())
                .apply()
            bump()
        }

    /** True once the limit was changed today: it can only be changed again tomorrow. */
    val limitLockedToday: Boolean
        get() = settings.getString(KEY_LIMIT_DAY, null) == LocalDate.now().toString()

    /** Pausing was removed: tracking is always on while the Accessibility service is enabled. */
    var paused: Boolean
        get() = false
        set(@Suppress("UNUSED_PARAMETER") value) { }

    /** On-screen counter size: 0 = small, 1 = normal, 2 = large, 3 = extra large. */
    var pillSize: Int
        get() = settings.getInt("pill_size", 1).coerceIn(0, 3)
        set(value) { settings.edit().putInt("pill_size", value.coerceIn(0, 3)).apply(); bump() }

    val pillScale: Float get() = PILL_SCALES[pillSize]

    /** "Prevent app uninstall" is on (device admin + guard in the tracking service). */
    var uninstallGuard: Boolean
        get() = local.getBoolean("uninstall_guard", false)
        set(value) { local.edit().putBoolean("uninstall_guard", value).apply(); bump() }

    var limitAlertsOn: Boolean
        get() = settings.getBoolean("limit_alerts", true)
        set(value) { settings.edit().putBoolean("limit_alerts", value).apply(); bump() }

    var warnAlertsOn: Boolean
        get() = settings.getBoolean("warn_alerts", true)
        set(value) { settings.edit().putBoolean("warn_alerts", value).apply(); bump() }

    var onboarded: Boolean
        get() = local.getBoolean("onboarded", false)
        set(value) { local.edit().putBoolean("onboarded", value).apply(); bump() }

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(settings.getString("theme", null) ?: "SYSTEM") }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) { settings.edit().putString("theme", value.name).apply(); bump() }

    /** We asked once to add the widget automatically. */
    var widgetPrompted: Boolean
        get() = local.getBoolean("widget_prompted_v2", false)
        set(value) { local.edit().putBoolean("widget_prompted_v2", value).apply() }

    /** Floating check-in card every few scrolls. */
    var bubbleOn: Boolean
        get() = true
        set(@Suppress("UNUSED_PARAMETER") value) { }

    /** Send the user out of social apps once the daily limit is reached. */
    var blockOn: Boolean
        get() = true
        set(@Suppress("UNUSED_PARAMETER") value) { }

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

    /** The buddy's little pet sounds on the You screen. */
    var buddySounds: Boolean
        get() = settings.getBoolean("buddy_sounds", true)
        set(value) { settings.edit().putBoolean("buddy_sounds", value).apply(); bump() }

    /** Count reels watched on TikTok / YouTube / Facebook… websites in browsers. */
    var browsersOn: Boolean
        get() = settings.getBoolean("browsers_on", true)
        set(value) { settings.edit().putBoolean("browsers_on", value).apply(); bump() }

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
        val PILL_SCALES = floatArrayOf(0.8f, 1f, 1.3f, 1.65f)
        private const val KEY_FIRST_DAY = "first_day"
        private const val KEY_LIMIT_DAY = "limit_set_day"
        const val MAX_LIMIT = 500

        @Volatile
        private var instance: ScrollStore? = null

        fun get(context: Context): ScrollStore =
            instance ?: synchronized(this) {
                instance ?: ScrollStore(context.applicationContext).also { instance = it }
            }
    }
}
