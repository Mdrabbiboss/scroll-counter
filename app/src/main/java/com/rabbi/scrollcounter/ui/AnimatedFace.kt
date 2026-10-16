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
 */
@Composable
fun AnimatedFace(mood: Mood, size: Dp, modifier: Modifier = Modifier, bounceKey: Any? = null) {
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
            }
        }
    }

    val bounce = remember { Animatable(1f) }
    LaunchedEffect(bounceKey) {
        if (bounceKey != null) {
            eyes.glanceAtCounter(android.os.SystemClock.uptimeMillis())
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
            val sc = bounce.value
            scaleX = sc; scaleY = sc
            translationY = float * this.size.height * 0.03f
            rotationZ = if (dizzy) wobble * 7f else float * 2f
        }
    ) {
        drawIntoCanvas {
            FaceRenderer.draw(it.nativeCanvas, this.size.width / 2, this.size.height / 2, this.size.minDimension / 2 * 0.94f, mood, blink, gazeX, gazeY, time)
        }
    }
}
