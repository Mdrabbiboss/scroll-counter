package com.rabbi.scrollcounter.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import com.rabbi.scrollcounter.R

/**
 * The app's typefaces, each used where it reads best:
 *
 *  • Anak Manja  — chunky 3D capitals. Only for big numbers (today's count, the on-screen
 *                  counter, the widget, the block screen). Pops like a scoreboard.
 *  • Matcha Style — playful hand-drawn capitals. Headlines and moods ("GOOD EVENING",
 *                  "FEELING FRESH!", page and dialog titles).
 *  • Blank Place — friendly rounded sans with lowercase. Buttons, labels, card titles, short text.
 *  • Inter       — plain and very readable. Only for small print and long explanations
 *                  (privacy, permission notes), where a decorative face would tire the eyes.
 *
 * Each decorative font has one weight, so faux-bold is switched off in the type styles.
 */
object AppFonts {
    val Numbers = FontFamily(Font(R.font.anak_manja, FontWeight.Normal))
    val Headline = FontFamily(Font(R.font.matcha_style, FontWeight.Normal))
    val Ui = FontFamily(Font(R.font.blank_place, FontWeight.Normal))
    val Reading = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )

    /** Back-compat alias used by a few screens for big titles. */
    val Display get() = Headline

    /** Typefaces for views drawn outside Compose (on-screen counter, widget). */
    @Volatile var numbers: Typeface = Typeface.create("sans-serif", Typeface.BOLD); private set
    @Volatile var headline: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL); private set
    @Volatile var ui: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); private set
    // Older names kept so existing drawing code keeps working.
    val textBold: Typeface get() = ui
    val textMedium: Typeface get() = ui
    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        runCatching { ResourcesCompat.getFont(context, R.font.anak_manja) }.getOrNull()?.let { numbers = it }
        runCatching { ResourcesCompat.getFont(context, R.font.matcha_style) }.getOrNull()?.let { headline = it }
        runCatching { ResourcesCompat.getFont(context, R.font.blank_place) }.getOrNull()?.let { ui = it }
    }
}
