package com.rabbi.scrollcounter.guide

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rabbi.scrollcounter.MainActivity
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.ui.theme.ScrollCounterTheme
import com.rabbi.scrollcounter.ui.tr

/**
 * The floating setup guide. It shrinks itself into a picture-in-picture window right away, so the
 * phone's Settings (opened just before it) stays usable underneath while the guide shows what to
 * tap. It follows along on its own: after the "restricted setting" refusal it opens App info,
 * after "Allow restricted settings" it opens Accessibility again, and once Scroll Counter is on it
 * brings the user back to the app and closes. Tapping the window opens it full size.
 */
class SetupGuideActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val skin = Skin.detect()
    private var stage by mutableStateOf(SetupGuide.Stage.ENABLE)
    private var startAt by mutableLongStateOf(System.currentTimeMillis())
    private var inPip by mutableStateOf(false)
    private var pipTried = false
    private var done = false
    private val openedAt = System.currentTimeMillis()

    private val poll = object : Runnable {
        override fun run() {
            if (done) return
            val now = SetupGuide.stage(this@SetupGuideActivity)
            if (now != stage) onStage(now)
            // Don't float forever if it's forgotten.
            if (System.currentTimeMillis() - openedAt > 15 * 60_000L) { close(); return }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScrollStore.get(this) // loads the language
        stage = SetupGuide.stage(this)
        if (stage == SetupGuide.Stage.ON) { finish(); return }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!enterPip()) close() }
        })
        setContent { ScrollCounterTheme(dark = true) { Content() } }
    }

    override fun onResume() {
        super.onResume()
        if (!pipTried) {
            pipTried = true
            // Into the floating window at once; if this phone (or the user) doesn't allow
            // picture-in-picture, just leave the Settings page that's already open.
            handler.postDelayed({ if (!enterPip()) close() }, 120)
        }
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, POLL_MS)
    }

    override fun onStop() {
        super.onStop()
        // The floating window was closed (swiped away / ✕): end the guide.
        if (!isChangingConfigurations && !isInPictureInPictureMode && !done) close()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && !isInPictureInPictureMode) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    private fun params(): PictureInPictureParams {
        val b = PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16))
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(true).setSeamlessResizeEnabled(false)
        return b.build()
    }

    private fun enterPip(): Boolean = runCatching {
        val ok = enterPictureInPictureMode(params())
        if (ok && Build.VERSION.SDK_INT >= 31) setPictureInPictureParams(params())
        ok
    }.getOrDefault(false)

    /** The phone moved on (the user did a step): follow with the next page and the next scenes. */
    private fun onStage(next: SetupGuide.Stage) {
        val prev = stage
        if (next == SetupGuide.Stage.ON) {
            done = true
            runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            }
            handler.postDelayed({ finishAndRemoveTask() }, 300)
            return
        }
        stage = next
        startAt = System.currentTimeMillis()
        when {
            // Refused once → App info now has "Allow restricted settings". Give the refusal a moment, then go there.
            prev == SetupGuide.Stage.BLOCKED && next == SetupGuide.Stage.CAN_ALLOW ->
                handler.postDelayed({ if (!done) SetupGuide.openFor(this, next) }, 1300)
            // Allowed → back to Accessibility to switch it on.
            next == SetupGuide.Stage.ENABLE ->
                handler.postDelayed({ if (!done) SetupGuide.openFor(this, next) }, 500)
        }
    }

    private fun close() {
        done = true
        handler.removeCallbacksAndMessages(null)
        finishAndRemoveTask()
    }

    @Composable
    private fun Content() {
        val dark = isSystemInDarkTheme()
        val scenes = remember(stage) { scenesFor(skin, stage) }
        if (inPip) {
            GuideMock(skin, scenes, dark, startAt, Modifier.fillMaxSize().background(Color.Black))
            return
        }
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF1B140B), Color(0xFF0A0806))))
                .safeDrawingPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                tr("How to turn on Scroll Counter"), color = Color.White, fontSize = 22.sp,
                fontWeight = FontWeight.Black, textAlign = TextAlign.Center
            )
            if (stage == SetupGuide.Stage.BLOCKED || stage == SetupGuide.Stage.CAN_ALLOW) {
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Your phone blocks this for apps installed outside the Play Store. Allow it once, then turn Scroll Counter on."),
                    color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
            GuideMock(skin, scenes, dark, startAt, Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                scenes.forEachIndexed { i, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFFFE6A3)),
                            contentAlignment = Alignment.Center
                        ) { Text("${i + 1}", color = Color(0xFF4A2A00), fontSize = 12.sp, fontWeight = FontWeight.Black) }
                        Spacer(Modifier.width(10.dp))
                        Text(s.caption, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFFE6A3), Color(0xFFFFC857), Color(0xFFFF9F43))))
                    .clickable { if (!enterPip()) close() },
                contentAlignment = Alignment.Center
            ) { Text(tr("Got it"), color = Color(0xFF4A2A00), fontSize = 16.sp, fontWeight = FontWeight.Black) }
            TextButton(onClick = { close() }) { Text(tr("Close guide"), color = Color.White.copy(alpha = 0.7f)) }
        }
    }

    companion object {
        private const val POLL_MS = 600L
    }
}
