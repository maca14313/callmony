package com.callmony.lib

import com.callmony.theme.osDark
import org.junit.Before
import org.junit.Test

/* v1.6.28 regressions — the two user reports this version fixes:
   1. THE OPEN FLASH ("there is some flash when it opens"): the splash used
      to render from the DEFAULT settings (theme "auto", language "en")
      until hydrate published the persisted ones — a dark-theme user saw
      dark system splash → LIGHT Compose splash (the flash) → dark main UI,
      and an am/om/ar/ti user saw the tagline flash English-first.
      seedBootAppearance must install theme + language + osDark BEFORE the
      first frame, and must be INERT once hydrated (an activity recreate
      can never clobber the real settings).
   2. THE SLOW OPEN ("still opens slow"): hydrate is now kicked from the
      platform entry point BEFORE the first frame, so it must stay
      single-shot — a second boot pass would double the open time. */

class V1628Test {

    @Before
    fun resetStore() {
        AppStore.state.settings = Db.DEFAULT_SETTINGS
        AppStore.state.hydrated = false
        osDark.value = false
    }

    @Test
    fun seedInstallsThemeLanguageAndOsDark() {
        AppStore.seedBootAppearance(sysDark = true, themeDark = true, lang = "am")
        check(AppStore.state.settings.theme == "dark") { "theme not seeded: ${AppStore.state.settings.theme}" }
        check(AppStore.state.settings.language == "am") { "language not seeded: ${AppStore.state.settings.language}" }
        check(osDark.value) { "osDark not seeded from sysDark" }
    }

    @Test
    fun seedResolvesLightAndNormalizesLanguage() {
        AppStore.seedBootAppearance(sysDark = false, themeDark = false, lang = "zz")
        check(AppStore.state.settings.theme == "light")
        // an unknown language code falls back to English (normalizeLang)
        check(AppStore.state.settings.language == "en")
        check(!osDark.value)
    }

    @Test
    fun seedIsInertOnceHydrated() {
        // real settings are already published → the seed must not touch them
        AppStore.state.hydrated = true
        AppStore.state.settings = AppStore.state.settings.copy(theme = "auto", language = "om")
        AppStore.seedBootAppearance(sysDark = true, themeDark = true, lang = "ar")
        check(AppStore.state.settings.theme == "auto") { "seed clobbered the hydrated theme" }
        check(AppStore.state.settings.language == "om") { "seed clobbered the hydrated language" }
    }

    /* The hydrate single-shot latch is NOT exercised here: calling the real
       hydrate() from a plain JVM test would die on the missing Main
       dispatcher / platform kv. The Robolectric battery covers it — boot
       now kicks hydrate from MainActivity BEFORE setContent, and
       BootDiagTest + CallRowSheetStripTest both waitUntil(hydrated) through
       that path (a double-run or a broken guard would hang boot). */
}
