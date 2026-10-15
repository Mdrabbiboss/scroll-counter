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
    var blink by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(mood) {
        if (mood == Mood.EXHAUSTED) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(1800, 4000))
            Animatable(0f).animateTo(1f, tween(80)) { blink = value }
            Animatable(1f).animateTo(0f, tween(130)) { blink = value }
        }
    }

    val bounce = remember { Animatable(1f) }
    LaunchedEffect(bounceKey) {
        if (bounceKey != null) {
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
            FaceRenderer.draw(it.nativeCanvas, this.size.width / 2, this.size.height / 2, this.size.minDimension / 2 * 0.94f, mood, blink)
        }
    }
}
