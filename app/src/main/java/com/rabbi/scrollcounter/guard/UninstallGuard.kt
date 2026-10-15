package com.rabbi.scrollcounter.guard

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.rabbi.scrollcounter.data.ScrollStore

/**
 * "Prevent app uninstall".
 *
 *  1. Device admin: while active, Android itself refuses to uninstall the app.
 *  2. Extra guard in the tracking service: if Scroll Counter's own uninstall dialog, App-info page
 *     or device-admin page is opened, the user is sent back. Only those system screens are looked
 *     at, and only to find Scroll Counter's name next to an uninstall / deactivate button.
 *
 * It can be turned off any time from inside Scroll Counter (after a small math check).
 */
object UninstallGuard {

    fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean = runCatching {
        context.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(context))
    }.getOrDefault(false)

    /** Intent that shows Android's "Activate device admin app?" screen. */
    fun activateIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(context))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Prevents Scroll Counter from being uninstalled on impulse. Scroll Counter uses no admin powers — " +
                    "it can't lock, wipe or change anything. Turn it off any time in Scroll Counter → Settings."
            )

    fun turnOff(context: Context) {
        ScrollStore.get(context).uninstallGuard = false
        runCatching {
            context.getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(component(context))
        }
    }

    /** Settings apps, security centres and package installers — the only places we look. */
    fun isGuardedScreen(pkg: String): Boolean =
        pkg.contains("packageinstaller") || pkg.contains("settings") || pkg.contains("securitycenter") ||
            pkg == "com.miui.home" || pkg.contains("permissioncontroller") || pkg.contains("safecenter") ||
            pkg.contains("phonemanager") || pkg.contains("devicecare") || pkg == "com.android.vending"

    private val DANGER = listOf(
        "uninstall", "deactivate", "device admin", "remove app", "delete app", "force stop",
        "আনইনস্টল", "নিষ্ক্রিয়", "আনইন্সটল",
    )

    fun shouldBlock(context: Context, root: AccessibilityNodeInfo?): Boolean {
        root ?: return false
        val label = runCatching { context.applicationInfo.loadLabel(context.packageManager).toString() }.getOrDefault("Scroll Counter")
        val mine = runCatching { root.findAccessibilityNodeInfosByText(label) }.getOrNull().orEmpty()
        if (mine.isEmpty()) return false
        return DANGER.any { word ->
            runCatching { root.findAccessibilityNodeInfosByText(word) }.getOrNull().orEmpty().isNotEmpty()
        }
    }

    private var lastToast = 0L

    fun kickOut(service: AccessibilityService) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        Handler(Looper.getMainLooper()).postDelayed({
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }, 120)
        val now = System.currentTimeMillis()
        if (now - lastToast > 4000) {
            lastToast = now
            Toast.makeText(service, "Scroll Counter is protected. Turn off protection inside the app first.", Toast.LENGTH_LONG).show()
        }
    }
}
