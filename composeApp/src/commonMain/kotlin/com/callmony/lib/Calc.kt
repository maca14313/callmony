package com.callmony.lib

import com.callmony.i18n.t
import com.callmony.model.CalcList
import com.callmony.model.EntryItem
import com.callmony.model.LedgerEntry
import com.callmony.model.ListItem
import com.callmony.model.Payment
import com.callmony.model.Repeat
import kotlin.math.abs
import kotlin.math.roundToLong

/* callmony — money math. Exact port of src/lib/calc.ts. */

/** Round to 2 decimals — used on every money computation. */
fun r2(n: Double): Double {
    val scaled = (n * 100).roundToLong() / 100.0
    return if (scaled == 0.0) 0.0 else scaled // normalize -0.0
}

fun sumPayments(pays: List<Payment>?): Double = r2(pays.orEmpty().sumOf { it.amount })

/* ---------------- Ledger ---------------- */

/** remaining on an entry; negative = overpaid → direction flips */
fun entryRemaining(e: LedgerEntry): Double = r2(e.amount - sumPayments(e.payments))

/** signed remaining: credit positive (they owe you), debit negative */
fun entrySignedRemaining(e: LedgerEntry): Double = r2((if (e.type == "credit") 1 else -1) * entryRemaining(e))

/** Balance for an owner (contact or 'general'). */
fun ownerBalance(entries: List<LedgerEntry>, ownerId: String): Double =
    r2(entries.filter { it.ownerId == ownerId && it.deletedAt == null }.sumOf { entrySignedRemaining(it) })

/** Pending from calculating lists that count in the owner's balance. */
fun openListsPending(lists: List<CalcList>, ownerId: String): Double =
    r2(lists.filter { it.ownerId == ownerId && it.deletedAt == null && it.postToLedger && !it.isTemplate }
        .sumOf { listTotals(it).pending })

/** Full balance incl. open lists. */
fun ownerFullBalance(entries: List<LedgerEntry>, lists: List<CalcList>, ownerId: String): Double =
    r2(ownerBalance(entries, ownerId) + openListsPending(lists, ownerId))

/** v1.6.16 — every owner's full balance in ONE pass (entries + open lists).
 *  For lists of rows that each show a balance (keypad live matches, search
 *  hits) this turns O(rows × entries) per keystroke into a single O(n + m)
 *  build + O(1) lookups — the per-row full scan was a big slice of the
 *  keypad's typing lag on real books. */
fun ownerBalances(entries: List<LedgerEntry>, lists: List<CalcList>): Map<String, Double> {
    val out = HashMap<String, Double>()
    for (e in entries) {
        if (e.deletedAt != null) continue
        val k = e.ownerId
        out[k] = r2((out[k] ?: 0.0) + entrySignedRemaining(e))
    }
    for (l in lists) {
        if (l.deletedAt != null || !l.postToLedger || l.isTemplate) continue
        val k = l.ownerId
        out[k] = r2((out[k] ?: 0.0) + listTotals(l).pending)
    }
    return out
}

data class OwnerTotals(val credit: Double, val debit: Double, val net: Double)

fun totalsForOwner(entries: List<LedgerEntry>, ownerId: String): OwnerTotals {
    val mine = entries.filter { it.ownerId == ownerId && it.deletedAt == null }
    val credit = r2(mine.filter { it.type == "credit" }.sumOf { entrySignedRemaining(it) })
    val debit = r2(mine.filter { it.type == "debit" }.sumOf { -entrySignedRemaining(it) })
    return OwnerTotals(credit, debit, r2(credit - debit))
}

data class GroupTotals(val count: Int, val net: Double, val remaining: Double, val paid: Double)

fun groupTotals(entries: List<LedgerEntry>, groupId: String): GroupTotals {
    val members = entries.filter { it.groupId == groupId && it.deletedAt == null }
    val net = r2(members.sumOf { (if (it.type == "credit") 1 else -1) * it.amount })
    val remaining = r2(members.sumOf { entrySignedRemaining(it) })
    val paid = r2(members.sumOf { sumPayments(it.payments) })
    return GroupTotals(members.size, net, remaining, paid)
}

