package com.rabbi.scrollcounter.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * A little face that gets more tired the more you scroll today.
 * The same drawing is used by the in-app hero, the floating bubble and the home-screen widget.
 */
enum class Mood(val label: String, val face: Int, val accent: Int) {
    FRESH("Feeling fresh", 0xFF5EEAD4.toInt(), 0xFF2DD4BF.toInt()),
    OKAY("Doing okay", 0xFFBEF264.toInt(), 0xFF84CC16.toInt()),
    TIRED("Getting tired", 0xFFFDE047.toInt(), 0xFFEAB308.toInt()),
    DRAINED("Really drained", 0xFFFDBA74.toInt(), 0xFFF97316.toInt()),
    EXHAUSTED("Exhausted — stop now", 0xFFFCA5A5.toInt(), 0xFFEF4444.toInt());

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
    private const val INK = 0xFF1C1917.toInt()
    private const val CHEEK = 0x66FB7185
    private const val SWEAT = 0xFF60A5FA.toInt()
    private const val BAGS = 0x55553C9A

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Draws a face of radius [r] centred at ([cx], [cy]). */
    fun draw(canvas: Canvas, cx: Float, cy: Float, r: Float, mood: Mood) {
        // Face with a darker rim
        fill.color = mood.face
        canvas.drawCircle(cx, cy, r, fill)
        stroke.color = mood.accent
        stroke.strokeWidth = r * 0.07f
        canvas.drawCircle(cx, cy, r - stroke.strokeWidth / 2, stroke)

        stroke.color = INK
        stroke.strokeWidth = r * 0.09f
        fill.color = INK
        val ex = r * 0.36f
        val ey = cy - r * 0.12f
        val er = r * 0.11f

        when (mood) {
            Mood.FRESH -> {
                // Bright open eyes with sparkle, big smile, rosy cheeks
                eye(canvas, cx - ex, ey, er * 1.15f, sparkle = true)
                eye(canvas, cx + ex, ey, er * 1.15f, sparkle = true)
                cheeks(canvas, cx, cy, r)
                arc(canvas, cx, cy + r * 0.12f, r * 0.42f, r * 0.34f, 20f, 140f)
            }
            Mood.OKAY -> {
                eye(canvas, cx - ex, ey, er, sparkle = true)
                eye(canvas, cx + ex, ey, er, sparkle = true)
                arc(canvas, cx, cy + r * 0.18f, r * 0.32f, r * 0.2f, 25f, 130f)
            }
            Mood.TIRED -> {
                // Half-closed lids, eye bags, flat mouth
                halfEye(canvas, cx - ex, ey, er * 1.2f)
                halfEye(canvas, cx + ex, ey, er * 1.2f)
                bags(canvas, cx - ex, ey, er, r)
                bags(canvas, cx + ex, ey, er, r)
                canvas.drawLine(cx - r * 0.24f, cy + r * 0.38f, cx + r * 0.24f, cy + r * 0.38f, stroke)
            }
            Mood.DRAINED -> {
                // Droopy line eyes, frown, sweat drop
                canvas.drawLine(cx - ex - er * 1.3f, ey - er * 0.2f, cx - ex + er * 1.3f, ey + er * 0.5f, stroke)
                canvas.drawLine(cx + ex - er * 1.3f, ey + er * 0.5f, cx + ex + er * 1.3f, ey - er * 0.2f, stroke)
                bags(canvas, cx - ex, ey + er * 0.4f, er, r)
                bags(canvas, cx + ex, ey + er * 0.4f, er, r)
                arc(canvas, cx, cy + r * 0.62f, r * 0.3f, r * 0.22f, 200f, 140f)
                sweat(canvas, cx + r * 0.62f, cy - r * 0.42f, r * 0.17f)
            }
            Mood.EXHAUSTED -> {
                // X eyes, open wobbly mouth, two sweat drops
                xEye(canvas, cx - ex, ey, er * 1.25f)
                xEye(canvas, cx + ex, ey, er * 1.25f)
                fill.color = INK
                canvas.drawOval(RectF(cx - r * 0.2f, cy + r * 0.28f, cx + r * 0.2f, cy + r * 0.56f), fill)
                sweat(canvas, cx + r * 0.64f, cy - r * 0.46f, r * 0.17f)
                sweat(canvas, cx - r * 0.7f, cy - r * 0.2f, r * 0.13f)
            }
        }
    }

    fun bitmap(sizePx: Int, mood: Mood): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = sizePx / 2f
        draw(c, r, r, r * 0.96f, mood)
        return bmp
    }

    private fun eye(c: Canvas, x: Float, y: Float, rad: Float, sparkle: Boolean) {
        fill.color = INK
        c.drawCircle(x, y, rad, fill)
        if (sparkle) {
            fill.color = 0xFFFFFFFF.toInt()
            c.drawCircle(x + rad * 0.35f, y - rad * 0.35f, rad * 0.32f, fill)
        }
    }

    private fun halfEye(c: Canvas, x: Float, y: Float, rad: Float) {
        // Lower half of an eye under a heavy lid
        fill.color = INK
        c.drawArc(RectF(x - rad, y - rad, x + rad, y + rad), 0f, 180f, true, fill)
        c.drawLine(x - rad * 1.25f, y, x + rad * 1.25f, y, stroke)
    }

    private fun xEye(c: Canvas, x: Float, y: Float, s: Float) {
        c.drawLine(x - s, y - s, x + s, y + s, stroke)
        c.drawLine(x - s, y + s, x + s, y - s, stroke)
    }

    private fun bags(c: Canvas, x: Float, y: Float, er: Float, r: Float) {
        val p = Paint(stroke).apply { color = BAGS; strokeWidth = r * 0.05f }
        c.drawArc(RectF(x - er * 1.3f, y + er * 0.4f, x + er * 1.3f, y + er * 2.0f), 20f, 140f, false, p)
    }

    private fun cheeks(c: Canvas, cx: Float, cy: Float, r: Float) {
        fill.color = CHEEK
        c.drawCircle(cx - r * 0.56f, cy + r * 0.18f, r * 0.13f, fill)
        c.drawCircle(cx + r * 0.56f, cy + r * 0.18f, r * 0.13f, fill)
    }

    private fun arc(c: Canvas, cx: Float, cy: Float, w: Float, h: Float, start: Float, sweep: Float) {
        c.drawArc(RectF(cx - w, cy - h, cx + w, cy + h), start, sweep, false, stroke)
    }

    private fun sweat(c: Canvas, x: Float, y: Float, s: Float) {
        val path = Path().apply {
            moveTo(x, y - s * 1.4f)
            quadTo(x + s, y, x, y + s)
            quadTo(x - s, y, x, y - s * 1.4f)
            close()
        }
        fill.color = SWEAT
        c.drawPath(path, fill)
    }
}
