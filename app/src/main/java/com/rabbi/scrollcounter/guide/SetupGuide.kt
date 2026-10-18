package com.rabbi.scrollcounter.guide

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import com.rabbi.scrollcounter.tracker.ScrollTrackerService
import com.rabbi.scrollcounter.tracker.TrackerStatus

/**
 * Turning on Accessibility with a floating, phone-specific how-to: the phone's Settings page opens
 * and a small picture-in-picture guide plays on top of it, showing exactly what to tap on this
 * brand (Samsung, Xiaomi, Vivo, Oppo / Realme / OnePlus, Pixel …). It moves on by itself as each
 * step is done, and brings the user back to Scroll Counter once it's on.
 */
object SetupGuide {

    /** Where the user is on the way to turning Scroll Counter on. */
    enum class Stage {
        /** Normal case: Accessibility → the app → switch on. */
        ENABLE,
        /** Android 13+ blocks Accessibility for apps installed from an APK; first try once (it gets refused). */
        BLOCKED,
        /** Tried once: now App info → ⋮ → "Allow restricted settings". */
        CAN_ALLOW,
        /** Done. */
        ON,
    }

    fun stage(context: Context): Stage {
        if (TrackerStatus.isEnabled(context)) return Stage.ON
        return when (restrictedMode(context)) {
            AppOpsManager.MODE_ERRORED -> Stage.BLOCKED
            AppOpsManager.MODE_IGNORED -> Stage.CAN_ALLOW
            else -> Stage.ENABLE
        }
    }

    /**
     * Android's "restricted settings" state for this app (the same app-op the phone's Settings
     * reads): ERRORED = blocked, IGNORED = refused once so App info now offers "Allow restricted
     * settings", ALLOWED (or anything else / older Android) = free to turn on.
     */
    private fun restrictedMode(context: Context): Int {
        if (Build.VERSION.SDK_INT < 33) return AppOpsManager.MODE_ALLOWED
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return AppOpsManager.MODE_ALLOWED
        return runCatching {
            ops.unsafeCheckOpNoThrow("android:access_restricted_settings", Process.myUid(), context.packageName)
        }.getOrDefault(AppOpsManager.MODE_ALLOWED)
    }

    /** Open the right Settings page, then float the guide on top of it. */
    fun launch(context: Context) {
        val stage = stage(context)
        if (stage == Stage.ON) { TrackerStatus.openAccessibilitySettings(context); return }
        openFor(context, stage)
        runCatching {
            context.startActivity(
                Intent(context, SetupGuideActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }

    /** The Settings page for [stage]. */
    fun openFor(context: Context, stage: Stage): Boolean = when (stage) {
        Stage.CAN_ALLOW -> start(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        )
        else -> openAccessibility(context)
    }

    /** Accessibility settings, asking (where the phone supports it) to point straight at Scroll Counter. */
    private fun openAccessibility(context: Context): Boolean {
        val me = ComponentName(context, ScrollTrackerService::class.java).flattenToString()
        val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", me)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", me) })
        return start(context, i) || start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun start(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
