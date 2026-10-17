package com.rabbi.scrollcounter.squat

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Counts real squats from body landmarks (image coordinates, y grows downwards).
 *
 * A squat is counted only when the hips really go down and come back up:
 *  • Standing baseline: the hip height and the hip→ankle length while you stand straight.
 *  • DOWN when the hips have dropped at least [DOWN_DROP] of that leg length (≈ thighs close to
 *    parallel) — or, seen from the side, the knee bends below [DOWN_ANGLE]° with a clear drop.
 *  • UP again when the hips are back near the standing height → +1.
 * Works facing the camera (where knee angles look straight) and from the side. Tiny knee bends,
 * bowing forward or wobbling never count, and reps faster than [MIN_REP_MS] are ignored.
 */
class SquatCounter(val target: Int = 10) {

    data class Pt(val x: Float, val y: Float, val ok: Boolean)

    enum class Cue { NO_BODY, SHOW_LEGS, STAND_TALL, GO_DOWN, LOWER, STAND_UP, REP, DONE }

    var reps = 0; private set
    /** 0 = standing, 1 = deep enough — for the depth meter. */
    var depth = 0f; private set
    var isDown = false; private set
    var ready = false; private set

    private var standHipY = Float.NaN
    private var legLen = Float.NaN
    private var readySince = 0L
    private var downAt = 0L
    private var lastRepAt = 0L
    private var ema = -1f

    fun reset() {
        reps = 0; depth = 0f; isDown = false; ready = false
        standHipY = Float.NaN; legLen = Float.NaN; readySince = 0L; downAt = 0L; lastRepAt = 0L; ema = -1f
    }

    fun update(
        now: Long,
        lSh: Pt, rSh: Pt, lHip: Pt, rHip: Pt, lKnee: Pt, rKnee: Pt, lAnkle: Pt, rAnkle: Pt,
    ): Cue {
        if (reps >= target) return Cue.DONE
        val hips = listOf(lHip, rHip).filter { it.ok }
        val shoulders = listOf(lSh, rSh).filter { it.ok }
        if (hips.isEmpty() || shoulders.isEmpty()) { depth = 0f; return Cue.NO_BODY }
        val legs = listOf(Triple(lHip, lKnee, lAnkle), Triple(rHip, rKnee, rAnkle)).filter { (h, k, a) -> h.ok && k.ok && a.ok }
        if (legs.isEmpty()) { depth = 0f; return Cue.SHOW_LEGS }

        val angle = legs.map { (h, k, a) -> kneeAngle(h, k, a) }.average().toFloat()
        ema = if (ema < 0f) angle else ema * 0.45f + angle * 0.55f
        val hipY = hips.map { it.y }.average().toFloat()
        val ankleY = legs.map { it.third.y }.average().toFloat()
        val curLeg = (ankleY - hipY).coerceAtLeast(1f)

        // 1. Stand straight for a moment so we learn your standing height.
        if (!ready) {
            if (ema >= STAND_ANGLE) {
                if (readySince == 0L) readySince = now
                standHipY = if (standHipY.isNaN()) hipY else standHipY * 0.7f + hipY * 0.3f
                legLen = if (legLen.isNaN()) curLeg else legLen * 0.7f + curLeg * 0.3f
                if (now - readySince >= READY_MS) { ready = true; return Cue.GO_DOWN }
            } else readySince = 0L
            depth = 0f
            return Cue.STAND_TALL
        }

        // While standing, keep the baseline fresh (you may step a little closer or further).
        if (!isDown && ema >= STAND_ANGLE && abs(hipY - standHipY) < legLen * 0.08f) {
            standHipY = standHipY * 0.9f + hipY * 0.1f
            legLen = legLen * 0.9f + curLeg * 0.1f
        }
        val drop = (hipY - standHipY) / legLen
        depth = (drop / DOWN_DROP).coerceIn(0f, 1f)

        if (!isDown) {
            val deep = drop >= DOWN_DROP || (ema <= DOWN_ANGLE && drop >= DOWN_DROP * 0.6f)
            if (deep) { isDown = true; downAt = now; return Cue.STAND_UP }
            return if (drop >= DOWN_DROP * 0.35f || ema < 150f) Cue.LOWER else Cue.GO_DOWN
        }
        // Down: back up near the standing height → one squat.
        val up = drop <= UP_DROP && ema >= UP_ANGLE
        if (up && now - downAt >= 200 && now - lastRepAt >= MIN_REP_MS) {
            isDown = false
            lastRepAt = now
            reps++
            return if (reps >= target) Cue.DONE else Cue.REP
        }
        return Cue.STAND_UP
    }

    private fun kneeAngle(h: Pt, k: Pt, a: Pt): Float {
        val ux = h.x - k.x; val uy = h.y - k.y
        val vx = a.x - k.x; val vy = a.y - k.y
        val d = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
        if (d <= 0f) return 180f
        val c = ((ux * vx + uy * vy) / d).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(c).toDouble()).toFloat()
    }

    companion object {
        const val STAND_ANGLE = 155f
        const val UP_ANGLE = 145f
        const val DOWN_ANGLE = 105f
        /** Hip drop (fraction of standing hip→ankle length) that counts as a real squat. */
        const val DOWN_DROP = 0.28f
        const val UP_DROP = 0.10f
        const val READY_MS = 700L
        const val MIN_REP_MS = 700L
    }
}
