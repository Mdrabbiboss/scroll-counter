package com.rabbi.scrollcounter.tracker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.rabbi.scrollcounter.block.BlockActivity
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.data.TrackedApps
import java.time.LocalDate

/**
 * Counts Reels / Shorts / short videos — one new reel = +1 — in supported apps, keeps a small
 * live counter on screen while you watch, and sends you out of the app once the daily limit
 * is reached. Ordinary feed, chat or profile scrolling is ignored.
 */
class ScrollTrackerService : AccessibilityService() {

    private var store: ScrollStore? = null
    private var pill: ReelCounterPill? = null
    private var detector: ReelDetector? = null
    private val handler = Handler(Looper.getMainLooper())

    private var lastBlockAt = 0L
    private var metersPerPx = 0.0254f / 400f
    private var currentPkg: String? = null
    private var lastScanAt = 0L

    /** While the counter is up, check every so often whether the user is still in a reels feed. */
    private val watcher = object : Runnable {
        override fun run() {
            val p = pill ?: return
            if (!p.isShowing) return
            val activePkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
            val stillTracked = activePkg != null && TrackedApps.forPackage(activePkg) != null
            val inReels = detector?.stillInReels() == true
            if (!stillTracked || !inReels || store?.bubbleOn != true || store?.paused == true) {
                p.hide()
                detector?.forgetPager()
            } else {
                handler.postDelayed(this, WATCH_MS)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = ScrollStore.get(this)
        pill = ReelCounterPill(this)
        val dm = resources.displayMetrics
        detector = ReelDetector(dm.widthPixels, dm.heightPixels)
        val dpi = if (dm.ydpi > 0f) dm.ydpi else dm.densityDpi.toFloat()
        metersPerPx = 0.0254f / dpi
        Notifier.ensureChannel(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val store = store ?: return
        val pkg = event.packageName?.toString() ?: return
        val app = TrackedApps.forPackage(pkg) ?: return
        if (store.paused || !store.isAppEnabled(app.key)) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg != currentPkg) {
                currentPkg = pkg
                detector?.reset(app.key)
            }
            // Limit reached → don't let the user stay in the app.
            if (shouldBlock(store)) block(app.name)
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Cheap, throttled check: did the user just open a Reels / Shorts feed?
            val now = SystemClock.uptimeMillis()
            if (store.bubbleOn && pill?.isShowing != true && now - lastScanAt > SCAN_GAP_MS) {
                lastScanAt = now
                val det = detector ?: return
                if (det.findReelPager(runCatching { rootInActiveWindow }.getOrNull())) {
                    showPill(store, store.todayTotal(), store.dailyLimit, bump = false)
                }
            }
            return
        }

        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        val det = detector ?: return
        val newReel = det.onScroll(app.key, event)

        if (shouldBlock(store) && det.lastPager != null) {
            block(app.name)
            return
        }

        if (newReel) {
            store.addScroll(app.key)
            store.addDistance(app.key, det.lastPagerHeight * metersPerPx)
            val total = store.todayTotal()
            val limit = store.dailyLimit
            checkAlerts(store, total, limit)
            if (store.blockOn && !store.inGrace && total >= limit) {
                block(app.name)
                return
            }
            showPill(store, total, limit, bump = true)
        } else if (det.lastPager != null && pill?.isShowing != true && store.bubbleOn) {
            // Entered a reels feed: bring the counter up even before the first swipe.
            if (det.stillInReels()) showPill(store, store.todayTotal(), store.dailyLimit, bump = false)
        }
    }

    private fun showPill(store: ScrollStore, total: Int, limit: Int, bump: Boolean) {
        if (!store.bubbleOn) return
        val p = pill ?: return
        val wasShowing = p.isShowing
        p.show(total, limit, bump)
        if (!wasShowing) {
            handler.removeCallbacks(watcher)
            handler.postDelayed(watcher, WATCH_MS)
        }
    }

    private fun shouldBlock(store: ScrollStore): Boolean =
        store.blockOn && !store.inGrace && store.todayTotal() >= store.dailyLimit

    private fun block(appName: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastBlockAt < 1500) return
        lastBlockAt = now
        pill?.hide()
        detector?.forgetPager()
        performGlobalAction(GLOBAL_ACTION_HOME)
        val i = Intent(this, BlockActivity::class.java)
            .putExtra(BlockActivity.EXTRA_APP, appName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        handler.postDelayed({ runCatching { startActivity(i) } }, 250)
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
        pill?.destroy()
        super.onDestroy()
    }

    companion object {
        private const val WATCH_MS = 1200L
        private const val SCAN_GAP_MS = 900L
        const val BUBBLE_EVERY = 1
        const val WARN_AT = 0.8f
    }
}
