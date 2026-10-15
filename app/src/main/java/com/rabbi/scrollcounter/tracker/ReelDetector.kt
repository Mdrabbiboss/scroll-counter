package com.rabbi.scrollcounter.tracker

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * Decides whether a scroll happened inside a short-video feed (Reels, Shorts, TikTok, Spotlight…)
 * and turns raw scroll events into "one reel = +1".
 *
 * How a reels feed is recognised, in any app:
 *  1. The scrolling container covers (almost) the whole screen, and
 *  2. its items are full-screen pages — a child as tall as the container — or the app's own
 *     view id says so (clips / reels / shorts / spotlight / pager).
 * A normal news feed is also full-screen, but its items are much shorter, so it never matches.
 *
 * Only the layout (sizes and view ids) is looked at — never text, captions or messages.
 */
class ReelDetector(private val screenW: Int, private val screenH: Int) {

    /** Per-app tracking so switching apps never double-counts. */
    private class AppState {
        var lastIndex = Int.MIN_VALUE
        var accum = 0f
        var lastEventAt = 0L
        var lastCountAt = 0L
        var gestureCounted = false
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
     * Returns true when this scroll event means the user moved to a new reel.
     */
    fun onScroll(appKey: String, event: AccessibilityEvent): Boolean {
        val src = event.source ?: return false
        if (!isReelPager(src)) return false
        lastPager = src
        lastPagerHeight = rect.height().coerceAtLeast(1)

        val st = states.getOrPut(appKey) { AppState() }
        val now = SystemClock.uptimeMillis()
        val idle = now - st.lastEventAt
        st.lastEventAt = now
        if (idle > GESTURE_GAP_MS) { st.accum = 0f; st.gestureCounted = false }

        // Best signal: the pager reports which item is showing. A settled pager shows exactly one.
        val from = event.fromIndex
        val to = event.toIndex
        if (from >= 0 && to >= 0) {
            if (from == to) {
                if (st.lastIndex == Int.MIN_VALUE) { st.lastIndex = from; return false }
                if (from != st.lastIndex) {
                    st.lastIndex = from
                    return countIfNotTooSoon(st, now)
                }
                return false
            }
            // Mid-swipe (two reels visible): wait until it settles.
            if (st.lastIndex != Int.MIN_VALUE) return false
        }

        // Fallback 1: add up how far the pager moved; half a page or more = a new reel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dy = abs(event.scrollDeltaY)
            if (dy in 1 until screenH * 3) {
                st.accum += dy
                if (st.accum >= lastPagerHeight * 0.5f) {
                    st.accum = 0f
                    return countIfNotTooSoon(st, now)
                }
                return false
            }
        }

        // Fallback 2: no index and no distance reported — one swipe gesture = one reel.
        if (!st.gestureCounted) {
            st.gestureCounted = true
            return countIfNotTooSoon(st, now)
        }
        return false
    }

    private fun countIfNotTooSoon(st: AppState, now: Long): Boolean {
        if (now - st.lastCountAt < MIN_COUNT_GAP_MS) return false
        st.lastCountAt = now
        return true
    }

    private fun isReelPager(src: AccessibilityNodeInfo): Boolean {
        src.getBoundsInScreen(rect)
        // 1. Must cover nearly the whole screen
        if (rect.width() < screenW * 0.85f || rect.height() < screenH * 0.6f) return false

        val id = src.viewIdResourceName ?: ""
        val cacheKey = "$id|${src.className}|${rect.height()}"
        pagerCache[cacheKey]?.let { return it }

        // 2a. The app names it as a reels / shorts pager
        val lower = id.lowercase()
        if (ID_HINTS.any { lower.contains(it) }) return remember(cacheKey, true)

        // 2b. Its items are full-screen pages
        val n = minOf(src.childCount, 6)
        var fullPages = 0
        var shortItems = 0
        for (i in 0 until n) {
            val c = runCatching { src.getChild(i) }.getOrNull() ?: continue
            c.getBoundsInScreen(child)
            if (child.height() >= rect.height() * 0.82f && child.width() >= rect.width() * 0.9f) fullPages++
            else if (child.height() in 1 until (rect.height() * 0.6f).toInt()) shortItems++
        }
        val isPager = fullPages >= 1 && shortItems <= 1
        // Only remember confident answers; empty containers may still be loading.
        return if (n > 0) remember(cacheKey, isPager) else false
    }

    private fun remember(key: String, value: Boolean): Boolean {
        if (pagerCache.size > 200) pagerCache.clear()
        pagerCache[key] = value
        return value
    }

    /** Is the reels feed still on screen? Used to hide the counter when you leave Reels. */
    fun stillInReels(): Boolean {
        val node = lastPager ?: return false
        return try {
            node.refresh() && node.isVisibleToUser && run {
                node.getBoundsInScreen(rect)
                rect.height() >= screenH * 0.6f
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
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 80) {
            val n = queue.removeFirst()
            visited++
            if (n.isScrollable && isReelPager(n)) {
                lastPager = n
                lastPagerHeight = rect.height().coerceAtLeast(1)
                return true
            }
            n.getBoundsInScreen(rect)
            // Only descend into large containers; reels feeds are always big.
            if (rect.height() >= screenH * 0.5f) {
                for (i in 0 until minOf(n.childCount, 12)) {
                    runCatching { n.getChild(i) }.getOrNull()?.let { queue.add(it) }
                }
            }
        }
        return false
    }

    fun forgetPager() {
        lastPager = null
    }

    companion object {
        private const val GESTURE_GAP_MS = 550L
        private const val MIN_COUNT_GAP_MS = 280L
        private val ID_HINTS = listOf("clips", "reel", "shorts", "spotlight", "short_video", "video_pager", "viewpager_vertical", "feed_pager")
    }
}
