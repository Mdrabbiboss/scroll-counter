package com.rabbi.scrollcounter.tracker

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rabbi.scrollcounter.face.FaceView
import com.rabbi.scrollcounter.face.Mood

/**
 * The small counter that sits at the top of the screen while you watch Reels / Shorts:
 * the scroll buddy and today's reel count. It bumps on every new reel, changes colour as you
 * near your limit, and slides away when you leave the reels feed.
 *
 * It is an accessibility overlay (no "display over other apps" permission) and is not
 * touchable, so it never gets in the way of the video.
 */
class ReelCounterPill(private val service: AccessibilityService) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var pill: android.view.View? = null
    private lateinit var face: FaceView
    private lateinit var countText: TextView

    private var visible = false
    val isShowing: Boolean get() = visible

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, service.resources.displayMetrics)

    /**
     * Show (or update) the counter. [bump] = a new reel was just counted.
     * The overlay window is created once and kept, so showing it again is instant — no window
     * is added or removed each time, which is what made it appear late or with a jerk.
     * [style] is the size chosen in Settings, and each size has its own shape:
     * 0 = bubble (round buddy with the count badge under it), 1 = pill, 2 = tile with a
     * progress bar, 3 = ring (buddy inside a progress ring, count and limit beside it).
     */
    private var style = 1
    private val tints = mutableListOf<GradientDrawable>()
    private var badge: GradientDrawable? = null
    private var limitText: TextView? = null
    private var meter: Meter? = null

    fun show(count: Int, limit: Int, bump: Boolean, style: Int = 1) {
        val mood = Mood.of(count, limit)
        if (root != null && style != this.style) { detach() }
        this.style = style.coerceIn(0, 3)
        if (root == null) attach()
        val p = pill ?: return
        val firstAppearance = !visible
        visible = true

        face.mood = mood
        countText.text = count.toString()
        limitText?.text = "/$limit"
        val ratio = if (limit > 0) count.toFloat() / limit else 0f
        // Frosted, slightly see-through glass — classy, never a solid block over the video.
        val (c1, c2) = when {
            ratio >= 1f -> 0xC87F1D1D.toInt() to 0xC8DC2626.toInt()
            ratio >= 0.8f -> 0xC87C2D12.toInt() to 0xC8EA580C.toInt()
            else -> 0xC0061F1C.toInt() to 0xC00D5F55.toInt()
        }
        tints.forEach { it.colors = intArrayOf(c1, c2) }
        val accent = when {
            ratio >= 1f -> 0xFFFF6B6B.toInt()
            ratio >= 0.8f -> 0xFFFFA24C.toInt()
            else -> 0xFFF8C33A.toInt()
        }
        meter?.let { it.fraction = ratio.coerceIn(0f, 1f); it.color = accent }
        badge?.let { b ->
            // Bubble: a cream badge like a sticker; it turns orange / red near and over the limit.
            b.setColor(when { ratio >= 1f -> 0xFFDC2626.toInt(); ratio >= 0.8f -> 0xFFEA580C.toInt(); else -> 0xFFFFF1D0.toInt() })
            countText.setTextColor(if (ratio >= 0.8f) 0xFFFFFFFF.toInt() else 0xFF5A3A12.toInt())
        } ?: countText.setTextColor(if (ratio >= 0.8f) 0xFFFFFFFF.toInt() else 0xFFFFD873.toInt())

        if (firstAppearance) {
            p.animate().cancel()
            p.visibility = android.view.View.VISIBLE
            p.translationY = -dp(14f); p.alpha = 0f
            p.scaleX = 0.9f; p.scaleY = 0.9f
            p.animate().translationY(0f).alpha(REST_ALPHA).scaleX(1f).scaleY(1f)
                .setDuration(300).setInterpolator(DecelerateInterpolator(2f))
                .withLayer().start()
        } else if (bump) {
            p.animate().cancel()
            p.alpha = REST_ALPHA; p.translationY = 0f
            p.scaleX = 1.07f; p.scaleY = 1.07f
            p.animate().scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(DecelerateInterpolator(1.6f)).withLayer().start()
            countText.translationY = dp(5f); countText.alpha = 0.35f
            countText.animate().translationY(0f).alpha(1f).setDuration(200)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        if (bump) face.post { face.react() }
    }

    fun hide() {
        val p = pill ?: return
        if (!visible) return
        visible = false
        p.animate().cancel()
        p.animate().translationY(-dp(10f)).alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withLayer()
            .withEndAction { if (!visible) p.visibility = android.view.View.INVISIBLE }
            .start()
    }

    fun destroy() {
        detach()
    }

    private fun glass(radius: Float) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xC0061F1C.toInt(), 0xC00D5F55.toInt())).apply {
        cornerRadius = radius
        setStroke(dp(1f).toInt(), 0x40FFFFFF)
        tints += this
    }

    private fun number(sizeSp: Float) = TextView(service).apply {
        textSize = sizeSp
        typeface = com.rabbi.scrollcounter.ui.theme.AppFonts.numbers
        letterSpacing = 0.01f
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTextColor(0xFFF8C33A.toInt())
    }

    private fun attach() {
        val ctx = service
        tints.clear(); badge = null; limitText = null; meter = null
        face = FaceView(ctx)
        val content: android.view.View = when (style) {
            // 0 — Bubble: the buddy in a round glass bubble, the count on a cream badge under it.
            0 -> FrameLayout(ctx).apply {
                clipChildren = false
                val circle = FrameLayout(ctx).apply {
                    background = glass(dp(30f)).apply { shape = GradientDrawable.OVAL }
                    elevation = dp(4f)
                    addView(face, FrameLayout.LayoutParams(dp(38f).toInt(), dp(38f).toInt(), Gravity.CENTER))
                }
                addView(circle, FrameLayout.LayoutParams(dp(48f).toInt(), dp(48f).toInt(), Gravity.CENTER_HORIZONTAL))
                countText = number(13f).apply {
                    setPadding(dp(9f).toInt(), dp(2.5f).toInt(), dp(9f).toInt(), dp(2.5f).toInt())
                    minWidth = dp(30f).toInt()
                    background = GradientDrawable().apply {
                        cornerRadius = dp(20f)
                        setStroke(dp(1.5f).toInt(), 0xFF3A2A12.toInt())
                        badge = this
                    }
                    elevation = dp(5f)
                }
                addView(countText, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(38f).toInt() })
            }
            // 2 — Tile: a rounded square with the buddy, the count and a small progress bar.
            2 -> LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(10f).toInt(), dp(7f).toInt(), dp(10f).toInt(), dp(8f).toInt())
                background = glass(dp(20f))
                elevation = dp(4f)
                addView(face, LinearLayout.LayoutParams(dp(42f).toInt(), dp(42f).toInt()))
                countText = number(22f).apply { setShadowLayer(dp(3f), 0f, dp(1f), 0x66000000) }
                addView(countText, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3f).toInt() })
                meter = Meter(ctx, ring = false)
                addView(meter, LinearLayout.LayoutParams(dp(46f).toInt(), dp(5f).toInt()).apply { topMargin = dp(6f).toInt() })
            }
            // 3 — Ring: the buddy inside a progress ring, then the count over "/limit".
            3 -> LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5f).toInt(), dp(5f).toInt(), dp(18f).toInt(), dp(5f).toInt())
                background = glass(dp(40f))
                elevation = dp(4f)
                val ringBox = FrameLayout(ctx).apply {
                    meter = Meter(ctx, ring = true)
                    addView(meter, FrameLayout.LayoutParams(-1, -1))
                    addView(face, FrameLayout.LayoutParams(dp(42f).toInt(), dp(42f).toInt(), Gravity.CENTER))
                }
                addView(ringBox, LinearLayout.LayoutParams(dp(56f).toInt(), dp(56f).toInt()))
                val col = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    countText = number(28f).apply { setShadowLayer(dp(3f), 0f, dp(1f), 0x66000000) }
                    addView(countText, LinearLayout.LayoutParams(-2, -2))
                    limitText = number(12f).apply { setTextColor(0xB3FFFFFF.toInt()) }
                    addView(limitText, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(1f).toInt() })
                }
                addView(col, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(9f).toInt() })
            }
            // 1 — Pill: buddy and count side by side.
            else -> LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5f).toInt(), dp(4f).toInt(), dp(16f).toInt(), dp(4f).toInt())
                background = glass(dp(30f))
                elevation = dp(4f)
                addView(face, LinearLayout.LayoutParams(dp(34f).toInt(), dp(34f).toInt()))
                countText = number(23f).apply { setShadowLayer(dp(3f), 0f, dp(1f), 0x66000000) }
                addView(countText, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(7f).toInt() })
            }
        }
        content.visibility = android.view.View.INVISIBLE
        val pillView = content
        val frame = FrameLayout(ctx).apply {
            val m = dp(10f).toInt()
            setPadding(m, m / 2, m, m)
            clipChildren = false
            clipToPadding = false
            addView(pillView, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeight() + dp(6f).toInt()
        }
        try {
            wm?.addView(frame, lp)
            root = frame
            pill = pillView
        } catch (e: Exception) {
            root = null
            pill = null
        }
    }

    private fun detach() {
        val r = root ?: return
        try { wm?.removeView(r) } catch (_: Exception) { }
        root = null
        pill = null
        visible = false
    }

    companion object {
        /** Resting opacity: a touch see-through so it sits lightly over the video. */
        private const val REST_ALPHA = 0.9f
    }

    private fun statusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else dp(24f).toInt()
    }

    /** Progress of today's count toward the limit: a thin bar (tile) or a ring (ring style). */
    private class Meter(context: android.content.Context, private val ring: Boolean) : android.view.View(context) {
        var fraction = 0f
            set(v) { field = v; invalidate() }
        var color = 0xFFF8C33A.toInt()
            set(v) { field = v; invalidate() }
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { strokeCap = android.graphics.Paint.Cap.ROUND }

        override fun onDraw(canvas: android.graphics.Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            if (ring) {
                val sw = w * 0.075f
                paint.style = android.graphics.Paint.Style.STROKE; paint.strokeWidth = sw
                val r = android.graphics.RectF(sw / 2, sw / 2, w - sw / 2, h - sw / 2)
                paint.color = 0x33FFFFFF
                canvas.drawOval(r, paint)
                paint.color = color
                if (fraction > 0f) canvas.drawArc(r, -90f, 360f * fraction.coerceAtLeast(0.02f), false, paint)
            } else {
                paint.style = android.graphics.Paint.Style.FILL
                val rad = h / 2
                paint.color = 0x33FFFFFF
                canvas.drawRoundRect(0f, 0f, w, h, rad, rad, paint)
                paint.color = color
                if (fraction > 0f) canvas.drawRoundRect(0f, 0f, (w * fraction).coerceAtLeast(h), h, rad, rad, paint)
            }
        }
    }
}
