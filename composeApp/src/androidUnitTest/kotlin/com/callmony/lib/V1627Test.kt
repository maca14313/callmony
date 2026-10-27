package com.callmony.lib

import com.callmony.model.DeviceContact
import com.callmony.model.DevicePhone
import com.callmony.model.EntryItem
import com.callmony.model.LedgerEntry
import org.junit.Before
import org.junit.Test

/* v1.6.27 regressions — the three user reports this version fixes:
   1. FAVOURITE NUMBER set/UNSET (the star in the choose-a-number sheet):
      primaryPhone() must follow ContactX.primaryPhone and fall back to the
      first stored phone after clearPrimaryPhone().
   2. THE QUICK ITEM ("in the ledger there could be an item that just have
      label and price") — what the quick-add row saves must round-trip through
      the store with its label as the single line item, a working remaining
      balance, and the kind the search kind-tabs filter on.
   3. THE SEARCH KIND TABS ("not listing the CONTACTS that have them") — the
      exact predicate the kind row uses to map entries → contact recordIDs. */

class V1627Test {

    @Before
    fun resetStore() {
        AppStore.state.deviceContacts = emptyList()
        AppStore.state.contactX = emptyList()
        AppStore.state.entries = emptyList()
    }

    private fun contact(vararg numbers: String): DeviceContact = DeviceContact(
        recordID = "r1",
        displayName = "Test Person",
        givenName = "Test",
        familyName = "Person",
        phones = numbers.mapIndexed { i, n -> DevicePhone("p$i", if (i == 0) "mobile" else "work", n) },
    )

    @Test
    fun favouriteNumberSetsAndUnsets() {
        val c = contact("+251911111111", "+251922222222")
        AppStore.state.deviceContacts = listOf(c)

        // default: the first stored phone
        check(primaryPhone(c) == "+251911111111") { "default not first phone: ${primaryPhone(c)}" }

        // set the favourite → every dial surface now gets the work number
        AppStore.setPrimaryPhone("r1", "+251922222222")
        check(primaryPhone(c) == "+251922222222") { "set favourite not honored: ${primaryPhone(c)}" }

        // UNSET (the star tapped on the already-favourite number) → back to
        // the first stored phone
        AppStore.clearPrimaryPhone("r1")
        val x = AppStore.contactXOf("r1")
        check(x?.primaryPhone == null) { "clearPrimaryPhone left ${x?.primaryPhone}" }
        check(primaryPhone(c) == "+251911111111") { "after unset not back to first: ${primaryPhone(c)}" }
    }

    @Test
    fun favouriteNumberFallsBackWhenPhoneRemoved() {
        // primaryPhone points at a number the contact no longer has → the
        // exact-match guard must fall through to the first stored phone
        val c = contact("+251911111111", "+251922222222")
        AppStore.state.deviceContacts = listOf(c)
        AppStore.setPrimaryPhone("r1", "+251999999999")
        check(primaryPhone(c) == "+251911111111") { "stale favourite not ignored: ${primaryPhone(c)}" }
    }

    @Test
    fun quickItemRoundTrips() {
        // exactly what the ledger quick-add row creates: label + price
        val e = LedgerEntry(
            id = uid(),
            ownerId = "c:r1",
            type = "credit",
            kind = "credit",
            amount = 25.0,
            items = listOf(EntryItem(id = uid(), name = "Milk", qty = 1.0, price = 25.0)),
            date = todayStr(),
            createdAt = now(),
            updatedAt = now(),
        )
        AppStore.saveEntry(e)

        val saved = AppStore.state.entries.first { it.id == e.id }
        check(saved.amount == 25.0)
        // the label rides as the single line item — the EntryCard shows it
        // as the card title (items.singleOrNull()?.name)
        check(saved.items.singleOrNull()?.name == "Milk") { "label not the single item" }
        check(entryRemaining(saved) == 25.0) { "remaining wrong: ${entryRemaining(saved)}" }
        // the search kind-tabs predicate must match it as a credit for r1
        check((saved.kind ?: saved.type) == "credit")
        check(saved.ownerId.startsWith("c:"))
    }

    @Test
    fun searchKindPredicateListsOnlyContactOwnersOfThatKind() {
        // the exact predicate the search kind row uses (MiscOverlays,
        // contactsWithKind): entries of the active kind owned by CONTACTS
        fun ownersOfKind(kind: String): Set<String> =
            AppStore.state.entries
                .filter { it.deletedAt == null && it.ownerId.startsWith("c:") && (it.kind ?: it.type) == kind }
                .map { it.ownerId.substring(2) }
                .toSet()

        AppStore.state.entries = listOf(
            LedgerEntry(id = "e1", ownerId = "c:r1", type = "credit", kind = "credit", amount = 10.0, date = todayStr(), createdAt = now(), updatedAt = now()),
            LedgerEntry(id = "e2", ownerId = "c:r2", type = "debit", kind = "debit", amount = 7.5, date = todayStr(), createdAt = now(), updatedAt = now()),
            LedgerEntry(id = "e3", ownerId = "general", type = "credit", kind = "income", amount = 99.0, date = todayStr(), createdAt = now(), updatedAt = now()),
            LedgerEntry(id = "e4", ownerId = "c:r1", type = "debit", kind = "expense", amount = 4.0, date = todayStr(), createdAt = now(), updatedAt = now()),
        )

        check(ownersOfKind("credit") == setOf("r1")) { "credit owners: ${ownersOfKind("credit")}" }
        check(ownersOfKind("debit") == setOf("r2")) { "debit owners: ${ownersOfKind("debit")}" }
        check(ownersOfKind("income").isEmpty()) { "general income must not map to a contact" }
        check(ownersOfKind("expense") == setOf("r1")) { "expense owners: ${ownersOfKind("expense")}" }

        // deleted entries never count
        AppStore.state.entries = AppStore.state.entries.map { if (it.id == "e1") it.copy(deletedAt = now()) else it }
        check(ownersOfKind("credit").isEmpty()) { "deleted entry still listed" }
    }
}
