package com.rabbi.scrollcounter.data

import android.content.Context

/** A social app we track. One app can have several package names (e.g. TikTok regional builds). */
data class TrackedApp(
    val key: String,
    val name: String,
    /** Brand color used for the app's icon tile and bars. */
    val color: Long,
    /** Text color on top of [color]. */
    val onColor: Long = 0xFFFFFFFF,
    val packages: List<String>,
)

object TrackedApps {
    val all = listOf(
        TrackedApp("facebook", "Facebook", 0xFF1877F2, packages = listOf("com.facebook.katana", "com.facebook.lite")),
        TrackedApp("instagram", "Instagram", 0xFFE1306C, packages = listOf("com.instagram.android")),
        TrackedApp("tiktok", "TikTok", 0xFF111111, packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go")),
        TrackedApp("youtube", "YouTube", 0xFFFF0033, packages = listOf("com.google.android.youtube")),
        TrackedApp("x", "X (Twitter)", 0xFF111111, packages = listOf("com.twitter.android")),
        TrackedApp("threads", "Threads", 0xFF111111, packages = listOf("com.instagram.barcelona")),
        TrackedApp("snapchat", "Snapchat", 0xFFFFE400, 0xFF111111, listOf("com.snapchat.android")),
        TrackedApp("reddit", "Reddit", 0xFFFF4500, packages = listOf("com.reddit.frontpage")),
        TrackedApp("linkedin", "LinkedIn", 0xFF0A66C2, packages = listOf("com.linkedin.android")),
        TrackedApp("pinterest", "Pinterest", 0xFFE60023, packages = listOf("com.pinterest")),
        TrackedApp("likee", "Likee", 0xFFFF2D7A, packages = listOf("video.like", "video.like.lite")),
        TrackedApp("kwai", "Kwai", 0xFFFF7E1B, packages = listOf("com.kwai.video")),
        TrackedApp("snack", "Snack Video", 0xFFFFD21E, 0xFF111111, listOf("com.kwai.bulldog")),
        TrackedApp("moj", "Moj", 0xFF7C3AED, packages = listOf("in.mohalla.video")),
    )

    private val byPackage: Map<String, TrackedApp> =
        all.flatMap { app -> app.packages.map { it to app } }.toMap()

    fun forPackage(pkg: String): TrackedApp? = byPackage[pkg]
    fun forKey(key: String): TrackedApp? = all.firstOrNull { it.key == key }

    /** True if any of the app's package names is installed (visible thanks to <queries> in the manifest). */
    fun isInstalled(context: Context, app: TrackedApp): Boolean =
        app.packages.any { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }
}
