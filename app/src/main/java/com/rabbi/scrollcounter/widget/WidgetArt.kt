package com.rabbi.scrollcounter.widget

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.text.TextPaint
import com.rabbi.scrollcounter.face.FaceRenderer
import com.rabbi.scrollcounter.face.Mood
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** What the widget shows. */
data class WidgetState(
    val total: Int,
    val limit: Int,
    val streak: Int = 0,
    val paused: Boolean = false,
    /** Show this mood regardless of the count (used by the in-app demo while the number animates). */
    val forcedMood: Mood? = null,
) {
    val progress: Float get() = if (limit <= 0) 0f else (total.toFloat() / limit).coerceIn(0f, 1f)
    val mood: Mood get() = forcedMood ?: Mood.of(total, limit)
}

/**
 * Draws the home-screen widget — the same art is used by the real widget (as one crisp bitmap at
 * the phone's own resolution) and by the in-app preview, so what you see is what you get.
 *
 * Each mood is its own little scene: a fresh emerald morning, a calm ocean, a warm sunset,
 * a plum night with a moon, and a red alert — with a glowing progress ring, the 3D buddy peeking
 * up from the bottom, and a soft glass sheen.
 */
object WidgetArt {

    private class Scene(
        val top: Int, val bottom: Int, val glow: Int,
        val ringA: Int, val ringB: Int,
        val headline: String, val deco: Deco,
    )

    private enum class Deco { SPARKLES, BUBBLES, SUN, MOON, ALERT, NONE }

    private fun scene(s: WidgetState): Scene = if (s.paused) Scene(
        0xFF2B3036.toInt(), 0xFF13161A.toInt(), 0xFF94A3B8.toInt(),
        0xFFCBD5E1.toInt(), 0xFF94A3B8.toInt(), "Tracking paused", Deco.NONE
    ) else when (s.mood) {
        Mood.FRESH -> Scene(0xFF0F6B50.toInt(), 0xFF07291F.toInt(), 0xFF34D399.toInt(),
            0xFFB8F5DC.toInt(), 0xFF34D399.toInt(), "Feeling fresh!", Deco.SPARKLES)
        Mood.OKAY -> Scene(0xFF11507A.toInt(), 0xFF071E33.toInt(), 0xFF38BDF8.toInt(),
            0xFFC7ECFF.toInt(), 0xFF38BDF8.toInt(), "Nice balance", Deco.BUBBLES)
        Mood.TIRED -> Scene(0xFF8A4A10.toInt(), 0xFF2B1606.toInt(), 0xFFF59E0B.toInt(),
            0xFFFFE9A8.toInt(), 0xFFF59E0B.toInt(), "Take a break?", Deco.SUN)
        Mood.DRAINED -> Scene(0xFF6A1D45.toInt(), 0xFF1F0816.toInt(), 0xFFFB7185.toInt(),
            0xFFFFD0A8.toInt(), 0xFFFB7185.toInt(), "Put it down?", Deco.MOON)
        Mood.EXHAUSTED -> Scene(0xFF7F1414.toInt(), 0xFF240404.toInt(), 0xFFEF4444.toInt(),
            0xFFFFC2C2.toInt(), 0xFFEF4444.toInt(), "Limit reached", Deco.ALERT)
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()
    private val path = Path()
    private val black: Typeface get() = com.rabbi.scrollcounter.ui.theme.AppFonts.numbers
    private val bold: Typeface get() = com.rabbi.scrollcounter.ui.theme.AppFonts.textBold
    private val medium: Typeface get() = com.rabbi.scrollcounter.ui.theme.AppFonts.textMedium

    /** Renders the full widget into a bitmap of [wPx]×[hPx]; [dp] = pixels per dp. */
    fun bitmap(wPx: Int, hPx: Int, dp: Float, state: WidgetState): Bitmap {
        val bmp = Bitmap.createBitmap(wPx.coerceAtLeast(1), hPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), wPx.toFloat(), hPx.toFloat(), dp, state, 0f)
        return bmp
    }

