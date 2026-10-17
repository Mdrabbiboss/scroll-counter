package com.rabbi.scrollcounter.guard

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rabbi.scrollcounter.MainActivity
import com.rabbi.scrollcounter.data.ScrollStore
import com.rabbi.scrollcounter.face.Mood
import com.rabbi.scrollcounter.ui.AnimatedFace
import com.rabbi.scrollcounter.ui.I18n
import com.rabbi.scrollcounter.ui.theme.Brand
import com.rabbi.scrollcounter.ui.theme.ScrollCounterTheme
import com.rabbi.scrollcounter.ui.tr
import java.io.File

/**
 * Android opens this screen instead of wiping everything when the user taps "Clear data" /
 * "Clear storage" / "Manage space" for Scroll Counter in the phone's Settings
 * (android:manageSpaceActivity). The reel history and today's limit stay, so clearing data
 * can't be used to reset the daily limit. Only the cache can be cleared here; past days are
 * deleted from inside the app (Settings → Delete all history).
 */
class ManageSpaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ScrollStore.get(this) // loads the language
        setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (I18n.lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                ScrollCounterTheme(dark = true) {
                    Column(
                        Modifier.fillMaxSize().background(Brand.hero).safeDrawingPadding()
                            .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AnimatedFace(Mood.FRESH, 150.dp)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            tr("Your history is protected"),
                            style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            tr("Scroll Counter's reel counts can't be wiped from here, so your daily limit stays fair.\n\nYou can free up space by clearing the cache. To delete past days, open the app and go to Settings → Delete all history."),
                            style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = { clearCache() },
                            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)
                        ) { Text(tr("Clear cache"), style = MaterialTheme.typography.titleMedium) }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                startActivity(Intent(this@ManageSpaceActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp)
                        ) { Text(tr("Open Scroll Counter"), color = Color.White) }
                        TextButton(onClick = { finish() }, modifier = Modifier.padding(top = 4.dp)) {
                            Text(tr("Close"), color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }

    private fun clearCache() {
        val freed = listOfNotNull(cacheDir, codeCacheDir, externalCacheDir).sumOf { size(it) }
        listOfNotNull(cacheDir, codeCacheDir, externalCacheDir).forEach { dir -> dir.listFiles()?.forEach { it.deleteRecursively() } }
        val mb = freed / (1024.0 * 1024.0)
        Toast.makeText(this, tr("Cache cleared (%1\$s MB)", String.format(java.util.Locale.US, "%.1f", mb)), Toast.LENGTH_SHORT).show()
    }

    private fun size(f: File): Long = if (f.isFile) f.length() else f.listFiles()?.sumOf { size(it) } ?: 0L
}
