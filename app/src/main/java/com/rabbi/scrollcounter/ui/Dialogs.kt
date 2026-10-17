package com.rabbi.scrollcounter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.ui.unit.sp
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

private val QuickLimits = listOf(50, 100, 200, 300, 400, 500)
private const val MIN_LIMIT = 10
private const val MAX_LIMIT = com.rabbi.scrollcounter.data.ScrollStore.MAX_LIMIT

/**
 * Premium limit editor: a frosted dark sheet with a big number you can type into,
 * −/+ buttons, a slider and quick presets. Shows a clear message for out-of-range values.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LimitDialog(current: Int, locked: Boolean = false, onDismiss: () -> Unit, onSquatUnlock: (() -> Unit)? = null, onSave: (Int) -> Unit) {
    if (locked) { LockedLimitSheet(current, onDismiss, onSquatUnlock); return }
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
        containerColor = Color(0xFF071012),
        contentColor = Color.White,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f)))
        }
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF0B2A2B), Color(0xFF071012), Color(0xFF05080A))))
                .navigationBarsPadding()
                .imePadding()
        ) {
        Column(
            Modifier.fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(tr("Set your daily reel limit"), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(
                tr("Now: %1\$s reels", current) + if (valid && value != current) tr("  →  New: %1\$s reels", value) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (valid && value != current) Brand.Gold else Color.White.copy(alpha = 0.65f),
                fontWeight = FontWeight.SemiBold
            )
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
                            if (valid) Brand.Gold.copy(alpha = 0.7f) else Brand.Over,
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
                        cursorBrush = SolidColor(Brand.Gold),
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
                    value == null -> tr("Type a number")
                    value < MIN_LIMIT -> tr("Minimum is %1\$s reels", MIN_LIMIT)
                    value > MAX_LIMIT -> tr("Maximum is %1\$s reels", MAX_LIMIT)
                    else -> tr("reels per day  ·  tap the number to type")
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
                    activeTrackColor = Brand.Gold,
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
                            .background(if (on) Brand.Gold else Color.White.copy(alpha = 0.08f))
                            .border(1.dp, if (on) Brand.Gold else Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                            .clickable { set(q); focus.clearFocus() }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text("$q", color = if (on) Brand.GoldDeep else Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                if (valid) tr("Heads-up at %1\$s · reels close at %2\$s", (value!! * 0.8f).toInt(), value)
                else tr("You'll get a heads-up at 80% of your limit"),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
            // Buttons stay pinned at the bottom so they're always visible, even with the keyboard open.
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                        .background(
                            if (valid) Brush.linearGradient(listOf(Color(0xFFFFE6A3), Color(0xFFFFC857), Color(0xFFFF9F43)))
                            else SolidColor(Color.White.copy(alpha = 0.12f))
                        )
                        .clickable(enabled = valid) { if (value == current) onDismiss() else onSave(value!!) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Check, null, tint = if (valid) Color(0xFF4A2A00) else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (valid && value != current) tr("Save & lock for today") else tr("Save limit"),
                            color = if (valid) Color(0xFF4A2A00) else Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black
                        )
                    }
                }
                Text(
                    tr("You can change your limit only once a day. After saving, it stays until tomorrow."),
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Cancel"), color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
    }
}

/** Shown when the limit was already changed today: read-only, unlocks tomorrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockedLimitSheet(current: Int, onDismiss: () -> Unit, onSquatUnlock: (() -> Unit)?) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF071012),
        contentColor = Color.White,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f)))
        }
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF0B2A2B), Color(0xFF071012), Color(0xFF05080A))))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Brand.Gold.copy(alpha = 0.16f))
                    .border(1.dp, Brand.Gold.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Lock, null, tint = Brand.Gold, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.height(14.dp))
            Text(tr("Today's limit is locked"), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.width(170.dp).clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(24.dp))
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("$current", style = MaterialTheme.typography.displayMedium, color = Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(6.dp))
            Text(tr("reels per day"), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(18.dp))
            // Live countdown until the limit can be changed again (midnight).
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brand.Gold.copy(alpha = 0.10f))
                    .border(1.dp, Brand.Gold.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(tr("YOU CAN EDIT AGAIN IN"), style = MaterialTheme.typography.labelSmall, color = Brand.Gold.copy(alpha = 0.85f), letterSpacing = 1.4.sp)
                Spacer(Modifier.height(2.dp))
                UnlockCountdown(MaterialTheme.typography.headlineMedium, Color.White, weight = FontWeight.Black)
                Text(tr("hours : minutes : seconds"), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                if (onSquatUnlock != null) tr("You already set your limit today. Stick with it! It unlocks at midnight — or earn one more change with 10 squats.")
                else tr("You already set your limit today and used today's squat unlock. Stick with it! It unlocks at midnight."),
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            if (onSquatUnlock != null) {
                // Earn it: 10 real squats in front of the camera unlock one more change today.
                Row(
                    Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFFFE6A3), Color(0xFFFFC857), Color(0xFFFF9F43))))
                        .clickable(onClick = onSquatUnlock),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.FitnessCenter, null, tint = Color(0xFF4A2A00), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(tr("Do 10 squats to unlock"), color = Color(0xFF4A2A00), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(4.dp))
                Text(tr("Uses the camera to count — nothing is recorded"), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Got it"), color = Color.White.copy(alpha = 0.8f))
                }
            } else Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFFE6A3), Color(0xFFFFC857), Color(0xFFFF9F43))))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(tr("Got it"), color = Color(0xFF4A2A00), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel")) } }
    )
}

/**
 * Deleting all history needs a deliberate step: a strong warning and a small sum to solve,
 * so it can't happen by an accidental tap.
 */
