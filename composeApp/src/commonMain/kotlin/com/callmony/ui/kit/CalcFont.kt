package com.callmony.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import callmony.composeapp.generated.resources.Res
import callmony.composeapp.generated.resources.calcfont_bold
import callmony.composeapp.generated.resources.calcfont_medium
import callmony.composeapp.generated.resources.calcfont_regular
import callmony.composeapp.generated.resources.calcfont_semibold
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font

/* v1.6.50 — THE CALCULATION TYPEFACE (report #8: "use better font family
 * for the content that goes with calculation"): IBM Plex Sans (SIL Open
 * Font License, © 2019 IBM Corp.), instanced statically from the official
 * variable font at four weights — Regular / Medium / SemiBold / Bold —
 * and bundled once as compose resources so BOTH platforms share the exact
 * same cut. Plex was picked deliberately for calculation content: open,
 * slightly-humanist letterforms with tall, unambiguous digits (0 / O, 1 /
 * l never collide), uniform stroke contrast that stays legible at the
 * canvas's small sizes, and a mathematically even rhythm between words
 * and operators on the same line. Everything that "goes with a
 * calculation" renders in it — the canvas's active line, the locked
 * ledger lines, every total, the show-your-work steps, the canvas title,
 * the keypad's own glyphs and the ledger card's calculation preview.
 * Every other surface keeps the platform system font.
 *
 * The family is resolved ONCE per process and never throws: font
 * resources resolve lazily at first text layout (Android reads them from
 * the APK's composeResources assets — the exact path the bundled logo
 * uses), and any environment that cannot read them (a JVM harness without
 * the assets) falls back to the platform's default family instead of
 * failing the render. */

private var cached: FontFamily? = null

/** The calculation typeface (IBM Plex Sans, four weights). Falls back to
 *  the platform default family when the bundled resources cannot load. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun calcFontFamily(): FontFamily {
    cached?.let { return it }
    val ff = runCatching {
        FontFamily(
            Font(Res.font.calcfont_regular, FontWeight.Normal),
            Font(Res.font.calcfont_medium, FontWeight.Medium),
            Font(Res.font.calcfont_semibold, FontWeight.SemiBold),
            Font(Res.font.calcfont_bold, FontWeight.Bold),
        )
    }.getOrDefault(FontFamily.Default)
    cached = ff
    return ff
}
