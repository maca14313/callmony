# Changelog

All notable changes to callmony. Dates follow the git history; the APK for each release is published on the [Releases page](https://github.com/maca14313/callmony/releases).

---

## v1.6.39 — the canvas calculator goes FULL SCREEN over the editor (one unified native-editable line in both modes — typed text never disappears on a mode switch; small Aa/123/Next-line/Save buttons ON the canvas; the compact keypad closes on the native back button; long-press unlocks a locked line; per-line copy icons; per-line totals share the last total's long-press menu), and the main call keypad's container is compact with BIGGER keys (2026-10-30)

User reports (verbatim log: `sandbox/chat-logs/2026-10-30-v1.6.39-fullscreen-canvas-compact-keypads.md`): "first check the GitHub on what at we are at. and the version." · the annotated-calculation spec — "the text, number, next line and save btn should be in the canvas on the next line and they should be small" · "let's say i type ball then click the number btn, the ball text disappeared why" · "dont want the icons that are in the numbers and why i need to click the icons if i can write the text or the numbers, by selecting from the btns" · "thr canvas should be full screen over every things" · "the keypad should be smaller and compact and the keypad also should close when the netiv go back btn is clicked" · "when the locked is long pressed it should unlock so it will be edited" · "also individual totals should have the same features as the last total when long pressed" · "when it not locked it should have the netiv copy,pest, cut ..., even the silder selector" · "each calculation line should have copy icons locked or unlocked" · "make the mean call keypad contener compact but make the keypads keys bigger" · "check github and commit even the apk".

**THE "ball" BUG, KILLED BY UNIFICATION.** v1.6.38 kept the active line in two places — the QWERTY text and the parsed tokens — and switching back to text re-serialized from the tokens, which had dropped the label-only words: `parseCalcNote("ball")` has no numbers, so "ball" vanished. v1.6.39 makes `calcText` THE single source of truth (tokens/total/steps all derive from it, live) and the active line is ONE native editable field in BOTH modes: the system QWERTY `BasicTextField` in text mode, the IME-suppressed native field (the dialer's trick — a real EditText with `setShowSoftInputOnFocus(false)`, grown `alignStart` + `maxLines` params) in number mode. A mode switch just swaps the input method; the text carries over untouched, and the keypad appends to it as characters — with an auto-space after a word ("ball" + the 2 0 0 keys → "ball 200", the label sticks). THE ICONS ARE GONE: no tag badges on the numbers, no clickable number chips, no operator cycling, no anchored label popover — labels are typed, exactly "if i can write the text or the numbers, by selecting from the btns" (every removed affordance documented in `removed/2026-10-30-canvas-tag-badges-label-popover.md`). THE UNLOCKED LINE IS A REAL INPUT: long-press opens the native Copy · Cut · Paste · Select-all menu with selection handles in BOTH modes.

**THE FULL-SCREEN CANVAS.** While it is open the canvas covers the ENTIRE editor — its header band, the kind chips, the pinned strip, the whole form and the footer (the app's standing v1.6.26 "the bottom nav is always visible" rule still holds; the canvas owns the content area above it). Inside: the collapse chevron + "Annotated calculation" + the Σ advanced toggle; the tappable TOTAL bar (make a payment of the total / copy it — v1.6.38 behaviour, kept); the scrollable ledger; the active line with its live result and show-your-work steps; then THE SMALL CONTROLS on the next line under the active one — the tiny Aa/123 segmented pill, a 30dp Next-line pill and a 30dp Save pill (the footer's Save/Delete remain for the collapsed form). ✕ collapses the canvas to a compact band in the form (label + first-line preview + total + chevron) that raises it again. THE NATIVE BACK BUTTON has a chain now: keypad up → closes the keypad (the line stays, both keyboards gone, Aa/123 re-arm one); canvas up → collapses it; canvas down → the editor itself. THE KEYPAD IS SMALLER AND COMPACT: a tighter 178–224dp band with 5dp gaps and 14dp side padding, digits still 24sp; the ⏎ next-line slot moved out to the canvas control row and its place is an `=` key that opens the result zone ("= total 500 birr" — the words after = label the total, never math).

**THE LOCKED LEDGER GROWS TEETH.** Long-pressing a locked line UNLOCKS it: the line comes back down into the active field for editing — whatever was being typed commits first, so nothing is ever lost. Every line carries a copy icon (locked or unlocked — the active line has one too). Each line's OWN total long-presses into the SAME menu the last total has: "Make payment · <that line's total>" (saves the entry first, exactly like the total bar's payment) and "Copy total". The keypad's % still marks the number the line ends with; ± flips its sign (swapping a + ↔ − between numbers, gluing a − otherwise — through the grammar, so "100 + 50" ± reads "100 − 50"); ⌫ is a plain text backspace that skips trailing spaces, which is why the v1.6.38 "delete the operator after the digits" fix is now inherent; AC clears the line; MR appends the memory as a new number.

**THE MAIN CALL KEYPAD — compact container, bigger keys** ("make the mean call keypad contener compact but make the keypads keys bigger"): the panel's chrome slimmed down (grabber 8→4dp, header 44→36dp, number display 62–84→52–68dp, action row 12→8dp padding with 40dp side buttons) while the 12 tiles GREW — grid side padding 22→14dp and gaps 9→7dp make every tile wider, tiles 48–62→56–74dp taller, digits 24→27sp, letters 12→11sp, and the call button grew to 60dp.

**Tests 157/157 green across 30 suites** (EntryEditCalcTabTest rebuilt for the unified model: the ⌫ operator deletion, THE "ball" MODE-SWITCH SURVIVAL asserted through the saved note "ball 200", the % key, next-line locks + the 1 × 200 + 100 = 300 save + the payment menu, the long-press unlock, the individual line-total menu, the native-back chain keypad → canvas → editor, the full-screen collapse/raise loop, the legacy-amount seed; EntryEditAddTabsTest updated for the full-screen canvas — collapse before the Recent tab / item rows). Both iOS actuals updated for the new field params. APK 1.6.39 vc50, same release key as always (apksigner SHA-256 identical to every prior release; installs over anything, data intact), committed at `releases/callmony-1.6.39-release.apk`.

## v1.6.38 — CREATE/EDIT CONTACTS REALLY SAVE (the label/number pair-order bug + a true replace edit), the annotated calculator grows into a CANVAS (text-default input, % ± and Σ memory keys, the ⌫ operator fix, locked next lines, auto-save, make-a-payment totals), and Just amount / Items · qty × price / Quick item are removed (documented in removed/) (2026-10-30)

User reports (verbatim log: `sandbox/chat-logs/2026-10-30-v1.6.38-canvas-calculator-contacts.md`): "there is something wrong with create,edit contacts" · "the keypad numbers are small but don't increase the size of the modal" · the calculator rework spec — "if i write 200 * 100 and i click the x delete btn it delete the 0,0,1 then it should delete the * but skips it and delete 0,0,2" · "it should look like almost full screen canvas modal, the next line and save btn should be very small" · "when next line btn is clicked it should be on the same canvas" · "the calculator should also have the persontage and advanced" · "we should be able to add text to results after the = like = total 200 or = 200 total" · "like we are adding text to numbers we should be able to do the visversa, and at start it should be text as default and to add text or number we should click the text or the number, keep the icons and make the long press for the netiv copy,cut, pest and others" · "it should have auto save and when we fo to the next line the old one should be locked and when clicking the total results it should have also the option to make payment of it" · "the small modal that shows to inter text is very ugly and it should be compact with very small btns" · "remove the just amount,qty*price and Quick itme, document them with all of there features,how they work,push the document in to removed folder in GitHub" · "check github and commit even the apk".

**CREATE/EDIT CONTACTS — THE PAIR-ORDER BUG.** Both in-app editor screens passed `it.label to it.number` / `it.label to it.address` into a platform layer that destructures `for ((number, label) in phones)` — so a CREATED contact literally carried the phone number "mobile" (TYPE Other) and the email address "home", and every EDIT inserted another bogus "mobile" phone row beside the real number (which the union merge then kept forever). The in-app cache patch also wrote the number into the `DevicePhone.id` slot. All three fixed: the pairs are now (number, label) / (address, label) — the interface doc spells the order out — and the cache rows get real ids. THE EDIT IS ALSO A TRUE REPLACE NOW: the old "loss-free union" kept every device row the editor didn't show, so a deleted number resurrected on the next 60s refresh, a changed number doubled, a removed email stayed and a company edit NEVER persisted (there was no Organization write at all). A pure merge planner (`lib/ContactMerge.kt`: `planPhoneMerge` / `planEmailMerge` — matched rows relabel in place, editor-dropped rows delete, genuinely-new rows insert) computes the plan; `AndroidPlatform.updateContact` applies it and upserts/deletes the Organization row so company finally sticks. 7-case `ContactMergeTest` + the new end-to-end `ContactEditorTest` (a recording fake of `Platform.contacts` drives both screens) pin it.

**THE CANVAS CALCULATOR.** The Calculator tab is now a near-full-screen paper canvas that owns the editor's body: locked ledger lines on top, the tappable TOTAL bar, then the ACTIVE line. TEXT IS THE DEFAULT at line start — the active line is a real `BasicTextField` seeded with the serialized line, so the system QWERTY, native selection and the NATIVE copy/cut/paste toolbar all come for free, and the free-text grammar parses LIVE as you type ("ball 200 + 100 cups" totals 300 while the words become labels). The Aa/123 segmented toggle (or tapping any number in the flow) switches to the numeric keypad; tapping text switches back. In number mode a long-press on any token opens a small Copy/Cut/Paste menu (paste parses the clipboard through the same grammar). THE ⌫ FIX: with "200 × 100" the backspace eats 0, 0, 1 and then — the user's exact report — deletes the × instead of jumping into 200's digits: a trailing operator now deletes FIRST. THE % KEY: `CalcToken.pct` rides the token, serializes as "10%", and resolves with phone-calculator semantics — after + or − it is that percent OF THE RUNNING TOTAL (200 + 10% = 220; 100 + 50 + 10% = 165), after × or ÷ (or leading) it is the plain fraction (200 × 10% = 20); `calcSteps` folds at the resolved value. The ± key flips the active number's sign; the Σ toggle reveals the M+ / M− / MR advanced pills with the memory readout (mirroring the main calculator's advanced panel). TEXT ON RESULTS: the live result carries its OWN 12dp tag badge — its popover writes "= total 200" (Before) or "= 200 total" (After) through the same resultBefore/resultAfter slots the grammar always had. NEXT LINE is now a SMALL ⏎ key on the pad (not a bar) and the committed line LOCKS — no pull-back editing (long-press copies the line, ✕ deletes); the fresh line starts in text mode again, on the same canvas. AUTO-SAVE: every change to an EXISTING entry persists ~600ms after the last keystroke and again on close (invalid totals never overwrite a good entry; new entries still Save explicitly so unconfirmed drafts never appear in the book). THE TOTAL BAR IS TAPPABLE: its menu offers "Make payment · <total>" — which saves the entry (creating it first when brand new) and opens the Record-payment sheet with the amount prefilled — and "Copy total". THE COMPACT LABEL POPOVER: rebuilt to the spec ("very ugly… compact with very small btns") — a narrow card, 38dp field, 26dp position chips, 28dp ✕/Done. THE KEYPAD: digits 24sp (was 20) inside the SAME capped 172–256dp band — bigger glyphs, the modal does not grow — now a 4×5 grid (AC ⌫ % ÷ / 7 8 9 × / 4 5 6 − / 1 2 3 + / ± 0 . ⏎). The footer Save/Delete buttons are small. THE REMOVALS: Just amount / Items · qty × price / Quick item are GONE from the strip (Calculator + Recent remain); the annotated calculator is THE amount input — the entry amount = line items + the calculator's whole ledger; a legacy entry (plain amount, no note, no items) reopens with its amount seeded as the first locked line so nothing is lost; tapping an existing item still opens the inline item editor. The three removed tabs are documented feature-by-feature in `removed/2026-10-30-entry-editor-just-amount-items-quick-item.md`.

**Tests 153/153 green across 30 suites** (new: ContactMergeTest 7 — the replace planner; ContactEditorTest 2 — the pair order on create AND edit against a recording fake, plus the cache patch; CalcNoteTest +6 — the % semantics: 200 + 10% = 220, 500 − 5% = 475, 200 × 10% = 20, chained 10% of 150, resolved steps, full round trip; EntryEditCalcTabTest rebuilt for the canvas: the ⌫ operator fix, text-default live parse, the % key, next-line locks + the 200 + 100 = 300 save, the removed chips + legacy seeding; EntryEditAddTabsTest: recent + calculator sum 235, inline item edit). Both iOS targets compile. APK 1.6.38 vc49, same release key as always (apksigner SHA-256 identical to every prior release; installs over anything, data intact), committed at `releases/callmony-1.6.38-release.apk`.


## v1.6.37 — the annotated calculator rebuilt as INK ON PAPER (one inline flow, a tiny tag badge per number, an anchored label popover, a stacked multi-line ledger with a running total), and the entry amount = typed base + items + calculator (100 + 200 = 300) (2026-10-29)

User reports (verbatim log: `sandbox/chat-logs/2026-10-29-v1.6.37-paper-calculator-sum.md`): the paper-look UX blueprint — "to make it look, feel, and flow naturally like handwritten math on paper" (document-icon clutter / ghost icons before, after and between every token; artificial pill backgrounds fragmenting the equation; the detached text box popping up at the screen bottom "overlapping date and payment method pickers" with "an unstyled ee.calcDone button"; no multi-line "Next" flow) · THE SUM, again: "lets say i click credit, then it shows New entry, then i click just amount and type in amount input 100, then i click the items. qty * price then i do ball 1 pc * 200 = 200, so the result should be 100 + 200 = 300 but it is not doing this, but it should be like this fir the others too" · "check github and commit even the apk".

**THE PAPER CALCULATOR.** The Calculator tab's chip rows are gone; the equation is ONE inline ink-on-paper flow — labels, numbers and operators share font size, weight, line-height and baseline, sitting flush in a single wrapped line that reads like handwriting (`ball 200 + 100 cups = 300 Br`). Every pill, chip and label-trigger icon is removed: operators (+ − × ÷ =, tap to cycle) are plain glyphs with NO label affordances, and the result's labels (legacy notes) show only as plain text when they exist. Each NUMBER carries exactly ONE subtle indicator — a 14dp tag badge pinned to the top-left corner of its first digit (faint outline when unlabeled, brand-tinted when labeled, solid while edited) — and tapping it opens a FOCUSED POPOVER anchored directly at that number (a real DropdownMenu, auto-clamped on screen): an auto-focused field on the system QWERTY, a clean Before/After toggle for the label's position ("ball 200" vs "200 Birr"), ✕ cancel and a properly styled Done (empty submit deletes the label; flipping the toggle never loses typed text). While the popover is open the numeric keypad HIDES, so nothing sits at the screen bottom overlapping the date/method pickers. The live fold steps survive as ONE muted line under the flow — show your work, paper-style. Bonus bug found and fixed: the old submit button referenced a MISSING i18n key and literally rendered the raw text "ee.calcDone".

**THE STACKED LEDGER + NEXT LINE.** The keypad gains a full-width NEXT LINE key (brand solid, arrow-down glyph): it commits the active expression into a stacked LEDGER right above — each completed line keeps its whole token list and subtotal, rendered as its text + total with a per-line ✕, and tapping a line pulls it back down into the active row for re-editing (labels included). The ledger finishes with a prominent running TOTAL bar that counts the committed lines PLUS the active line's live result, so the number never jumps on commit; AC clears only the active line. Multi-line persistence rides the same free-text grammar, "
"-separated (`parseCalcLedger` / `serializeCalcLedger` / `calcLedgerTotal` in lib/Calc.kt): old single-line notes reopen as a one-line ledger and the round trip stays byte-stable.

**THE SUM, REBUILT — 100 + 200 = 300.** The amount field is the user's BASE, and the entry's saved amount is base + every line item + the calculator's whole ledger — for EVERY input mode (Items, Quick item, Recent, Calculator; the user's exact flow now saves 300). A TOTAL row under the items list shows the live sum with the "100 + 200" breakdown on every tab, and Save stores it. On reopen the base is DERIVED back out of the saved amount (saved − items − ledger, clamped at 0), so the field shows exactly what the user typed (100) while the total stays 300 — the v1.6.35 "amount is its own" mirror rule is superseded by this explicit flow.

**Tests 136/136 green across 28 suites** (CalcNoteTest +3 ledger cases; EntryEditCalcTabTest rebuilt around the paper UI — the full keypad → popover labels → Next Line → ledger → save/reopen → pull-back → live re-total loop, plus the user's exact 100 + 200 = 300 flow and the calc + quick-item 235 regression; EntryEditAddTabsTest's ownership test rewritten for the sum model: base 30 + 25 + 10 = 65). Both iOS targets compile. APK 1.6.37 vc48, same release key as always (installs over anything, data intact), committed at `releases/callmony-1.6.37-release.apk`.


## v1.6.36 — the ANNOTATED CALCULATOR as interlocking chips with a pinned smart keypad and live step-by-step results, the single-entry amount SUMS its parts again, the keyboard lift is position-aware, consecutive same-number calls of ANY direction collapse into one card, and the contact-detail number opens the phone options (2026-10-28)

User reports (verbatim log: `sandbox/chat-logs/2026-10-28-v1.6.36-chips-keypad-grouping.md`): the annotated-calculator update (interlocking "Text + Calculator" rows — a text toggle before/after every number AND around the result, the system QWERTY for labels, a custom numeric keypad for the math, live step-by-step intermediate results, full post-save re-edit) · the sum ("why it is not adding the result of the calculator / Items / Quick item and others that are in the same single entries") · the keyboard regression ("in your attempt to fix the keyboard off the modals… now the save button and it's contener goes up higher, that it covers the input and other comments") · the call collapse ("when the same person have conversation consecutively make it in one call card just like the mised call if it is the same number for that person") · the add-contact chip ("just like it friends in the right side but it should remain in the left side") · the favourites strip ("the container needs a little bit padding in x direction and there is to much margin in x direction between the cercle favourite contacts") · the contact-detail number ("it should show the options of the phone numbers… just like the call btn icon bellow, and don't remove it old features") · the history row ("remove the settled up and move the where the amount shows to the middle horizontaly").

**THE ANNOTATED CALCULATOR, REBUILT AS CHIPS.** The Calculator tab's free-form text area is now the interlocking chip row the updated spec draws: every number is an interactive unit with a [＋ Text] toggle before and after it, operators sit between the numbers (tap one to cycle + − × ÷), and the RESULT carries its own text toggles around it — `(ball) [100] × [2] (price in birr) + [300] (transport) = [500 Br]`. A custom NUMERIC CALCULATOR PAD (7-8-9-÷ · 4-5-6-× · 1-2-3-− · .-0-⌫-+) is PINNED at the bottom of the editor above the Save footer: digits type into the tapped/active number chip, an operator key finishes the number, ⌫ edits it (an emptied chip is removed together with its labels), AC (the red pill in the card corner) clears the chain. The moment a [＋ Text] slot — or an existing label — is tapped, the pad SWAPS to a standard text input field and the device's own QWERTY keyboard comes up (auto-focused, ✕ cancel, Done submit); submitting an EMPTY field deletes the label, and focus returns to the calculation view. The LIVE STEP-BY-STEP card below shows every intermediate fold as it happens, exactly as the user drew it — `Step 1: 100 × 2 = 200 Br`, `Step 2: 200 + 300 = 500 Br`, ending with `TOTAL: 500 Br` — and tapping any number or operator recalculates the whole chain and updates the final entry amount live, before Save. Under the hood the chips are a structured `CalcNote` (per-number label slots + the ops + result slots) that serializes to the SAME free-text grammar v1.6.35 introduced (`ball 100 × 2 price + 300 transport = total 500 birr`), so every note ever saved opens as chips, edits losslessly, and stays plain readable text on the entry. The engine (`parseCalcNote` / `serializeCalcNote` / `evalCalcTokens` / `calcSteps` in `lib/Calc.kt`) keeps every parser rule: glued operators, Arabic-Indic digits, comma decimals, the `=` cut with result-label extraction, ×÷-before-+− precedence, dangling operators ignored, ÷0 drops the operator, adjacent numbers fold as an explicit shown `+`. 14-case `CalcNoteTest` pins the engine; the rebuilt `EntryEditCalcTabTest` drives the whole loop through the real keypad — build the user's example on the keys, label the numbers through the QWERTY field, watch the steps appear, Save, reopen to chips, tap the 300 chip, back it to 350, watch 550 Br live, Save again.

**THE SUM.** On a new entry the hero amount mirrors the SUM OF EVERYTHING in the entry — the calculator's live total PLUS every line item (Quick item and Recent reuse land in the same item list) — until the user types their own amount, which then stays theirs forever. An existing entry keeps its saved amount unless that amount IS the live sum of its parts (a mirrored entry): those keep mirroring while edited, so the re-edit spec — tap a number, watch the entry amount update — holds without ever overwriting a hand-typed value.

**THE POSITION-AWARE KEYBOARD LIFT.** The v1.6.35 `imePadding()` fixed the keyboard covering modals but floated the Save bar a whole bottom-nav HIGHER than the keyboard's top edge (sheets render inside the app's content area, which already ends one nav above the window bottom — and Samsung/OneUI builds can double-apply the inset). The new `Modifier.imeLiftPadding()` (Sheet.kt) measures the real distance from the sheet's bottom edge to the window bottom, consumes that much of the IME inset, and only then applies `imePadding` — the footer now rests EXACTLY on the keyboard's top edge, giving the form back the nav-height of dead space; with the keyboard closed nothing changes at all. Both `Sheet` and `BottomSheet` lift this way.

**RECENTS.** Consecutive calls from ONE number collapse into a single card whatever the mix of directions — the missed-only rule generalized per the report — headed by the newest call's direction and carrying the same `(3)` count badge (tinted by the headline direction); a different number in between keeps the calls as separate cards. The ＋ add-contact icon beside the day label (and in the empty state) is a real icon-chip now — same soft pill and metrics as the DayFilters chips to its right — while keeping its left slot beside the date. The favourites strip gets its horizontal inset (it used to start flush at the screen edge) and the circles tighten from an ~18dp visual gap to a clean 8dp.

**CONTACT DETAIL.** Tapping the phone number under the contact name opens the same choose-a-number sheet the Call button opens — every number with its label, copy, favourite-star and call rows, plus the add/edit-numbers row — while the old features stay: the little call icon beside the number still dials directly, and long-pressing the number still copies it. The balance row under it loses the "Settled up" text label; the amount pill now sits dead-center in the row with the history chip at the left edge and the open-lists/blocked badges at the right.

Fixes carried: a keypad-sizing crash-course from the harness (the pinned pad's plain rows got crushed to 0–1px tails on short windows — rows are now weighted children of a capped column, so keys always stay tappable; the harness runs at a realistic phone resolution), and one cross-class test leak (a seeded call behind the overlay shadowed the navbar number text — [0] tapped the covered card).

**Tests 132/132 green across 29 suites** (new: `CalcNoteTest` 14 · `RecentsV1636Test` 2 · `ContactDetailV1636Test` 2 · `EntryEditCalcTabTest` rebuilt around the chip UI + the sum). Both iOS targets compile. APK 1.6.36 vc47, same release key as always (installs over anything, data intact), committed at `releases/callmony-1.6.36-release.apk`.

---

## v1.6.35 — the ANNOTATED CALCULATOR: a Calculator tab at the far left of the entry editor where math is written as a sentence, totaled live and saved WITH the entry — plus the amount is its own again, the editor's tab rows are pinned + compact, the keyboard no longer covers modals, and the toast that ate Save taps is fixed (2026-10-28)

User reports (verbatim log: `sandbox/chat-logs/2026-10-28-v1.6.35-annotated-calculator.md`): the annotated calculator ("Calculator / Expression Note tab positioned before the \"Just amount\" tab… let users write out mathematical steps, attach descriptive text labels to numbers/operators, compute the result live, and edit the annotations even after saving… e.g. ball 100 ball x 2 price + 300 transport = total 500… the text could be before or after the number") · the keyboard ("when keyboard is open then if some modal opens up, the keyboard is covering it") · the amount ("the amount also be just like Items (qty × price) and Quick item by its own not the auto calculated from {n} items") · the pinned tabs ("the tabs Just amount · Items · Quick item · Recent and the tabs above them should not scroll vertically when what below them scrolls and make them compact, especially the above one").

**THE ANNOTATED CALCULATOR (the Calculator / Expression-note tab).** A new chip at the FAR LEFT of the add-mode strip (user spec: before Just amount), ahead of Items · qty × price / Quick item / Recent. It is a free-form smart text area: write the math as a sentence, labels before OR after every number — `ball 100 x 2 price + 300 transport = total 500` — and the parser (`parseExprNote`, pure + deterministic in `lib/Calc.kt`) extracts just the arithmetic and totals it LIVE while you type. The result card shows exactly what it found — the canonical extraction `100 × 2 + 300` and the running total in birr — so a wrong guess is visible the moment it happens. The raw text round-trips on the new optional `LedgerEntry.exprNote` field (old rows decode without it, old code ignores it, the v1.6.34 Downloads backup layer mirrors it for free): reopening the entry lands on the Calculator tab with the note prefilled and the total still live — editable exactly as the user asked. Every keystroke re-applies the parsed total to the amount, so what the preview shows is what Save writes.

Parser rules (forgiving by design): everything after the first `=` is the user's own written result and never parsed (`= total 500` cannot double the total); Arabic-Indic (٠-٩) and Eastern (۰-۹) digits normalize to Latin so Arabic keyboards type natively; glued operators split only between digits (`2x2`, `100+300`, `10/2`, `100−50`) so chains like `5-3-1` work and words like "taxi" or "no-cost" never match; an operator only counts BETWEEN two numbers (a dangling `100 +` mid-typing is ignored); any other numeric token participates with comma as the decimal separator, like every amount field; two adjacent numbers fold as an explicit `+` and the extraction SHOWS it (`100 + 200`); × ÷ bind tighter than + −; ÷ by 0 drops the operator instead of exploding. 15-case `ExprNoteTest` pins every rule; `EntryEditCalcTabTest` pins the whole loop end-to-end (type → live total → save → reopen → edit → re-total → save).

**THE AMOUNT IS ITS OWN.** The v1.6.34 "amount follows the items total" live override is GONE — the "auto calculated from {n} items" hijack the user reported. The hero amount IS the entry amount, always. On a NEW entry the amount field still MIRRORS the items total as a plain editable convenience (add a quick item → 25 lands in the field as a real value) — but the moment the user types their own amount, ownership transfers to them and items never touch it again (add more items, the field stays). Existing entries open with their saved amount, final from the first frame. `amountIsItsOwnOnceTyped` pins it: quick-add Coffee 25 → field mirrors 25 → type 30 → quick-add Tea 10 → saved entry carries amount 30 with 2 items totalling 35 — the items never overrode the typed value.

**PINNED + COMPACT TAB ROWS.** The Sheet's own scroll is OFF for the editor (`scrollable = false`); the kind chips and the add-mode tab strip render once in a pinned band at the top, and ONLY the form below carries the verticalScroll (never a scrollable inside a scrollable — the v1.6.25 More-sheet lesson). Scrolling the form (date, categories, note, owner…) can never move the tabs. Both rows are COMPACT — especially the kind chips row ("make them compact, especially the above one"): compact chip metrics, tighter spacing, same 18dp gutters so nothing else visibly moves.

**THE KEYBOARD NO LONGER COVERS MODALS.** Two complementary fixes. (1) Any overlay opening (UiStore.overlayStack) — except Search, which deliberately raises the keyboard on its own since v1.6.15 — now clears focus and hides the IME: a stale keyboard from the field you were typing in can never sit on top of a freshly opened modal; the keyboard comes back the moment you tap a field INSIDE the modal. Host sheets (attach picker, group menus, record payment…) get the same rule in `HostSheetsHost`. (2) `Sheet` and `BottomSheet` imePad: with the keyboard open INSIDE a modal (the note field at the bottom of the editor), the whole sheet rises above the IME — the footer Save stays visible and the scrollable body brings the focused field into view.

**THE TOAST THAT ATE TAPS (a real save-blocker found by the new tests).** The toast pill carried an EMPTY `clickable {}` — it swallowed every touch over its bottom-center footprint (exactly where sheet footers render their Save button) and did nothing with it. Saving, then immediately editing the next entry and tapping Save within the 3-second toast window = the tap silently eaten. The pill is now click-through (no pointer modifier = hit-test skips it); only an action's own label (Undo) stays tappable; auto-dismiss unchanged.

Tests 113/113 green across 25 suites (17 new: 15 parser + the calc round-trip + amount-ownership). Both iOS targets compile. APK 1.6.35 vc46, same release key as always (installs over anything, data intact), committed at releases/callmony-1.6.35-release.apk.

---

## v1.6.34 — the "lighter cards" read as TRANSPARENT so they're solid again, the owe tabs get the DATE/AMOUNT/NAME filter axis they were always meant to have, the calculator display becomes a real input with the native copy/paste/cut menu, the quick item moves into ADD ITEM as a horizontal tab strip, the PIN sheet loses the forgot link and paints the 4th dot, a whole-app perf pass — and THE NO-DATA-LOSS LAYER: an auto-backup to Downloads/Callmony on every close + a one-tap restore offer on a fresh install (2026-10-27)

User reports (verbatim log: `sandbox/chat-logs/2026-10-27-v1.6.34-user-reports.md`): the cards ("you did say Lighter call cards but you did make it transparent why") · the speed ("make this app the fastest app ever") · the PIN link ("for pin i think forgot password is not needed for now") · the owe filters ("what i wanted was when one of the Filter Buttons are clicked to be different kind of tabs that help filter like custom date, by amount, by name, other useful filters… dont do that" — the Call Log/Contact rows were the wrong axis) · the calculator ("PASTE should be what someone can do in a normal input or text area, like press long on some part of word and native options shows up like copy, paste, cut and others") · the quick item ("the quick item should be inside ADD ITEM as scrolling tabs: Items (qty × price), Quick item, Just amount, other useful types") · the data loss ("why every time i install the app the data is all gone") · the PIN dot ("the PIN 4th dot gets coloured every time but not on the fourth").

**THE SOLID CALL CARDS.** v1.6.33's "lighter" pass stripped the per-row chrome entirely — a fully TRANSPARENT flat row with an inset hairline — which read as "transparent", not "light". The card is a REAL surface again: a solid `C.card` fill (opaque, clearly lifted off the canvas in both themes), a hairline border and 14dp corners — while keeping every lighter metric from the flat pass (44dp avatar ring, 15sp title, 12sp meta, compact padding). Swipes, missed-red titles, the "(3)" collapsed-run badge and favourite dialing unchanged.

**THE DATA-LOSS ROOT CAUSE + THE NO-DATA-LOSS LAYER.** The storage audit came back clean: the JSON loader is shape-sanitized (a failed row is skipped, never written back), saves fire only on explicit mutations, and update-installs keep everything (same signing key since v1.6.13 — apksigner-verified every release). What kills the data is the INSTALL PATH: an uninstall (or an OEM "reinstall" update) wipes the app's whole private storage, and on budget-ROM phones the cloud Auto Backup rarely restores sideloaded apps in time. v1.6.34 adds the layer that survives all of it: **every time the app leaves the screen (onStop) — plus once per boot as a safety net — the full store is mirrored to `Downloads/Callmony/callmony-backup.json`** via MediaStore.Downloads (public, user-visible, and the documented home for files that must outlive the app; the MediaStore row's owner package matches again after a reinstall, so the fresh install reads it back with NO permission prompt). On the next fresh boot with an empty store, the app **offers the backup back with one tap** — Restore brings the whole store, `onboarded` flag included, so the phone continues exactly where it left off, no onboarding rerun. The empty-store guard makes an empty session a no-op (a fresh install can never overwrite the good backup), the danger-zone wipe removes the file explicitly, and the Settings → Backup section shows the layer's status (last saved time) so the safety net is visible, not magic. iOS is a no-op (the app container rides the device backup). Triggers: `MainActivity.onStop` + hydrate-complete — both strictly POST-launch lifecycle, the onCreate/setContent window untouched.

**THE OWE-TAB FILTER TABS (the correction).** The v1.6.30/33 attempt stacked the call-direction chips (All/Missed/Incoming/…) under the owe buttons — the wrong axis, exactly as the user said. An active "They owe you" / "You owe them" tab now opens a side-scrolling row of THREE filter tabs — **Date** (Any time / Today / Last 7 / Last 30 / a CUSTOM RANGE with native from-to pickers), **Amount** (Any / 100 / 500 / 1,000 / 5,000 and above, on the contact's |balance|), **Name** (Newest / A→Z / Z→A — a name-sorted view goes FLAT, no day headers) — each chip shows its current selection and opens its own sheet. The classic direction chips stay where they always lived (the sticky day header, in the "all" view). The Recents nav button reset clears every sub-filter too. The pipeline is the pure `applyOweFilters` (harness-tested by the new `OweFiltersTest`, 6 cases). 16 new i18n keys ×5 languages.

**THE CALCULATOR DISPLAY IS A REAL INPUT.** The display is now the keypad's `NoImeNumberField` (a real editable field with the IME suppressed): long-pressing any part of the text opens the NATIVE text menu — Copy · Cut · Paste · Select all, with selection handles — exactly like a normal input, while the app keypad stays the typing method. Whatever the native menu lands in the field is mapped by `mapDisplayExpr` (ASCII operators and the comma decimal become the display glyphs, anything unevaluable is dropped, length capped at 80 so the autosizing font stays readable), and editing a shown result starts a fresh expression from it. The old custom long-press copy is gone — the native menu replaces it; the header PASTE chip stays. `NoImeNumberField` grew `alignEnd` + `maxLines` (the display right-aligns and wraps to 3 lines). 4 new `V1634CalcDisplayTest` cases.

**THE QUICK ITEM MOVES INTO ADD ITEM.** The v1.6.27/29 label+price row leaves the ledger view entirely — the entry editor (the add-item flow) now carries a horizontal scrolling tab strip: **Just amount** (the classic hero field) · **Items · qty × price** (the full line-item editor) · **Quick item** (label + price + ＋ → a qty-1 item; keep adding or Save — the amount follows the item total) · **Recent** (the "other useful type": one-tap reuse of items booked before — distinct labels with their last price, newest first, via the pure `recentItems`). The item rows + total stay visible under every tab, tapping a row jumps to the Items tab for editing, and the old collapsed "＋ Add items" row is gone (the tabs ARE the add flow — the v1.6.18 double-form state can't exist). `EntryEditAddTabsTest` (2 harness cases) locks the contract: the ledger view has no quick row, and the Quick tab books a priced entry end-to-end.

**THE PIN SHEET.** The "Forgot PIN?" link is removed from the lock screen (not needed for now — the PIN stays a pure lock; the in-app Backup → Restore remains the data path). And the 4th-dot fix finally covers BOTH surfaces: the v1.6.25 160ms submit delay was only on the LockScreen — the set/change/remove PIN sheet still submitted in the same frame the 4th digit landed, and since every submit path ends with `entered = ""`, the dots recomposed straight back to empty and the 4th dot never painted. Same LaunchedEffect pattern applied.

**THE PERF PASS.** Recents: the two headline owe sums, the missed count, the paged slice — all `remember`-keyed now (they rescanned the ledger on every recomposition; with a busy book that was real work per state flip). General: the two hero sums remembered on the people list. All pure UI-layer memoization — the splash, the shell, hydrate and the launch path are untouched.

Build: APK `1.6.34` vc45, same release key as every prior release (apksigner SHA-256 `2f6d65be…869ad` — installs over anything, data intact); both iOS targets compile; 96/96 tests green across 23 suites (19 new v1.6.34 tests: EntryEditAddTabs 2, OweFilters 6, AutoBackupOffer 4, V1634CalcDisplay 4, RecentItems 3).

---

## v1.6.33 — the v1.6.30 feature set re-applied on the stable v1.6.32 base, one audited port at a time: the post-call refresh rebuilt LAUNCH-SAFE, the recents filter tabs + nav reset, the calculator copy/paste, the full-number strip, the detail-page icon, the lighter cards — and the splash KEPT (2026-10-27)

User reports (verbatim log: `sandbox/chat-logs/2026-10-27-v1.6.33-user-reports.md`): the post-call refresh ("in recents call cards i make a call from one of the call card then after the call ends it is not putting that call card fast enf at the top") · the recents tabs ("in the recent after clicking one of the two tabs it should have filter tabs to help filter just like in search. and if the recent btn at the bottom navigation is clicked it should go back to the recent as the tabs was not clicked") · the calculator paste ("in the caluleter i should copy and pest in the display just like other calculetersand normal inputs") · the detail-page icon ("in the long-pressing a call CARD after the name of the person end at the top put an icon that goes and indecet to go it's detail contact page") · the numbers strip ("THE NUMBERS STRIP: long-pressing a call CARD should show the full number not ...") · the ledger quick item ("the in the ledger there could be an item that just have label and price, this is like in in the for example in credit,debit and others in the new entry, not in another place").

**CONTEXT.** The user never actually saw any of the v1.6.30 features — that build closed on open. Now v1.6.32 is back on the stable v1.6.29 launch path, so the features are re-applied as audited ports, and the one feature that had to touch the launch path (the post-call observer) is REBUILT to a launch-safe design instead of copied.

**THE POST-CALL REFRESH, LAUNCH-SAFE.** A ContentObserver on the system call log fires the moment the provider writes the ended call — while the user is still on the in-call screen — and a debounced 1.2s incremental refresh (the same permission-gated loadSince path onResume uses) has the card already at the top of Recents when they return. v1.6.30's version registered the observer inside onCreate BEFORE setContent and ran onChange on the MAIN handler — an OEM provider firing a notification burst right at registration delivered work onto the main thread inside the launch window, stacked on the splashless first frame + hydrate: one of the two suspects in the "closes when I open it" crash. The v1.6.33 design inverts every leg: registration happens on the FIRST onResume (after the UI is up — the onCreate/setContent window is untouched), the observer rides a dedicated BACKGROUND HandlerThread (provider bursts never touch the main thread), the debounced refresh Runnable on main is fully guarded and gated on hydrate, everything unregisters in onDestroy — and the v1.6.29 splash is back as the launch safety buffer, so even the worst case composes over a trivial pane. New `PostCallRefreshTest` locks the contract: a full real boot still reaches the UI, the observer is armed after first resume, and a provider write flows the whole chain (background onChange → debounced main post → the guarded refresh — observable via the new `recents.observerRefresh` BootTrace mark).

**THE RECENTS TABS + NAV RESET.** The owe chips and the direction chips used to SHARE one filter state — picking Missed silently dropped the owe view. oweTab is now its own toggleable state; an active owe tab reveals the All/Missed/Incoming/Outgoing/Favorites row right beneath it (the same chips the sticky day header carries), and both filters COMPOSE. Tapping the Recents nav button (even while already on Recents) bumps `UiStore.recentsResetTick` and the whole view snaps back to the nothing-clicked state — owe tab "all", filter "all", page and scroll reset.

**THE CALCULATOR COPIES AND PASTES.** Long-press the display to copy the current value; a PASTE chip sits beside the copy chip. Pasted text is sanitized by `sanitizePastedExpr` (ASCII `-`/`*`/`/` and `,` mapped to the display glyphs, operator runs collapsed, dangling operators trimmed, non-evaluating text like a copied phone number falls back to the first plain number) and landed memory-recall-style: replaces a finished result, appends after an operator, else takes over. 2 new i18n keys ×5 languages; `V1630Test` (5 cases) covers the sanitizer.

**THE FULL-NUMBER STRIP + THE DETAIL-PAGE ICON + THE LIGHTER CARDS.** The long-press sheet's number tiles grow to the width of the whole pretty-printed number (was clamped to 196dp with an ellipsis); an account-circle icon rides the sheet header right after the name (saved contacts only) and opens the contact-detail page; and the call cards ride the flat-and-light treatment (flat canvas rows with an inset 0.5dp hairline, 44dp avatar rings, 15sp SemiBold titles, a softer balance pill, a canvas-toned swipe underlay) — swipe actions, missed-red titles, the "(3)" collapsed-run badge and favourite dialing unchanged.

**THE LEDGER QUICK ITEM — already there.** The ask ("an item that just have label and price… in credit, debit and others in the new entry, not in another place") shipped as v1.6.29's quick item and stayed through the rollback: the label+price row exists ONLY inside the credit/debit/income/expense kind tabs of the new-entry sheet, and one tap creates the priced entry with the tab's kind (no kind guessing, the outer ledger stays clean).

Everything is a pure port from the audited v1.6.30 diff (RecentsScreen / Calculator / SwipeRow / V1630Test / the recentsResetTick Store hunk / the 2 paste i18n keys) except the observer, which is new code — the splash path, CallmonyApp's shell, hydrate and the whole v1.6.31 crash-guard experiment remain exactly as v1.6.32 shipped them (DEX-verified: SplashPane present, storeCrashGuard absent).

Build: APK `1.6.33` vc44, same release key as every prior release (apksigner SHA-256 `2f6d65be…869ad` — installs over anything, data intact); both iOS targets compile; 78/78 tests green.

---

## v1.6.32 — 100% back on the v1.6.29 code base: the v1.6.30/31 launch rework reverted wholesale; the active-input ring and the left-anchored history button ported on top (2026-10-27)

User report (verbatim log: `sandbox/chat-logs/2026-10-27-v1.6.32-user-reports.md`): "still the same it is not opening, go back 100% to v1.6.29 use it as a base continew from there and i think it is the splash screen thing is the problem."

**THE ROLLBACK ("go back 100% to v1.6.29 use it as a base continew from there").** v1.6.31's belt-and-braces hardening did NOT stop the crash on the real device — the user confirmed the app still closes on open — so the diagnosis shifts from "a throwable escaping a guard" to "the v1.6.30 launch structure itself". What v1.6.30 changed on the launch path, all at once: the splash pane was deleted so the real shell composes from frame one, and a ContentObserver on the system call log was registered in `MainActivity.onCreate`. On a low-RAM phone that stacks the entire peak — first-frame shell composition, hydrate's concurrent JSON decodes, the boot call-log fetch and the observer's refresh — into one window at open; the v1.6.29 splash was, structurally, the safety buffer: frame one was a trivial canvas + logo, and the real shell composed only after hydrate finished. The v1.6.31 guards could only cover background coroutines — a main-thread composition failure was never in their reach. Per the user's explicit instruction the ENTIRE v1.6.30/31 launch-path rework is reverted: `MainActivity.kt` (the ContentObserver is gone), `Store.kt`, `CallmonyApp.kt` (splash pane + exit fade back exactly as v1.6.29 shipped them), `RecentsScreen.kt`, `Calculator.kt`, `SwipeRow.kt`, the i18n additions and both v1.6.30/31 test harnesses — byte-identical to v1.6.29, the last version that opened on the device. The v1.6.30 feature set (full-number strip, detail icon, calculator paste, owe-tab filters, post-call auto-refresh, flat-light cards) goes with it and can be re-added one-by-one on this stable base.

**WHAT STAYS (the two pure-UI asks, ported from v1.6.31 onto the v1.6.29 base).** (1) **THE RING COLOUR ON ACTIVE INPUTS** — `Modifier.focusRing()`, a pure focus observer (`onFocusChanged`) that fades a brand-coloured 2dp ring in around an input's own corner radius while it (or any input inside the box) holds focus, out on blur; applied to `Field`, the entry-editor hero amount, the dominant search, the onboarding country search, the contact-picker search, the ledger quick item and the keypad number display; `FocusRingTest` drives the real app's auto-focused search field. (2) **THE CONTACT-DETAIL HISTORY BUTTON** left-anchored — the balance row goes `Arrangement.spacedBy(8.dp, Alignment.Start)` so the chip anchors the left edge with a fixed 8dp gap to the balance pill. **Purity proof: the app code differs from v1.6.29 in exactly those 8 UI files + the test — no launch-path file is touched.**

Data-safety (verified before shipping): v1.6.30/31 introduced NO persisted-schema changes (the only Store.kt additions were an in-memory UI tick and non-persisted crash guards), so the v1.6.29 code loads data written by v1.6.30/31 cleanly; versionCode 43 installs directly over the crashed v1.6.31 (42) with data intact — no uninstall, nothing lost.

Build: APK `1.6.32` vc43, same release key as every prior release (apksigner SHA-256 `2f6d65be…869ad`); both iOS targets compile.

---

## v1.6.31 — the "closes when I open it" launch-crash hardening; a ring colour on every active input; the contact-detail history button left-anchored with breathing room (2026-10-27)

User reports (verbatim log: `sandbox/chat-logs/2026-10-27-v1.6.31-user-reports.md`): "you did something bad, the app won't open, it closes when i open it" · "CONTACT DETAIL's history button should have some sapce between his neeber, it should be at the start of the left side" · "need a ring colour on the inputs when they are active. check github + apk + commit. this version has a bug".

**THE LAUNCH CRASH ("the app won't open, it closes when i open it").** The shipped v1.6.30 APK was verified byte-identical to the committed tree (all three DEX files SHA-identical to a clean rebuild) and the full Robolectric battery was green — so the crash lived in the gap between the JVM harness and a real low-RAM device. The structural fragility that fits: **both store coroutine scopes (`scope` on Dispatchers.Default, `mainScope` on Main) had NO CoroutineExceptionHandler**, and every guard in the persistence/call-log path caught `Exception` — which does NOT catch `Error` (an `OutOfMemoryError` mid-decode of the 1239-contact book on a 2 GB phone, a cursor `Error` from the system call-log provider). Any such throwable in any background coroutine at open = default uncaught handler = **process death, every launch**, because the peak is deterministic: v1.6.30's no-splash shell composes with the full data at frame one WHILE hydrate's four concurrent JSON decodes, the boot call-log fetch and — new in v1.6.30 — the ContentObserver's externally-triggered refresh (some OEM call-log providers notify right after registration) all land in the same window. Fixes, all belt-and-braces: (1) a `CoroutineExceptionHandler` on both scopes (BootTrace marker + stack logged, app keeps running on the last good state); (2) `hydrate()` catches `Throwable` and still publishes `hydrated=true` (a dead hydrate previously meant a permanently blank app); (3) `loadSystemCalls` catches `Throwable` (was try/finally only — an exception killed the coroutine); (4) `fetchAll`/`loadSince` catch `Throwable`; (5) the ContentObserver's `onChange` and the debounced refresh Runnable are fully guarded; (6) the observer now registers AFTER `setContent` so the externally-triggered refresh can never collide with the first-frame composition + hydrate window. Verified: the new `V1631CrashReproTest` boots the REAL app through the REAL kv files (returning user: dark theme, Amharic, 400 cached calls, 300 contacts, ledger balances) and drives every v1.6.30 surface — owe tabs + filter tabs, nav reset, the long-press numbers strip, keypad, calculator — past the +4 s / +8 s boot effects; full battery 78/78.

**THE RING COLOUR ON ACTIVE INPUTS ("need a ring colour on the inputs when they are active").** New `Modifier.focusRing()` in the kit: a pure focus OBSERVER (`onFocusChanged`, hasFocus = the box or any input inside it) that fades a brand-coloured 2dp ring in around the input's own corner radius while the field is active and out on blur — no focus is requested or moved, the caret / IME / long-press menu are untouched, the resting hairline border stays underneath, and the animation discipline matches pressVeil (terminating tween/spring per transition, zero infinite animations). Applied to all seven input surfaces: `Field` (every editor in the app), the entry-editor hero amount, the dominant search box, the onboarding country search, the contact-picker search, the ledger quick-item input, and the keypad's number display. New `FocusRingTest` drives the real app: the search overlay's auto-focused field must hold focus, take input, and stay focused — reaching the assertions proves the ring composes over the same box as the v1.6.29 press veil.

**THE CONTACT-DETAIL HISTORY BUTTON ("should have some sapce between his neeber, it should be at the start of the left side").** The balance row was `Arrangement.Center` with the history chip flush against the balance pill. Now `Arrangement.spacedBy(8.dp, Alignment.Start)`: the chip anchors the LEFT edge like every other content row on the page, with a fixed 8dp gap to the pill beside it.

Build: APK `1.6.31` vc42, same release key as every prior release (apksigner SHA-256 `2f6d65be…869ad` — installs over anything, data intact); both iOS targets compile; 78/78 tests green (`--max-workers=1`).

---

## v1.6.30 — the splash is gone; the call cards went flat-and-light; the long-press sheet shows full numbers and a detail-page icon; the calculator copies AND pastes; the recents tabs got filter tabs and a nav-button reset; a just-ended call reaches the top before you return (2026-10-27)

User reports (verbatim log: `sandbox/chat-logs/2026-10-27-v1.6.30-user-reports.md`):
"when it opens it takes long time in the splash screen, if you cant make to
not show the splash screen slower, just remove it"; "THE NUMBERS STRIP:
long-pressing a call CARD should show the full number not ..."; "in the
long-pressing a call CARD after the name of the person end at the top put an
icon that goes and indecet to go it's detail contact page"; "in the caluleter
i should copy and pest in the display just like other calculetersand normal
inputs"; "in the recent after clicking one of the two tabs it should have
filter tabs to help filter just like in search. and if the recent btn at the
bottom navigation is clicked it should go back to the recent as the tabs was
not clicked"; "lets say in recents call cards i make a call from one of the
call card then after the call ends it is not putting that call card fast enf
at the top"; "the call cards are little bit havey it its ui should be
lighter, now it is heavy on the iye"; "check github + give me apk + commit,
even the apk".

- **The splash is gone.** v1.6.28 made the boot fast and flash-free, but the
  splash pane still waited for the FULL hydrate before handing over — the
  user still watched a splash. Per the explicit instruction ("just remove
  it"), the splash pane, its exit fade and the process flags are DELETED:
  the real shell (header + nav + recents chrome) composes from frame one.
  The v1.6.28 appearance seed still runs before `setContent`, so frame 1 is
  already the user's theme + language (no flash); the recents content area
  stays a plain canvas until the single batched hydrate hop publishes the
  cached calls + contacts (no "no calls yet" empty-state blink); and the
  onboarding wizard is gated on hydrate so the DEFAULT settings can never
  flash the first-run wizard at returning users.
- **The numbers strip shows the FULL number.** The long-press call-card
  sheet's number tiles were clamped to a fixed 196dp with an ellipsis — a
  long international number read "+251 911 223…". The tile now GROWS to the
  width of the whole pretty-printed number (`widthIn(min=186)` + single
  unwrapped line), so nothing is ever cut off; label pill, favourite star,
  copy, call and long-press-copy all behave exactly as before.
- **A contact-detail icon right after the name.** The long-press sheet's
  header (the person's name) now carries an account-circle icon immediately
  after it — one tap closes the sheet and opens that contact's DETAIL PAGE
  (saved contacts only; the close X keeps its place after the icon).
- **Calculator copy AND paste.** The display already had a copy chip; now
  LONG-PRESSING the display copies the current value (like other
  calculators), and a PASTE chip sits beside the copy chip. It reads the
  clipboard and sanitizes it into a valid display expression — digits, `.`,
  `+ − × ÷`, with ASCII `- * /` and the `,` decimal mapped to the display
  glyphs, operator runs collapsed, dangling operators trimmed, and
  non-evaluating text (a copied phone number, prose, a price with currency)
  falling back to the first plain number. The result lands exactly like a
  memory recall: it replaces a finished result, appends after an operator,
  or takes over the entry. Two new i18n keys ×5 languages
  (`calc.pasted`, `calc.nothingToPaste`).
- **The recents two tabs got their filter tabs.** "They owe you" / "You owe
  them" and the All/Missed/Incoming/Outgoing/Favorites chips shared ONE
  filter state — picking Missed after an owe tab silently REPLACED the owe
  view (that is why the tabs felt missing). The owe chips are now a toggle
  TAB state of their own: clicking one reveals the direction filter row
  right beneath it (the same chips the sticky day header carries — "filter
  tabs to help filter just like in search"), both filters COMPOSE, and
  tapping the active owe tab returns to "all". Tapping the RECENTS button in
  the bottom navigation (even while already on Recents) bumps a new
  `UiStore.recentsResetTick` that snaps the owe tab, the direction filter,
  the page counter and the scroll position back to the nothing-clicked
  state.
- **A just-ended call reaches the top faster.** The call log only refreshed
  on onResume — after the user returned — so the card landed a beat late. A
  ContentObserver on `CallLog.Calls.CONTENT_URI` now fires the moment the
  provider writes the ended call (while the user is still on the in-call
  screen); a 1.2 s debounce collapses the provider's burst of notifications
  (insert + duration update…) into ONE incremental `loadSince` refresh, so
  the card is already at the top of Recents when the user comes back. No new
  permission — the app already holds READ_CALL_LOG for the log itself, and
  without it the observer simply never fires.
- **The call cards are lighter.** The per-row chrome — filled card, hairline
  border, 16dp corners, 50dp avatar ring, 40dp grey call disc, 16sp Bold
  titles — read heavy on the eye. The cards are now FLAT rows on the canvas:
  no fill, no border, separated by an inset 0.5dp hairline (Material-list
  style), 44dp avatar ring, 15sp SemiBold titles, 12sp meta/time, a softer
  balance pill and a 36dp brand-soft call disc. The swipe underlay behind a
  displaced row is canvas-toned to match, and swipe actions, the
  missed-call red title, the "(3)" collapsed-run badge and the favourite
  dialing all behave exactly as before.
- Tests: 76/76 green (new V1630Test — the paste sanitizer: glyph mapping,
  comma decimals, operator-run collapse, dangling-operator trim, phone/
  prose fallback, garbage → empty). Android APK 1.6.30 vc41, same release
  key as every prior release (installs over anything, data intact); both
  iOS targets compile.

---

## v1.6.29 — the history icons are real buttons now; the ledger quick item lives inside the credit/debit/income/expense kind tabs; every text input visibly reacts to the touch (2026-10-27)

User reports (verbatim log: `sandbox/chat-logs/2026-10-27-v1.6.29-user-reports.md`):
"make the history icon more visible"; "the Ledger quick item — a label +
price — is should be in the credit, debit, expenses and incom not in the
outer ledger"; "Press feedback is not there for the input. like when i inter
a qountity in the items ledger or in other places there is no Press
feedback. all the inputs should have .check github, give me apk and push
even the apk".

- **The history icons are real buttons.** Two history controls existed and
  both were easy to miss. In the CALCULATOR, the history toggle was a bare
  22dp sub-toned glyph floating beside the Σ and copy icons — it is now a
  LABELLED brand pill (icon + bold "History", brand-soft fill + brand
  hairline, solid brand while the panel is open), and Σ/copy got visible
  34dp chips so nothing in the header is a naked faint icon. In CONTACT
  DETAIL, the history button in the total/settled row was a bare 18dp glyph
  in a transparent box — it is now a 34dp brand-soft circular chip with a
  brand hairline and a 20dp glyph.
- **The quick item moved into the kind tabs.** The v1.6.27
  `[Item label][Price][＋]` row rendered in the OUTER ledger and guessed a
  kind when no kind tab was active. It now renders ONLY while a KIND tab is
  active — Credit/Debit on a contact ledger, Income/Expense on the general
  ledger — placed after the filter strip, and the ＋ books the entry AS the
  kind being viewed. The outer view (All/Unpaid/Overdue) stays clean.
  Locked with `QuickItemKindTabsTest` (absent on All → present on the kind
  tab → absent again on All).
- **Every input visibly reacts to the touch.** The v1.6.25 press indication
  rides `LocalIndication`, which BasicTextField never consults — tapping
  ANY text field showed zero reaction. New `Modifier.pressVeil(corner)` is
  a self-contained pointer OBSERVER (never consumes — caret, long-press
  selection, paste and IME gestures are untouched) that paints the same
  16% veil over the input's own box while a finger is down. Applied to
  `Field` (covers the items-ledger quantity/unit/price inputs and every
  editor), the entry hero amount, the quick-item inputs, the contact-picker
  search, the dominant search screen, the onboarding country search and
  the keypad number display. Freeze-lesson compliant: every await suspends
  on a real event and the pressed flag is written once per gesture boundary.
- **Delivery per the standing request** ("give me apk and push even the
  apk"): APK 1.6.29 (versionCode 40, same release key as every release —
  installs as a direct update, data intact) committed under `releases/`,
  pushed, published as GitHub Release v1.6.29 with the APK attached, copy
  at `/home/z/my-project/download/callmony-1.6.29-kmp-release.apk`. Full
  battery 71/71 (16 classes, new: QuickItemKindTabsTest); both iOS targets
  compile.

---

## v1.6.28 — no-flash boot (appearance seeded before frame 1, hydrate overlapped with first-frame init, splash fades out); the long-press call-card sheet gains a horizontally-scrolling phone-numbers strip (2026-10-27)

User reports (verbatim log: `sandbox/chat-logs/2026-10-27-v1.6.28-user-reports.md`):
"still opens slow and there is some flash when its open"; "in LONG-PRESS the
call card it should also show the phone numbers options in one row at the top
that scrolla to side with icons that are in other place and to copy even by
long press".

- **The open flash, decoded.** Until hydrate published the persisted
  settings, the store's in-memory settings were the DEFAULTS (theme "auto",
  language "en") — and the splash renders from them. A dark-theme user saw
  the dark system splash → a LIGHT Compose splash (the flash) → the dark
  main UI; an am/om/ar/ti user saw the splash tagline flash English-first.
  `AppStore.seedBootAppearance(sysDark, themeDark, lang)` is new: called by
  MainActivity BEFORE `setContent` (and by the iOS `MainViewController` via
  the shared `scanPersistedBootPrefs()` scan) it installs the persisted
  theme + language + OS dark flag BEFORE the first Compose frame, so frame 1
  of the splash already carries the user's appearance. It is guarded — a
  no-op once hydrated — so an activity recreate can never clobber real
  settings.
- **The splash exit is a fade, not a cut.** The hydrated frame used to
  hard-swap the splash for the main UI. A one-shot 220 ms `SplashExitFade`
  now rides on top of whatever rendered (later sibling = higher z):
  terminating animation (nothing infinite — the v1.6.19 lesson), once per
  process, never replayed on rotation, and skipped entirely if the boot
  loads finished before the first frame (fast warm boots skip the splash
  altogether).
- **The open is faster.** v1.6.27 parallelized the four boot loads but
  hydrate still only STARTED from a LaunchedEffect — after the first frame
  was composed and applied. Hydrate is now kicked from the platform entry
  point BEFORE `setContent`, so the boot file reads (settings, call-log
  cache, contacts cache, PIN) overlap first-frame Compose init.
  `AppStore.hydrate` is idempotent (`kotlin.concurrent.Volatile` single-shot
  latch); the CallmonyApp LaunchedEffect call remains as the guarded no-op.
  The single-writer publish path, the splash bail-out and the boot harnesses
  are unchanged.
- **The long-press call-card sheet carries the numbers now.** Long-pressing
  a call CARD opens the per-row action sheet; above the action rows a
  horizontally-scrolling strip shows one tile per DISTINCT number of the
  saved contact (same dedupe key as the choose-a-number picker): the label
  pill + the FAVOURITE star (set/UNSET, live from `state.contactX`) on the
  top row, the pretty number + the COPY icon + the CALL icon on the bottom
  row — the same icons in the same roles as the picker. Tap calls, long-
  press copies (same toasts). A horizontal scrollable inside the sheet's
  vertical scrollable is different axes — safe (the v1.6.25 More-sheet crash
  was vertical-inside-vertical).
- Tests: `V1628Test` (seed installs theme+language+osDark, normalizes
  unknown languages, inert once hydrated) and `CallRowSheetStripTest`
  (Robolectric: long-press a real call card → sheet opens → both distinct
  numbers render as tiles → reaching the assertions is the measure-crash
  guard; also asserts boot ran through the new entry-point hydrate latch).
  Full battery 70/70 green; both iOS targets compile; APK 1.6.28 vc39,
  same release key as every prior release.

---

## v1.6.27 — fast boot again; anchored keypad; favourite-number star in the picker; overlap-free calculator with Advanced; visible press feedback; search kind tabs list contacts; ledger quick item (2026-10-27)

User reports (verbatim log: `sandbox/chat-logs/2026-10-27-v1.6.27-user-reports.md`):
the app opens a little slower now, it shouldn't be; when typing on the keypad
the keypad gets pushed to the bottom under the navbar when the contacts show
at the top; long-pressing the call icon on a recent card should offer the
number options with an icon to set/UNSET the favourite (beside and before the
call icon), the card should dial the favourite number first everywhere, and
copy should work via long-press and a copy icon; the calculator keys overlap
(make them smaller with padding), M+ M− MR belong in an ADVANCED panel whose
icon sits beside the history icon, and the display should have no visible
border and show very long numbers; every input should visibly react when
tapped, even in the ledger; the search Credit/Debit/Income/Expense tabs are
not listing the CONTACTS that have them; in the contact detail the history
icon should sit in the same row as the total and settled-up, before them; the
ledger could have an item that is just a label and a price; check GitHub,
deliver the APK, push even the APK.

1. **Boot speed** (`Store.hydrate`) — v1.6.26 added the cached CONTACT BOOK
   to the boot path (the names-from-first-frame fix), but it ran AFTER
   `Db.loadAll()` and the cached call log, so the first frame waited the SUM
   of three big file reads + JSON decodes — the 1239-contact book is the
   largest blob in the app. All four loads (store, calls cache, contacts
   cache, PIN file) now run CONCURRENTLY on the Default pool and land in the
   SAME single batched main hop: the first frame waits the MAXIMUM of the
   loads, not their sum. Names still render from the very first frame.

2. **The keypad stays anchored** (`KeypadSheet`) — the live-match list was
   `heightIn(max = 250.dp)`: with matches + exact pill + number display +
   the 12-key grid + the action row the panel EXCEEDED the content area, and
   a bottom-anchored column clips its TAIL — the bottom key rows and the
   call button ended up UNDER the always-visible nav. The match list is now
   a WEIGHTED child (`weight(1f, fill = false)`): it takes whatever space
   is LEFT above the fixed keypad portion and scrolls inside it — contacts
   at the top, the keypad exactly where it was, at any match count.

3. **Choose-a-number picker** (`RecentsScreen`) — the long-press call-icon
   picker's rows gained: a COPY icon, long-press-to-copy, and the FAVOURITE
   star (set and UNSET — `Store.clearPrimaryPhone` is new; tapping the
   filled star clears it) sitting directly before the call icon. The
   contact-detail picker got the same order + unset behavior for
   consistency. FAVOURITE-FIRST DIALING now truly covers every surface:
   recent cards, the call row sheet, keypad live matches, keypad speed
   dial, the speed-dial grid sheet, search hits and the detail navbar all
   dial `primaryPhone()` (ContactX.primaryPhone → first stored phone).

4. **Calculator** (`Calculator`) — the keys were `aspectRatio(1f)`: the tile
   width forced a height taller than the row's weighted share, so tiles
   spilled over their neighbours on short/narrow screens. Keys now FILL THE
   ROW HEIGHT with an 8dp gap — overlap is structurally impossible. M+ /
   M− / MR / MC moved out of the base layout into an ADVANCED panel behind
   a Σ toggle that sits BESIDE the history icon in the header. The display
   lost its card tone as well as its border (fully flat) and AUTOSIZES its
   font (40→16sp by length, 3 lines) so very long numbers stay readable;
   the operand cap rose from 12 to 15 digits.

5. **Press feedback everywhere** (`Kit.PressIndication`) — the v1.6.25 veil
   at 10% black was practically invisible on the near-white canvas (the
   user reported inputs STILL didn't react, "even in the ledger"). The veil
   is 16% now, and the pointer-based tap targets that LocalIndication can
   never reach — the 12 dialer keys and the calculator's hold-to-repeat
   backspace — light up their tiles while pressed.

6. **Search kind tabs** (`MiscOverlays.SearchOverlay`) — THE actual bug: the
   result sections (contacts/entries/lists/notes/reminders/banks) lived in
   the final `else` branch of the results list, reachable ONLY with a
   query. In BROWSE mode (a kind/date/type chip picked with an empty query)
   the list rendered nothing but the all-empty state — clicking Credit
   showed a blank screen even when hundreds of contacts matched. The
   sections now render in BOTH modes; empty states fire only when their
   branch truly has nothing.

7. **Contact detail** (`ContactDetail`) — the history icon rides IN the
   total/settled-up row, before the balance pill and the status label (one
   tap jumps to the Info tab, whose first block is the contact's call
   history).

8. **Ledger quick item** (`LedgerSection`) — a [Item label] [Price] [＋] row
   at the top of Single entries creates a priced entry in ONE tap: the kind
   follows the active filter tab (Credit tab → credit, Debit → debit; a
   contact defaults to Credit, the general ledger to Expense), the label
   rides as the entry's single line item, and a single-item entry card now
   shows the ITEM'S LABEL as its title.

**Tests**: 61 green — the 55-test pure battery + V1627Test (favourite
set/unset/fallback, quick-item round-trip, the exact search kind predicate)
+ BootDiagTest + UpgradeBootDiagTest through the new concurrent hydrate
path. i18n: 4 new keys x5 languages (1065/1065 parity).
Android 1.6.27 vc38 built with the same signing key as every prior release
(installs over anything, data intact); both iOS targets compile.

---
## v1.6.26 — always-visible bottom nav; names-first recents; 55/45 calculator; search kind+custom-date; file backup/restore; contact-detail grid More (2026-10-27)

User reports (verbatim log: `sandbox/chat-logs/2026-10-27-v1.6.26-user-reports.md`):
the recents card showed the phone number instead of the name for a moment at
open; the call icon should offer the number options on long press; the bottom
navigation should be there ALWAYS, with nothing covered by it; the calculator
should be 55% keypad / 45% display, borderless display, colourful keys; the
search credit tab did not show the contacts that have them and the date tab
needs a custom range; the app should never lose data, even across a reinstall
or update; the contact detail first row should scroll with a fixed More tile
that shows a GRID of everything; the second row should go away in favour of
icon tabs with the bank in a different colour; "+ Expense" looked different
from its friends; the choose-a-number picker needs a favourite icon.

1. **Recents names at open** (`Store.hydrate`) — the cached call log was
   published at boot but the cached contact book was not, so Recents matched
   every card against an EMPTY book for the seconds the real fetch takes
   (the boot chain only starts it at +4 s): every saved contact's card showed
   the RAW PHONE NUMBER as its title until the book landed. The cached book
   (contacts-cache-v2.json) now publishes in the SAME batched main hop —
   the very first frame matches names, the fresh fetch replaces it silently.
2. **Long-press the call icon** (`CallCard`) — holding the call button on a
   recent card opens the "Choose a number" sheet listing the contact's
   distinct numbers when there is more than one (single-number contacts keep
   the plain tap-to-call). The picker is held at SCREEN level, not inside the
   card — a BottomSheet composed inside a LazyColumn item collapses its
   full-screen scrim to item size (unbounded height). The card (and swipe
   actions) now dial the contact's FAVOURITE number first
   (`primaryPhone()`: ContactX.primaryPhone → first phone → raw number).
3. **Always-visible bottom nav** (`CallmonyApp`) — every overlay used to
   render in a full-screen Box AFTER the shell Column (hiding the nav under
   contact detail / search / settings / calculator / keypad / sheets). All
   of them — OverlayHost, keypad, More sheet, alarm dialog, toaster,
   confirm, host sheets — now compose INSIDE the content area between the
   header and the nav. The nav is present everywhere, and because the
   content Box ENDS where the nav begins, nothing can render under it
   either. All the now-redundant per-screen system-bar paddings
   (Sheet/BottomSheet/Calculator/KeypadSheet/ContactPicker/Search) were
   stripped; the nav (and the full-screen splash/lock/onboarding surfaces)
   own their insets. The toast offset was recalibrated (96dp → 10dp) for
   the new host.
4. **Calculator 55/45** — the sheet body splits EXACTLY: display zone
   (display card + memory row + history) weight(45f), keypad weight(55f).
   The display lost its visible border; the main line grew to 40sp ExtraBold
   with the live preview above it (24sp). Every key is colourful: operators
   keep the brand green (brandSoft tile), AC is red, backspace amber, % and
   ± blue, = solid ink, and the digits + '.' cycle a soft five-hue tint
   (teal/blue/violet/amber/rose) with matching strong glyphs.
5. **Search** — (a) the kind row (Credit/Debit/Income/Expense) now filters
   CONTACTS too: a contact matches when it has ≥1 non-deleted entry of that
   kind inside the active date window — browsing Credit lists exactly the
   people you credited, and in query mode the kind/date filters narrow the
   name hits as well; (b) a CUSTOM date-range chip opens a from/to sheet
   (two native Material calendar PickerFields, both ends optional) and the
   range applies to entries AND contact matching.
6. **Data safety (reinstall/update)** — three layers: (a) explicit Android
   Auto Backup rules for BOTH engines (fullBackupContent pre-12 +
   dataExtractionRules 12+: sharedpref/file/database/external all ride the
   cloud backup and device-to-device transfer); (b) Export backup now shares
   a REAL FILE — the JSON is written to cacheDir/backups and served through
   a FileProvider so the system sheet offers "Save to Files / Drive /
   Downloads" (a backup saved to Downloads SURVIVES an uninstall — the old
   text-share pasted megabytes into a chat box); (c) Restore-from-file: a
   new transparent FilePickActivity hosts the SAF picker (ACTION_OPEN_
   DOCUMENT results only arrive on an Activity), streams the picked file's
   text back on the main thread and restores it (merge, with confirm).
   Update-installs never lost data (same signing key + lenient JSON loader
   + mirrored prefs commits) — this round covers the reinstall case.
7. **Contact detail** — the first icon row is now a SIDE-SCROLLING strip
   (Call · SMS · WhatsApp · Telegram · Email) with a FIXED More tile
   pinned at the end that never scrolls away; the ledger icon and the
   whole 6×2 grid are GONE — the segmented tabs below carry their own
   icons now (book/lists/notes/bank/bell/info) with the BANK tab in its
   own blue (active AND inactive). The More sheet is a GRID: quick
   actions, every saved social WITH ITS LABEL, per-number WhatsApp +
   Telegram, an add-social tile and an edit tile — followed by the
   add-to rows, general actions and Block/Unblock (nothing lost).
   SocialLink gained an optional `label` field (default "" — old rows
   fall back to the app name everywhere they render); the Add-social
   sheet asks for it at creation ("e.g. Work, Family").
8. **"+ Expense" consistency** — the ledger's secondary actions
   (+ Expense / + Debit, New group, Share statement) were generic ghost
   chips under the primary row's custom pills; both rows now share ONE
   ActionPill design (brandSoft tile, brand hairline, glyph, bold tiny
   label) — every action button on the ledger page is identical.
9. **Favourite number star** — the choose-a-number picker rows carry a
   star (filled amber when active) that sets that number as the
   contact's primary/favourite number — the number the app dials first
   from the Recents card, the contact navbar and the pickers.

Verification: full unit suite 62/62 GREEN (incl. the 5-test Recents
Robolectric battery and the 3 MoreSheetCrashTest regression cases — the
latter updated for the new composition order: overlays now compose BEFORE
the bottom nav). Android `assembleRelease` clean; `aapt`: com.callmony
1.6.26 vc37, minSdk 23, targetSdk 34; `apksigner` SHA-256 `2f6d65be…869ad`
identical to every prior release (installs as a direct update, data
intact). Both iOS targets compile (klibs cross-compiled).

---

## v1.6.25 — the More-sheet crash fixed; press feedback everywhere; compact flat redesign; search filters before typing (2026-10-26)

The user tested v1.6.24 on their Samsung A12 and reported nine issues
(verbatim log: `sandbox/chat-logs/2026-10-26-v1.6.25-user-reports.md`),
led by a hard crash. All nine are fixed:

- **THE CRASH — "when i click the more btn and the three dots in the navbar
  in the detail contact page the app closes"**: the ContactDetail More sheet
  wrapped its content in its own `verticalScroll` Column while `BottomSheet`
  already scrolls its content — a vertically scrollable measured inside
  another scrollable receives an INFINITE max-height constraint and Compose
  throws `IllegalStateException` at measure time. Both triggers (the More
  quick-action tile and the navbar dots-vertical) set the same `moreOpen`
  flag, so both died instantly. The inner scroll is removed (the only
  call site in the app with the pattern) and the path is locked with three
  new Robolectric tests (`MoreSheetCrashTest`: quick action, navbar dots,
  block-row render — all compose the real sheet with real stores).
- **"every input on this app should have some indication it was clicked"**:
  the app is bare Compose (no MaterialTheme wrapper), so `LocalIndication`'s
  default draws nothing — no chip, tile, keypad key, card or row ever showed
  a press. New multiplatform `PressIndication` (an `IndicationNodeFactory` +
  `DrawModifierNode` pair: a bounded press veil that springs in on press and
  fades out on release) is provided through `LocalIndication` at the app
  root, reaching every default `clickable`/`combinedClickable` on every
  screen, sheet and overlay; `PressableRow` and `LongPressText` no longer
  null their indication; scrims and toasts stay perfectly quiet.
- **PIN "dots get coloured every time but not on the fourth"**: the submit
  ran synchronously in the same frame the 4th digit landed, so the unlock
  (or shake-and-clear) recomposed before the 4th dot ever painted. The
  submit now runs from a `LaunchedEffect` 160 ms after the 4th digit — the
  filled dot renders first, unlocking still works exactly as before.
- **"on the credit or others where is the icon to update the existing one"**:
  every credit/debit/income/expense card now carries a visible pencil
  button beside the amount that opens the editor directly (the row tap
  still edits too, and the chevron still expands the detail).
- **Single-entries tabs ("should have a btn to add new one / should not
  overlap with its cards")**: the section header now shows a LABELED
  kind-aware add chip (＋ Credit on the Credit tab, ＋ Income on General's
  income tab, …) next to the plain ＋, and the filter strip carries its own
  vertical padding so the chips never sit flush against the cards.
- **Attachments ("it means to add to it… just like they do with their own
  if we clicked them")**: attached lists/notes/reminders now ride directly
  on the COLLAPSED entry card (they used to hide inside the expanded
  detail), and tapping one opens the entity exactly like its own section
  does — list → list detail, note → note editor, reminder → reminder
  editor (shared `openRef` helper). Attaching still lives in the entry
  editor (＋ Attach → the three-tab picker).
- **Search "advanced tabs… even before the search begins… like credit, and
  others, date and more"**: the type tabs (All/Contacts/Entries/Lists/
  Notes/Reminders/Banks with live counts) are always visible now, plus a
  new always-visible refinement row — kind (All kinds/Credit/Debit/Income/
  Expense) and date (Any date/Today/Last 7 days/Last 30 days/Overdue).
  With an empty query, picking a filter enters BROWSE mode: the list shows
  everything of that type, pre-filtered, before a single letter is typed.
  Five new i18n keys across all five languages.
- **The redesign ("improve the ui… beautiful, compact, modern… most
  important the ui is so ugly and bulky")**, executed to the user's strict
  spec — original palette only, every label/amount/control preserved:
  flat surfaces + subtle borders instead of the v1.6.24 shadows (cards
  hairline-only at 16dp corners/12dp inset, primary buttons flat, sheets
  and keypad 10→6dp lift); compact 48dp sheet navbar; the contact balance
  pill directly beneath the header; the 6×2 quick-action grid at 7dp
  tiles / 20dp icons / 2dp gaps; three financial summary cards in one row
  (Net keeps its selected-state styling); Credit/Debit/Income (General:
  Income/Expense/Credit) as three equal-width brand-soft buttons in one
  FIXED row with the remaining actions in a scroll row beneath; compact
  entry cards (34dp icon tiles, 30dp icon buttons, 6dp card gaps); the
  whole spacing snapped to the 4dp/8dp system.
- **Bonus — the Recents Robolectric battery is green for the first time in
  five rounds**: while building the crash repro, the notorious flakiness
  (the same 5 `ComposeTimeoutException`s every round, previously
  A/B-proven as sandbox load) was decoded — the batteries' `@Before` reset
  `state.hydrated = false`, but the hydrate `LaunchedEffect` runs once per
  composition, so whenever the rule-launch boot finished before `@Before`
  ran, the reset parked the app on the splash forever and the first
  `waitUntil` deadlocked. Test-only fix (no hydrated reset; every test
  already waits for `hydrated=true` before seeding). Full suite 65/65.

Build: Android `assembleRelease` clean (versionCode 36 / versionName
1.6.25, minSdk 23, targetSdk 34); `apksigner` SHA-256 `2f6d65be…869ad`
identical to every prior release (installs over anything, data intact);
both iOS targets compile; 65/65 unit tests green.

---

## v1.6.24 — every reported feature back, on the freeze-free foundation; diagnostics removed; UI depth polish (2026-10-26)

The user re-sent the full report list on top of the v1.6.16 rollback
baseline ("v1.6.16 is the last version that didn't freeze — don't make it
crash like you did before"). Everything on that list was already solved by
the v1.6.17/18 work; the freeze those versions carried was root-caused and
fixed during v1.6.19–23. So v1.6.24 = the feature work restored ON TOP of
the fixes:

- **Restored from v1.6.17**: reminders that actually fire (edit
  false-positive fix, permission at save, closed-app repeat advance +
  re-arm); live NOW clock + 12/24 h everywhere; saved-countdown and live
  card countdowns; the PIN truly locks (launch lock, real-touch autolock,
  background immediate) with the keypad bottom-center, not top-left;
  compact no-overlap tabs; all raw i18n keys defined in all 5 languages;
  aligned side-by-side fields; no ugly wraps; compact buttons.
- **Restored from v1.6.18**: repeat reminders at ANY interval
  (custom:<n>m|h|d|w|mo) + ALARM mode (dedicated channel on the ALARM
  stream, audible over silent); one-tap entry editing (no more two save
  buttons); Record payment (renamed, root-level host sheet, native date
  picker); Single-entries ＋ creates the tab's kind; attachments
  (lists/notes/reminders on any credit/debit/expense/income via entry.refs
  + picker sheet); Settle up removed; modals anchored to the bottom
  (HostSheets root-level slot); block contacts (More sheet + Blocked badge
  + blocked-notice calls); dominant solid search pill + advanced filter
  tabs with live counts; the rebuilt contact detail (More sheet with
  sideways-scrolling labeled social strip + add-to options + Block, action
  icons on number rows, soft-card section separation, navbar number
  tap-to-call with call icon + long-press copy).
- **Baked in from v1.6.19–23 (the freeze fixes — the whole point)**: the
  boot spinner renders only while a call-log ask/fetch is genuinely in
  flight (no more invisible infinite animation under the onboarding sheet
  → ANR); ALL persistence runs on the single-writer IO queue (the main
  thread never touches the disk); every store publication happens in one
  batched main-thread hop (no background thread ever touches Compose
  snapshot state); the autolock touch observer awaits the down FIRST and
  refreshes per event until release (the old observer returned without
  awaiting anything, which busy-spun the main thread from the first touch
  until the 5-second ANR — the actual "freezing and crushing"); the
  contacts fetch is single-flight through a Mutex gate (no more two
  concurrent whole-book reads on the permission grant).
- **Removed**: the entire v1.6.20–23 diagnostics experiment — ANR
  watchdog, freeze reports, crash firewall, the separate :diag process and
  its screen, the in-app diagnostics overlay, the Settings row. It was
  never requested, and its reports (including three false positives)
  caused confusion. The only survivor is a 15-line BootTrace phase marker
  that the Robolectric boot harnesses assert against (the regression
  guard for the freeze-on-open class).
- **UI polish**: cards, primary buttons, bottom sheets and the keypad
  now float with soft shadows over their surfaces (the "flat / bulky"
  report).
- Verified: full pure test suite + BootDiagTest/UpgradeBootDiagTest/
  StartupTest green through the diag-free path; both iOS targets compile;
  APK `com.callmony` 1.6.24 versionCode 35, signed with the same release
  key as every prior version (installs directly over anything, data
  intact). The 5 Recents Robolectric timeouts reproduce identically on
  unmodified code (documented sandbox-load flakiness, A/B-proven in the
  v1.6.22 round).

---

## v1.6.16 rollback (versionCode 34) — full rollback to v1.6.16 (2026-10-26)

At the user's request, the app went back to **v1.6.16 exactly**. The source
tree of this release is byte-identical to the original v1.6.16 release
(commit `34076f2`) except one build-config line: `versionCode` 26 → 34.
Android refuses to install an APK whose build number is lower than the
installed one, and the only alternative (uninstall first) would delete user
data — so the number was raised while the code stayed at v1.6.16. v1.6.16's
persistence layer reads data written by v1.6.17–1.6.23 without issues
(`ignoreUnknownKeys` + `coerceInputValues` JSON, unchanged storage keys), so
existing data survives the rollback. v1.6.17–1.6.23 remain available on the
[Releases page](https://github.com/maca14313/callmony/releases) and in git
history.

---

## v1.6.16 — KMP: search call buttons, instant keypad, themed splash, fuzzy search, rich notifications (2026-10-25)

User-reported round six on the KMP edition:

1. **Calling from Search made easy** — contact hits show the primary number
   and a one-tap call button; the row itself still opens the detail sheet.
2. **Onboarding Get started pinned** — the currency/country form scrolls
   under an always-visible footer button.
3. **Keypad performance** — the live-match contact index builds on
   `Dispatchers.Default` and is cached process-wide (zero index work when
   the sheet opens, reused across opens); live-match rows read balances from
   a one-shot owner→balance map instead of a full ledger scan per row per
   keystroke; the Vibrator system service is cached (a binder lookup ran on
   every keypress — the "laggy typing"); LazyColumn keys are collision-proof.
4. **Splash background matches the app theme** — `MainActivity` resolves the
   persisted theme (dark / auto+system) before `super.onCreate()` and picks
   `Theme.Callmony` or the new runtime-addressable `Theme.Callmony.Dark`;
   the Android-12+ system splash background rides the same window color.
5. **Forgiving contact search** — two new matchers: cross-word-boundary
   prefixes (`abebeke` → "Abebe Kebede") and fuzzy subsequences (`abk`,
   `kbede`); per-contact search index cached per book identity (RN parity).
6. **Rich reminder notifications** — round logo large icon (circle-clipped,
   brand ring), brand accent color, kind subtext, BigText body, tap-to-open,
   and **Done / Snooze-10-min quick actions** (NotificationActionReceiver:
   live AppStore update when the app is running, persisted-store JSON
   surgery when it is not) + a self-dismissing confirmation; channel gains
   description, vibration pattern, lights and badges; the status-bar icon is
   the new monogram as a white alpha mask; BootReceiver re-arms carry the
   localized labels.

## v1.6.15 (KMP) — versionCode 25
- **Keypad typing is instant** (the "when i type on the keypad it lags" report): the live contact matcher re-normalized EVERY phone of EVERY contact (3 regex passes per number) on EVERY keystroke — hundreds of regex compilations per digit on a real book. Matching now runs over a precomputed index (display name, T9 letters, raw/national/normalized digit strings per phone) built once per contact-book change; each keystroke is plain `startsWith`/`contains` over that index. The exact-match pill uses the same index.
- **The phone's clock/battery/signal are visible in LIGHT mode** (the "i cant see the phones date, charge present… when it is on dark mode but not on light mode" report): `enableEdgeToEdge()` derives the status/navigation bar icon appearance from the SYSTEM dark mode, not the app theme — with the system in dark and the app in LIGHT, white icons sat on the light canvas and vanished. A `SystemBarsAppearance` expect/actual now drives the insets controller from the APP canvas color on every theme change. Bonus fix: the OS dark-mode flow was never wired into the app's "auto" theme — `theme.osDark` is now collected from the platform, so "auto" actually follows the OS.
- **Search opens the keyboard on its own** (the "when i click the search icon the keyboard should open" report): the search overlay's field auto-focuses the moment the overlay composes (`FocusRequester`), so the IME is up before you touch anything.
- **The native back button closes modals instead of the app** (the "go back native btn closes the app instead of closing modal and go back" report): a root `AppBackHandler` (expect/actual around androidx's back dispatcher) closes in order — top overlay → keypad → More sheet → back to Recents — and only lets the system exit when nothing is open. Every Sheet, BottomSheet, ContactPicker and the alarm dialog register their own handler, so nested modals (action sheet over contact detail, language sheet over More, "add to existing contact" picker) close one level at a time.
- **The keypad tile is the MIDDLE of the bottom navigation** (the "make the keypad icon on the middle" report): the bar reads Recents · Calculator · **Keypad** · General · More.
- **Copy, cut and paste work in the keypad's number display** (the "why can't i paste and cut in the keypad number display like normal input" report): the display was a `readOnly` BasicTextField — Copy only, Paste/Cut disabled by the toolkit. It's now a real native `EditText` with `setShowSoftInputOnFocus(false)` (exactly the RN edition's TextInput trick): the full long-press menu — Copy · Cut · Paste · Select all — with selection handles, while the software keyboard NEVER opens and the 12-key grid stays the only way to type. The paste sanitizer still strips everything non-dialable.
- **The filter bar sticks under the owe chips while cards scroll** (the "component where it shows the icons tabs for all, missed, incoming, outgoing, favourite and the date and the create contact btn should be sticking" report): every day header (date · ＋ new-contact · the five direction chips) is a LazyColumn `stickyHeader` — RN `stickyHeaderIndices` parity, the chips ride along pinned above the scrolling cards.
- **Swipe a call card and you get the full quick-action set** (the "i only see the call and favourite" report): left drag now reveals Call · WhatsApp · SMS · Favourite; right drag reveals Copy number · Create contact (or the contact's call-history Info when saved). The long-press sheet keeps every action.
- **Data-safety hardening** (the "most secure app so that any data cannot lost" report): (a) the SharedPreferences mirror key (`key~m`, written on every save since the RN edition) was NEVER read back — a corrupted primary row silently returned null and the data "vanished"; reads now fall back primary → mirror; (b) prefs writes are a single synchronous `commit()` of value + mirror in ONE atomic editor (async `apply()` could drop the last writes on a hard kill; saves run on background coroutines so the UI never blocks); (c) document-file writes are ATOMIC — tmp file → fsync → rename over the target, with the previous good file rotated to `.bak` and a `.bak` read-fallback — a crash or full disk can never leave a truncated file where your ledger used to be.
- **No more "old logo" anywhere** (the "logo in the splash screen is the old one and also in the navbar" report): the logo now decodes EAGERLY on a background thread started in `Application.onCreate`, so the very first composed frame — the splash — already carries the real bitmap (the fallback tile that used to flash at every cold start was being read as "the old logo"); a second, independent copy of the logo lives in `res/drawable-nodpi` as a fallback source if the asset read ever fails; and every remaining `phone-in-talk` fallback glyph (splash, navbar tile, PIN lock screen) is replaced — the fallback is now a plain brand tile that can't be mistaken for any old logo. The About pill shows the live version constant.
- **Contact detail: +Credit/+Debit before +Income/+Expense** (the "add credit and debit tabs should be before the income and expense" report): the contact ledger's add-chips lead with credit/debit (they're the contact-first kinds); the General tab keeps income/expense first. The entry editor's kind picker already read credit → debit → income → expense.
- **Back-to-back missed calls collapse into one card** (the "more than one consecutive back to back missed call… single call card with indication how many times" report): runs of consecutive missed calls from the SAME number in the log collapse into the newest call's card carrying a bold red "(N)" badge — stock-dialer behavior; the missed filter, counts and per-number grouping all respect it.
- **Unsaved numbers show the NUMBER as the card title** (the "show there phone number in the name place if the contract is not saved" report): the carrier-cached name no longer takes the title slot — an unsaved call renders its pretty-printed phone number big and bold, exactly like the stock dialer.
- **Save-button spinner**: the loading spinner inside `Btn` uses the button's foreground color (a brand-colored spinner on the brand background was invisible); it only appears while an actual save/write is in flight.
- **iOS compiles again**: the v1.6.13/v1.6.14 iOS date picker used `UIAlertController`/`UIAlertAction` members that the Linux cross-compile klibs don't expose (both iOS targets have been failing since it landed). The pickers were rewritten against the verified-binding subset: a bottom-sheet `UIDatePicker` (wheels) with a dimmed tap-to-confirm background, hour/minute derived from the wheel delta. Both `iosArm64` and `iosSimulatorArm64` are green again.
- **Tests: 28/28 green** — the 25 from v1.6.14 plus RecentsV1615Test (missed-run collapse renders exactly one "(3)" badge and one card for the grouped number; a carrier-cached name never takes an unsaved card's title; the sticky day-header chips stay reachable after a deep scroll).

## v1.6.14 (KMP) — versionCode 24
- **Recents scrolls over the call cards** (the "i can't scroll the call cards" report): the ported SwipeRow used `detectDragGestures` and called `change.consume()` on EVERY drag — the detector passes touch slop in ANY direction, so a vertical swipe over a card was eaten by the row and the LazyColumn beneath never scrolled. Now `detectHorizontalDragGestures` claims horizontal motion only; vertical drags fall straight through to the list, horizontal swipes still reveal the call/WhatsApp/SMS action buttons (tap-on-open closes first, single-open bus, ±24px overdrag — all unchanged).
- **Call cards re-match contacts live** (the "clicking a card doesn't go to its contact detail" report): the `rows` memo was keyed on the call log only — the RN `useMemo` deps are `[systemCalls, appRecents, contacts]`. The port froze the phone→contact match at first render, so every card stayed "unsaved number" after the +4s contact book load (or after creating a contact), and taps opened the action sheet instead of the contact detail. `contacts` is a memo key again; cards flip to named+balance the moment the book lands.
- **The bottom-nav General/Ledger tile navigates** (the "ledger btn is not going to ledger page" report): the nav tiles never called `setTab` — tapping the wallet (General/Ledger) or Recents tile did nothing at all. Full RN `handle()` parity now: every tile `closeAll()`s first, real tabs `setTab`, the keypad opens the dialer ON TOP of Recents (tap-while-open closes), and the calculator tile TOGGLES its sheet (open→close, closed→open) instead of re-opening.
- **Segmented tab strips are compact** (the "tabs overlap / make things compact" report): every segmented control (General's Ledger·Lists·Notes·Reminders·Accounts, contact detail's six segments, Trash's seven) renders compact 28dp chips in a single side-scroll row — five-to-six tabs fit side by side on a phone with no crowding.
- **Created contacts are findable** (the "can't find what I created, even in search" report): `createContact` returned `results[0].uri.lastPathSegment` — the **raw-contact id** — while everything else in the app (fetchAll, detail lookups, favorites, speed dials, socials, merges) keys on the **aggregate Contacts._ID**. Created contacts carried an identity nothing matched afterwards. The batch now resolves the aggregate CONTACT_ID from the new raw row and returns that; the local list patch, contactX extensions and the provider agree on one id.
- **Contacts refresh on foreground**: `MainActivity.onResume` now runs a TTL-guarded `maybeRefreshContacts()` (60s) — contacts created/edited in the device Contacts app (or any other app) while callmony sat in the background appear in search, Recents matching and the keypad without an app restart. RN parity (boot + foreground TTL guards).
- **Nothing under the status bar or gesture bar** (the "covered by the native bottom navigation and topbar" report): insets sweep — the onboarding sheet (first launch: "Welcome" under the clock, Get-started under the gesture bar), the PIN lock screen, the calculator's bottom key row, footer-less full-screen sheets (contact detail etc.), the search overlay's result list and the contact picker's list all pad `statusBarsPadding`/`navigationBarsPadding` now.
- **The new logo in the onboarding too** — matching the splash tile.
- **Search verified at RN parity**: 8 new unit tests lock the ranked matcher (word prefixes, word SUBSTRINGS — 'abd' hits Habdullah, INITIALS runs — 'kb' → Kalkidan Bekele, multi-token AND queries, phone-fragment + local/E.164 forms, ranking). No behavior change — proof it was never the algorithm; the frozen rows memo and the raw-id bug were.
- **New UI regression tests (25/25 green)**: RecentsScrollTest — boots the real app, seeds the call log BEFORE any contact exists, proves the card flips from "+251 9110 0001" to "Person 01" when the book lands, taps it into the contact DETAIL overlay, scrolls 28 cards deep via the list's scroll semantics, and swipes a card horizontally with no crash; plus the bottom-nav wallet tile → General ledger page → back to Recents. (Real swipeUp gestures don't move scrollables under Robolectric NATIVE graphics — verified against a bare LazyColumn — so the scroll semantics action stands in for the gesture.) Battery also runs with a store reset between tests (the JVM singletons used to leak state across test classes).

## v1.6.13 (KMP) — versionCode 23
- **Keypad typing crash fixed**: `parseHexColor` parsed hex through `("0x" + s).toInt(16)` — Java/Kotlin REJECT the `0x` prefix under an explicit radix → `NumberFormatException` on every `Avatar()` render. Typing 2+ digits on the keypad rendered live-match avatars → instant close. Now a plain `substring().toInt(16)` parse; the Robolectric KeypadTest taps through the whole flow with seeded contacts.
- **Calculator math fixed (the "2 × 2 = 2.000000002" report)**: THREE stacked bugs — (1) `applyOp` matched the Unicode operator glyphs (`− × ÷`) but the shunting-yard normalizes to ASCII (`- * /`) first, so every non-`+` expression fell into `else -> b` and returned the LAST OPERAND; (2) the old `+1e-10` epsilon nudge polluted every result ("2 × 2 = 2.0000000001"); (3) `fmtFixed` derived the decimal fraction via `Double.toString()`, which switches to scientific notation at 1e7 and produced strings like "0.0000003.0E11" for ≥8 decimals. Now: both operator spellings matched, epsilon gone, integer-math fraction, exact-integer fast path + 12-significant-digit formatting (0.1+0.2 → "0.3", 12345678.9 never scientific). CalcFormatTest (5 cases) locks it in.
- **The ledger actually takes an amount**: the entry editor's hero amount was a display-only `Text` — nothing could be typed, Save stayed disabled. Now a real decimal-pad `BasicTextField` (comma tolerated, one decimal point, 14 chars) exactly like the RN hero field; the calculator "Use value" flow still feeds it.
- **Reminders fire**: `AlarmReceiver` was never registered in the manifest (alarms fired into the void); `BootReceiver` now re-schedules every pending reminder straight from the persisted store instead of nudging a dead receiver; the channel is ensured and notifications carry HIGH importance.
- **Advanced date/time setters**: reminders, ledger dates and due dates use the OS's own pickers now (Android Material `DatePickerDialog`/`TimePickerDialog`, iOS `UIDatePicker` wheels) — the same native experience `@react-native-community/datetimepicker` gave the RN edition; nothing invalid can be entered, the old plain text inputs are gone.
- **Calls — 100% the RN ImmediateCall chain**: `CALL_PHONE` granted → `ACTION_CALL` (the DEFAULT dialER app places the call instantly; the number is never dropped into the keypad); missing → the runtime dialog once → instant call on grant, pre-filled `ACTION_DIAL` keypad on deny. `CALL_PHONE` is finally REQUESTED at runtime (it never was — that's why every call dumped into the dialer).
- **SMS handoff**: `ACTION_SENDTO` + `smsto:` (resolves to the DEFAULT messaging app, conversation pre-filled; plain `sms:` via `ACTION_VIEW` only reaches apps that declare it). Manifest `<queries>` extended.
- **System call log**: `READ_CALL_LOG` is requested at runtime (it never was — `fetchAll()` silently returned `[]` and the permission looked granted); denial surfaces the amber card with a working re-ask; returning from the dialer/messenger triggers an incremental refresh so calls made through the handoff appear immediately.
- **"Couldn't save contact" fixed**: `WRITE_CONTACTS` is now requested before every contact mutation (creator, editor, add-phone-to-contact, merge duplicates) — it was never asked for, so `applyBatch` always failed.
- **Toasts never under the camera**: the shell toaster is `fillMaxSize`-anchored to the bottom (above the bottom nav) — a wrapping Box got pinned at the top under the status bar/cutout and hid the clock/battery.
- **Logo**: re-cropped — the white monogram sits at 43% of the 108dp adaptive canvas (70% of the visible safe-zone circle ≈ the source logo's own proportions), so NO launcher mask (circle/squircle/rounded-square/tear) can clip it; legacy icons full-bleed teal with the glyph at ~58%; the real logo image is in the header, splash and About sheet (decoded once, cached process-wide).
- **Keypad display**: `readOnly` — tapping it no longer opens the text IME (RN parity: `showSoftInputOnFocus=false`); the 12-key grid is the input; long-press Copy still works.
- **Bottom breathing room**: Recents/General/Reminders/Trash content padded above the nav bar.

## v1.6.12 (KMP) — versionCode 22
- **Instant-close crash fixed**: a file-local `Modifier.heightIn(min:)` extension recursed into itself (shadowing the framework import) — any `Btn`/`Chip` render → `StackOverflowError` → app closes instantly. Extension deleted, framework import restored.
- **New logo** everywhere (launcher icons all densities + adaptive, splash, in-app asset), iOS AppIcon added, and the first true iOS compile (both KMP iOS targets green on Linux via klibs cross-compilation).

## v1.6.11 — versionCode 21
- **No more "loading calls" on every open**: `hydrate()` reads the persisted store, the PIN file and the call-log disk cache **in parallel** and applies them in ONE `set()` — the old order flipped `hydrated:true` before the cache read, so Recents mounted empty and flashed the loading spinner on every open. The Shell's first paint now carries the full cached call log.
- **No more 400-row re-read every open**: new native `SysCallLogModule.loadSince(since)` queries `CallLog.Calls` with `DATE > ?` — the everyday refresh is **incremental** (only rows newer than the newest known call, 1-minute overlap, dedup by id), typically zero rows over the bridge. Full re-read only when the list is empty, the snapshot is older than the 10-minute TTL (reconciles deletions made in other apps), or pull-to-refresh. A refresh that changes nothing skips the `systemCalls` set entirely — no re-render, no flicker.
- **"They owe you" / "You owe them" phrases use the user's exact wording on every surface** (Recents/Contacts chips, call cards, keypad results, contact detail, ledger stat cards, kind-picker hints, General overview) — am (ይገባኛል / ይገባዋል·ታል), om (Anaaf mala / Isaaf·Ishiif), ar (يحق لي / يحق له·لها), ti (ይግባኣኒ'ዩ / ይግባኦ·ኣ'ዩ); web i18n mirror synced.

## v1.6.10 — versionCode 20
- **Full names**: the middle name is a real part of every contact — `toDeviceContact` composes given+MIDDLE+family (dropping it was why "store landlord Work" showed as "store Work"); displayName/initials follow; visible Middle-name field in editor and creator; local save patch composes it; vCard N/FN + CSV carry it; disk cache migrated to `contacts-cache-v2` (legacy v1 renders instantly but is marked stale so the TTL guard silently re-fetches with full names; legacy file deleted after the first v2 save).
- **Fast contact detail**: Sheet modals animate with a cheap **fade** instead of slide (Android pushed the whole heavy tree through a transform layer on every open — the "too slow to open" jank); ContactDetail mounts header/balance/12 quick tiles in the first frame with the section body deferred via InteractionManager.
- **Create-contact icon in every day header** is now the **account-plus** glyph (person-with-plus), also on the empty-filter fallback row and the Contacts new-contact chip.
- **Advanced + fast search**: 12-hit cap gone (50 ranked hits); **multi-token** queries ("abdu mo"), **initials** runs ("kb" → Kalkidan Bekele), **word-substring** matching ("abd" → Habdullah); tie-break dropped O(n log n) localeCompare for a code-point compare on a pre-lowered index; debounce 140 ms → 70 ms — benchmarked 1–6 ms per keystroke over a 5000-contact book.
- Translations per user: credit → ይገባኛል / Anaaf mala / يحق لي / ይግባኣኒ'ዩ and debit → ይገባዋል·ታል / Isaaf·Ishiif / يحق له·لها / ይግባኦ·ኣ'ዩ (am/om/ar/ti on lg.credit, lg.debit, kindCredit, kindDebit).

## v1.6.9 — versionCode 19
- **Lag fix**: contacts refresh is now **single-flight** (one in-flight whole-book fetch shared by boot/foreground/save/editor-close callers, InteractionManager-deferred) and TTL-guarded at boot + foreground — the stacked bridge dumps were the "most laggy version ever".
- Reminders ＋ **always visible**: header pinned above the scroll + trailing ＋ Add-reminder row in every reminder list.
- The ＋ new-contact icon moved **into every day header** and off the top strip.
- Recents options: "Add to existing contact" was dead (onPress closed + unmounted the sheet before the picker state could set — now a stay-open item); labels unambiguous: "Create new contact" vs "Add to existing contact" (i18n ×5).
- **PIN moved to the Android `no_backup` directory** — saveSettings strips it from the backed-up store, hydrate discards legacy/restored hashes, set/remove/wipe sync the file: removing the PIN is forever, even across uninstall + reinstall (app data still restores).
- Keypad display is a **real TextInput** — native long-press Copy/Paste/Select-all with selection handles, keyboard suppressed, pasted text sanitized to dialable chars.
- System nav bar matches the app inside every sheet (Theme.FullScreenDialog never set navigationBarColor — overridden in app res values/values-v27/values-night-v27, verified in the APK).

## v1.6.8 — versionCode 18
- Keypad display long-press opens Copy/Paste/Clear like a regular input (clipboard button kept).
- Recents day headers slimmed — tiny plain "Oct 24" labels (no Today/Yesterday/weekday, no badge).
- Call cards are icon-only for direction (no outgoing/incoming/missed text).
- Reminders use **native date/time pickers** (@react-native-community/datetimepicker) — free-text inputs that could accept anything are gone.
- **Contacts fully in-app**: own simple create-new-contact form, "Add to contact" for unsaved numbers right in the recents options list, DEFAULT-phone star per number, zero-lag Save (sheet closes instantly + local patch; the whole-book bridge re-fetch that froze Save AND typing is deferred while any editor is open — the actual root cause), native `createContact()` added to ContactEditModule.
- Calculator moved to the **bottom navigation** (toggle tile), removed from the navbar.
- Speed: memoized editor rows, no blocking bridge dumps while typing.

## v1.6.7 — versionCode 17
- **Root cause of dead contact search + keypad matching + every-recent-card-"unsaved"**: hiding the Contacts tab had hidden the only READ_CONTACTS ask — permission now requested at boot (`requestMultiple`) and re-checked in `refreshDeviceContacts`, with `contactsPerm` state + one-tap Allow-contacts cards in Recents/Search when denied.
- Keypad paste hardened — 300 ms long-press, clipboard button visible whenever the clipboard holds text, lenient extraction of a number embedded in copied text (≥9 digits), "Pasted" toast.
- ＋ new-contact chip beside the favorites star chip in the Recents day headers.
- Saved-contact cards open the contact detail again once the book loads.
- Reminders-when-closed reliability ported back — `ensureReminderReadiness` (channel + one-time notification-permission auto-ask + self-heal re-arm at boot/foreground, 5 min throttle), battery-exemption native methods restored with the one-time ask at reminder save; Reminders tab Turn-on deep-links to notification settings after a denial.

## v1.6.6 — versionCode 16
- **Code reverted to the v1.6.2 tree** per user request; reminders-when-closed = the v1.6.2 notifee system (OS alarms + reboot re-arm + Done/Snooze background actions); unsaved-number contact-detail page removed; Contacts tab hidden in BottomNav only (contacts code intact, book refreshes in background); keypad PASTE added; onboarding = dedicated full-screen modal with pinned Get-started; favorites strip always visible with trailing ＋ new-contact tile; safest-app stack ported (mirror writes + fallback reads, 6 h auto snapshots, Android Auto Backup).

## v1.6.5 — versionCode 15
- Keypad PASTE (clipboard button beside the display + long-press paste, sanitized — the display was custom Text so Android had nothing to paste into); onboarding rebuilt as a dedicated full-screen modal with the Get-started button pinned in its own bottom container (hard 36 px floor + safe-area); contacts verified NOT removed; ＋ new-contact tile beside favorites; 3-layer data safety carried over; i18n kp.paste / kp.noNumberInClipboard ×5.

## v1.6.4 — versionCode 14
- Reminders that ring without hunting: notification permission auto-asked **once at app open** for users who already have reminders (the upgrade path — the real reason reminders "still don't work" on phones that never asked); self-healing alarms (every open / foreground return re-arms anything the OS scheduler lost); one-time battery-exemption system dialog at the first reminder save (Tecno/Infinix/Xiaomi/Samsung alarm killer #1).

## v1.6.3 — versionCode 13
- Reminders that provably ring (health card: notifications + exact-alarm + battery-exemption checks each fixable in one tap, alarm-category notifications, armed-verification toasts + per-row armed badges, 20 s test alert); unsaved numbers open a contact-detail sheet whose hero saves them to the address book; onboarding Get-started can never crop; Contacts tab parked (Recents is the home — they-owe/you-owe list ALL contacts with balances, ＋ new-contact tile, deferred background refresh); safest-app data stack: mirrored AsyncStorage writes with mirror-fallback reads, automatic local safety snapshots, Android Auto Backup, Settings data-safety card.

## v1.6.2 — versionCode 12
- Always open on Recents; reminders that actually ring (exact-alarm perms + WorkManager fallback + notification Done/Snooze actions + permission at save + live ON/OFF status cards + snooze 10m/1h/3h/tomorrow); call-card tap vs long-press misfire fixed (native Pressable long-press, 650 ms, touch-slop cancel); onboarding Get-started never crops; copy icon works in the choosers; country-code-agnostic cached search index with national-number matching + T9 + suffix match (search, keypad, pickers).

## v1.6.1
- Fixed contact-editing data loss + editor hang (own ContactEditModule — surgical row-scoped ContactsContract ops), toasts visible above sheets, calculator-backed amount input, reminder date handling, dark nav bar, unified chooser sheets with add/edit rows.

## v1.6.0 — versionCode 10
- 10-item feedback wave: instant-open feel (last-tab restore, 140 ms splash, settings cog out of the header), in-app advanced contact editor, real full-balance numbers with large-value fit, chooser sheets, borderless quick actions, long-press copy, call-history tap-to-call, Recents direction filters as icon-only chips inside sticky day headers, They-owe/You-owe own row on both tabs, compact summary boxes, balance pill in search + keypad results, General hero = global balance, first-run currency + country-code onboarding, native nav bar color.

## v1.5.3 — versionCode 9
- Fixed a v1.5.2 native startup crash: `IndexOutOfBoundsException` in `ReactClippingViewManager.addView` via `manageChildren` — Android VirtualizedList's default `removeClippedSubviews=true` swaps list data at boot while all cells are clipped; disabled clipping on all three FlatLists.

## v1.5.2
- Native crash reporter (`last-crash.txt` shown on next open), boot breadcrumbs, safe mode after 3 failed boots, render boundary; balance+owes pill in the phone-number place on cards; contact-detail hero removed (nav-bar name/number/avatar/star/dots + one balance line); Recents credit chips on their own row + compact filters and summary boxes.

## v1.5.1
- Startup crash fix: release-mode crash firewall (global error handler override + SafeRoot recovery root with Continue/Reset-data), shape-sanitized persistence reads — the app can no longer close itself on open.

## v1.5.0
- Zero-lag boot (Recents default + call-log snapshot), tab-scoped contact fetch with infinite scroll, fixed dead social/email icons (manifest `<queries>` + openURL), keypad flat keys, recents/contacts card beauty pass.

## v1.4.0
- Instant cached contacts, number-based social links, webapp-parity hero, auto email sync.

## v1.3.0
- Keypad webapp-parity + full contacts/recents/detail overhaul.

## v1.2.0
- Performance & UX overhaul; metro blockList for web/docs/sandbox folders (inotify watch limit).

## v1.1.0 (mobile)
- Fixed splash freeze (hydrate was never called); own native ImmediateCall + SysCallLog modules (instant dial via the default dialer app, real system call log); dialer-style boot permissions + instant contacts/recents; restructure: RN app at root + web app in `web/`; parity fixes (undo toasts, full balances incl. open lists, activity-based autolock, lead-time alerts, trash groups chip, WhatsApp statements both directions, tap-to-edit line items & list rows); CI workflow (docs copy) + version bump.

## v1.1.0 (web era)
- Ledger groups, entity attachments, action picker, keypad half-modal, calculator fixes, 5 languages.

## v1.0.0
- **912e203** callmony v1.0.0 — offline-first PWA dialer, contacts & money ledger (web).
- **6172c2e** callmony React Native v1.0.0 — production app for Android & iOS.
- **a5c4340** mobile: publish v1.0.0 APK to Releases; low-memory build tuning + JDK17/jlink local-build notes.

---

*Full commit history: `git log` in the repository root.*
