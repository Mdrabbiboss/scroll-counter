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
    /** Shown over an app without leaving it: notification shade, keyboards, permission dialogs. */
    private val transientPkgs = HashSet<String>()
    private val launchers = HashSet<String>()
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
        if (n == 0 && detector?.leftReels == true && pill?.isShowing == true) { leaveReels(); return@Runnable }
        if (n > 0) {
            detector?.noteCurrent(app.key)
            onNewReels(app, n, detector?.lastPagerHeight ?: 0)
        }
    }
    /** Signature apps (YouTube Shorts): is a new short on screen? (see [ReelDetector.signatureSettle]) */
    private var sigApp: TrackedApp? = null
    private var sigQueued = false
    private val sigRunnableRef: Runnable get() = sigRunnable
    private val sigRunnable: Runnable = Runnable {
        sigQueued = false
        val app = sigApp ?: return@Runnable
        val det = detector ?: return@Runnable
        val n = det.signatureSettle(app.key, runCatching { rootInActiveWindow }.getOrNull())
        if (n == ReelDetector.RETRY) { sigQueued = true; handler.postDelayed(sigRunnableRef, 250); return@Runnable }
        if (n > 0) { det.noteCurrent(app.key); onNewReels(app, n, det.lastPagerHeight) }
    }

    /** Check for a new short soon. [restart] = after a swipe (wait for it to finish moving). */
    private fun queueSignatureCheck(app: TrackedApp, delay: Long, restart: Boolean) {
        sigApp = app
        if (restart) { handler.removeCallbacks(sigRunnable); sigQueued = false }
        if (sigQueued) return
        sigQueued = true
        handler.postDelayed(sigRunnable, delay)
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
    // ---- page watch: counts a reel when a new video page fills the screen (no scroll events needed) ----
    private var pageCheckPkg: String? = null
    private var pageCheckTries = 0
    private var pageCheckQueued = false
    private val pageCheckRunnable: Runnable = Runnable { runPageCheck() }

    /**
     * Throttled (not debounced): a playing video redraws the screen all the time, so a check that
     * waited for quiet would never run. At most one check is waiting at a time.
     */
    private fun queuePageCheck(pkg: String) {
        if (pageCheckQueued && pageCheckPkg == pkg) return
        pageCheckPkg = pkg
        pageCheckTries = 0
        pageCheckQueued = true
        handler.removeCallbacks(pageCheckRunnable)
        handler.postDelayed(pageCheckRunnable, PAGE_CHECK_MS)
    }

    private fun runPageCheck() {
        val pkg = pageCheckPkg ?: run { pageCheckQueued = false; return }
        val s = store ?: run { pageCheckQueued = false; return }
        if (pill?.isShowing != true || pkg != pillPkg) { pageCheckQueued = false; return }
        val app = TrackedApps.forPackage(pkg)
        val n = detector?.pageCheck(app.key, runCatching { rootInActiveWindow }.getOrNull()) ?: 0
        if (n == ReelDetector.RETRY && ++pageCheckTries <= 6) {
            handler.postDelayed(pageCheckRunnable, 250)
            return
        }
        pageCheckQueued = false
        if (n > 0 && !s.paused) {
            detector?.noteCurrent(app.key)
            onNewReels(app, n, detector?.lastPagerHeight ?: 0)
        }
    }

    private var webApp: TrackedApp? = null
    private val webSettleRunnable = Runnable {
        val app = webApp ?: return@Runnable
        val n = web?.settle(app.key) ?: 0
        if (n > 0) onNewReels(app, n, resources.displayMetrics.heightPixels)
    }

    /**
     * While the counter is up, check every so often whether the user is still in a reels feed.
     * The counter stays up the whole time the reels feed is on screen — also while a video loads,
     * the app redraws, or a comment sheet / the notification shade is over it — and hides only
     * when you really leave: another app or the home screen, or the reels feed has been gone
     * from the screen for a few checks in a row.
     */
    private val watcher = object : Runnable {
        override fun run() {
            val p = pill ?: return
            if (!p.isShowing) return
            val s = store
            val root = runCatching { rootInActiveWindow }.getOrNull()
            val activePkg = root?.packageName?.toString()
            // The shade, a keyboard or a permission dialog over the reel: not leaving — just wait.
            val transient = activePkg == null || activePkg in transientPkgs
            // Any other app, the home screen or Scroll Counter itself in front: you left the reels.
            val left = !transient && (activePkg in launchers || (pillPkg != null && activePkg != pillPkg))
            val stillThere = when {
                left -> false
                transient -> true
                TrackedApps.isBrowser(activePkg!!) -> web?.inReels(TrackedApps.forPackage(activePkg).key) == true
                else -> detector?.reelsStillOnScreen(root) == true ||
                    detector?.findReelPager(root) == true ||
                    snap?.inReels(TrackedApps.forPackage(activePkg).key) == true
            }
            if (stillThere) misses = 0 else misses++
            // Some video viewers change pages without any event we'd react to: look anyway.
            if (stillThere && !transient && activePkg != null && activePkg == pillPkg && detector?.hasSignature(activePkg) != true)
                queuePageCheck(activePkg)
            // Watch time: while the reels feed is on screen (and the screen is on), the clock runs.
            val now = SystemClock.uptimeMillis()
            val interactive = runCatching { getSystemService(android.os.PowerManager::class.java)?.isInteractive }.getOrNull() != false
            if (stillThere && !transient && interactive && activePkg != null) {
                val key = TrackedApps.forPackage(activePkg).key
                if (watchKey != null && watchKey != key) flushWatchTime()
                watchKey = key
                if (lastWatchTick > 0L) watchMs += (now - lastWatchTick).coerceIn(0L, 2_000L)
                lastWatchTick = now
                if (watchMs >= 15_000L) flushWatchTime()
            } else {
                lastWatchTick = 0L
            }
            if (left || misses >= HIDE_AFTER_MISSES || s?.bubbleOn != true || s.paused) leaveReels()
            else handler.postDelayed(this, WATCH_MS)
        }
    }

    /** The app the counter is showing for (set when it appears). */
    private var pillPkg: String? = null

    /** You left the reels: hide the counter, save watch time, and look for reels again. */
    private fun leaveReels() {
        misses = 0
        flushWatchTime()
        lastWatchTick = 0L
        pill?.hide()
        pillPkg = null
        handler.removeCallbacks(watcher)
        Notifier.clearLive(this@ScrollTrackerService)
        detector?.forgetPager()
        startPresenceScan()
    }

    private var misses = 0

    // ---- watch time (time spent in reels feeds), saved in small batches ----
    private var watchKey: String? = null
    private var watchMs = 0L
    private var lastWatchTick = 0L

    private fun flushWatchTime() {
        val key = watchKey ?: return
        val secs = (watchMs / 1000L).toInt()
        if (secs > 0) {
            store?.addWatchTime(key, secs)
            watchMs -= secs * 1000L
        }
    }

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
                // Limit reached: Reels may not be opened — close the app (the rest of it is fine).
                if (shouldBlock(s)) { DetectLog.add(pkg, "limit reached + reels opened → closed"); block(app.name); return }
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
    }

    private fun buildIgnoreList() {
        ignored.clear()
        ignored += setOf(packageName, "com.android.systemui", "android", "com.android.settings",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.google.android.packageinstaller", "com.android.packageinstaller")
        transientPkgs.clear()
        transientPkgs += setOf("com.android.systemui", "android",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller")
        launchers.clear()
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
                .forEach { ignored += it.activityInfo.packageName; launchers += it.activityInfo.packageName }
        }
        runCatching {
            val imm = getSystemService(InputMethodManager::class.java)
            imm?.enabledInputMethodList?.forEach { ignored += it.packageName; transientPkgs += it.packageName }
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

        // Home screen (or another app's window) in front: the reels are left — hide at once.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pill?.isShowing == true &&
            pkg !in transientPkgs && pillPkg != null && pkg != pillPkg &&
            runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull().let { it == null || it != pillPkg }) leaveReels()
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
                } else detector?.screenChanged(app.key)
                // Limit reached: the app itself stays usable (posts, chats…) — only opening Reels /
                // Shorts closes it, which the reels checks below take care of.
                // Back in a social app: look for the reels feed straight away.
                if (!isBrowser && store.isAppEnabled(app.key) && isSocial(pkg, app)) startPresenceScan(80L)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (isBrowser) return
                // In reels: see whether a different video page now fills the screen.
                if (pill?.isShowing == true && pkg == pillPkg && detector?.hasSignature(pkg) != true) queuePageCheck(pkg)
                // Shorts player open: the short on screen may have changed (some apps send no
                // scroll events when you swipe) — check now and then.
                if (pill?.isShowing == true && detector?.hasSignature(pkg) == true) {
                    val app = TrackedApps.forPackage(pkg)
                    if (store.isAppEnabled(app.key)) queueSignatureCheck(app, SIG_CHECK_MS, restart = false)
                }
                // Cheap, throttled check: did the user just open a Reels / Shorts feed?
                val now = SystemClock.uptimeMillis()
                if (!store.bubbleOn || pill?.isShowing == true || now - lastScanAt < SCAN_GAP_MS) return
                val app = TrackedApps.forPackage(pkg)
                if (!store.isAppEnabled(app.key) || !isSocial(pkg, app)) return
                lastScanAt = now
                val det = detector ?: return
                if (det.findReelPager(runCatching { rootInActiveWindow }.getOrNull())) {
                    if (shouldBlock(store)) { DetectLog.add(pkg, "limit reached + reels opened → closed"); block(app.name); return }
                    if (det.prime(app.key)) { DetectLog.add(pkg, "reels opened → +1 (first reel)"); onNewReels(app, 1, det.lastPagerHeight) }
                    else showPill(store, store.todayTotal(), store.dailyLimit, bump = false)
                }
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val app = TrackedApps.forPackage(pkg)
                if (!store.isAppEnabled(app.key)) return
                if (!isBrowser) detector?.noteMotion(app.key)
                if (!isBrowser && pill?.isShowing == true && pkg == pillPkg && detector?.hasSignature(pkg) != true) queuePageCheck(pkg)
                if (isBrowser) onBrowserScroll(store, app, event) else onAppScroll(store, app, event)
            }
        }
    }

    private fun onAppScroll(store: ScrollStore, app: TrackedApp, event: AccessibilityEvent) {
        val det = detector ?: return
        // YouTube Shorts & co.: recognised by their player and counted by which short is on screen.
        if (det.hasSignature(event.packageName?.toString() ?: "")) {
            if (!det.signatureScroll(app.key, event)) return
            if (shouldBlock(store)) { block(app.name); return }
            queueSignatureCheck(app, ReelDetector.SETTLE_MS, restart = true)
            if (pill?.isShowing != true && store.bubbleOn) showPill(store, store.todayTotal(), store.dailyLimit, bump = false)
            return
        }
        if (!det.onScroll(app.key, event)) {
            // A feed / profile / inbox list is being scrolled: you're out of the reels.
            if (det.leftReels && pill?.isShowing == true) { DetectLog.add(app.packages.first(), "list scrolled → left reels"); leaveReels() }
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
        // Readable reels pager → close right away. Self-drawn apps (Facebook Lite) report feed and
        // reels from the same view, so for them we wait until the swipe is confirmed as reels.
        // Anything not yet proven to be reels is only closed / shown after its swipes confirm it.
        if (!det.lastAcceptedOpaque && det.lastAcceptedConfident && shouldBlock(store)) { block(app.name); return }
        // Judge the swipe once the feed is still.
        settleApp = app
        handler.removeCallbacks(settleRunnable)
        handler.postDelayed(settleRunnable, ReelDetector.SETTLE_MS)
        if (det.lastAcceptedConfident && pill?.isShowing != true && store.bubbleOn) showPill(store, store.todayTotal(), store.dailyLimit, bump = false)
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
        if (shouldBlock(store)) { block(app.name); return }   // limit already reached: no more reels
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
        if (!wasShowing) {
            pillPkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()?.takeIf { it !in ignored } ?: currentPkg
            misses = 0
        }
        p.show(total, limit, bump, store.pillSize)
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
        flushWatchTime(); lastWatchTick = 0L
        Notifier.clearLive(this)
        detector?.forgetPager()
        val blockedPkg = currentPkg
        // Coming back to it (recent apps) must close it again on the very first reel.
        blockedPkg?.let { detector?.noteBlocked(TrackedApps.forPackage(it).key) }
        // Open the limit screen straight over the app instead of pressing Home first: pressing Home
        // makes video apps (YouTube…) shrink into a floating picture-in-picture window and keep
        // playing. Covered by our screen, the app just stops.
        val i = Intent(this, BlockActivity::class.java)
            .putExtra(BlockActivity.EXTRA_APP, appName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (runCatching { startActivity(i) }.isFailure) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            handler.postDelayed({ runCatching { startActivity(i) } }, 250)
        }
        // If a floating video window still appears, close it; then end the app in the background.
        handler.postDelayed({ closeFloatingVideo(4) }, 450)
        // End it in the background — tried a few times, as some phones keep it alive at first — so
        // going back to it from recent apps restarts it on its home screen, not on the Reels.
        if (blockedPkg != null && !TrackedApps.isBrowser(blockedPkg)) {
            for (delay in longArrayOf(1500L, 3500L, 7000L)) handler.postDelayed({ endApp(blockedPkg) }, delay)
        }
    }

    /** Closes any picture-in-picture window by dragging it onto the system's dismiss target. */
    private fun closeFloatingVideo(tries: Int) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val pip = runCatching { windows.firstOrNull { it.isInPictureInPictureMode } }.getOrNull()
        if (pip == null) {
            if (tries > 0) handler.postDelayed({ closeFloatingVideo(tries - 1) }, 500)
            return
        }
        val r = android.graphics.Rect()
        pip.getBoundsInScreen(r)
        val dm = resources.displayMetrics
        val tx = dm.widthPixels / 2f
        val ty = dm.heightPixels * 0.9f
        val drag = android.graphics.Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()); lineTo(tx, ty) }
        val hold = android.graphics.Path().apply { moveTo(tx, ty); lineTo(tx + 1f, ty + 1f) }
        val s1 = android.accessibilityservice.GestureDescription.StrokeDescription(drag, 0, 600, true)
        val s2 = s1.continueStroke(hold, 0, 450, false)
        val again = { handler.postDelayed({ closeFloatingVideo(tries - 1) }, 700); Unit }
        val ok = runCatching {
            dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(s1).build(), object : GestureResultCallback() {
                override fun onCompleted(g: android.accessibilityservice.GestureDescription?) {
                    runCatching { dispatchGesture(android.accessibilityservice.GestureDescription.Builder().addStroke(s2).build(), null, null) }
                    if (tries > 0) again()
                }
                override fun onCancelled(g: android.accessibilityservice.GestureDescription?) { if (tries > 0) again() }
            }, null)
        }.getOrDefault(false)
        if (!ok && tries > 0) again()
    }

    /** Ends the blocked app's background process, so it's really closed (not just hidden). */
    private fun endApp(pkg: String) {
        runCatching {
            getSystemService(android.app.ActivityManager::class.java)?.killBackgroundProcesses(pkg)
        }
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
        flushWatchTime()
        handler.removeCallbacksAndMessages(null)
        Notifier.clearLive(this)
        pill?.destroy()
        super.onDestroy()
    }

    companion object {
        private const val WATCH_MS = 800L
        /** Page watch: look this long after the screen last changed (the swipe has settled). */
        private const val PAGE_CHECK_MS = 420L
        /** Reels feed missing from the screen this many checks in a row (~3 s) = you left. */
        private const val HIDE_AFTER_MISSES = 4
        private const val SCAN_GAP_MS = 300L
        private const val PRESENCE_MS = 450L
        private const val PRESENCE_MAX_TRIES = 24
        private const val WEB_SETTLE_MS = 420L
        private const val SIG_CHECK_MS = 500L
        private const val URL_GAP_MS = 350L
        private const val GUARD_GAP_MS = 400L
        const val BUBBLE_EVERY = 1
        const val WARN_AT = 0.8f
    }
}