    /**
     * Draws everything. [phase] (0..1, looping) only twinkles the decorations in the in-app
     * preview; the home-screen widget uses 0.
     */
    @Synchronized
    fun draw(c: Canvas, w: Float, h: Float, dp: Float, state: WidgetState, phase: Float, blink: Float = 0f, gazeX: Float = 0f, gazeY: Float = 0f) {
        val sc = scene(state)
        val radius = 26f * dp
        val wide = w >= h * 1.55f

        c.save()
        path.reset()
        rf.set(0f, 0f, w, h)
        path.addRoundRect(rf, radius, radius, Path.Direction.CW)
        c.clipPath(path)

        // ---- background: diagonal gradient + a big soft glow + rolling hills ----
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, 0f, w * 0.35f, h, sc.top, sc.bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        val gx = if (wide) w * 0.62f else w * 0.68f
        val gy = h * 0.58f
        p.shader = RadialGradient(gx, gy, maxOf(w, h) * 0.62f,
            intArrayOf(alpha(sc.glow, 0.42f), alpha(sc.glow, 0.12f), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        hill(c, w, h, 0.70f, 0.08f, 0.055f, phase)
        hill(c, w, h, 0.80f, -0.06f, 0.075f, phase)

        decorations(c, w, h, dp, sc, wide, phase)

        // ---- layout ----
        val pad = 14f * dp
        val u = min(w, h)
        val ringSize = if (wide) h * 0.72f else u * 0.46f
        val ringX = pad
        val ringY = if (wide) (h - ringSize) / 2f else pad
        ring(c, ringX, ringY, ringSize, state.progress, sc, dp, phase)

        // count inside the ring
        val cx = ringX + ringSize / 2
        val cy = ringY + ringSize / 2
        tp.reset(); tp.isAntiAlias = true
        tp.typeface = black
        tp.color = 0xFFFFFFFF.toInt()
        tp.textAlign = Paint.Align.CENTER
        val countText = compact(state.total)
        tp.textSize = ringSize * (if (countText.length >= 4) 0.24f else 0.29f)
        tp.setShadowLayer(6f * dp, 0f, 2f * dp, 0x55000000)
        c.drawText(countText, cx, cy + tp.textSize * 0.22f, tp)
        tp.clearShadowLayer()
        tp.typeface = medium
        tp.textSize = ringSize * 0.105f
        tp.color = 0xCCFFFFFF.toInt()
        c.drawText("of ${state.limit} reels", cx, cy + ringSize * 0.23f, tp)

        // ---- buddy peeking up from the bottom-right ----
        val faceR = if (wide) h * 0.26f else u * 0.19f
        val fx = w - faceR - 6f * dp
        val bob = if (state.mood == Mood.EXHAUSTED) 0f else sin(phase * 2 * PI).toFloat() * 1.5f * dp
        val fy = h - faceR * 0.55f + bob
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(fx, fy, faceR * 1.7f, intArrayOf(alpha(sc.glow, 0.45f), 0), null, Shader.TileMode.CLAMP)
        c.drawCircle(fx, fy, faceR * 1.7f, p)
        p.shader = null
        FaceRenderer.draw(c, fx, fy, faceR, if (state.paused) Mood.OKAY else state.mood, blink, gazeX, gazeY)

        // ---- headline + streak, bottom-left (square) or beside the ring (wide) ----
        tp.reset(); tp.isAntiAlias = true
        tp.textAlign = Paint.Align.LEFT
        val textX = if (wide) ringX + ringSize + 14f * dp else pad + 2f * dp
        val maxW = (fx - faceR - 6f * dp) - textX
        val headSize = if (wide) h * 0.13f else u * 0.10f
        val subSize = headSize * 0.64f
        tp.typeface = com.rabbi.scrollcounter.ui.theme.AppFonts.headline
        tp.textSize = headSize
        val lines = wrap(sc.headline, if (wide) w - pad - textX else maxW, 2)
        val blockH = lines.size * headSize * 1.08f + subSize * 1.6f
        // Square: sit on the bottom edge. Wide: centred next to the ring.
        var y = if (wide) (h - blockH) / 2f + headSize * 0.95f
        else h - pad + 1f * dp - subSize * 1.6f - (lines.size - 1) * headSize * 1.08f
        tp.color = 0xFFFFFFFF.toInt()
        tp.setShadowLayer(5f * dp, 0f, 1.5f * dp, 0x44000000)
        lines.forEach { c.drawText(it, textX, y, tp); y += headSize * 1.08f }
        tp.clearShadowLayer()

        val sub = when {
            state.paused -> "Tap to resume"
            state.mood == Mood.EXHAUSTED -> "Rest time"
            state.streak > 0 -> "${state.streak} day${if (state.streak == 1) "" else "s"}"
            else -> "Day 1"
        }
        tp.typeface = bold
        tp.textSize = subSize
        tp.color = 0xD9FFFFFF.toInt()
        // Our own drawn fire / sparkle instead of a system emoji.
        val glyph = when {
            state.paused || state.mood == Mood.EXHAUSTED -> null
            state.streak > 0 -> com.rabbi.scrollcounter.face.GlyphArt.Glyph.FLAME
            else -> com.rabbi.scrollcounter.face.GlyphArt.Glyph.SPARKLE
        }
        var tx = textX
        val baseY = y + subSize * 0.5f
        if (glyph != null) {
            val gs = subSize * 1.25f
            com.rabbi.scrollcounter.face.GlyphArt.draw(c, glyph, tx, baseY - gs * 0.86f, gs)
            tx += gs + subSize * 0.3f
        }
        c.drawText(ellipsize(sub, maxW - (tx - textX)), tx, baseY, tp)

        // ---- glass sheen + hairline border ----
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h * 0.5f, p)
        p.shader = null
        c.restore()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * dp
        p.color = 0x24FFFFFF
        rf.set(0.6f * dp, 0.6f * dp, w - 0.6f * dp, h - 0.6f * dp)
        c.drawRoundRect(rf, radius, radius, p)
        p.style = Paint.Style.FILL
    }

    // -------------------------------------------------------------------------- public pieces (in-app hero)

    /** The mood scene alone — gradient, glow, hills, sheen (no sparkles) — for the app's hero card. */
    @Synchronized
    fun drawScene(c: Canvas, w: Float, h: Float, dp: Float, state: WidgetState, phase: Float, radius: Float) {
        val sc = scene(state)
        c.save()
        path.reset(); rf.set(0f, 0f, w, h)
        path.addRoundRect(rf, radius, radius, Path.Direction.CW)
        c.clipPath(path)
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, 0f, w * 0.35f, h, sc.top, sc.bottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * 0.5f, h * 0.36f, maxOf(w, h) * 0.6f,
            intArrayOf(alpha(sc.glow, 0.40f), alpha(sc.glow, 0.10f), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        hill(c, w, h, 0.74f, 0.05f, 0.05f, phase)
        hill(c, w, h, 0.84f, -0.04f, 0.07f, phase)
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.45f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h * 0.45f, p)
        p.shader = null
        c.restore()
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * dp
        p.shader = LinearGradient(0f, 0f, 0f, h, 0x4DFFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP)
        rf.set(0.6f * dp, 0.6f * dp, w - 0.6f * dp, h - 0.6f * dp)
        c.drawRoundRect(rf, radius, radius, p)
        p.shader = null
        p.style = Paint.Style.FILL
    }

    /** The glowing progress ring alone, in the mood's colours. */
    @Synchronized
    fun drawRing(c: Canvas, x: Float, y: Float, size: Float, state: WidgetState, dp: Float, phase: Float, headDot: Boolean = true) =
        ring(c, x, y, size, state.progress, scene(state), dp, phase, headDot)

    /** The mood's glow colour (ARGB), used to tint the app's background. */
    fun glowColor(state: WidgetState): Int = scene(state).glow

    /** The mood's headline ("Feeling fresh!", "Put it down?"…). */
    fun headline(state: WidgetState): String = scene(state).headline

    // -------------------------------------------------------------------------- pieces

    private fun ring(c: Canvas, x: Float, y: Float, size: Float, progress: Float, sc: Scene, dp: Float, phase: Float, headDot: Boolean = true) {
        val stroke = size * 0.095f
        val inset = stroke / 2 + 2f * dp
        rf.set(x + inset, y + inset, x + size - inset, y + size - inset)
        val cx = rf.centerX(); val cy = rf.centerY(); val r = rf.width() / 2

        // inner disc for depth
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(cx, cy - r * 0.3f, r * 1.1f, 0x1AFFFFFF, 0x05000000, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r - stroke * 0.6f, p)
        p.shader = null

        // track
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.strokeCap = Paint.Cap.ROUND
        p.color = 0x26FFFFFF
        c.drawArc(rf, 0f, 360f, false, p)

        // tiny tick dots inside the track
        p.style = Paint.Style.FILL
        p.color = 0x1FFFFFFF
        for (i in 0 until 40) {
            val a = (i / 40f) * 2 * PI - PI / 2
            val rr = r - stroke * 1.15f
            c.drawCircle(cx + (cos(a) * rr).toFloat(), cy + (sin(a) * rr).toFloat(), 0.9f * dp, p)
        }

        val sweep = (progress * 360f).coerceAtLeast(if (progress > 0f) 6f else 0f)
        if (sweep > 0f) {
            val grad = SweepGradient(cx, cy, intArrayOf(sc.ringA, sc.ringB, sc.ringB), floatArrayOf(0f, (sweep / 360f).coerceAtLeast(0.01f), 1f))
            val m = Matrix(); m.setRotate(-90f, cx, cy); grad.setLocalMatrix(m)
            p.style = Paint.Style.STROKE
            p.strokeWidth = stroke
            p.strokeCap = Paint.Cap.ROUND
            // glow
            p.shader = grad
            p.alpha = 110
            p.maskFilter = BlurMaskFilter(stroke * 0.9f, BlurMaskFilter.Blur.NORMAL)
            c.drawArc(rf, -90f, sweep, false, p)
            p.maskFilter = null
            p.alpha = 255
            c.drawArc(rf, -90f, sweep, false, p)
            p.shader = null

            // glowing head dot (the app draws its animated buddy there instead)
            if (headDot) {
            val a = Math.toRadians((-90f + sweep).toDouble())
            val hx = cx + (cos(a) * r).toFloat()
            val hy = cy + (sin(a) * r).toFloat()
            p.style = Paint.Style.FILL
            val pulse = 1f + 0.15f * sin(phase * 2 * PI).toFloat()
            p.shader = RadialGradient(hx, hy, stroke * 1.4f * pulse, intArrayOf(0xCCFFFFFF.toInt(), 0), null, Shader.TileMode.CLAMP)
            c.drawCircle(hx, hy, stroke * 1.4f * pulse, p)
            p.shader = null
            p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(hx, hy, stroke * 0.30f, p)
            }
        }
        p.style = Paint.Style.FILL
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun hill(c: Canvas, w: Float, h: Float, base: Float, tilt: Float, a: Float, phase: Float) {
        val shift = sin(phase * 2 * PI).toFloat() * w * 0.02f
        path.reset()
        path.moveTo(0f, h * (base + tilt))
        path.cubicTo(w * 0.3f + shift, h * (base - 0.10f), w * 0.65f - shift, h * (base + 0.08f), w, h * (base - tilt))
        path.lineTo(w, h); path.lineTo(0f, h); path.close()
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, h * base, 0f, h, alpha(0xFFFFFF, a), alpha(0xFFFFFF, a * 0.2f), Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = null
    }

    private fun decorations(c: Canvas, w: Float, h: Float, dp: Float, sc: Scene, wide: Boolean, phase: Float) {
        val u = min(w, h)
        // Spots in the top-right area, clear of the ring and the text.
        val spots = if (wide) listOf(0.48f to 0.13f, 0.62f to 0.21f, 0.76f to 0.10f, 0.90f to 0.24f, 0.95f to 0.47f)
        else listOf(0.70f to 0.14f, 0.88f to 0.30f, 0.62f to 0.36f, 0.92f to 0.10f, 0.78f to 0.47f)
        fun tw(i: Int) = 0.65f + 0.35f * sin((phase + i * 0.23f) * 2 * PI).toFloat()

        when (sc.deco) {
            Deco.SPARKLES -> spots.forEachIndexed { i, (fx, fy) ->
                if (i % 2 == 1 && i != 3) heart(c, w * fx, h * fy, u * 0.035f * tw(i), 0xFFF9A8D4.toInt())
                else sparkle(c, w * fx, h * fy, u * (if (i == 0) 0.075f else 0.05f) * tw(i), 0xFFFDE68A.toInt())
            }
            Deco.BUBBLES -> spots.forEachIndexed { i, (fx, fy) ->
                if (i % 2 == 0) sparkle(c, w * fx, h * fy, u * 0.05f * tw(i), 0xFFE0F2FE.toInt())
                else bubble(c, w * fx, h * fy, u * 0.035f * tw(i))
            }
            Deco.SUN -> {
                val sx = if (wide) w * 0.78f else w * 0.80f
                val sy = h * 0.20f
                val r = u * 0.10f
                p.reset(); p.isAntiAlias = true
                p.shader = RadialGradient(sx, sy, r * 3f, intArrayOf(0x88FFD27A.toInt(), 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(sx, sy, r * 3f, p)
                p.shader = LinearGradient(sx, sy - r, sx, sy + r, 0xFFFFF1B8.toInt(), 0xFFF59E0B.toInt(), Shader.TileMode.CLAMP)
                c.drawCircle(sx, sy, r, p)
                p.shader = null
                sparkle(c, w * spots[2].first, h * spots[2].second, u * 0.04f * tw(2), 0xFFFFE9A8.toInt())
            }
            Deco.MOON -> {
                val mx = if (wide) w * 0.80f else w * 0.80f
                val my = h * 0.19f
                val r = u * 0.085f
                p.reset(); p.isAntiAlias = true
                p.shader = RadialGradient(mx, my, r * 3f, intArrayOf(0x66FDE68A, 0), null, Shader.TileMode.CLAMP)
                c.drawCircle(mx, my, r * 3f, p)
                p.shader = null
                val moon = Path().apply { addCircle(mx, my, r, Path.Direction.CW) }
                val cut = Path().apply { addCircle(mx + r * 0.55f, my - r * 0.35f, r * 0.9f, Path.Direction.CW) }
                moon.op(cut, Path.Op.DIFFERENCE)
                p.color = 0xFFFDE68A.toInt()
                c.drawPath(moon, p)
                listOf(0.62f to 0.12f, 0.95f to 0.40f, 0.66f to 0.40f, 0.55f to 0.28f).forEachIndexed { i, (fx, fy) ->
                    p.color = alpha(0xFFFFFF, 0.5f * tw(i))
                    c.drawCircle(w * fx, h * fy, (1.3f + i % 2) * dp, p)
                }
            }
            Deco.ALERT -> spots.take(4).forEachIndexed { i, (fx, fy) ->
                sparkle(c, w * fx, h * fy, u * 0.045f * tw(i), 0xFFFFB4B4.toInt())
            }
            Deco.NONE -> Unit
        }
    }

    private fun sparkle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(x, y, r * 1.8f, intArrayOf(alpha(color, 0.55f), 0), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 1.8f, p)
        p.shader = null
        path.reset()
        val k = r * 0.22f
        path.moveTo(x, y - r)
        path.quadTo(x + k, y - k, x + r, y)
        path.quadTo(x + k, y + k, x, y + r)
        path.quadTo(x - k, y + k, x - r, y)
        path.quadTo(x - k, y - k, x, y - r)
        path.close()
        p.color = color
        c.drawPath(path, p)
    }

    private fun heart(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        path.reset()
        path.moveTo(x, y + s * 0.9f)
        path.cubicTo(x - s * 1.6f, y - s * 0.2f, x - s * 0.7f, y - s * 1.3f, x, y - s * 0.45f)
        path.cubicTo(x + s * 0.7f, y - s * 1.3f, x + s * 1.6f, y - s * 0.2f, x, y + s * 0.9f)
        path.close()
        p.reset(); p.isAntiAlias = true
        p.color = color
        c.drawPath(path, p)
    }

    private fun bubble(c: Canvas, x: Float, y: Float, r: Float) {
        p.reset(); p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.18f
        p.color = 0x66E0F2FE
        c.drawCircle(x, y, r, p)
        p.style = Paint.Style.FILL
        p.color = 0x88FFFFFF.toInt()
        c.drawCircle(x - r * 0.35f, y - r * 0.35f, r * 0.22f, p)
    }

    // -------------------------------------------------------------------------- text helpers

    private fun wrap(text: String, maxW: Float, maxLines: Int): List<String> {
        val words = text.split(' ')
        val out = ArrayList<String>()
        var line = ""
        for (word in words) {
            val t = if (line.isEmpty()) word else "$line $word"
            if (tp.measureText(t) <= maxW || line.isEmpty()) line = t
            else { out += line; line = word }
        }
        if (line.isNotEmpty()) out += line
        return if (out.size <= maxLines) out else out.take(maxLines - 1) + ellipsize(out.drop(maxLines - 1).joinToString(" "), maxW)
    }

    private fun ellipsize(text: String, maxW: Float): String {
        if (tp.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && tp.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun compact(n: Int): String = when {
        n >= 10_000 -> "${n / 1000}k"
        else -> n.toString()
    }

    private fun alpha(c: Int, a: Float): Int = ((a * 255).toInt().coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)
}
