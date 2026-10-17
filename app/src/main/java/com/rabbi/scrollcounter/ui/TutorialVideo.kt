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

/**
 * One part of the spoken guide: the video holds on [atMs] while the line is said, then plays the
 * action up to [untilMs]. A [whilePlaying] line is said while its part plays (the ending).
 */
private class Cue(val atMs: Int, val untilMs: Int, val en: String, val whilePlaying: Boolean = false) {
    /** The line in the app's language (falls back to English). */
    val text: String get() = tr(en)
}

/**
 * The guide for res/raw/accessibility_tutorial.mp4. The times are where the captions burnt into
 * the video change (0, 0.95, 4.6, 5.85, 7.9 s): each step is explained first, then shown.
 */
private val ACCESSIBILITY_GUIDE = listOf(
    Cue(0, 0, "To count your reels, Scroll Counter needs one permission: Accessibility. Let me show you how to turn it on. It only takes a few seconds."),
    Cue(0, 950, "First, tap the button below to open your phone's Accessibility settings. Then scroll down to Downloaded services."),
    Cue(950, 4600, "Find Scroll Counter in the list, and tap it."),
    Cue(4600, 5850, "Now turn on the switch next to Scroll Counter."),
    Cue(5850, 7900, "Your phone will show a warning and ask for permission. Tap Allow."),
    Cue(7900, Int.MAX_VALUE, "That's it! Scroll Counter is now on. Come back to this app, and it will start counting your reels.", whilePlaying = true),
)

/** How long the screen is shown before the guide starts talking. */
private const val GUIDE_DELAY_MS = 2000L

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
    val guide = remember { NarratedVideo(context.applicationContext, if (narrate) ACCESSIBILITY_GUIDE else emptyList()) }
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
 * Plays the video together with the spoken guide: for each step the video holds on the step's
 * first frame while the step is explained, then plays the action, then holds on the next step.
 * So the voice always says what's about to happen on screen — never ahead or behind it.
 */
private class NarratedVideo(private val context: Context, private val cues: List<Cue>) {
    private val main = Handler(Looper.getMainLooper())
    private var mp: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = cues.isEmpty()
    private var wantVoice = false
    private var narrating = false
    private var delayDone = false
    private var cue = 0
    private var playing = false        // the current cue's action is playing
    private var spokenCue = -1         // the last cue whose line has been fully said
    private var completed = false
    private var released = false

    init {
        if (cues.isNotEmpty()) runCatching {
            tts = TextToSpeech(context) { status -> main.post { onTtsReady(status) } }
        }.onFailure { ttsFailed = true }
    }

    fun attach(player: MediaPlayer, voice: Boolean) {
        mp = player
        completed = false
        player.setOnCompletionListener { completed = true; if (!narrating) { it.seekTo(0); it.start() } }
        wantVoice = voice
        if (!voice || ttsFailed) { loopQuietly(); return }
        // Show the first frame and wait: the guide starts a moment after the screen appears.
        showFirstFrame(player)
        main.postDelayed({
            delayDone = true
            if (released || mp == null) return@postDelayed
            if (ttsReady) playGuide()
            // The phone's voice still not ready after a while: just show the video.
            else main.postDelayed({ if (!ttsReady && !narrating && mp != null) { ttsFailed = true; loopQuietly() } }, 2500)
        }, GUIDE_DELAY_MS)
    }

