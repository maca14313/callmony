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

/* ---------------- Annotated expressions (v1.6.35) ---------------- */

/** v1.6.35 — what parseExprNote found in a free-form annotated
 *  calculation. `expr` is the canonical extraction ("100 × 2 + 300"),
 *  `value` the live total. Pure + deterministic: same text, same result,
 *  harness-testable with zero Compose. */
data class ExprNoteResult(
    val terms: Int,      // how many numbers participate
    val expr: String,    // canonical extraction; "" when nothing found
    val value: Double?,  // the live total; null when nothing computable
) {
    val ok: Boolean get() = value != null && terms > 0 && value.isFinite()
}

/** v1.6.35 — the EXPRESSION-NOTE parser (the "annotated calculator"):
 *  the user writes math in plain sentences, labels before or after every
 *  number — "ball 100 x 2 price + 300 transport = total 500" — and this
 *  picks out just the arithmetic, live, while they type.
 *
 *  Rules (forgiving by design, deterministic, pure):
 *   - everything after the first '=' is the user's own written result and
 *     is NEVER parsed (so "= total 500" cannot double the total)
 *   - Arabic-Indic (٠-٩) and Eastern (۰-۹) digits are normalized to Latin
 *     first, so Arabic-keyboard users type numbers natively
 *   - GLUED operators split only BETWEEN digits ("2x2", "100+300", "10/2",
 *     "100−50"); lookarounds keep the digits, so chains like "5-3-1" work
 *     and words like "taxi" or "no-cost" never match
 *   - a standalone token of + − - × x X * ÷ / is an operator only when it
 *     sits BETWEEN two numbers; anything else is label text, skipped
 *   - any other token that parses as a number participates — comma counts
 *     as the decimal separator, exactly like every amount field in the app
 *   - two numbers with no operator between them fold as an implicit "+"
 *     (the extracted expression SHOWS it — nothing is ever a surprise)
 *   - precedence: × ÷ bind tighter than + −, folded left to right
 *   - dangling operators while typing ("100 +") are simply ignored — the
 *     total is always the best complete parse of what's there
 *   - ÷ by 0 drops the operator instead of exploding (100 ÷ 0 → 100 + 0) */
