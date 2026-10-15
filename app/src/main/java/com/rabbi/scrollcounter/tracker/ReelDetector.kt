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

    /**
     * Records one scroll event. Returns true if it came from a reels feed (the caller should then
     * schedule [settle]). Never counts by itself.
     */
    fun onScroll(appKey: String, event: AccessibilityEvent): Boolean {
        val src = event.source ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            event.scrollDeltaX != 0 && abs(event.scrollDeltaX) > abs(event.scrollDeltaY)) return false
        val accepted = isReelPager(src)
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
        lastPager = src
        lastPagerHeight = rect.height().coerceAtLeast(1)
        src.viewIdResourceName?.let { id -> src.packageName?.toString()?.let { knownPagerIds[it] = id } }

        val st = states.getOrPut(appKey) { AppState() }
        val key = "${src.viewIdResourceName}|${src.className}"
        st.opaque = opaque
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
        }

        if (from >= 0 && to >= 0) {
            st.hadIndex = true
            if (from == to) st.eventIndex = from
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
    fun settle(appKey: String): Int {
        val st = states[appKey] ?: return 0
        if (!st.pending) return 0
        st.pending = false
        val hadIndex = st.hadIndex
        val hadDelta = st.hadDelta
        val accum = st.accum
        st.hadIndex = false
        st.hadDelta = false
        st.accum = 0f

        val h = lastPagerHeight.toFloat().coerceAtLeast(1f)

        // Apps that draw everything themselves (Facebook Lite…) expose a single full-screen view
        // with no children. There, only a swipe that moved exactly ~one screen is a reel.
        if (st.opaque) {
            if (!hadDelta) return 0
            val pages = accum / h
            val whole = pages.roundToInt()
            return if (whole in 1..3 && abs(pages - whole) <= 0.18f * whole) mark(st, whole) else 0
        }

        // 1. Best: ask the pager which item fills the screen right now.
        var index = currentItemIndex()
        // 2. Next best: the last settled index the scroll events reported.
        if (index == null && hadIndex && st.eventIndex != Int.MIN_VALUE) index = st.eventIndex

        var n = 0
        if (index != null) {
            if (st.lastIdx != Int.MIN_VALUE && index != st.lastIdx) st.indexReliable = true
            st.lastIdx = index
            if (st.maxIndex == Int.MIN_VALUE) st.maxIndex = index
            else if (index > st.maxIndex) {
                n = (index - st.maxIndex).coerceAtMost(3)
                st.maxIndex = index
            }
            // A trusted item number that didn't move forward = bounced back or rewatching.
            if (n == 0 && st.indexReliable) return 0
        }
        if (n > 0) return mark(st, n)

        // 3. No trustworthy item numbers: whole pages moved forward.
        if (hadDelta) {
            val pages = accum / h
            if (pages < 0.5f) return 0                 // half swipe that snapped back, or going up
            return mark(st, pages.roundToInt().coerceIn(1, 3))
        }

        // 4. Nothing reported at all: one settled swipe on a still-full-screen pager = one reel.
        return if (stillInReels()) mark(st) else 0
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

        // 3. Its visible items must be full-screen pages stacked vertically.
        var fullPages = 0
        var shortItems = 0
        var sideBySide = 0
        var prevTop = Int.MIN_VALUE
        var prevLeft = Int.MIN_VALUE
        for (i in 0 until n) {
            val c = runCatching { src.getChild(i) }.getOrNull() ?: continue
            c.getBoundsInScreen(child)
            if (child.isEmpty) continue
            if (child.height() >= rect.height() * 0.8f && child.width() >= rect.width() * 0.9f) fullPages++
            else if (child.height() < rect.height() * 0.6f && !partlyOffScreen(child)) shortItems++
            if (prevTop != Int.MIN_VALUE && abs(child.top - prevTop) < 8 && abs(child.left - prevLeft) > rect.width() / 4) sideBySide++
            prevTop = child.top; prevLeft = child.left
        }
        val ok = sideBySide == 0 && fullPages >= 1 && shortItems <= (if (named) 2 else 1)
        // Only remember a "yes": a "no" may just be a moment mid-transition, so look again next time.
        if (!ok) lastReason = "items full$fullPages short$shortItems row$sideBySide"
        return if (ok) remember(cacheKey, true) else false
    }

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

        // Fast path: the reels pager's view id we saw before in this app — found in one lookup.
        knownPagerIds[pkg]?.let { id ->
            val hits = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull().orEmpty()
            for (n in hits) {
                if (n.isVisibleToUser && isReelPager(n)) { found(pkg, n); return true }
            }
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 160) {
            val n = queue.removeFirst()
            visited++
            if (n.isScrollable && isReelPager(n)) { found(pkg, n); return true }
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

    private fun found(pkg: String, n: AccessibilityNodeInfo) {
        lastPager = n
        n.getBoundsInScreen(rect)
        lastPagerHeight = rect.height().coerceAtLeast(1)
        n.viewIdResourceName?.let { if (pkg.isNotEmpty()) knownPagerIds[pkg] = it }
    }

    /** Remember which reel is on screen when a feed opens, so the very first swipe counts too. */
    fun prime(appKey: String) {
        val pager = lastPager ?: return
        val st = states.getOrPut(appKey) { AppState() }
        st.primedKey = "${pager.viewIdResourceName}|${pager.className}"
        st.primedIndex = currentItemIndex()
    }

    fun forgetPager() {
        lastPager = null
    }

    companion object {
        /** How long the feed must be still before a swipe is judged. */
        const val SETTLE_MS = 350L
        private const val MIN_COUNT_GAP_MS = 200L
        private val ID_HINTS = listOf("clips", "reel", "shorts", "spotlight", "short_video", "video_pager", "viewpager_vertical", "feed_pager")
        private val EXCLUDE_HINTS = listOf(
            "comment", "reply", "replies", "chat", "message", "thread_list", "inbox", "sheet",
            "drawer", "menu", "search", "grid", "gallery", "carousel", "story_tray",
            "emoji", "sticker", "keyboard", "share", "dialog", "popup", "notification",
        )
    }
}
