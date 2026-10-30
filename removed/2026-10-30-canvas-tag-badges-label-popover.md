# Removed features — the canvas calculator's tag badges & label popover

**Removed in:** v1.6.39 (versionCode 50)
**Reason (user instruction, verbatim):** *"dont want the icons that are in the numbers and why i need to click the icons if i can write the text or the numbers, by selecting from the btns"*
**Replaced by:** ONE unified active line. The Aa / 123 buttons on the canvas choose the input method (system QWERTY or the compact keypad), and labels are simply TYPED — "ball 200 + 100 cups" — parsed live by the same free-text grammar. Nothing is an icon tap anymore.

From v1.6.36 to v1.6.38 the canvas calculator's active line was an interactive token flow: chips, per-number affordances, and an anchored popover for naming numbers. v1.6.39 removed the icon-based interaction layer entirely. This document preserves what they did, so the behaviour can be restored or re-implemented anywhere.

---

## 1. The tag badges (`calcTag:$i`, `calcTag:res`)

A tiny 14dp rounded badge pinned to the TOP-LEFT corner of every number chip and of the live result (offset −7dp / −9dp, so it sat half outside the number, "pinned to the first digit").

**Features**
- Three states: neutral (`C.chip` + hairline border + a 9dp tag glyph in `C.sub`) when the number had no label; labelled (`C.brandSoft` + brand glyph) once a before/after label existed; active/solid (`C.brand` + on-brand glyph) while that number's popover was open.
- Tapping the badge opened the label popover anchored at that number (`openCalcLabel("t$i")`); tapping the result's badge opened it for the result (`"res"`).
- The badge existed purely as a clickable affordance — it showed no text, only the tag icon.

**Why it went:** the badge was the only way to reach the popover, but the user can type the label directly (the grammar already binds words to numbers), and with text/number entry on the mode buttons the icon tap was a redundant third interaction. The v1.6.38 report — *"i type ball then click the number btn, the ball text disappeared why"* — was rooted in the same split model (the typed text only mattered once it was re-serialized into tokens), so v1.6.39 unified the line into one text source and the badges had nothing left to annotate.

## 2. The anchored label popover (`CalcLabelPopover`, tags `calcLabelField`, `calcPos:before`, `calcPos:after`, `calcLabelDone`)

A compact dropdown card (208–264dp wide) anchored at the number/result it labelled: a 38dp text field, a Before / After chip row, and tiny ✕ / Done buttons.

**Features**
- Opened by tapping a tag badge, a rendered label, or a labelled number; auto-focused its field.
- **Before / After** chose the label's position around the number: "ball 200" vs "200 birr". Flipping sides swapped to the other side's stored label when one existed, otherwise kept the typed text.
- **Done** committed the label onto the chosen side of that number (`CalcToken.before` / `CalcToken.after`) — the other side cleared (one label, one position). An empty submit DELETED the label.
- For the result chip the same popover labelled the total: "total 500" / "500 birr" (`CalcNote.resultBefore` / `resultAfter`).
- ✕ cancelled without changes.

**How it worked internally**
- State: `calcLabelTarget` ("t<i>" for number i, "res" for the result), `calcLabelText`, `calcLabelPos` ("before"/"after").
- `openCalcLabel` seeded the field from the side that already carried text (default BEFORE); `toggleCalcLabelPos` swapped; `commitCalcLabel` wrote into the token and re-serialized; `cancelCalcLabel` dismissed.

## 3. The clickable token flow (per-number editing, tags `calcNum:$i`, `calcOp:$i`, `calcLbl:$i`, `calcLblAfter:$i`)

In number mode the active line was a FlowRow of chips: labels, numbers, operators and the live result, each individually tappable.

**Features**
- Tapping a NUMBER made it the keypad's ACTIVE target (`calcActive = i`): underlined + brand-coloured; digits from the keypad edited THAT number; ⌫ deleted inside it.
- Tapping a rendered LABEL opened that number's popover.
- Tapping an OPERATOR cycled it (+ → − → × → ÷ → +).
- `enterNumMode(aim)` aimed the keypad at a specific number; `enterTextMode()` re-serialized the tokens BACK into the text field — the re-serialization that could drop un-parsed text (the "ball" bug).

**Why it went:** all of it was the "click the icons" interaction the user questioned. In v1.6.39 the active line is one native editable text field in BOTH modes; the keypad appends to the END of the line as characters; % / ± work on the number the line ends with; the ⌫ key is a plain text backspace (which is why the v1.6.38 "delete the operator after the digits" fix is now inherent). Labels, the result zone ("= total 500 birr") and multi-line locking all still work — they are typed, not clicked.

---

**Where the grammar lives on:** `parseCalcNote` / `serializeCalcNote` / `evalCalcTokens` in `composeApp/src/commonMain/kotlin/com/callmony/lib/Calc.kt` are unchanged — labels before/after numbers, the result zone after `=`, `%` markers, multi-line ledgers, all of it still parses and serializes identically. Only the chip UI that edited them is gone.
