package com.callmony.lib

import org.junit.Test
import java.util.Calendar
import kotlin.random.Random

/* The shared pure-Kotlin civil calendar (KmpDate/dateOf) must behave exactly
   like the java.util.Calendar implementation it replaced on Android. */

class KmpDateTest {

    @Test
    fun matchesJavaCalendarOverRandomInstants() {
        val rnd = Random(42)
        repeat(300) {
            val epoch = rnd.nextLong(0L, 1_900_000_000_000L)
            val cal = Calendar.getInstance()
            cal.timeInMillis = epoch
            val d = dateOf(epoch)
            check(d.year == cal.get(Calendar.YEAR)) { "year $epoch: ${d.year} vs ${cal.get(Calendar.YEAR)}" }
            check(d.month0 == cal.get(Calendar.MONTH)) { "month $epoch" }
            check(d.day == cal.get(Calendar.DAY_OF_MONTH)) { "day $epoch" }
            check(d.hour == cal.get(Calendar.HOUR_OF_DAY)) { "hour $epoch" }
            check(d.minute == cal.get(Calendar.MINUTE)) { "minute $epoch" }
            check(d.second == cal.get(Calendar.SECOND)) { "second $epoch" }
        }
    }

    @Test
    fun constructAndRoundTrip() {
        val d = KmpDate(2024, 9, 24, 17, 45, 30) // month0=9 -> October
        check(d.year == 2024 && d.month0 == 9 && d.day == 24 && d.hour == 17 && d.minute == 45 && d.second == 30)
        val back = dateOf(d.time())
        check(back.year == 2024 && back.month0 == 9 && back.day == 24 && back.hour == 17 && back.minute == 45)
    }

    @Test
    fun lenientNormalization() {
        // day 0 of March = last day of February (2024 is a leap year)
        val feb29 = KmpDate(2024, 2, 0)
        check(feb29.month0 == 1 && feb29.day == 29) { "got ${feb29.month0}/${feb29.day}" }
        // month0=12 -> January of next year
        val jan = KmpDate(2024, 12, 15)
        check(jan.year == 2025 && jan.month0 == 0 && jan.day == 15)
        // month0=-1 -> December of previous year
        val dec = KmpDate(2024, -1, 15)
        check(dec.year == 2023 && dec.month0 == 11 && dec.day == 15)
        // last day of a 30-day month
        val apr30 = KmpDate(2024, 4, 0)
        check(apr30.month0 == 3 && apr30.day == 30)
    }

    @Test
    fun addDaysAcrossMonthAndYear() {
        val d2 = KmpDate(2024, 0, 31).addDays(1)
        check(d2.month0 == 1 && d2.day == 1) { "got ${d2.month0}/${d2.day}" }
        val ny = KmpDate(2024, 11, 31).addDays(1)
        check(ny.year == 2025 && ny.month0 == 0 && ny.day == 1)
        val back = KmpDate(2024, 2, 1).addDays(-1)
        check(back.month0 == 1 && back.day == 29)
    }
}
