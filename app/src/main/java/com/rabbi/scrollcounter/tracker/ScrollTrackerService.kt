package com.rabbi.scrollcounter.tracker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.rabbi.scrollcounter.block.BlockActivity
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.data.TrackedApp
import com.rabbi.scrollcounter.data.TrackedApps
import com.rabbi.scrollcounter.guard.UninstallGuard
import java.time.LocalDate

/**
 * Counts Reels / Shorts / short videos — one new reel = +1 — in ANY app on the phone and on
 * short-video websites in any browser. Shows a small live counter while you watch, and sends you
 * home once the daily limit is reached. Comments, chats, normal feeds and reading are ignored.
 */
class ScrollTrackerService : AccessibilityService() {

    private var store: ScrollStore? = null
    private var pill: ReelCounterPill? = null
    private var detector: ReelDetector? = null
    private var web: BrowserReelDetector? = null
    private val handler = Handler(Looper.getMainLooper())

    private var lastBlockAt = 0L
    private var metersPerPx = 0.0254f / 400f
    private var currentPkg: String? = null
    private var lastScanAt = 0L
    private var lastUrlCheckAt = 0L
    private var lastGuardCheckAt = 0L

    /** Packages that never contain reels: this app, system UI, home screens, keyboards, settings. */
    private val ignored = HashSet<String>()
    /** Packages we've decided are social/video apps (for blocking and the quick reels scan). */
    private val socialCache = HashMap<String, Boolean>()
    private val countedKeys = HashSet<String>()

    // ---- settle timers: a swipe is judged once the feed has been still for a moment ----
    private var settleApp: TrackedApp? = null
    private val settleRunnableRef: Runnable get() = settleRunnable
    private val settleRunnable: Runnable = Runnable {
        val app = settleApp ?: return@Runnable
        val n = detector?.settle(app.key, runCatching { rootInActiveWindow }.getOrNull()) ?: 0
        if (n == ReelDetector.RETRY) {
            // Still snapping into place — look again in a moment.
            handler.postDelayed(settleRunnableRef, 250)
            return@Runnable
        }
        DetectLog.add(app.packages.first(), if (n > 0) "swipe settled → +$n reel" else "swipe settled → same reel")
        if (n > 0) {
            detector?.noteCurrent(app.key)
            onNewReels(app, n, detector?.lastPagerHeight ?: 0)
        }
    }
    /** Movement-based reels detection for apps whose layout we can't read (see [onAppScroll]). */
    private var snap: BrowserReelDetector? = null
    private var snapApp: TrackedApp? = null
    private val snapSettleRunnable = Runnable {
        val app = snapApp ?: return@Runnable
        val n = snap?.settle(app.key) ?: 0
        if (n > 0) {
            DetectLog.add(app.packages.first(), "swipe pattern → +$n reel")
            onNewReels(app, n, resources.displayMetrics.heightPixels)
        }
    }
    private var webApp: TrackedApp? = null
    private val webSettleRunnable = Runnable {
        val app = webApp ?: return@Runnable
        val n = web?.settle(app.key) ?: 0
        if (n > 0) onNewReels(app, n, resources.displayMetrics.heightPixels)
    }

    /** While the counter is up, check every so often whether the user is still in a reels feed. */
    private val watcher = object : Runnable {
        override fun run() {
            val p = pill ?: return
            if (!p.isShowing) return
            val s = store
            val activePkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
            val inReels = when {
                activePkg == null || activePkg in ignored -> false
                TrackedApps.isBrowser(activePkg) -> web?.inReels(TrackedApps.forPackage(activePkg).key) == true
                else -> detector?.stillInReels() == true || snap?.inReels(TrackedApps.forPackage(activePkg).key) == true
            }
            // Before hiding, look again on the current screen (the old pager node can go stale
            // after the app redraws), and only hide after two misses in a row — no flicker.
            val stillThere = inReels || (activePkg != null && activePkg !in ignored && !TrackedApps.isBrowser(activePkg) &&
                detector?.findReelPager(runCatching { rootInActiveWindow }.getOrNull()) == true)
            if (stillThere) misses = 0 else misses++
            if (misses >= 2 || s?.bubbleOn != true || s.paused) {
                misses = 0
                p.hide()
                detector?.forgetPager()
                startPresenceScan()
            } else {
                handler.postDelayed(this, WATCH_MS)
            }
        }
    }

    private var misses = 0

