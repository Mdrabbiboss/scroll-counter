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
 * The buddy's little pet-like voice, synthesised in code (no sound files): six cute sounds for
 * each mood (chirps, giggles and "wheee" when fresh; hums and "boop?" when okay; yawns, sighs
 * and snores when tired; whimpers and sniffles when drained; dizzy groans when exhausted), each
 * acted out with its own move. Stays quiet when the phone is on silent / vibrate.
 */
object BuddyVoice {
    private const val RATE = 44100
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, ShortArray>()
    private val envCache = HashMap<String, FloatArray>()
    @Volatile private var playingUntil = 0L
    private val lastIdx = HashMap<Mood, Int>()

    /** One sound segment: [ms] long, pitch gliding [f0]→[f1] Hz, with an optional gentle vibrato. */
    private class Seg(
        val ms: Int, val f0: Float, val f1: Float, val amp: Float = 1f,
        val vibHz: Float = 0f, val vibDepth: Float = 0f, val silent: Boolean = false
    )

    private fun gap(ms: Int) = Seg(ms, 0f, 0f, 0f, silent = true)

    /** One cute sound and the move the buddy makes while saying it. */
    private class Variant(val gesture: Gesture, val segs: List<Seg>)
    private fun v(g: Gesture, vararg segs: Seg) = Variant(g, segs.toList())

