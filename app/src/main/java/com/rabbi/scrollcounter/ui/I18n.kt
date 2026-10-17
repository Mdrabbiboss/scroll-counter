package com.rabbi.scrollcounter.ui

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** The app's languages (native name first, so everyone finds their own). */
enum class Lang(val code: String, val native: String, val english: String, val rtl: Boolean = false) {
    EN("en", "English", "English"),
    BN("bn", "বাংলা", "Bangla"),
    HI("hi", "हिन्दी", "Hindi"),
    UR("ur", "اردو", "Urdu", rtl = true),
    AR("ar", "العربية", "Arabic", rtl = true),
    ES("es", "Español", "Spanish"),
    PT("pt", "Português", "Portuguese"),
    FR("fr", "Français", "French"),
    DE("de", "Deutsch", "German"),
    IT("it", "Italiano", "Italian"),
    RU("ru", "Русский", "Russian"),
    TR("tr", "Türkçe", "Turkish"),
    ID("id", "Bahasa Indonesia", "Indonesian"),
    VI("vi", "Tiếng Việt", "Vietnamese"),
    TH("th", "ไทย", "Thai"),
    JA("ja", "日本語", "Japanese"),
    KO("ko", "한국어", "Korean"),
    ZH("zh", "中文（简体）", "Chinese (Simplified)");

    /** Latin digits everywhere (so every number in the app looks the same), local month/day names. */
    val locale: Locale get() = Locale.forLanguageTag("$code-u-nu-latn")

    companion object {
        fun of(code: String?): Lang? = entries.firstOrNull { it.code == code }
        /** The phone's own language when the app speaks it, else English. */
        fun fromDevice(): Lang {
            val dev = runCatching { android.content.res.Resources.getSystem().configuration.locales[0].language }.getOrDefault("en")
            return entries.firstOrNull { it.code == dev || (it == ID && dev == "in") } ?: EN
        }
    }
}

/**
 * Tiny translation layer: every text in the app is written in English and looked up in
 * assets/i18n/<code>.json (English → translation). Missing entries simply stay English.
 */
object I18n {
    @Volatile var lang: Lang = Lang.EN
        private set
    @Volatile private var table: Map<String, String> = emptyMap()
    private val systemLocale: Locale = Locale.getDefault()

    fun apply(context: Context, l: Lang) {
        lang = l
        table = if (l == Lang.EN) emptyMap() else runCatching {
            val json = context.assets.open("i18n/${l.code}.json").use { it.readBytes().toString(Charsets.UTF_8) }
            val o = JSONObject(json)
            buildMap { o.keys().forEach { k -> put(k, o.getString(k)) } }
        }.getOrDefault(emptyMap())
        Locale.setDefault(if (l == Lang.EN && systemLocale.language == "en") systemLocale else l.locale)
    }

    fun lookup(s: String): String = table[s] ?: s
}

/** Translate a text. */
fun tr(s: String): String = I18n.lookup(s)

/** Translate a text with %1$s-style placeholders, then fill them in. */
fun tr(s: String, vararg args: Any?): String =
    runCatching { String.format(Locale.US, I18n.lookup(s), *args) }.getOrElse { String.format(Locale.US, s, *args) }
