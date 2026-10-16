package com.rabbi.scrollcounter.face

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The buddy's little pet-like voice, synthesised in code (no sound files): happy chirps when
 * fresh, a content coo when okay, a yawn when tired, a whimper when drained and a dizzy groan
 * when exhausted. Stays quiet when the phone is on silent / vibrate.
 */
object BuddyVoice {
    private const val RATE = 44100
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, ShortArray>()
    private val envCache = HashMap<String, FloatArray>()
    @Volatile private var playingUntil = 0L

    /** One sound segment: [ms] long, pitch gliding [f0]→[f1] Hz, with an optional gentle vibrato. */
    private class Seg(
        val ms: Int, val f0: Float, val f1: Float, val amp: Float = 1f,
        val vibHz: Float = 0f, val vibDepth: Float = 0f, val silent: Boolean = false
    )

    private fun gap(ms: Int) = Seg(ms, 0f, 0f, 0f, silent = true)

    // Clean, cute "ocarina / bubble" tones only — no noise, no buzzy harmonics.
    private fun variants(mood: Mood): List<List<Seg>> = when (mood) {
        Mood.FRESH -> listOf(
            // "pip-pip-piu!"
            listOf(Seg(75, 1050f, 1450f), gap(55), Seg(85, 1150f, 1600f), gap(55), Seg(170, 1500f, 1250f, vibHz = 7f, vibDepth = 0.015f)),
            // bubbly "bloop-bloop!"
            listOf(Seg(110, 700f, 1150f), gap(60), Seg(130, 820f, 1350f)),
            // giggle "hi-hi-hi"
            listOf(Seg(80, 1250f, 1400f, 0.9f), gap(60), Seg(80, 1300f, 1450f, 0.9f), gap(60), Seg(110, 1350f, 1550f, 0.9f)),
        )
        Mood.OKAY -> listOf(
            // content "mm-hmm~"
            listOf(Seg(170, 560f, 640f, 0.9f), gap(70), Seg(280, 650f, 540f, 0.9f, vibHz = 5f, vibDepth = 0.012f)),
            // curious "boop?"
            listOf(Seg(170, 650f, 900f, 0.9f)),
            // little happy hum
            listOf(Seg(380, 600f, 680f, 0.8f, vibHz = 5f, vibDepth = 0.015f)),
        )
        Mood.TIRED -> listOf(
            // soft yawn "aah~ uhh"
            listOf(Seg(260, 560f, 760f, 0.75f), Seg(650, 760f, 380f, 0.85f, vibHz = 4f, vibDepth = 0.012f)),
            // sleepy "hmmm…"
            listOf(Seg(560, 520f, 400f, 0.75f, vibHz = 4.5f, vibDepth = 0.015f)),
            // drowsy "mm… mm"
            listOf(Seg(200, 560f, 500f, 0.75f), gap(110), Seg(320, 520f, 420f, 0.7f)),
        )
        Mood.DRAINED -> listOf(
            // little whimper "nnn… nnn"
            listOf(Seg(210, 860f, 720f, 0.8f, vibHz = 6f, vibDepth = 0.02f), gap(100), Seg(360, 760f, 540f, 0.8f, vibHz = 6f, vibDepth = 0.02f)),
            // worried "uh-oh"
            listOf(Seg(160, 620f, 560f, 0.85f), gap(80), Seg(300, 540f, 420f, 0.85f)),
        )
        Mood.EXHAUSTED -> listOf(
            // dizzy "ooo-ooo~"
            listOf(Seg(850, 520f, 330f, 0.85f, vibHz = 5f, vibDepth = 0.03f)),
            // woozy "wooo-ah"
            listOf(Seg(420, 640f, 460f, 0.85f, vibHz = 6f, vibDepth = 0.025f), gap(60), Seg(300, 480f, 380f, 0.8f)),
        )
    }

