package com.callmony.lib

import com.callmony.model.EntryItem
import com.callmony.model.LedgerEntry
import kotlinx.serialization.json.Json
import org.junit.Test

/* v1.6.34 regression — the AUTO-RESTORE OFFER's parser (user report:
   "every time I install the app the data is all gone"). parseForOffer is
   the gate between the surviving Downloads/Callmony backup file and the
   one-tap restore: a VALID backup holding REAL data parses to its DTO
   (exportedAt included, for the prompt's date) + PersistedData; an empty
   or corrupt file never offers anything. */

class AutoBackupOfferTest {

    private fun backupJson(entries: Int, exportedAt: Long = 1_800_000_000_000L): String {
        val dto = PersistedDataDto(
            exportedAt = exportedAt,
            entries = (1..entries).map { i ->
                LedgerEntry(
                    id = "e$i", ownerId = "u:me", type = "credit", kind = "credit",
                    amount = 25.0, items = listOf(EntryItem("i$i", "Coffee", 1.0, price = 25.0)),
                    date = "2026-01-01", createdAt = exportedAt, updatedAt = exportedAt,
                )
            },
        )
        return Json.encodeToString(PersistedDataDto.serializer(), dto)
    }

    @Test
    fun validBackupWithRealDataOffers() {
        val offer = AutoBackup.parseForOffer(backupJson(2))
        check(offer != null) { "expected an offer" }
        val (dto, data) = offer
        check(dto.exportedAt == 1_800_000_000_000L)
        check(data.entries.size == 2)
        check(data.entries[0].items[0].name == "Coffee")
    }

    @Test
    fun emptyBackupNeverOffers() {
        check(AutoBackup.parseForOffer(backupJson(0)) == null)
        check(AutoBackup.parseForOffer("""{"app":"callmony","version":1,"exportedAt":5}""") == null)
    }

    @Test
    fun garbageNeverOffers() {
        check(AutoBackup.parseForOffer(null) == null)
        check(AutoBackup.parseForOffer("") == null)
        check(AutoBackup.parseForOffer("not json at all") == null)
        check(AutoBackup.parseForOffer("{\"broken\":") == null)
    }

    @Test
    fun forwardCompatibleWithUnknownFields() {
        // the offer must accept exactly what Backup.exportAll produces —
        // and stay open to FUTURE backup shapes (unknown fields ignored,
        // the same lenient Json Backup.parse has always used)
        val base = Json.parseToJsonElement(backupJson(1)).toString()
        val withFuture = base.dropLast(1) + ""","futureField":{"x":1}}"""
        check(AutoBackup.parseForOffer(withFuture) != null) { "future backup shape rejected" }
    }
}
