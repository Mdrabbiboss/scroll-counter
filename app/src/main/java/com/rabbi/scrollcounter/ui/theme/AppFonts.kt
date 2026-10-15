package com.rabbi.scrollcounter.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import com.rabbi.scrollcounter.R

/**
 * The app's typefaces:
 *  • Outfit — a clean geometric face for big numbers and headlines (counts look crisp and premium)
 *  • Inter — a highly readable UI face for everything else
 */
object AppFonts {
    val Display = FontFamily(
        Font(R.font.outfit_regular, FontWeight.Normal),
        Font(R.font.outfit_bold, FontWeight.Bold),
    )
    val Text = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
        Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    )

    /** For views drawn outside Compose (on-screen counter, widget). Falls back to the system font. */
    @Volatile var numbers: Typeface = Typeface.create("sans-serif", Typeface.BOLD); private set
    @Volatile var textBold: Typeface = Typeface.create("sans-serif", Typeface.BOLD); private set
    @Volatile var textMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); private set
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        runCatching { ResourcesCompat.getFont(context, R.font.outfit_bold) }.getOrNull()?.let { numbers = it }
        runCatching { ResourcesCompat.getFont(context, R.font.inter_extrabold) }.getOrNull()?.let { textBold = it }
        runCatching { ResourcesCompat.getFont(context, R.font.inter_medium) }.getOrNull()?.let { textMedium = it }
    }
}