fun parseExprNote(raw: String): ExprNoteResult {
    if (raw.isBlank()) return ExprNoteResult(0, "", null)

    // 1 — the user's own stated total (after the first '=') is not math
    val body = raw.substringBefore('=')

    // 2 — Arabic-Indic / Eastern digits → Latin (AR keyboards)
    val latin = StringBuilder(body.length)
    for (ch in body) {
        val c = ch.code
        latin.append(
            when (c) {
                in 0x0660..0x0669 -> ('0' + (c - 0x0660))
                in 0x06F0..0x06F9 -> ('0' + (c - 0x06F0))
                else -> ch
            }
        )
    }

    // 3 — split glued operators, but only between digits (lookarounds
    //     keep the digits so "5-3-1" becomes "5 - 3 - 1", not "5 - 3-1")
    val spaced = latin.toString()
        .replace(Regex("(?<=\\d)\\s*[xX×*]\\s*(?=\\d)"), " × ")
        .replace(Regex("(?<=\\d)\\s*[÷/]\\s*(?=\\d)"), " ÷ ")
        .replace(Regex("(?<=\\d)\\s*[-−]\\s*(?=\\d)"), " - ")
        .replace(Regex("(?<=\\d)\\s*\\+\\s*(?=\\d)"), " + ")

    // 4 — tokenize: numbers and standalone operators participate, every
    //     other token is a label and is skipped
    val seq = ArrayList<Any>(16)
    for (tok in spaced.split(Regex("\\s+"))) {
        if (tok.isEmpty()) continue
        when (tok) {
            "+" -> seq.add('+')
            "-", "−" -> seq.add('-')
            "×", "*", "x", "X" -> seq.add('×')
            "÷", "/" -> seq.add('÷')
            else -> {
                val n = tok.replace(',', '.').toDoubleOrNull()
                if (n != null && n.isFinite()) seq.add(n)
            }
        }
    }

    // 5 — an operator only counts BETWEEN two numbers (a dangling "+" —
    //     mid-typing — or a leading one is just text)
    val cleaned = ArrayList<Any>(seq.size)
    for (i in seq.indices) {
        val el = seq[i]
        if (el is Double) { cleaned.add(el); continue }
        val prevOk = i > 0 && seq[i - 1] is Double
        val nextOk = i + 1 < seq.size && seq[i + 1] is Double
        if (prevOk && nextOk) cleaned.add(el)
    }
    if (cleaned.isEmpty()) return ExprNoteResult(0, "", null)

    // 6 — fold × ÷ first (left to right); ÷0 drops the op, never explodes
    val folded = ArrayList<Any>(cleaned.size)
    var i = 0
    while (i < cleaned.size) {
        val el = cleaned[i]
        if (el is Char && (el == '×' || el == '÷') &&
            folded.isNotEmpty() && folded.last() is Double &&
            i + 1 < cleaned.size && cleaned[i + 1] is Double
        ) {
            val a = folded.removeAt(folded.size - 1) as Double
            val b = cleaned[i + 1] as Double
            if (el == '÷' && b == 0.0) {
                folded.add(a); folded.add(b) // ÷0 → the op is dropped
            } else {
                folded.add(r2(if (el == '×') a * b else a / b))
            }
            i += 2
        } else { folded.add(el); i += 1 }
    }

    // 7 — fold + − left to right; adjacent numbers fold as an implicit +
    var acc: Double? = null
    var op: Char? = null
    for (el in folded) {
        if (el is Double) {
            val cur = acc
            acc = when {
                cur == null -> el
                op == '-' -> r2(cur - el)
                else -> r2(cur + el) // '+' or implicit + between numbers
            }
            op = null
        } else if (el is Char && acc != null) {
            op = el
        }
    }

    // 8 — the display extraction keeps the user's own terms and glyphs
    //     ("100 × 2 + 300"), synthesizing the "+" of an implicit fold;
    //     terms are counted from the VALIDATED sequence, so "100 × 2" is
    //     still two user terms even though the fold collapsed them
    val terms = cleaned.count { it is Double }
    val parts = ArrayList<String>(cleaned.size)
    var prevNum = false
    for (el in cleaned) {
        when (el) {
            is Double -> {
                if (prevNum) parts.add("+")
                parts.add(fmtTerm(el))
                prevNum = true
            }
            else -> {
                parts.add(if (el == '-') "−" else el.toString())
                prevNum = false
            }
        }
    }

    val total = acc
    return ExprNoteResult(terms, parts.joinToString(" "), total)
}

/** Term formatter for the extracted expression — whole numbers without a
 *  trailing ".0" ("100", not "100.0"), decimals as typed. */
private fun fmtTerm(d: Double): String =
    if (d == d.toLong().toDouble() && abs(d) < 1e15) d.toLong().toString() else d.toString()

/* ---------------- Annotated calculator v2 (v1.6.36) ---------------- */

/** v1.6.36 — ONE annotated number of the interlocking chip row, exactly as
 *  the user's updated spec draws it: a label slot BEFORE the number, the
 *  editable number itself, a label slot AFTER it, and the operator that
 *  follows this number (null on the chain's last number). */
data class CalcToken(
    val before: String = "",  // "ball"
    val number: String,       // "100" (editable text; comma normalized to dot)
    val after: String = "",   // "price in birr"
    val op: String? = null,   // "+", "-", "×", "÷" — the op AFTER this number
)

/** v1.6.36 — the whole annotated calculation as structured data: the number
 *  chain + the label slots around the RESULT chip. This is the editor's
 *  source of truth; it serializes back to the SAME free-text grammar the
 *  v1.6.35 parser already speaks, so old notes open as chips and new chips
 *  stay plain readable text on the saved entry. */
data class CalcNote(
    val tokens: List<CalcToken> = emptyList(),
    val resultBefore: String = "",  // label before the total ("total")
    val resultAfter: String = "",   // label after the total ("birr")
)

/** v1.6.36 — one live intermediate result for the step-by-step card
 *  ("100 × 2" → 200, then "200 + 300" → 500). */
data class CalcStep(val expr: String, val value: Double)

private fun latinizeDigits(s: String): String {
    val sb = StringBuilder(s.length)
    for (ch in s) {
        val c = ch.code
        sb.append(
            when (c) {
                in 0x0660..0x0669 -> ('0' + (c - 0x0660))
                in 0x06F0..0x06F9 -> ('0' + (c - 0x06F0))
                else -> ch
            }
        )
    }
    return sb.toString()
}

/** v1.6.36 — parse a free-text annotated note (v1.6.35 grammar) into the
 *  structured chip model. Labels before/after every number stick to their
 *  number; glued operators split between digits; everything after the
 *  first '=' is the result zone — its words become the result labels
 *  around the written total (never part of the math). Deterministic and
 *  pure: the same text always yields the same chips. */
