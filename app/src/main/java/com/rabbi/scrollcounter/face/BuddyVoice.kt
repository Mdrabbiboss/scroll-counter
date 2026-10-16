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
    private const val RATE = 22050
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = HashMap<String, ShortArray>()
    @Volatile private var playingUntil = 0L

    /** One sound segment: [ms] long, pitch gliding [f0]→[f1] Hz, optional vibrato and breath. */
    private class Seg(
        val ms: Int, val f0: Float, val f1: Float, val amp: Float = 1f,
        val vibHz: Float = 0f, val vibDepth: Float = 0f, val breath: Float = 0f, val silent: Boolean = false
    )

    private fun gap(ms: Int) = Seg(ms, 0f, 0f, 0f, silent = true)

    private fun variants(mood: Mood): List<List<Seg>> = when (mood) {
        Mood.FRESH -> listOf(
            // "pip-pip-piiip!"
            listOf(Seg(70, 1150f, 1650f), gap(45), Seg(80, 1250f, 1800f), gap(40), Seg(150, 1700f, 1350f, vibHz = 14f, vibDepth = 0.03f)),
            // happy trill "brrrip!"
            listOf(Seg(260, 950f, 1550f, vibHz = 24f, vibDepth = 0.07f), gap(30), Seg(90, 1500f, 1900f, 0.8f)),
            // cute "hee-hee"
            listOf(Seg(110, 1300f, 1500f, 0.9f), gap(70), Seg(130, 1350f, 1600f, 0.9f)),
        )
        Mood.OKAY -> listOf(
            // content "mm-hmm"
            listOf(Seg(170, 520f, 610f, 0.9f), gap(60), Seg(260, 620f, 480f, 0.85f, vibHz = 6f, vibDepth = 0.015f)),
            // soft "boop?"
            listOf(Seg(150, 700f, 930f, 0.85f)),
            // little hum
            listOf(Seg(420, 560f, 640f, 0.75f, vibHz = 5f, vibDepth = 0.03f)),
        )
        Mood.TIRED -> listOf(
            // yawn "aaa-uhh"
            listOf(Seg(240, 520f, 700f, 0.7f, breath = 0.25f), Seg(620, 700f, 300f, 0.85f, breath = 0.3f)),
            // sigh
            listOf(Seg(520, 430f, 300f, 0.55f, breath = 0.6f)),
        )
        Mood.DRAINED -> listOf(
            // whimper "hnn… hnnn"
            listOf(Seg(200, 820f, 680f, 0.8f, vibHz = 8f, vibDepth = 0.035f), gap(90), Seg(360, 720f, 480f, 0.8f, vibHz = 7f, vibDepth = 0.04f)),
            // tired "uh-oh"
            listOf(Seg(150, 520f, 450f, 0.85f), gap(70), Seg(280, 450f, 330f, 0.85f, breath = 0.15f)),
        )
        Mood.EXHAUSTED -> listOf(
            // dizzy groan "uuuuhh"
            listOf(Seg(950, 330f, 210f, 0.9f, vibHz = 5f, vibDepth = 0.07f, breath = 0.2f)),
            // woozy "whoa-oa-oa"
            listOf(Seg(700, 600f, 330f, 0.85f, vibHz = 9f, vibDepth = 0.12f)),
        )
    }

    private fun synth(segs: List<Seg>): ShortArray {
        val total = segs.sumOf { it.ms } * RATE / 1000 + RATE / 20
        val out = FloatArray(total)
        var pos = 0
        var phase = 0.0
        var noiseLp = 0f
        val rnd = Random(7)
        for (s in segs) {
            val n = s.ms * RATE / 1000
            if (s.silent) { pos += n; continue }
            val att = min(n / 4, RATE * 12 / 1000)
            val rel = min(n / 2, RATE * 45 / 1000)
            for (i in 0 until n) {
                if (pos + i >= total) break
                val t = i.toFloat() / n
                // smooth glide + vibrato
                val glide = s.f0 + (s.f1 - s.f0) * (t * t * (3 - 2 * t))
                val vib = 1f + s.vibDepth * sin(2 * PI * s.vibHz * i / RATE).toFloat()
                phase += 2 * PI * glide * vib / RATE
                // warm, rounded tone: fundamental + soft harmonics
                var v = (sin(phase) + 0.28 * sin(2 * phase) + 0.10 * sin(3 * phase)).toFloat() / 1.38f
                if (s.breath > 0f) {
                    noiseLp += 0.18f * ((rnd.nextFloat() * 2 - 1) - noiseLp)
                    v = v * (1 - s.breath) + noiseLp * 2.2f * s.breath
                }
                val env = when {
                    i < att -> i.toFloat() / att
                    i > n - rel -> (n - i).toFloat() / rel
                    else -> 1f
                }
                out[pos + i] += v * env * s.amp
            }
            pos += n
        }
        val gain = 0.42f
        return ShortArray(total) { (out[it] * gain).coerceIn(-1f, 1f).times(Short.MAX_VALUE).toInt().toShort() }
    }

    /** True when sound is allowed: phone ringer is on normal and media isn't muted. */
    private fun allowed(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
    }

    /** Play one random sound for [mood]. Ignored while another sound is still playing. */
    fun speak(context: Context, mood: Mood) {
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
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
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
                val ms = pcm.size * 1000L / RATE
                playingUntil = android.os.SystemClock.uptimeMillis() + ms + 150
                main.postDelayed({ runCatching { track.stop(); track.release() } }, ms + 300)
            }
        }
    }

    /** Pause between idle sounds — calmer moods speak less often. */
    fun idleDelayMs(mood: Mood): Long = when (mood) {
        Mood.FRESH -> 22_000L + Random.nextLong(16_000L)
        Mood.OKAY -> 28_000L + Random.nextLong(18_000L)
        Mood.TIRED -> 26_000L + Random.nextLong(16_000L)
        Mood.DRAINED -> 22_000L + Random.nextLong(14_000L)
        Mood.EXHAUSTED -> 18_000L + Random.nextLong(12_000L)
    }
}
