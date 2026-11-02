package com.callmony

import com.callmony.lib.applyModeSplit
import com.callmony.lib.CalcNote
import com.callmony.lib.CalcToken
import com.callmony.lib.calcLedgerTotal
import com.callmony.lib.calcSteps
import com.callmony.lib.evalCalcTokens
import com.callmony.lib.parseCalcLedger
import com.callmony.lib.parseCalcNote
import com.callmony.lib.quoteTextNumbers
import com.callmony.lib.serializeCalcLedger
import com.callmony.lib.textModeRanges
import com.callmony.lib.serializeCalcNote
import com.callmony.lib.displayCalcNote
import com.callmony.lib.calcBreakFold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
/* v1.6.36 — the ANNOTATED CALCULATOR v2 engine (the chip model behind the
   entry editor's Calculator tab, per the user's updated spec: interlocking
   "Text + Calculator" rows — a label slot before/after every number and
   around the result — with live step-by-step intermediate results).

   This battery pins:
     - parseCalcNote: free text (the v1.6.35 grammar, incl. every old note
       ever saved) → structured chips, labels stuck to their numbers
     - serializeCalcNote: chips → the same free text, byte-stable
     - evalCalcTokens: the live total with the parser's exact semantics
       (precedence, dangling ops, ÷0, implicit +)
     - calcSteps: the step-by-step folds — the user's own example is
       "Step 1: 100 × 2 = 200 Br · Step 2: 200 + 300 = 500 Br" */
class CalcNoteTest {

    @Test
    fun the_user_example_round_trips_through_the_chips() {
        val n = parseCalcNote("ball 100 × 2 price + 300 transport")
        assertEquals(3, n.tokens.size)
        assertEquals(CalcToken(before = "ball", number = "100", op = "×"), n.tokens[0])
        assertEquals(CalcToken(number = "2", after = "price", op = "+"), n.tokens[1])
        assertEquals(CalcToken(number = "300", after = "transport"), n.tokens[2])
        assertEquals(500.0, evalCalcTokens(n.tokens)!!, 0.0)
        // serialize is the SAME text — old notes reopen as chips, new chips
        // stay plain readable text on the entry
        assertEquals("ball 100 × 2 price + 300 transport", serializeCalcNote(n))
    }

