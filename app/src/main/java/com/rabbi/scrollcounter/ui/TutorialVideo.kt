package com.rabbi.scrollcounter.ui

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A short, silent, looping how-to video (from res/raw) inside a sleek phone frame.
 * Uses a TextureView so the rounded corners clip the video properly.
 */
@Composable
fun TutorialVideo(rawRes: Int, aspect: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(Unit) {
        onDispose { runCatching { player[0]?.release() }; player[0] = null }
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
                                mp.isLooping = true
                                mp.setVolume(0f, 0f)
                                mp.start()
                                player[0] = mp
                            }
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            runCatching { player[0]?.release() }; player[0] = null
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
            }
        )
    }
}