    /**
     * While a social app is in front and the counter isn't up, keep looking for its reels feed a
     * few times a second — so the counter appears right away when you come back to Reels, even if
     * the app sends no events while the video just plays.
     */
    private val presenceScan = object : Runnable {
        override fun run() {
            val s = store ?: return
            val p = pill ?: return
            if (p.isShowing || s.paused || !s.bubbleOn) return
            val root = runCatching { rootInActiveWindow }.getOrNull() ?: run { handler.postDelayed(this, PRESENCE_MS); return }
            val pkg = root.packageName?.toString() ?: return
            if (pkg in ignored || TrackedApps.isBrowser(pkg)) return
            val app = TrackedApps.forPackage(pkg)
            if (!s.isAppEnabled(app.key) || !isSocial(pkg, app)) return
            val det = detector ?: return
            if (det.findReelPager(root)) {
                // The reel you open on counts too.
                if (det.prime(app.key)) { DetectLog.add(pkg, "reels opened → +1 (first reel)"); onNewReels(app, 1, det.lastPagerHeight) }
                else showPill(s, s.todayTotal(), s.dailyLimit, bump = false)
            } else if (++presenceTries < PRESENCE_MAX_TRIES) {
                // Keep looking for a little while; after that, screen-change events take over.
                handler.postDelayed(this, PRESENCE_MS)
            }
        }
    }

    private var presenceTries = 0

    private fun startPresenceScan(delay: Long = 120L) {
        presenceTries = 0
        handler.removeCallbacks(presenceScan)
        handler.postDelayed(presenceScan, delay)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val s = ScrollStore.get(this)
        store = s
        pill = ReelCounterPill(this)
        val dm = resources.displayMetrics
        detector = ReelDetector(dm.widthPixels, dm.heightPixels)
        web = BrowserReelDetector(dm.heightPixels)
        snap = BrowserReelDetector(dm.heightPixels)
        val dpi = if (dm.ydpi > 0f) dm.ydpi else dm.densityDpi.toFloat()
        metersPerPx = 0.0254f / dpi
        buildIgnoreList()
        countedKeys.addAll(runCatching { s.keysWithData() }.getOrDefault(emptySet()))
        Notifier.ensureChannel(this)
        Notifier.live(this, s.todayTotal(), s.dailyLimit)
    }

    private fun buildIgnoreList() {
        ignored.clear()
        ignored += setOf(packageName, "com.android.systemui", "android", "com.android.settings",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.google.android.packageinstaller", "com.android.packageinstaller")
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
                .forEach { ignored += it.activityInfo.packageName }
        }
        runCatching {
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.enabledInputMethodList?.forEach { ignored += it.packageName }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val store = store ?: return
        val pkg = event.packageName?.toString() ?: return

        // Uninstall protection: keep the user away from Scroll Counter's uninstall / deactivate screens.
        if (store.uninstallGuard && pkg != packageName &&
            (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) &&
            UninstallGuard.isGuardedScreen(pkg)
        ) {
            val now = SystemClock.uptimeMillis()
            if (now - lastGuardCheckAt > GUARD_GAP_MS || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                lastGuardCheckAt = now
                if (UninstallGuard.shouldBlock(this, runCatching { rootInActiveWindow }.getOrNull())) {
                    UninstallGuard.kickOut(this)
                    return
                }
            }
        }

        if (pkg in ignored || store.paused) return

        val isBrowser = TrackedApps.isBrowser(pkg)
        if (isBrowser && !store.browsersOn) return
        if (isBrowser) checkBrowserAddress(store, pkg)

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val app = TrackedApps.forPackage(pkg)
                if (pkg != currentPkg) {
                    currentPkg = pkg
                    detector?.reset(app.key)
                    web?.reset(app.key)
                    snap?.reset(app.key)
                }
                // Limit reached → don't let the user stay in a social app.
                if (!isBrowser && store.isAppEnabled(app.key) && isSocial(pkg, app) && shouldBlock(store)) { block(app.name); return }
                // Back in a social app: look for the reels feed straight away.
                if (!isBrowser && store.isAppEnabled(app.key) && isSocial(pkg, app)) startPresenceScan(80L)
                else if (!isBrowser && pill?.isShowing == true) { pill?.hide(); detector?.forgetPager() }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (isBrowser) return
                // Cheap, throttled check: did the user just open a Reels / Shorts feed?
                val now = SystemClock.uptimeMillis()
                if (!store.bubbleOn || pill?.isShowing == true || now - lastScanAt < SCAN_GAP_MS) return
                val app = TrackedApps.forPackage(pkg)
                if (!store.isAppEnabled(app.key) || !isSocial(pkg, app)) return
                lastScanAt = now
                val det = detector ?: return
                if (det.findReelPager(runCatching { rootInActiveWindow }.getOrNull())) {
                    if (det.prime(app.key)) { DetectLog.add(pkg, "reels opened → +1 (first reel)"); onNewReels(app, 1, det.lastPagerHeight) }
                    else showPill(store, store.todayTotal(), store.dailyLimit, bump = false)
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val app = TrackedApps.forPackage(pkg)
                if (!store.isAppEnabled(app.key)) return
                if (isBrowser) onBrowserScroll(store, app, event) else onAppScroll(store, app, event)
            }
        }
    }

