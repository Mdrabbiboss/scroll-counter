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
 * One part of the spoken guide. Normally the video shows the step's first frame ([atMs]) while
 * the line is said, then plays the action up to [untilMs]. A [whilePlaying] line is said while its
 * part plays (scrolling, the ending); the video then waits at [untilMs] until the line is done.
 */
private class Cue(val atMs: Int, val untilMs: Int, val en: String, val whilePlaying: Boolean = false) {
    /** The line in the app's language (falls back to English). */
    val text: String get() = tr(en)
}

/**
 * The guide for res/raw/accessibility_tutorial.mp4. The times are where the captions burnt into
 * the video change (0, 0.95, 4.6, 5.85, 7.9 s): each tap is explained first, then shown; the
 * scrolling at the start and the ending play while they're described.
 */
private val ACCESSIBILITY_GUIDE = listOf(
    Cue(0, 900, "First, tap the button below to open your phone's Accessibility settings. Then scroll down to Downloaded services.", whilePlaying = true),
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
                            // Prepared in the background, so opening this screen never stutters.
                            runCatching {
                                val mp = MediaPlayer()
                                context.resources.openRawResourceFd(rawRes).use { afd ->
                                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                }
                                mp.setSurface(Surface(st))
                                mp.setOnPreparedListener { ready ->
                                    ready.setVolume(0f, 0f)
                                    guide.attach(ready, voiceOn)
                                }
                                mp.prepareAsync()
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
    /** The phone's voice engine is slow to set up (loading a language can take seconds): never on the UI thread. */
    private val bg = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var mp: MediaPlayer? = null
    @Volatile private var tts: TextToSpeech? = null
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
        // Creating the engine is quick; setting it up (language, voice) happens off the UI thread.
        if (cues.isNotEmpty()) runCatching {
            tts = TextToSpeech(context) { status -> runCatching { bg.execute { setUpVoice(status) } } }
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
            if (released || mp == null || !wantVoice) return@postDelayed
            if (ttsReady) playGuide()
            // The phone's voice still getting ready: show the video meanwhile; the guide starts
            // from the beginning as soon as the voice is ready.
            else main.postDelayed({ if (!ttsReady && !narrating && mp != null) loopQuietly() }, 1500)
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
        stopVoice()
        narrating = false
        runCatching { mp?.release() }
        mp = null
    }

    fun release() {
        released = true
        detach()
        val engine = tts
        tts = null
        bg.execute { runCatching { engine?.shutdown() } }
        bg.shutdown()
    }

    /** Start the spoken guide from the beginning. */
    fun playGuide() {
        wantVoice = true
        val p = mp ?: return
        if (ttsFailed) { loopQuietly(); return }
        if (!ttsReady || !delayDone) return // starts as soon as both are ready
        main.removeCallbacks(ticker)
        stopVoice()
        narrating = true
        completed = false
        spokenCue = -1
        runCatching { p.isLooping = false; if (p.isPlaying) p.pause(); p.seekTo(0) }
        startCue(0)
    }

    fun mute() {
        wantVoice = false
        stopVoice()
        if (narrating) { narrating = false; main.removeCallbacks(ticker) }
        loopQuietly()
    }

    fun pause() {
        main.removeCallbacks(ticker)
        stopVoice()
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
            runCatching {
                if (p.isPlaying) p.pause()
                // Show the step's own first frame (its caption) while it's explained.
                if (p.currentPosition < c.atMs) p.seekTo(c.atMs.toLong(), MediaPlayer.SEEK_CLOSEST)
            }
            speak(i)
        }
        main.removeCallbacks(ticker)
        main.post(ticker)
    }

    /** Runs on [bg]: picks the language and the clearest voice, then tells the UI thread. */
    private fun setUpVoice(status: Int) {
        val ok = runCatching { configure(status) }.getOrDefault(false)
        main.post {
            if (released) return@post
            if (!ok) { ttsFailed = true; if (mp != null && delayDone) loopQuietly(); return@post }
            ttsReady = true
            if (wantVoice && mp != null && delayDone && !narrating) playGuide()
        }
    }

    private fun configure(status: Int): Boolean {
        if (released) return false
        val engine = tts ?: return false
        if (status != TextToSpeech.SUCCESS) return false
        val lang = I18n.lang
        val wanted = Locale.forLanguageTag(when (lang) {
            Lang.BN -> "bn-BD"; Lang.ZH -> "zh-CN"; Lang.PT -> "pt-BR"; Lang.ES -> "es-ES"; Lang.EN -> "en-US"
            else -> lang.code
        })
        var ok = runCatching { engine.setLanguage(wanted) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
        if (!ok && lang == Lang.BN) ok = runCatching { engine.setLanguage(Locale.forLanguageTag("bn-IN")) }.getOrDefault(-2) >= TextToSpeech.LANG_AVAILABLE
        if (!ok) return false // no voice for this language on the phone
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
        return true
    }

    private fun stopVoice() {
        val engine = tts ?: return
        runCatching { bg.execute { runCatching { engine.stop() } } }
    }

    private fun speak(i: Int) {
        val engine = tts ?: return
        val line = cues[i].text
        runCatching { bg.execute { runCatching { engine.speak(line, TextToSpeech.QUEUE_FLUSH, null, i.toString()) } } }
    }

    /** A line has been said: show its action (or go straight on if it has none). */
    private fun onSpoken(id: String?) {
        val i = id?.toIntOrNull() ?: return
        if (!narrating || i != cue) return
        spokenCue = i
        val c = cues[i]
        if (c.whilePlaying) return // the ticker moves on once the video has reached the step's end
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
                    val reached = completed || pos >= c.untilMs
                    // Wait here (on this step's last frame) until the line has been said.
                    if (reached && !completed) runCatching { if (p.isPlaying) p.pause() }
                    if (reached && spokenCue == cue) {
                        playing = false
                        if (cue + 1 < cues.size) next() else finish()
                        return
                    }
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
