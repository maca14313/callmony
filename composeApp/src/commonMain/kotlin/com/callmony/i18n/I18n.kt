package com.callmony.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.callmony.lib.AppStore
import com.callmony.lib.setDateNames

/* callmony i18n — English · አማርኛ · Afaan Oromo · العربية (RTL) · ትግርኛ
   EN is the source catalog — every other language reaches parity.
   (Port of the React Native edition's src/i18n/index.ts.) */

data class Language(val code: String, val label: String, val native: String, val rtl: Boolean)

val LANGUAGES = listOf(
    Language("en", "English", "English", false),
    Language("am", "Amharic", "አማርኛ", false),
    Language("om", "Afaan Oromo", "Afaan Oromoo", false),
    Language("ar", "Arabic", "العربية", true),
    Language("ti", "Tigrinya", "ትግርኛ", false),
)

private val DICTS: Map<String, Map<String, String>> = mapOf(
    "en" to EN, "am" to AM, "om" to OM, "ar" to AR, "ti" to TI,
)

fun isRTL(lang: String?): Boolean = LANGUAGES.find { it.code == lang }?.rtl ?: false

fun normalizeLang(v: String?): String {
    val l = v ?: "en"
    return if (DICTS.containsKey(l)) l else "en"
}

/** Replace {placeholders} in a translated string. */
private fun fill(s: String, vars: Map<String, String>?): String {
    if (vars == null) return s
    var out = s
    for ((k, v) in vars) out = out.replace("{$k}", v)
    return out
}

/** Imperative translate — reads the current language from the store. */
fun t(key: String, vars: Map<String, String>? = null): String {
    val lang = normalizeLang(AppStore.state.settings.language)
    ensureDateNames(lang)
    return fill(DICTS[lang]?.get(key) ?: EN[key] ?: key, vars)
}

/** Current language + direction. */
fun currentLang(): Language {
    val lang = normalizeLang(AppStore.state.settings.language)
    return LANGUAGES.first { it.code == lang }
}

private var dateNamesLang: String? = null

private fun ensureDateNames(lang: String) {
    if (dateNamesLang != lang) {
        dateNamesLang = lang
        applyDateNames(lang)
    }
}

/** Localized date names for the date formatter. */
fun applyDateNames(lang: String) {
    val d = DICTS[lang] ?: EN
    val months = (d["date.months"] ?: EN["date.months"] ?: "").split(",").map { it.trim() }
    val weekdays = (d["date.weekdays"] ?: EN["date.weekdays"] ?: "").split(",").map { it.trim() }
    setDateNames(months, weekdays, d["date.today"] ?: "Today", d["date.yesterday"] ?: "Yesterday")
}
