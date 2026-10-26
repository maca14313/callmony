package com.callmony.lib

/* v1.6.24 — the tiny survivor of the v1.6.20-23 diagnostics experiment.

   The watchdog, freeze reports, crash firewall and diag screen are GONE
   (the user never asked for them and they caused confusion); what stays is
   the one genuinely useful, completely invisible piece: a plain string
   marking WHERE the app is in its boot/UI lifecycle.

   Why keep even this: the Robolectric boot harnesses (BootDiagTest /
   UpgradeBootDiagTest) drive a full app boot and assert the phase reaches
   ui.idle — that is the regression guard for the "app freezes forever on
   open" class (the v1.6.18 infinite-spinner-under-onboarding hang was
   caught exactly this way). A plain var, no threads, no UI, no storage. */

object BootTrace {
    /** current boot/UI phase, e.g. "app.init" … "store.loaded" … "ui.idle".
     *  Plain var (Kotlin/Native has no @Volatile); written from any thread,
     *  read by the boot harness. Not Compose state — nothing renders it. */
    var phase: String = "boot"
        private set

    fun mark(p: String) { phase = p }
}
