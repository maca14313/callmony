package com.callmony.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow

/* v1.6.52 — THE LIGHT GLASS THEME (user spec: "Follow the 'Light Glass'
   style below on every screen and component"). The token system keeps the
   v1.6.x ThemeColors surface (every screen reads colors through C(), so a
   palette re-skin re-themes the whole app) but LIGHT is now the Light
   Glass palette — bright white canvas, brand-blue actions, brand-tinted
   hairlines and shadows, generous radii — and DARK is a matching deep-navy
   glass cut so the existing theme switch stays functional. The raw tokens
   (AppColors/AppShapes) below are the spec's swap-to-rebrand point. */

/* ---------------- design tokens (swap here to rebrand) ---------------- */

object AppColors {
    val Brand      = Color(0xFF1F7AEC)   // primary actions, active tabs, key numbers
    val BrandLight = Color(0xFF3B94FF)   // gradient start
    val BrandDark  = Color(0xFF1565D8)   // gradient end / pressed
    val Brand50    = Color(0xFFEEF5FF)   // tints, hover/selected rows, tags
    val Accent     = Color(0xFFFF6B4A)   // warm glow only, used sparingly
    val Ink        = Color(0xFF0F1B2D)   // main text
    val Muted      = Color(0xFF64748B)   // secondary text
    val Line       = Color(0x1A1F7AEC)   // brand @10% hairlines
    val Success    = Color(0xFF10B981)
    val Danger     = Color(0xFFEF4444)
    val Warning    = Color(0xFFFBBF24)
}

object AppShapes {
    val Card   = RoundedCornerShape(24.dp)
    val Sheet  = RoundedCornerShape(36.dp)
    val Tile   = RoundedCornerShape(12.dp)
    val Pill   = RoundedCornerShape(percent = 50)
}

/* ---------------- the ThemeColors API (unchanged surface) ---------------- */

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
) {
    /** v1.6.52 — the brand GRADIENT endpoints (Light Glass primary
     *  surfaces: buttons, FAB, keypad dialer bar). */
    val brandLight: Color get() = if (mode == "dark") Color(0xFF3B94FF) else AppColors.BrandLight
    val brandDark: Color get() = if (mode == "dark") Color(0xFF0E4CAB) else AppColors.BrandDark
}

val LIGHT = ThemeColors(
    mode = "light",
    canvas = Color.White, // v1.6.54 — pure white page (round 2: no blue spill, bg only white)
    card = Color.White,
    cardAlt = Color(0xFFF1F6FD),
    ink = AppColors.Ink,
    sub = AppColors.Muted,
    faint = Color(0xFF93A3B8),
    line = AppColors.Line,
    brand = AppColors.Brand,
    brandSoft = AppColors.Brand50,
    onBrand = Color.White,
    green = AppColors.Success,
    red = AppColors.Danger,
    amber = Color(0xFFB7791F), // readable amber on white (WCAG on light glass)
    blue = Color(0xFF2563EB),
    chip = Color(0xFFEFF4FB),
    scrim = Color(0x660F1B2D), // v1.6.54 — Ink @40% scrim (round 2, spec 1: the page behind a sheet dims under Ink@40%)
    inputBg = Color.White,
    headerBg = Color.White, // v1.6.54 — the header band is the page itself (transparent over white; round 2, spec 8)
)

val DARK = ThemeColors(
    mode = "dark",
    canvas = Color(0xFF0B1626), // deep navy glass
    card = Color(0xFF152238),
    cardAlt = Color(0xFF1B2B45),
    ink = Color(0xFFEAF1FB),
    sub = Color(0xFF9FB1C9),
    faint = Color(0xFF67799A),
    line = Color(0x243B94FF),
    brand = Color(0xFF3B94FF),
    brandSoft = Color(0xFF14304F),
    onBrand = Color(0xFF061224),
    green = Color(0xFF34D399),
    red = Color(0xFFF87171),
    amber = Color(0xFFFBBF24),
    blue = Color(0xFF60A5FA),
    chip = Color(0xFF1B2B45),
    scrim = Color(0x99061021),
    inputBg = Color(0xFF152238),
    headerBg = Color(0xFF0B1626),
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

/* type scales — nothing smaller than 13 (readability requirement).
   v1.6.52: sizes unchanged (the app's dense ledger surfaces were tuned
   against these stops); the FACES change — AppTheme now provides Lexend
   app-wide, and hero numerals get the Light glass treatment (48-64sp
   Light/ExtraLight in Brand) at their dedicated call sites. */
object F {
    const val hero = 30
    const val title = 22
    const val h2 = 18
    const val body = 16
    const val small = 14
    const val tiny = 13 // absolute minimum
    const val keypad = 30
}
