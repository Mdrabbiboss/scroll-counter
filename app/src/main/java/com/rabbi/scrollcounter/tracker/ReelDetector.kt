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
        // A reels pager never shows more than two items at once (the one leaving and the one
        // arriving). A list reporting 3+ visible items is a feed, inbox, comments… — not reels.
        val span = if (event.fromIndex >= 0 && event.toIndex >= 0) event.toIndex - event.fromIndex else 0
        val accepted = span < 2 && isReelPager(src)
        if (span >= 2) lastReason = "list shows ${span + 1} items"
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
        // A reels feed always comes to rest with one video filling it exactly. If it isn't
        // snapped yet, it's still animating (try again shortly) — or it's not a reels feed at all.
        if (!st.opaque && !st.allReels && !isSnapped()) {
            if (st.retries < MAX_RETRIES) { st.retries++; return RETRY }
            st.retries = 0
            st.pending = false
            st.hadIndex = false; st.hadDelta = false; st.accum = 0f
            return 0
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

        // Apps that draw everything themselves (Facebook Lite…) expose a single full-screen view
        // with no children. There, only a swipe that moved exactly ~one screen is a reel.
        if (st.opaque) {
            // Self-drawn screen: only a run of exact one-screen swipes is a reels feed. A normal
            // feed fling never lands on exactly one screen twice in a row.
            if (!hadDelta) return 0
            val pages = accum / h
            val whole = pages.roundToInt()
            val exact = accum > 0 && whole in 1..2 && abs(pages - whole) <= 0.08f * whole
            if (!exact) { st.opaqueStreak = 0; DetectLog.add(appKey, "opaque swipe ${"%.2f".format(pages)} pages → no"); return 0 }
            st.opaqueStreak++
            DetectLog.add(appKey, "opaque snap #${st.opaqueStreak}")
            return when {
                st.opaqueStreak >= 3 -> mark(st, whole)
                st.opaqueStreak == 2 -> mark(st, whole + 1)
                else -> 0
            }
        }

        // ---- Evidence that the feed really moved to another video ----
        var index = currentItemIndex()
        if (index == null && hadIndex && st.eventIndex != Int.MIN_VALUE) index = st.eventIndex
        val prevIdx = st.lastIdx
        val idxMoved = index != null && prevIdx != Int.MIN_VALUE && index != prevIdx
        if (idxMoved) st.indexReliable = true
        if (index != null) st.lastIdx = index
        val pages = accum / h
        // Some apps (TikTok) report neither a distance nor a usable item number. There the page
        // itself changing (checked below) is the evidence; elsewhere a tiny shift is ignored.
        val tinyShift = hadDelta && abs(pages) < 0.35f && !idxMoved
        if (tinyShift) return 0   // a small shift (comment box, layout change, half swipe) — same reel

        // ---- Which video is on screen now? ----
        // Two independent checks: the page view that fills the feed (a new reel brings a different
        // page onto the screen; opening comments or a bounce keeps the same one), and a one-way
        // code of the page's labels (kept only in memory). Either changing = a new reel.
        val ck = snappedChildKey()
        val fp = pageFingerprint()
        val ckChanged = ck != null && st.currentChild != null && ck != st.currentChild
        val fpChanged = fp != null && st.currentFp != null && fp != st.currentFp
        val known = (ck != null && st.currentChild != null) || (fp != null && st.currentFp != null)
        if (ck != null) st.currentChild = ck
        if (fp != null) st.currentFp = fp
        val wentBack = (hadDelta && pages < -0.35f) || (idxMoved && index != null && prevIdx != Int.MIN_VALUE && index < prevIdx) ||
            (fpChanged && fp in st.seenFps)
        if (fp != null) {
            st.seenFps.add(fp)
            if (st.seenFps.size > 80) st.seenFps.remove(st.seenFps.first())
        }
        if (ckChanged || fpChanged) {
            if (wentBack) return 0
            var n = 1
            if (index != null && st.indexReliable && st.maxIndex != Int.MIN_VALUE && index - st.maxIndex > 1) n = index - st.maxIndex
            else if (hadDelta && pages >= 1.5f) n = pages.roundToInt()
            if (index != null && index > st.maxIndex) st.maxIndex = index
            return mark(st, n.coerceIn(1, 3))
        }
        if (known) return 0   // same page, same labels → still the same reel

        // ---- Then item numbers, then distance. Never count without evidence. ----
        if (index != null) {
            if (st.maxIndex == Int.MIN_VALUE) { st.maxIndex = index; return 0 }
            if (index > st.maxIndex) {
                val n = (index - st.maxIndex).coerceAtMost(3)
                st.maxIndex = index
                return mark(st, n)
            }
            if (st.indexReliable) return 0
        }
        if (hadDelta && pages >= 0.5f) return mark(st, pages.roundToInt().coerceIn(1, 3))
        // TikTok-style apps whose pager reports nothing at all: one settled swipe = one video.
        if (st.allReels && !hadDelta && !hadIndex) return mark(st)
        return 0
    }

    /**
     * Hash of the labels on the page that fills the pager (view ids + descriptions, skipping
     * anything with digits such as times, likes or view counts, which change while you watch).
     */
    private fun pageFingerprint(): String? {
        val pager = lastPager ?: return null
        return try {
            pager.getBoundsInScreen(rect)
            val h = rect.height()
            var page: AccessibilityNodeInfo? = null
            for (i in 0 until minOf(pager.childCount, 6)) {
                val c = pager.getChild(i) ?: continue
                c.getBoundsInScreen(child)
                if (abs(child.top - rect.top) <= h * 0.03f && child.height() >= h * 0.96f) { page = c; break }
            }
            page ?: return null
            val sb = StringBuilder()
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(page)
            var visited = 0
            var labels = 0
            while (queue.isNotEmpty() && visited < 60) {
                val n = queue.removeFirst()
                visited++
                for (t in arrayOf(n.contentDescription, n.text)) {
                    val v = t?.toString()?.trim() ?: continue
                    if (v.length < 2 || v.any { it.isDigit() }) continue
                    sb.append(v.take(80)).append('|')
                    labels++
                }
                for (i in 0 until minOf(n.childCount, 12)) n.getChild(i)?.let { queue.add(it) }
            }
            if (labels == 0) null else sb.toString().hashCode().toString()
        } catch (e: Exception) {
            null
        }
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
        lastPager = innerPager(n) ?: n
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
        // Remember the reel you opened on, so the first swipe away from it counts once.
        if (isSnapped()) pageFingerprint()?.let { st.currentFp = it; st.seenFps.add(it) }
        st.currentChild = snappedChildKey()
    }

    fun forgetPager() {
        lastPager = null
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
        private val ID_HINTS = listOf("clips", "reel", "shorts", "spotlight", "short_video", "video_pager", "viewpager_vertical", "feed_pager")
        private val EXCLUDE_HINTS = listOf(
            "comment", "reply", "replies", "chat", "message", "thread_list", "inbox", "sheet",
            "drawer", "menu", "search", "grid", "gallery", "carousel", "story_tray",
            "emoji", "sticker", "keyboard", "share", "dialog", "popup", "notification",
        )
    }
}
