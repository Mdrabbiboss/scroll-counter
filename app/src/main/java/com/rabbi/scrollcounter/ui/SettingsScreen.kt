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
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
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
    onRequestStorage: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
    onPickLanguage: () -> Unit = {},
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
        if (protectedNow) Toast.makeText(context, tr("Uninstall protection is on"), Toast.LENGTH_SHORT).show()
    }
    val onProtect: () -> Unit = {
        runCatching { adminLauncher.launch(com.rabbi.scrollcounter.guard.UninstallGuard.activateIntent(context)) }
            .onFailure { Toast.makeText(context, tr("This phone doesn't allow it"), Toast.LENGTH_SHORT).show() }
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
    var showLog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { ScreenHeader(tr("Settings"), "Scroll Counter") { AppLogo(48.dp) } }

        // ---- Tracking ----
        item { SectionLabel(tr("Tracking")) }
        item {
            PremiumCard {
                SettingRow(
                    Icons.Rounded.AccessibilityNew, tr("Scroll tracking"),
                    if (status.trackerOn) tr("On — allowed in Accessibility") else tr("Off — tap Enable to start counting"),
                    iconTint = if (status.trackerOn) Brand.Good else MaterialTheme.colorScheme.error,
                ) {
                    if (status.trackerOn) PillButton(tr("Manage"), Icons.Rounded.OpenInNew) { TrackerStatus.openAccessibilitySettings(context) }
                    else PillButton(tr("Enable"), gold = true, onClick = onEnableTracking)
                }
                SettingRow(
                    Icons.Rounded.BatteryChargingFull, tr("Battery optimization"),
                    if (status.batteryUnrestricted) tr("Unrestricted — tracking stays reliable")
                    else tr("Restricted — some phones may stop tracking"),
                    iconTint = if (status.batteryUnrestricted) Brand.Good else Brand.Warn,
                    onClick = { TrackerStatus.openBatterySettings(context) }
                )
            }
        }

        // ---- Goal ----
        item { SectionLabel(tr("Daily goal")) }
        item {
            PremiumCard {
                val now by rememberNow()
                val today = now.toLocalDate()
                val locked = remember(tick, today) { store.limitLockedToday }
                SettingRow(Icons.Rounded.Flag, tr("Daily reel limit"),
                    if (locked) tr("%1\$s reels · unlocks in %2\$s", limit, formatCountdownShort(secondsToUnlock(now))) else tr("%1\$s reels per day · max %2\$s, once a day", limit, ScrollStore.MAX_LIMIT),
                    iconTint = Brand.Gold, onClick = onEditLimit) {
                    if (locked) PillButton(tr("Locked"), Icons.Rounded.Lock, onClick = onEditLimit)
                    else PillButton(tr("Edit"), Icons.Rounded.Edit, gold = true, onClick = onEditLimit)
                }
            }
        }

        // ---- On-screen counter ----
        item { SectionLabel(tr("On-screen counter")) }
        item {
            PremiumCard {
                Text(tr("Counter size"), style = MaterialTheme.typography.titleMedium)
                Text(tr("The little counter at the top while you watch reels"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                val size = remember(tick) { store.pillSize }
                PillPreview(size)
                Spacer(Modifier.height(14.dp))
                SegmentedToggle(listOf(tr("Small"), tr("Normal"), tr("Large"), tr("XL")), size, { store.pillSize = it })
            }
        }

        // ---- Protection ----
        item { SectionLabel(tr("Protection")) }
        item {
            PremiumCard {
                SettingRow(
                    Icons.Rounded.Shield, tr("Prevent app uninstall"),
                    if (protectedNow) tr("On — Scroll Counter can't be uninstalled until you turn this off here")
                    else tr("Stops you from uninstalling Scroll Counter on impulse"),
                    iconTint = if (protectedNow) Brand.Good else Brand.Gold,
                    onClick = { if (protectedNow) confirm = Confirm.UNPROTECT else onProtect() }
                ) {
                    PremiumSwitch(
                        checked = protectedNow,
                        onCheckedChange = { on -> if (on) onProtect() else confirm = Confirm.UNPROTECT }
                    )
                }
                SettingRow(
                    Icons.Rounded.Widgets, tr("Home screen widget"),
                    if (status.widgetPlaced) tr("On your home screen") else tr("Tap to add it to your home screen"),
                    iconTint = Brand.Good,
                    onClick = onAddWidget
                )
            }
        }

        // ---- Notifications ----
        item { SectionLabel(tr("Notifications")) }
        item {
            PremiumCard {
                SettingRow(
                    Icons.Rounded.NotificationsActive, tr("Notification permission"),
                    if (status.notificationsOn) tr("Allowed") else tr("Off — alerts can't be shown"),
                    iconTint = if (status.notificationsOn) Brand.Good else MaterialTheme.colorScheme.error,
                ) {
                    if (status.notificationsOn) PillButton(tr("Test"), Icons.Rounded.NotificationsActive) {
                        Notifier.test(context)
                        Toast.makeText(context, tr("Test notification sent"), Toast.LENGTH_SHORT).show()
                    }
                    else PillButton(tr("Allow"), gold = true, onClick = onRequestNotifications)
                }
                SettingRow(
                    Icons.Rounded.WarningAmber, tr("80% heads-up"),
                    tr("When you reach %1\$s reels", (limit * ScrollTrackerService.WARN_AT).toInt()),
                    iconTint = Brand.Warn,
                    onClick = { store.warnAlertsOn = !warnAlerts }
                ) { PremiumSwitch(checked = warnAlerts, onCheckedChange = { store.warnAlertsOn = it }) }
                SettingRow(
                    Icons.Rounded.Campaign, tr("Limit reached alert"),
                    tr("When you pass %1\$s reels", limit),
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { store.limitAlertsOn = !limitAlerts }
                ) { PremiumSwitch(checked = limitAlerts, onCheckedChange = { store.limitAlertsOn = it }) }
            }
        }

        // ---- Apps ----
        item { SectionLabel(tr("Apps to track")) }
        item {
            PremiumCard {
                Text(
                    tr("Reels are counted in every app — these are the social & video apps on your phone. New apps appear here automatically."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                val list = apps
                if (list == null) {
                    Text(tr("Looking for your apps…"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 10.dp))
                } else if (list.isEmpty()) {
                    Text(tr("No social apps installed yet"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 10.dp))
                }
                list?.forEach { app ->
                    val enabled = remember(tick, app.key) { store.isAppEnabled(app.key) }
                    TrackedAppRow(app, if (enabled) tr("Counting reels") else tr("Not counted"), enabled) { store.setAppEnabled(app.key, it) }
                }
            }
        }

        item { SectionLabel(tr("Browsers")) }
        item {
            PremiumCard {
                SettingRow(
                    Icons.Rounded.Public, tr("Count reels on websites"),
                    if (browsersOn) tr("TikTok, Shorts & Reels in Chrome or any browser") else tr("Off — browsers are ignored"),
                    onClick = { store.browsersOn = !browsersOn }
                ) { PremiumSwitch(checked = browsersOn, onCheckedChange = { store.browsersOn = it }) }
                if (browsersOn) browsers.forEach { app ->
                    val enabled = remember(tick, app.key) { store.isAppEnabled(app.key) }
                    TrackedAppRow(app, tr("Browser"), enabled) { store.setAppEnabled(app.key, it) }
                }
            }
        }

        // ---- Appearance ----
        item { SectionLabel(tr("Appearance")) }
        item {
            PremiumCard {
                SettingRow(Icons.Rounded.Language, tr("Language"), store.language.native, iconTint = Brand.Gold, onClick = onPickLanguage) {
                    PillButton(tr("Change"), onClick = onPickLanguage)
                }
                Spacer(Modifier.height(8.dp))
                SettingRow(Icons.Rounded.Palette, tr("Theme"), tr("Follow the phone, or pick one"))
                Spacer(Modifier.height(8.dp))
                SegmentedToggle(
                    listOf(tr("System"), tr("Light"), tr("Dark")),
                    theme.ordinal,
                    { store.themeMode = ThemeMode.entries[it] }
                )
                Spacer(Modifier.height(8.dp))
                val sounds = remember(tick) { store.buddySounds }
                SettingRow(
                    Icons.Rounded.MusicNote, tr("Buddy sounds"),
                    if (sounds) tr("Your buddy talks by mood, like a pet (media volume)") else tr("Off"),
                    iconTint = Brand.Gold
                ) { PremiumSwitch(checked = sounds, onCheckedChange = { store.buddySounds = it }) }
            }
        }

        // ---- About ----
        item { SectionLabel(tr("About")) }
        item {
            PremiumCard {
                SettingRow(Icons.Rounded.PrivacyTip, tr("Privacy policy"), tr("What's stored and what isn't"), onClick = { showPolicy = true })
                if (AdsConsent.privacyOptionsRequired) {
                    SettingRow(Icons.Rounded.Shield, tr("Ad privacy choices"), tr("Change your ad consent"),
                        onClick = { (context as? Activity)?.let { AdsConsent.showPrivacyOptions(it) } })
                }
                SettingRow(Icons.Rounded.Star, tr("Rate Scroll Counter"), tr("Opens Google Play"), iconTint = Brand.Warn, onClick = { rateApp(context) })
                SettingRow(Icons.Rounded.Share, tr("Share with friends"), null, onClick = { shareApp(context) })
            }
        }
    }

    when (confirm) {
        Confirm.UNPROTECT -> DeleteHistoryDialog(
            title = tr("Turn off uninstall protection?"),
            body = tr("Scroll Counter will be uninstallable again, and the device-admin protection is removed.\n\nSure you want to drop this commitment?"),
            confirmLabel = tr("Turn off"),
            dismissLabel = tr("Keep protection"),
            onConfirm = {
                com.rabbi.scrollcounter.guard.UninstallGuard.turnOff(context)
                protectedNow = false
                confirm = null
                Toast.makeText(context, tr("Uninstall protection turned off"), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirm = null }
        )
        Confirm.DELETE_ALL -> DeleteHistoryDialog(
            onConfirm = {
                store.clearAll(); confirm = null
                Toast.makeText(context, tr("All history deleted"), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { confirm = null }
        )
        null -> Unit
    }
    if (showPolicy) PrivacyPolicyDialog { showPolicy = false }
    if (showLog) DetectionLogDialog { showLog = false }
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
    context.startActivity(Intent.createChooser(send, tr("Share Scroll Counter")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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

/** Live list of what the tracker saw recently (Settings → Detection check). */
@Composable
private fun DetectionLogDialog(onDismiss: () -> Unit) {
    var lines by remember { mutableStateOf(com.rabbi.scrollcounter.tracker.DetectLog.snapshot()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            lines = com.rabbi.scrollcounter.tracker.DetectLog.snapshot()
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Detection check")) },
        text = {
            Column {
                Text(
                    tr("Open an app, watch a few reels, then come back here. Newest first."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.lazy.LazyColumn(Modifier.height(380.dp)) {
                    if (lines.isEmpty()) item { Text(tr("Nothing yet — make sure tracking is on, then scroll some reels."), style = MaterialTheme.typography.bodySmall) }
                    items(lines.size) { i ->
                        Text(
                            lines[i],
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            modifier = Modifier.padding(vertical = 3.dp)
                        )
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(tr("Close")) } },
        dismissButton = {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Row {
                androidx.compose.material3.TextButton(onClick = { com.rabbi.scrollcounter.tracker.DetectLog.clear(); lines = emptyList() }) { Text(tr("Clear")) }
                androidx.compose.material3.TextButton(onClick = {
                    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText(tr("Scroll Counter detection"), lines.joinToString("\n")))
                    Toast.makeText(ctx, tr("Copied — paste it in the chat"), Toast.LENGTH_SHORT).show()
                }) { Text(tr("Copy")) }
            }
        }
    )
}

/** A live copy of the on-screen counter at the chosen size. */
@Composable
private fun PillPreview(style: Int) {
    val glass = androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xC0061F1C), androidx.compose.ui.graphics.Color(0xC00D5F55)))
    val rim = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x40FFFFFF))
    val gold = androidx.compose.ui.graphics.Color(0xFFFFD873)
    val num = com.rabbi.scrollcounter.ui.theme.AppFonts.Numbers
    val fresh = com.rabbi.scrollcounter.face.Mood.FRESH
    Box(
        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF2A2F3A), androidx.compose.ui.graphics.Color(0xFF14171D)))),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.Crossfade(style, label = "pillStyle") { st ->
            when (st) {
                0 -> Box(contentAlignment = Alignment.TopCenter) {
                    Box(
                        Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(glass)
                            .border(rim, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) { AnimatedFace(fresh, 38.dp) }
                    Text(
                        "12", color = androidx.compose.ui.graphics.Color(0xFF5A3A12), fontFamily = num, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 38.dp).clip(RoundedCornerShape(20.dp)).background(androidx.compose.ui.graphics.Color(0xFFFFF1D0))
                            .border(1.5.dp, androidx.compose.ui.graphics.Color(0xFF3A2A12), RoundedCornerShape(20.dp))
                            .padding(horizontal = 9.dp, vertical = 2.dp)
                    )
                }
                2 -> androidx.compose.foundation.layout.Column(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(glass).border(rim, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedFace(fresh, 42.dp)
                    Text("12", color = gold, fontFamily = num, fontSize = 22.sp, modifier = Modifier.padding(top = 2.dp))
                    Box(Modifier.padding(top = 5.dp).width(46.dp).height(5.dp).clip(RoundedCornerShape(50)).background(androidx.compose.ui.graphics.Color(0x33FFFFFF))) {
                        Box(Modifier.width(18.dp).height(5.dp).clip(RoundedCornerShape(50)).background(androidx.compose.ui.graphics.Color(0xFFF8C33A)))
                    }
                }
                3 -> Row(
                    Modifier.clip(RoundedCornerShape(40.dp)).background(glass).border(rim, RoundedCornerShape(40.dp))
                        .padding(start = 5.dp, end = 18.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(56.dp).drawBehind {
                            val sw = size.width * 0.075f
                            val inset = sw / 2
                            val arcSize = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
                            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                            drawArc(androidx.compose.ui.graphics.Color(0x33FFFFFF), 0f, 360f, false, tl, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                            drawArc(androidx.compose.ui.graphics.Color(0xFFF8C33A), -90f, 130f, false, tl, arcSize,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        },
                        contentAlignment = Alignment.Center
                    ) { AnimatedFace(fresh, 42.dp) }
                    Spacer(Modifier.width(9.dp))
                    androidx.compose.foundation.layout.Column {
                        Text("12", color = gold, fontFamily = num, fontSize = 28.sp)
                        Text("/33", color = androidx.compose.ui.graphics.Color(0xB3FFFFFF), fontFamily = num, fontSize = 12.sp)
                    }
                }
                else -> Row(
                    Modifier.clip(RoundedCornerShape(30.dp)).background(glass).border(rim, RoundedCornerShape(30.dp))
                        .padding(start = 5.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedFace(fresh, 34.dp)
                    Spacer(Modifier.width(7.dp))
                    Text("12", color = gold, fontFamily = num, fontSize = 23.sp)
                }
            }
        }
    }
}
