package com.rabbi.scrollcounter.ui

import androidx.compose.animation.AnimatedContent
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
 * First-run setup: welcome → accessibility (with disclosure) → notifications → battery.
 * Each step advances automatically once the user has granted it in system settings.
 */
@Composable
fun OnboardingScreen(
    status: DeviceStatus,
    onOpenAccessibility: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBattery: () -> Unit,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }

    // Move on by itself when the user comes back from settings with the step done.
    LaunchedEffect(status, step) {
        if (step == 1 && status.trackerOn) step = 2
        if (step == 2 && status.notificationsOn) step = 3
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { i ->
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
                    1 -> StepBody(
                        Icons.Rounded.AccessibilityNew, MaterialTheme.colorScheme.primary,
                        "Allow scroll tracking",
                        DISCLOSURE_TEXT,
                        done = status.trackerOn,
                        badge = "REQUIRED"
                    )
                    2 -> StepBody(
                        Icons.Rounded.NotificationsActive, Brand.Warn,
                        "Get a heads-up",
                        "Scroll Counter can tell you when you've used 80% of your daily limit, and again when you pass it. Nothing else.",
                        done = status.notificationsOn,
                        badge = "RECOMMENDED"
                    )
                    else -> StepBody(
                        Icons.Rounded.BatteryChargingFull, Brand.Good,
                        "Keep counting reliably",
                        "Some phones (Xiaomi, Oppo, Vivo, Samsung and others) stop background services to save battery. " +
                            "Set Scroll Counter to \"Unrestricted\" / \"Don't optimize\" so your count stays accurate. It uses almost no battery.",
                        done = status.batteryUnrestricted,
                        badge = "RECOMMENDED"
                    )
                }
            }
        }

        // Buttons
        when (step) {
            0 -> PrimaryButton("Get started") { step = 1 }
            1 -> {
                // Required: the app can't count anything without it, so there is no skip.
                if (status.trackerOn) PrimaryButton("Continue") { step = 2 }
                else {
                    PrimaryButton("Agree & open settings", onOpenAccessibility)
                    RequiredNote()
                }
            }
            2 -> {
                if (status.notificationsOn) PrimaryButton("Continue") { step = 3 }
                else PrimaryButton("Allow notifications", onRequestNotifications)
                SecondaryButton("Not now") { step = 3 }
            }
            else -> {
                if (status.batteryUnrestricted) PrimaryButton("Finish", onFinish)
                else PrimaryButton("Open battery settings", onOpenBattery)
                if (!status.batteryUnrestricted) SecondaryButton("Finish", onFinish)
            }
        }
    }
}

@Composable
private fun Welcome() {
    Box(
        Modifier.size(104.dp).clip(RoundedCornerShape(32.dp)).background(Brand.hero),
        contentAlignment = Alignment.Center
    ) { Icon(Icons.Rounded.SwipeVertical, null, tint = Color.White, modifier = Modifier.size(52.dp)) }
    Spacer(Modifier.height(24.dp))
    Text("Know your scroll", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        "See how much you really scroll on social media, and set a daily limit that works for you.",
        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(28.dp))
    Feature(Icons.Rounded.SwipeVertical, "Every scroll, counted", "Facebook, Instagram, TikTok, YouTube, X and more")
    Feature(Icons.Rounded.Straighten, "Distance you scrolled", "In meters, and compared to real landmarks")
    Feature(Icons.Rounded.Timeline, "Daily limit & history", "Alerts, streaks and 30-day trends")
    Feature(Icons.Rounded.Lock, "Private by design", "Nothing on your screen is read. Data stays on your phone.")
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

@Composable
private fun StepBody(icon: ImageVector, tint: Color, title: String, text: String, done: Boolean, badge: String) {
    Box(
        Modifier.size(96.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) { Icon(if (done) Icons.Rounded.CheckCircle else icon, null, tint = if (done) Brand.Good else tint, modifier = Modifier.size(46.dp)) }
    Spacer(Modifier.height(20.dp))
    val required = badge == "REQUIRED"
    Text(
        badge,
        style = MaterialTheme.typography.labelMedium,
        color = if (required) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background((if (required) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
    Spacer(Modifier.height(10.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(12.dp))
    if (done) {
        Text("Done ✓", style = MaterialTheme.typography.titleMedium, color = Brand.Good, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RequiredNote() {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Required — Scroll Counter can't count scrolls without it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text(text) }
}
