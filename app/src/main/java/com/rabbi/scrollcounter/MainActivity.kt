package com.rabbi.scrollcounter

import com.rabbi.scrollcounter.ui.tr

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.animation.togetherWith
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.rabbi.scrollcounter.ads.AdsConsent
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.data.ThemeMode
import com.rabbi.scrollcounter.tracker.Notifier
import com.rabbi.scrollcounter.tracker.TrackerStatus
import com.rabbi.scrollcounter.ui.DisclosureDialog
import com.rabbi.scrollcounter.ui.BadgeCelebration
import com.rabbi.scrollcounter.ui.SharePreviewSheet
import com.rabbi.scrollcounter.ui.WidgetPromoScreen
import com.rabbi.scrollcounter.ui.HistoryScreen
import com.rabbi.scrollcounter.ui.LimitDialog
import com.rabbi.scrollcounter.ui.OnboardingScreen
import com.rabbi.scrollcounter.ui.SettingsScreen
import com.rabbi.scrollcounter.ui.TodayScreen
import com.rabbi.scrollcounter.ui.glassBackdrop
import com.rabbi.scrollcounter.ui.theme.ScrollCounterTheme

/** TEST ad unit. Replace with your own AdMob banner unit id before publishing. */
private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = ScrollStore.get(this)
        setContent {
            // Arabic and Urdu read right-to-left.
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides
                    if (com.rabbi.scrollcounter.ui.I18n.lang.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
            ) { AppRoot(this, store) }
        }
        // Housekeeping and ads start after the first frame, so they never delay opening the app.
        Thread {
            store.prune()
            store.firstDay // stamps the install day on first launch
            Notifier.ensureChannel(this)
        }.start()
        window.decorView.post { AdsConsent.gather(this) }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Today("You", Icons.Rounded.Home),
    History("History", Icons.Rounded.BarChart),
    Settings("Settings", Icons.Rounded.Settings),
}

