package com.rabbi.scrollcounter.ui

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import com.rabbi.scrollcounter.face.EyeLife
import com.rabbi.scrollcounter.face.FaceRenderer
import com.rabbi.scrollcounter.face.Mood
import kotlin.math.sin

/**
 * The full-body Scroll Counter buddy: the glossy 3D head on a little round body with arms and
 * feet. It breathes, sways and waves while fresh, droops as it gets tired, and wobbles dizzily
 * (spinning eyes, panting) when the limit is reached. Eyes glance around and blink like a person's.
 */
@Composable
fun BuddyCharacter(mood: Mood, height: Dp, modifier: Modifier = Modifier, bounceKey: Any? = null) {
    val eyes = remember { EyeLife() }
    var time by remember { mutableFloatStateOf(0f) }
    var blink by remember { mutableFloatStateOf(0f) }
    var gx by remember { mutableFloatStateOf(0f) }
    var gy by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) withFrameMillis { t ->
            eyes.update(t)
            blink = eyes.blink; gx = eyes.gazeX; gy = eyes.gazeY
            time = (t % 1_000_000L) / 1000f
        }
    }
    val hop = remember { Animatable(0f) }
    LaunchedEffect(bounceKey) {
        if (bounceKey != null) {
            eyes.glanceAtCounter(android.os.SystemClock.uptimeMillis())
            hop.snapTo(1f)
            hop.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 420f))
        }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    Canvas(modifier.size(height * 0.82f, height)) {
        drawIntoCanvas { cv ->
            val c = cv.nativeCanvas
            val w = size.width
            val h = size.height
            val tired = mood == Mood.TIRED || mood == Mood.DRAINED
            val dizzy = mood == Mood.EXHAUSTED
            val energy = when (mood) { Mood.FRESH -> 1f; Mood.OKAY -> 0.8f; Mood.TIRED -> 0.5f; Mood.DRAINED -> 0.35f; Mood.EXHAUSTED -> 0.25f }

            val breathe = sin(time * 2.2f) * 0.03f
            val bob = -hop.value * h * 0.08f + sin(time * 2.2f) * h * 0.008f
            val sway = if (dizzy) sin(time * 5.5f) * 7f else sin(time * 1.3f) * 2.5f * energy

            val r = w * 0.40f                        // head radius
            val headCx = w / 2
            val headCy = r * 1.05f + bob + (if (tired) h * 0.015f else 0f)
            val bodyTop = headCy + r * 0.70f
            val bodyW = r * 1.02f
            val bodyH = r * (0.92f + breathe)
            val bodyCx = w / 2
            val groundY = h * 0.965f

            // Ground shadow (shrinks while hopping)
            val shadowW = w * (0.30f - hop.value * 0.08f)
            paint.shader = RadialGradient(w / 2, groundY, shadowW, intArrayOf(0x55000000, 0), null, Shader.TileMode.CLAMP)
            c.drawOval(RectF(w / 2 - shadowW, groundY - h * 0.02f, w / 2 + shadowW, groundY + h * 0.02f), paint)

            val light = mixColor(mood.face, 0xFFFFFFFF.toInt(), 0.45f)
            val shade = mixColor(mood.face, mood.accent, 0.7f)

            c.save()
            c.rotate(sway, w / 2, groundY)

            // Legs + feet
            val legTop = bodyTop + bodyH * 0.80f
            val step = if (mood == Mood.FRESH) sin(time * 3f) * r * 0.03f else 0f
            for (side in listOf(-1f, 1f)) {
                val lx = bodyCx + side * bodyW * 0.28f
                paint.shader = LinearGradient(lx, legTop, lx, groundY, mood.face, shade, Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(lx - r * 0.12f, legTop, lx + r * 0.12f, groundY - r * 0.06f + side * step), r * 0.12f, r * 0.12f, paint)
                // foot
                c.drawOval(RectF(lx - r * 0.20f + side * r * 0.04f, groundY - r * 0.16f + side * step, lx + r * 0.20f + side * r * 0.04f, groundY + side * step * 0.2f), paint)
            }

            // Arms (behind the body edge), swinging; droop when tired, flop when dizzy
            val armLen = r * 0.62f
            for (side in listOf(-1f, 1f)) {
                val ax = bodyCx + side * bodyW * 0.46f
                val ay = bodyTop + bodyH * 0.30f
                val base = when {
                    dizzy -> 18f + sin(time * 6f + side) * 25f
                    tired -> 10f + sin(time * 1.2f) * 4f
                    else -> 28f + sin(time * 2.4f + side * 1.2f) * 14f * energy
                }
                // Fresh buddy gives a little wave with the right hand now and then
                val wave = if (mood == Mood.FRESH && side > 0) (maxOf(0f, sin(time * 0.7f)) * 70f) * (0.6f + 0.4f * sin(time * 9f)) else 0f
                c.save()
                c.rotate(side * (base + wave) * -1f + 0f, ax, ay)
                paint.shader = LinearGradient(ax, ay, ax, ay + armLen, mood.face, shade, Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(ax - r * 0.10f, ay, ax + r * 0.10f, ay + armLen), r * 0.10f, r * 0.10f, paint)
                c.drawCircle(ax, ay + armLen, r * 0.13f, paint)
                c.restore()
            }

            // Body: soft round belly with the same glossy light as the head
            paint.shader = RadialGradient(bodyCx - bodyW * 0.3f, bodyTop + bodyH * 0.25f, bodyW * 1.3f,
                intArrayOf(light, mood.face, shade), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawOval(RectF(bodyCx - bodyW / 2, bodyTop, bodyCx + bodyW / 2, bodyTop + bodyH), paint)
            paint.shader = RadialGradient(bodyCx, bodyTop + bodyH * 0.55f, bodyW * 0.32f,
                intArrayOf(0x33FFFFFF, 0), null, Shader.TileMode.CLAMP)
            c.drawOval(RectF(bodyCx - bodyW * 0.3f, bodyTop + bodyH * 0.3f, bodyCx + bodyW * 0.3f, bodyTop + bodyH * 0.85f), paint)
            paint.shader = null

            // Head
            FaceRenderer.draw(c, headCx, headCy, r, mood, blink, gx, gy, time)
            c.restore()
        }
    }
}

private fun mixColor(a: Int, b: Int, t: Float): Int {
    fun ch(x: Int, sh: Int) = (x shr sh) and 0xFF
    fun m(sh: Int) = (ch(a, sh) + (ch(b, sh) - ch(a, sh)) * t).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
}
