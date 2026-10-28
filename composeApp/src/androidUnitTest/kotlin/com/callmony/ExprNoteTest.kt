package com.callmony

import com.callmony.lib.parseExprNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/* v1.6.35 — the EXPRESSION-NOTE parser (the annotated calculator's
   engine). The user writes math as a sentence — "ball 100 x 2 price +
   300 transport = total 500" — and parseExprNote extracts just the
   arithmetic, live. This is the battery that pins every rule:
   labels before/after numbers, the '=' cut, precedence, glued operators,
   words containing x, dangling operators while typing, implicit +,
   decimals/comma, Arabic digits, ÷0, and the no-numbers case. */

class ExprNoteTest {

    @Test
    fun the_user_example_parses_to_500() {
        val r = parseExprNote("ball 100 ball x 2 price + 300 transport = total 500")
        assertTrue(r.ok)
        assertEquals(500.0, r.value!!, 0.0)
        assertEquals(3, r.terms)
        assertEquals("100 × 2 + 300", r.expr)
    }

    @Test
    fun labels_before_and_after_numbers() {
        val r = parseExprNote("rent 1200 deposit")
        assertTrue(r.ok)
        assertEquals(1200.0, r.value!!, 0.0)
        assertEquals(1, r.terms)
    }

    @Test
    fun everything_after_the_first_equals_is_ignored() {
        // "= 500" is the user's own written total — it must never fold in
        val r = parseExprNote("100 + 200 = 300 birr cash")
        assertTrue(r.ok)
        assertEquals(300.0, r.value!!, 0.0)
        // and a lone result with nothing before it is not a calculation
        assertFalse(parseExprNote("= 500").ok)
    }

    @Test
    fun multiplication_binds_tighter_than_addition() {
        assertEquals(20.0, parseExprNote("10 + 2 x 5").value!!, 0.0)
        assertEquals(25.0, parseExprNote("10 x 2 + 5").value!!, 0.0)
        assertEquals(14.0, parseExprNote("2 + 3 x 4").value!!, 0.0)
    }

    @Test
    fun glued_operators_split_between_digits() {
        assertEquals(400.0, parseExprNote("fee 100+300").value!!, 0.0)
        assertEquals(4.0, parseExprNote("2x2").value!!, 0.0)
        assertEquals(5.0, parseExprNote("10/2").value!!, 0.0)
        assertEquals(42.0, parseExprNote("6×7").value!!, 0.0)
        assertEquals(25.0, parseExprNote("100÷4").value!!, 0.0)
        assertEquals(1.0, parseExprNote("5-3-1").value!!, 0.0)
    }

    @Test
    fun words_containing_x_are_not_operators() {
        val r = parseExprNote("taxi 100 + 50 tip")
        assertTrue(r.ok)
        assertEquals(150.0, r.value!!, 0.0)
        assertEquals(2, r.terms)
    }

    @Test
    fun dangling_operators_while_typing_are_ignored() {
        assertEquals(100.0, parseExprNote("ball 100 +").value!!, 0.0)
        assertEquals(100.0, parseExprNote("ball 100 x").value!!, 0.0)
        assertEquals(100.0, parseExprNote("+ 100").value!!, 0.0)
    }

    @Test
    fun adjacent_numbers_fold_as_an_explicit_plus() {
        val r = parseExprNote("100 200")
        assertTrue(r.ok)
        assertEquals(300.0, r.value!!, 0.0)
        // the extraction SHOWS the implicit fold — never a surprise
        assertEquals("100 + 200", r.expr)
    }

    @Test
    fun decimals_and_comma_as_decimal_separator() {
        assertEquals(3.25, parseExprNote("1,5 x 2 + 0.25").value!!, 0.0)
        assertEquals(2.5, parseExprNote("1.25 + 1,25").value!!, 0.0)
        assertEquals("1.5 × 2 + 0.25", parseExprNote("1.5 x 2 + 0.25").expr)
    }

    @Test
    fun arabic_indic_digits_normalize() {
        // ١٠٠ = 100, ٥٠ = 50 — Arabic keyboards type these natively
        val r = parseExprNote("شحن ١٠٠ + ٥٠")
        assertTrue(r.ok)
        assertEquals(150.0, r.value!!, 0.0)
    }

    @Test
    fun negative_terms_and_results() {
        assertEquals(-50.0, parseExprNote("-100 + 50").value!!, 0.0)
        val r = parseExprNote("100 - 300")
        assertTrue(r.ok)
        assertEquals(-200.0, r.value!!, 0.0)
        // Save stays disabled for <= 0 downstream; the parse itself is fine
    }

    @Test
    fun no_numbers_is_not_ok() {
        assertFalse(parseExprNote("hello world").ok)
        assertFalse(parseExprNote("").ok)
        assertFalse(parseExprNote("   ").ok)
        assertNull(parseExprNote("lunch with Sam").value)
    }

    @Test
    fun division_by_zero_drops_the_operator_instead_of_exploding() {
        val r = parseExprNote("100 ÷ 0")
        assertTrue(r.ok)
        assertEquals(100.0, r.value!!, 0.0)
    }

    @Test
    fun chains_fold_left_to_right() {
        assertEquals(24.0, parseExprNote("2 x 3 x 4").value!!, 0.0)
        assertEquals(50.0, parseExprNote("1000 / 4 / 5").value!!, 0.0)
    }

    @Test
    fun minus_displays_as_a_true_minus_glyph() {
        assertEquals("100 − 300", parseExprNote("100 - 300").expr)
    }
}
