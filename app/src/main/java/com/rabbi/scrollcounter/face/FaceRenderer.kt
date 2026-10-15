package com.rabbi.scrollcounter.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * The scroll buddy: a glossy, softly-lit 3D-style face that gets more tired the more you scroll.
 * Used by the in-app screens, the floating check-in card and the home-screen widget.
 */
enum class Mood(val label: String, val face: Int, val accent: Int) {
    FRESH("Feeling fresh", 0xFF6EEAD6.toInt(), 0xFF14B8A6.toInt()),
    OKAY("Doing okay", 0xFFC5F27A.toInt(), 0xFF65A30D.toInt()),
    TIRED("Getting tired", 0xFFFDE68A.toInt(), 0xFFEAB308.toInt()),
    DRAINED("Really drained", 0xFFFDBA8C.toInt(), 0xFFEA580C.toInt()),
    EXHAUSTED("Exhausted — stop now", 0xFFFCA5A5.toInt(), 0xFFDC2626.toInt());

    companion object {
        fun of(total: Int, limit: Int): Mood {
            val r = if (limit <= 0) 0f else total.toFloat() / limit
            return when {
                r < 0.25f -> FRESH
                r < 0.5f -> OKAY
                r < 0.75f -> TIRED
                r < 1f -> DRAINED
                else -> EXHAUSTED
            }
        }
    }
}

