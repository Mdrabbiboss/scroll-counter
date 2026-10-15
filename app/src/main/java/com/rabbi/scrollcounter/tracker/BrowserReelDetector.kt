package com.rabbi.scrollcounter.tracker

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Counts short videos watched on websites — TikTok, YouTube Shorts, Facebook / Instagram Reels…
 * — in Chrome or any other browser.
 *
 * Web pages don't expose a "pager", and we deliberately never read the address bar or page text.
 * Instead we use how a reels feed *moves*: every swipe snaps by exactly one screen-tall video and
 * lands perfectly aligned. Ordinary reading scrolls by random amounts. So:
 *
 *  1. Scroll events are added up per swipe (until the page is still for a moment).
 *  2. A swipe that moved ~1 (or 2–3) full viewport heights AND landed on a page boundary is a snap.
 *  3. Two snaps in a row → we're in a reels feed; from then on every snap = +1.
 *     (The two snaps that proved it are counted too.)
 *  4. Any ordinary, non-snapping scroll leaves reels mode.
 */
class BrowserReelDetector(private val screenH: Int) {

    private class State {
        val lastY = HashMap<Int, Int>()
        var gesture = 0f
        var viewport = 0
        var landedY = -1
        var streak = 0
        var inReels = false
        var lastSnapAt = 0L
        var lastCountAt = 0L
    }

    private val states = HashMap<String, State>()
    private val rect = Rect()

    fun reset(pkg: String) { states.remove(pkg) }

    fun inReels(pkg: String): Boolean {
        val st = states[pkg] ?: return false
        // A reels session with no swipe for 3 minutes has probably ended.
        if (st.inReels && SystemClock.uptimeMillis() - st.lastSnapAt > 180_000) st.inReels = false
        return st.inReels
    }

    /** Feed one scroll event. Call [settle] once the page has been still for ~400 ms. */
    fun onScroll(pkg: String, event: AccessibilityEvent) {
        val st = states.getOrPut(pkg) { State() }
        val src = event.source
        var vp = 0
        if (src != null) {
            src.getBoundsInScreen(rect)
            vp = rect.height()
        }
        // Tiny scrollers (carousels, comment boxes) are not a video feed.
        if (vp < screenH * 0.45f) return
        // Sideways swipes never count.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            event.scrollDeltaX != 0 && abs(event.scrollDeltaX) > abs(event.scrollDeltaY)) return

        st.viewport = vp
        val key = src?.let { (it.viewIdResourceName ?: "").hashCode() * 31 + rect.top * 7 + rect.height() } ?: 0
        val y = event.scrollY
        var dy = 0
        if (y >= 0) {
            val prev = st.lastY[key]
            if (prev != null) dy = y - prev
            st.lastY[key] = y
            st.landedY = y
        }
        if (dy == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) dy = event.scrollDeltaY
        if (abs(dy) < screenH * 4) st.gesture += dy
    }

    /**
     * The swipe has finished. Returns how many new reels it represents (0 if none).
     */
    fun settle(pkg: String): Int {
        val st = states[pkg] ?: return 0
        val vp = st.viewport
        val moved = abs(st.gesture)
        st.gesture = 0f
        if (vp <= 0 || moved < vp * 0.12f) return 0 // a nudge, not a swipe

        val pages = moved / vp
        val whole = pages.roundToInt()
        val snapped = whole in 1..3 && abs(pages - whole) <= 0.12f * whole
        // Landing exactly on a video boundary (when the browser tells us the position).
        val aligned = st.landedY < 0 || run {
            val off = st.landedY % vp
            minOf(off, vp - off) <= vp * 0.06f
        }

        val now = SystemClock.uptimeMillis()
        if (!(snapped && aligned)) {
            st.streak = 0
            st.inReels = false
            return 0
        }
        // Snaps far apart in time don't prove a feed.
        if (now - st.lastSnapAt > 90_000) st.streak = 0
        st.lastSnapAt = now
        st.streak++

        if (st.inReels) return countable(st, whole, now)
        if (st.streak >= 2) {
            st.inReels = true
            // Count the snap that started the run as well as this one.
            return countable(st, whole + 1, now)
        }
        return 0
    }

    private fun countable(st: State, n: Int, now: Long): Int {
        if (now - st.lastCountAt < 250) return 0
        st.lastCountAt = now
        return n
    }
}