fun parseCalcNote(raw: String): CalcNote {
    if (raw.isBlank()) return CalcNote()

    val body = raw.substringBefore('=')
    val tail = if (raw.contains('=')) raw.substringAfter('=').trim() else ""

    // split glued operators between digits ("2x2", "100+300") after
    // normalizing Arabic-Indic / Eastern digits — same rules as parseExprNote
    val spaced = latinizeDigits(body)
        .replace(Regex("(?<=\\d)\\s*[xX×*]\\s*(?=\\d)"), " × ")
        .replace(Regex("(?<=\\d)\\s*[÷/]\\s*(?=\\d)"), " ÷ ")
        .replace(Regex("(?<=\\d)\\s*[-−]\\s*(?=\\d)"), " - ")
        .replace(Regex("(?<=\\d)\\s*\\+\\s*(?=\\d)"), " + ")

    val tokens = mutableListOf<CalcToken>()
    var before = mutableListOf<String>()  // words waiting for their number
    var after = mutableListOf<String>()   // words collected after a number
    var curNum: String? = null            // the number being annotated
    var curOp: String? = null             // op already set for it

    fun flush() {
        val n = curNum ?: return
        tokens.add(
            CalcToken(
                before = before.joinToString(" "),
                number = n,
                after = after.joinToString(" "),
                op = curOp,
            )
        )
        before = mutableListOf()
        after = mutableListOf()
        curNum = null
        curOp = null
    }

    for (tok in spaced.split(Regex("\\s+"))) {
        if (tok.isEmpty()) continue
        when (tok) {
            "+", "-", "−", "×", "*", "x", "X", "÷", "/" -> {
                val op = when (tok) {
                    "+", "-" -> tok
                    "−" -> "-"
                    "×", "*", "x", "X" -> "×"
                    else -> "÷"
                }
                if (curNum != null) {
                    // an operator BETWEEN two numbers terminates the token
                    curOp = op
                    flush()
                }
                // a dangling/leading operator is ignored (old rule)
            }
            else -> {
                val n = latinizeDigits(tok).replace(',', '.').toDoubleOrNull()
                if (n != null && n.isFinite()) {
                    if (curNum != null) {
                        // adjacent numbers: the fold shows an explicit "+"
                        curOp = "+"
                        flush()
                    }
                    curNum = latinizeDigits(tok).replace(',', '.')
                } else if (curNum != null) {
                    after.add(tok)
                } else {
                    before.add(tok)
                }
            }
        }
    }
    flush()

    // the result zone: "= total 500 birr" → before="total", after="birr".
    // No number in the tail → all the words become the trailing label.
    var resBefore = ""
    var resAfter = ""
    if (tail.isNotEmpty()) {
        val words = tail.split(Regex("\\s+")).filter { it.isNotEmpty() }
        var seenNumber = false
        val pre = mutableListOf<String>()
        val post = mutableListOf<String>()
        for (w in words) {
            val n = latinizeDigits(w).replace(',', '.').toDoubleOrNull()
            if (!seenNumber && n != null && n.isFinite()) seenNumber = true
            else if (!seenNumber) pre.add(w) else post.add(w)
        }
        resBefore = pre.joinToString(" ")
        resAfter = post.joinToString(" ")
    }

    return CalcNote(tokens, resBefore, resAfter)
}

/** v1.6.36 — chips → the free-text grammar. Byte-stable round-trip for
 *  anything the chip editor can produce ("ball 100 × 2 price + 300
 *  transport = total 500 birr"); the result zone is only written when the
 *  user annotated the total (labels exist), never by default. */
fun serializeCalcNote(n: CalcNote): String {
    val sb = StringBuilder()
    n.tokens.forEachIndexed { i, tk ->
        if (i > 0) sb.append(' ')
        if (tk.before.isNotBlank()) sb.append(tk.before.trim()).append(' ')
        sb.append(tk.number)
        if (tk.after.isNotBlank()) sb.append(' ').append(tk.after.trim())
        tk.op?.let { sb.append(' ').append(it) }
    }
    if (n.tokens.isNotEmpty() && (n.resultBefore.isNotBlank() || n.resultAfter.isNotBlank())) {
        val total = evalCalcTokens(n.tokens) ?: 0.0
        sb.append(" = ")
        if (n.resultBefore.isNotBlank()) sb.append(n.resultBefore.trim()).append(' ')
        sb.append(fmtTerm(total))
        if (n.resultAfter.isNotBlank()) sb.append(' ').append(n.resultAfter.trim())
    }
    return sb.toString().trim()
}

