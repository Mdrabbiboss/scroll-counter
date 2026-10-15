package com.rabbi.scrollcounter.tracker

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rabbi.scrollcounter.face.FaceView
import com.rabbi.scrollcounter.face.Mood

/**
 * The small check-in card that slides in at the top of the screen every few scrolls.
 * Drawn as an accessibility overlay, so it needs no "draw over other apps" permission,
 * and it is not touchable, so it never blocks what the user is doing.
 */
class ScrollBubble(private val service: AccessibilityService) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var card: LinearLayout? = null
    private lateinit var face: FaceView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var barFill: View
    private lateinit var barTrack: FrameLayout
    private val hideRunnable = Runnable { hide() }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, service.resources.displayMetrics)

    fun show(appName: String, appCount: Int, total: Int, limit: Int) {
        val mood = Mood.of(total, limit)
        if (root == null) attach()
        face.mood = mood
        face.post { face.react() }
        title.text = "$appCount scrolls on $appName"
        subtitle.text = "Today $total / $limit  ·  ${mood.label}"
        (card?.background as? GradientDrawable)?.setStroke(dp(1.5f).toInt(), mood.accent)

        val fraction = if (limit > 0) (total.toFloat() / limit).coerceIn(0f, 1f) else 0f
        barTrack.post {
            barFill.layoutParams = barFill.layoutParams.apply { width = (barTrack.width * fraction).toInt() }
            (barFill.background as GradientDrawable).setColor(mood.accent)
            barFill.requestLayout()
        }

        val c = card ?: return
        handler.removeCallbacks(hideRunnable)
        c.animate().cancel()
        if (c.alpha < 1f) {
            c.translationY = -dp(120f)
            c.alpha = 0f
            c.scaleX = 0.92f; c.scaleY = 0.92f
            c.animate().translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(420).setInterpolator(OvershootInterpolator(1.1f)).start()
        } else {
            // Already visible: give it a little "bump" so the update is noticed.
            c.animate().scaleX(1.05f).scaleY(1.05f).setDuration(110).withEndAction {
                c.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }.start()
        }
        handler.postDelayed(hideRunnable, SHOW_MS)
    }

    private fun hide() {
        val c = card ?: return
        c.animate().translationY(-dp(120f)).alpha(0f).setDuration(300)
            .setInterpolator(DecelerateInterpolator()).withEndAction { detach() }.start()
    }

    private fun attach() {
        val ctx = service
        face = FaceView(ctx)
        title = TextView(ctx).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
        }
        subtitle = TextView(ctx).apply { setTextColor(0xCCFFFFFF.toInt()); textSize = 12.5f }
        barTrack = FrameLayout(ctx).apply {
            background = GradientDrawable().apply { cornerRadius = dp(3f); setColor(0x33FFFFFF) }
        }
        barFill = View(ctx).apply {
            background = GradientDrawable().apply { cornerRadius = dp(3f); setColor(0xFF5EEAD4.toInt()) }
        }
        barTrack.addView(barFill, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))

        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(subtitle, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(1f).toInt() })
            addView(barTrack, LinearLayout.LayoutParams(-1, dp(5f).toInt()).apply { topMargin = dp(7f).toInt() })
        }
        val cardView = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val p = dp(12f).toInt()
            setPadding(p, p, dp(18f).toInt(), p)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xF2052A26.toInt(), 0xF20D4F47.toInt())
            ).apply { cornerRadius = dp(22f) }
            elevation = dp(10f)
            addView(face, LinearLayout.LayoutParams(dp(46f).toInt(), dp(46f).toInt()))
            addView(texts, LinearLayout.LayoutParams(dp(220f).toInt(), -2).apply { marginStart = dp(12f).toInt() })
            alpha = 0f
        }
        val frame = FrameLayout(ctx).apply {
            val m = dp(8f).toInt()
            setPadding(m, m, m, m * 2)
            addView(cardView, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))
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
            card = cardView
        } catch (e: Exception) {
            root = null
            card = null
        }
    }

    private fun detach() {
        val r = root ?: return
        try { wm?.removeView(r) } catch (_: Exception) { }
        root = null
        card = null
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        detach()
    }

    private fun statusBarHeight(): Int {
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else dp(24f).toInt()
    }

    companion object {
        private const val SHOW_MS = 2800L
    }
}