    @Test
    fun result_labels_live_around_the_total() {
        val n = parseCalcNote("ball 100 × 2 price + 300 transport = total 500 birr")
        assertEquals("total", n.resultBefore)
        assertEquals("birr", n.resultAfter)
        // the math never reads the result zone
        assertEquals(500.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals(
            "ball 100 × 2 price + 300 transport = total 500 birr",
            serializeCalcNote(n),
        )
    }

    @Test
    fun keypad_built_chips_serialize_cleanly() {
        val n = CalcNote(
            tokens = listOf(
                CalcToken(number = "100", op = "×"),
                CalcToken(number = "2", op = "+"),
                CalcToken(number = "300"),
            ),
        )
        assertEquals("100 × 2 + 300", serializeCalcNote(n))
        assertEquals(500.0, evalCalcTokens(n.tokens)!!, 0.0)
        // a result label zone is only written when the user annotated the
        // total — never by default
        assertEquals(
            "100 × 2 + 300 = total 500",
            serializeCalcNote(n.copy(resultBefore = "total")),
        )
    }

    @Test
    fun the_users_step_by_step_example_folds_exactly_as_drawn() {
        val n = parseCalcNote("ball 100 × 2 price + 300 transport")
        val steps = calcSteps(n.tokens)
        assertEquals(2, steps.size)
        assertEquals("100 × 2", steps[0].expr)
        assertEquals(200.0, steps[0].value, 0.0)
        assertEquals("200 + 300", steps[1].expr)
        assertEquals(500.0, steps[1].value, 0.0)
    }

    @Test
    fun multiplication_folds_before_addition_in_the_steps() {
        val steps = calcSteps(parseCalcNote("100 + 200 × 3").tokens)
        assertEquals(700.0, evalCalcTokens(parseCalcNote("100 + 200 × 3").tokens)!!, 0.0)
        assertEquals(2, steps.size)
        assertEquals("200 × 3", steps[0].expr)
        assertEquals(600.0, steps[0].value, 0.0)
        assertEquals("100 + 600", steps[1].expr)
        assertEquals(700.0, steps[1].value, 0.0)
    }

    @Test
    fun single_number_has_no_intermediate_steps_but_a_total() {
        val n = parseCalcNote("rent 1200 deposit")
        assertEquals(1, n.tokens.size)
        assertTrue(calcSteps(n.tokens).isEmpty())
        assertEquals(1200.0, evalCalcTokens(n.tokens)!!, 0.0)
    }

    @Test
    fun glued_operators_and_labels_parse() {
        val n = parseCalcNote("fee 100+300")
        assertEquals(400.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals("fee 100 + 300", serializeCalcNote(n))
        assertEquals(4.0, evalCalcTokens(parseCalcNote("2x2").tokens)!!, 0.0)
        assertEquals(42.0, evalCalcTokens(parseCalcNote("6×7").tokens)!!, 0.0)
    }

    @Test
    fun adjacent_numbers_gain_an_explicit_shown_plus() {
        val n = parseCalcNote("100 200")
        assertEquals(300.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals("100 + 200", serializeCalcNote(n))
    }

    @Test
    fun dangling_operator_is_kept_but_never_evaluated() {
        val n = parseCalcNote("ball 100 +")
        assertEquals(1, n.tokens.size)
        assertEquals("+", n.tokens[0].op) // kept: the chain is mid-typing
        assertEquals(100.0, evalCalcTokens(n.tokens)!!, 0.0) // but ignored
        assertEquals("ball 100 +", serializeCalcNote(n))
    }

    @Test
    fun division_by_zero_drops_the_operator() {
        val n = parseCalcNote("100 ÷ 0")
        assertEquals(100.0, evalCalcTokens(n.tokens)!!, 0.0)
        val steps = calcSteps(n.tokens)
        assertEquals(1, steps.size)
        assertEquals("100 ÷ 0", steps[0].expr)
        assertEquals(100.0, steps[0].value, 0.0)
    }

    @Test
    fun decimals_commas_and_arabic_digits() {
        assertEquals(3.25, evalCalcTokens(parseCalcNote("1,5 x 2 + 0.25").tokens)!!, 0.0)
        assertEquals(150.0, evalCalcTokens(parseCalcNote("شحن ١٠٠ + ٥٠").tokens)!!, 0.0)
        // the Arabic label survives the round trip, the digits normalize
        assertEquals("شحن 100 + 50", serializeCalcNote(parseCalcNote("شحن ١٠٠ + ٥٠")))
    }

    @Test
    fun words_without_numbers_are_just_labels() {
        assertTrue(parseCalcNote("hello world").tokens.isEmpty())
        assertTrue(parseCalcNote("").tokens.isEmpty())
        assertNull(evalCalcTokens(emptyList()))
    }

    @Test
    fun labels_survive_edits_and_deletion() {
        val base = parseCalcNote("ball 100 × 2 price")
        // editing a label (chip → text) and writing it back
        val edited = base.copy(
            tokens = base.tokens.mapIndexed { i, tk ->
                if (i == 1) tk.copy(after = "price in birr") else tk
            },
        )
        assertEquals("ball 100 × 2 price in birr", serializeCalcNote(edited))
        // deleting a label (empty submit) removes it from the text
        val deleted = edited.copy(
            tokens = edited.tokens.mapIndexed { i, tk ->
                if (i == 0) tk.copy(before = "") else tk
            },
        )
        assertEquals("100 × 2 price in birr", serializeCalcNote(deleted))
    }

    @Test
    fun multi_word_labels_stay_one_label() {
        val n = parseCalcNote("ball 100 × 2 price in birr + 300 transport cost")
        assertEquals("price in birr", n.tokens[1].after)
        assertEquals("transport cost", n.tokens[2].after)
        assertEquals("ball 100 × 2 price in birr + 300 transport cost", serializeCalcNote(n))
    }

    /* ---------------- v1.6.37 — the stacked ledger ---------------- */

    @Test
    fun ledger_parses_multi_line_notes_into_lines() {
        val lines = parseCalcLedger("ball 100 × 2\n50 + 25\n")
        assertEquals(2, lines.size)
        assertEquals("ball", lines[0].tokens[0].before)
        assertEquals(200.0, evalCalcTokens(lines[0].tokens)!!, 0.0)
        assertEquals(75.0, evalCalcTokens(lines[1].tokens)!!, 0.0)
        // a legacy single-line note is a one-line ledger
        assertEquals(1, parseCalcLedger("ball 100 × 2 price + 300 transport").size)
        // blank input is an empty ledger
        assertTrue(parseCalcLedger("").isEmpty())
        assertTrue(parseCalcLedger(" \n  ").isEmpty())
    }

    @Test
    fun ledger_round_trips_through_the_save() {
        // what the editor serializes on Save: committed lines + the active
        // line, "\n"-separated, each in the same free-text grammar
        val lines = listOf(
            parseCalcNote("ball 100 × 2"),
            parseCalcNote("50 + 25"),
        )
        assertEquals("ball 100 × 2\n50 + 25", serializeCalcLedger(lines))
        // the round trip is stable
        assertEquals(lines, parseCalcLedger(serializeCalcLedger(lines)))
        // a line whose chain was emptied drops out entirely
        assertEquals("50 + 25", serializeCalcLedger(listOf(CalcNote(), parseCalcNote("50 + 25"))))
    }

    @Test
    fun ledger_total_sums_every_line() {
        val lines = parseCalcLedger("ball 1 × 200\n2 × 150\n25")
        assertEquals(525.0, calcLedgerTotal(lines)!!, 0.0)
        // nothing that computes → null (no bar)
        assertNull(calcLedgerTotal(emptyList()))
        assertNull(calcLedgerTotal(listOf(CalcNote())))
    }

    /* ---------------- v1.6.38 — the % key (phone-calculator semantics) ---------------- */

    @Test
    fun percent_after_plus_is_percent_of_the_running_total() {
        // the user's market math: 200 + 10% = 220
        val n = parseCalcNote("200 + 10%")
        assertEquals(2, n.tokens.size)
        assertTrue(n.tokens[1].pct)
        assertEquals(220.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals("200 + 10%", serializeCalcNote(n))
    }

    @Test
    fun percent_after_minus_discounts() {
        val n = parseCalcNote("500 - 5%")
        assertEquals(475.0, evalCalcTokens(n.tokens)!!, 0.0)
        // a SPACED "%" glues onto the number before it, same thing
        assertEquals(475.0, evalCalcTokens(parseCalcNote("500 - 5 %").tokens)!!, 0.0)
    }

    @Test
    fun percent_after_multiply_is_the_plain_fraction() {
        val n = parseCalcNote("200 × 10%")
        assertEquals(20.0, evalCalcTokens(n.tokens)!!, 0.0)
    }

    @Test
    fun chained_percent_uses_the_running_total() {
        // 100 + 50 = 150, then 10% of 150 = 15 → 165 (the classic behaviour)
        assertEquals(165.0, evalCalcTokens(parseCalcNote("100 + 50 + 10%").tokens)!!, 0.0)
    }

    @Test
    fun percent_steps_fold_at_the_resolved_value() {
        val steps = calcSteps(parseCalcNote("200 + 10%").tokens)
        assertEquals(1, steps.size)
        assertEquals(220.0, steps[0].value, 0.0)
    }

    @Test
    fun percent_survives_the_full_round_trip_and_mixed_labels() {
        val n = parseCalcNote("ball 200 + 10% tax = total 220 birr")
        assertEquals("ball 200 + 10% tax = total 220 birr", serializeCalcNote(n))
        assertEquals(220.0, evalCalcTokens(n.tokens)!!, 0.0)
    }
    /* ---------------- v1.6.40 — TEXT-MODE numbers never calculate ---------------- */

    @Test
    fun text_mode_numbers_are_quoted_into_words() {
        // "2 rooms" typed entirely on the QWERTY: the 2 is an annotation
        val marked = quoteTextNumbers("2 rooms", setOf(0))
        assertEquals("\"2\" rooms", marked)
        val n = parseCalcNote(marked)
        assertTrue("the quoted 2 must not be a token", n.tokens.isEmpty())
        assertEquals("\"2\" rooms", n.text)
        assertNull(evalCalcTokens(n.tokens))
    }

    @Test
    fun keypad_numbers_still_calculate() {
        // nothing typed on the QWERTY → the marker changes nothing
        assertEquals("ball 200", quoteTextNumbers("ball 200", emptySet()))
        assertEquals(200.0, evalCalcTokens(parseCalcNote("ball 200").tokens)!!, 0.0)
    }

    @Test
    fun quoted_marker_survives_the_round_trip() {
        val n = parseCalcNote("\"2\" rooms 500")
        // the 500 (keypad) is the ONLY math; the quoted 2 rides its label
        assertEquals(1, n.tokens.size)
        assertEquals("\"2\" rooms", n.tokens[0].before)
        assertEquals("500", n.tokens[0].number)
        assertEquals(500.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals("\"2\" rooms 500", serializeCalcNote(n))
    }

    @Test
    fun a_line_of_only_text_is_a_pure_annotation() {
        val n = parseCalcNote("just words no math")
        assertTrue(n.tokens.isEmpty())
        assertEquals("just words no math", n.text)
        assertEquals("just words no math", serializeCalcNote(n))
        // its total is nothing — it never enters the ledger sum
        assertNull(calcLedgerTotal(listOf(n)))
    }

    @Test
    fun the_ledger_keeps_annotation_lines_without_totalling_them() {
        val lines = parseCalcLedger("note only\n100 + 200")
        assertEquals(2, lines.size)
        assertEquals(300.0, calcLedgerTotal(lines)!!, 0.0)
        assertEquals("note only\n100 + 200", serializeCalcLedger(lines))
    }

    @Test
    fun old_unquoted_notes_still_calculate_exactly_as_before() {
        // back-compat: a v1.6.39 note has no markers — every number counts
        val n = parseCalcNote("ball 100 × 2 price + 300 transport")
        assertEquals(500.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals("ball 100 × 2 price + 300 transport", serializeCalcNote(n))
    }

    @Test
    fun quoted_number_after_an_operator_is_a_label_not_an_operand() {
        // keypad 100, QWERTY "+ 5": the 5 annotates, the math stays 100
        val marked = quoteTextNumbers("100 + 5", setOf(6))
        val n = parseCalcNote(marked)
        assertEquals(100.0, evalCalcTokens(n.tokens)!!, 0.0)
    }
    /* ---------------- v1.6.41 — the text-mode colour spans ---------------- */

    @Test
    fun quoted_numbers_carry_the_textmode_colour_span() {
        // v1.6.45 — words carry the TEXTMODE colour too (letters only ever
        // come from the text keyboard): base + the quoted "2" + rooms = 3
        val s = com.callmony.ui.overlays.coloredLockedLine(
            "\"2\" rooms 500", androidx.compose.ui.graphics.Color.Gray, com.callmony.ui.overlays.TEXTMODE_COLOR,
        )
        assertEquals(3, s.spanStyles.size)
        // the quoted token "2" is the TEXTMODE colour; the whole line is the base
        val quoted = s.spanStyles.first { it.item.color == com.callmony.ui.overlays.TEXTMODE_COLOR }
        assertEquals(0, quoted.start)
        assertEquals(3, quoted.end) // "2" with its quotes
        // and the WORD span rides beside it — same colour, saved or edited
        val word = s.spanStyles.last { it.item.color == com.callmony.ui.overlays.TEXTMODE_COLOR }
        assertEquals(4, word.start)
        assertEquals(9, word.end) // rooms
    }

    /* ---------------- v1.6.42 — the commit-date marker ---------------- */

    @Test
    fun the_commit_date_rides_the_round_trip() {
        val n = parseCalcNote("ball 100 × 2 @2026-10-31")
        assertEquals("2026-10-31", n.date)
        assertEquals(2, n.tokens.size)
        assertEquals(200.0, evalCalcTokens(n.tokens)!!, 0.0)
        // serialize puts it back; displayCalcNote keeps it out of the text
        assertEquals("ball 100 × 2 @2026-10-31", serializeCalcNote(n))
        assertEquals("ball 100 × 2", com.callmony.lib.displayCalcNote(n))
    }

    @Test
    fun a_dated_annotation_line_round_trips_too() {
        val n = parseCalcNote("note only @2026-10-30")
        assertEquals("2026-10-30", n.date)
        assertEquals("note only", n.text)
        assertEquals("note only @2026-10-30", serializeCalcNote(n))
        assertEquals("note only", com.callmony.lib.displayCalcNote(n))
    }

    @Test
    fun old_notes_without_a_date_open_with_no_date() {
        val n = parseCalcNote("ball 100 × 2")
        assertEquals("", n.date)
        assertEquals("ball 100 × 2", serializeCalcNote(n))
    }

    @Test
    fun the_date_marker_never_becomes_math_or_a_label() {
        // a marker with separators the grammar could otherwise eat
        val n = parseCalcNote("100 + 300 @2026-10-31")
        assertEquals("2026-10-31", n.date)
        assertEquals(400.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertTrue(n.resultBefore.isEmpty() && n.resultAfter.isEmpty())
    }

    @Test
    fun the_ledger_carries_dates_line_by_line() {
        val lines = parseCalcLedger("100 @2026-10-30\n200 @2026-10-31")
        assertEquals(2, lines.size)
        assertEquals("2026-10-30", lines[0].date)
        assertEquals("2026-10-31", lines[1].date)
        assertEquals(300.0, calcLedgerTotal(lines)!!, 0.0)
        assertEquals("100 @2026-10-30\n200 @2026-10-31", serializeCalcLedger(lines))
    }

    /* ---------- v1.6.45 — the colour separation that survives save + edit ---------- */

    @Test
    fun text_mode_ranges_mark_words_and_quoted_numbers_only() {
        // "rooms" = word (letters only ever come from the text keyboard),
        // "500" = keypad number, the quoted "2" = text-mode annotation
        //            r o o m s   5 0 0   " 2 "   b i r r
        // indices:   0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17
        val s = "rooms 500 \"2\" birr"
        val marks = textModeRanges(s)
        assertTrue("rooms must be text-mode", (0..4).all { it in marks })
        assertTrue("the keypad number must stay ink", (6..8).none { it in marks })
        assertTrue("the quoted annotation must be text-mode", (10..12).all { it in marks })
        assertTrue("birr must be text-mode", (14..17).all { it in marks })
        assertTrue("spaces never carry a mark", s.indices.filter { s[it] == ' ' }.none { it in marks })
        // operators stay ink even between words
        assertEquals(emptySet<Int>(), textModeRanges("3 + 4 × 2"))
        // blank is blank
        assertEquals(emptySet<Int>(), textModeRanges("   "))
    }

    @Test
    fun unlock_marks_match_the_locked_render_marks() {
        // a committed line's display text — the SAME set drives the locked
        // line's colours (coloredLockedLine) and the qwerty marks rebuilt
        // when the line is unlocked for editing, so live / saved / edited
        // all show one separation
        //        "   2   "       r o o m s       5 0 0
        // idx:   0   1   2   3   4 5 6 7 8 9   10 11 12
        val display = "\"2\" rooms 500"
        val marks = textModeRanges(display)
        assertTrue((0..2).all { it in marks })  // the quoted "2"
        assertTrue((4..8).all { it in marks })  // rooms
        assertTrue((10..12).none { it in marks }) // the keypad 500
    }

    /* ---------- v1.6.48 — the MODE-SPLIT pre-parse (report #4) + the newline fold (report #6) ---------- */

    @Test
    fun the_users_glued_example_splits_and_calculates() {
        // report #4 verbatim: "555555555" on the keypad, "dsssssss" glued
        // straight after on the QWERTY — the keypad number still calculates
        //              5 5 5 5 5 5 5 5 5 d s s s s s s s
        // indices:     0 1 2 3 4 5 6 7 8 9 ...
        val raw = "555555555dsssssss"
        val qwerty = (9..16).toSet()
        val split = applyModeSplit(raw, qwerty)
        assertEquals("555555555 dsssssss", split)
        val n = parseCalcNote(split)
        assertEquals(1, n.tokens.size)
        assertEquals("555555555", n.tokens[0].number)
        assertEquals("dsssssss", n.tokens[0].after)
        assertEquals(555555555.0, evalCalcTokens(n.tokens)!!, 0.0)
        // round-trip stable: the split form is plain grammar
        assertEquals("555555555 dsssssss", serializeCalcNote(n))
        // and the RENDER marks match the live field's (word blue, number ink)
        val marks = textModeRanges("555555555 dsssssss")
        assertTrue((0..8).none { it in marks })
        assertTrue((10..16).all { it in marks })
    }

    @Test
    fun a_text_mode_number_glued_after_keypad_digits_is_quoted_not_folded() {
        // "555" on the keypad then "5" on the QWERTY, no space: the run
        // boundary still splits, and the TEXT-mode number is quoted — it
        // must NOT fold into the keypad number
        val split = applyModeSplit("5555", setOf(3))
        assertEquals("555 \"5\"", split)
        val n = parseCalcNote(split)
        assertEquals(555.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals(1, n.tokens.size)
        assertEquals("\"5\"", n.tokens[0].after)
    }

    @Test
    fun pure_text_tokens_and_pure_keypad_tokens_never_split() {
        // all-QWERTY word: one run, unchanged
        assertEquals("dsssssss", applyModeSplit("dsssssss", (0..7).toSet()))
        // all-QWERTY NUMBER: quoteTextNumbers' own rule (quoted annotation)
        assertEquals("\"200\"", applyModeSplit("200", (0..2).toSet()))
        // all-keypad: untouched, calculating
        assertEquals("555", applyModeSplit("555", emptySet()))
        // already-quoted: self-preserving
        assertEquals("\"200\"", applyModeSplit("\"200\"", (1..3).toSet()))
        // no qwerty marks at all → identity, whatever the text
        assertEquals("rooms 500 = 500", applyModeSplit("rooms 500 = 500", emptySet()))
    }

    @Test
    fun a_three_run_token_splits_at_each_mode_boundary() {
        // keypad "5", QWERTY "d", keypad "5" — all three runs separate; the
        // two keypad numbers fold as the grammar's implicit +
        val split = applyModeSplit("5d5", setOf(1))
        assertEquals("5 d 5", split)
        val n = parseCalcNote(split)
        assertEquals(10.0, evalCalcTokens(n.tokens)!!, 0.0)
    }

    @Test
    fun the_newline_is_just_whitespace_to_the_calculation() {
        // report #6: the enter key lands a real newline INSIDE the same
        // individual calculation — the grammar splits on \s+, so the line
        // keeps calculating across the break
        val n = parseCalcNote("555\n5")
        assertEquals(560.0, evalCalcTokens(n.tokens)!!, 0.0)
        // and the serialized (committed) form is one line, folded cleanly
        assertEquals("555 + 5", serializeCalcNote(n))
        // the 1:1 fold keeps qwerty indices valid through applyModeSplit:
        // "555\n5" with the LAST 5 typed in text mode quotes only that 5
        val split = applyModeSplit("555\n5".replace('\n', ' '), setOf(4))
        assertEquals("555 \"5\"", split)
    }

    @Test
    fun the_break_marker_round_trips_as_a_visual_line_break() {
        // v1.6.49 report #5: the enter key's newline rides the save as the
        // ⏎ marker — parse stamps the token after it, serialize writes it
        // back, and the DISPLAY turns it into the visual break the user
        // typed (the locked line never collapses to "just one line")
        val n = parseCalcNote("275 +\u23CE70")
        assertEquals(345.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals("275 +\u23CE70", serializeCalcNote(n))
        assertEquals("275 +\n70", displayCalcNote(n))
        // the break rides the date/currency markers too
        val withMeta = parseCalcNote("275 +\u23CE70 @2026-11-02 ~USD")
        assertEquals("275 +\u23CE70 @2026-11-02 ~USD", serializeCalcNote(withMeta))
        assertEquals("USD", withMeta.currency)
        assertEquals("275 +\n70", displayCalcNote(withMeta))
    }

    @Test
    fun the_break_marker_is_just_whitespace_to_the_grammar() {
        // the marker separates tokens exactly like a space does — adjacent
        // numbers fold as +, labels carry their break inline
        val n = parseCalcNote("555\u23CE5")
        assertEquals(560.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals("555 +\u23CE5", serializeCalcNote(n))
        // a REAL newline reaching the parse directly (an unfolded legacy
        // line) still folds to the plain space — only the ⏎ marker carries
        // the visual break
        val legacy = parseCalcNote("555\n5")
        assertEquals(560.0, evalCalcTokens(legacy.tokens)!!, 0.0)
        assertEquals("555 + 5", serializeCalcNote(legacy))
    }

    @Test
    fun the_per_line_currency_marker_rides_the_note() {
        // v1.6.49 report #1: the line's OWN display currency rides a
        // trailing " ~USD" — bookkeeping, never part of the calculation or
        // its labels
        val n = parseCalcNote("ball 100 price ~USD")
        assertEquals("USD", n.currency)
        assertEquals(100.0, evalCalcTokens(n.tokens)!!, 0.0)
        assertEquals("ball 100 price ~USD", serializeCalcNote(n))
        // absent → null (the entry's sum currency / the live setting)
        assertNull(parseCalcNote("ball 100 price").currency)
        // the ledger round-trip keeps one line's choice off the others
        val ledger = parseCalcLedger("100 ~USD\n200")
        assertEquals("USD", ledger[0].currency)
        assertNull(ledger[1].currency)
        assertEquals("100 ~USD\n200", serializeCalcLedger(ledger))
        // a pure annotation line carries its currency too
        val note = parseCalcNote("just words ~EUR")
        assertEquals("EUR", note.currency)
        assertEquals("just words", note.text)
    }

    @Test
    fun the_break_fold_is_index_aligned_for_the_qwerty_marks() {
        // v1.6.49: the editor folds real newlines to the marker 1:1 BEFORE
        // the mode-split, so QWERTY indices stay valid through it
        assertEquals("555\u23CE5", calcBreakFold("555\n5"))
        assertEquals("no newlines", calcBreakFold("no newlines"))
        val split = applyModeSplit(calcBreakFold("555\n5"), setOf(4))
        assertEquals("555\u23CE\"5\"", split)
        val n = parseCalcNote(split)
        assertEquals(555.0, evalCalcTokens(n.tokens)!!, 0.0)
}
}
