package com.callmony.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow

/* callmony — theme tokens (light + dark), an exact mirror of the React
   Native edition's src/theme.ts palette. */

val BRAND = Color(0xFF16a085)
val BRAND_DARK = Color(0xFF2fc4a6)
val INK = Color(0xFF172b2a)
val CANVAS = Color(0xFFf7faf9)

data class ThemeColors(
    val mode: String, // "light" | "dark"
    val canvas: Color,
    val card: Color,
    val cardAlt: Color,
    val ink: Color,
    val sub: Color,
    val faint: Color,
    val line: Color,
    val brand: Color,
    val brandSoft: Color,
    val onBrand: Color,
    val green: Color,
    val red: Color,
    val amber: Color,
    val blue: Color,
    val chip: Color,
    val scrim: Color,
    val inputBg: Color,
    val headerBg: Color,
)

val LIGHT = ThemeColors(
    mode = "light",
    canvas = CANVAS,
    card = Color(0xFFffffff),
    cardAlt = Color(0xFFeef4f2),
    ink = INK,
    sub = Color(0xFF5c6f6c),
    faint = Color(0xFF8ba09c),
    line = Color(0xFFe2ebe8),
    brand = BRAND,
    brandSoft = Color(0xFFe0f2ee),
    onBrand = Color(0xFFffffff),
    green = Color(0xFF1d9a6b),
    red = Color(0xFFd64545),
    amber = Color(0xFFb97d10),
    blue = Color(0xFF4a7d9b),
    chip = Color(0xFFe7efec),
    scrim = Color(0x73091615),
    inputBg = Color(0xFFffffff),
    headerBg = CANVAS,
)

val DARK = ThemeColors(
    mode = "dark",
    canvas = Color(0xFF0e1514),
    card = Color(0xFF16211f),
    cardAlt = Color(0xFF1c2927),
    ink = Color(0xFFe8f1ef),
    sub = Color(0xFF9db4b0),
    faint = Color(0xFF6b827e),
    line = Color(0xFF243230),
    brand = BRAND_DARK,
    brandSoft = Color(0xFF16443c),
    onBrand = Color(0xFF08201c),
    green = Color(0xFF3ecf94),
    red = Color(0xFFff7b72),
    amber = Color(0xFFe3b341),
    blue = Color(0xFF7ab3d0),
    chip = Color(0xFF20302d),
    scrim = Color(0x99000000),
    inputBg = Color(0xFF1c2927),
    headerBg = Color(0xFF0e1514),
)

/** The OS color scheme, observed reactively (0 = unspecified/light, 1 = dark). */
val osDark = MutableStateFlow(false)

/** Resolve theme from the user setting ("auto" follows the OS). */
fun resolveTheme(setting: String): ThemeColors {
    val dark = setting == "dark" || (setting == "auto" && osDark.value)
    return if (dark) DARK else LIGHT
}

@Composable
fun themeColors(setting: String): ThemeColors = resolveTheme(setting)

/* type scales — nothing smaller than 13 (readability requirement) */
object F {
    const val hero = 30
    const val title = 22
    const val h2 = 18
    const val body = 16
    const val small = 14
    const val tiny = 13 // absolute minimum
    const val keypad = 30
}