@Composable
fun DeleteHistoryDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = tr("Delete all history?"),
    body: String = tr("This permanently removes from this phone:\n• Every past day's reel count and distance (today's count stays)\n• Your hourly activity and app breakdown\n• Your streak (it starts again from zero)\n\nThis can't be undone."),
    confirmLabel: String = tr("Delete everything"),
    dismissLabel: String = tr("Keep my history"),
) {
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
        title = { Text(title, textAlign = TextAlign.Center) },
        text = {
            Column {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(danger.copy(alpha = 0.10f))
                        .border(1.dp, danger.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(tr("Solve this to confirm"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(tr("Not quite — try again"), style = MaterialTheme.typography.bodySmall, color = danger)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = correct,
                colors = ButtonDefaults.buttonColors(containerColor = danger, contentColor = Color.White)
            ) { Text(confirmLabel, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}

val DISCLOSURE_TEXT: String get() =
    tr("Scroll Counter uses Android's Accessibility Service to count the Reels, Shorts and short videos you watch in any social or video app (Facebook, Instagram, TikTok, YouTube, Snapchat and others) and on short-video websites in your browser.\n\n• To recognise a Reels feed it looks only at the screen's layout — the size of the video player, its view names and how it scrolls\n• In browsers it checks the address bar only to tell when a short-video page (TikTok, Shorts, Reels) is open — never stored\n• It does NOT read or store messages, captions, comments or anything you type\n• It shows a small counter at the top while you watch, counts your watch time, and closes Reels when your daily limit is reached\n• Chats, comments, normal feeds and reading are ignored, and your data never leaves your phone\n\nOn the next screen, tap \"Scroll Counter\" and switch it on.")

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
            Text(tr("Turn on scroll tracking"), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(tr("Watch how — it takes 10 seconds:"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            TutorialVideo(com.rabbi.scrollcounter.R.raw.accessibility_tutorial, aspect = 480f / 1104f, modifier = Modifier.fillMaxWidth(0.55f))
            Spacer(Modifier.height(16.dp))
            Text(tr("How tracking works"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(DISCLOSURE_TEXT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAgree,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text(tr("Agree & open settings"), style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(tr("Not now")) }
        }
    }
}

val PRIVACY_POLICY_TEXT: String get() =
    tr("What we collect\nOnly how many Reels / Shorts you watch, how long you watch them, an estimated scroll distance, and the hour it happened, per app (and per browser for short-video websites). Nothing about what you watched; web addresses are never stored.\n\nWhere it is stored\nOn your phone only. Scroll Counter has no account, no server and no analytics. History older than 60 days is deleted automatically, and you can delete everything in Settings.\n\nAccessibility Service\nUsed only to recognise when a Reels / Shorts feed is on screen and when you move to the next video. It looks at the screen's layout (sizes, view names and how it scrolls) only; in browsers it also checks the address bar to recognise short-video pages, without storing it. It does not read, store or send messages, captions or anything you type.\n\nAds\nThe app shows Google AdMob banner ads. Google may use your device's advertising ID to show ads, and asks for your consent where the law requires it. You can change ad choices in Settings.\n\nHistory backup\nSo clearing the app's data or reinstalling can't erase your counts, a small backup file of the numbers only (date, app, reel count, watch time, distance, hour, streak badges) is kept in a hidden \".ScrollCounter\" folder on your phone's storage, and a copy in Google Play services' Block Store. It never contains what you watched and is never uploaded by the app.")

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Privacy policy")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(PRIVACY_POLICY_TEXT + "\n\n" + tr("Camera\nUsed only when you choose \"Do 10 squats to unlock\", to count your squats with on-device pose detection. No photo or video is recorded, saved or sent."), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Close")) } }
    )
}
