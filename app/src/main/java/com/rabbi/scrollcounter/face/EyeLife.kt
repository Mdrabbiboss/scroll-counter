package com.rabbi.scrollcounter.face

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Gives the buddy human-like eyes, independent of what's on screen:
 *
 *  • Saccades — quick, decelerating jumps to a new spot every ~0.6–2.8 s (mostly near the
 *    centre, sometimes to the side, sometimes over at the counter), exactly like real eyes,
 *    instead of only moving when a reel changes.
 *  • Fixational drift — a tiny, slow wander while the eyes rest on a spot.
 *  • Natural blinks every ~2–6 s (close fast, open slower), sometimes a double blink, and big
 *    eye jumps are often accompanied by a blink.
 *
 * Call [update] every frame with the current time; read [gazeX], [gazeY] (-1..1) and [blink] (0..1).
 */
class EyeLife(private val rnd: Random = Random) {
    var gazeX = 0f; private set
    var gazeY = 0f; private set
    var blink = 0f; private set

    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var moveStart = 0L
    private var moveDur = 1L
    private var nextSaccade = 0L

    private var blinkStart = -1L
    private var blinkTwice = false
    private var nextBlink = 0L
    private var started = false

    fun update(now: Long) {
        if (!started) {
            started = true
            nextSaccade = now + 500 + rnd.nextLong(900)
            nextBlink = now + 1200 + rnd.nextLong(2400)
            moveStart = now
        }

        // ---- saccades ----
        if (now >= nextSaccade) {
            val (x, y) = pickTarget()
            saccadeTo(x, y, now)
            nextSaccade = now + moveDur + 600 + rnd.nextLong(2200)
        }
        val t = ((now - moveStart).toFloat() / moveDur).coerceIn(0f, 1f)
        val e = 1f - (1f - t) * (1f - t) * (1f - t) // ease-out cubic: fast start, soft landing
        val drift = now / 1000f
        gazeX = fromX + (toX - fromX) * e + 0.035f * sin(drift * 1.7f) + 0.02f * sin(drift * 4.3f)
        gazeY = fromY + (toY - fromY) * e + 0.03f * sin(drift * 1.3f + 1f)

        // ---- blinks ----
        if (blinkStart < 0 && now >= nextBlink) startBlink(now)
        if (blinkStart >= 0) {
            val bt = now - blinkStart
            blink = when {
                bt < CLOSE_MS -> bt / CLOSE_MS.toFloat()
                bt < CLOSE_MS + HOLD_MS -> 1f
                bt < CLOSE_MS + HOLD_MS + OPEN_MS -> 1f - (bt - CLOSE_MS - HOLD_MS) / OPEN_MS.toFloat()
                else -> {
                    if (blinkTwice) { blinkTwice = false; blinkStart = now + 60; 0f }
                    else { blinkStart = -1; nextBlink = now + 2000 + rnd.nextLong(4000); 0f }
                }
            }.coerceIn(0f, 1f)
        }
    }

    /** Glance at the counter (to the right) — used when a new reel is counted. */
    fun glanceAtCounter(now: Long) {
        saccadeTo(0.85f, 0.1f, now)
        nextSaccade = now + moveDur + 700 + rnd.nextLong(500)
    }

    private fun saccadeTo(x: Float, y: Float, now: Long) {
        fromX = gazeX; fromY = gazeY
        toX = x; toY = y
        moveStart = now
        val dist = hypot(toX - fromX, toY - fromY)
        moveDur = (70 + dist * 110).toLong()
        // People often blink during a big eye jump.
        if (dist > 0.8f && blinkStart < 0 && rnd.nextFloat() < 0.35f) startBlink(now)
    }

    private fun startBlink(now: Long) {
        blinkStart = now
        blinkTwice = rnd.nextFloat() < 0.18f
    }

    private fun pickTarget(): Pair<Float, Float> {
        val r = rnd.nextFloat()
        return when {
            r < 0.42f -> small() to small()                                            // back to centre
            r < 0.64f -> (if (rnd.nextBoolean()) 1 else -1) * (0.55f + rnd.nextFloat() * 0.4f) to small() // to a side
            r < 0.78f -> small() to (if (rnd.nextBoolean()) -0.6f else 0.5f) + small() // up / down
            r < 0.92f -> 0.8f + small() * 0.5f to 0.1f + small() * 0.5f                // at the counter
            else -> (rnd.nextFloat() * 2 - 1) * 0.8f to (rnd.nextFloat() * 2 - 1) * 0.5f
        }.let { (x, y) -> x.coerceIn(-1f, 1f) to y.coerceIn(-1f, 1f) }
    }

    private fun small() = (rnd.nextFloat() * 2 - 1) * 0.18f

    val isMoving: Boolean get() = abs(toX - gazeX) > 0.01f || blink > 0f

    companion object {
        private const val CLOSE_MS = 75L
        private const val HOLD_MS = 35L
        private const val OPEN_MS = 125L
    }
}