object FaceRenderer {
    private const val INK = 0xFF2A1A16.toInt()
    private const val INK_DEEP = 0xFF0D0605.toInt()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val s = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, sh: Int) = (x shr sh) and 0xFF
        fun m(sh: Int) = (ch(a, sh) + (ch(b, sh) - ch(a, sh)) * t).toInt().coerceIn(0, 255)
        return (m(24) shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }

    private fun alpha(c: Int, a: Float) = ((a * 255).toInt().coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)

    /**
     * Draws the face of radius [r] at ([cx], [cy]).
     * [blink] 0 = eyes open, 1 = closed (for blink animations).
     */
    @Synchronized
    fun draw(canvas: Canvas, cx: Float, cy: Float, r: Float, mood: Mood, blink: Float = 0f, gazeX: Float = 0f, gazeY: Float = 0f) {
        gx = gazeX.coerceIn(-1f, 1f); gy = gazeY.coerceIn(-1f, 1f)
        p.shader = null; p.style = Paint.Style.FILL
        s.shader = null

        // --- Head: lit from the top-left, shaded bottom-right
        val light = mix(mood.face, 0xFFFFFFFF.toInt(), 0.55f)
        val shade = mix(mood.face, mood.accent, 0.75f)
        p.shader = RadialGradient(
            cx - r * 0.35f, cy - r * 0.42f, r * 1.55f,
            intArrayOf(light, mood.face, shade), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, p)
        // Soft rim shadow for depth
        p.shader = RadialGradient(
            cx, cy, r, intArrayOf(0x00000000, 0x00000000, alpha(mood.accent, 0.45f)),
            floatArrayOf(0f, 0.78f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, p)
        // Glossy highlight
        p.shader = RadialGradient(
            cx - r * 0.38f, cy - r * 0.55f, r * 0.42f,
            intArrayOf(0x99FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP
        )
        canvas.drawOval(RectF(cx - r * 0.78f, cy - r * 0.92f, cx + r * 0.02f, cy - r * 0.2f), p)
        p.shader = null

        val ex = r * 0.34f
        val ey = cy - r * 0.08f
        val ew = r * 0.17f
        val eh = r * 0.22f

        // --- Cheeks
        val blushA = when (mood) { Mood.FRESH -> 0.55f; Mood.OKAY -> 0.45f; Mood.TIRED -> 0.3f; Mood.DRAINED -> 0.35f; Mood.EXHAUSTED -> 0.6f }
        for (side in listOf(-1f, 1f)) {
            val bx = cx + side * r * 0.56f
            val by = cy + r * 0.24f
            p.shader = RadialGradient(bx, by, r * 0.2f, intArrayOf(alpha(0xFF6B8A, blushA), 0x00FF6B8A), null, Shader.TileMode.CLAMP)
            canvas.drawOval(RectF(bx - r * 0.22f, by - r * 0.14f, bx + r * 0.22f, by + r * 0.14f), p)
        }
        p.shader = null

        // --- Eyes
        when (mood) {
            Mood.EXHAUSTED -> {
                spiralEye(canvas, cx - ex, ey, ew * 1.15f, r)
                spiralEye(canvas, cx + ex, ey, ew * 1.15f, r)
            }
            else -> {
                val lid = when (mood) { Mood.TIRED -> 0.45f; Mood.DRAINED -> 0.62f; else -> 0f }
                val closed = maxOf(lid, blink)
                glossyEye(canvas, cx - ex, ey, ew, eh, closed, mood, r)
                glossyEye(canvas, cx + ex, ey, ew, eh, closed, mood, r)
                if (mood == Mood.TIRED || mood == Mood.DRAINED) {
                    // Eye bags
                    s.color = alpha(0x6B3FA0, 0.35f); s.strokeWidth = r * 0.035f
                    for (side in listOf(-1f, 1f)) {
                        val x = cx + side * ex
                        canvas.drawArc(RectF(x - ew * 1.1f, ey + eh * 0.35f, x + ew * 1.1f, ey + eh * 1.35f), 25f, 130f, false, s)
                    }
                }
                if (mood == Mood.DRAINED) {
                    // Worried brows
                    s.color = INK; s.strokeWidth = r * 0.055f
                    canvas.drawLine(cx - ex - ew, ey - eh * 1.35f, cx - ex + ew * 0.8f, ey - eh * 1.75f, s)
                    canvas.drawLine(cx + ex + ew, ey - eh * 1.35f, cx + ex - ew * 0.8f, ey - eh * 1.75f, s)
                }
            }
        }

        // --- Mouth
        val my = cy + r * 0.36f
        when (mood) {
            Mood.FRESH -> {
                // Open happy smile with a little tongue
                val path = Path().apply {
                    moveTo(cx - r * 0.24f, my - r * 0.06f)
                    quadTo(cx, my - r * 0.0f, cx + r * 0.24f, my - r * 0.06f)
                    quadTo(cx + r * 0.2f, my + r * 0.24f, cx, my + r * 0.25f)
                    quadTo(cx - r * 0.2f, my + r * 0.24f, cx - r * 0.24f, my - r * 0.06f)
                    close()
                }
                p.color = INK_DEEP; canvas.drawPath(path, p)
                canvas.save(); canvas.clipPath(path)
                p.color = 0xFFFF7A90.toInt()
                canvas.drawCircle(cx, my + r * 0.25f, r * 0.13f, p)
                canvas.restore()
            }
            Mood.OKAY -> {
                s.color = INK; s.strokeWidth = r * 0.07f
                canvas.drawArc(RectF(cx - r * 0.2f, my - r * 0.2f, cx + r * 0.2f, my + r * 0.08f), 25f, 130f, false, s)
            }
            Mood.TIRED -> {
                s.color = INK; s.strokeWidth = r * 0.065f
                val path = Path().apply {
                    moveTo(cx - r * 0.17f, my + r * 0.04f)
                    quadTo(cx - r * 0.06f, my - r * 0.02f, cx, my + r * 0.03f)
                    quadTo(cx + r * 0.08f, my + r * 0.08f, cx + r * 0.17f, my + r * 0.01f)
                }
                canvas.drawPath(path, s)
            }
            Mood.DRAINED -> {
                s.color = INK; s.strokeWidth = r * 0.065f
                canvas.drawArc(RectF(cx - r * 0.17f, my + r * 0.02f, cx + r * 0.17f, my + r * 0.26f), 205f, 130f, false, s)
                sweat(canvas, cx + r * 0.66f, cy - r * 0.42f, r * 0.15f)
            }
            Mood.EXHAUSTED -> {
                // Open, wobbly "ugh" mouth
                p.color = INK_DEEP
                canvas.drawOval(RectF(cx - r * 0.14f, my - r * 0.02f, cx + r * 0.14f, my + r * 0.24f), p)
                p.color = 0xFFFF7A90.toInt()
                canvas.drawOval(RectF(cx - r * 0.08f, my + r * 0.11f, cx + r * 0.08f, my + r * 0.22f), p)
                sweat(canvas, cx + r * 0.68f, cy - r * 0.46f, r * 0.16f)
                sweat(canvas, cx - r * 0.74f, cy - r * 0.2f, r * 0.12f)
            }
        }
    }

    private var gx = 0f
    private var gy = 0f

    private fun glossyEye(c: Canvas, x0: Float, y0: Float, w0: Float, h: Float, closed: Float, mood: Mood, r: Float) {
        // Gaze: the eye moves inside its socket and looks slightly narrower when turned sideways.
        val x = x0 + gx * w0 * 0.55f
        val y = y0 + gy * h * 0.32f
        val w = w0 * (1f - 0.12f * kotlin.math.abs(gx))
        if (closed >= 0.95f) {
            // Closed: a soft curved line
            s.color = INK; s.strokeWidth = r * 0.055f
            c.drawArc(RectF(x - w, y - h * 0.4f, x + w, y + h * 0.6f), 20f, 140f, false, s)
            return
        }
        val rect = RectF(x - w, y - h, x + w, y + h)
        c.save()
        // Upper lid hides the top part of the eye
        if (closed > 0f) c.clipRect(x - w * 1.5f, y - h + 2 * h * closed, x + w * 1.5f, y + h * 1.5f)
        p.shader = LinearGradient(x, y - h, x, y + h, INK, INK_DEEP, Shader.TileMode.CLAMP)
        c.drawOval(rect, p)
        p.shader = null
        // Two catch-lights make the eyes look wet and alive
        val lx = x - gx * w0 * 0.25f
        val ly0 = y - gy * h * 0.15f
        p.color = 0xFFFFFFFF.toInt()
        c.drawOval(RectF(lx - w * 0.15f, ly0 - h * 0.7f, lx + w * 0.6f, ly0 - h * 0.05f), p)
        p.color = 0xCCFFFFFF.toInt()
        c.drawCircle(lx - w * 0.45f, ly0 + h * 0.45f, w * 0.18f, p)
        c.restore()
        if (closed > 0f) {
            // Eyelid edge
            s.color = mix(mood.accent, INK, 0.4f); s.strokeWidth = r * 0.05f
            val ly = y - h + 2 * h * closed
            c.drawLine(x - w * 1.1f, ly, x + w * 1.1f, ly, s)
        }
    }

    private fun spiralEye(c: Canvas, x: Float, y: Float, rad: Float, r: Float) {
        s.color = INK; s.strokeWidth = r * 0.045f
        val path = Path()
        var a = 0.0
        var first = true
        while (a < Math.PI * 5) {
            val rr = rad * (a / (Math.PI * 5)).toFloat()
            val px = x + rr * Math.cos(a).toFloat()
            val py = y + rr * Math.sin(a).toFloat()
            if (first) { path.moveTo(px, py); first = false } else path.lineTo(px, py)
            a += 0.25
        }
        c.drawPath(path, s)
    }

    private fun sweat(c: Canvas, x: Float, y: Float, sz: Float) {
        val path = Path().apply {
            moveTo(x, y - sz * 1.5f)
            quadTo(x + sz * 1.05f, y + sz * 0.1f, x, y + sz)
            quadTo(x - sz * 1.05f, y + sz * 0.1f, x, y - sz * 1.5f)
            close()
        }
        p.shader = LinearGradient(x, y - sz * 1.5f, x, y + sz, 0xFFBFE6FF.toInt(), 0xFF3B9BF0.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = null
        p.color = 0xCCFFFFFF.toInt()
        c.drawCircle(x - sz * 0.3f, y - sz * 0.1f, sz * 0.22f, p)
    }

    fun bitmap(sizePx: Int, mood: Mood): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = sizePx / 2f
        draw(c, r, r, r * 0.96f, mood)
        return bmp
    }
}
