package com.rabbi.scrollcounter.tracker

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Live device state the UI shows and the setup buttons fix. */
data class DeviceStatus(
    val trackerOn: Boolean,
    val notificationsOn: Boolean,
    val batteryUnrestricted: Boolean,
    val widgetPlaced: Boolean = false,
    val uninstallProtected: Boolean = false,
    val historySafe: Boolean = false,
)

object TrackerStatus {

    fun read(context: Context) = DeviceStatus(
        trackerOn = isEnabled(context),
        notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        batteryUnrestricted = isBatteryUnrestricted(context),
        widgetPlaced = com.rabbi.scrollcounter.widget.WidgetPin.isPlaced(context),
        uninstallProtected = com.rabbi.scrollcounter.guard.UninstallGuard.isAdminActive(context),
        historySafe = com.rabbi.scrollcounter.data.DurableStore.Vault.hasAccess(context),
    )

    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(context, ScrollTrackerService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun isBatteryUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Android's Accessibility screen, where the user turns the scroll tracker on/off. */
    fun openAccessibilitySettings(context: Context) = start(
        context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    )

    /** Battery optimization list (no special permission needed), falling back to app info. */
    /** One-tap "Allow" dialog for unrestricted battery; falls back to the settings list. */
    @android.annotation.SuppressLint("BatteryLife")
    fun openBatterySettings(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        if (start(context, direct)) return
        if (!start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            openAppDetails(context)
        }
    }

    fun openNotificationSettings(context: Context) {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (!start(context, i)) openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        start(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        )
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
