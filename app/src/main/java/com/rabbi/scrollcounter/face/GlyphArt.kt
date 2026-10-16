package com.rabbi.scrollcounter.face

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * Scroll Counter's own little icons, drawn in code (no system emoji), so they match the app's
 * glossy 3D style on every phone: a fire for streaks, a sparkle, a lightning bolt and a check.
 * Each draws inside a square of side [s] whose top-left is (x, y).
 */
object GlyphArt {
    enum class Glyph { FLAME, SPARKLE, BOLT, CHECK }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    @Synchronized
    fun draw(c: Canvas, glyph: Glyph, x: Float, y: Float, s: Float, dim: Boolean = false) {
        when (glyph) {
            Glyph.FLAME -> flame(c, x, y, s, dim)
            Glyph.SPARKLE -> sparkle(c, x, y, s)
            Glyph.BOLT -> bolt(c, x, y, s)
            Glyph.CHECK -> check(c, x, y, s)
        }
    }

    /** Glossy two-layer flame. [dim] = no streak yet: a cool, unlit ember. */
    private fun flame(c: Canvas, x: Float, y: Float, s: Float, dim: Boolean) {
        fun X(f: Float) = x + f * s
        fun Y(f: Float) = y + f * s
        // soft glow
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(X(0.5f), Y(0.68f), s * 0.62f,
            intArrayOf(if (dim) 0x3394A3B8 else 0x66FF8A1F, 0), null, Shader.TileMode.CLAMP)
        c.drawCircle(X(0.5f), Y(0.68f), s * 0.62f, p)
        // outer flame
        path.reset()
        path.moveTo(X(0.52f), Y(0.02f))
        path.cubicTo(X(0.64f), Y(0.22f), X(0.88f), Y(0.36f), X(0.88f), Y(0.63f))
        path.cubicTo(X(0.88f), Y(0.86f), X(0.71f), Y(0.98f), X(0.5f), Y(0.98f))
        path.cubicTo(X(0.29f), Y(0.98f), X(0.12f), Y(0.86f), X(0.12f), Y(0.64f))
        path.cubicTo(X(0.12f), Y(0.48f), X(0.21f), Y(0.38f), X(0.30f), Y(0.29f))
        path.cubicTo(X(0.31f), Y(0.41f), X(0.36f), Y(0.49f), X(0.43f), Y(0.52f))
        path.cubicTo(X(0.40f), Y(0.34f), X(0.45f), Y(0.16f), X(0.52f), Y(0.02f))
        path.close()
        p.shader = if (dim) LinearGradient(X(0.5f), Y(0f), X(0.5f), Y(1f),
            intArrayOf(0xFF7C8A99.toInt(), 0xFFA9B6C4.toInt(), 0xFFCBD5E1.toInt()), null, Shader.TileMode.CLAMP)
        else LinearGradient(X(0.5f), Y(0f), X(0.5f), Y(1f),
            intArrayOf(0xFFE8380D.toInt(), 0xFFFF7A1A.toInt(), 0xFFFFC23A.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        // inner flame
        path.reset()
        path.moveTo(X(0.53f), Y(0.42f))
        path.cubicTo(X(0.61f), Y(0.55f), X(0.73f), Y(0.64f), X(0.73f), Y(0.78f))
        path.cubicTo(X(0.73f), Y(0.90f), X(0.63f), Y(0.97f), X(0.5f), Y(0.97f))
        path.cubicTo(X(0.37f), Y(0.97f), X(0.27f), Y(0.90f), X(0.27f), Y(0.78f))
        path.cubicTo(X(0.27f), Y(0.68f), X(0.35f), Y(0.62f), X(0.42f), Y(0.56f))
        path.cubicTo(X(0.44f), Y(0.62f), X(0.47f), Y(0.66f), X(0.50f), Y(0.67f))
        path.cubicTo(X(0.48f), Y(0.58f), X(0.49f), Y(0.50f), X(0.53f), Y(0.42f))
        path.close()
        p.shader = if (dim) LinearGradient(X(0.5f), Y(0.4f), X(0.5f), Y(1f), 0xFFE2E8F0.toInt(), 0xFFF8FAFC.toInt(), Shader.TileMode.CLAMP)
        else LinearGradient(X(0.5f), Y(0.4f), X(0.5f), Y(1f), 0xFFFFE27A.toInt(), 0xFFFFFBEA.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        // gloss
        p.shader = null
        p.color = 0x55FFFFFF
        c.drawOval(X(0.24f), Y(0.50f), X(0.36f), Y(0.70f), p)
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, s: Float) {
        fun star(cx: Float, cy: Float, r: Float) {
            val k = r * 0.2f
            path.reset()
            path.moveTo(cx, cy - r)
            path.quadTo(cx + k, cy - k, cx + r, cy)
            path.quadTo(cx + k, cy + k, cx, cy + r)
            path.quadTo(cx - k, cy + k, cx - r, cy)
            path.quadTo(cx - k, cy - k, cx, cy - r)
            path.close()
            p.shader = LinearGradient(cx, cy - r, cx, cy + r, 0xFFFFF4C2.toInt(), 0xFFF5B82E.toInt(), Shader.TileMode.CLAMP)
            c.drawPath(path, p)
        }
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(x + s * 0.42f, y + s * 0.55f, s * 0.6f, intArrayOf(0x55FFD54A, 0), null, Shader.TileMode.CLAMP)
        c.drawCircle(x + s * 0.42f, y + s * 0.55f, s * 0.6f, p)
        star(x + s * 0.42f, y + s * 0.56f, s * 0.42f)
        star(x + s * 0.80f, y + s * 0.20f, s * 0.18f)
        p.shader = null
    }

    private fun bolt(c: Canvas, x: Float, y: Float, s: Float) {
        fun X(f: Float) = x + f * s
        fun Y(f: Float) = y + f * s
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(X(0.5f), Y(0.5f), s * 0.6f, intArrayOf(0x55FFD54A, 0), null, Shader.TileMode.CLAMP)
        c.drawCircle(X(0.5f), Y(0.5f), s * 0.6f, p)
        path.reset()
        path.moveTo(X(0.60f), Y(0.02f)); path.lineTo(X(0.18f), Y(0.56f)); path.lineTo(X(0.46f), Y(0.56f))
        path.lineTo(X(0.36f), Y(0.98f)); path.lineTo(X(0.84f), Y(0.40f)); path.lineTo(X(0.55f), Y(0.40f))
        path.close()
        p.shader = LinearGradient(X(0.5f), Y(0f), X(0.5f), Y(1f), 0xFFFFF0A0.toInt(), 0xFFFF9F1C.toInt(), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = null
    }

    private fun check(c: Canvas, x: Float, y: Float, s: Float) {
        val cx = x + s / 2; val cy = y + s / 2; val r = s * 0.46f
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(cx, cy - r, cx, cy + r, 0xFF4ADE80.toInt(), 0xFF16A34A.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.color = 0x40FFFFFF
        c.drawOval(cx - r * 0.6f, cy - r * 0.85f, cx + r * 0.3f, cy - r * 0.25f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.11f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = 0xFFFFFFFF.toInt()
        path.reset()
        path.moveTo(cx - r * 0.45f, cy + r * 0.02f)
        path.lineTo(cx - r * 0.1f, cy + r * 0.36f)
        path.lineTo(cx + r * 0.48f, cy - r * 0.3f)
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }
}
