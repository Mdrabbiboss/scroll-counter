package com.rabbi.scrollcounter.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rabbi.scrollcounter.data.Badges
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.face.BadgeArt
import com.rabbi.scrollcounter.share.ShareCard
import com.rabbi.scrollcounter.ui.theme.Brand
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One streak medal (drawn in code). */
@Composable
fun BadgeMedal(badge: Badges.Badge, unlocked: Boolean, size: Dp, modifier: Modifier = Modifier, glow: Boolean = unlocked) {
    Canvas(modifier.size(size)) {
        drawIntoCanvas { BadgeArt.draw(it.nativeCanvas, badge, 0f, 0f, this.size.minDimension, unlocked, glow) }
    }
}

private val BadgeDateFmt: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM yyyy")

/** You screen: the row of streak medals, progress to the next one, and a share button. */
@Composable
fun BadgesCard(store: ScrollStore, tick: Long, onOpen: () -> Unit, onShare: () -> Unit) {
    val dates = remember(tick) { store.badgeDates() }
    val streak = remember(tick) { store.streak() }
    val best = remember(tick) { maxOf(store.bestStreak, streak) }
    val next = Badges.next(best)
    PremiumCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Streak badges"), style = MaterialTheme.typography.titleMedium)
                Text(
                    tr("%1\$s of %2\$s earned", dates.size, Badges.ALL.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Badges.ALL.forEach { b -> BadgeMedal(b, b.days in dates, 42.dp, glow = false) }
        }
        Spacer(Modifier.height(14.dp))
        if (next != null) {
            val left = (next.days - streak).coerceAtLeast(1)
            Text(
                tr("Next: %1\$s  ·  ", tr(next.title)) + if (left == 1) tr("1 more day") else tr("%1\$s more days", left),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            val frac = (streak.toFloat() / next.days).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))) {
                Box(
                    Modifier.fillMaxWidth(frac.coerceAtLeast(0.03f)).height(8.dp).clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFFE6A3), Brand.Gold, Color(0xFFFF9F43))))
                )
            }
        } else {
            Text(tr("Every badge earned. You're a legend!"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))
        GoldButton(tr("Share my progress"), onShare)
    }
}

@Composable
private fun GoldButton(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFFE6A3), Color(0xFFFFC857), Color(0xFFFF9F43))))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Share, null, tint = Color(0xFF4A2A00), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color(0xFF4A2A00), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
    }
}

/** All badges with their dates / days to go. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesSheet(store: ScrollStore, tick: Long, onShareBadge: (Badges.Badge) -> Unit, onDismiss: () -> Unit) {
    val dates = remember(tick) { store.badgeDates() }
    val streak = remember(tick) { store.streak() }
    val best = remember(tick) { maxOf(store.bestStreak, streak) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF071012), contentColor = Color.White,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF0B2A2B), Color(0xFF071012))))
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp).padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(tr("Streak badges"), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(
                tr("Current streak: ") + (if (streak == 1) tr("1 day") else tr("%1\$s days", streak)) + tr("  ·  Best: ") + (if (best == 1) tr("1 day") else tr("%1\$s days", best)),
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tr("Stay under your daily limit every day to earn them. Badges are yours to keep."),
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Badges.ALL.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { b ->
                        val date = dates[b.days]
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                                .background(Color.White.copy(alpha = if (date != null) 0.09f else 0.04f))
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                                .clickable(enabled = date != null) { onShareBadge(b) }
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            BadgeMedal(b, date != null, 92.dp)
                            Text(tr(b.title), style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (date != null) tr("Unlocked ") + LocalDate.parse(date).format(BadgeDateFmt)
                                else { val l = (b.days - streak).coerceAtLeast(1); if (l == 1) tr("1 more day") else tr("%1\$s more days", l) },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (date != null) Brand.Gold else Color.White.copy(alpha = 0.55f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(tr("Tap an earned badge to share it."), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.5f))
        }
    }
}

/** Big "badge unlocked" celebration: rays spin, the medal pops in. */
@Composable
fun BadgeCelebration(badge: Badges.Badge, onShare: () -> Unit, onDismiss: () -> Unit) {
    val pop = remember { Animatable(0.2f) }
    LaunchedEffect(badge) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
    val spin = rememberInfiniteTransition(label = "rays")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    val t = badge.tier
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                Text(tr("BADGE UNLOCKED"), style = MaterialTheme.typography.labelLarge, color = Color(t.light), letterSpacing = 3.sp)
                Spacer(Modifier.height(10.dp))
                Box(contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(300.dp).rotate(angle)) {
                        val cx = size.width / 2; val cy = size.height / 2
                        for (i in 0 until 16) {
                            rotate(i * 22.5f, androidx.compose.ui.geometry.Offset(cx, cy)) {
                                drawPath(
                                    androidx.compose.ui.graphics.Path().apply {
                                        moveTo(cx, cy); lineTo(cx - 18f, 0f); lineTo(cx + 18f, 0f); close()
                                    },
                                    Brush.verticalGradient(listOf(Color(t.light).copy(alpha = 0f), Color(t.light).copy(alpha = 0.28f)), startY = 0f, endY = cy)
                                )
                            }
                        }
                    }
                    BadgeMedal(badge, true, 220.dp, Modifier.scale(pop.value))
                }
                Text(tr(badge.title).uppercase(), style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(tr(badge.description), style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                GoldButton(tr("Share"), onShare)
                TextButton(onClick = onDismiss) { Text(tr("Nice!"), color = Color.White.copy(alpha = 0.85f)) }
            }
        }
    }
}

/** Preview of the share image with a big Share button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePreviewSheet(bitmap: Bitmap, message: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF071012), contentColor = Color.White,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(tr("Share your progress"), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(14.dp))
            Image(
                bitmap.asImageBitmap(), null,
                modifier = Modifier.fillMaxWidth(0.78f).aspectRatio(ShareCard.W.toFloat() / ShareCard.H)
                    .clip(RoundedCornerShape(22.dp)).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(22.dp))
            )
            Spacer(Modifier.height(18.dp))
            GoldButton(tr("Share")) { ShareCard.share(context, bitmap, message); onDismiss() }
            TextButton(onClick = onDismiss) { Text(tr("Cancel"), color = Color.White.copy(alpha = 0.75f)) }
        }
    }
}
