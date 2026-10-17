package com.rabbi.scrollcounter.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.Locale

/** One spoken step of the Accessibility tutorial and where its part of the video ends. */
private class Step(val endMs: Int, val en: String) {
    /** The step in the app's language (falls back to English). */
    val text: String get() = tr(en)
}

/**
 * The 5 steps shown in res/raw/accessibility_tutorial.mp4, with the moment each step's part of
 * the video ends (the captions burnt into the video change at these points).
 */
private val ACCESSIBILITY_STEPS = listOf(
    Step(950, "Tap the button below to open Accessibility settings. Then scroll down to Downloaded services."),
    Step(4400, "Tap Scroll Counter."),
    Step(5900, "Now turn the switch on."),
    Step(8500, "Your phone will ask for permission. Tap Allow."),
    Step(Int.MAX_VALUE, "Done! Scroll Counter is on. Come back to this app, and it will start counting your reels."),
)

/**
 * A short how-to video (from res/raw) inside a sleek phone frame, with a spoken guide in the
 * app's language: each step is read out by the phone's own voice (Text-to-Speech), and the video
 * waits on each step until it has been said, so picture and voice always match. After the guide
 * the video keeps looping quietly. The speaker button turns the voice off, or plays it again.
 * Uses a TextureView so the rounded corners clip the video properly.
 */
