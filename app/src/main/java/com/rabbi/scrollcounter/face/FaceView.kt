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

    override fun onDraw(canvas: Canvas) {
        val r = minOf(width, height) / 2f
        FaceRenderer.draw(canvas, width / 2f, height / 2f, r * 0.96f, mood)
    }
}
