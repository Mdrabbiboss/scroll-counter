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
import com.rabbi.scrollcounter.face.BuddyVoice.Gesture as G

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
    var stretch by remember { mutableFloatStateOf(0f) } // >0 taller & thinner, <0 squashed
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
                    actScale = 1f + (actScale - 1f) * 0.85f; shut *= 0.85f; wide *= 0.85f; stretch *= 0.85f
                } else {
                    val pr = pf.progress(now)
                    val bell = kotlin.math.sin(pr * Math.PI).toFloat()          // 0→1→0 over the sound
                    val sec = (now - pf.startMs) / 1000f
                    talk = a
                    // Reset, then each move sets only what it uses.
                    actX = 0f; actY = 0f; actRot = 0f; actScale = 1f; shut = 0f; wide = 0f; stretch = 0f
                    fun ease(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
                    val sn = { f: Float -> kotlin.math.sin(sec * f) }
                    when (pf.gesture) {
                        G.HOP -> { actY = -0.10f * a; actScale = 1f + 0.06f * a; actRot = 4f * sn(18f) * a }
                        G.BOUNCE -> { actY = -0.14f * a; stretch = 0.09f * a - 0.07f * (1f - a) * bell }
                        G.GIGGLE -> { actRot = 6f * sn(30f) * bell; actX = 0.02f * sn(30f) * bell; actY = -0.03f * a; shut = 0.65f * bell }
                        G.SPIN -> { actRot = 360f * ease(pr); actY = -0.08f * bell; actScale = 1f + 0.05f * bell; wide = 0.4f * bell }
                        G.SHIMMY -> { actX = 0.035f * sn(24f) * bell; actRot = 5f * sn(24f) * bell; actY = -0.02f * a }
                        G.SQUISH -> {
                            if (pr < 0.35f) { val k = pr / 0.35f; stretch = -0.13f * k; shut = k }
                            else { val k = (pr - 0.35f) / 0.65f; stretch = 0.10f * kotlin.math.sin(k * Math.PI).toFloat() - 0.13f * (1f - ease(k * 3f)); shut = (1f - k * 3f).coerceAtLeast(0f); actScale = 1f + 0.07f * kotlin.math.sin(k * Math.PI).toFloat() }
                        }
                        G.NOD -> { actY = 0.04f * kotlin.math.abs(sn(9f)) * bell; actRot = 2f * sn(9f) * bell; shut = 0.3f * bell }
                        G.TILT -> { val dir = if ((pf.startMs / 7L) % 2L == 0L) 1f else -1f; actRot = 16f * dir * bell; actX = 0.02f * dir * bell; wide = 0.7f * bell }
                        G.SWAY -> { actRot = 9f * sn(7f) * bell; actX = 0.03f * sn(7f) * bell; actY = -0.02f * a; shut = 0.35f * bell }
                        G.YAWN -> { actRot = -8f * bell; actY = -0.04f * bell; actScale = 1f + 0.07f * bell; wide = 1f; shut = (bell * 1.4f).coerceAtMost(1f) }
                        G.DROOP -> {
                            if (pr < 0.8f) { val k = ease(pr / 0.8f); actRot = 12f * k; actY = 0.06f * k; shut = k }
                            else { val k = (pr - 0.8f) / 0.2f; actRot = 12f * (1f - k); actY = 0.06f * (1f - k) - 0.03f * kotlin.math.sin(k * Math.PI).toFloat(); shut = 1f - k; wide = 0.8f * kotlin.math.sin(k * Math.PI).toFloat() }
                        }
                        G.SNORE -> { actScale = 1f + 0.06f * sn(5.5f) * bell; actY = 0.02f * bell; shut = (bell * 3f).coerceAtMost(1f) }
                        G.SIGH -> { actScale = 1f - 0.07f * bell; actY = 0.05f * bell; stretch = -0.05f * bell; shut = 0.5f * bell }
                        G.STRETCH -> { stretch = 0.14f * bell; actY = -0.05f * bell; shut = 0.7f * bell; wide = 0.6f * bell }
                        G.SHIVER -> { actRot = 3.5f * sn(55f) * bell; actX = 0.012f * sn(61f) * bell; actY = 0.03f * bell; actScale = 1f - 0.04f * bell }
                        G.SNIFF -> { actY = -0.04f * a; stretch = 0.04f * a; shut = 0.2f * bell }
                        G.SHAKE -> { actRot = 11f * sn(16f) * bell; actX = 0.02f * sn(16f) * bell; shut = 0.3f * bell }
                        G.WOBBLE -> { actRot = 14f * sn(6f) * bell; actX = 0.04f * sn(6f) * bell; actY = 0.03f * bell; wide = 0.5f }
                        G.FLOP -> {
                            if (pr < 0.3f) { val k = pr / 0.3f; actY = 0.10f * k * k; stretch = -0.12f * k }
                            else { val k = (pr - 0.3f) / 0.7f; actY = 0.10f * (1f - k) - 0.05f * kotlin.math.abs(kotlin.math.sin(k * Math.PI * 2)).toFloat() * (1f - k); stretch = -0.12f * (1f - k) }
                            shut = 0.5f * bell
                        }
                        G.ORBIT -> { actX = 0.05f * kotlin.math.cos(sec * 7f) * bell; actY = 0.05f * sn(7f) * bell; actRot = 10f * sn(7f) * bell; wide = 0.5f * bell }
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
            scaleX = sc * (1f - stretch * 0.6f); scaleY = sc * (1f + stretch)
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
