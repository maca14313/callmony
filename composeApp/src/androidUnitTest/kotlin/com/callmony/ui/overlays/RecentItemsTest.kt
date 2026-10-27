package com.callmony.ui.overlays

import com.callmony.model.EntryItem
import com.callmony.model.LedgerEntry
import org.junit.Test

/* v1.6.34 regression — the RECENT tab of the add-item flow (the "other
   useful type" the user asked for): one-tap reuse of items booked before.
   recentItems keeps the DISTINCT labels across every entry (newest entry
   first, case-insensitive), each with the price it was last booked at,
   capped so the chip row stays a row. */

private fun entry(id: String, updatedAt: Long, vararg items: EntryItem) = LedgerEntry(
    id = id, ownerId = "u:me", type = "credit", kind = "credit", amount = 0.0,
    items = items.toList(), date = "2026-01-01", createdAt = updatedAt, updatedAt = updatedAt,
)

class RecentItemsTest {

    @Test
    fun newestEntryFirstWithCaseInsensitiveDedupe() {
        val list = listOf(
            entry("old", 100, EntryItem("i1", "Coffee", 1.0, price = 25.0)),
            entry("new", 200, EntryItem("i2", "coffee", 1.0, price = 30.0), EntryItem("i3", "Taxi", 1.0, price = 40.0)),
        )
        val out = recentItems(list)
        // the NEW entry's spelling + price win; the old duplicate is gone
        check(out == listOf("coffee" to 30.0, "Taxi" to 40.0)) { out }
    }

    @Test
    fun blankLabelsAndEmptyEntriesAreSkipped() {
        val list = listOf(
            entry("e", 100, EntryItem("i1", "  ", 1.0, price = 5.0)),
        )
        check(recentItems(list).isEmpty())
        check(recentItems(emptyList()).isEmpty())
    }

    @Test
    fun cappedAtFourteenLabels() {
        val items = (1..30).map { EntryItem("i$it", "Item $it", 1.0, price = it.toDouble()) }
        check(recentItems(listOf(entry("e", 100, *items.toTypedArray()))).size == 14)
    }
}
