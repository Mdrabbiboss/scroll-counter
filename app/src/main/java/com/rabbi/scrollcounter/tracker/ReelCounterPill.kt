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
    private var pill: LinearLayout? = null
    private lateinit var face: FaceView
    private lateinit var countText: TextView

    private var visible = false
    val isShowing: Boolean get() = visible

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, service.resources.displayMetrics)

    /**
     * Show (or update) the pill. [bump] = a new reel was just counted.
     * The overlay window is created once and kept, so showing it again is instant — no window
     * is added or removed each time, which is what made it appear late or with a jerk.
     */
    fun show(count: Int, limit: Int, bump: Boolean) {
        val mood = Mood.of(count, limit)
        if (root == null) attach()
        val p = pill ?: return
        val firstAppearance = !visible
        visible = true

        face.mood = mood
        countText.text = count.toString()
        val ratio = if (limit > 0) count.toFloat() / limit else 0f
        // Frosted, slightly see-through glass — classy, never a solid block over the video.
        val (c1, c2) = when {
            ratio >= 1f -> 0xB87F1D1D.toInt() to 0xB8DC2626.toInt()
            ratio >= 0.8f -> 0xB87C2D12.toInt() to 0xB8EA580C.toInt()
            else -> 0xB0061F1C.toInt() to 0xB00D5F55.toInt()
        }
        (p.background as GradientDrawable).apply {
            colors = intArrayOf(c1, c2)
            setStroke(dp(1f).toInt(), 0x40FFFFFF)
        }
        countText.setTextColor(if (ratio >= 0.8f) 0xFFFFFFFF.toInt() else 0xFFFFD873.toInt())

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

    private fun attach() {
        val ctx = service
        face = FaceView(ctx)
        countText = TextView(ctx).apply {
            textSize = 23f
            typeface = com.rabbi.scrollcounter.ui.theme.AppFonts.numbers
            letterSpacing = 0.01f
            setShadowLayer(dp(3f), 0f, dp(1f), 0x66000000)
            includeFontPadding = false
            setTextColor(0xFFF8C33A.toInt())
        }
        val pillView = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5f).toInt(), dp(4f).toInt(), dp(16f).toInt(), dp(4f).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xF2052A26.toInt(), 0xF20D5F55.toInt())).apply {
                cornerRadius = dp(30f)
            }
            elevation = dp(4f)
            visibility = android.view.View.INVISIBLE
            addView(face, LinearLayout.LayoutParams(dp(34f).toInt(), dp(34f).toInt()))
            addView(countText, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(7f).toInt() })
        }
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
}
