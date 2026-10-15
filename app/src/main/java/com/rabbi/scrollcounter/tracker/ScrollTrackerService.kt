package com.rabbi.scrollcounter.tracker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.rabbi.scrollcounter.block.BlockActivity
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.data.TrackedApps
import java.time.LocalDate
import kotlin.math.abs

/**
 * Counts scroll *gestures* in supported social apps, shows a check-in card every few scrolls,
 * and sends the user out of the app once the daily limit is reached.
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
    private var bubble: ScrollBubble? = null
    private val handler = Handler(Looper.getMainLooper())

    private val lastEventAt = HashMap<String, Long>()
    private val gesturePx = HashMap<String, Float>()
    private val pendingFinalize = HashMap<String, Runnable>()
    private var lastBlockAt = 0L

    private var screenHeightPx = 2000f
    private var metersPerPx = 0.0254f / 400f

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = ScrollStore.get(this)
        bubble = ScrollBubble(this)
        val dm = resources.displayMetrics
        screenHeightPx = dm.heightPixels.toFloat()
        val dpi = if (dm.ydpi > 0f) dm.ydpi else dm.densityDpi.toFloat()
        metersPerPx = 0.0254f / dpi
        Notifier.ensureChannel(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val store = store ?: return
        if (store.paused) return
        val pkg = event.packageName?.toString() ?: return
        val app = TrackedApps.forPackage(pkg) ?: return
        if (!store.isAppEnabled(app.key)) return

        // Limit reached → don't let the user stay in the app (opening it or scrolling in it).
        if (shouldBlock(store)) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
            ) block(app.name)
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return

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
            onNewScroll(store, key, app.name)
        }

        reportedDeltaPx(event)?.let { gesturePx[key] = (gesturePx[key] ?: 0f) + it }

        // (Re)schedule the end-of-gesture handler.
        pendingFinalize.remove(key)?.let { handler.removeCallbacks(it) }
        val r = Runnable { finalizeGesture(key) }
        pendingFinalize[key] = r
        handler.postDelayed(r, GESTURE_GAP_MS)
    }

    private fun onNewScroll(store: ScrollStore, key: String, appName: String) {
        val total = store.todayTotal()
        val limit = store.dailyLimit
        val appCount = store.todayCount(key)

        checkAlerts(store, total, limit)

        if (store.blockOn && !store.inGrace && total >= limit) {
            block(appName)
            return
        }
        if (store.bubbleOn && appCount % BUBBLE_EVERY == 0) {
            bubble?.show(appName, appCount, total, limit)
        }
    }

    private fun shouldBlock(store: ScrollStore): Boolean =
        store.blockOn && !store.inGrace && store.todayTotal() >= store.dailyLimit

    private fun block(appName: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockAt < 1500) return
        lastBlockAt = now
        bubble?.destroy()
        performGlobalAction(GLOBAL_ACTION_HOME)
        val i = Intent(this, BlockActivity::class.java)
            .putExtra(BlockActivity.EXTRA_APP, appName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        handler.postDelayed({ runCatching { startActivity(i) } }, 250)
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

    private fun checkAlerts(store: ScrollStore, total: Int, limit: Int) {
        val today = LocalDate.now().toString()
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
        bubble?.destroy()
        super.onDestroy()
    }

    companion object {
        private const val GESTURE_GAP_MS = 600L
        private const val FALLBACK_SCREEN_FRACTION = 0.7f
        const val BUBBLE_EVERY = 5
        const val WARN_AT = 0.8f
    }
}
