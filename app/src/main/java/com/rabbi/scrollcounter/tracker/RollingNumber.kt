package com.rabbi.scrollcounter.tracker

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.animation.PathInterpolator

/**
 * The reel count with an odometer roll: when the number goes up, only the digits that change
 * move — the old digit slides up and fades away, then the new one rises from below into place.
 * Digits that stay the same don't move. Every digit gets the same width, so nothing jiggles.
 */
class RollingNumber(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var current = ""
    private var previous = ""
    private var progress = 1f
    private var anim: ValueAnimator? = null

    var textColor: Int = 0xFFF8C33A.toInt()
        set(v) { field = v; invalidate() }

    fun setTextSizeSp(sp: Float) {
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        requestLayout(); invalidate()
    }

    fun setTypeface(tf: Typeface?) { paint.typeface = tf; requestLayout(); invalidate() }

    fun setShadow(radius: Float, dy: Float, color: Int) { paint.setShadowLayer(radius, 0f, dy, color); invalidate() }

    /** Show [value]; [roll] = animate the change (a new reel was counted). */
    fun setNumber(value: Int, roll: Boolean) {
        val text = value.toString()
        if (text == current) return
        anim?.cancel()
        val lengthChanged = text.length != current.length
        previous = current
        current = text
        if (!roll || previous.isEmpty()) {
            progress = 1f
            if (lengthChanged) requestLayout()
            invalidate()
            return
        }
        if (lengthChanged) requestLayout()
        progress = 0f
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ROLL_MS
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun digitWidth(): Float = (0..9).maxOf { paint.measureText(it.toString()) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fm = paint.fontMetrics
        val chars = maxOf(current.length, if (progress < 1f) previous.length else 0, 1)
        val w = (chars * digitWidth()).toInt() + paddingLeft + paddingRight
        val h = (fm.descent - fm.ascent).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(maxOf(w, suggestedMinimumWidth), widthMeasureSpec),
            resolveSize(maxOf(h, suggestedMinimumHeight), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val fm = paint.fontMetrics
        val dw = digitWidth()
        val contentH = fm.descent - fm.ascent
        val top = paddingTop + (height - paddingTop - paddingBottom - contentH) / 2f
        val baseline = top - fm.ascent
        val travel = contentH * 0.62f
        val n = maxOf(current.length, previous.length)
        // Right-aligned columns, centred as a group.
        val groupW = current.length.coerceAtLeast(1) * dw
        val right = (width + groupW) / 2f
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        for (i in 0 until n) {
            val x = right - dw * (i + 0.5f)
            val newCh = current.getOrNull(current.length - 1 - i)
            val oldCh = previous.getOrNull(previous.length - 1 - i)
            if (progress >= 1f || newCh == oldCh) {
                if (newCh != null) draw(canvas, newCh, x, baseline, 1f)
                continue
            }
            // Old digit leaves first (up and out), the new one follows from below.
            val out = (progress / 0.55f).coerceIn(0f, 1f)
            val inn = ((progress - 0.25f) / 0.75f).coerceIn(0f, 1f)
            if (oldCh != null && out < 1f) draw(canvas, oldCh, x, baseline - travel * out, 1f - out)
            if (newCh != null) draw(canvas, newCh, x, baseline + travel * (1f - inn), inn)
        }
        canvas.restore()
    }

    private fun draw(canvas: Canvas, ch: Char, x: Float, y: Float, alpha: Float) {
        paint.color = textColor
        paint.alpha = (alpha.coerceIn(0f, 1f) * android.graphics.Color.alpha(textColor)).toInt()
        canvas.drawText(ch.toString(), x, y, paint)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel(); progress = 1f
        super.onDetachedFromWindow()
    }

    companion object {
        private const val ROLL_MS = 420L
    }
}
