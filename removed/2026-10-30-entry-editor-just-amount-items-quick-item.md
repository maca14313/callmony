# Removed features — the entry editor's amount-input tabs

**Removed in:** v1.6.38 (versionCode 49)
**Reason (user instruction, verbatim):** *"remove the just amount, qty*price and Quick item, document them with all of there features, how they work, push the document in to removed folder in GitHub"*
**Replaced by:** the annotated calculator canvas — the single amount input for every entry. Words, numbers and operators flow as one line of free text ("ball 200 + 100 cups = 300 Br"), multi-line with a locked ledger and a running total.

From v1.6.34 to v1.6.37 the entry editor (Credit / Debit / Income / Expense) carried a horizontally scrolling strip of input-mode tabs under the kind chips:

```
[ Calculator ] [ Just amount ] [ Items · qty × price ] [ Quick item ] [ Recent ]
```

v1.6.38 removed **Just amount**, **Items · qty × price** and **Quick item**. The strip now carries only **Calculator** and **Recent**. This document preserves everything they did, exactly as they worked, so the behaviour can be restored or re-implemented anywhere.

---

## 1. Just amount (`ee.tabJustAmount`, chip icon `cash-check`)

The classic "hero amount" input, ported unchanged from the React Native edition.

**Features**
- A full-width rounded box (18dp corners, brand at 8% tint) with the label "Amount" above a large 30sp extra-bold `BasicTextField`.
- Decimal-pad system keyboard; digits and one dot/comma only, cleaned live (`raw.replace(',', '.')`, extra dots stripped, 14 chars max). A faint "0" placeholder when empty; the formatted money value (`money()`, e.g. "100 Br") echoed underneath in a tiny caption.
- A calculator icon button on the right (40dp) that opened the app's main calculator sheet and wrote the picked value back into the field — only shown when the entry had no line items.
- v1.6.37 "base" semantics: the typed value was the entry's BASE; the final amount = base + every line item + the calculator's whole ledger (the "type 100, add a 200 item → 300" flow). On reopen the base was DERIVED back out of the saved amount (saved − items − ledger, clamped at 0) so the field always showed exactly what the user typed.

**How it worked internally**
- State: `amount: String`, parsed as `baseNum = amount.replace(",", ".").toDoubleOrNull()`.
- The active tab on every new entry and on any existing entry without items or an expression note.
- Contribution to the saved `LedgerEntry.amount`: `effAmount = baseNum + itemsSum + calcPartsTotal`.

---

## 2. Items · qty × price (`ee.tabItems`, chip icon `format-list-bulleted`)

The full line-item editor — the oldest accounting surface in the app, ported from the RN edition.

**Features**
- A card form with four fields: Item name (free text), Qty (decimal-pad, default "1"), Unit (free text, placeholder e.g. "pc"), Unit price (decimal-pad, placeholder "0").
- A quick-unit chip row (`pic`, `kg`, `pcs`, `bag`, `box`, `l`) that toggled the unit field.
- "Add item" button → validated (name required, price > 0, toast "Item incomplete" otherwise) and appended an `EntryItem(id, name, qty, unit?, price)` to the entry's item list.
- Below the form: every booked item rendered as a card row "name · qty unit × price = total" with a ✕ remove button; tapping a row switched back to this tab with the item loaded into the form for editing ("Save item" / "Cancel" banner).
- The item list itself was ALWAYS visible on every tab, whatever the input method, with the running TOTAL row underneath ("Total 300" + the "100 + 200" breakdown caption).

**How it worked internally**
- State: `items: List<EntryItem>`, `editingItem: EntryItem?`.
- Each item contributed `qty * price` to `itemsSum`, which flowed into `effAmount`.
- Entries that carried items opened directly on this tab; items were saved on the `LedgerEntry.items` field and survived round trips.
- v1.6.38 note: the inline EDITOR for existing items still exists (tap an item row) — only the ADD tab is gone. New items can still be booked through the Recent tab or by writing them into the calculator line.

---

## 3. Quick item (`ee.tabQuick`, chip icon `clock-fast`)

The one-line "label + price" booking, born as the v1.6.27/29 ledger quick row and moved into the entry editor's tab strip in v1.6.34 (user request: *"the quick item — label + price — should be inside ADD ITEM"*).

**Features**
- Two fields in one row: a free-text label (placeholder "Label of the item") and a 110dp-wide price field (decimal-pad, placeholder "Price"); pressing either field's IME action or the big brand ＋ button (46dp, white plus) booked the item.
- Validation toasts: "Label of the item is required" when the label was blank; "Amount must be greater than zero" for a missing/zero price.
- A successful booking appended a qty-1 `EntryItem(name = label, qty = 1.0, price = price)` to the same items list as the Items tab, cleared both fields, and was ready for the next line — the "keep adding until done, then Save" flow.
- The entry amount followed the item total automatically.

**How it worked internally**
- State: `quickLabel: String`, `quickPrice: String`; `addQuickItem()` built the `EntryItem` with `uid()`.
- The ＋ button was matched in the test harness by its "plus" content description inside the tab's input row.

---

## Why they were removed

The annotated calculator subsumes all three flows in a single, faster surface:

- **Just amount** → type the number directly into the calculator line (a bare number is a valid line).
- **Items · qty × price** → write the arithmetic as text: "ball 2 pc × 200" computes 400 with the label attached to the number, and "Next line" stacks multiple positions like a receipt.
- **Quick item** → the same one-line flow: "Tea 35" is a labelled number; the Recent tab still offers one-tap repeat bookings of previously used items.

The removal also simplifies THE SUM (v1.6.37): the entry amount is now always `line items + the calculator's ledger` — there is no separate typed base to reconcile, and legacy entries reopen with their plain amount seeded as the calculator's first locked line so nothing is lost.

**Kept:** the Recent tab, the inline item editor for existing items, the always-visible item list with ✕ remove, and every other section of the entry editor (kind chips, date/method, categories, due date, note, attachments, owner, groups).