@Composable
fun TutorialVideo(rawRes: Int, aspect: Float, modifier: Modifier = Modifier, narrate: Boolean = true) {
    val context = LocalContext.current
    var voiceOn by remember { mutableStateOf(narrate) }
    val guide = remember { NarratedVideo(context.applicationContext, if (narrate) ACCESSIBILITY_STEPS else emptyList()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                // Left the screen (e.g. went to the phone's Settings): stop talking.
                Lifecycle.Event.ON_PAUSE -> guide.pause()
                Lifecycle.Event.ON_RESUME -> guide.resumeQuietly()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs); guide.release() }
    }
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .aspectRatio(aspect)
            .shadow(18.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2A3A3C), Color(0xFF0B1213))))
            .border(1.5.dp, Color.White.copy(alpha = 0.22f), shape)
            .padding(5.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(21.dp)),
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            runCatching {
                                val mp = MediaPlayer.create(context, rawRes) ?: return
                                mp.setSurface(Surface(st))
                                mp.setVolume(0f, 0f)
                                guide.attach(mp, voiceOn)
                            }
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            guide.detach()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
            }
        )
        if (narrate) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(34.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable {
                        voiceOn = !voiceOn
                        if (voiceOn) guide.playGuide() else guide.mute()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (voiceOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                    contentDescription = if (voiceOn) tr("Voice guide on") else tr("Voice guide off"),
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

/**
 * Plays the video in step with the spoken guide. Every step's speech starts with its part of the
 * video; if the speech is longer, the video holds on the step's last frame until it's finished.
 */
private class NarratedVideo(private val context: Context, private val steps: List<Step>) {
    private val main = Handler(Looper.getMainLooper())
    private var mp: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = steps.isEmpty()
    private var wantVoice = false
    private var narrating = false
    private var step = 0
    private var spoken = BooleanArray(steps.size)
    private var completed = false
    private var released = false

    init {
        if (steps.isNotEmpty()) runCatching {
            tts = TextToSpeech(context) { status -> main.post { onTtsReady(status) } }
        }.onFailure { ttsFailed = true }
    }

    fun attach(player: MediaPlayer, voice: Boolean) {
        mp = player
        player.setOnCompletionListener { completed = true; if (!narrating) { it.seekTo(0); it.start() } }
        wantVoice = voice
        if (voice && !ttsFailed) playGuide() else loopQuietly()
        // The phone's voice can take a moment to start; if it never does, just show the video.
        main.postDelayed({ if (wantVoice && !ttsReady && !narrating) { ttsFailed = true; loopQuietly() } }, 2500)
    }

    fun detach() {
        main.removeCallbacksAndMessages(null)
        runCatching { tts?.stop() }
        narrating = false
        runCatching { mp?.release() }
        mp = null
    }

    fun release() {
        released = true
        detach()
        runCatching { tts?.shutdown() }
        tts = null
    }

    /** Start the spoken guide from the first step. */
    fun playGuide() {
        wantVoice = true
        val p = mp ?: return
        if (ttsFailed) { loopQuietly(); return }
        if (!ttsReady) { runCatching { p.seekTo(0) }; return } // starts as soon as the voice is ready
        main.removeCallbacks(ticker)
        runCatching { tts?.stop() }
        narrating = true
        completed = false
        step = 0
        spoken = BooleanArray(steps.size)
        runCatching { p.isLooping = false; p.seekTo(0); speak(0); p.start() }
        main.post(ticker)
    }

    fun mute() {
        wantVoice = false
        runCatching { tts?.stop() }
        if (narrating) { narrating = false; main.removeCallbacks(ticker) }
        loopQuietly()
    }

    fun pause() {
        main.removeCallbacks(ticker)
        runCatching { tts?.stop() }
        narrating = false
        runCatching { if (mp?.isPlaying == true) mp?.pause() }
    }

    fun resumeQuietly() { if (mp != null && !narrating) loopQuietly() }

    private fun loopQuietly() {
        val p = mp ?: return
        runCatching { p.isLooping = true; if (!p.isPlaying) { if (completed) p.seekTo(0); p.start() } }
    }

    private fun onTtsReady(status: Int) {
        if (released) return
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) { ttsFailed = true; if (mp != null) loopQuietly(); return }
        val lang = I18n.lang
        val wanted = Locale.forLanguageTag(when (lang) {
            Lang.BN -> "bn-BD"; Lang.ZH -> "zh-CN"; Lang.PT -> "pt-BR"; Lang.ES -> "es-ES"; Lang.EN -> "en-US"
            else -> lang.code
        })
        var ok = runCatching { engine.setLanguage(wanted) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
        if (!ok && lang == Lang.BN) ok = runCatching { engine.setLanguage(Locale.forLanguageTag("bn-IN")) }.getOrDefault(-2) >= TextToSpeech.LANG_AVAILABLE
        if (!ok) { ttsFailed = true; if (mp != null) loopQuietly(); return } // no voice for this language on the phone
        // The clearest installed voice for the language (offline first).
        runCatching {
            engine.voices?.filter {
                it.locale.language == engine.voice?.locale?.language &&
                    !it.isNetworkConnectionRequired && "notInstalled" !in (it.features ?: emptySet())
            }?.maxByOrNull { it.quality }?.let { engine.voice = it }
        }
        engine.setSpeechRate(0.95f)
        engine.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { main.post { markSpoken(id) } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { main.post { markSpoken(id) } }
        })
        ttsReady = true
        if (wantVoice && mp != null && !narrating) playGuide()
    }

    private fun markSpoken(id: String?) {
        val i = id?.toIntOrNull() ?: return
        if (i in spoken.indices) spoken[i] = true
    }

    private fun speak(i: Int) {
        tts?.speak(steps[i].text, TextToSpeech.QUEUE_FLUSH, null, i.toString())
    }

    /** Keeps picture and voice together: hold on a step until it's been said, then go on. */
    private val ticker = object : Runnable {
        override fun run() {
            val p = mp ?: return
            if (!narrating) return
            val pos = runCatching { p.currentPosition }.getOrDefault(0)
            val endOfStep = completed || pos >= steps[step].endMs
            if (endOfStep) {
                if (!spoken[step]) {
                    runCatching { if (p.isPlaying) p.pause() }
                } else if (step + 1 < steps.size) {
                    step++
                    speak(step)
                    runCatching { if (!p.isPlaying && !completed) p.start() }
                } else {
                    // Whole guide said: keep the video looping quietly.
                    narrating = false
                    main.postDelayed({ loopQuietly() }, 600)
                    return
                }
            }
            main.postDelayed(this, 40)
        }
    }
}
