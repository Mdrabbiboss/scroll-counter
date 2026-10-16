package com.rabbi.scrollcounter.face

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View

/**
 * Plain Android View around [FaceRenderer] (used by the on-screen reel counter).
 * The eyes are always alive — they glance around, rest, drift and blink like a person's —
 * not only when a new reel is counted.
 */
class FaceView(context: Context) : View(context) {
    var mood: Mood = Mood.FRESH
        set(value) {
            field = value
            invalidate()
        }

    /** Extra blink driven from outside (kept for compatibility). */
    var blink: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val eyes = EyeLife()
    private var running = false

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            eyes.update(SystemClock.uptimeMillis())
            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        postOnAnimation(frame)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        val visible = visibility == VISIBLE && isAttachedToWindow
        if (visible && !running) { running = true; postOnAnimation(frame) }
        else if (!visible) { running = false; removeCallbacks(frame) }
    }

    override fun onDraw(canvas: Canvas) {
        val r = minOf(width, height) / 2f
        FaceRenderer.draw(
            canvas, width / 2f, height / 2f, r * 0.94f, mood,
            maxOf(blink, eyes.blink), eyes.gazeX, eyes.gazeY,
            (SystemClock.uptimeMillis() % 1_000_000L) / 1000f
        )
    }

    /** New reel: a springy pop, and the eyes glance over at the counter. */
    fun react() {
        eyes.glanceAtCounter(SystemClock.uptimeMillis())
        animate().cancel()
        scaleX = 1.22f; scaleY = 1.22f
        animate().scaleX(1f).scaleY(1f).setDuration(420)
            .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
        if (mood == Mood.EXHAUSTED) {
            android.animation.ObjectAnimator.ofFloat(this, "rotation", 0f, -10f, 10f, -7f, 7f, 0f).setDuration(700).start()
        }
    }
}
