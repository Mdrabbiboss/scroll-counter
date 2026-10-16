package com.rabbi.scrollcounter.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rabbi.scrollcounter.MainActivity
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.face.FaceRenderer
import com.rabbi.scrollcounter.face.Mood
import com.rabbi.scrollcounter.ui.theme.Brand
import com.rabbi.scrollcounter.ui.theme.ScrollCounterTheme
import java.time.LocalDate

/** Full-screen "limit reached" page shown after the user is sent out of a social app. */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = ScrollStore.get(this)
        val appName = intent.getStringExtra(EXTRA_APP) ?: "social apps"
        setContent {
            ScrollCounterTheme(dark = true) {
                BlockScreen(
                    appName = appName,
                    total = store.todayTotal(),
                    limit = store.dailyLimit,
                    graceAvailable = store.graceAvailableToday,
                    onGoHome = { goHome() },
                    onFiveMore = {
                        store.graceDate = LocalDate.now().toString()
                        store.graceUntil = System.currentTimeMillis() + GRACE_MS
                        finish()
                    },
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                    }
                )
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_APP = "app"
        private const val GRACE_MS = 5 * 60 * 1000L
    }
}

@Composable
private fun BlockScreen(
    appName: String,
    total: Int,
    limit: Int,
    graceAvailable: Boolean,
    onGoHome: () -> Unit,
    onFiveMore: () -> Unit,
    onOpenApp: () -> Unit,
) {
    BackHandler { onGoHome() }
    Column(
        Modifier.fillMaxSize().background(Brand.hero).safeDrawingPadding().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // The exhausted buddy, with a slow red "alarm" glow pulsing behind it.
        val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "halo")
        val glow by pulse.animateFloat(
            0.35f, 0.75f,
            androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1400), androidx.compose.animation.core.RepeatMode.Reverse
            ), label = "glow"
        )
        androidx.compose.foundation.layout.Box(
            Modifier.size(240.dp).drawBehind {
                drawCircle(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(Color(0xFFFF5A5A).copy(alpha = glow * 0.55f), Color.Transparent)
                    ), radius = size.minDimension / 2
                )
            },
            contentAlignment = Alignment.Center
        ) { com.rabbi.scrollcounter.ui.AnimatedFace(Mood.EXHAUSTED, 180.dp) }
        Spacer(Modifier.height(20.dp))
        Text("Daily limit reached", style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "You've watched $total reels today (limit $limit).\n$appName is closed for today — your eyes and mind need a break.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onGoHome,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand.HeroTop)
        ) { Text("Okay, take a break", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(10.dp))
        if (graceAvailable) {
            OutlinedButton(
                onClick = onFiveMore,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.55f))
            ) { Text("5 more minutes (once a day)", color = Color.White, fontWeight = FontWeight.Medium) }
        } else {
            Text("You've already used today's 5 extra minutes.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onOpenApp) { Text("Open Scroll Counter", color = Color.White, fontWeight = FontWeight.Medium) }
    }
}