/** v1.6.36 — the live total of the chip chain. Same semantics as
 *  parseExprNote: × ÷ bind tighter than + − (folded left to right), a
 *  dangling trailing op is ignored, ÷0 drops the operator, and a missing
 *  op between numbers folds as an explicit "+". Null when nothing
 *  computes. */
fun evalCalcTokens(tokens: List<CalcToken>): Double? {
    val nums = ArrayList<Double>()
    val ops = ArrayList<Char>()
    var lastValidIdx = -1
    tokens.forEachIndexed { i, tk ->
        val v = tk.number.replace(',', '.').toDoubleOrNull() ?: return@forEachIndexed
        if (lastValidIdx >= 0) {
            // the op that separated the previous valid number from this one
            val prevOp = tokens.getOrNull(lastValidIdx)?.op
            ops.add(prevOp?.get(0) ?: '+')
        }
        nums.add(v)
        lastValidIdx = i
    }
    return evalChain(nums, ops)
}

private fun evalChain(nums: List<Double>, ops: List<Char>): Double? {
    if (nums.isEmpty()) return null
    val vs = nums.toMutableList()
    val os = ops.toMutableList()
    // fold × ÷ first (left to right); ÷0 drops the operator
    var i = 0
    while (i < os.size) {
        val op = os[i]
        if ((op == '×' || op == '÷') && i < vs.size - 1) {
            val a = vs[i]
            val b = vs[i + 1]
            if (op == '÷' && b == 0.0) {
                os.removeAt(i)
                continue
            }
            vs[i] = r2(if (op == '×') a * b else a / b)
            vs.removeAt(i + 1)
            os.removeAt(i)
        } else i++
    }
    // fold + − left to right (adjacent numbers fold as +)
    var acc = vs[0]
    for (j in os.indices) {
        val b = vs[j + 1]
        acc = if (os[j] == '-') r2(acc - b) else r2(acc + b)
    }
    return acc
}

/** v1.6.36 — the STEP-BY-STEP intermediate results the user's spec draws:
 *  every × ÷ fold is one step ("100 × 2" → 200), then every + − fold is
 *  one ("200 + 300" → 500) — the running display of the user's own
 *  example. Empty for a single bare number (nothing intermediate). */
fun calcSteps(tokens: List<CalcToken>): List<CalcStep> {
    val nums = ArrayList<Double>()
    val ops = ArrayList<Char>()
    var lastValidIdx = -1
    tokens.forEachIndexed { i, tk ->
        val v = tk.number.replace(',', '.').toDoubleOrNull() ?: return@forEachIndexed
        if (lastValidIdx >= 0) {
            val prevOp = tokens.getOrNull(lastValidIdx)?.op
            ops.add(prevOp?.get(0) ?: '+')
        }
        nums.add(v)
        lastValidIdx = i
    }
    if (nums.size <= 1) return emptyList()

    val steps = mutableListOf<CalcStep>()
    val vs = nums.toMutableList()
    val os = ops.toMutableList()
    // pass 1 — the × ÷ folds
    var i = 0
    while (i < os.size) {
        val op = os[i]
        if ((op == '×' || op == '÷') && i < vs.size - 1) {
            val a = vs[i]
            val b = vs[i + 1]
            if (op == '÷' && b == 0.0) {
                steps.add(CalcStep("${fmtTerm(a)} ÷ 0", a))
                os.removeAt(i)
                continue
            }
            val r = r2(if (op == '×') a * b else a / b)
            steps.add(CalcStep("${fmtTerm(a)} ${if (op == '×') "×" else "÷"} ${fmtTerm(b)}", r))
            vs[i] = r
            vs.removeAt(i + 1)
            os.removeAt(i)
        } else i++
    }
    // pass 2 — the + − folds on the running total
    if (vs.size > 1) {
        var acc = vs[0]
        for (j in os.indices) {
            val b = vs[j + 1]
            val r = if (os[j] == '-') r2(acc - b) else r2(acc + b)
            steps.add(CalcStep("${fmtTerm(acc)} ${if (os[j] == '-') "−" else "+"} ${fmtTerm(b)}", r))
            acc = r
        }
    }
    return steps
}
