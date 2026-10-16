package com.rabbi.scrollcounter.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import com.rabbi.scrollcounter.face.FaceRenderer
import com.rabbi.scrollcounter.face.Mood
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The living scroll buddy: blinks every few seconds, floats gently, bounces when [bounceKey]
 * changes (e.g. on every new scroll) and wobbles dizzily when exhausted.
 * [still] keeps the head fixed in place (no float, bounce or wobble): only the face is alive.
 */
@Composable
fun AnimatedFace(
    mood: Mood, size: Dp, modifier: Modifier = Modifier, bounceKey: Any? = null, still: Boolean = false,
    performance: com.rabbi.scrollcounter.face.BuddyVoice.Performance? = null,
) {
    val perf by androidx.compose.runtime.rememberUpdatedState(performance)
    var talk by remember { mutableFloatStateOf(0f) }
    var actX by remember { mutableFloatStateOf(0f) }    // extra translation (fraction of size)
    var actY by remember { mutableFloatStateOf(0f) }
    var actRot by remember { mutableFloatStateOf(0f) }  // extra rotation (degrees)
    var actScale by remember { mutableFloatStateOf(1f) }
    var shut by remember { mutableFloatStateOf(0f) }    // eyes closed by the act (yawn)
    var wide by remember { mutableFloatStateOf(0f) }
    // Human-like eyes: glances, resting drift and natural blinks, every frame.
    val eyes = remember { com.rabbi.scrollcounter.face.EyeLife() }
    var blink by remember { mutableFloatStateOf(0f) }
    var gazeX by remember { mutableFloatStateOf(0f) }
    var gazeY by remember { mutableFloatStateOf(0f) }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameMillis { t ->
                eyes.update(t)
                blink = eyes.blink; gazeX = eyes.gazeX; gazeY = eyes.gazeY
                time = (t % 1_000_000L) / 1000f
                // Act out the current sound: mouth follows its loudness, body does the gesture.
                val pf = perf
                val now = android.os.SystemClock.uptimeMillis()
                val a = pf?.talkAt(now) ?: -1f
                if (pf == null || a < 0f) {
                    talk *= 0.8f; actX *= 0.85f; actY *= 0.85f; actRot *= 0.85f
                    actScale = 1f + (actScale - 1f) * 0.85f; shut *= 0.85f; wide *= 0.85f
                } else {
                    val pr = pf.progress(now)
                    val bell = kotlin.math.sin(pr * Math.PI).toFloat()          // 0→1→0 over the sound
                    val sec = (now - pf.startMs) / 1000f
                    talk = a
                    when (pf.gesture) {
                        com.rabbi.scrollcounter.face.BuddyVoice.Gesture.HOP -> {
                            actY = -0.10f * a; actScale = 1f + 0.06f * a; actRot = 4f * kotlin.math.sin(sec * 18f) * a; wide = 0f; shut = 0f
                        }
                        com.rabbi.scrollcounter.face.BuddyVoice.Gesture.SWAY -> {
                            actRot = 9f * kotlin.math.sin(sec * 7f) * bell; actX = 0.03f * kotlin.math.sin(sec * 7f) * bell; actY = -0.02f * a; wide = 0f; shut = 0.35f * bell
                        }
                        com.rabbi.scrollcounter.face.BuddyVoice.Gesture.YAWN -> {
                            actRot = -8f * bell; actY = -0.04f * bell; actScale = 1f + 0.07f * bell; wide = 1f; shut = (bell * 1.4f).coerceAtMost(1f)
                        }
                        com.rabbi.scrollcounter.face.BuddyVoice.Gesture.SHIVER -> {
                            actRot = 3.5f * kotlin.math.sin(sec * 55f) * bell; actX = 0.012f * kotlin.math.sin(sec * 61f) * bell; actY = 0.03f * bell; actScale = 1f - 0.04f * bell; wide = 0f; shut = 0f
                        }
                        com.rabbi.scrollcounter.face.BuddyVoice.Gesture.WOBBLE -> {
                            actRot = 14f * kotlin.math.sin(sec * 6f) * bell; actX = 0.04f * kotlin.math.sin(sec * 6f) * bell; actY = 0.03f * bell; wide = 0.5f; shut = 0f
                        }
                    }
                }
            }
        }
    }

    val bounce = remember { Animatable(1f) }
    LaunchedEffect(bounceKey) {
        if (bounceKey != null) {
            eyes.glanceAtCounter(android.os.SystemClock.uptimeMillis())
            if (still) return@LaunchedEffect
            bounce.snapTo(1.16f)
            bounce.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 650f))
        }
    }

    val idle = rememberInfiniteTransition(label = "idle")
    val float by idle.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float"
    )
    val wobble by idle.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wobble"
    )
    val dizzy = mood == Mood.EXHAUSTED

    Canvas(
        modifier.size(size).graphicsLayer {
            if (still) return@graphicsLayer
            val sc = bounce.value * actScale
            scaleX = sc; scaleY = sc
            translationX = actX * this.size.width
            translationY = float * this.size.height * 0.03f + actY * this.size.height
            rotationZ = (if (dizzy) wobble * 7f else float * 2f) + actRot
        }
    ) {
        drawIntoCanvas {
            FaceRenderer.draw(it.nativeCanvas, this.size.width / 2, this.size.height / 2, this.size.minDimension / 2 * 0.94f, mood, maxOf(blink, shut), gazeX, gazeY, time, talk, wide)
        }
    }
}
