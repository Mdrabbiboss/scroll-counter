package com.rabbi.scrollcounter.share

import com.rabbi.scrollcounter.ui.tr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.content.FileProvider
import com.rabbi.scrollcounter.data.Badges
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.face.BadgeArt
import com.rabbi.scrollcounter.face.FaceRenderer
import com.rabbi.scrollcounter.face.GlyphArt
import com.rabbi.scrollcounter.face.Mood
import com.rabbi.scrollcounter.ui.formatDuration
import com.rabbi.scrollcounter.ui.theme.AppFonts
import com.rabbi.scrollcounter.widget.WidgetArt
import com.rabbi.scrollcounter.widget.WidgetState
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Beautiful share images (1080 × 1350, the Instagram / Facebook portrait size):
 *  • [renderDay]   — today's reels, the buddy's mood, watch time, streak and best badge.
 *  • [renderBadge] — a freshly earned streak badge.
 * [share] hands the image to any app (Facebook, WhatsApp, Instagram…) through the share sheet.
 */
object ShareCard {
    const val W = 1080
    const val H = 1350
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rf = RectF()

    fun renderDay(context: Context): Bitmap {
        val store = ScrollStore.get(context)
        AppFonts.load(context)
        val today = store.dayStats(LocalDate.now())
        val total = today.totalScrolls
        val limit = store.dailyLimit
        val streak = runCatching { store.streak() }.getOrDefault(0)
        val state = WidgetState(total, limit, streak)
        val mood = Mood.of(total, limit)

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        WidgetArt.drawScene(c, W.toFloat(), H.toFloat(), 3f, state, 0f, 0f)

        header(c, LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")))

        // Mood headline
        text(c, WidgetArt.headline(state).uppercase(), W / 2f, 300f, 86f, AppFonts.headline, 0xFFFFFFFF.toInt(), shadow = true)

        // Buddy
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(W / 2f, 500f, 260f, intArrayOf(0x55FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        c.drawCircle(W / 2f, 500f, 260f, p)
        p.shader = null
        FaceRenderer.draw(c, W / 2f, 500f, 150f, mood, 0f, 0f, 0f, 1.2f)

        // Big count
        text(c, "$total", W / 2f, 900f, 250f, AppFonts.numbers, 0xFFFFFFFF.toInt(), shadow = true)
        text(c, tr("reels today · limit %1\$s", limit), W / 2f, 975f, 42f, AppFonts.ui, 0xD9FFFFFF.toInt())

        // Three glass stats
        val secs = today.totalSeconds
        val best = store.badgeDates().keys.maxOrNull()?.let { Badges.of(it) }
        val boxW = 300f; val gap = 30f; val top = 1040f; val bh = 170f
        val x0 = (W - (boxW * 3 + gap * 2)) / 2f
        stat(c, x0, top, boxW, bh, if (secs == 0) tr("0 min") else formatDuration(secs), tr("watch time")) { cx, cy -> clock(c, cx, cy, 22f) }
        stat(c, x0 + boxW + gap, top, boxW, bh, if (streak == 1) tr("1 day") else tr("%1\$s days", streak), tr("streak")) { cx, cy ->
            GlyphArt.draw(c, GlyphArt.Glyph.FLAME, cx - 24f, cy - 26f, 48f, dim = streak == 0)
        }
        stat(c, x0 + (boxW + gap) * 2, top, boxW, bh, best?.let { tr(it.title) } ?: "—", tr("best badge")) { cx, cy ->
            val b = best ?: Badges.ALL.first()
            BadgeArt.draw(c, b, cx - 34f, cy - 30f, 68f, unlocked = best != null, glow = false)
        }

        footer(c)
        return bmp
    }

    fun renderBadge(context: Context, badge: Badges.Badge): Bitmap {
        AppFonts.load(context)
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val t = badge.tier
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(0f, 0f, W * 0.3f, H.toFloat(), mix(t.dark, 0xFF0B1220.toInt(), 0.35f), 0xFF05070C.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        // light rays
        val cx = W / 2f; val cy = 620f
        for (i in 0 until 18) {
            val a0 = i / 18.0 * 2 * PI
            val a1 = a0 + PI / 36
            val path = android.graphics.Path()
            path.moveTo(cx, cy)
            path.lineTo(cx + (cos(a0) * 1100).toFloat(), cy + (sin(a0) * 1100).toFloat())
            path.lineTo(cx + (cos(a1) * 1100).toFloat(), cy + (sin(a1) * 1100).toFloat())
            path.close()
            p.shader = RadialGradient(cx, cy, 900f, intArrayOf(withAlpha(t.light, 0.20f), withAlpha(t.light, 0f)), null, Shader.TileMode.CLAMP)
            c.drawPath(path, p)
        }
        p.shader = RadialGradient(cx, cy, 520f, intArrayOf(withAlpha(t.base, 0.55f), 0), null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, 520f, p)
        p.shader = null

        header(c, null)
        text(c, tr("BADGE UNLOCKED"), W / 2f, 260f, 46f, AppFonts.ui, withAlpha(t.light, 0.95f), spacing = 0.18f)
        BadgeArt.draw(c, badge, cx - 330f, cy - 280f, 660f, unlocked = true)
        text(c, tr(badge.title).uppercase(), W / 2f, 1080f, 92f, AppFonts.headline, 0xFFFFFFFF.toInt(), shadow = true)
        text(c, tr(badge.description), W / 2f, 1150f, 42f, AppFonts.ui, 0xD9FFFFFF.toInt())
        footer(c)
        return bmp
    }

    /** Opens the share sheet with [bmp] and a short message. */
    fun share(context: Context, bmp: Bitmap, message: String) {
        runCatching {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "scroll-counter.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, message)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---------------------------------------------------------------- pieces

    private fun header(c: Canvas, date: String?) {
        // little buddy + app name
        FaceRenderer.draw(c, 112f, 112f, 34f, Mood.FRESH)
        p.reset(); p.isAntiAlias = true
        p.typeface = AppFonts.ui
        p.textSize = 40f
        p.color = 0xFFFFFFFF.toInt()
        c.drawText("Scroll Counter", 166f, 126f, p)
        if (date != null) {
            p.textAlign = Paint.Align.RIGHT
            p.textSize = 34f
            p.color = 0xB3FFFFFF.toInt()
            c.drawText(date, W - 70f, 124f, p)
            p.textAlign = Paint.Align.LEFT
        }
    }

    private fun footer(c: Canvas) {
        p.reset(); p.isAntiAlias = true
        p.color = 0x26FFFFFF
        rf.set(W / 2f - 330f, H - 130f, W / 2f + 330f, H - 58f)
        c.drawRoundRect(rf, 36f, 36f, p)
        text(c, tr("Tracking my reels with Scroll Counter"), W / 2f, H - 81f, 32f, AppFonts.ui, 0xE6FFFFFF.toInt())
    }

    private inline fun stat(c: Canvas, x: Float, y: Float, w: Float, h: Float, value: String, label: String, icon: (Float, Float) -> Unit) {
        p.reset(); p.isAntiAlias = true
        p.color = 0x24FFFFFF
        rf.set(x, y, x + w, y + h)
        c.drawRoundRect(rf, 34f, 34f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0x33FFFFFF
        c.drawRoundRect(rf, 34f, 34f, p)
        p.style = Paint.Style.FILL
        icon(x + w / 2f, y + 46f)
        text(c, fit(value, w - 30f, 40f), x + w / 2f, y + 118f, 40f, AppFonts.ui, 0xFFFFFFFF.toInt())
        text(c, label.uppercase(), x + w / 2f, y + 152f, 24f, AppFonts.ui, 0xB3FFFFFF.toInt(), spacing = 0.12f)
    }

    private fun clock(c: Canvas, cx: Float, cy: Float, r: Float) {
        p.reset(); p.isAntiAlias = true
        p.shader = LinearGradient(cx, cy - r, cx, cy + r, 0xFFBAE6FD.toInt(), 0xFF38BDF8.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.color = 0xFF0C4A6E.toInt(); p.strokeWidth = r * 0.16f; p.strokeCap = Paint.Cap.ROUND
        c.drawLine(cx, cy, cx, cy - r * 0.6f, p)
        c.drawLine(cx, cy, cx + r * 0.45f, cy + r * 0.1f, p)
        p.strokeCap = Paint.Cap.BUTT
    }

    private fun fit(s: String, maxW: Float, size: Float): String {
        p.typeface = AppFonts.ui; p.textSize = size
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.length > 2 && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, tf: android.graphics.Typeface, color: Int, shadow: Boolean = false, spacing: Float = 0f) {
        p.reset(); p.isAntiAlias = true
        p.typeface = tf; p.textSize = size; p.textAlign = Paint.Align.CENTER; p.letterSpacing = spacing
        // shrink to fit the card
        while (p.measureText(s) > W - 120f && p.textSize > 20f) p.textSize -= 2f
        if (shadow) p.setShadowLayer(18f, 0f, 8f, 0x66000000)
        p.color = color
        c.drawText(s, x, y, p)
        p.clearShadowLayer()
        p.letterSpacing = 0f
        p.textAlign = Paint.Align.LEFT
    }

    private fun withAlpha(c: Int, a: Float) = ((a * 255).toInt().coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)
    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, s: Int) = (x shr s) and 0xFF
        fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt()
        return (0xFF shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }
}
