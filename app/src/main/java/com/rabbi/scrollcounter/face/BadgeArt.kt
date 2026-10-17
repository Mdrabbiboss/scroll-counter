package com.rabbi.scrollcounter.face

import com.rabbi.scrollcounter.ui.tr

import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.rabbi.scrollcounter.data.Badges
import com.rabbi.scrollcounter.ui.theme.AppFonts
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glossy streak medals, drawn in code: ribbon tails, a scalloped rim, a shiny coin with the
 * number of days in the middle. Locked medals are grey with a small padlock.
 * Draws inside a square of side [s] whose top-left is (x, y).
 */
object BadgeArt {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rf = RectF()
    private val grey = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })

    @Synchronized
    fun draw(c: Canvas, badge: Badges.Badge, x: Float, y: Float, s: Float, unlocked: Boolean, glow: Boolean = unlocked) {
        val t = badge.tier
        val cx = x + s / 2f
        val cy = y + s * 0.42f
        val r = s * 0.33f

        val layer = if (!unlocked) c.saveLayerAlpha(x - s * 0.2f, y - s * 0.2f, x + s * 1.2f, y + s * 1.2f, 130) else -1
        p.reset(); p.isAntiAlias = true
        if (!unlocked) p.colorFilter = grey

        // soft glow behind
        if (glow) {
            p.shader = RadialGradient(cx, cy, r * 1.9f, intArrayOf(withAlpha(t.base, 0.55f), withAlpha(t.base, 0f)), null, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r * 1.9f, p)
        }

        // ribbon tails
        for (side in listOf(-1f, 1f)) {
            path.reset()
            val bx = cx + side * r * 0.38f
            path.moveTo(bx - r * 0.30f, cy + r * 0.55f)
            path.lineTo(bx + r * 0.30f, cy + r * 0.55f)
            path.lineTo(bx + r * 0.30f + side * r * 0.12f, cy + r * 1.62f)
            path.lineTo(bx + side * r * 0.06f, cy + r * 1.38f)
            path.lineTo(bx - r * 0.30f + side * r * 0.12f, cy + r * 1.62f)
            path.close()
            p.shader = LinearGradient(bx, cy + r * 0.5f, bx, cy + r * 1.6f, t.base, t.dark, Shader.TileMode.CLAMP)
            c.drawPath(path, p)
            p.shader = null
            p.color = withAlpha(0xFFFFFFFF.toInt(), 0.18f)
            c.drawRect(bx - r * 0.06f, cy + r * 0.55f, bx + r * 0.06f, cy + r * 1.4f, p)
        }

        // scalloped rim (16 bumps)
        path.reset()
        val bumps = 16
        for (i in 0..bumps * 2) {
            val a = (i.toFloat() / (bumps * 2)) * 2 * PI - PI / 2
            val rr = if (i % 2 == 0) r * 1.13f else r * 1.02f
            val px = cx + (cos(a) * rr).toFloat(); val py = cy + (sin(a) * rr).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        p.shader = LinearGradient(cx, cy - r, cx, cy + r, t.light, t.dark, Shader.TileMode.CLAMP)
        c.drawPath(path, p)

        // coin
        p.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.45f, r * 1.5f,
            intArrayOf(t.light, t.base, t.dark), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 0.95f, p)
        // inner ring
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.06f
        p.color = withAlpha(t.light, 0.85f)
        c.drawCircle(cx, cy, r * 0.78f, p)
        p.strokeWidth = r * 0.025f
        p.color = withAlpha(t.dark, 0.5f)
        c.drawCircle(cx, cy, r * 0.72f, p)
        p.style = Paint.Style.FILL

        // number + "DAYS"
        val num = badge.days.toString()
        p.typeface = AppFonts.numbers
        p.textAlign = Paint.Align.CENTER
        p.textSize = r * (if (num.length >= 3) 0.62f else 0.78f)
        p.color = withAlpha(t.dark, 0.55f)
        c.drawText(num, cx, cy + r * 0.22f + r * 0.05f, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawText(num, cx, cy + r * 0.22f, p)
        p.typeface = AppFonts.ui
        p.textSize = r * 0.19f
        p.letterSpacing = 0.12f
        p.color = withAlpha(0xFFFFFFFF.toInt(), 0.92f)
        c.drawText(if (badge.days == 1) tr("DAY") else tr("DAYS"), cx, cy + r * 0.52f, p)
        p.letterSpacing = 0f
        p.typeface = null
        p.textAlign = Paint.Align.LEFT

        // gloss
        p.shader = LinearGradient(cx, cy - r, cx, cy, 0x70FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        rf.set(cx - r * 0.72f, cy - r * 0.9f, cx + r * 0.72f, cy - r * 0.05f)
        c.drawOval(rf, p)
        p.shader = null

        if (layer != -1) {
            c.restoreToCount(layer)
            // padlock
            val lx = cx + r * 0.72f; val ly = cy + r * 0.72f; val ls = r * 0.42f
            p.reset(); p.isAntiAlias = true
            p.color = 0xFF1F2937.toInt()
            c.drawCircle(lx, ly, ls * 0.75f, p)
            p.color = 0xFFE5E7EB.toInt()
            rf.set(lx - ls * 0.32f, ly - ls * 0.05f, lx + ls * 0.32f, ly + ls * 0.38f)
            c.drawRoundRect(rf, ls * 0.08f, ls * 0.08f, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = ls * 0.11f
            rf.set(lx - ls * 0.2f, ly - ls * 0.36f, lx + ls * 0.2f, ly + ls * 0.08f)
            c.drawArc(rf, 180f, 180f, false, p)
            p.style = Paint.Style.FILL
        }
        p.colorFilter = null
    }

    private fun withAlpha(c: Int, a: Float) = ((a * 255).toInt().coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)
}
