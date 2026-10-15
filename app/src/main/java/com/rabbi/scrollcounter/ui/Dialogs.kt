package com.rabbi.scrollcounter.ui

import androidx.compose.foundation.layout.Arrangement
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
                    value < MIN_LIMIT -> "Minimum is $MIN_LIMIT scrolls"
                    value > MAX_LIMIT -> "Maximum is $MAX_LIMIT scrolls"
                    else -> "scrolls per day  ·  tap the number to type"
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

const val DISCLOSURE_TEXT =
    "Scroll Counter uses Android's Accessibility Service only to notice when you scroll in the " +
        "social apps you choose (Facebook, Instagram, TikTok, YouTube and others).\n\n" +
        "• It does NOT read text, messages, photos or anything shown on your screen\n" +
        "• It does nothing in any other app\n" +
        "• It shows a small check-in card every 5 scrolls, and takes you to the home screen once your daily limit is reached\n" +
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
