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
 * It is an accessibility overlay (no "display over other apps" permission). Drag it anywhere
 * (the spot is remembered); tap it for a card with today's numbers ([PillPanel]), where it can be
 * made smaller / bigger or hidden until you leave Reels. Only the counter itself takes touches —
 * the video around it works as normal.
 */
class ReelCounterPill(private val service: AccessibilityService) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private var root: FrameLayout? = null
    private var pill: android.view.View? = null
    private lateinit var face: FaceView
    private lateinit var countText: RollingNumber

    private var visible = false
    val isShowing: Boolean get() = visible

    /** Today's numbers for the tap card, the "Open app" action and the size buttons (set by the service). */
    var info: (() -> PillInfo)? = null
    var onOpenApp: (() -> Unit)? = null
    var onSizeChange: ((Int) -> Unit)? = null

    private var lp: WindowManager.LayoutParams? = null
    private val panel = PillPanel(service, this)
    /** Hidden by the user until they leave Reels (still counting underneath). */
    private var sessionHidden = false
    /** Hidden by the user for a while (wall-clock time, kept across apps and restarts). */
    private val hiddenUntil: Long get() = prefs.getLong(KEY_HIDE_UNTIL, 0L)
    private val snoozed: Boolean get() = sessionHidden || System.currentTimeMillis() < hiddenUntil
    /** The view is currently faded out because of a hide. */
    private var hiddenNow = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val unhide = Runnable { if (visible && hiddenNow && !snoozed) reveal() }
    private val prefs = service.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)

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
        countText.setNumber(count, roll = bump && !firstAppearance)
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
            countText.textColor = if (ratio >= 0.8f) 0xFFFFFFFF.toInt() else 0xFF5A3A12.toInt()
        } ?: run { countText.textColor = if (ratio >= 0.8f) 0xFFFFFFFF.toInt() else 0xFFFFD873.toInt() }

        if (snoozed) {
            // Still counting, just out of sight; come back by itself when the time is up.
            if (!hiddenNow) { hiddenNow = true; p.visibility = android.view.View.INVISIBLE; setTouchable(false) }
            scheduleUnhide()
            return
        }
        if (firstAppearance || hiddenNow) {
            reveal()
        } else if (bump) {
            p.animate().cancel()
            p.alpha = REST_ALPHA; p.translationY = 0f
            p.scaleX = 1.07f; p.scaleY = 1.07f
            p.animate().scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(DecelerateInterpolator(1.6f)).withLayer().start()
        }
        if (bump) face.post { face.react() }
    }

    private fun reveal() {
        val p = pill ?: return
        hiddenNow = false
        setTouchable(true)
        p.animate().cancel()
        p.visibility = android.view.View.VISIBLE
        p.translationY = -dp(14f); p.alpha = 0f
        p.scaleX = 0.9f; p.scaleY = 0.9f
        p.animate().translationY(0f).alpha(REST_ALPHA).scaleX(1f).scaleY(1f)
            .setDuration(300).setInterpolator(DecelerateInterpolator(2f))
            .withLayer().start()
    }

    private fun scheduleUnhide() {
        handler.removeCallbacks(unhide)
        val left = hiddenUntil - System.currentTimeMillis()
        if (!sessionHidden && left > 0) handler.postDelayed(unhide, left + 300)
    }

    fun hide() {
        val p = pill ?: return
        panel.close()
        sessionHidden = false               // "until I leave Reels" ends here; a timed hide goes on
        handler.removeCallbacks(unhide)
        if (!visible) return
        visible = false
        setTouchable(false)
        if (hiddenNow) { hiddenNow = false; p.visibility = android.view.View.INVISIBLE; return }
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

    /**
     * "Hide counter": out of sight (and out of the way) while counting goes on — until [untilMs]
     * (wall-clock), or until you leave Reels when [untilMs] is null. It comes back by itself.
     */
    fun snooze(untilMs: Long?) {
        val p = pill ?: return
        if (untilMs == null) sessionHidden = true
        else prefs.edit().putLong(KEY_HIDE_UNTIL, untilMs).apply()
        panel.close()
        if (!visible) return
        hiddenNow = true
        setTouchable(false)
        p.animate().cancel()
        p.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(220).setInterpolator(DecelerateInterpolator())
            .withEndAction { if (hiddenNow) p.visibility = android.view.View.INVISIBLE }.start()
        scheduleUnhide()
    }

    /** Where the counter is on screen: x, y, width, height. */
    fun anchorRect(): IntArray? {
        val r = root ?: return null
        val p = pill ?: return null
        val loc = IntArray(2)
        p.getLocationOnScreen(loc)
        return intArrayOf(loc[0], loc[1], p.width, p.height).takeIf { r.isAttachedToWindow }
    }

    private fun setTouchable(on: Boolean) {
        val l = lp ?: return
        val r = root ?: return
        val f = if (on) l.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                else l.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (f == l.flags) return
        l.flags = f
        runCatching { wm?.updateViewLayout(r, l) }
    }

    private fun screen(): Pair<Int, Int> {
        val dm = service.resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    /** Put the window at the remembered spot (centre-x and top as fractions of the screen), or top centre. */
    private fun place() {
        val r = root ?: return
        val l = lp ?: return
        val (sw, sh) = screen()
        val w = r.width.takeIf { it > 0 } ?: r.measuredWidth
        val h = r.height.takeIf { it > 0 } ?: r.measuredHeight
        val cx = prefs.getFloat("cx", 0.5f)
        val top = if (prefs.contains("ty")) (prefs.getFloat("ty", 0f) * sh).toInt() else statusBarHeight() + dp(6f).toInt() - dp(5f).toInt()
        l.x = (cx * sw - w / 2f).toInt().coerceIn(0, (sw - w).coerceAtLeast(0))
        l.y = top.coerceIn(0, (sh - h).coerceAtLeast(0))
        runCatching { wm?.updateViewLayout(r, l) }
    }

    private fun savePosition() {
        val r = root ?: return
        val l = lp ?: return
        val (sw, sh) = screen()
        prefs.edit().putFloat("cx", (l.x + r.width / 2f) / sw).putFloat("ty", l.y.toFloat() / sh).apply()
    }

    /** Drag to move it anywhere; a tap opens the card. */
    private fun touchHandler() = object : android.view.View.OnTouchListener {
        private var downX = 0f; private var downY = 0f
        private var startX = 0; private var startY = 0
        private var dragging = false
        private val slop = android.view.ViewConfiguration.get(service).scaledTouchSlop

        override fun onTouch(v: android.view.View, e: android.view.MotionEvent): Boolean {
            val l = lp ?: return false
            val r = root ?: return false
            val p = pill ?: return false
            if (!visible || snoozed) return false
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = l.x; startY = l.y; dragging = false
                    p.animate().cancel()
                    p.animate().scaleX(0.95f).scaleY(0.95f).setDuration(90).start()
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && kotlin.math.hypot(dx, dy) > slop) {
                        dragging = true
                        panel.close()
                        r.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        p.animate().cancel()
                        p.animate().scaleX(1.12f).scaleY(1.12f).alpha(1f).setDuration(140).setInterpolator(DecelerateInterpolator()).start()
                    }
                    if (dragging) {
                        val (sw, sh) = screen()
                        l.x = (startX + dx).toInt().coerceIn(0, (sw - r.width).coerceAtLeast(0))
                        l.y = (startY + dy).toInt().coerceIn(0, (sh - r.height).coerceAtLeast(0))
                        runCatching { wm?.updateViewLayout(r, l) }
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    p.animate().cancel()
                    p.animate().scaleX(1f).scaleY(1f).alpha(REST_ALPHA).setDuration(220)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
                    if (dragging) savePosition()
                    else if (e.actionMasked == android.view.MotionEvent.ACTION_UP &&
                        android.os.SystemClock.uptimeMillis() - panel.closedAt > 350) {
                        r.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        face.react()
                        anchorRect()?.let { a -> panel.toggle(a[0], a[1], a[2], a[3]) }
                    }
                    dragging = false
                }
            }
            return true
        }
    }

    private fun glass(radius: Float) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xC0061F1C.toInt(), 0xC00D5F55.toInt())).apply {
        cornerRadius = radius
        setStroke(dp(1f).toInt(), 0x40FFFFFF)
        tints += this
    }

    private fun counter(sizeSp: Float) = RollingNumber(service).apply {
        setTextSizeSp(sizeSp)
        setTypeface(com.rabbi.scrollcounter.ui.theme.AppFonts.numbers)
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
                countText = counter(13f).apply {
                    setPadding(dp(9f).toInt(), dp(2.5f).toInt(), dp(9f).toInt(), dp(2.5f).toInt())
                    minimumWidth = dp(30f).toInt()
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
                countText = counter(22f).apply { setShadow(dp(3f), dp(1f), 0x66000000) }
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
                    countText = counter(28f).apply { setShadow(dp(3f), dp(1f), 0x66000000) }
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
                countText = counter(23f).apply { setShadow(dp(3f), dp(1f), 0x66000000) }
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
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or      // touchable only while it's showing
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = statusBarHeight() + dp(1f).toInt()
        }
        frame.setOnTouchListener(touchHandler())
        try {
            wm?.addView(frame, lp)
            root = frame
            pill = pillView
            this.lp = lp
            frame.post { place() }
            frame.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol) place() }
        } catch (e: Exception) {
            root = null
            pill = null
            this.lp = null
        }
    }

    private fun detach() {
        panel.close()
        val r = root ?: return
        try { wm?.removeView(r) } catch (_: Exception) { }
        root = null
        pill = null
        lp = null
        visible = false
    }

    companion object {
        /** Resting opacity: a touch see-through so it sits lightly over the video. */
        private const val REST_ALPHA = 0.9f
        const val PREFS = "pill_position"
        const val KEY_HIDE_UNTIL = "hide_until"
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
