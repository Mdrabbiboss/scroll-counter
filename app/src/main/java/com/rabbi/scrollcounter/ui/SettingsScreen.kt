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
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private enum class Confirm { RESET_TODAY, DELETE_ALL }

@Composable
fun SettingsScreen(
    store: ScrollStore,
    tick: Long,
    status: DeviceStatus,
    onEnableTracking: () -> Unit,
    onEditLimit: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val limit = remember(tick) { store.dailyLimit }
    val paused = remember(tick) { store.paused }
    val limitAlerts = remember(tick) { store.limitAlertsOn }
    val warnAlerts = remember(tick) { store.warnAlertsOn }
    val theme = remember(tick) { store.themeMode }
    val bubbleOn = remember(tick) { store.bubbleOn }
    val blockOn = remember(tick) { store.blockOn }
    val installed = remember { TrackedApps.all.associate { it.key to TrackedApps.isInstalled(context, it) } }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var showPolicy by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader("Settings", "Scroll Counter") }

        // ---- Tracking ----
        item { SectionLabel("Tracking") }
        item {
            PremiumCard {
                SettingRow(
                    Icons.Rounded.AccessibilityNew, "Scroll tracking",
                    if (status.trackerOn) "On — allowed in Accessibility" else "Off — tap Enable to start counting",
                    iconTint = if (status.trackerOn) Brand.Good else MaterialTheme.colorScheme.error,
                ) {
                    if (status.trackerOn) FilledTonalButton(onClick = { TrackerStatus.openAccessibilitySettings(context) }) { Text("Manage") }
                    else Button(onClick = onEnableTracking) { Text("Enable") }
                }
                SettingRow(
                    Icons.Rounded.PauseCircle, "Pause tracking",
                    if (paused) "Paused — scrolls aren't counted" else "Stop counting for a while",
                    iconTint = Brand.Warn,
                    onClick = { store.paused = !paused }
                ) { PremiumSwitch(checked = paused, onCheckedChange = { store.paused = it }) }
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
                SettingRow(Icons.Rounded.Flag, "Daily scroll limit", "$limit scrolls per day", onClick = onEditLimit)
            }
        }

        // ---- While scrolling ----
        item { SectionLabel("While you scroll") }
        item {
            PremiumCard {
                SettingRow(
                    Icons.Rounded.Mood, "Scroll check-in",
                    "A card with a face every ${ScrollTrackerService.BUBBLE_EVERY} scrolls — it gets tired as you scroll more",
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = { store.bubbleOn = !bubbleOn }
                ) { PremiumSwitch(checked = bubbleOn, onCheckedChange = { store.bubbleOn = it }) }
                SettingRow(
                    Icons.Rounded.Block, "Close apps at limit",
                    "Takes you out of social apps once you hit $limit scrolls (5 extra minutes once a day)",
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { store.blockOn = !blockOn }
                ) { PremiumSwitch(checked = blockOn, onCheckedChange = { store.blockOn = it }) }
                SettingRow(
                    Icons.Rounded.Widgets, "Home screen widget",
                    "Long-press your home screen → Widgets → Scroll Counter",
                    iconTint = Brand.Good
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
                    if (status.notificationsOn) FilledTonalButton(onClick = {
                        Notifier.test(context)
                        Toast.makeText(context, "Test notification sent", Toast.LENGTH_SHORT).show()
                    }) { Text("Test") }
                    else Button(onClick = onRequestNotifications) { Text("Allow") }
                }
                SettingRow(
                    Icons.Rounded.WarningAmber, "80% heads-up",
                    "When you reach ${(limit * ScrollTrackerService.WARN_AT).toInt()} scrolls",
                    iconTint = Brand.Warn,
                    onClick = { store.warnAlertsOn = !warnAlerts }
                ) { PremiumSwitch(checked = warnAlerts, onCheckedChange = { store.warnAlertsOn = it }) }
                SettingRow(
                    Icons.Rounded.Campaign, "Limit reached alert",
                    "When you pass $limit scrolls",
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { store.limitAlertsOn = !limitAlerts }
                ) { PremiumSwitch(checked = limitAlerts, onCheckedChange = { store.limitAlertsOn = it }) }
            }
        }

        // ---- Apps ----
        item { SectionLabel("Apps to track") }
        item {
            PremiumCard {
                TrackedApps.all.forEach { app ->
                    val enabled = remember(tick, app.key) { store.isAppEnabled(app.key) }
                    val isInstalled = installed[app.key] == true
                    Row(
                        Modifier.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(app, size = 38)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (isInstalled) "Installed" else "Not installed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        PremiumSwitch(checked = enabled, onCheckedChange = { store.setAppEnabled(app.key, it) })
                    }
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
                SettingRow(Icons.Rounded.FileDownload, "Export as CSV", "Last ${ScrollStore.HISTORY_DAYS} days, to Drive, Sheets, email…",
                    onClick = { exportCsv(context, store) })
                SettingRow(Icons.Rounded.Restore, "Reset today", "Set today's count back to zero", iconTint = Brand.Warn,
                    onClick = { confirm = Confirm.RESET_TODAY })
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
        Confirm.RESET_TODAY -> ConfirmDialog(
            "Reset today?", "Today's scroll count, distance and hourly activity go back to zero.",
            "Reset", destructive = false,
            onConfirm = {
                store.clearDay(LocalDate.now()); confirm = null
                Toast.makeText(context, "Today reset", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirm = null }
        )
        Confirm.DELETE_ALL -> ConfirmDialog(
            "Delete all history?", "Every stored day is removed from this phone. This can't be undone.",
            "Delete", destructive = true,
            onConfirm = {
                store.clearAll(); confirm = null
                Toast.makeText(context, "History deleted", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirm = null }
        )
        null -> Unit
    }
    if (showPolicy) PrivacyPolicyDialog { showPolicy = false }
}

private fun exportCsv(context: Context, store: ScrollStore) {
    val csv = store.exportCsv()
    if (csv.lines().count { it.isNotBlank() } <= 1) {
        Toast.makeText(context, "Nothing to export yet", Toast.LENGTH_SHORT).show()
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Scroll Counter export (${LocalDate.now()})")
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    context.startActivity(Intent.createChooser(send, "Export scroll history").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
