package com.callmony.ui.screens

import com.callmony.model.DeviceContact
import org.junit.Test

/* v1.6.34 regression — the owe-tab SUB-FILTERS (user report: "what i
   wanted was when one of the Filter Buttons (They owe you / You owe them)
   are clicked to be different kind of tabs that help filter like custom
   date, by amount, by name, other useful filters"). The v1.6.30/33 attempt
   stacked the CALL-DIRECTION chips under the owe buttons — wrong axis.
   applyOweFilters is the pure pipeline behind the new Date / Amount /
   Name chips: owe direction → classic direction → minimum |balance| →
   date window (presets + custom range) → name ordering. */

private fun contact(id: String, name: String) = DeviceContact(
    recordID = id, displayName = name, givenName = name, familyName = "",
)

private fun row(key: String, contact: DeviceContact?, at: Long, direction: String = "incoming") =
    CallRow(key = key, phone = "9$key", name = contact?.displayName, contact = contact, direction = direction, at = at, duration = 10)

class OweFiltersTest {

    private val nowMs = 1_800_000_000_000L
    private val day = 86_400_000L

    private val alice = contact("1", "Alice")
    private val bob = contact("2", "Bob")
    private val carol = contact("3", "Carol")

    private val rows = listOf(
        row("a", alice, nowMs),                   // Alice: owes you 500 — just now
        row("b", bob, nowMs - 3 * day),           // Bob: owes you 10 — 3 days back
        row("c", carol, nowMs - 40 * day),        // Carol: you owe 1000 — far back
    )

    private val balances = mapOf("1" to 500.0, "2" to 10.0, "3" to -1000.0)

    private fun run(tab: String, f: OweFilters, direction: String = "all") =
        applyOweFilters(rows, tab, balances, emptySet(), direction, f, nowMs)

    @Test
    fun oweTabNarrowsToTheBalanceDirection() {
        val theyOwe = run("theyOwe", OweFilters())
        check(theyOwe.map { it.key } == listOf("a", "b")) { theyOwe.map { it.key } }
        val youOwe = run("youOwe", OweFilters())
        check(youOwe.map { it.key } == listOf("c")) { youOwe.map { it.key } }
    }

    @Test
    fun minAmountFiltersOnAbsoluteBalance() {
        val f = OweFilters(minAmount = 100.0)
        check(run("theyOwe", f).map { it.key } == listOf("a"))
        // |−1000| ≥ 100 — the you-owe side is filtered on magnitude too
        check(run("youOwe", f).map { it.key } == listOf("c"))
    }

    @Test
    fun dateWindowPresetsFilter() {
        check(run("theyOwe", OweFilters(days = 7)).map { it.key } == listOf("a", "b"))
        check(run("theyOwe", OweFilters(days = 1)).map { it.key } == listOf("a"))
        check(run("theyOwe", OweFilters(days = 30)).map { it.key } == listOf("a", "b"))
    }

    @Test
    fun customRangeWindowFilters() {
        val f = OweFilters(days = -1, fromMs = nowMs - 2 * day, toMs = nowMs)
        check(run("theyOwe", f).map { it.key } == listOf("a")) { run("theyOwe", f).map { it.key } }
        // a window in the far past keeps nothing
        val none = OweFilters(days = -1, fromMs = nowMs - 100 * day, toMs = nowMs - 50 * day)
        check(run("theyOwe", none).isEmpty())
    }

    @Test
    fun nameSortsGoFlatAlphabetical() {
        val az = run("theyOwe", OweFilters(nameSort = "az"))
        check(az.map { it.key } == listOf("a", "b")) // Alice, Bob — already az
        val za = run("theyOwe", OweFilters(nameSort = "za"))
        check(za.map { it.key } == listOf("b", "a")) { za.map { it.key } }
        // an unnamed row sorts by raw number, behind every name
        val extra = listOf(row("z", null, nowMs))
        val sorted = applyOweFilters(extra + rows, "theyOwe", balances, emptySet(), "all", OweFilters(nameSort = "az"), nowMs)
        check(sorted.first().key == "a") { sorted.map { it.key } } // Alice first, number last
    }

    @Test
    fun classicDirectionStillComposesInsideAnOweTab() {
        val missed = run("theyOwe", OweFilters(), direction = "missed")
        check(missed.isEmpty()) // all fixtures are incoming
        val incoming = run("theyOwe", OweFilters(), direction = "incoming")
        check(incoming.map { it.key } == listOf("a", "b"))
    }
}