    private fun onAppScroll(store: ScrollStore, app: TrackedApp, event: AccessibilityEvent) {
        val det = detector ?: return
        if (!det.onScroll(app.key, event)) {
            // Not a recognisable pager (web-based, Flutter, React Native or custom apps):
            // fall back to how the feed moves — exact one-screen snaps, twice in a row = reels.
            // Standard Android lists are read reliably by their layout, so a "no" there is final.
            if (det.lastSourceNativeList) return
            // Known social apps are read by their layout (or the self-drawn check); the movement
            // fallback is only for unknown apps, so it can never invent counts in Facebook Lite.
            if (TrackedApps.isKnown(app.packages.first())) return
            val sn = snap ?: return
            sn.onScroll(app.key, event)
            if (sn.inReels(app.key) && shouldBlock(store)) { block(app.name); return }
            snapApp = app
            handler.removeCallbacks(snapSettleRunnable)
            handler.postDelayed(snapSettleRunnable, WEB_SETTLE_MS)
            return
        }
        if (shouldBlock(store)) { block(app.name); return }
        // Judge the swipe once the feed is still.
        settleApp = app
        handler.removeCallbacks(settleRunnable)
        handler.postDelayed(settleRunnable, ReelDetector.SETTLE_MS)
        if (pill?.isShowing != true && store.bubbleOn) showPill(store, store.todayTotal(), store.dailyLimit, bump = false)
    }

    private fun onBrowserScroll(store: ScrollStore, app: TrackedApp, event: AccessibilityEvent) {
        val w = web ?: return
        w.onScroll(app.key, event)
        if (w.inReels(app.key) && shouldBlock(store)) { block(app.name); return }
        webApp = app
        handler.removeCallbacks(webSettleRunnable)
        handler.postDelayed(webSettleRunnable, WEB_SETTLE_MS)
    }

    /** Throttled look at the browser's address bar: a new short-video address = a new reel. */
    private fun checkBrowserAddress(store: ScrollStore, pkg: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastUrlCheckAt < URL_GAP_MS) {
            handler.removeCallbacks(urlRecheck)
            handler.postDelayed(urlRecheck, URL_GAP_MS)
            return
        }
        lastUrlCheckAt = now
        val app = TrackedApps.forPackage(pkg)
        if (!store.isAppEnabled(app.key)) return
        val n = web?.checkAddress(app.key, runCatching { rootInActiveWindow }.getOrNull()) ?: 0
        if (n > 0) onNewReels(app, n, resources.displayMetrics.heightPixels)
    }

    private val urlRecheck = Runnable {
        val s = store ?: return@Runnable
        val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: return@Runnable
        if (TrackedApps.isBrowser(pkg) && s.browsersOn) {
            lastUrlCheckAt = 0L
            checkBrowserAddress(s, pkg)
        }
    }

    private fun onNewReels(app: TrackedApp, n: Int, pageHeightPx: Int) {
        val store = store ?: return
        if (store.paused) return
        repeat(n) { store.addScroll(app.key) }
        store.addDistance(app.key, n * pageHeightPx * metersPerPx)
        countedKeys += app.key
        val total = store.todayTotal()
        val limit = store.dailyLimit
        Notifier.live(this, total, limit)
        checkAlerts(store, total, limit)
        if (store.blockOn && !store.inGrace && total >= limit) {
            block(app.name)
            return
        }
        showPill(store, total, limit, bump = true)
    }

    /** A social / video app: from our catalogue, the phone's own category, or reels already counted there. */
    private fun isSocial(pkg: String, app: TrackedApp): Boolean =
        app.key in countedKeys || socialCache.getOrPut(pkg) {
            TrackedApps.isKnown(pkg) || TrackedApps.isSocialCategory(pkg)
        }

    private fun showPill(store: ScrollStore, total: Int, limit: Int, bump: Boolean) {
        if (!store.bubbleOn) return
        val p = pill ?: return
        val wasShowing = p.isShowing
        p.show(total, limit, bump, store.pillScale)
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
        Notifier.clearLive(this)
        pill?.destroy()
        super.onDestroy()
    }

    companion object {
        private const val WATCH_MS = 800L
        private const val SCAN_GAP_MS = 300L
        private const val PRESENCE_MS = 450L
        private const val PRESENCE_MAX_TRIES = 24
        private const val WEB_SETTLE_MS = 420L
        private const val URL_GAP_MS = 350L
        private const val GUARD_GAP_MS = 400L
        const val BUBBLE_EVERY = 1
        const val WARN_AT = 0.8f
    }
}