/** Σ line totals of an entry's item lines (qty × unit price). */
fun itemsTotal(items: List<EntryItem>?): Double =
    r2(items.orEmpty().sumOf { it.qty * it.price })

/** Σ quantities — "8 items" when the lines are 3 + 4 + 1. */
fun itemsCount(items: List<EntryItem>?): Double = items.orEmpty().sumOf { it.qty }

/* ---------------- Calculating lists ---------------- */

/** line total = qty × price − row discount + row tax */
fun lineTotal(i: ListItem): Double {
    val base = r2(i.qty * i.unitPrice)
    var t = base
    i.discount?.let { d ->
        t = r2(t - (if (d.type == "pct") r2(base * d.value / 100) else d.value))
    }
    i.taxPct?.let { pct -> t = r2(t + r2(t * pct / 100)) }
    return maxOf(t, 0.0)
}

fun itemPaid(i: ListItem): Double = sumPayments(i.payments)

fun itemRemaining(i: ListItem): Double = r2(lineTotal(i) - itemPaid(i))

data class ListTotals(
    val subtotal: Double,
    val listDiscount: Double,
    val listTax: Double,
    val extra: Double,
    val grand: Double,
    val paid: Double,
    val pending: Double,
    val count: Int,
    val paidCount: Int,
)

fun listTotals(l: CalcList): ListTotals {
    val subtotal = r2(l.items.sumOf { lineTotal(it) })
    var listDiscount = 0.0
    l.discount?.let { d ->
        listDiscount = if (d.type == "pct") r2(subtotal * d.value / 100) else d.value
    }
    val afterDiscount = r2(subtotal - listDiscount)
    val listTax = l.taxPct?.let { r2(afterDiscount * it / 100) } ?: 0.0
    val extra = l.extraCharge ?: 0.0
    val grand = r2(afterDiscount + listTax + extra)
    val paid = r2(l.items.sumOf { itemPaid(it) })
    val pending = r2(grand - paid)
    val count = l.items.size
    val paidCount = l.items.count { itemRemaining(it) <= 0.005 && lineTotal(it) > 0 } +
        l.items.count { lineTotal(it) == 0.0 && it.checked }
    return ListTotals(subtotal, listDiscount, listTax, extra, grand, paid, pending, count, paidCount)
}

/** group rows with same name+unit for display (raw rows stay intact) */
fun groupItems(items: List<ListItem>): List<List<ListItem>> {
    val map = LinkedHashMap<String, MutableList<ListItem>>()
    for (i in items) {
        val key = "${i.name.trim().lowercase()}|${(i.unit ?: "").trim().lowercase()}"
        map.getOrPut(key) { mutableListOf() }.add(i)
    }
    return map.values.toList()
}

/* ---------------- Reminder recurrence ---------------- */

private fun sameDay(a: KmpDate, b: KmpDate): Boolean =
    a.year == b.year && a.month0 == b.month0 && a.day == b.day

/* KmpDate auto-normalizes out-of-range fields (month 13 → Jan next year,
   day 0 → last day of previous month), so "day 0 of next month" gives the
   number of days in a month, exactly like new Date(y, m+1, 0) in JS. */
private fun daysInMonth(year: Int, month0: Int): Int =
    KmpDate(year, month0 + 1, 0).day

/** Next occurrence strictly after `from` for a repeating reminder.
 *  v1.6.18: understands custom intervals ("custom:<n><unit>", unit ∈
 *  m minutes | h hours | d days | w weeks | mo months) on top of the
 *  five fixed repeats — "a repeat reminder that works in ANY interval". */
