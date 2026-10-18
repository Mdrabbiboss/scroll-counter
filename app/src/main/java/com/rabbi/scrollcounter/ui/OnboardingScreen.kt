package com.rabbi.scrollcounter.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rabbi.scrollcounter.tracker.DeviceStatus
import com.rabbi.scrollcounter.ui.theme.Brand

/**
 * First-run setup: welcome → accessibility (with disclosure) → notifications → history backup (and restore) → battery.
 * Each step advances automatically once the user has granted it in system settings.
 */
@Composable
fun OnboardingScreen(
    status: DeviceStatus,
    onOpenAccessibility: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenStorage: () -> Unit,
    onRestoreBackup: () -> Unit = {},
    language: Lang = Lang.EN,
    onPickLanguage: () -> Unit = {},
    onOpenBattery: () -> Unit,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }

    // Every permission is required. Each step moves on by itself once it's granted, and the
    // notification / battery prompts open automatically when their step appears.
    var batteryTries by rememberSaveable { mutableIntStateOf(0) }
    var autoAsked by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(status, step) {
        if (step == 1 && status.trackerOn) { kotlinx.coroutines.delay(500); step = 2; return@LaunchedEffect }
        if (step == 2 && status.notificationsOn) { kotlinx.coroutines.delay(500); step = 3; return@LaunchedEffect }
        // Android 10+ needs no permission here, so the step stays to offer "Restore from backup file";
        // it only moves on by itself right after an older phone grants storage access.
        if (step == 3 && status.historySafe && autoAsked == 3) { kotlinx.coroutines.delay(500); step = 4; return@LaunchedEffect }
        if (step == 4 && status.batteryUnrestricted) return@LaunchedEffect // wait for the user to tap Finish
        if (autoAsked != step) {
            when (step) {
                2 -> if (!status.notificationsOn) { autoAsked = step; kotlinx.coroutines.delay(700); onRequestNotifications() }
                3 -> if (!status.historySafe) { autoAsked = step; kotlinx.coroutines.delay(700); onOpenStorage() }
                4 -> if (!status.batteryUnrestricted) { autoAsked = step; kotlinx.coroutines.delay(700); batteryTries++; onOpenBattery() }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().glassBackdrop(isDarkTheme()).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Language first — right at the top of the very first screen.
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.End) {
            LanguageChip(language, onPickLanguage)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(5) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50))
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }

        AnimatedContent(targetState = step, label = "step", modifier = Modifier.weight(1f)) { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (s) {
                    0 -> Welcome()
                    1 -> {
                        StepHero(Icons.Rounded.AccessibilityNew, MaterialTheme.colorScheme.primary,
                            tr("Allow scroll tracking"), tr("Counts each reel you swipe. Takes 10 seconds."), status.trackerOn)
                        if (!status.trackerOn) { Spacer(Modifier.height(22.dp)); TrackingDisclosure() }
                    }
                    2 -> StepHero(Icons.Rounded.NotificationsActive, Brand.Warn,
                        tr("Get a heads-up"), tr("A nudge at 80% and when you hit your limit."), status.notificationsOn)
                    3 -> StepHero(Icons.Rounded.Lock, Brand.Gold,
                        tr("Keep your history safe"),
                        if (status.historySafe) tr("Your counts survive reinstalls and Clear data.") else tr("Allow storage so your counts survive reinstalls."),
                        status.historySafe)
                    else -> StepHero(Icons.Rounded.BatteryChargingFull, Brand.Good,
                        tr("Keep counting reliably"), tr("Set battery to Unrestricted so counting never stops."), status.batteryUnrestricted)
                }
            }
        }

        // Buttons — no skipping: every permission must be given to reach the end.
        when (step) {
            0 -> PrimaryButton(tr("Get started")) { step = 1 }
            1 -> {
                if (status.trackerOn) PrimaryButton(tr("Continue")) { step = 2 }
                else {
                    PrimaryButton(tr("Agree & open settings"), onOpenAccessibility)
                    RequiredNote()
                }
            }
            2 -> {
                if (status.notificationsOn) PrimaryButton(tr("Continue")) { step = 3 }
                else {
                    PrimaryButton(tr("Allow notifications"), onRequestNotifications)
                    RequiredNote()
                }
            }
            3 -> {
                if (status.historySafe) {
                    PrimaryButton(tr("Continue")) { step = 4 }
                    SecondaryButton(tr("Restore from backup file"), onRestoreBackup)
                } else {
                    PrimaryButton(tr("Allow history backup"), onOpenStorage)
                    RequiredNote()
                }
            }
            else -> {
                if (status.batteryUnrestricted) PrimaryButton(tr("Finish"), onFinish)
                else {
                    PrimaryButton(tr("Allow unrestricted battery")) { batteryTries++; onOpenBattery() }
                    RequiredNote()
                    // A few phones hide this option entirely; don't trap their owners forever.
                    if (batteryTries >= 3) SecondaryButton(tr("My phone doesn't have this option"), onFinish)
                }
            }
        }
    }
}

@Composable
private fun Welcome() {
    WelcomeShowcase(Modifier.fillMaxWidth())
    Spacer(Modifier.height(18.dp))
    Text(tr("See your reels count"), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        tr("Every Reel, Short and TikTok you swipe — counted live, with a little buddy that gets tired as you go."),
        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))
    SupportedAppsRow()
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The step's picture: a glowing, gently floating tile with pulsing rings; turns into a green tick when done. */
@Composable
private fun StepHero(icon: ImageVector, accent: Color, title: String, subtitle: String, done: Boolean) {
    val inf = rememberInfiniteTransition(label = "hero")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "pulse")
    val float by inf.animateFloat(-5f, 5f, infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    val tint by animateColorAsState(if (done) Brand.Good else accent, tween(500), label = "tint")
    val shape = RoundedCornerShape(34.dp)
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.34f), Color.Transparent), center, r), r, center)
            for (k in 0..1) {
                val p = (pulse + k * 0.5f) % 1f
                drawCircle(tint.copy(alpha = 0.45f * (1f - p)), r * (0.40f + 0.55f * p), center, style = Stroke(1.5.dp.toPx()))
            }
        }
        Box(
            Modifier.offset(y = float.dp).size(108.dp)
                .shadow(28.dp, shape, ambientColor = tint, spotColor = tint)
                .clip(shape)
                .background(Brush.linearGradient(listOf(lerp(tint, Color.White, 0.28f), tint, lerp(tint, Color.Black, 0.30f))))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.05f))), shape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(done, label = "icon") { d ->
                Icon(if (d) Icons.Rounded.Check else icon, null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    if (done) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Brand.Good.copy(alpha = 0.16f)).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Brand.Good, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(tr("Done"), style = MaterialTheme.typography.titleSmall, color = Brand.Good)
        }
    } else Text(
        subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
private fun RequiredNote() {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            tr("Required — you can't continue without it"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.fillMaxWidth().height(56.dp)
            .shadow(18.dp, shape, ambientColor = primary, spotColor = primary)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(lerp(primary, Color.White, 0.35f), primary)))
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, style = MaterialTheme.typography.titleMedium, color = Color(0xFF032B28), fontWeight = FontWeight.Bold) }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text(text) }
}
