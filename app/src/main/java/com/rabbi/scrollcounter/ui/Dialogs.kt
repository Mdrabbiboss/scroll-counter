package com.rabbi.scrollcounter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.rabbi.scrollcounter.ui.theme.Brand
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

private val QuickLimits = listOf(10, 50, 100, 200, 300, 500, 1000)
private const val MIN_LIMIT = 10
private const val MAX_LIMIT = 2000

/**
 * Premium limit editor: a frosted dark sheet with a big number you can type into,
 * −/+ buttons, a slider and quick presets. Shows a clear message for out-of-range values.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LimitDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in MIN_LIMIT..MAX_LIMIT
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun set(v: Int) {
        text = v.coerceIn(MIN_LIMIT, MAX_LIMIT).toString()
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Brand.HeroTop,
        contentColor = Color.White,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f)))
        }
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Brand.HeroTop, Brand.HeroBottom)))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("DAILY SCROLL LIMIT", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(18.dp))

            // − [ number ] +
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−") { set((value ?: current) - if ((value ?: current) <= 200) 10 else 50) }
                Spacer(Modifier.width(14.dp))
                Box(
                    Modifier.width(170.dp).clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(
                            1.5.dp,
                            if (valid) Brand.Glow.copy(alpha = 0.6f) else Brand.Over,
                            RoundedCornerShape(24.dp)
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { new -> text = new.filter { it.isDigit() }.take(4) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.displayMedium.copy(
                            color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
                        ),
                        cursorBrush = SolidColor(Brand.Glow),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(14.dp))
                StepButton("+") { set((value ?: current) + if ((value ?: current) < 200) 10 else 50) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    value == null -> "Type a number"
                    value < MIN_LIMIT -> "Minimum is $MIN_LIMIT reels"
                    value > MAX_LIMIT -> "Maximum is $MAX_LIMIT reels"
                    else -> "reels per day  ·  tap the number to type"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (valid) Color.White.copy(alpha = 0.65f) else Brand.Over
            )

            Spacer(Modifier.height(18.dp))
            Slider(
                value = (value ?: current).coerceIn(MIN_LIMIT, MAX_LIMIT).toFloat(),
                onValueChange = {
                    val v = if (it < 200f) (it / 10f).roundToInt() * 10 else (it / 50f).roundToInt() * 50
                    text = v.coerceIn(MIN_LIMIT, MAX_LIMIT).toString()
                },
                valueRange = MIN_LIMIT.toFloat()..MAX_LIMIT.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Brand.Glow,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth()) {
                Text("$MIN_LIMIT", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
                Text("$MAX_LIMIT", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            }

            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickLimits.forEach { q ->
                    val on = value == q
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(if (on) Brand.Glow else Color.White.copy(alpha = 0.08f))
                            .border(1.dp, if (on) Brand.Glow else Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                            .clickable { set(q); focus.clearFocus() }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text("$q", color = if (on) Brand.HeroTop else Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                if (valid) "Heads-up at ${(value!! * 0.8f).toInt()} · apps close at $value"
                else "You'll get a heads-up at 80% of your limit",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { if (valid) onSave(value!!) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Brand.HeroTop,
                    disabledContainerColor = Color.White.copy(alpha = 0.2f),
                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                )
            ) { Text("Save limit", style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White, style = MaterialTheme.typography.headlineMedium) }
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

/**
 * Deleting all history needs a deliberate step: a strong warning and a small sum to solve,
 * so it can't happen by an accidental tap.
 */
@Composable
fun DeleteHistoryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val a = remember { (3..19).random() }
    val b = remember { (2..15).random() }
    val plus = remember { listOf(true, false).random() || a <= b }
    val answer = if (plus) a + b else a - b
    var input by remember { mutableStateOf("") }
    val correct = input.toIntOrNull() == answer
    val wrong = input.isNotEmpty() && input.length >= answer.toString().length && !correct
    val danger = MaterialTheme.colorScheme.error

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(danger.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.WarningAmber, null, tint = danger, modifier = Modifier.size(30.dp)) }
        },
        title = { Text("Delete all history?", textAlign = TextAlign.Center) },
        text = {
            Column {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(danger.copy(alpha = 0.10f))
                        .border(1.dp, danger.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        "This permanently removes from this phone:\n" +
                            "• Every day's scroll count and distance\n" +
                            "• Your hourly activity and app breakdown\n" +
                            "• Your streak (it starts again from zero)\n\n" +
                            "This can't be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("Solve this to confirm", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$a ${if (plus) "+" else "−"} $b  =",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { v -> input = v.filter { it.isDigit() || it == '-' }.take(4) },
                        singleLine = true,
                        isError = wrong,
                        placeholder = { Text("?") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.width(96.dp)
                    )
                }
                if (wrong) {
                    Spacer(Modifier.height(6.dp))
                    Text("Not quite — try again", style = MaterialTheme.typography.bodySmall, color = danger)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = correct,
                colors = ButtonDefaults.buttonColors(containerColor = danger, contentColor = Color.White)
            ) { Text("Delete everything", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep my history") } }
    )
}

const val DISCLOSURE_TEXT =
    "Scroll Counter uses Android's Accessibility Service to count the Reels, Shorts and short videos you watch " +
        "in any social or video app (Facebook, Instagram, TikTok, YouTube, Snapchat and others) and on short-video websites in your browser.\n\n" +
        "• To recognise a Reels feed it looks only at the screen's layout — the size of the video player, its view names and how it scrolls\n" +
        "• It does NOT read or store text, web addresses, messages, captions, comments or anything you type\n" +
        "• It shows a small counter at the top while you watch, and takes you to the home screen when your daily limit is reached\n" +
        "• Chats, comments, normal feeds and reading are ignored, and your data never leaves your phone\n\n" +
        "On the next screen, tap \"Scroll Counter\" and switch it on."

/** Prominent disclosure + animated how-to, shown before sending the user to Accessibility settings (Google Play policy). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclosureDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Turn on scroll tracking", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("Here's exactly what to tap on the next screen:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            AccessibilityGuide(Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text("How tracking works", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(DISCLOSURE_TEXT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAgree,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text("Agree & open settings", style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
        }
    }
}

const val PRIVACY_POLICY_TEXT =
    "What we collect\n" +
        "Only how many Reels / Shorts you watch, an estimated scroll distance, and the hour it happened, " +
        "per app (and per browser for short-video websites). Nothing about what you watched, and no web addresses.\n\n" +
        "Where it is stored\n" +
        "On your phone only. Scroll Counter has no account, no server and no analytics. History older " +
        "than 60 days is deleted automatically, and you can delete everything in Settings.\n\n" +
        "Accessibility Service\n" +
        "Used only to recognise when a Reels / Shorts feed is on screen and when you move to the next video. " +
        "It looks at the screen's layout (sizes, view names and how it scrolls) only; it does not read, store or send text, web addresses, " +
        "messages, captions or anything you type.\n\n" +
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
