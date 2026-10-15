package com.rabbi.scrollcounter.guard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.rabbi.scrollcounter.data.ScrollStore

/**
 * Device-admin receiver used ONLY for "Prevent app uninstall": while it's active, Android refuses
 * to uninstall Scroll Counter. It requests no admin policies at all (no lock, wipe or password).
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Turning this off lets Scroll Counter be uninstalled, and your reel limit stops protecting you."

    override fun onDisabled(context: Context, intent: Intent) {
        ScrollStore.get(context).uninstallGuard = false
    }

    override fun onEnabled(context: Context, intent: Intent) {
        ScrollStore.get(context).uninstallGuard = true
    }
}
