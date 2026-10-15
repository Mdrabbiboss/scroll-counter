package com.rabbi.scrollcounter.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/** An app whose Reels / short videos we count. One app can have several package names. */
data class TrackedApp(
    val key: String,
    val name: String,
    /** Brand color used for the app's icon tile and bars. */
    val color: Long,
    /** Text color on top of [color]. */
    val onColor: Long = 0xFFFFFFFF,
    val packages: List<String>,
    val isBrowser: Boolean = false,
)

/**
 * The registry of apps Scroll Counter knows about.
 *
 *  • A built-in catalogue of short-video / social apps from around the world (brand colours,
 *    regional builds grouped under one name).
 *  • ANY other app is resolved on the fly: its key is its package name and its name/colour come
 *    from the phone itself — so a brand-new reels app works the moment it's installed.
 *  • Browsers (Chrome, Samsung Internet, Firefox, Opera, Edge…) are detected from the phone, so
 *    TikTok / YouTube Shorts / Reels opened on the web are counted too.
 *
 * Only apps that are actually installed are shown in the UI.
 */
object TrackedApps {

    val known: List<TrackedApp> = listOf(
        // Global
        TrackedApp("facebook", "Facebook", 0xFF1877F2, packages = listOf("com.facebook.katana", "com.facebook.lite", "com.facebook.mlite")),
        TrackedApp("instagram", "Instagram", 0xFFE1306C, packages = listOf("com.instagram.android", "com.instagram.lite")),
        TrackedApp("tiktok", "TikTok", 0xFF111111, packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go", "com.tiktok.lite.go")),
        TrackedApp("youtube", "YouTube", 0xFFFF0033, packages = listOf("com.google.android.youtube", "com.google.android.apps.youtube.mango", "app.revanced.android.youtube", "app.rvx.android.youtube")),
        TrackedApp("snapchat", "Snapchat", 0xFFFFE400, 0xFF111111, listOf("com.snapchat.android")),
        TrackedApp("x", "X (Twitter)", 0xFF111111, packages = listOf("com.twitter.android", "com.twitter.android.lite")),
        TrackedApp("threads", "Threads", 0xFF111111, packages = listOf("com.instagram.barcelona")),
        TrackedApp("reddit", "Reddit", 0xFFFF4500, packages = listOf("com.reddit.frontpage")),
        TrackedApp("pinterest", "Pinterest", 0xFFE60023, packages = listOf("com.pinterest")),
        TrackedApp("linkedin", "LinkedIn", 0xFF0A66C2, packages = listOf("com.linkedin.android")),
        TrackedApp("tumblr", "Tumblr", 0xFF001935, packages = listOf("com.tumblr")),
        TrackedApp("triller", "Triller", 0xFFE6007E, packages = listOf("co.triller.droid")),
        TrackedApp("lemon8", "Lemon8", 0xFFF5E04A, 0xFF111111, listOf("com.bd.nproject")),
        TrackedApp("capcut", "CapCut", 0xFF111111, packages = listOf("com.lemon.lvoverseas")),
        TrackedApp("vimeo", "Vimeo", 0xFF1AB7EA, packages = listOf("com.vimeo.android.videoapp")),
        TrackedApp("dailymotion", "Dailymotion", 0xFF0D0D0D, packages = listOf("com.dailymotion.dailymotion")),
        TrackedApp("bigo", "BIGO LIVE", 0xFF00C1D4, packages = listOf("sg.bigo.live", "sg.bigo.live.lite")),
        TrackedApp("imo", "imo", 0xFF0A84FF, packages = listOf("com.imo.android.imoim", "com.imo.android.imoimbeta")),
        TrackedApp("telegram", "Telegram", 0xFF2AABEE, packages = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")),
        TrackedApp("whatsapp", "WhatsApp", 0xFF25D366, packages = listOf("com.whatsapp", "com.whatsapp.w4b")),
        TrackedApp("discord", "Discord", 0xFF5865F2, packages = listOf("com.discord")),
        TrackedApp("twitch", "Twitch", 0xFF9146FF, packages = listOf("tv.twitch.android.app")),
        TrackedApp("bluesky", "Bluesky", 0xFF1185FE, packages = listOf("xyz.blueskyweb.app")),
        TrackedApp("9gag", "9GAG", 0xFF111111, packages = listOf("com.ninegag.android.app")),
        // South Asia
        TrackedApp("likee", "Likee", 0xFFFF2D7A, packages = listOf("video.like", "video.like.lite")),
        TrackedApp("kwai", "Kwai", 0xFFFF7E1B, packages = listOf("com.kwai.video")),
        TrackedApp("snack", "Snack Video", 0xFFFFD21E, 0xFF111111, listOf("com.kwai.bulldog")),
        TrackedApp("moj", "Moj", 0xFF7C3AED, packages = listOf("in.mohalla.video")),
        TrackedApp("sharechat", "ShareChat", 0xFF2196F3, packages = listOf("in.mohalla.sharechat")),
        TrackedApp("josh", "Josh", 0xFFFF3D57, packages = listOf("com.eterno.shortvideos")),
        TrackedApp("chingari", "Chingari", 0xFFFF6A00, packages = listOf("io.chingari.app")),
        TrackedApp("takatak", "MX TakaTak", 0xFF1E88E5, packages = listOf("com.next.innovation.takatak")),
        TrackedApp("roposo", "Roposo", 0xFFE53935, packages = listOf("com.roposo.android")),
        // East Asia
        TrackedApp("douyin", "Douyin", 0xFF111111, packages = listOf("com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite")),
        TrackedApp("kuaishou", "Kuaishou", 0xFFFF5000, packages = listOf("com.smile.gifmaker", "com.kuaishou.nebula")),
        TrackedApp("bilibili", "Bilibili", 0xFFFB7299, packages = listOf("tv.danmaku.bili", "com.bilibili.app.in", "tv.danmaku.bilibilihd")),
        TrackedApp("xiaohongshu", "Xiaohongshu", 0xFFFF2442, packages = listOf("com.xingin.xhs")),
        TrackedApp("weibo", "Weibo", 0xFFE6162D, packages = listOf("com.sina.weibo", "com.weico.international")),
        TrackedApp("wechat", "WeChat", 0xFF07C160, packages = listOf("com.tencent.mm")),
        TrackedApp("line", "LINE", 0xFF06C755, packages = listOf("jp.naver.line.android")),
        TrackedApp("kakao", "KakaoTalk", 0xFFFEE500, 0xFF111111, listOf("com.kakao.talk")),
        TrackedApp("naver", "Naver", 0xFF03C75A, packages = listOf("com.nhn.android.search")),
        TrackedApp("zalo", "Zalo", 0xFF0068FF, packages = listOf("com.zing.zalo")),
        // Russia / Middle East / others
        TrackedApp("vk", "VK", 0xFF0077FF, packages = listOf("com.vkontakte.android", "com.vk.clips")),
        TrackedApp("ok", "Odnoklassniki", 0xFFEE8208, packages = listOf("ru.ok.android")),
        TrackedApp("rutube", "Rutube", 0xFF100943, packages = listOf("ru.rutube.app")),
    )

    private val knownByPackage: Map<String, TrackedApp> =
        known.flatMap { app -> app.packages.map { it to app } }.toMap()
    private val knownByKey: Map<String, TrackedApp> = known.associateBy { it.key }

    /** Popular browsers, so they're recognised even before the phone is queried. */
    private val knownBrowsers = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.focus", "org.mozilla.fenix",
        "com.opera.browser", "com.opera.mini.native", "com.opera.gx", "com.opera.browser.beta",
        "com.microsoft.emmx", "com.brave.browser", "com.duckduckgo.mobile.android",
        "com.UCMobile.intl", "com.uc.browser.en", "com.vivaldi.browser", "com.kiwibrowser.browser",
        "com.mi.globalbrowser", "com.android.browser", "com.huawei.browser", "com.heytap.browser",
        "com.yandex.browser", "mark.via.gp", "com.google.android.googlequicksearchbox",
        "com.ecosia.android", "com.cloudmosa.puffinFree", "org.chromium.webview_shell",
    )

    @Volatile private var ctx: Context? = null
    private val dynamic = ConcurrentHashMap<String, TrackedApp>()
    @Volatile private var browserCache: Set<String>? = null

    /** Called once from [ScrollStore]; every entry point (UI, service, widget) creates the store first. */
    fun attach(context: Context) {
        if (ctx == null) ctx = context.applicationContext
    }

    // ---------------------------------------------------------------- lookups

    /** The app behind a package — a catalogue entry, or one built from the phone's own info. */
    fun forPackage(pkg: String): TrackedApp =
        knownByPackage[pkg] ?: dynamic.getOrPut(pkg) { build(pkg) }

    /** Looks up a stats key: catalogue key, or a package name for any other app. */
    fun forKey(key: String): TrackedApp? =
        knownByKey[key] ?: if (key.contains('.')) forPackage(key) else null

    fun isKnown(pkg: String): Boolean = knownByPackage.containsKey(pkg)

    fun isBrowser(pkg: String): Boolean =
        pkg in knownBrowsers || pkg in (browserCache ?: loadBrowsers())

    /** True if any of the app's package names is installed. */
    fun isInstalled(context: Context, app: TrackedApp): Boolean =
        app.packages.any { installed(context.packageManager, it) }

    /** Phone's own category says this is a social / video app (Android 8+ apps declare it). */
    fun isSocialCategory(pkg: String): Boolean {
        val pm = ctx?.packageManager ?: return false
        val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return false
        return info.category == ApplicationInfo.CATEGORY_SOCIAL || info.category == ApplicationInfo.CATEGORY_VIDEO
    }

    // ---------------------------------------------------------------- lists for the UI

    /**
     * Installed apps where reels can be watched: the catalogue apps that are installed, every
     * installed app the phone marks as Social or Video, and any app where reels were already
     * counted. Newly installed apps show up automatically the next time the list is built.
     */
    fun installedApps(context: Context, extraKeys: Collection<String> = emptyList()): List<TrackedApp> {
        attach(context)
        val pm = context.packageManager
        val result = LinkedHashMap<String, TrackedApp>()
        known.forEach { app -> if (app.packages.any { installed(pm, it) }) result[app.key] = app }

        val browsers = loadBrowsers()
        launcherPackages(pm).forEach { pkg ->
            if (pkg == context.packageName || knownByPackage.containsKey(pkg) || pkg in browsers) return@forEach
            val info = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@forEach
            if (info.category == ApplicationInfo.CATEGORY_SOCIAL || info.category == ApplicationInfo.CATEGORY_VIDEO) {
                result.getOrPut(pkg) { forPackage(pkg) }
            }
        }
        extraKeys.forEach { key ->
            if (result.containsKey(key) || knownByKey.containsKey(key) || !key.contains('.')) return@forEach
            if (key in browsers || !installed(pm, key)) return@forEach
            result[key] = forPackage(key)
        }
        return result.values.toList()
    }

    /** Installed web browsers (Chrome, Samsung Internet, Firefox…). */
    fun installedBrowsers(context: Context): List<TrackedApp> {
        attach(context)
        val pm = context.packageManager
        return loadBrowsers().filter { installed(pm, it) }
            .map { forPackage(it) }
            .sortedBy { it.name.lowercase() }
    }

    /** Forget cached lists after an app is installed or removed. */
    fun refresh() {
        browserCache = null
        dynamic.clear()
    }

    // ---------------------------------------------------------------- internals

    private fun installed(pm: PackageManager, pkg: String): Boolean =
        runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess

    private fun launcherPackages(pm: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching { pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet() }
            .getOrDefault(emptySet())
    }

    private fun loadBrowsers(): Set<String> {
        browserCache?.let { return it }
        val pm = ctx?.packageManager ?: return knownBrowsers
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PackageManager.MATCH_ALL else 0
        val found = runCatching { pm.queryIntentActivities(intent, flags).map { it.activityInfo.packageName } }
            .getOrDefault(emptyList())
            // An app that only opens its own links (YouTube, Facebook…) isn't a browser.
            .filter { !knownByPackage.containsKey(it) && looksLikeBrowser(pm, it) }
        return (found.toSet() + knownBrowsers).also { browserCache = it }
    }

    /** A real browser opens ANY web link, not just its own domain. */
    private fun looksLikeBrowser(pm: PackageManager, pkg: String): Boolean {
        if (pkg in knownBrowsers) return true
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://scroll-counter-probe-${pkg.hashCode()}.example"))
            .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(pkg)
        return runCatching { pm.queryIntentActivities(probe, 0).isNotEmpty() }.getOrDefault(false)
    }

    private fun build(pkg: String): TrackedApp {
        val pm = ctx?.packageManager
        val info = pm?.let { runCatching { it.getApplicationInfo(pkg, 0) }.getOrNull() }
        val label = info?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: prettyName(pkg)
        val color = info?.let { runCatching { iconColor(pm.getApplicationIcon(it)) }.getOrNull() } ?: 0xFF14B8A6
        val on = if (luminance(color) > 0.6) 0xFF111111 else 0xFFFFFFFF
        return TrackedApp(pkg, label, color, on, listOf(pkg), isBrowser = pkg in knownBrowsers || browserCache?.contains(pkg) == true)
    }

    /** "com.example.coolvideo" → "Coolvideo" when the app is no longer installed. */
    private fun prettyName(pkg: String): String {
        val part = pkg.split('.').lastOrNull { it.length > 2 && it !in setOf("android", "app", "lite", "mobile") } ?: pkg
        return part.replaceFirstChar { it.uppercase() }
    }

    /** The icon's dominant vivid colour, used for the app's bars and tile. */
    private fun iconColor(d: android.graphics.drawable.Drawable): Long {
        val size = 24
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        val hsv = FloatArray(3)
        var bestScore = -1f
        var best = 0
        // 12 hue buckets weighted by saturation × brightness; pick the strongest bucket's mean colour
        val r = FloatArray(12); val g = FloatArray(12); val b = FloatArray(12); val w = FloatArray(12)
        for (y in 0 until size) for (x in 0 until size) {
            val p = bmp.getPixel(x, y)
            if (Color.alpha(p) < 200) continue
            Color.colorToHSV(p, hsv)
            val weight = hsv[1] * hsv[2]
            if (weight < 0.15f) continue
            val i = ((hsv[0] / 30f).toInt()).coerceIn(0, 11)
            r[i] += Color.red(p) * weight; g[i] += Color.green(p) * weight; b[i] += Color.blue(p) * weight; w[i] += weight
        }
        bmp.recycle()
        for (i in 0 until 12) if (w[i] > bestScore) { bestScore = w[i]; best = i }
        if (bestScore <= 0f) return 0xFF14B8A6
        val rr = (r[best] / w[best]).toInt().coerceIn(0, 255)
        val gg = (g[best] / w[best]).toInt().coerceIn(0, 255)
        val bb = (b[best] / w[best]).toInt().coerceIn(0, 255)
        return (0xFF000000 or (rr.toLong() shl 16) or (gg.toLong() shl 8) or bb.toLong())
    }

    private fun luminance(c: Long): Double {
        val r = ((c shr 16) and 0xFF) / 255.0
        val g = ((c shr 8) and 0xFF) / 255.0
        val b = (c and 0xFF) / 255.0
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}
