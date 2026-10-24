package com.callmony

import com.callmony.ui.components.evalExpression
import com.callmony.ui.components.fmtResult
import org.junit.Assert.assertEquals
import org.junit.Test

/* v1.6.13 calculator precision — the user report: "2 × 2 is 2.000000002".
   Exact integers, no double noise, no premature scientific notation. */
class CalcFormatTest {

    @Test
    fun integers_are_exact() {
        assertEquals("4", fmtResult(2.0 * 2.0))
        assertEquals("100", fmtResult(25.0 * 4.0))
        assertEquals("0", fmtResult(0.0))
        assertEquals("-6", fmtResult(-2.0 * 3.0))
        assertEquals("1000000", fmtResult(1000.0 * 1000.0))
    }

    @Test
    fun eval_integers() {
        assertEquals(4.0, evalExpression("2×2")!!, 0.0)
        assertEquals(8.0, evalExpression("2+2×3")!!, 0.0)
        assertEquals(0.5, evalExpression("1÷2")!!, 0.0)
        assertEquals(null, evalExpression("5÷0"))
    }

    @Test
    fun double_noise_is_killed() {
        // 0.1 + 0.2 = 0.30000000000000004 in IEEE — must display "0.3"
        assertEquals("0.3", fmtResult(0.1 + 0.2))
        // 1.1 × 1.1 = 1.2100000000000002 — must display "1.21"
        assertEquals("1.21", fmtResult(1.1 * 1.1))
        // 2.0000000000000004-ish noise → clean "2"
        assertEquals("2", fmtResult(2.0000000000000004))
    }

    @Test
    fun no_premature_scientific_notation() {
        // Double.toString switches to 1.0E7 at 1e7 — the calculator must not
        assertEquals("12345678.9", fmtResult(12345678.9))
        assertEquals("123456789.1", fmtResult(123456789.1))
        // ≥1e12 stays exponential (spec, same as RN)
        assertEquals("1.0E12", fmtResult(1.0e12))
    }

    @Test
    fun division_and_percent_format_clean() {
        assertEquals("0.333333333333", fmtResult(1.0 / 3.0))
        assertEquals("0.05", fmtResult(5.0 / 100.0))
        assertEquals("12.5", fmtResult(25.0 / 2.0))
    }
}
