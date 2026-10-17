package com.rabbi.scrollcounter.tracker

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Recognises a Reels / Shorts / TikTok-style feed in ANY app and turns its scrolling into an exact
 * "one new reel = +1".
 *
 * Accuracy rules
 *  • A reels feed is a vertical pager that fills the screen from the top, whose items are each a
 *    full screen tall. Comment sheets, chat lists, description panels, side-scrolling galleries and
 *    normal feeds never match (they start lower, have short rows, or scroll sideways).
 *  • Counting happens only when a swipe has SETTLED (the service calls [settle] ~350 ms after the
 *    last scroll event). We then read which item is on screen right now — the pager's own item
 *    number — so fast swipes, half swipes that bounce back and coalesced events are all exact.
 *  • Only moving FORWARD to a reel you haven't reached yet in this feed counts; going back to
 *    rewatch one doesn't.
 *
 * Only the layout (sizes, view ids, item numbers) is looked at — never text, captions or comments.
 */
class ReelDetector(private val screenW: Int, private val screenH: Int) {

    private class AppState {
        var pagerKey = ""
        var maxIndex = Int.MIN_VALUE     // furthest reel reached in this feed
        var eventIndex = Int.MIN_VALUE   // last settled index reported by events
        var accum = 0f                   // signed distance moved since the last settle
        var pending = false              // a swipe happened and hasn't been settled yet
        var hadIndex = false             // this swipe reported item indexes
        var hadDelta = false             // this swipe reported a distance
        var lastCountAt = 0L
        var primedKey = ""
        var primedIndex: Int? = null
        var lastIdx = Int.MIN_VALUE      // index seen at the previous settle
        var indexReliable = false        // the item number has been seen to change → trust it
        var opaque = false               // container exposes no children (e.g. Facebook Lite)
        var lastScrollY = -1
        var retries = 0
        var opaqueStreak = 0
        var allReels = false             // TikTok-style app: the whole feed is short videos
        var currentChild: Int? = null    // identity of the page view that filled the pager last
        var currentFp: String? = null
        val seenFps = LinkedHashSet<String>()
        var behind = 0                   // reels you went back by (feeds that only report distance)
        var eventFresh = false           // this swipe ended on an exact item number (from == to)
        var pagerNode = 0                // identity of the pager view (changes when the app rebuilds it)
        var candStreak = 0               // exact one-screen swipes on a not-yet-proven pager
        var opaqueOdd = 0                // self-drawn apps: feed-like (not one-screen) moves in a row
        // Signature apps (YouTube Shorts…): which short is on screen, and the furthest one reached.
        var sigKey: String? = null
        var sigMax = Int.MIN_VALUE
        var sigPager = 0
    }

    private val states = HashMap<String, AppState>()
    private val pagerCache = HashMap<String, Boolean>()
    private val rect = Rect()
    private val child = Rect()

    /** The reels container seen last, so the on-screen counter can tell when you leave it. */
    var lastPager: AccessibilityNodeInfo? = null
        private set
    var lastPagerHeight: Int = screenH
        private set

    fun reset(appKey: String) {
        states.remove(appKey)
    }

    /** Pager views confirmed as reels feeds (by view identity), see [onScroll]. */
    private val trustedPagers = HashSet<Int>()

