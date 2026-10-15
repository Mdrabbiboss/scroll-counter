package com.rabbi.scrollcounter.face

import android.content.Context
import android.graphics.Canvas
import android.view.View

/** Plain Android View wrapper around [FaceRenderer] (used by the floating bubble). */
class FaceView(context: Context) : View(context) {
    var mood: Mood = Mood.FRESH
        set(value) {
            field = value
            invalidate()
        }

    var blink: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val r = minOf(width, height) / 2f
        FaceRenderer.draw(canvas, width / 2f, height / 2f, r * 0.94f, mood, blink)
    }

    /** A springy pop followed by a quick blink — makes the buddy feel alive. */
    fun react() {
        animate().cancel()
        scaleX = 1.25f; scaleY = 1.25f
        animate().scaleX(1f).scaleY(1f).setDuration(420)
            .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
        if (mood != Mood.EXHAUSTED) {
            android.animation.ValueAnimator.ofFloat(0f, 1f, 0f).apply {
                startDelay = 650; duration = 220
                addUpdateListener { blink = it.animatedValue as Float }
            }.start()
        } else {
            android.animation.ObjectAnimator.ofFloat(this, "rotation", 0f, -10f, 10f, -7f, 7f, 0f).setDuration(700).start()
        }
    }
}
