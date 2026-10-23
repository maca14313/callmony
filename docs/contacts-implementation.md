# Contacts implementation summary (CallMony)

> **Status (v1.6.3):** the Contacts *tab* is parked per user request — the app
> opens on **Recents** only. **Nothing was deleted**: the whole contacts
> pipeline stays live (Recents, search, keypad, pickers, detail pages, editors
> all read the address book). This document is the summary of *how contacts
> are implemented*, so the tab can be brought back in one line when needed.

## 1. Data sources & layers

| Layer | What | Where |
|---|---|---|
| **Device address book** | The phone's real contacts (read via `react-native-contacts`) | `src/native/deviceContacts.ts` |
| **App-side extensions** (favorites, tags, socials, primary number, speed dial) | `ContactX[]` keyed by `recordID`, persisted in AsyncStorage | `src/lib/store.ts` → `saveContactX` |
| **Disk snapshot cache** | Full address-book JSON written to the app documents dir for instant cold starts | `saveCachedContacts` / `loadCachedContacts` |
| **Search index** | WeakMap-cached per-contact index (phones raw/norm/national + name words + company + emails + T9) | `src/lib/utils.ts` → `getContactSearchIndex` |

## 2. Permissions & privacy

- `READ_CONTACTS` / `WRITE_CONTACTS` declared in the manifest and requested at
  runtime (`requestContactsPermission`).
- The OS address book is **never** queried on the boot path: the shell applies
  the cached snapshot first, then a deferred (4 s) background re-query keeps
  names fresh (v1.6.3, needed since the tab is parked).
- Everything is 100% on-device; nothing is uploaded anywhere.

## 3. Reading contacts

- `fetchAllContacts()` returns `DeviceContact[]` (recordID, names, company,
  phones with labels, emails, addresses, birthday, thumbnail IDs).
- `refreshDeviceContacts()` re-queries immediately and rewrites the snapshot
  cache — called after every in-app contact create/edit and on foreground return.
- `loadCachedContacts()` reads the snapshot (with a saved-at stamp) so Recents
  resolves names instantly on a cold open, before any bridge call.

## 4. Number matching (country-code agnostic)

`src/lib/utils.ts`:

- `normalizePhone(raw, countryCode)` → E.164-ish digits; handles `00` prefix,
  `+CC` with/without trunk zero, local trunk-zero forms and USSD codes.
- `nationalDigits(raw, countryCode)` → the significant national tail
  (`0912…`, `+251912…`, `251912…` all → `912…`) — the v1.6.2 rule that makes
  search/recents/keypad match numbers regardless of stored format.
- `contactByPhone(phone)` (store) resolves any raw number to a contact via the
  normalized index; used by Recents rows, the keypad sheet and the pickers.

## 5. Editing contacts (in-app, v1.6.1)

- The **in-app editor** is `src/overlays/ContactEditor.tsx`, backed by our own
  **`ContactEditModule.kt`** native module (row-id-scoped ContentProvider
  batches; always settles its promise; merges the OS row with the cached copy
  so no phone number can be lost).
- `react-native-contacts`' own `updateContact` is deliberately NOT used — it
  rewrites MIMETYPE rows and can drop numbers across multi-raw contacts.
- Creating contacts goes through the OS create form prefilled:
  `openNewContactForm({ phone, givenName })` — used by the Recents "+" tile,
  the unsaved-number detail page and the long-press action sheet.
- `deleteDeviceContact` / `findDuplicateContacts` (merge suggestions) live in
  `src/native/deviceContacts.ts`.

## 6. The parked Contacts tab

- Screen: `src/screens/ContactsScreen.tsx` (alphabetical list, infinite
  scroll, favorites strip, they-owe/you-owe filters, summary strip,
  swipe actions) — unchanged, just not routed.
- To bring it back, restore three one-liners:
  1. `App.tsx`: `{tab === "contacts" && <ContactsScreen />}` (+ import),
  2. `src/components/BottomNav.tsx`: add the `contacts` entry to `TABS`,
  3. `App.tsx` `headerTitle`: add the `tab === "contacts"` branch.
- The `Tab` union in `src/lib/ui-store.ts` still contains `"contacts"`.

## 7. Where contacts surface today (v1.6.3, tab parked)

- **Recents (home):** every call row resolves its contact via
  `contactByPhone`; favorites strip + "＋" add-contact tile; THEY-OWE /
  YOU-OWE views list **all** contacts with nonzero balances.
- **Unsaved numbers:** tapping a call card whose number is not in the address
  book opens `src/overlays/UnknownContact.tsx` — save-to-contacts hero,
  quick actions, and the call history for that number.
- **ContactDetail overlay** (`src/overlays/ContactDetail.tsx`): ledger,
  lists, notes, bank, reminders, info (phones/emails/socials/birthday/work/
  notes/call history), 12-tile quick-action grid.
- **Search overlay & KeypadSheet:** the shared cached search index with T9 +
  national-number matching.
- **ContactPicker** (`src/components/ContactPicker.tsx`): the unified chooser
  used by every editor (owner pick, number pick with add/edit rows).
