package com.rabbi.scrollcounter.tracker

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.data.TrackedApps
import java.time.LocalDate
import kotlin.math.abs

/**
 * Counts scroll *gestures* in supported social apps.
 *
 * Apps fire many TYPE_VIEW_SCROLLED events for a single swipe (one per frame of the fling),
 * so raw events are grouped: a new scroll is counted only after [GESTURE_GAP_MS] of silence.
 *
 * Distance: we add up the pixel deltas the app reports (Android 9+). Apps that don't report
 * deltas (e.g. full-screen video feeds) get an estimate of [FALLBACK_SCREEN_FRACTION] of the
 * screen height per swipe. Pixels are converted to real-world meters using the screen's DPI.
 */
class ScrollTrackerService : AccessibilityService() {

    private var store: ScrollStore? = null
    private val handler = Handler(Looper.getMainLooper())

    private val lastEventAt = HashMap<String, Long>()
    private val gesturePx = HashMap<String, Float>()
    private val pendingFinalize = HashMap<String, Runnable>()

    private var screenHeightPx = 2000f
    private var metersPerPx = 0.0254f / 400f

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = ScrollStore.get(this)
        val dm = resources.displayMetrics
        screenHeightPx = dm.heightPixels.toFloat()
        val dpi = if (dm.ydpi > 0f) dm.ydpi else dm.densityDpi.toFloat()
        metersPerPx = 0.0254f / dpi
        Notifier.ensureChannel(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val store = store ?: return
        if (store.paused) return
        val pkg = event.packageName?.toString() ?: return
        val app = TrackedApps.forPackage(pkg) ?: return
        if (!store.isAppEnabled(app.key)) return

        val key = app.key
        val now = SystemClock.uptimeMillis()
        val last = lastEventAt[key] ?: 0L
        lastEventAt[key] = now

        if (now - last > GESTURE_GAP_MS) {
            // Previous gesture still pending? Close it out before starting a new one.
            pendingFinalize.remove(key)?.let {
                handler.removeCallbacks(it)
                it.run()
            }
            gesturePx[key] = 0f
            store.addScroll(key)
            checkAlerts(store)
        }

        reportedDeltaPx(event)?.let { gesturePx[key] = (gesturePx[key] ?: 0f) + it }

        // (Re)schedule the end-of-gesture handler.
        pendingFinalize.remove(key)?.let { handler.removeCallbacks(it) }
        val r = Runnable { finalizeGesture(key) }
        pendingFinalize[key] = r
        handler.postDelayed(r, GESTURE_GAP_MS)
    }

    private fun reportedDeltaPx(event: AccessibilityEvent): Float? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val dy = abs(event.scrollDeltaY)
        // -1 / 0 = "not reported"; huge values are bogus resets.
        return if (dy > 1 && dy < screenHeightPx * 3) dy.toFloat() else null
    }

    private fun finalizeGesture(key: String) {
        pendingFinalize.remove(key)
        val px = gesturePx.remove(key) ?: return
        val distancePx = if (px > 0f) px else screenHeightPx * FALLBACK_SCREEN_FRACTION
        store?.addDistance(key, distancePx * metersPerPx)
    }

    private fun checkAlerts(store: ScrollStore) {
        val today = LocalDate.now().toString()
        val total = store.dayStats(LocalDate.now()).totalScrolls
        val limit = store.dailyLimit

        if (total >= limit) {
            if (store.limitAlertsOn && store.lastLimitAlert != today) {
                store.lastLimitAlert = today
                Notifier.limitReached(this, total, limit)
            }
        } else if (total >= (limit * WARN_AT).toInt()) {
            if (store.warnAlertsOn && store.lastWarnAlert != today) {
                store.lastWarnAlert = today
                Notifier.almostThere(this, total, limit)
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val GESTURE_GAP_MS = 600L
        private const val FALLBACK_SCREEN_FRACTION = 0.7f
        const val WARN_AT = 0.8f
    }
}
