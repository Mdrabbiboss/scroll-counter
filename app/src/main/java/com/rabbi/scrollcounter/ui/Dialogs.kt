package com.rabbi.scrollcounter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val QuickLimits = listOf(200, 300, 500, 800, 1000)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LimitDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by remember { mutableFloatStateOf(current.toFloat()) }
    val v = value.roundToInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily scroll limit") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$v", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                Text("scrolls per day", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = value,
                    onValueChange = { value = ((it / 50f).roundToInt() * 50).toFloat() },
                    valueRange = 100f..2000f,
                    steps = 37,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickLimits.forEach { q ->
                        FilterChip(selected = v == q, onClick = { value = q.toFloat() }, label = { Text("$q") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "You'll get a heads-up at 80% and an alert when you pass it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v) }) { Text("Save", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

const val DISCLOSURE_TEXT =
    "Scroll Counter uses Android's Accessibility Service only to notice when you scroll in the " +
        "social apps you choose (Facebook, Instagram, TikTok, YouTube and others).\n\n" +
        "• It does NOT read text, messages, photos or anything shown on your screen\n" +
        "• It does nothing in any other app\n" +
        "• Your scroll data never leaves your phone\n\n" +
        "On the next screen, tap \"Scroll Counter\" and switch it on."

/** Prominent disclosure shown before sending the user to Accessibility settings (Google Play policy). */
@Composable
fun DisclosureDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How tracking works") },
        text = { Text(DISCLOSURE_TEXT) },
        confirmButton = { TextButton(onClick = onAgree) { Text("Agree & continue", fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}

const val PRIVACY_POLICY_TEXT =
    "What we collect\n" +
        "Only the number of times you scroll, an estimated scroll distance, and the hour it happened, " +
        "for the social apps you turned on. Nothing about what you looked at.\n\n" +
        "Where it is stored\n" +
        "On your phone only. Scroll Counter has no account, no server and no analytics. History older " +
        "than 60 days is deleted automatically, and you can delete everything in Settings.\n\n" +
        "Accessibility Service\n" +
        "Used only to detect scroll events in the selected social apps. It cannot read screen content " +
        "(the service is configured without window-content access).\n\n" +
        "Ads\n" +
        "The app shows Google AdMob banner ads. Google may use your device's advertising ID to show ads, " +
        "and asks for your consent where the law requires it. You can change ad choices in Settings.\n\n" +
        "Export\n" +
        "Export creates a CSV of your stats and hands it to the app you choose. Nothing is sent automatically."

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy policy") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(PRIVACY_POLICY_TEXT, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
