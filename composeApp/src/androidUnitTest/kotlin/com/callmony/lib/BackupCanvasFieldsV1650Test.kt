package com.callmony.lib

import com.callmony.model.LedgerEntry
import com.callmony.model.Payment
import kotlinx.serialization.json.Json
import org.junit.Test

/* v1.6.50 — regression battery for report #13 of the thirteen-report round
   ("this app should not loss all kind of data it have that user puts at
   it, even when installing again or update"): every CANVAS-era field the
   user's ledger entries carry must survive the BACKUP export → restore
   round trip byte-for-byte — the calculation grammar itself (exprNote
   with its ⏎ intra-line breaks, the @date and ~currency markers), the
   canvas title, the prefix choice, the per-entry currency and font size,
   and the recorded payments with their line attribution. The no-data-loss
   layer itself (Android Auto Backup rules + the auto-exported
   Downloads/Callmony file + the one-tap restore offer) shipped in
   v1.6.26/34; this battery pins that the round trip never DROPS a canvas
   field as the canvas grows them. */

class BackupCanvasFieldsV1650Test {

    @Test
    fun everyCanvasFieldSurvivesTheBackupRoundTrip() {
        val entry = LedgerEntry(
            id = "e1", ownerId = "u:me", type = "credit", kind = "credit",
            amount = 650.0,
            date = "2026-02-03", dueDate = "2026-03-01",
            category = "rent", method = "bank",
            note = "the monthly rent",
            exprNote = "rooms 3 × 200 ~USD @2026-02-03\ntransport 50",
            calcTitle = "February rent",
            calcPrefix = "number",
            calcCurrency = "USD",
            calcFontSize = "17",
            payments = listOf(
                Payment(id = "p1", amount = 300.0, date = "2026-02-05", method = "bank", note = "first", lineIdx = 0),
                Payment(id = "p2", amount = 50.0, date = "2026-02-10", method = "cash", lineIdx = null),
            ),
            createdAt = 1_800_000_000_000L, updatedAt = 1_800_000_100_000L,
        )
        val text = Json.encodeToString(
            PersistedDataDto.serializer(),
            PersistedDataDto(exportedAt = 1_800_000_200_000L, entries = listOf(entry)),
        )
        val back = checkNotNull(Backup.parse(text)) { "the backup must parse" }
        val e = checkNotNull(back.entries.firstOrNull { it.id == "e1" })

        check(e.exprNote == entry.exprNote) { "the calculation grammar must survive — got ${e.exprNote}" }
        check(e.calcTitle == "February rent") { "the canvas title must survive" }
        check(e.calcPrefix == "number") { "the prefix choice must survive" }
        check(e.calcCurrency == "USD") { "the entry currency must survive" }
        check(e.calcFontSize == "17") { "the px font size must survive" }
        check(e.amount == 650.0 && e.dueDate == "2026-03-01" && e.category == "rent") {
            "the classic fields must survive too"
        }
        check(e.payments.size == 2 && e.payments[0].lineIdx == 0 && e.payments[1].lineIdx == null) {
            "the payments and their line attribution must survive"
        }
    }

    @Test
    fun aLegacyBackupWithNoCanvasFieldsStillLoads() {
        // pre-canvas rows (no calcTitle / calcPrefix / calcCurrency /
        // calcFontSize / exprNote at all) must restore unchanged — the
        // update path can never be a downgrade for old data
        val text = """
            {"app":"callmony","version":1,"exportedAt":5,
             "entries":[{"id":"old","ownerId":"u:me","type":"debit","kind":"debit",
                         "amount":75.0,"date":"2025-11-01",
                         "createdAt":1,"updatedAt":1}]}
        """.trimIndent()
        val back = checkNotNull(Backup.parse(text)) { "a legacy backup must parse" }
        val e = back.entries.first()
        check(e.id == "old" && e.amount == 75.0 && e.calcTitle == null && e.calcFontSize == null)
    }
}