    private fun showFirstFrame(p: MediaPlayer) {
        runCatching {
            p.isLooping = false
            p.seekTo(0)
            p.start()
            main.postDelayed({ runCatching { if (narrating.not() && p.isPlaying && wantVoice) { p.pause(); p.seekTo(0) } } }, 60)
        }
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

    /** Start the spoken guide from the beginning. */
    fun playGuide() {
        wantVoice = true
        val p = mp ?: return
        if (ttsFailed) { loopQuietly(); return }
        if (!ttsReady || !delayDone) return // starts as soon as both are ready
        main.removeCallbacks(ticker)
        runCatching { tts?.stop() }
        narrating = true
        completed = false
        spokenCue = -1
        runCatching { p.isLooping = false; if (p.isPlaying) p.pause(); p.seekTo(0) }
        startCue(0)
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

    fun resumeQuietly() { if (mp != null && !narrating && delayDone) loopQuietly() }

    private fun loopQuietly() {
        val p = mp ?: return
        runCatching { p.isLooping = true; if (!p.isPlaying) { if (completed) p.seekTo(0); p.start() } }
    }

    private fun startCue(i: Int) {
        val p = mp ?: return
        cue = i
        val c = cues[i]
        if (c.whilePlaying) {
            playing = true
            speak(i)
            runCatching { if (!p.isPlaying) p.start() }
        } else {
            playing = false
            runCatching { if (p.isPlaying) p.pause() }
            speak(i)
        }
        main.removeCallbacks(ticker)
        main.post(ticker)
    }

    private fun onTtsReady(status: Int) {
        if (released) return
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) { ttsFailed = true; if (mp != null && delayDone) loopQuietly(); return }
        val lang = I18n.lang
        val wanted = Locale.forLanguageTag(when (lang) {
            Lang.BN -> "bn-BD"; Lang.ZH -> "zh-CN"; Lang.PT -> "pt-BR"; Lang.ES -> "es-ES"; Lang.EN -> "en-US"
            else -> lang.code
        })
        var ok = runCatching { engine.setLanguage(wanted) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
        if (!ok && lang == Lang.BN) ok = runCatching { engine.setLanguage(Locale.forLanguageTag("bn-IN")) }.getOrDefault(-2) >= TextToSpeech.LANG_AVAILABLE
        if (!ok) { ttsFailed = true; if (mp != null && delayDone) loopQuietly(); return } // no voice for this language
        // The clearest installed voice for the language (offline first).
        runCatching {
            engine.voices?.filter {
                it.locale.language == engine.voice?.locale?.language &&
                    !it.isNetworkConnectionRequired && "notInstalled" !in (it.features ?: emptySet())
            }?.maxByOrNull { it.quality }?.let { engine.voice = it }
        }
        engine.setSpeechRate(0.92f)
        engine.setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) { main.post { onSpoken(id) } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { main.post { onSpoken(id) } }
        })
        ttsReady = true
        if (wantVoice && mp != null && delayDone && !narrating) playGuide()
    }

    private fun speak(i: Int) {
        tts?.speak(cues[i].text, TextToSpeech.QUEUE_FLUSH, null, i.toString())
    }

    /** A line has been said: show its action (or go straight on if it has none). */
    private fun onSpoken(id: String?) {
        val i = id?.toIntOrNull() ?: return
        if (!narrating || i != cue) return
        spokenCue = i
        val c = cues[i]
        if (c.whilePlaying) return // the ticker finishes once the video has played to the end
        if (c.untilMs <= c.atMs) { next(); return }
        playing = true
        runCatching { mp?.start() }
    }

    private fun next() {
        if (cue + 1 < cues.size) startCue(cue + 1) else finish()
    }

    private fun finish() {
        narrating = false
        main.removeCallbacks(ticker)
        main.postDelayed({ loopQuietly() }, 700)
    }

    /** Stops each action exactly where the next step begins. */
    private val ticker = object : Runnable {
        override fun run() {
            val p = mp ?: return
            if (!narrating) return
            val c = cues[cue]
            if (playing) {
                val pos = runCatching { p.currentPosition }.getOrDefault(0)
                if (c.whilePlaying) {
                    if (completed && spokenCue == cue) { finish(); return }
                } else if (completed || pos >= c.untilMs) {
                    runCatching { if (p.isPlaying) p.pause() }
                    playing = false
                    next()
                    return
                }
            }
            main.postDelayed(this, 25)
        }
    }
}
