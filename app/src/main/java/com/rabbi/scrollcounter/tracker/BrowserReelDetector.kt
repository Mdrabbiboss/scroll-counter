package com.rabbi.scrollcounter.tracker

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Counts short videos watched on websites — TikTok, YouTube Shorts, Instagram / Facebook Reels,
 * Snapchat Spotlight, Douyin, Likee, Kwai… — in Chrome or any other browser.
 *
 * Two signals, merged so one video is never counted twice:
 *
 *  1. The page address. Each short video has its own address (…/shorts/ID, …/reel/ID,
 *     …/video/ID). When the ID changes, you moved to a new video → +1. The address is only
 *     compared in memory to spot that change — it is never stored or sent anywhere.
 *  2. How the feed moves. On a short-video page every swipe moves exactly one screen-tall video.
 *     Sites whose address doesn't change per video (e.g. TikTok's For You feed) are counted this
 *     way. On ordinary websites a feed only counts after two perfectly snapping swipes in a row,
 *     so normal reading and scrolling are never counted.
 *
 * Only reels are counted: YouTube videos, Facebook / Instagram feeds, profiles, search pages etc.
 * are recognised as not-reels and ignored.
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

        // address-based tracking
        var urlKind = UrlKind.UNKNOWN
        var reelId: String? = null
        var lastUrl: String? = null
        var titleKey: String? = null
        var pendingTitle: String? = null
        var lastUrlAt = 0L
        // reels already counted by one signal, waiting to be matched by the other
        var urlCredit = 0
        var urlCreditAt = 0L
        var scrollCredit = 0
        var scrollCreditAt = 0L
    }

    private enum class UrlKind { UNKNOWN, NOT_REELS, REELS }

    private val states = HashMap<String, State>()
    private val rect = Rect()
    private val urlIdCache = HashMap<String, String>()

    fun reset(pkg: String) {
        // Keep the address state (same tab is still open); clear only the swipe in progress.
        states[pkg]?.let { it.gesture = 0f; it.lastY.clear() }
    }

    fun inReels(pkg: String): Boolean {
        val st = states[pkg] ?: return false
        if (st.urlKind == UrlKind.REELS) return true
        if (st.urlKind == UrlKind.NOT_REELS) return false
        if (st.inReels && SystemClock.uptimeMillis() - st.lastSnapAt > 180_000) st.inReels = false
        return st.inReels
    }

    // ------------------------------------------------------------------ address signal

    /**
     * Reads the browser's address bar (by its view id) and returns how many NEW reels the address
     * change represents (0 or 1). Call it, throttled, on browser events.
     */
    fun checkAddress(pkg: String, root: AccessibilityNodeInfo?): Int {
        root ?: return 0
        val st = states.getOrPut(pkg) { State() }
        val url = readUrl(pkg, root)
        if (url == null) {
            DetectLog.add(pkg, "browser: address bar not found")
        } else if (url != st.lastUrl) {
            st.lastUrl = url
            st.lastUrlAt = SystemClock.uptimeMillis()
            val (kind, id) = classify(url)
            st.urlKind = kind
            DetectLog.add(pkg, "browser: ${url.substringBefore('/').substringBefore('?')} → $kind" + if (id != null) " (video id)" else "")
            if (kind != UrlKind.REELS) {
                st.reelId = null
                st.titleKey = null
                if (kind == UrlKind.NOT_REELS) { st.inReels = false; st.streak = 0 }
                return 0
            }
            if (id != null) return newVideo(st, "u:$id")
        }

        // Short-video page whose address doesn't change per video: the page title does.
        if (st.urlKind == UrlKind.REELS && (st.reelId == null || st.reelId!!.startsWith("t:"))) {
            val title = pageTitle(root) ?: return 0
            if (title != st.pendingTitle) { st.pendingTitle = title; return 0 } // wait until it's stable
            if (title == st.titleKey) return 0
            st.titleKey = title
            return newVideo(st, "t:$title")
        }
        return 0
    }

    /** A new video id/title on a short-video page: +1, unless the swipe signal just counted it. */
    private fun newVideo(st: State, id: String): Int {
        if (id == st.reelId) return 0
        st.reelId = id
        val now = SystemClock.uptimeMillis()
        if (st.scrollCredit > 0 && now - st.scrollCreditAt < CREDIT_MS) {
            st.scrollCredit--
            return 0
        }
        st.urlCredit++
        st.urlCreditAt = now
        st.lastCountAt = now
        return 1
    }

    /** The web page's title (Chrome & co. put it on the web-content node). Only its hash is kept. */
    private fun pageTitle(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 60) {
            val n = queue.removeFirst()
            visited++
            val cls = n.className?.toString() ?: ""
            if (cls.contains("WebView")) {
                val t = (n.text ?: n.contentDescription)?.toString()?.trim()
                return t?.takeIf { it.length > 3 }?.hashCode()?.toString()
            }
            for (i in 0 until minOf(n.childCount, 10)) runCatching { n.getChild(i) }.getOrNull()?.let { queue.add(it) }
        }
        return null
    }

    private fun readUrl(pkg: String, root: AccessibilityNodeInfo): String? {
        val ids = urlIdCache[pkg]?.let { listOf(it) } ?: URL_BAR_IDS.map { "$pkg:id/$it" }
        for (id in ids) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull() ?: continue
            val text = nodes.firstNotNullOfOrNull { n -> n.text?.toString()?.takeIf { it.isNotBlank() } } ?: continue
            urlIdCache[pkg] = id
            return normalize(text)
        }
        return null
    }

    private fun normalize(raw: String): String {
        var u = raw.trim().lowercase()
        u = u.removePrefix("https://").removePrefix("http://")
        u = u.removePrefix("www.").removePrefix("m.").removePrefix("mobile.").removePrefix("web.")
        return u.substringBefore('#')
    }

    private fun classify(url: String): Pair<UrlKind, String?> {
        val host = url.substringBefore('/').substringBefore('?')
        val path = "/" + url.substringAfter('/', "").substringBefore('?')
        // Many browsers show only the domain. Then we can't tell Shorts from normal pages, so
        // let the swipe/title signals decide (TikTok is short videos all the way through).
        if (!url.contains('/') || path == "/") {
            return if (host.endsWith("tiktok.com") || host.endsWith("douyin.com")) UrlKind.REELS to null
            else UrlKind.UNKNOWN to null
        }
        fun idAfter(marker: String): String? =
            path.substringAfter(marker, "").substringBefore('/').takeIf { it.length >= 4 }

        return when {
            host.endsWith("youtube.com") ->
                if (path.startsWith("/shorts")) UrlKind.REELS to idAfter("/shorts/") else UrlKind.NOT_REELS to null
            host.endsWith("tiktok.com") -> when {
                path.contains("/video/") || path.contains("/photo/") -> UrlKind.REELS to (idAfter("/video/") ?: idAfter("/photo/"))
                path.startsWith("/@") || path.startsWith("/search") || path.startsWith("/messages") ||
                    path.startsWith("/upload") || path.startsWith("/login") -> UrlKind.NOT_REELS to null
                else -> UrlKind.REELS to null // For You / Following / Explore feeds
            }
            host.endsWith("instagram.com") -> when {
                path.startsWith("/reel/") -> UrlKind.REELS to idAfter("/reel/")
                path.startsWith("/reels") -> UrlKind.REELS to idAfter("/reels/")
                else -> UrlKind.NOT_REELS to null
            }
            host.endsWith("facebook.com") || host.endsWith("fb.com") -> when {
                path.startsWith("/reel/") -> UrlKind.REELS to idAfter("/reel/")
                path.startsWith("/reels") -> UrlKind.REELS to idAfter("/reels/")
                path.contains("/reel/") -> UrlKind.REELS to idAfter("/reel/")
                else -> UrlKind.NOT_REELS to null
            }
            host.endsWith("snapchat.com") ->
                if (path.startsWith("/spotlight")) UrlKind.REELS to idAfter("/spotlight/") else UrlKind.NOT_REELS to null
            host.endsWith("douyin.com") || host.endsWith("kwai.com") || host.endsWith("likee.video") ||
                host.endsWith("snackvideo.com") || host.endsWith("triller.co") || host.endsWith("moj.in") ||
                host.endsWith("sharechat.com") || host.endsWith("kuaishou.com") ->
                if (path.contains("/video/")) UrlKind.REELS to idAfter("/video/") else UrlKind.REELS to null
            host.endsWith("pinterest.com") || host.endsWith("reddit.com") || host.endsWith("x.com") ||
                host.endsWith("twitter.com") || host.endsWith("google.com") || host.endsWith("wikipedia.org") ->
                UrlKind.NOT_REELS to null
            else -> UrlKind.UNKNOWN to null
        }
    }

    // ------------------------------------------------------------------ movement signal

    /** Feed one scroll event. Call [settle] once the page has been still for ~400 ms. */
    fun onScroll(pkg: String, event: AccessibilityEvent) {
        val st = states.getOrPut(pkg) { State() }
        if (st.urlKind == UrlKind.NOT_REELS) return
        val src = event.source
        var vp = 0
        if (src != null) {
            src.getBoundsInScreen(rect)
            vp = rect.height()
        }
        // Small scrollers (carousels, comment boxes, menus) are never the video feed.
        if (vp < screenH * 0.55f) return
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

    /** The swipe has finished. Returns how many new reels it represents (0 if none). */
    fun settle(pkg: String): Int {
        val st = states[pkg] ?: return 0
        val vp = st.viewport
        val moved = st.gesture
        st.gesture = 0f
        if (vp <= 0 || abs(moved) < vp * 0.12f) return 0 // a nudge, not a swipe
        val now = SystemClock.uptimeMillis()

        if (st.urlKind == UrlKind.NOT_REELS) return 0

        if (st.urlKind == UrlKind.REELS) {
            // Known short-video page: every forward swipe of ~a screen is a new video.
            if (moved < vp * 0.5f) return 0
            val n = (moved / vp).roundToInt().coerceIn(1, 3)
            return merge(st, n, now)
        }

        // Unknown site: only a run of perfectly snapping swipes proves it's a reels feed.
        val pages = abs(moved) / vp
        val whole = pages.roundToInt()
        val snapped = whole in 1..3 && abs(pages - whole) <= 0.12f * whole
        // We must know where the page stopped: a reels feed always lands exactly on a video.
        val aligned = st.landedY >= 0 && run {
            val off = st.landedY % vp
            minOf(off, vp - off) <= vp * 0.06f
        }
        if (!(snapped && aligned)) {
            st.streak = 0
            st.inReels = false
            return 0
        }
        if (now - st.lastSnapAt > 90_000) st.streak = 0
        st.lastSnapAt = now
        st.streak++
        if (moved < 0) return 0 // going back to a video already seen
        if (st.inReels) return merge(st, whole, now)
        if (st.streak >= 2) {
            st.inReels = true
            return merge(st, whole + 1, now)
        }
        return 0
    }

    /** Subtract reels the address signal already counted for this same swipe. */
    private fun merge(st: State, n: Int, now: Long): Int {
        var count = n
        if (st.urlCredit > 0 && now - st.urlCreditAt < CREDIT_MS) {
            val used = minOf(st.urlCredit, count)
            st.urlCredit -= used
            count -= used
        } else st.urlCredit = 0
        if (count <= 0) return 0
        if (now - st.lastCountAt < 200) return 0
        st.lastCountAt = now
        // The address may update a moment later for this same video.
        if (st.urlKind != UrlKind.UNKNOWN || st.reelId != null) {
            st.scrollCredit = count
            st.scrollCreditAt = now
        }
        return count
    }

    companion object {
        private const val CREDIT_MS = 2500L
        /** Address-bar view ids used by Chrome and the popular browsers built on it, Samsung, Firefox, Opera… */
        private val URL_BAR_IDS = listOf(
            "url_bar", "location_bar_edit_text", "url_field", "mozac_browser_toolbar_url_view",
            "omnibarTextInput", "url", "search_box_text", "address_bar_edit_text", "sbrowser_url_bar",
            "url_edittext", "addressbar_url", "title_bar_url", "tv_url",
        )
    }
}