fun nextOccurrence(dueAt: Long, repeat: Repeat, from: Long = now()): Long {
    if (repeat == "none") return dueAt
    val custom = parseCustomRepeat(repeat)
    if (custom != null && (custom.second == "m" || custom.second == "h")) {
        // sub-day intervals are pure epoch arithmetic
        val stepMs = if (custom.second == "m") custom.first * 60_000L else custom.first * 3_600_000L
        var t = dueAt
        var guard = 0
        while (t <= from && guard < 100_000) { t += stepMs; guard++ }
        return t
    }
    var next = dateOf(dueAt)
    val f = dateOf(from)
    var guard = 0
    while (next.time() <= from && guard < 500) {
        guard++
        next = when {
            repeat == "daily" -> next.addDays(1)
            repeat == "weekly" -> next.addDays(7)
            repeat == "monthly" -> {
                val day = next.day
                val firstOfNext = KmpDate(next.year, next.month0 + 1, 1)
                val dim = KmpDate(firstOfNext.year, firstOfNext.month0 + 1, 0).day
                KmpDate(firstOfNext.year, firstOfNext.month0, minOf(day, dim), next.hour, next.minute, next.second)
            }
            repeat == "yearly" -> {
                val day = next.day
                val month = next.month0
                val n = KmpDate(next.year + 1, month, 1)
                val dim = KmpDate(n.year, n.month0 + 1, 0).day
                KmpDate(n.year, n.month0, minOf(day, dim), next.hour, next.minute, next.second)
            }
            custom != null -> when (custom.second) {
                // every N days / N weeks
                "d" -> next.addDays(custom.first)
                "w" -> next.addDays(custom.first * 7)
                // every N months (day clamped, like the fixed monthly branch)
                else -> {
                    val total = next.month0 + custom.first
                    val y = next.year + total / 12
                    val m = total % 12
                    val dim = daysInMonth(y, m)
                    KmpDate(y, m, minOf(next.day, dim), next.hour, next.minute, next.second)
                }
            }
            else -> return next.time()
        }
    }
    if (repeat == "daily" && sameDay(next, f) && next.time() <= from) {
        next = next.addDays(1)
    }
    return next.time()
}

/** v1.6.18 — parse "custom:<n><unit>" (m|h|d|w|mo). Null when not custom
 *  or malformed. n must be a positive integer. */
fun parseCustomRepeat(repeat: String): Pair<Int, String>? {
    if (!repeat.startsWith("custom:")) return null
    val body = repeat.removePrefix("custom:")
    val unit: String = when {
        body.endsWith("mo") -> "mo"
        body.length >= 2 && (body.endsWith("m") || body.endsWith("h") || body.endsWith("d") || body.endsWith("w")) ->
            body.takeLast(1)
        else -> return null
    }
    val digits = body.dropLast(if (unit == "mo") 2 else 1)
    val n = digits.toIntOrNull() ?: return null
    if (n <= 0) return null
    return n to unit
}

/** v1.6.18 — human label for any repeat value, used on reminder cards and
 *  the editor summary ("Every 2 weeks", "Every 90 minutes"). */
fun repeatLabelOf(repeat: String): String {
    return when (repeat) {
        "none" -> ""
        "daily" -> t("rm.repeatDaily")
        "weekly" -> t("rm.repeatWeekly")
        "monthly" -> t("rm.repeatMonthly")
        "yearly" -> t("rm.repeatYearly")
        else -> {
            val c = parseCustomRepeat(repeat) ?: return ""
            val unit = when (c.second) {
                "m" -> if (c.first == 1) t("rm.unitMinute") else t("rm.unitMinutes")
                "h" -> if (c.first == 1) t("rm.unitHour") else t("rm.unitHours")
                "d" -> if (c.first == 1) t("rm.unitDay") else t("rm.unitDays")
                "w" -> if (c.first == 1) t("rm.unitWeek") else t("rm.unitWeeks")
                else -> if (c.first == 1) t("rm.unitMonth") else t("rm.unitMonths")
            }
            "${t("rm.everyN")} ${c.first} $unit"
        }
    }
}

/** Due status for display */
fun dueStatus(dueAt: Long, done: Boolean = false): String {
    if (done) return "upcoming"
    val nowD = dateOf(now())
    val d = dateOf(dueAt)
    if (d.time() <= nowD.time()) return "overdue"
    if (sameDay(d, nowD)) return "today"
    return "upcoming"
}