    private fun synth(segs: List<Seg>): ShortArray {
        val tail = RATE / 10
        val total = segs.sumOf { it.ms } * RATE / 1000 + tail
        val out = FloatArray(total)
        var pos = 0
        for (s in segs) {
            val n = s.ms * RATE / 1000
            if (s.silent) { pos += n; continue }
            var phase = 0.0
            val att = min(n / 3, RATE * 18 / 1000)
            val rel = min(n / 2, RATE * 80 / 1000)
            for (i in 0 until n) {
                if (pos + i >= total) break
                val t = i.toFloat() / n
                val glide = s.f0 + (s.f1 - s.f0) * (t * t * (3 - 2 * t))          // smooth glide
                val vib = 1f + s.vibDepth * sin(2 * PI * s.vibHz * i / RATE).toFloat()
                phase += 2 * PI * glide * vib / RATE
                // Pure, round tone: sine + a whisper of the octave for a soft "ocarina" voice.
                val v = (sin(phase) + 0.07 * sin(2 * phase)).toFloat() / 1.07f
                // Raised-cosine fades: no clicks at the start or end of a note.
                val env = when {
                    i < att -> (0.5f - 0.5f * kotlin.math.cos(PI * i / att).toFloat())
                    i > n - rel -> (0.5f - 0.5f * kotlin.math.cos(PI * (n - i) / rel).toFloat())
                    else -> 1f
                }
                out[pos + i] += v * env * s.amp
            }
            pos += n
        }
        // Gentle low-pass to keep it smooth and warm.
        var lp = 0f
        for (i in out.indices) { lp += 0.35f * (out[i] - lp); out[i] = lp }
        val gain = 0.55f
        return ShortArray(total) { (out[it] * gain).coerceIn(-1f, 1f).times(Short.MAX_VALUE).toInt().toShort() }
    }

    /** How the face acts while a sound plays. */
    enum class Gesture { HOP, SWAY, YAWN, SHIVER, WOBBLE }

    /**
     * One "performance": the sound's loudness every 10 ms ([talk], 0..1, drives the mouth) and
     * the body gesture that goes with it, starting at [startMs] (uptime).
     */
    class Performance(val talk: FloatArray, val gesture: Gesture, val startMs: Long) {
        val durationMs get() = talk.size * 10L
        /** Mouth opening 0..1 at [now], or -1 once the sound is over. */
        fun talkAt(now: Long): Float {
            val i = ((now - startMs) / 10L).toInt()
            return if (i < 0) 0f else if (i >= talk.size) -1f else talk[i]
        }
        /** 0..1 progress through the sound. */
        fun progress(now: Long) = ((now - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
    }

    fun gestureFor(mood: Mood) = when (mood) {
        Mood.FRESH -> Gesture.HOP
        Mood.OKAY -> Gesture.SWAY
        Mood.TIRED -> Gesture.YAWN
        Mood.DRAINED -> Gesture.SHIVER
        Mood.EXHAUSTED -> Gesture.WOBBLE
    }

    /** Loudness envelope every 10 ms, normalised and smoothed, so the mouth follows the sound. */
    private fun envelope(pcm: ShortArray): FloatArray {
        val win = RATE / 100
        val n = pcm.size / win
        val raw = FloatArray(n) { w ->
            var sum = 0.0
            for (i in w * win until (w + 1) * win) { val v = pcm[i] / 32768.0; sum += v * v }
            kotlin.math.sqrt(sum / win).toFloat()
        }
        val max = raw.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val out = FloatArray(n)
        var a = 0f
        for (i in 0 until n) {
            val target = (raw[i] / max).coerceIn(0f, 1f)
            a += (if (target > a) 0.55f else 0.25f) * (target - a)   // quick open, softer close
            out[i] = a
        }
        return out
    }

    /** Sound plays on the media volume (also in vibrate mode); silent only when media is muted. */
    private fun allowed(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
    }

    /**
     * Play one random sound for [mood]. Ignored while another sound is still playing.
     * [onStart] (main thread) gets the [Performance] so the face can act it out in sync.
     */
    fun speak(context: Context, mood: Mood, onStart: (Performance) -> Unit = {}) {
        val app = context.applicationContext
        val now = android.os.SystemClock.uptimeMillis()
        if (now < playingUntil || !allowed(app)) return
        val list = variants(mood)
        val idx = Random.nextInt(list.size)
        playingUntil = now + 1500
        io.execute {
            runCatching {
                val key = "${mood.name}$idx"
                val pcm = synchronized(cache) { cache.getOrPut(key) { synth(list[idx]) } }
                val env = synchronized(envCache) { envCache.getOrPut(key) { envelope(pcm) } }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                val perf = Performance(env, gestureFor(mood), android.os.SystemClock.uptimeMillis() + 40)
                main.post { onStart(perf) }
                val ms = pcm.size * 1000L / RATE
                playingUntil = android.os.SystemClock.uptimeMillis() + ms + 150
                main.postDelayed({ runCatching { track.stop(); track.release() } }, ms + 300)
            }
        }
    }

    /** Pause between idle sounds — calmer moods speak less often. */
    fun idleDelayMs(mood: Mood): Long = when (mood) {
        Mood.FRESH -> 12_000L + Random.nextLong(10_000L)
        Mood.OKAY -> 15_000L + Random.nextLong(10_000L)
        Mood.TIRED -> 14_000L + Random.nextLong(10_000L)
        Mood.DRAINED -> 12_000L + Random.nextLong(9_000L)
        Mood.EXHAUSTED -> 10_000L + Random.nextLong(8_000L)
    }
}