@Composable
private fun AppRoot(activity: ComponentActivity, store: ScrollStore) {
    val context = LocalContext.current
    val tick by store.changes.collectAsStateWithLifecycle()

    val mode = remember(tick) { store.themeMode }
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) { ThemeMode.SYSTEM -> systemDark; ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }

    // Live device state, re-read every time the user returns to the app (e.g. from Settings).
    var status by remember { mutableStateOf(TrackerStatus.read(context)) }
    LifecycleResumeEffect(Unit) {
        status = TrackerStatus.read(context)
        // Back in the app (e.g. after allowing storage on an older phone): bring the saved history back.
        if (status.historySafe) store.restoreDurable()
        onPauseOrDispose { }
    }

    // Older phones (Android 10 and below) use the normal storage permission dialog.
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = TrackerStatus.read(context)
        if (status.historySafe) store.restoreDurable()
    }
    val requestStorage: () -> Unit = {
        // Android 10+ needs no permission for the backup file in Download/ScrollCounter.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    // "Restore from backup file": after a reinstall Android hides the old backup from the app,
    // so the user picks it once in the system file picker (opened right in Download/ScrollCounter).
    val restoreLauncher = rememberLauncherForActivityResult(PickBackupFile()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = store.restoreFromFile(uri)
        android.widget.Toast.makeText(
            context,
            if (ok) tr("History restored") else tr("That file isn't a Scroll Counter backup"),
            android.widget.Toast.LENGTH_LONG
        ).show()
    }
    val restoreBackup: () -> Unit = { runCatching { restoreLauncher.launch(Unit) } }

    // Notifications: ask once with the system dialog (Android 13+); after that, open app settings.
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = TrackerStatus.read(context)
    }
    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedNotifications) {
            askedNotifications = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            TrackerStatus.openNotificationSettings(context)
        }
    }

    var showDisclosure by remember { mutableStateOf(false) }
    // 10 squats done → reopen the limit editor, now unlocked for one more change.
    var showLimitAfterSquats by remember { mutableStateOf(false) }
    val squatLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) showLimitAfterSquats = true
    }

    // ---- Language: pick on the first screen or in Settings; the app redraws in it ----
    var showLanguages by remember { mutableStateOf(false) }
    val openLanguages: () -> Unit = { showLanguages = true }
    val pickLanguage: (com.rabbi.scrollcounter.ui.Lang) -> Unit = { l ->
        showLanguages = false
        if (l != store.language) {
            store.language = l
            com.rabbi.scrollcounter.widget.ScrollWidget.refreshAll(context)
            activity.recreate()
        }
    }

    // ---- Streak badges: celebrate new ones; share images of the day or a badge ----
    var celebrate by remember { mutableStateOf<com.rabbi.scrollcounter.data.Badges.Badge?>(null) }
    var sharing by remember { mutableStateOf<Pair<android.graphics.Bitmap, String>?>(null) }
    val shareDay: () -> Unit = {
        val t0 = store.todayTotal(); val l0 = store.dailyLimit
        sharing = com.rabbi.scrollcounter.share.ShareCard.renderDay(context) to
            com.rabbi.scrollcounter.ui.tr("I've watched %1\$s reels today (limit %2\$s). Tracking my reels with Scroll Counter.", t0, l0)
    }
    val shareBadge: (com.rabbi.scrollcounter.data.Badges.Badge) -> Unit = { b ->
        sharing = com.rabbi.scrollcounter.share.ShareCard.renderBadge(context, b) to
            com.rabbi.scrollcounter.ui.tr("I just earned the \"%1\$s\" badge: %2\$s! Tracking my reels with Scroll Counter.",
                com.rabbi.scrollcounter.ui.tr(b.title), com.rabbi.scrollcounter.ui.tr(b.description))
    }
    LaunchedEffect(tick, store.onboarded) {
        if (!store.onboarded || celebrate != null) return@LaunchedEffect
        val fresh = runCatching { store.updateBadges() }.getOrDefault(emptyList())
        fresh.maxByOrNull { it.days }?.let {
            celebrate = it
            if (store.buddySounds) com.rabbi.scrollcounter.face.BuddyVoice.speak(context, com.rabbi.scrollcounter.face.Mood.FRESH)
        }
    }
    var showLimit by remember { mutableStateOf(false) }
    val onboarded = remember(tick) { store.onboarded }

    // Home-screen widget: a full-screen showcase, then the system "Add to home screen" prompt
    // (or manual steps if the launcher can't pin widgets).
    var showWidgetHelp by remember { mutableStateOf(false) }
    var showWidgetPromo by remember { mutableStateOf(false) }
    var widgetPlaced by remember { mutableStateOf(false) }
    val addWidget: () -> Unit = { showWidgetPromo = true }
    // Offer it once automatically, right after setup (or on first open after updating).
    LaunchedEffect(onboarded) {
        if (onboarded && !store.widgetPrompted) {
            store.widgetPrompted = true
            kotlinx.coroutines.delay(350)
            showWidgetPromo = true
        }
    }
    // While the showcase is open, watch for the widget landing on the home screen.
    LaunchedEffect(showWidgetPromo) {
        widgetPlaced = false
        if (!showWidgetPromo) return@LaunchedEffect
        val before = com.rabbi.scrollcounter.widget.WidgetPin.count(context)
        while (!widgetPlaced) {
            kotlinx.coroutines.delay(600)
            if (com.rabbi.scrollcounter.widget.WidgetPin.count(context) > before) {
                widgetPlaced = true
                com.rabbi.scrollcounter.widget.ScrollWidget.refreshAll(context)
            }
        }
    }

    // First-run setup and the widget showcase are always shown in the premium dark look.
    val darkBars = dark || !onboarded || showWidgetPromo
    LaunchedEffect(darkBars) {
        val style = if (darkBars) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    // The whole app takes on today's mood colour, like the widget.
    val todayTotal = remember(tick) { store.todayUsed() }
    val limitNow = remember(tick) { store.dailyLimit }
    val accent by androidx.compose.animation.animateColorAsState(
        androidx.compose.ui.graphics.Color(
            com.rabbi.scrollcounter.widget.WidgetArt.glowColor(com.rabbi.scrollcounter.widget.WidgetState(todayTotal, limitNow))
        ),
        androidx.compose.animation.core.tween(900), label = "accent"
    )

    ScrollCounterTheme(dark) {
        if (!onboarded) {
            ScrollCounterTheme(true) {
                OnboardingScreen(
                    status = status,
                    onOpenAccessibility = { com.rabbi.scrollcounter.guide.SetupGuide.launch(context) },
                    onRequestNotifications = requestNotifications,
                    onOpenStorage = requestStorage,
                    onRestoreBackup = restoreBackup,
                    language = store.language,
                    onPickLanguage = openLanguages,
                    onOpenBattery = { TrackerStatus.openBatterySettings(context) },
                    onFinish = { store.onboarded = true }
                )
            }
        } else {
            var tab by rememberSaveable { mutableStateOf(Tab.Today) }
            val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
            Scaffold(
                modifier = Modifier.glassBackdrop(dark, accent),
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                bottomBar = {
                    Column {
                        if (AdsConsent.canRequestAds) BannerAd()
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = if (dark) androidx.compose.ui.graphics.Color(0xF0060A0B) else androidx.compose.ui.graphics.Color(0xE6FFFFFF),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            tonalElevation = 0.dp
                        ) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = {
                                        if (tab != t) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        tab = t
                                    },
                                    icon = {
                                        // "You": the living 3D buddy in today's mood — its eyes look around and blink.
                                        if (t == Tab.Today) com.rabbi.scrollcounter.ui.AnimatedFace(
                                            com.rabbi.scrollcounter.face.Mood.of(todayTotal, limitNow), 30.dp, bounceKey = todayTotal,
                                            modifier = Modifier.graphicsLayer { alpha = if (tab == t) 1f else 0.7f }
                                        ) else Icon(t.icon, contentDescription = com.rabbi.scrollcounter.ui.tr(t.label))
                                    },
                                    label = { Text(com.rabbi.scrollcounter.ui.tr(t.label)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = accent.copy(alpha = if (dark) 0.26f else 0.22f),
                                        selectedIconColor = if (dark) androidx.compose.ui.graphics.Color(0xFFFFD98A) else MaterialTheme.colorScheme.onSurface,
                                        selectedTextColor = if (dark) androidx.compose.ui.graphics.Color(0xFFFFD98A) else MaterialTheme.colorScheme.onSurface,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                // Each tab keeps its scroll position; switching slides + fades in the direction you moved.
                val tabStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                androidx.compose.animation.AnimatedContent(
                    targetState = tab,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        val enter = androidx.compose.animation.slideInHorizontally(
                            androidx.compose.animation.core.spring(dampingRatio = 0.88f, stiffness = 380f)
                        ) { it / 6 * dir } +
                            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 50)) +
                            androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(320), initialScale = 0.97f)
                        val exit = androidx.compose.animation.slideOutHorizontally(
                            androidx.compose.animation.core.tween(220)
                        ) { -it / 10 * dir } +
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) +
                            androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(220), targetScale = 0.985f)
                        (enter togetherWith exit).using(androidx.compose.animation.SizeTransform(clip = false))
                    },
                    label = "tabs"
                ) { t ->
                    tabStates.SaveableStateProvider(t.name) {
                        when (t) {
                            Tab.Today -> TodayScreen(
                                store, tick, status, { showDisclosure = true }, { showLimit = true }, onAddWidget = addWidget,
                                onShareDay = { shareDay() }, onShareBadge = { shareBadge(it) }
                            )
                            Tab.History -> HistoryScreen(store, tick)
                            Tab.Settings -> SettingsScreen(
                                store, tick, status,
                                onEnableTracking = { showDisclosure = true },
                                onEditLimit = { showLimit = true },
                                onRequestNotifications = requestNotifications,
                                onRequestStorage = requestStorage,
                                onRestoreBackup = restoreBackup,
                                onPickLanguage = openLanguages,
                                onAddWidget = addWidget
                            )
                        }
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = onboarded && showWidgetPromo,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(350)) +
                androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(400), initialScale = 1.04f),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(300))
        ) {
            WidgetPromoScreen(
                placed = widgetPlaced && showWidgetPromo,
                onAdd = {
                    if (!com.rabbi.scrollcounter.widget.WidgetPin.request(context)) showWidgetHelp = true
                },
                onClose = { showWidgetPromo = false; status = TrackerStatus.read(context) }
            )
        }

        if (showWidgetHelp) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showWidgetHelp = false },
                title = { Text(tr("Add the widget")) },
                text = {
                    Text(
                        tr("Your launcher doesn't support one-tap adding, so do it by hand:\n\n1. Go to your home screen\n2. Touch and hold an empty spot\n3. Tap \"Widgets\"\n4. Find \"Scroll Counter\", then drag it onto the home screen")
                    )
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { showWidgetHelp = false }) { Text(tr("Got it")) } }
            )
        }

        if (showDisclosure) {
            DisclosureDialog(
                onAgree = { showDisclosure = false; com.rabbi.scrollcounter.guide.SetupGuide.launch(context) },
                onDismiss = { showDisclosure = false }
            )
        }
        if (showLanguages) com.rabbi.scrollcounter.ui.LanguageSheet(store.language, pickLanguage) { showLanguages = false }
        celebrate?.let { b ->
            BadgeCelebration(b, onShare = { celebrate = null; shareBadge(b) }, onDismiss = { celebrate = null })
        }
        sharing?.let { (bmp, msg) -> SharePreviewSheet(bmp, msg) { sharing = null } }
        LaunchedEffect(showLimitAfterSquats) { if (showLimitAfterSquats) { showLimitAfterSquats = false; kotlinx.coroutines.delay(250); showLimit = true } }
        if (showLimit) {
            LimitDialog(
                current = store.dailyLimit,
                locked = store.limitLockedToday,
                freshStart = store.squatUnlockedNow,
                onDismiss = { showLimit = false },
                onSquatUnlock = if (!store.squatUnlockAvailable) null else ({
                    showLimit = false
                    squatLauncher.launch(android.content.Intent(context, com.rabbi.scrollcounter.squat.SquatUnlockActivity::class.java))
                }),
                onSave = {
                    store.dailyLimit = it
                    // A new, higher limit means today's alerts may fire again.
                    store.lastLimitAlert = null
                    store.lastWarnAlert = null
                    showLimit = false
                }
            )
        }
    }
}

@Composable
private fun BannerAd() {
    val widthDp = LocalConfiguration.current.screenWidthDp
    Box(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                    adUnitId = BANNER_AD_UNIT_ID
                    loadAd(AdRequest.Builder().build())
                }
            },
            onRelease = { it.destroy() }
        )
    }
}

/** System file picker for a backup file, opening in Download/ScrollCounter where the app saves it. */
private class PickBackupFile : androidx.activity.result.contract.ActivityResultContract<Unit, android.net.Uri?>() {
    override fun createIntent(context: android.content.Context, input: Unit) =
        android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(android.content.Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            .putExtra(
                android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                android.provider.DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:${android.os.Environment.DIRECTORY_DOWNLOADS}/${com.rabbi.scrollcounter.data.DurableStore.Vault.FOLDER}"
                )
            )

    override fun parseResult(resultCode: Int, intent: android.content.Intent?): android.net.Uri? =
        if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}
