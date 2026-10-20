package com.rabbi.scrollcounter.tracker

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rabbi.scrollcounter.face.FaceView
import com.rabbi.scrollcounter.face.Mood
import com.rabbi.scrollcounter.ui.formatDuration
import com.rabbi.scrollcounter.ui.tr

/** What the panel shows: today's numbers at the moment it opens. */
data class PillInfo(
    val count: Int,
    val limit: Int,
    val appName: String?,
    val appCount: Int,
    val watchSeconds: Int,
    val apps: List<Pair<String, Int>>,
    val size: Int,
)

/**
 * The card that drops down from the floating counter when it's tapped: the buddy with how it
 * feels, today's count against the limit, reels left, watch time and this app's count; "More"
 * opens today's per-app list. The counter's size can be made smaller / bigger right here, it can
 * be hidden until you leave Reels, or the app opened. Closes on a tap outside or after a while.
 */
class PillPanel(private val service: AccessibilityService, private val host: ReelCounterPill) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var expanded = false
    /** Showing the "hide for how long" picker instead of the size row and buttons. */
    private var picking = false
    /** Index in [HIDE_STEPS], or [UNTIL_LEAVE] / [REST_OF_TODAY]. */
    private var hideChoice = 4
    private var anchor = IntArray(4)
    var closedAt = 0L
        private set
    val isOpen: Boolean get() = root != null

    private val autoClose = Runnable { close() }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, service.resources.displayMetrics)

    fun toggle(x: Int, y: Int, w: Int, h: Int) { if (isOpen) close() else { picking = false; open(x, y, w, h, animate = true) } }

    /** Open under (or above) the counter at x,y,w,h on screen. */
    fun open(x: Int, y: Int, w: Int, h: Int, animate: Boolean) {
        val info = host.info?.invoke() ?: return
        anchor = intArrayOf(x, y, w, h)
        closeNow()
        val dm = service.resources.displayMetrics
        val panelW = minOf(dm.widthPixels - dp(24f).toInt(), dp(310f).toInt())
        val card = build(info, panelW)
        val frame = FrameLayout(service).apply {
            val m = dp(8f).toInt()
            setPadding(m, m, m, m)
            clipToPadding = false
            addView(card, FrameLayout.LayoutParams(panelW, -2))
            // A tap anywhere outside closes it.
            setOnTouchListener { _, e -> if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) { close(); true } else false }
        }
        frame.measure(
            View.MeasureSpec.makeMeasureSpec(panelW + dp(16f).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val fw = frame.measuredWidth
        val fh = frame.measuredHeight
        val gap = dp(2f).toInt()
        val below = y + h + gap
        val py = if (below + fh <= dm.heightPixels) below else (y - fh - gap).coerceAtLeast(0)
        val px = (x + w / 2 - fw / 2).coerceIn(0, (dm.widthPixels - fw).coerceAtLeast(0))
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; this.x = px; this.y = py }
        try { wm?.addView(frame, params) } catch (_: Exception) { return }
        root = frame; lp = params
        if (animate) {
            card.alpha = 0f; card.scaleX = 0.9f; card.scaleY = 0.9f
            card.pivotX = (x + w / 2 - px - dp(8f)).coerceIn(0f, panelW.toFloat())
            card.pivotY = if (py >= y) 0f else fh.toFloat()
            card.translationY = if (py >= y) -dp(10f) else dp(10f)
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(260).setInterpolator(OvershootInterpolator(1.1f)).withLayer().start()
        }
        restartTimer()
    }

    private fun restartTimer() {
        handler.removeCallbacks(autoClose)
        handler.postDelayed(autoClose, if (expanded) 15_000L else 9_000L)
    }

    /** Rebuild in place (after "More" / a size change). */
    private fun reopen() { val a = anchor; open(a[0], a[1], a[2], a[3], animate = false) }

    fun close() {
        val r = root ?: return
        root = null; lp = null
        closedAt = SystemClock.uptimeMillis()
        handler.removeCallbacks(autoClose)
        val card = r.getChildAt(0)
        card?.animate()?.alpha(0f)?.scaleX(0.94f)?.scaleY(0.94f)?.setDuration(160)
            ?.setInterpolator(DecelerateInterpolator())?.withEndAction { runCatching { wm?.removeView(r) } }?.start()
            ?: runCatching { wm?.removeView(r) }
    }

    private fun closeNow() {
        val r = root ?: return
        root = null; lp = null
        handler.removeCallbacks(autoClose)
        runCatching { wm?.removeView(r) }
    }

    // ---------------- the card ----------------

    private fun build(info: PillInfo, width: Int): View {
        val ctx = service
        val ratio = if (info.limit > 0) info.count.toFloat() / info.limit else 0f
        val mood = Mood.of(info.count, info.limit)
        val accent = when { ratio >= 1f -> 0xFFFF6B6B.toInt(); ratio >= 0.8f -> 0xFFFFA24C.toInt(); else -> 0xFFF8C33A.toInt() }
        val message = when {
            info.count >= info.limit -> tr("Limit reached — time for a real-world break.")
            ratio >= 0.8f -> tr("Almost there — only %1\$s reels left.", info.limit - info.count)
            info.count == 0 -> tr("A clean slate. Keep it mindful today.")
            else -> tr("%1\$s reels left in today's limit.", info.limit - info.count)
        }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f).toInt(), dp(14f).toInt(), dp(16f).toInt(), dp(14f).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xF5142022.toInt(), 0xF5080D0E.toInt())).apply {
                cornerRadius = dp(24f)
                setStroke(dp(1f).toInt(), 0x55F8C33A)
            }
            elevation = dp(10f)
            // Any touch inside keeps it open a little longer.
            setOnTouchListener { _, _ -> restartTimer(); false }
        }

        // Buddy + how it feels + close
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val face = FaceView(ctx).apply { this.mood = mood }
            addView(face, LinearLayout.LayoutParams(dp(50f).toInt(), dp(50f).toInt()))
            face.post { face.react() }
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(tr(mood.label), 16f, 0xFFFFFFFF.toInt(), bold = true))
                addView(text(message, 12.5f, 0xCCFFFFFF.toInt()).apply { setPadding(0, dp(2f).toInt(), 0, 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12f).toInt() })
            addView(round("✕", 0x1FFFFFFF, 0xCCFFFFFF.toInt(), 30f) { close() })
        })

        // 20 / 450 and the bar
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(12f).toInt(), 0, dp(6f).toInt())
            addView(text("${info.count}", 34f, accent, numbers = true))
            addView(text(" / ${info.limit}", 15f, 0xB3FFFFFF.toInt(), numbers = true).apply { setPadding(0, 0, 0, dp(5f).toInt()) })
            addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
            addView(text("${(ratio * 100).toInt().coerceAtMost(999)}%", 14f, accent, numbers = true).apply { setPadding(0, 0, 0, dp(5f).toInt()) })
        })
        card.addView(Bar(ctx, ratio.coerceIn(0f, 1f), accent), LinearLayout.LayoutParams(-1, dp(8f).toInt()))

        // Left · Watch time · This app
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12f).toInt(), 0, 0)
            val gapPx = dp(8f).toInt()
            fun chip(value: String, label: String, first: Boolean) = addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(6f).toInt(), dp(9f).toInt(), dp(6f).toInt(), dp(9f).toInt())
                background = GradientDrawable().apply { cornerRadius = dp(14f); setColor(0x14FFFFFF) }
                addView(text(value, 16f, 0xFFFFFFFF.toInt(), bold = true, numbers = true))
                addView(text(label, 11f, 0x99FFFFFF.toInt()).apply { setPadding(0, dp(2f).toInt(), 0, 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (!first) marginStart = gapPx })
            chip("${(info.limit - info.count).coerceAtLeast(0)}", tr("Left"), true)
            chip(formatDuration(info.watchSeconds), tr("Watch time"), false)
            chip("${info.appCount}", info.appName ?: tr("This app"), false)
        })

        // More: today's per-app list
        if (expanded && info.apps.isNotEmpty()) {
            card.addView(text(tr("Today by app"), 12f, 0x99FFFFFF.toInt(), bold = true).apply { setPadding(0, dp(14f).toInt(), 0, dp(4f).toInt()) })
            val max = info.apps.maxOf { it.second }.coerceAtLeast(1)
            info.apps.forEach { (name, n) ->
                card.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4f).toInt(), 0, dp(4f).toInt())
                    addView(text(name, 13f, 0xFFFFFFFF.toInt()).apply { maxLines = 1 }, LinearLayout.LayoutParams(dp(92f).toInt(), -2))
                    addView(Bar(ctx, n.toFloat() / max, accent), LinearLayout.LayoutParams(0, dp(6f).toInt(), 1f).apply { marginStart = dp(8f).toInt(); marginEnd = dp(10f).toInt() })
                    addView(text("$n", 13f, accent, numbers = true))
                })
            }
        }
        card.addView(text(if (expanded) tr("Less") + "  ▴" else tr("More") + "  ▾", 13f, 0xFFF8C33A.toInt(), bold = true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10f).toInt(), 0, dp(10f).toInt())
            setOnClickListener { expanded = !expanded; reopen() }
        }, LinearLayout.LayoutParams(-1, -2))

        if (picking) { addHidePicker(card); return card }

        // Counter size: smaller / bigger
        val sizes = listOf(tr("Small"), tr("Normal"), tr("Large"), tr("XL"))
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f).toInt(), dp(6f).toInt(), dp(6f).toInt(), dp(6f).toInt())
            background = GradientDrawable().apply { cornerRadius = dp(16f); setColor(0x0FFFFFFF) }
            addView(text(tr("Counter size"), 13f, 0xCCFFFFFF.toInt()), LinearLayout.LayoutParams(0, -2, 1f))
            addView(round("−", 0x1FFFFFFF, 0xFFFFFFFF.toInt(), 32f) {
                if (info.size > 0) { host.onSizeChange?.invoke(info.size - 1); handler.postDelayed({ host.anchorRect()?.let { anchor = it }; reopen() }, 80) }
            })
            addView(text(sizes[info.size.coerceIn(0, 3)], 13f, 0xFFFFFFFF.toInt(), bold = true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(dp(64f).toInt(), -2))
            addView(round("+", 0x1FFFFFFF, 0xFFFFFFFF.toInt(), 32f) {
                if (info.size < 3) { host.onSizeChange?.invoke(info.size + 1); handler.postDelayed({ host.anchorRect()?.let { anchor = it }; reopen() }, 80) }
            })
        }, LinearLayout.LayoutParams(-1, -2))

        // Hide · Open app
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10f).toInt(), 0, 0)
            if (host.isHiddenNow) addView(button(tr("Show counter"), filled = false) { close(); host.unhideNow() }, LinearLayout.LayoutParams(0, dp(44f).toInt(), 1f))
            else addView(button(tr("Hide counter"), filled = false) { picking = true; reopen() }, LinearLayout.LayoutParams(0, dp(44f).toInt(), 1f))
            addView(button(tr("Open app"), filled = true) { close(); host.onOpenApp?.invoke() },
                LinearLayout.LayoutParams(0, dp(44f).toInt(), 1f).apply { marginStart = dp(10f).toInt() })
        })
        return card
    }

    /** "Hide the counter for": − 15 min + , or until you leave Reels / for the rest of today. */
    private fun addHidePicker(card: LinearLayout) {
        val ctx = service
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f).toInt(), dp(12f).toInt(), dp(14f).toInt(), dp(14f).toInt())
            background = GradientDrawable().apply { cornerRadius = dp(18f); setColor(0x12FFFFFF); setStroke(dp(1f).toInt(), 0x33F8C33A) }
            addView(text(tr("Hide the counter for"), 13f, 0xCCFFFFFF.toInt(), bold = true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, -2))
            // − value +
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(10f).toInt(), 0, dp(10f).toInt())
                addView(round("−", 0x24FFFFFF, 0xFFFFFFFF.toInt(), 40f) {
                    hideChoice = if (hideChoice < 0) HIDE_STEPS.size - 1 else (hideChoice - 1).coerceAtLeast(0); reopen()
                })
                val label = when (hideChoice) {
                    UNTIL_LEAVE -> tr("Until I leave Reels")
                    REST_OF_TODAY -> tr("Rest of today")
                    else -> formatDuration(HIDE_STEPS[hideChoice] * 60)
                }
                addView(text(label, if (hideChoice < 0) 16f else 26f, 0xFFF8C33A.toInt(), bold = true, numbers = hideChoice >= 0).apply {
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(dp(150f).toInt(), -2))
                addView(round("+", 0x24FFFFFF, 0xFFFFFFFF.toInt(), 40f) {
                    hideChoice = if (hideChoice < 0) 0 else (hideChoice + 1).coerceAtMost(HIDE_STEPS.size - 1); reopen()
                })
            }, LinearLayout.LayoutParams(-1, -2))
            // Quick picks
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                fun pick(label: String, value: Int, first: Boolean) = addView(TextView(ctx).apply {
                    text = label
                    textSize = 12.5f
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    setPadding(dp(8f).toInt(), dp(9f).toInt(), dp(8f).toInt(), dp(9f).toInt())
                    val on = hideChoice == value
                    setTextColor(if (on) 0xFF4A2A00.toInt() else 0xFFFFFFFF.toInt())
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14f)
                        setColor(if (on) 0xFFF8C33A.toInt() else 0x14FFFFFF)
                        if (!on) setStroke(dp(1f).toInt(), 0x33FFFFFF)
                    }
                    setOnClickListener { press(this); hideChoice = value; reopen() }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (!first) marginStart = dp(8f).toInt() })
                pick(tr("Until I leave Reels"), UNTIL_LEAVE, true)
                pick(tr("Rest of today"), REST_OF_TODAY, false)
            }, LinearLayout.LayoutParams(-1, -2))
            // How much to hide: drag from a faint ghost to fully gone.
            val prefs = service.getSharedPreferences(ReelCounterPill.PREFS, android.content.Context.MODE_PRIVATE)
            val start = prefs.getInt(ReelCounterPill.KEY_HIDE_LEVEL, 0).coerceIn(0, 90)
            fun levelLabel(v: Int) = if (v <= 0) tr("Fully hidden") else tr("Faint") + " · $v%"
            val value = text(levelLabel(start), 12.5f, 0xFFF8C33A.toInt(), bold = true)
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(14f).toInt(), 0, 0)
                addView(text(tr("How visible while hidden"), 12.5f, 0xCCFFFFFF.toInt()), LinearLayout.LayoutParams(0, -2, 1f))
                addView(value)
            }, LinearLayout.LayoutParams(-1, -2))
            addView(android.widget.SeekBar(ctx).apply {
                max = 90
                progress = start
                val gold = android.content.res.ColorStateList.valueOf(0xFFF8C33A.toInt())
                progressTintList = gold
                thumbTintList = gold
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x40FFFFFF)
                setPadding(dp(10f).toInt(), dp(8f).toInt(), dp(10f).toInt(), dp(4f).toInt())
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: android.widget.SeekBar, v: Int, fromUser: Boolean) {
                        val snapped = if (v < 5) 0 else v
                        value.text = levelLabel(snapped)
                        if (fromUser) { restartTimer(); host.previewHideLevel(snapped / 100f) }
                    }
                    override fun onStartTrackingTouch(sb: android.widget.SeekBar) {}
                    override fun onStopTrackingTouch(sb: android.widget.SeekBar) {
                        val snapped = if (sb.progress < 5) 0 else sb.progress
                        prefs.edit().putInt(ReelCounterPill.KEY_HIDE_LEVEL, snapped).apply()
                        sb.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        host.endPreview()
                    }
                })
            }, LinearLayout.LayoutParams(-1, -2))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4f).toInt(), 0, dp(4f).toInt(), 0)
                addView(text(tr("Fully hidden"), 10.5f, 0x80FFFFFF.toInt()), LinearLayout.LayoutParams(0, -2, 1f))
                addView(text(tr("Faint"), 10.5f, 0x80FFFFFF.toInt()))
            }, LinearLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, -2))

        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10f).toInt(), 0, 0)
            addView(button(tr("Cancel"), filled = false) { picking = false; reopen() }, LinearLayout.LayoutParams(0, dp(44f).toInt(), 1f))
            addView(button(tr("Hide"), filled = true) {
                val now = System.currentTimeMillis()
                val until: Long? = when (hideChoice) {
                    UNTIL_LEAVE -> null
                    REST_OF_TODAY -> java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    else -> now + HIDE_STEPS[hideChoice] * 60_000L
                }
                picking = false
                close()
                host.snooze(until)
            }, LinearLayout.LayoutParams(0, dp(44f).toInt(), 1f).apply { marginStart = dp(10f).toInt() })
        })
    }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false, numbers: Boolean = false) = TextView(service).apply {
        text = s
        textSize = sp
        setTextColor(color)
        includeFontPadding = false
        typeface = if (numbers) com.rabbi.scrollcounter.ui.theme.AppFonts.numbers ?: Typeface.DEFAULT_BOLD
        else if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun round(label: String, bg: Int, fg: Int, sizeDp: Float, onClick: () -> Unit) = TextView(service).apply {
        text = label
        textSize = 15f
        setTextColor(fg)
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp).toInt(), dp(sizeDp).toInt())
        setOnClickListener { press(this); onClick() }
    }

    private fun button(label: String, filled: Boolean, onClick: () -> Unit) = TextView(service).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 1
        if (filled) {
            setTextColor(0xFF4A2A00.toInt())
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFFFFE6A3.toInt(), 0xFFFFC857.toInt(), 0xFFFF9F43.toInt())).apply { cornerRadius = dp(16f) }
        } else {
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply { cornerRadius = dp(16f); setColor(0x14FFFFFF); setStroke(dp(1f).toInt(), 0x40FFFFFF) }
        }
        setOnClickListener { press(this); onClick() }
    }

    private fun press(v: View) {
        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }.start()
    }

    companion object {
        /** Hide-for steps, in minutes. */
        private val HIDE_STEPS = intArrayOf(1, 2, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180)
        private const val UNTIL_LEAVE = -1
        private const val REST_OF_TODAY = -2
    }

    /** A rounded progress bar. */
    private class Bar(context: android.content.Context, private val fraction: Float, private val color: Int) : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: android.graphics.Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val r = h / 2
            paint.color = 0x26FFFFFF
            canvas.drawRoundRect(0f, 0f, w, h, r, r, paint)
            if (fraction > 0f) {
                paint.color = color
                canvas.drawRoundRect(0f, 0f, (w * fraction).coerceAtLeast(h), h, r, r, paint)
            }
        }
    }
}
