package com.rabbi.scrollcounter.tracker

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
    private var hiding = false

    val isShowing: Boolean get() = root != null && !hiding

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, service.resources.displayMetrics)

    /** Show (or update) the pill. [bump] = a new reel was just counted. */
    fun show(count: Int, limit: Int, bump: Boolean) {
        val mood = Mood.of(count, limit)
        val firstAppearance = root == null || hiding
        if (root == null) attach()
        val p = pill ?: return
        hiding = false

        face.mood = mood
        countText.text = count.toString()
        val ratio = if (limit > 0) count.toFloat() / limit else 0f
        val (c1, c2) = when {
            ratio >= 1f -> 0xF2B91C1C.toInt() to 0xF2E5484D.toInt()
            ratio >= 0.8f -> 0xF29A3412.toInt() to 0xF2EA580C.toInt()
            else -> 0xF2052A26.toInt() to 0xF20D5F55.toInt()
        }
        (p.background as GradientDrawable).apply {
            colors = intArrayOf(c1, c2)
            setStroke(dp(1.2f).toInt(), (mood.accent and 0x00FFFFFF) or (0x99 shl 24))
        }
        countText.setTextColor(if (ratio >= 0.8f) 0xFFFFFFFF.toInt() else 0xFFF8C33A.toInt())

        p.animate().cancel()
        if (firstAppearance) {
            p.translationY = -dp(80f); p.alpha = 0f
            p.scaleX = 0.8f; p.scaleY = 0.8f
            p.animate().translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(420).setInterpolator(OvershootInterpolator(1.4f)).start()
        } else if (bump) {
            p.scaleX = 1.12f; p.scaleY = 1.12f
            p.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(OvershootInterpolator(2.5f)).start()
            countText.translationY = dp(6f); countText.alpha = 0.3f
            countText.animate().translationY(0f).alpha(1f).setDuration(220).start()
        }
        if (bump) face.post { face.react() }
    }

    fun hide() {
        val p = pill ?: return
        if (hiding) return
        hiding = true
        p.animate().cancel()
        p.animate().translationY(-dp(80f)).alpha(0f).setDuration(260)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { if (hiding) detach() }
            .start()
    }

    fun destroy() {
        detach()
    }

    private fun attach() {
        val ctx = service
        face = FaceView(ctx)
        countText = TextView(ctx).apply {
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
            elevation = dp(8f)
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
        hiding = false
    }

    private fun statusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else dp(24f).toInt()
    }
}
