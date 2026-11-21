package com.callmony.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import callmony.composeapp.generated.resources.Res
import callmony.composeapp.generated.resources.lexend_bold
import callmony.composeapp.generated.resources.lexend_extralight
import callmony.composeapp.generated.resources.lexend_light
import callmony.composeapp.generated.resources.lexend_medium
import callmony.composeapp.generated.resources.lexend_regular
import callmony.composeapp.generated.resources.lexend_semibold
import com.callmony.lib.AppStore
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font

/* v1.6.52 — THE APP THEME (Light Glass spec: "Material 3 as the base, fully
   re-themed … wrap the app in AppTheme { }"). The app was bare Compose with
   no MaterialTheme since v1.0; this wrapper provides the re-themed M3
   scheme (never default purple) AND — more importantly for the re-skin —
   the LEXEND FontFamily through MaterialTheme.typography + LocalTextStyle,
   so every Text() in the tree inherits Lexend without a single call-site
   edit. The legacy C()/ThemeColors token system stays the app's source of
   truth (every screen reads it); the M3 scheme mirrors the same palette so
   the (few) M3 components used (CircularProgressIndicator, ripple-less
   surfaces) match too. */

private var cachedLexend: FontFamily? = null

/** Lexend (SIL OFL, v1.6.52) — the Light Glass typeface: 200/300 hero
 *  numerals, 400 body, 500 labels, 600 titles. Falls back to the platform
 *  default family when the bundled resources cannot load (JVM harnesses). */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun lexendFamily(): FontFamily {
    cachedLexend?.let { return it }
    val ff = runCatching {
        FontFamily(
            Font(Res.font.lexend_extralight, FontWeight.ExtraLight),
            Font(Res.font.lexend_light, FontWeight.Light),
            Font(Res.font.lexend_regular, FontWeight.Normal),
            Font(Res.font.lexend_medium, FontWeight.Medium),
            Font(Res.font.lexend_semibold, FontWeight.SemiBold),
            Font(Res.font.lexend_bold, FontWeight.Bold),
        )
    }.getOrDefault(FontFamily.Default)
    cachedLexend = ff
    return ff
}

/* v1.6.52 — reserved for M3-component typography overrides (unused by the
   bare-Text app surface; the ambient style carries the family only). */
private val LocalAppTypography = staticCompositionLocalOf<Typography?> { null }

/** The Light Glass M3 light scheme (spec's lightColorScheme mapping). */
fun glassLightScheme(): ColorScheme = lightColorScheme(
    primary = AppColors.Brand,
    onPrimary = Color.White,
    primaryContainer = AppColors.Brand50,
    onPrimaryContainer = AppColors.BrandDark,
    secondary = AppColors.Muted,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = AppColors.Ink,
    surface = Color.White,
    onSurface = AppColors.Ink,
    surfaceVariant = AppColors.Brand50,
    onSurfaceVariant = AppColors.Muted,
    outline = AppColors.Line,
    error = AppColors.Danger,
    tertiary = AppColors.Accent,
)

/** The matching deep-navy dark scheme. */
fun glassDarkScheme(): ColorScheme = darkColorScheme(
    primary = Color(0xFF3B94FF),
    onPrimary = Color(0xFF061224),
    primaryContainer = Color(0xFF14304F),
    onPrimaryContainer = Color(0xFFBBD9FF),
    secondary = Color(0xFF9FB1C9),
    background = Color(0xFF0B1626),
    onBackground = Color(0xFFEAF1FB),
    surface = Color(0xFF152238),
    onSurface = Color(0xFFEAF1FB),
    surfaceVariant = Color(0xFF1B2B45),
    onSurfaceVariant = Color(0xFF9FB1C9),
    outline = Color(0x243B94FF),
    error = Color(0xFFF87171),
    tertiary = AppColors.Accent,
)

private fun glassTypography(ff: FontFamily): Typography = Typography(
    // hero numerals: light-weight, generous — the Light Glass signature
    displayLarge = TextStyle(fontFamily = ff, fontWeight = FontWeight.ExtraLight, fontSize = 56.sp, lineHeight = 62.sp),
    displayMedium = TextStyle(fontFamily = ff, fontWeight = FontWeight.Light, fontSize = 48.sp, lineHeight = 54.sp),
    displaySmall = TextStyle(fontFamily = ff, fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 46.sp),
    headlineLarge = TextStyle(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = ff, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = ff, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = ff, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = ff, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = ff, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = ff, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = ff, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = ff, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

/** The app theme. Light Glass by default; dark scheme follows the same
 *  setting the legacy token system uses (settings.theme + OS auto).
 *
 *  METRICS CONTRACT: the ambient LocalTextStyle provides ONLY the Lexend
 *  family — never fontSize/lineHeight from the M3 typography. The app's
 *  dense pixel-pinned surfaces (the canvas's 11-stop pixel ladder, the
 *  date-beside-total contract, the prefix slot geometry) set explicit
 *  sizes everywhere; letting M3's lineHeights ride the ambient style
 *  changed measured text heights app-wide (caught by the v1.6.49 date/
 *  total pin). M3 components still read the full typography. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val setting = AppStore.state.settings.theme
    val dark = setting == "dark" || (setting == "auto" && isSystemInDarkTheme())
    val scheme = if (dark) glassDarkScheme() else glassLightScheme()
    val ff = lexendFamily()
    MaterialTheme(
        colorScheme = scheme,
        typography = glassTypography(ff),
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides TextStyle(fontFamily = ff),
        ) {
            content()
        }
    }
}
