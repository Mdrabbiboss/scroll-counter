package com.rabbi.scrollcounter.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rabbi.scrollcounter.BuildConfig
import com.rabbi.scrollcounter.ads.AdsConsent
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.data.ThemeMode
import com.rabbi.scrollcounter.data.TrackedApps
import com.rabbi.scrollcounter.tracker.DeviceStatus
import com.rabbi.scrollcounter.tracker.Notifier
import com.rabbi.scrollcounter.tracker.ScrollTrackerService
import com.rabbi.scrollcounter.tracker.TrackerStatus
import com.rabbi.scrollcounter.ui.theme.Brand
import java.time.LocalDate

private enum class Confirm { UNPROTECT, DELETE_ALL }

@Composable
fun SettingsScreen(
    store: ScrollStore,
    tick: Long,
    status: DeviceStatus,
    onEnableTracking: () -> Unit,
    onEditLimit: () -> Unit,
    onRequestNotifications: () -> Unit,
    onAddWidget: () -> Unit = {},
) {
    val context = LocalContext.current
    val limit = remember(tick) { store.dailyLimit }
    val limitAlerts = remember(tick) { store.limitAlertsOn }
    val warnAlerts = remember(tick) { store.warnAlertsOn }
    val theme = remember(tick) { store.themeMode }
    var protectedNow by remember(status) { mutableStateOf(status.uninstallProtected) }
    val adminLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        protectedNow = com.rabbi.scrollcounter.guard.UninstallGuard.isAdminActive(context)
        store.uninstallGuard = protectedNow
        if (protectedNow) Toast.makeText(context, "Uninstall protection is on", Toast.LENGTH_SHORT).show()
    }
    val onProtect: () -> Unit = {
        runCatching { adminLauncher.launch(com.rabbi.scrollcounter.guard.UninstallGuard.activateIntent(context)) }
            .onFailure { Toast.makeText(context, "This phone doesn't allow it", Toast.LENGTH_SHORT).show() }
    }
    val browsersOn = remember(tick) { store.browsersOn }
    // Only apps installed on this phone — rebuilt every time Settings opens, so new apps appear.
    var apps by remember { mutableStateOf<List<com.rabbi.scrollcounter.data.TrackedApp>?>(null) }
    var browsers by remember { mutableStateOf<List<com.rabbi.scrollcounter.data.TrackedApp>>(emptyList()) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            TrackedApps.refresh()
            val a = TrackedApps.installedApps(context, store.keysWithData())
            val b = TrackedApps.installedBrowsers(context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { apps = a; browsers = b }
        }
    }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var showPolicy by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader("Settings", "Scroll Counter") { AppLogo(48.dp) } }

        // ---- Tracking ----
        item { SectionLabel("Tracking") }
        item {
            PremiumCard {
                SettingRow(
                    Icons.Rounded.AccessibilityNew, "Scroll tracking",
                    if (status.trackerOn) "On — allowed in Accessibility" else "Off — tap Enable to start counting",
                    iconTint = if (status.trackerOn) Brand.Good else MaterialTheme.colorScheme.error,
                ) {
                    if (status.trackerOn) PillButton("Manage", Icons.Rounded.OpenInNew) { TrackerStatus.openAccessibilitySettings(context) }
                    else PillButton("Enable", gold = true, onClick = onEnableTracking)
                }
                SettingRow(
                    Icons.Rounded.BatteryChargingFull, "Battery optimization",
                    if (status.batteryUnrestricted) "Unrestricted — tracking stays reliable"
                    else "Restricted — some phones may stop tracking",
                    iconTint = if (status.batteryUnrestricted) Brand.Good else Brand.Warn,
                    onClick = { TrackerStatus.openBatterySettings(context) }
                )
            }
        }

        // ---- Goal ----
        item { SectionLabel("Daily goal") }
        item {
            PremiumCard {
                SettingRow(Icons.Rounded.Flag, "Daily scroll limit", "$limit reels per day", onClick = onEditLimit)
            }
        }

        // ---- Protection ----
        item { SectionLabel("Protection") }
        item {
            PremiumCard {
                SettingRow(
                    Icons.Rounded.Shield, "Prevent app uninstall",
                    if (protectedNow) "On — Scroll Counter can't be uninstalled until you turn this off here"
                    else "Stops you from uninstalling Scroll Counter on impulse",
                    iconTint = if (protectedNow) Brand.Good else Brand.Gold,
                    onClick = { if (protectedNow) confirm = Confirm.UNPROTECT else onProtect() }
                ) {
                    PremiumSwitch(
                        checked = protectedNow,
                        onCheckedChange = { on -> if (on) onProtect() else confirm = Confirm.UNPROTECT }
                    )
                }
                SettingRow(
                    Icons.Rounded.Widgets, "Home screen widget",
                    if (status.widgetPlaced) "On your home screen ✓" else "Tap to add it to your home screen",
                    iconTint = Brand.Good,
                    onClick = onAddWidget
                )
            }
        }

        // ---- Notifications ----
        item { SectionLabel("Notifications") }
        item {
            PremiumCard {
                SettingRow(
                    Icons.Rounded.NotificationsActive, "Notification permission",
                    if (status.notificationsOn) "Allowed" else "Off — alerts can't be shown",
                    iconTint = if (status.notificationsOn) Brand.Good else MaterialTheme.colorScheme.error,
                ) {
                    if (status.notificationsOn) PillButton("Test", Icons.Rounded.NotificationsActive) {
                        Notifier.test(context)
                        Toast.makeText(context, "Test notification sent", Toast.LENGTH_SHORT).show()
                    }
                    else PillButton("Allow", gold = true, onClick = onRequestNotifications)
                }
                SettingRow(
                    Icons.Rounded.WarningAmber, "80% heads-up",
                    "When you reach ${(limit * ScrollTrackerService.WARN_AT).toInt()} reels",
                    iconTint = Brand.Warn,
                    onClick = { store.warnAlertsOn = !warnAlerts }
                ) { PremiumSwitch(checked = warnAlerts, onCheckedChange = { store.warnAlertsOn = it }) }
                SettingRow(
                    Icons.Rounded.Campaign, "Limit reached alert",
                    "When you pass $limit reels",
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { store.limitAlertsOn = !limitAlerts }
                ) { PremiumSwitch(checked = limitAlerts, onCheckedChange = { store.limitAlertsOn = it }) }
            }
        }

        // ---- Apps ----
        item { SectionLabel("Apps to track") }
        item {
            PremiumCard {
                Text(
                    "Reels are counted in every app — these are the social & video apps on your phone. New apps appear here automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                val list = apps
                if (list == null) {
                    Text("Looking for your apps…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 10.dp))
                } else if (list.isEmpty()) {
                    Text("No social apps installed yet", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 10.dp))
                }
                list?.forEach { app ->
                    val enabled = remember(tick, app.key) { store.isAppEnabled(app.key) }
                    TrackedAppRow(app, if (enabled) "Counting reels" else "Not counted", enabled) { store.setAppEnabled(app.key, it) }
                }
            }
        }

        item { SectionLabel("Browsers") }
        item {
            PremiumCard {
                SettingRow(
                    Icons.Rounded.Public, "Count reels on websites",
                    if (browsersOn) "TikTok, Shorts & Reels in Chrome or any browser" else "Off — browsers are ignored",
                    onClick = { store.browsersOn = !browsersOn }
                ) { PremiumSwitch(checked = browsersOn, onCheckedChange = { store.browsersOn = it }) }
                if (browsersOn) browsers.forEach { app ->
                    val enabled = remember(tick, app.key) { store.isAppEnabled(app.key) }
                    TrackedAppRow(app, "Browser", enabled) { store.setAppEnabled(app.key, it) }
                }
            }
        }

        // ---- Appearance ----
        item { SectionLabel("Appearance") }
        item {
            PremiumCard {
                SettingRow(Icons.Rounded.Palette, "Theme", "Follow the phone, or pick one")
                Spacer(Modifier.height(8.dp))
                SegmentedToggle(
                    listOf("System", "Light", "Dark"),
                    theme.ordinal,
                    { store.themeMode = ThemeMode.entries[it] }
                )
            }
        }

        // ---- Data ----
        item { SectionLabel("Your data") }
        item {
            PremiumCard {
                SettingRow(Icons.Rounded.DeleteForever, "Delete all history", "Removes every stored day", iconTint = MaterialTheme.colorScheme.error,
                    onClick = { confirm = Confirm.DELETE_ALL })
            }
        }

        // ---- About ----
        item { SectionLabel("About") }
        item {
            PremiumCard {
                SettingRow(Icons.Rounded.PrivacyTip, "Privacy policy", "What's stored and what isn't", onClick = { showPolicy = true })
                if (AdsConsent.privacyOptionsRequired) {
                    SettingRow(Icons.Rounded.Shield, "Ad privacy choices", "Change your ad consent",
                        onClick = { (context as? Activity)?.let { AdsConsent.showPrivacyOptions(it) } })
                }
                SettingRow(Icons.Rounded.Star, "Rate Scroll Counter", "Opens Google Play", iconTint = Brand.Warn, onClick = { rateApp(context) })
                SettingRow(Icons.Rounded.Share, "Share with friends", null, onClick = { shareApp(context) })
                SettingRow(Icons.Rounded.Info, "Version", BuildConfig.VERSION_NAME, iconTint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    when (confirm) {
        Confirm.UNPROTECT -> DeleteHistoryDialog(
            title = "Turn off uninstall protection?",
            body = "Scroll Counter will be uninstallable again, and the device-admin protection is removed.\n\n" +
                "Sure you want to drop this commitment?",
            confirmLabel = "Turn off",
            dismissLabel = "Keep protection",
            onConfirm = {
                com.rabbi.scrollcounter.guard.UninstallGuard.turnOff(context)
                protectedNow = false
                confirm = null
                Toast.makeText(context, "Uninstall protection turned off", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirm = null }
        )
        Confirm.DELETE_ALL -> DeleteHistoryDialog(
            onConfirm = {
                store.clearAll(); confirm = null
                Toast.makeText(context, "All history deleted", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirm = null }
        )
        null -> Unit
    }
    if (showPolicy) PrivacyPolicyDialog { showPolicy = false }
}

private fun rateApp(context: Context) {
    val pkg = context.packageName
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun shareApp(context: Context) {
    val text = "I'm tracking how much I scroll with Scroll Counter: https://play.google.com/store/apps/details?id=${context.packageName}"
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(send, "Share Scroll Counter").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun TrackedAppRow(app: com.rabbi.scrollcounter.data.TrackedApp, subtitle: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app, size = 38)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PremiumSwitch(checked = enabled, onCheckedChange = onChange)
    }
}