    /**
     * Records one scroll event. Returns true if it came from a reels feed (the caller should then
     * schedule [settle]). Never counts by itself.
     */
    fun onScroll(appKey: String, event: AccessibilityEvent): Boolean {
        val src = event.source ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            event.scrollDeltaX != 0 && abs(event.scrollDeltaX) > abs(event.scrollDeltaY)) return false
        // A reels pager never shows more than two items at once (the one leaving and the one
        // arriving). A list reporting 3+ visible items is a feed, inbox, comments… — not reels.
        val span = if (event.fromIndex >= 0 && event.toIndex >= 0) event.toIndex - event.fromIndex else 0
        // Once a scroller has been confirmed as a reels pager, the very same view keeps counting —
        // its layout looks messy mid-swipe and must not make us drop a reel. (It's the same view
        // object, so a feed or inbox that merely looks similar is never trusted this way.)
        val nodeId = src.hashCode()
        val trusted = nodeId in trustedPagers && run { src.getBoundsInScreen(rect); rect.height() >= screenH * 0.5f }
        if (trusted) lastWasOpaque = false
        val accepted = trusted || (span < 2 && isReelPager(src))
        if (!trusted && span >= 2) lastReason = "list shows ${span + 1} items"
        // Only a container that is surely reels is trusted through messy mid-swipe layouts.
        val sure = accepted && !lastWasOpaque && (isConfident(src) || isConfident(runCatching { innerPager(src) }.getOrNull()))
        lastAcceptedConfident = sure || (trusted && nodeId in trustedPagers)
        if (sure) {
            if (trustedPagers.size > 20) trustedPagers.clear()
            trustedPagers.add(nodeId)
        }
        // A different full-page scroller that is clearly a list (feed, profile, inbox) is being
        // scrolled: the user has left the reels. (Comment sheets start lower and never count.)
        leftReels = false
        if (!accepted) {
            src.getBoundsInScreen(rect)
            val fullPage = rect.width() >= screenW * 0.85f && rect.height() >= screenH * 0.6f && rect.top <= screenH * 0.22f
            val listy = lastReason.startsWith("list shows") || lastReason == "page is a list" || lastReason == "holds a list"
            val isReelsNode = lastPager?.let { it.hashCode() == nodeId || runCatching { innerPager(src) }.getOrNull()?.hashCode() == it.hashCode() } == true
            if (fullPage && listy && !isReelsNode) leftReels = true
        }
        val cn = src.className?.toString() ?: ""
        lastSourceNativeList = NATIVE_LISTS.any { cn.endsWith(it) }
        DetectLog.add(
            src.packageName?.toString() ?: appKey,
            "${src.className?.toString()?.substringAfterLast('.') ?: "?"}" +
                (src.viewIdResourceName?.substringAfter(":id/")?.let { " #$it" } ?: "") +
                " h${rect.height() * 100 / screenH.coerceAtLeast(1)}% top${rect.top * 100 / screenH.coerceAtLeast(1)}%" +
                " kids${src.childCount}" +
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) " dy${event.scrollDeltaY}" else "") +
                " y${event.scrollY} i${event.fromIndex}-${event.toIndex}" +
                if (accepted) " → REELS" else " → no ($lastReason)"
        )
        if (!accepted) return false
        val opaque = lastWasOpaque
        if (sure) rememberShape(innerPager(src) ?: src)
        // ViewPager2: the real pager of videos is the single inner list — track that one.
        lastPager = innerPager(src) ?: src
        lastPagerHeight = rect.height().coerceAtLeast(1)
        src.viewIdResourceName?.let { id -> src.packageName?.toString()?.let { knownPagerIds[it] = id } }

        val st = states.getOrPut(appKey) { AppState() }
        val key = "${src.viewIdResourceName}|${src.className}"
        st.opaque = opaque
        st.allReels = (src.packageName?.toString() ?: "") in ALL_REELS_APPS
        val from = event.fromIndex
        val to = event.toIndex
        if (key != st.pagerKey) {
            // A different reels feed (e.g. opened from a profile): start a fresh baseline. The
            // first event of a swipe still shows the reel we started on as its lowest index.
            st.pagerKey = key
            st.maxIndex = if (st.primedIndex != null && st.primedKey == key) st.primedIndex!!
                else if (from >= 0) from else Int.MIN_VALUE
            st.eventIndex = Int.MIN_VALUE
            st.lastIdx = Int.MIN_VALUE
            st.indexReliable = false
            st.lastScrollY = -1
            st.accum = 0f
            st.behind = 0
        }

        // The app rebuilt its feed (back from another screen or app, refreshed…): numbers and
        // distances from before don't apply any more, so start a fresh baseline from here.
        val node = lastPager?.hashCode() ?: 0
        if (st.pagerNode != 0 && node != 0 && node != st.pagerNode) {
            st.maxIndex = if (from >= 0) from else Int.MIN_VALUE
            st.lastIdx = Int.MIN_VALUE
            st.eventIndex = Int.MIN_VALUE
            st.indexReliable = false
            st.behind = 0
            st.lastScrollY = -1
            st.currentChild = null
            DetectLog.add(appKey, "feed rebuilt → fresh start")
        }
        if (node != 0) st.pagerNode = node
        if (from >= 0 && to >= 0) {
            st.hadIndex = true
            if (from == to) { st.eventIndex = from; st.eventFresh = true }
        }
        var dy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY else 0
        // Older phones / custom views: work the distance out from the scroll position instead.
        val y = event.scrollY
        if (dy == 0 && y >= 0 && st.lastScrollY >= 0) dy = y - st.lastScrollY
        if (y >= 0) st.lastScrollY = y
        if (dy != 0 && abs(dy) < screenH * 3) {
            st.accum += dy
            st.hadDelta = true
        }
        st.pending = true
        return true
    }

    /**
     * The feed has been still for a moment. Returns how many NEW reels the user moved to (0..3).
     */
    fun settle(appKey: String, root: AccessibilityNodeInfo? = null): Int {
        val st = states[appKey] ?: return 0
        val pager = lastPager
        val confident = st.opaque || isConfident(pager)
        lastMoveExact = false
        leftReels = false
        atRestNotReels = false
        val n = settleInner(appKey, st, root)
        if (n > 0 && confident) rememberShape(lastPager)
        if (n <= 0 || confident) {
            // An unconfirmed page that, at rest, isn't a reel page at all: not in reels.
            if (n == 0 && atRestNotReels && (!confident || st.opaque)) leftReels = true
            return n
        }
        // Not proven to be reels yet (no reels view name, not a short-video-only app): a profile,
        // photo viewer or post can look like a full-screen page too. Only a run of exact one-screen
        // swipes onto new pages proves it — profiles and feeds never move like that. Until then
        // nothing is counted, shown or closed.
        if (n == 1 && lastMoveExact) {
            st.candStreak++
            if (st.candStreak >= 2) {
                pager?.let { provenNodes.add(it.hashCode()) }
                rememberShape(pager)
                st.candStreak = 0
                DetectLog.add(appKey, "2 exact reel swipes → reels confirmed")
                return 2
            }
            DetectLog.add(appKey, "exact page swipe 1/2 → waiting to confirm reels")
            return 0
        }
        st.candStreak = 0
        leftReels = true
        DetectLog.add(appKey, "not an exact reel swipe on an unconfirmed page → not counted")
        return 0
    }

    /**
     * Set after [onScroll] / [settle] when what just happened clearly is NOT reels: a feed,
     * profile or inbox list was scrolled, or a page moved like a normal feed. The counter hides
     * right away then; otherwise it stays up as long as the reels container is on screen.
     */
    var leftReels = false
        private set
    private var atRestNotReels = false

    // The reels container last confirmed, so a redrawn copy of it (apps rebuild their views when
    // a new video loads) is still recognised and the counter doesn't blink off while in reels.
    private var shapePkg = ""
    private var shapeId = ""
    private var shapeCls = ""

    private fun rememberShape(n: AccessibilityNodeInfo?) {
        n ?: return
        shapePkg = n.packageName?.toString() ?: return
        shapeId = n.viewIdResourceName ?: ""
        shapeCls = n.className?.toString() ?: ""
    }

    private fun fillsScreen(n: AccessibilityNodeInfo): Boolean = runCatching {
        n.isVisibleToUser && run {
            n.getBoundsInScreen(tmp)
            tmp.width() >= screenW * 0.85f && tmp.height() >= screenH * 0.6f && tmp.top <= screenH * 0.22f
        }
    }.getOrDefault(false)

    /**
     * Is the confirmed reels feed still on screen? True while the same container — or a fresh copy
     * of it (same view name, or same kind of view when it has no name) — still fills the screen.
     * Used to keep the counter up the whole time you're in reels, even while a video loads.
     */
    fun reelsStillOnScreen(root: AccessibilityNodeInfo?): Boolean {
        if (stillInReels()) return true
        root ?: return false
        val pkg = root.packageName?.toString() ?: return false
        if (pkg != shapePkg || (shapeId.isEmpty() && shapeCls.isEmpty())) return false
        if (shapeId.isNotEmpty()) {
            val hits = runCatching { root.findAccessibilityNodeInfosByViewId(shapeId) }.getOrNull().orEmpty()
            hits.firstOrNull { fillsScreen(it) }?.let { lastPager = it; return true }
        }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 220) {
            val n = queue.removeFirst()
            visited++
            val idOk = shapeId.isEmpty() || n.viewIdResourceName == shapeId
            // Same kind of container, filling the screen, and still laid out like a reels pager
            // (a profile grid or feed list of the same view type is not).
            if (idOk && n.className?.toString() == shapeCls && fillsScreen(n) && !scrollsOnlySideways(n) &&
                runCatching { isReelPager(n) }.getOrDefault(false)) { lastPager = n; return true }
            n.getBoundsInScreen(tmp)
            if (tmp.height() >= screenH * 0.4f) for (i in 0 until minOf(n.childCount, 14)) runCatching { n.getChild(i) }.getOrNull()?.let { queue.add(it) }
        }
        return false
    }

    /** Set by [settleInner]: the swipe moved exactly one whole page onto a new one. */
    private var lastMoveExact = false

    /** Pager views proven to be reels by exact one-screen swipes (by view identity). */
    private val provenNodes = HashSet<Int>()

    /**
     * Is this container known for sure to be a reels / shorts feed — by its view name, by being a
     * short-video-only app (TikTok…), or by having been proven with exact page swipes?
     */
    private fun isConfident(n: AccessibilityNodeInfo?): Boolean {
        n ?: return false
        val pkg = n.packageName?.toString() ?: ""
        if (pkg in ALL_REELS_APPS) return true
        val id = (n.viewIdResourceName ?: "").lowercase()
        if (id.isNotEmpty() && ID_HINTS.any { id.contains(it) }) return true
        return n.hashCode() in provenNodes
    }

    /** The last accepted scroll came from a container that is surely reels (see [isConfident]). */
    var lastAcceptedConfident = false
        private set

    private fun settleInner(appKey: String, st: AppState, root: AccessibilityNodeInfo?): Int {
        if (!st.pending) return 0
        // A comment / share sheet open over the reel: scrolling it is never a new reel, even when
        // the app (Facebook Lite…) reports that scroll from the same view as the video feed.
        if (root != null && sheetOpen(root)) {
            st.pending = false; st.retries = 0
            st.hadIndex = false; st.hadDelta = false; st.accum = 0f
            DetectLog.add(appKey, "comment/share sheet open → not counted")
            return 0
        }
        // Give the feed a moment to finish snapping onto the next video before reading it.
        if (!st.opaque && !st.allReels) {
            // At rest, the screen must still look like a reels feed. Apps that draw everything in
            // one view (Facebook Lite) report every screen — feed, comments, reels — from the same
            // scroller, so the swipe is only counted if, once settled, it's really a reel page.
            val pg = lastPager
            val ok = pg != null && isSnapped() && runCatching { pg.refresh() && isReelPager(pg) && !lastWasOpaque }.getOrDefault(false)
            if (!ok) {
                if (st.retries < MAX_RETRIES) { st.retries++; return RETRY }
                st.retries = 0
                st.pending = false
                st.hadIndex = false; st.hadDelta = false; st.accum = 0f
                atRestNotReels = true
                DetectLog.add(appKey, "at rest: not a reel page ($lastReason)")
                return 0
            }
        }
        st.retries = 0
        st.pending = false
        val hadIndex = st.hadIndex
        val hadDelta = st.hadDelta
        val accum = st.accum
        st.hadIndex = false
        st.hadDelta = false
        st.accum = 0f

        val h = lastPagerHeight.toFloat().coerceAtLeast(1f)
        val pages = accum / h

        // Apps that draw everything themselves (Facebook Lite…): only a run of exact one-screen
        // swipes is a reels feed — a normal feed fling never lands on exactly one screen twice.
        if (st.opaque) {
            if (!hadDelta) return 0
            val whole = pages.roundToInt()
            val exact = accum > 0 && whole == 1 && abs(pages - whole) <= 0.05f
            // A real move that isn't one exact screen is a normal feed being scrolled (same view as
            // the reels in these apps): that's leaving the reels.
            // Two such moves in a row, so one odd swipe (or a comment list) never hides the counter.
            if (!exact && abs(pages) >= 0.35f) { st.opaqueOdd++; if (st.opaqueOdd >= 2) atRestNotReels = true }
            if (exact) st.opaqueOdd = 0
            if (!exact) { st.opaqueStreak = 0; DetectLog.add(appKey, "self-drawn: swipe ${"%.2f".format(pages)} screens → not reels"); return 0 }
            st.opaqueStreak++
            DetectLog.add(appKey, "self-drawn: exact snap #${st.opaqueStreak}")
            // Three exact one-screen snaps in a row before believing it's a reels feed — comment
            // lists and news feeds never move like that. Then the three are counted together.
            return when {
                st.opaqueStreak >= 4 -> mark(st, 1)
                st.opaqueStreak == 3 -> mark(st, 3)
                else -> 0
            }
        }

        // One finished swipe on a confirmed reels feed. Decide forward / back / bounce from
        // whatever the app reports; when it reports nothing, the swipe itself is the new reel.
        // Which reel is on screen now: the pager's own item number is best; the number from the
        // scroll events is used only if this swipe actually ended on one (a stale number from an
        // earlier swipe would make a real new reel look like "the same one").
        var index = currentItemIndex()
        if (index == null && hadIndex && st.eventFresh && st.eventIndex != Int.MIN_VALUE) index = st.eventIndex
        st.eventFresh = false
        // Identity of the page view filling the screen: a different one = you moved to another reel.
        val childNow = snappedChildKey()
        val childBefore = st.currentChild
        if (childNow != null) st.currentChild = childNow
        val prev = st.lastIdx
        if (index != null) st.lastIdx = index

        if (index != null && prev != Int.MIN_VALUE) {
            if (index > prev) {
                st.indexReliable = true
                // Only reels past the furthest one reached count: going back and forward again
                // over reels you've already seen never counts them twice.
                val frontier = maxOf(prev, st.maxIndex)
                st.maxIndex = maxOf(st.maxIndex, index)
                st.behind = 0
                if (index <= frontier) { DetectLog.add(appKey, "forward to #$index, already seen → not counted"); return 0 }
                lastMoveExact = index - prev == 1
                return mark(st, (index - frontier).coerceAtMost(3))
            }
            if (index < prev) {
                st.indexReliable = true
                // A jump back of many reels at once is the app refreshing its feed, not you swiping
                // back: start counting from here again.
                if (prev - index > 3) { st.maxIndex = index; st.behind = 0; DetectLog.add(appKey, "feed restarted at #$index"); return 0 }
                st.maxIndex = maxOf(st.maxIndex, prev)
                st.behind = (st.maxIndex - index).coerceIn(0, 30); return 0       // went back
            }
            // Same number: trust it if it has proven reliable, otherwise fall through to distance.
            if (st.indexReliable && !(hadDelta && pages >= 0.6f)) return 0
        }
        if (hadDelta) {
            if (pages <= -0.35f) {                                                              // went back
                val k = (-pages).roundToInt().coerceIn(1, 3)
                st.behind = (st.behind + k).coerceAtMost(30)
                if (index == null && st.lastIdx != Int.MIN_VALUE) st.lastIdx -= k
                return 0
            }
            if (pages < 0.35f) return 0                                                         // bounce or nudge
            var n = pages.roundToInt().coerceIn(1, 3)
            // Coming forward again over reels you went back past: those were already counted.
            val again = minOf(n, st.behind)
            st.behind -= again; n -= again
            if (index == null && st.lastIdx != Int.MIN_VALUE) st.lastIdx += pages.roundToInt().coerceIn(1, 3)
            if (n == 0) { DetectLog.add(appKey, "forward to a reel already seen → not counted"); return 0 }
            advance(st, index)
            lastMoveExact = abs(pages - 1f) <= 0.08f && childNow != null && childNow != childBefore
            return mark(st, n)
        }
        // The app reported neither a number nor a distance: go by whether a different page view
        // now fills the screen. Same page view = the swipe bounced back onto the same reel.
        if (childNow != null && childBefore != null) {
            if (childNow == childBefore) { DetectLog.add(appKey, "same page on screen → not counted"); return 0 }
            if (index == null && st.lastIdx != Int.MIN_VALUE) st.lastIdx += 1
            advance(st, index)
            lastMoveExact = true
            return mark(st)
        }
        if (index == null && st.lastIdx != Int.MIN_VALUE) st.lastIdx += 1
        advance(st, index)
        return mark(st)
    }

    /** A reel was counted without the app's own number: move the "furthest reached" mark along. */
    private fun advance(st: AppState, index: Int?) {
        val at = index ?: st.lastIdx.takeIf { it != Int.MIN_VALUE } ?: return
        st.maxIndex = maxOf(st.maxIndex, at)
    }

    /** Identity of the page view that exactly fills the pager, or null. No text is read. */
    private fun snappedChildKey(): Int? {
        val pager = lastPager ?: return null
        return try {
            pager.getBoundsInScreen(rect)
            val h = rect.height()
            if (h <= 0) return null
            for (i in 0 until minOf(pager.childCount, 6)) {
                val c = pager.getChild(i) ?: continue
                c.getBoundsInScreen(child)
                if (abs(child.top - rect.top) <= h * 0.03f && child.height() >= h * 0.96f) return c.hashCode()
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** True when one page fills the pager exactly (the feed has snapped onto a video). */
    private fun isSnapped(): Boolean {
        val pager = lastPager ?: return false
        return try {
            if (!pager.refresh()) return false
            pager.getBoundsInScreen(rect)
            val h = rect.height()
            if (h <= 0) return false
            val n = minOf(pager.childCount, 6)
            if (n == 0) return true
            for (i in 0 until n) {
                val c = pager.getChild(i) ?: continue
                c.getBoundsInScreen(child)
                if (abs(child.top - rect.top) <= h * 0.03f && child.height() >= h * 0.96f) return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun mark(st: AppState, n: Int = 1): Int {
        val now = SystemClock.uptimeMillis()
        if (now - st.lastCountAt < MIN_COUNT_GAP_MS) return 0
        st.lastCountAt = now
        return n
    }

    /** Index of the item that fills the pager now, from the pager's own collection info. */
    private fun currentItemIndex(): Int? {
        val pager = lastPager ?: return null
        return try {
            if (!pager.refresh()) return null
            pager.getBoundsInScreen(rect)
            val h = rect.height()
            if (h <= 0) return null
            for (i in 0 until minOf(pager.childCount, 6)) {
                val c = pager.getChild(i) ?: continue
                c.getBoundsInScreen(child)
                if (child.height() >= h * 0.9f && child.top <= rect.top + h * 0.05f) {
                    val info = c.collectionItemInfo ?: return null
                    return info.rowIndex.takeIf { it >= 0 }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Set by [isReelPager] when the accepted container has no children (self-drawn apps). */
    private var lastWasOpaque = false

    /** The last accepted scroll came from a self-drawn app (feed and reels look the same there). */
    val lastAcceptedOpaque: Boolean get() = lastWasOpaque

    /** The last rejected scroller was a standard Android list, whose layout we can read reliably. */
    var lastSourceNativeList = false
        private set
    private var lastReason = ""

    private fun isReelPager(src: AccessibilityNodeInfo): Boolean {
        lastWasOpaque = false
        lastReason = ""
        src.getBoundsInScreen(rect)
        // 1. Must fill the screen: full width and most of the height. Comment / share sheets
        //    slide up from the bottom and start low on the screen, so they're rejected.
        if (rect.width() < screenW * 0.85f) { lastReason = "narrow"; return false }
        if (rect.height() < screenH * 0.6f) { lastReason = "short"; return false }
        if (rect.top > screenH * 0.22f) { lastReason = "starts low"; return false }

        val id = src.viewIdResourceName ?: ""
        val cls = src.className?.toString() ?: ""
        val cacheKey = "${src.packageName}|$id|$cls|${rect.height()}"
        pagerCache[cacheKey]?.let { if (!it) lastReason = "excluded"; return it }

        val lower = id.lowercase()
        // 2. Never a reel: comments, replies, chats, sheets, menus, search results, grids.
        if (EXCLUDE_HINTS.any { lower.contains(it) }) { lastReason = "comments/chat id"; return remember(cacheKey, false) }
        if (cls.contains("WebView") || cls.endsWith("HorizontalScrollView") || cls.contains("GridView"))
            { lastReason = "web/grid"; return remember(cacheKey, false) }
        if (scrollsOnlySideways(src)) { lastReason = "sideways"; return remember(cacheKey, false) }
        val ci = src.collectionInfo
        if (ci != null && ci.columnCount > 1 && ci.rowCount <= 1) { lastReason = "horizontal list"; return remember(cacheKey, false) }

        val named = ID_HINTS.any { lower.contains(it) }
        val n = minOf(src.childCount, 6)
        if (n == 0) {
            // Self-drawn apps (Facebook Lite…) expose no children. Accept a full-screen scroller
            // in a known short-video app; counting then needs an exact one-screen swipe.
            val pkg = src.packageName?.toString() ?: ""
            if (rect.height() >= screenH * 0.75f && (named || com.rabbi.scrollcounter.data.TrackedApps.isKnown(pkg))) {
                lastWasOpaque = true
                return true
            }
            lastReason = "no children"
            return false // maybe still loading; decide later (not cached)
        }

        // 3. Its items must be pages exactly as tall as the feed itself, stacked vertically.
        //    A news feed has posts of different heights, so a fully visible item that is
        //    shorter than the feed means it's NOT reels — even if one big post fills most of it.
        val hh = rect.height()
        var exactPages = 0
        var otherItems = 0
        var sideBySide = 0
        var prevTop = Int.MIN_VALUE
        var prevLeft = Int.MIN_VALUE
        for (i in 0 until n) {
            val c = runCatching { src.getChild(i) }.getOrNull() ?: continue
            c.getBoundsInScreen(child)
            if (child.isEmpty) continue
            val touchesEdge = child.top <= rect.top + 3 || child.bottom >= rect.bottom - 3
            if (child.height() >= hh * 0.5f && child.height() < hh * 0.96f && pageLooksLikeList(c) && !isPagerOfPages(c)) { lastReason = "holds a list"; return false }
            if (abs(child.height() - hh) <= hh * 0.04f && child.width() >= rect.width() * 0.9f) {
                // A wrapper whose single child is a whole scrolling list (inbox, feed, settings)
                // looks like a "page" — but a reel page is never itself a list.
                // ViewPager2 wraps an inner RecyclerView that is itself the pager of video pages —
                // that's fine. Only a list of ordinary rows (inbox, feed, settings) is rejected.
                if (pageLooksLikeList(c) && !isPagerOfPages(c)) { lastReason = "page is a list"; return false }
                exactPages++
            }
            else if (!touchesEdge && child.height() > hh * 0.05f) otherItems++
            else if (touchesEdge && child.height() < hh * 0.96f && child.width() >= rect.width() * 0.9f) {
                // a page partly scrolled in/out at the edge — fine
            }
            if (prevTop != Int.MIN_VALUE && abs(child.top - prevTop) < 8 && abs(child.left - prevLeft) > rect.width() / 4) sideBySide++
            prevTop = child.top; prevLeft = child.left
        }
        // Mid-swipe two pages are each partly visible; at rest one fills it exactly.
        val midSwipe = exactPages == 0 && otherItems == 0 && n in 1..3
        val pkgName = src.packageName?.toString() ?: ""
        // Apps that are short videos all the way through (TikTok, Likee, Kwai…): any full-screen
        // vertical pager is the video feed. Profile grids and comment sheets are still rejected.
        val allReels = pkgName in ALL_REELS_APPS
        val ok = if (allReels) sideBySide == 0 && otherItems == 0 && (exactPages >= 1 || midSwipe)
            else sideBySide == 0 && otherItems == 0 && (exactPages >= 1 || (midSwipe && (named || lastPager?.let { samePager(it, src) } == true)))
        if (!ok) lastReason = "items exact$exactPages other$otherItems row$sideBySide"
        // Not cached: a feed and a reels viewer can share the same view class and size.
        return ok
    }

    private val tmp = Rect()
    private val tmp2 = Rect()

    private fun innerPager(src: AccessibilityNodeInfo): AccessibilityNodeInfo? = try {
        if (src.childCount == 1) src.getChild(0)?.takeIf { it.childCount > 0 && isPagerOfPages(it) } else null
    } catch (e: Exception) { null }

    /** A list whose items are each (almost) as tall as the list itself — i.e. a pager of videos. */
    private fun isPagerOfPages(list: AccessibilityNodeInfo): Boolean = try {
        list.getBoundsInScreen(tmp2)
        val lh = tmp2.height().coerceAtLeast(1)
        val top = tmp2.top; val bottom = tmp2.bottom
        val n = minOf(list.childCount, 6)
        var pages = 0
        var rows = 0
        val r = Rect()
        for (i in 0 until n) {
            val c = list.getChild(i) ?: continue
            c.getBoundsInScreen(r)
            if (r.isEmpty) continue
            val edge = r.top <= top + 3 || r.bottom >= bottom - 3
            if (abs(r.height() - lh) <= lh * 0.04f) pages++
            else if (!edge) rows++
            else if (r.height() < lh * 0.3f && n > 2) rows++
        }
        n in 1..3 && rows == 0 && (pages >= 1 || n == 2)
    } catch (e: Exception) { false }

    /** True if this "page" is (or is mostly filled by) a scrolling list rather than a video page. */
    private fun pageLooksLikeList(page: AccessibilityNodeInfo): Boolean {
        fun isList(n: AccessibilityNodeInfo): Boolean {
            val cls = n.className?.toString() ?: ""
            val rows = n.collectionInfo?.rowCount ?: 0
            return rows > 2 || (n.isScrollable && (cls.endsWith("RecyclerView") || cls.endsWith("ListView") || cls.endsWith("ScrollView")))
        }
        return try {
            if (isList(page)) return true
            page.getBoundsInScreen(tmp)
            val ph = tmp.height().coerceAtLeast(1)
            val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            for (i in 0 until minOf(page.childCount, 10)) page.getChild(i)?.let { queue.add(it to 1) }
            var visited = 0
            while (queue.isNotEmpty() && visited < 40) {
                val (n, depth) = queue.removeFirst()
                visited++
                n.getBoundsInScreen(tmp)
                if (tmp.height() >= ph * 0.55f && isList(n)) return true
                if (depth < 3) for (i in 0 until minOf(n.childCount, 10)) n.getChild(i)?.let { queue.add(it to depth + 1) }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun samePager(a: AccessibilityNodeInfo, b: AccessibilityNodeInfo): Boolean =
        a.viewIdResourceName == b.viewIdResourceName && a.className == b.className && a.packageName == b.packageName

    /** A page mid-swipe is cut off at the container edge; it's not a "short row". */
    private fun partlyOffScreen(r: Rect): Boolean =
        r.top <= rect.top + 4 || r.bottom >= rect.bottom - 4

    private fun scrollsOnlySideways(node: AccessibilityNodeInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val ids = runCatching { node.actionList.map { it.id } }.getOrNull() ?: return false
        val vertical = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id in ids ||
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id in ids
        val horizontal = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id in ids ||
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id in ids
        return horizontal && !vertical
    }

    private fun remember(key: String, value: Boolean): Boolean {
        if (pagerCache.size > 300) pagerCache.clear()
        pagerCache[key] = value
        return value
    }

    /** Is the reels feed still filling the screen? Used to hide the counter when you leave Reels. */
    fun stillInReels(): Boolean {
        val node = lastPager ?: return false
        return try {
            node.refresh() && node.isVisibleToUser && run {
                node.getBoundsInScreen(rect)
                rect.height() >= screenH * 0.6f && rect.top <= screenH * 0.22f
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Looks through the current screen (breadth-first, a limited number of nodes) for a reels
     * feed, so the counter can appear as soon as you open Reels, before the first swipe.
     */
    fun findReelPager(root: AccessibilityNodeInfo?): Boolean {
        root ?: return false
        val pkg = root.packageName?.toString() ?: ""
        if (hasSignature(pkg)) return findSignature(root)

        // Fast path: the reels pager's view id we saw before in this app — found in one lookup.
        knownPagerIds[pkg]?.let { id ->
            val hits = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull().orEmpty()
            for (n in hits) {
                if (n.isVisibleToUser && isReelPager(n) && !lastWasOpaque && surelyReels(n)) { found(pkg, n); return true }
            }
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 160) {
            val n = queue.removeFirst()
            visited++
            if (n.isScrollable && isReelPager(n) && !lastWasOpaque && surelyReels(n)) { found(pkg, n); return true }
            n.getBoundsInScreen(rect)
            if (rect.height() >= screenH * 0.4f) {
                for (i in 0 until minOf(n.childCount, 14)) {
                    runCatching { n.getChild(i) }.getOrNull()?.let { queue.add(it) }
                }
            }
        }
        return false
    }

    private val knownPagerIds = HashMap<String, String>()

    /**
     * Only a container that is surely reels may show the counter, count the opened reel or close
     * the app just by being on screen. Anything else (a profile with a full-screen cover photo,
     * a photo viewer…) must first prove itself with exact reel swipes — see [settle].
     */
    private fun surelyReels(n: AccessibilityNodeInfo): Boolean {
        if (isConfident(n) || isConfident(runCatching { innerPager(n) }.getOrNull())) return true
        lastReason = "not confirmed as reels"
        return false
    }

    private fun found(pkg: String, n: AccessibilityNodeInfo) {
        lastPager = innerPager(n) ?: n
        rememberShape(lastPager)
        n.getBoundsInScreen(rect)
        lastPagerHeight = rect.height().coerceAtLeast(1)
        n.viewIdResourceName?.let { if (pkg.isNotEmpty()) knownPagerIds[pkg] = it }
    }

    /** Remember which reel is on screen when a feed opens, so the very first swipe counts too. */
    /**
     * A reels feed just appeared on screen. Remembers where it is, and returns true when the reel
     * you're looking at should be counted — the first reel you open counts too. Coming back to
     * the very same reel (e.g. after a quick look at another app) doesn't count it twice.
     */
    fun prime(appKey: String): Boolean {
        val pager = lastPager ?: return false
        val st = states.getOrPut(appKey) { AppState() }
        if (hasSignature(pager.packageName?.toString() ?: "")) primeSignature(st)
        st.primedKey = "${pager.viewIdResourceName}|${pager.className}"
        val idx = currentItemIndex()
        st.primedIndex = idx
        if (idx != null) st.lastIdx = idx
        val child = snappedChildKey()
        st.currentChild = child
        if (!isSnapped()) return false
        val key = "${pager.hashCode()}|${child ?: 0}|${idx ?: -1}"
        val now = SystemClock.uptimeMillis()
        val last = lastEntry[appKey]
        if (last != null && last.first == key) return false
        if (last != null && now - last.second < 1500) return false
        lastEntry[appKey] = key to now
        st.lastCountAt = now
        return true
    }

    private val lastEntry = HashMap<String, Pair<String, Long>>()

    private val sheetRect = Rect()

    /**
     * True when a panel slides up over the reel — a scrolling list that starts part-way down the
     * screen and reaches the bottom (comments, share, likes). The reel page itself starts at the top.
     */
    fun sheetOpen(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 180) {
            val n = queue.removeFirst()
            visited++
            n.getBoundsInScreen(sheetRect)
            if (n.isVisibleToUser &&
                sheetRect.top in (screenH * 0.12f).toInt()..(screenH * 0.78f).toInt() &&
                sheetRect.bottom >= screenH * 0.85f &&
                sheetRect.width() >= screenW * 0.9f &&
                sheetRect.height() >= screenH * 0.3f
            ) {
                val cls = n.className?.toString() ?: ""
                val listy = n.isScrollable || (n.collectionInfo?.rowCount ?: 0) > 1 ||
                    cls.endsWith("RecyclerView") || cls.endsWith("ListView") || cls.endsWith("ScrollView")
                if (listy) return true
            }
            for (i in 0 until minOf(n.childCount, 16)) runCatching { n.getChild(i) }.getOrNull()?.let { queue.add(it) }
        }
        return false
    }

    /** After a counted swipe: remember this reel so re-opening the app on it won't count it again. */
    fun noteCurrent(appKey: String) {
        val pager = lastPager ?: return
        val key = "${pager.hashCode()}|${snappedChildKey() ?: 0}|${currentItemIndex() ?: -1}"
        lastEntry[appKey] = key to SystemClock.uptimeMillis()
    }

    fun forgetPager() {
        lastPager = null
    }

    // ------------------------------------------------------------------ signature apps
    //
    // Some apps (YouTube) build their Shorts player in a way the general layout rules can't read
    // reliably. For them we recognise the Shorts player itself by its view id, and count a reel
    // whenever the short that fills the player changes to a new one — read from the player's own
    // item number, or the page view's identity. Only layout is read, never text.

    /** True for apps whose Shorts / Reels player is recognised by its signature. */
    fun hasSignature(pkg: String): Boolean = SIGNATURES.keys.any { pkg == it || pkg.startsWith("$it.") } || pkg in YOUTUBE_PKGS

    private fun sigIdsFor(pkg: String): List<String> = SIGNATURES[pkg] ?: if (pkg in YOUTUBE_PKGS) YOUTUBE_IDS else emptyList()

    private fun isSignaturePager(n: AccessibilityNodeInfo): Boolean {
        val pkg = n.packageName?.toString() ?: return false
        val id = n.viewIdResourceName?.substringAfter(":id/")?.lowercase() ?: return false
        val ids = sigIdsFor(pkg)
        val named = ids.any { id == it } || (id.contains("reel") || id.contains("shorts")) && EXCLUDE_HINTS.none { id.contains(it) }
        if (!named) return false
        if (!n.isVisibleToUser) return false
        n.getBoundsInScreen(rect)
        if (rect.width() < screenW * 0.85f || rect.height() < screenH * 0.55f || rect.top > screenH * 0.25f) return false
        return !scrollsOnlySideways(n)
    }

    /** Finds the Shorts player on screen: by its exact id first, then a short search. */
    private fun findSignaturePager(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        root ?: return null
        val pkg = root.packageName?.toString() ?: return null
        for (id in sigIdsFor(pkg)) {
            val hits = runCatching { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") }.getOrNull().orEmpty()
            hits.firstOrNull { runCatching { isSignaturePager(it) }.getOrDefault(false) }?.let { return it }
        }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        var fallback: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty() && visited < 200) {
            val n = queue.removeFirst()
            visited++
            if (runCatching { isSignaturePager(n) }.getOrDefault(false)) {
                // Prefer the scrolling player itself over a container that merely wraps it.
                if (n.isScrollable) return n
                if (fallback == null && n.childCount > 0) fallback = n
            }
            n.getBoundsInScreen(tmp)
            if (tmp.height() >= screenH * 0.4f) for (i in 0 until minOf(n.childCount, 14)) runCatching { n.getChild(i) }.getOrNull()?.let { queue.add(it) }
        }
        return fallback
    }

    /** A scroll event in a signature app. Returns true when it came from the Shorts player. */
    fun signatureScroll(appKey: String, event: AccessibilityEvent): Boolean {
        val src = event.source ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            event.scrollDeltaX != 0 && abs(event.scrollDeltaX) > abs(event.scrollDeltaY)) return false
        if (!runCatching { isSignaturePager(src) }.getOrDefault(false)) return false
        val st = states.getOrPut(appKey) { AppState() }
        lastPager = src
        lastPagerHeight = rect.height().coerceAtLeast(1)
        if (event.fromIndex >= 0 && event.fromIndex == event.toIndex) { st.eventIndex = event.fromIndex; st.hadIndex = true }
        var dy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) event.scrollDeltaY else 0
        val y = event.scrollY
        if (dy == 0 && y >= 0 && st.lastScrollY >= 0) dy = y - st.lastScrollY
        if (y >= 0) st.lastScrollY = y
        if (dy != 0 && abs(dy) < screenH * 3) { st.accum += dy; st.hadDelta = true }
        st.pending = true
        DetectLog.add(src.packageName?.toString() ?: appKey,
            "shorts player #${src.viewIdResourceName?.substringAfter(":id/")} dy$dy i${event.fromIndex}-${event.toIndex} kids${src.childCount}")
        return true
    }

    /**
     * Signature apps: is a new short on screen? Called after a swipe settles, and now and then
     * while the player is open (some apps send no scroll events). Returns new reels (0/1), or
     * [RETRY] while the player is still moving between two shorts.
     */
    fun signatureSettle(appKey: String, root: AccessibilityNodeInfo?): Int {
        val st = states.getOrPut(appKey) { AppState() }
        val pager = lastPager?.takeIf { runCatching { it.refresh() && isSignaturePager(it) }.getOrDefault(false) }
            ?: findSignaturePager(root)
        if (pager == null) { st.pending = false; return 0 }
        lastPager = pager
        pager.getBoundsInScreen(rect)
        lastPagerHeight = rect.height().coerceAtLeast(1)
        val pagerId = pager.hashCode()
        if (st.sigPager != pagerId) {
            // A different Shorts player (opened from a channel, search…): new baseline.
            st.sigPager = pagerId; st.sigKey = null; st.sigMax = Int.MIN_VALUE
        }
        if (!isSnapped()) {
            if (st.retries < MAX_RETRIES) { st.retries++; return RETRY }
            st.retries = 0; return 0
        }
        st.retries = 0
        val idx = currentItemIndex() ?: st.eventIndex.takeIf { st.hadIndex && it != Int.MIN_VALUE }
        val childKey = snappedChildKey()
        val key = idx?.let { "i$it" } ?: childKey?.let { "v$it" }
        val hadDelta = st.hadDelta
        val pages = st.accum / lastPagerHeight.toFloat()
        val wasPending = st.pending
        st.pending = false; st.hadDelta = false; st.hadIndex = false; st.accum = 0f

        if (key == null) {
            // The player shows no pages at all: fall back to how far the swipe went.
            if (wasPending && hadDelta && pages >= 0.35f) { DetectLog.add(appKey, "shorts: swipe ${"%.2f".format(pages)} screens → +1"); return mark(st, 1) }
            return 0
        }
        val prevKey = st.sigKey
        if (prevKey == null) { st.sigKey = key; if (idx != null) st.sigMax = idx; return 0 }
        if (key == prevKey) return 0
        st.sigKey = key
        if (idx != null) {
            if (st.sigMax != Int.MIN_VALUE && idx <= st.sigMax) { DetectLog.add(appKey, "shorts: back to #$idx → not counted"); return 0 }
            st.sigMax = idx
        } else if (hadDelta && pages < 0f) {
            DetectLog.add(appKey, "shorts: swiped back → not counted"); return 0
        }
        DetectLog.add(appKey, "shorts: new short ${key} → +1")
        return mark(st, 1)
    }

    /** Signature apps: the Shorts player is on screen (for the counter and the first reel). */
    fun findSignature(root: AccessibilityNodeInfo?): Boolean {
        val n = findSignaturePager(root) ?: return false
        lastPager = n
        rememberShape(n)
        n.getBoundsInScreen(rect)
        lastPagerHeight = rect.height().coerceAtLeast(1)
        return true
    }

    /** Signature apps: remember the short on screen as the starting point. */
    private fun primeSignature(st: AppState) {
        val pager = lastPager ?: return
        st.sigPager = pager.hashCode()
        val idx = currentItemIndex()
        st.sigKey = idx?.let { "i$it" } ?: snappedChildKey()?.let { "v$it" }
        st.sigMax = idx ?: Int.MIN_VALUE
    }

    companion object {
        /** How long the feed must be still before a swipe is judged. */
        const val SETTLE_MS = 350L
        /** [settle] result meaning "still moving — call again in a moment". */
        const val RETRY = -1
        private val ALL_REELS_APPS = setOf(
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go", "com.tiktok.lite.go",
            "com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite", "video.like", "video.like.lite",
            "com.kwai.video", "com.kwai.bulldog", "in.mohalla.video", "com.eterno.shortvideos", "io.chingari.app",
            "com.next.innovation.takatak", "com.smile.gifmaker", "com.kuaishou.nebula", "co.triller.droid",
        )
        private val NATIVE_LISTS = listOf("RecyclerView", "ListView", "ScrollView", "ViewPager", "ViewPager2", "GridView")
        private const val MAX_RETRIES = 3
        private const val MIN_COUNT_GAP_MS = 200L
        /** YouTube (and its common variants). */
        private val YOUTUBE_PKGS = setOf(
            "com.google.android.youtube", "com.google.android.apps.youtube.mango",
            "app.revanced.android.youtube", "app.rvx.android.youtube", "com.vanced.android.youtube",
        )
        /** View ids of YouTube's Shorts player (newest first). */
        private val YOUTUBE_IDS = listOf("reel_recycler", "reel_watch_pager", "shorts_recycler", "reel_player_recycler")
        /** Other apps whose reels player is recognised by id. */
        private val SIGNATURES = mapOf<String, List<String>>()
        private val ID_HINTS = listOf("clips", "reel", "shorts", "spotlight", "short_video", "video_pager", "viewpager_vertical", "feed_pager")
        private val EXCLUDE_HINTS = listOf(
            "comment", "reply", "replies", "chat", "message", "thread_list", "inbox", "sheet",
            "drawer", "menu", "search", "grid", "gallery", "carousel", "story_tray",
            "emoji", "sticker", "keyboard", "share", "dialog", "popup", "notification",
        )
    }
}