    // Clean, cute "ocarina / bubble" tones only — no noise, no buzzy harmonics. Six per mood,
    // each with its own move, so the buddy never feels repetitive.
    private fun variants(mood: Mood): List<Variant> = when (mood) {
        Mood.FRESH -> listOf(
            // "pip-pip-piu!" — happy hop
            v(Gesture.HOP, Seg(75, 1050f, 1450f), gap(55), Seg(85, 1150f, 1600f), gap(55), Seg(170, 1500f, 1250f, vibHz = 7f, vibDepth = 0.015f)),
            // "bloop-bloop!" — bounces on each bloop
            v(Gesture.BOUNCE, Seg(110, 700f, 1150f), gap(70), Seg(130, 820f, 1350f)),
            // "hi-hi-hi" — giggles, eyes squeezed
            v(Gesture.GIGGLE, Seg(80, 1250f, 1400f, 0.9f), gap(60), Seg(80, 1300f, 1450f, 0.9f), gap(60), Seg(110, 1350f, 1550f, 0.9f)),
            // "wheee~!" — a full twirl
            v(Gesture.SPIN, Seg(620, 760f, 1700f, 0.9f, vibHz = 6f, vibDepth = 0.012f)),
            // little bird trill — shimmies
            v(Gesture.SHIMMY, Seg(420, 1300f, 1480f, 0.8f, vibHz = 22f, vibDepth = 0.045f)),
            // "mwah!" — squishes, then pops
            v(Gesture.SQUISH, Seg(60, 900f, 1250f, 0.9f), gap(40), Seg(150, 1400f, 1100f)),
        )
        Mood.OKAY -> listOf(
            // content "mm-hmm~" — nods
            v(Gesture.NOD, Seg(170, 560f, 640f, 0.9f), gap(70), Seg(280, 650f, 540f, 0.9f, vibHz = 5f, vibDepth = 0.012f)),
            // curious "boop?" — tilts its head
            v(Gesture.TILT, Seg(190, 650f, 920f, 0.9f)),
            // happy hum — sways
            v(Gesture.SWAY, Seg(420, 600f, 680f, 0.8f, vibHz = 5f, vibDepth = 0.015f)),
            // "la-la!" — two little bounces
            v(Gesture.BOUNCE, Seg(150, 700f, 720f, 0.85f), gap(80), Seg(210, 880f, 860f, 0.85f)),
            // cosy "aww~" — squish
            v(Gesture.SQUISH, Seg(360, 720f, 560f, 0.8f, vibHz = 5f, vibDepth = 0.015f)),
            // whistle "fwee-oo" — shimmy
            v(Gesture.SHIMMY, Seg(180, 1000f, 1300f, 0.75f), Seg(260, 1300f, 900f, 0.75f, vibHz = 6f, vibDepth = 0.01f)),
        )
        Mood.TIRED -> listOf(
            // soft yawn "aah~ uhh"
            v(Gesture.YAWN, Seg(260, 560f, 760f, 0.75f), Seg(650, 760f, 380f, 0.85f, vibHz = 4f, vibDepth = 0.012f)),
            // sleepy "hmmm…" — nods off, then jolts awake
            v(Gesture.DROOP, Seg(620, 520f, 380f, 0.75f, vibHz = 4.5f, vibDepth = 0.015f)),
            // drowsy "mm… mm" — slow nods
            v(Gesture.NOD, Seg(200, 560f, 500f, 0.75f), gap(110), Seg(320, 520f, 420f, 0.7f)),
            // tiny snore "zzz" — breathes in and out, eyes shut
            v(Gesture.SNORE, Seg(380, 330f, 360f, 0.6f, vibHz = 28f, vibDepth = 0.03f), gap(120), Seg(420, 360f, 310f, 0.55f, vibHz = 28f, vibDepth = 0.03f)),
            // long sigh "haaah" — deflates
            v(Gesture.SIGH, Seg(640, 640f, 420f, 0.75f)),
            // stretch "mmm-ah!" — stretches tall
            v(Gesture.STRETCH, Seg(320, 500f, 720f, 0.8f), Seg(220, 800f, 900f, 0.8f)),
        )
        Mood.DRAINED -> listOf(
            // little whimper "nnn… nnn" — shivers
            v(Gesture.SHIVER, Seg(210, 860f, 720f, 0.8f, vibHz = 6f, vibDepth = 0.02f), gap(100), Seg(360, 760f, 540f, 0.8f, vibHz = 6f, vibDepth = 0.02f)),
            // worried "uh-oh" — tilts
            v(Gesture.TILT, Seg(160, 620f, 560f, 0.85f), gap(80), Seg(300, 540f, 420f, 0.85f)),
            // sniffles — little jerks
            v(Gesture.SNIFF, Seg(60, 900f, 1000f, 0.7f), gap(70), Seg(60, 950f, 1050f, 0.7f), gap(90), Seg(260, 820f, 600f, 0.8f, vibHz = 7f, vibDepth = 0.02f)),
            // sad "awww" — deflates
            v(Gesture.SIGH, Seg(520, 700f, 450f, 0.8f, vibHz = 6f, vibDepth = 0.025f)),
            // "nooo~" — shakes its head
            v(Gesture.SHAKE, Seg(640, 560f, 400f, 0.8f, vibHz = 5f, vibDepth = 0.03f)),
            // tiny cry "eee-hee" — droops
            v(Gesture.DROOP, Seg(200, 1000f, 900f, 0.75f, vibHz = 9f, vibDepth = 0.04f), gap(80), Seg(320, 950f, 700f, 0.75f, vibHz = 9f, vibDepth = 0.04f)),
        )
        Mood.EXHAUSTED -> listOf(
            // dizzy "ooo-ooo~" — wobbles
            v(Gesture.WOBBLE, Seg(850, 520f, 330f, 0.85f, vibHz = 5f, vibDepth = 0.03f)),
            // woozy "wooo-ah" — slow dizzy spin
            v(Gesture.SPIN, Seg(420, 640f, 460f, 0.85f, vibHz = 6f, vibDepth = 0.025f), gap(60), Seg(300, 480f, 380f, 0.8f)),
            // low groan "uhhh" — deflates
            v(Gesture.SIGH, Seg(700, 380f, 300f, 0.8f, vibHz = 4f, vibDepth = 0.02f)),
            // "bwah!" — flops down and bounces
            v(Gesture.FLOP, Seg(120, 700f, 500f, 0.85f), Seg(320, 450f, 300f, 0.8f)),
            // seeing stars — circles round
            v(Gesture.ORBIT, Seg(900, 920f, 520f, 0.7f, vibHz = 8f, vibDepth = 0.05f)),
            // "nooo" — shakes its head
            v(Gesture.SHAKE, Seg(160, 600f, 560f, 0.85f), gap(60), Seg(420, 560f, 360f, 0.85f, vibHz = 5f, vibDepth = 0.03f)),
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
    enum class Gesture {
        HOP, BOUNCE, GIGGLE, SPIN, SHIMMY, SQUISH, NOD, TILT, SWAY, YAWN,
        DROOP, SNORE, SIGH, STRETCH, SHIVER, SNIFF, SHAKE, WOBBLE, FLOP, ORBIT
    }

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
        // A different one from last time.
        var idx = Random.nextInt(list.size)
        if (idx == lastIdx[mood] && list.size > 1) idx = (idx + 1 + Random.nextInt(list.size - 1)) % list.size
        lastIdx[mood] = idx
        playingUntil = now + 1500
        io.execute {
            runCatching {
                val key = "${mood.name}$idx"
                val pcm = synchronized(cache) { cache.getOrPut(key) { synth(list[idx].segs) } }
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
                val perf = Performance(env, list[idx].gesture, android.os.SystemClock.uptimeMillis() + 40)
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
