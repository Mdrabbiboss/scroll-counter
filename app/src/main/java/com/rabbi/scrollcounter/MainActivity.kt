package com.rabbi.scrollcounter

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
        setContent { AppRoot(this, store) }
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
        onPauseOrDispose { }
    }

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
    val todayTotal = remember(tick) { store.todayTotal() }
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
                    onOpenAccessibility = { TrackerStatus.openAccessibilitySettings(context) },
                    onRequestNotifications = requestNotifications,
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
                                        if (t == Tab.Today) Icon(
                                            androidx.compose.ui.res.painterResource(com.rabbi.scrollcounter.R.drawable.ic_buddy),
                                            contentDescription = t.label, modifier = Modifier.size(26.dp)
                                        ) else Icon(t.icon, contentDescription = t.label)
                                    },
                                    label = { Text(t.label) },
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
                            Tab.Today -> TodayScreen(store, tick, status, { showDisclosure = true }, { showLimit = true }, onAddWidget = addWidget)
                            Tab.History -> HistoryScreen(store, tick)
                            Tab.Settings -> SettingsScreen(
                                store, tick, status,
                                onEnableTracking = { showDisclosure = true },
                                onEditLimit = { showLimit = true },
                                onRequestNotifications = requestNotifications,
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
                title = { Text("Add the widget") },
                text = {
                    Text(
                        "Your launcher doesn't support one-tap adding, so do it by hand:\n\n" +
                            "1. Go to your home screen\n" +
                            "2. Touch and hold an empty spot\n" +
                            "3. Tap \"Widgets\"\n" +
                            "4. Find \"Scroll Counter\", then drag it onto the home screen"
                    )
                },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { showWidgetHelp = false }) { Text("Got it") } }
            )
        }

        if (showDisclosure) {
            DisclosureDialog(
                onAgree = { showDisclosure = false; TrackerStatus.openAccessibilitySettings(context) },
                onDismiss = { showDisclosure = false }
            )
        }
        if (showLimit) {
            LimitDialog(
                current = store.dailyLimit,
                onDismiss = { showLimit = false },
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
