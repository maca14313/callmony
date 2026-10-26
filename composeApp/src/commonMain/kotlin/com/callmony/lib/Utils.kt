package com.callmony.lib

import com.callmony.model.AVATAR_PALETTE
import com.callmony.model.CURRENCIES
import com.callmony.model.DeviceContact
import com.callmony.platform.Platform
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/* callmony — utilities (dates, money, phones, misc). Exact port of the
   React Native edition's src/lib/utils.ts. */

/** The app's version (kept in sync with composeApp/build.gradle.kts). */
const val APP_VERSION = "v1.6.16"

private val RNG = kotlin.random.Random.Default

fun uid(): String =
    "${now().toString(36)}-${RNG.nextBytes(6).joinToString("") { it.toString(16) }}${RNG.nextBytes(3).joinToString("") { it.toString(16) }}"

fun now(): Long = platformClockMillis()

expect fun platformClockMillis(): Long

/* ---------------- display names (device contacts) ---------------- */

/** The number the app dials/shows first — ContactX.primaryPhone, else first phone. */
fun primaryPhone(c: DeviceContact?): String? {
    if (c == null) return null
    val x = AppStore.contactXOf(c.recordID)
    if (x?.primaryPhone != null) {
        val exact = c.phones.firstOrNull { it.number == x.primaryPhone }
        if (exact != null) return exact.number
    }
    return c.phones.firstOrNull()?.number
}

fun displayName(c: DeviceContact?): String {
    if (c == null) return "Unknown"
    val n = "${c.givenName} ${c.middleName ?: ""} ${c.familyName}"
        .replace(Regex("\\s+"), " ")
        .trim()
    return n.ifEmpty { c.nickname ?: c.company ?: c.displayName.ifEmpty { "No name" } }
}

fun initialsOf(c: DeviceContact?): String {
    val name = displayName(c)
    val parts = name.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    return parts.take(2).map { it.first() }.joinToString("").uppercase()
}

fun avatarColors(c: DeviceContact?): Pair<String, String> {
    val seed = displayName(c).firstOrNull()?.code ?: 65
    val p = AVATAR_PALETTE[seed % AVATAR_PALETTE.size]
    return p.color to p.text
}

/* ---------------- money (deterministic, no locale dependency) ---------------- */

private fun groupDigits(intPart: String): String {
    val sb = StringBuilder()
    for ((i, ch) in intPart.withIndex()) {
        if (i > 0 && (intPart.length - i) % 3 == 0) sb.append(',')
        sb.append(ch)
    }
    return sb.toString()
}

private fun numberStr(n: Double): String {
    val absV = abs(n)
    val fixed = if (absV == floor(absV) && !absV.isInfinite() && absV < 1e15) {
        val l = absV.toLong()
        l.toString()
    } else fmtFixed(absV, 2)
    val parts = fixed.split(".")
    val g = groupDigits(parts[0])
    return if (parts.size > 1) "$g.${parts[1]}" else g
}

fun currencySymbol(code: String): String =
    CURRENCIES.find { it.code == code }?.symbol ?: code

fun money(n: Double, currency: String = "ETB", signed: Boolean = false): String {
    val sym = currencySymbol(currency)
    val body = "${numberStr(n)} $sym"
    if (signed && n != 0.0) return "${if (n > 0) "+" else "−"}$body"
    return if (n < 0) "−$body" else body
}

fun compactMoney(n: Double, currency: String = "ETB", signed: Boolean = false): String {
    val absV = abs(n)
    if (absV >= 100000) {
        val v = if (absV >= 1000000) fmtFixed(absV / 1000000, 1) + "M" else fmtFixed(absV / 1000, 0) + "K"
        val sign = if (signed && n != 0.0) { if (n > 0) "+" else "−" } else if (n < 0) "−" else ""
        return "$sign$v ${currencySymbol(currency)}"
    }
    return money(n, currency, signed)
}

/* ---------------- dates (localized via i18n registry) ---------------- */

object DateNames {
    var months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    var weekdays = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    var today = "Today"
    var yesterday = "Yesterday"
}

fun setDateNames(months: List<String>, weekdays: List<String>, today: String, yesterday: String) {
    if (months.size == 12) DateNames.months = months
    if (weekdays.size == 7) DateNames.weekdays = weekdays
    if (today.isNotEmpty()) DateNames.today = today
    if (yesterday.isNotEmpty()) DateNames.yesterday = yesterday
}

/* ---------------- dates — pure-Kotlin civil calendar (proleptic Gregorian,
   Howard Hinnant's algorithms), local timezone via tzOffsetMinutes.
   Lenient like java.util.Calendar: out-of-range month/day normalize. ---------------- */

internal expect fun tzOffsetMinutes(epochMillis: Long): Int

private fun daysFromCivil(y0: Long, m: Long, d: Long): Long {
    val y = y0 - (if (m <= 2) 1 else 0)
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

private fun civilFromDays(z0: Long): Triple<Long, Long, Long> {
    val z = z0 + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + (if (mp < 10) 3 else -9)
    return Triple(y + (if (m <= 2) 1 else 0), m, d)
}

class KmpDate(y: Int, m0: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0) {
    private val millis: Long
    private val yearF: Int
    private val month0F: Int
    private val dayF: Int
    private val hourF: Int
    private val minuteF: Int
    private val secondF: Int

    init {
        // lenient: month 12 = next January, day 0 = last day of previous month…
        val months = y.toLong() * 12 + m0.toLong()
        val yy = months.floorDiv(12)
        val mm = months.mod(12L) + 1
        val days = daysFromCivil(yy, mm, 1) + (d.toLong() - 1)
        val utcGuess = days * 86_400_000L + h * 3_600_000L + mi * 60_000L + s * 1_000L
        millis = utcGuess - tzOffsetMinutes(utcGuess) * 60_000L
        val (fy, fm, fd) = fieldsOf(millis)
        yearF = fy; month0F = fm; dayF = fd
        val local = millis + tzOffsetMinutes(millis) * 60_000L
        hourF = (local.mod(86_400_000L) / 3_600_000L).toInt()
        minuteF = (local.mod(3_600_000L) / 60_000L).toInt()
        secondF = (local.mod(60_000L) / 1_000L).toInt()
    }

    val year: Int get() = yearF
    val month0: Int get() = month0F
    val day: Int get() = dayF
    val hour: Int get() = hourF
    val minute: Int get() = minuteF
    val second: Int get() = secondF

    fun time(): Long = millis

    fun addDays(n: Int): KmpDate = KmpDate(yearF, month0F, dayF + n, hourF, minuteF, secondF)
}

private fun fieldsOf(epoch: Long): Triple<Int, Int, Int> {
    val local = epoch + tzOffsetMinutes(epoch) * 60_000L
    val days = local.floorDiv(86_400_000L)
    val (y, m, d) = civilFromDays(days)
    return Triple(y.toInt(), (m - 1).toInt(), d.toInt())
}

fun dateOf(epoch: Long): KmpDate {
    val (y, m0, d) = fieldsOf(epoch)
    val local = epoch + tzOffsetMinutes(epoch) * 60_000L
    return KmpDate(y, m0, d, (local.mod(86_400_000L) / 3_600_000L).toInt(),
        (local.mod(3_600_000L) / 60_000L).toInt(), (local.mod(60_000L) / 1_000L).toInt())
}

fun todayStr(): String = dateStr(dateOf(platformClockMillis()))

fun dateStr(d: KmpDate): String {
    val p = { n: Int -> n.toString().padStart(2, '0') }
    return "${d.year}-${p(d.month0 + 1)}-${p(d.day)}"
}

private fun parseYmd(s: String): KmpDate? {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(s) ?: return null
    val (y, mo, d) = m.destructured
    return KmpDate(y.toInt(), mo.toInt() - 1, d.toInt())
}

fun fd(date: String): String {
    val d = parseYmd(date) ?: return date
    return "${DateNames.months[d.month0]} ${d.day}, ${d.year}"
}

fun fdShort(date: String): String {
    val d = parseYmd(date) ?: return date
    return "${DateNames.months[d.month0]} ${d.day}"
}

fun fdEpoch(epoch: Long): String {
    val d = dateOf(epoch)
    return "${DateNames.months[d.month0]} ${d.day}, ${d.year}"
}

fun fdShortEpoch(epoch: Long): String {
    val d = dateOf(epoch)
    return "${DateNames.months[d.month0]} ${d.day}"
}

fun ft(epoch: Long): String {
    val d = dateOf(epoch)
    val h = d.hour
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12:${d.minute.toString().padStart(2, '0')} $ampm"
}

/* v1.6.8 — the Recents day headers show ONLY "Mon D" (e.g. "Oct 24"). */
fun dayGroup(epoch: Long): String {
    val d = dateOf(epoch)
    return "${DateNames.months[d.month0]} ${d.day}"
}

fun relTime(epoch: Long): String {
    val diff = platformClockMillis() - epoch
    val m = floor(diff / 60000.0).toInt()
    if (m < 1) return "·"
    if (m < 60) return "${m}m"
    val h = m / 60
    if (h < 24) return "${h}h"
    val d = h / 24
    if (d < 7) return "${d}d"
    return fdShortEpoch(epoch)
}

fun durationStr(secs: Int): String {
    if (secs <= 0) return "—"
    val m = secs / 60
    val s = secs % 60
    if (m == 0) return "${s}s"
    return "$m:${s.toString().padStart(2, '0')}"
}

fun clockStr(secs: Int): String {
    val m = secs / 60
    val s = secs % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

/* ---------------- phones ---------------- */

val DIAL_CODES: Map<String, String> = mapOf(
    "ET" to "251", "ER" to "291", "DJ" to "253", "SO" to "252", "KE" to "254", "SD" to "249", "EG" to "20",
    "SA" to "966", "AE" to "971", "US" to "1", "GB" to "44", "CA" to "1", "DE" to "49", "FR" to "33",
    "IT" to "39", "ES" to "34", "NL" to "31", "IN" to "91", "NG" to "234", "ZA" to "27", "TZ" to "255", "UG" to "256",
)

fun dialCodeOf(countryCode: String): String = DIAL_CODES[countryCode] ?: "251"

fun normalizePhone(raw: String, countryCode: String = "ET"): String {
    var n = raw.replace(Regex("[^\\d+*#]"), "")
    if (n.isEmpty()) return ""
    if (Regex("^0+$").matches(n)) return n
    if (n.startsWith("00")) n = "+${n.substring(2)}"
    if (n.startsWith("+")) {
        val rest = n.substring(1)
        val cc = DIAL_CODES[countryCode] ?: "251"
        if (rest.startsWith(cc + "0")) return rest.substring(0, cc.length) + rest.substring(cc.length + 1)
        return rest
    }
    if (n.contains("*") || n.contains("#")) return n.replace(Regex("\\D"), "")
    val cc = DIAL_CODES[countryCode] ?: "251"
    if (n.startsWith("0")) n = n.substring(1)
    return "$cc$n"
}

/** NATIONAL significant digits — country code AND trunk zero removed. */
fun nationalDigits(rawOrDigits: String, countryCode: String = "ET"): String {
    val norm = normalizePhone(rawOrDigits, countryCode).replace(Regex("\\D"), "")
    if (norm.isEmpty()) return ""
    val cc = DIAL_CODES[countryCode] ?: "251"
    if (norm.startsWith(cc) && norm.length > cc.length) return norm.substring(cc.length).replaceFirst(Regex("^0+"), "")
    return norm
}

fun prettyPhone(raw: String, countryCode: String = "ET"): String {
    if (raw.isEmpty()) return ""
    if (raw.contains("*") || raw.contains("#")) return raw
    val digits = normalizePhone(raw, countryCode)
    if (digits.replaceFirst(Regex("^0+"), "").length < 3) return raw
    if (digits.startsWith("251") && digits.length >= 12) {
        return "+251 ${digits.substring(3, 6)} ${digits.substring(6, 9)} ${digits.substring(9)}"
    }
    if (digits.length > 8) {
        return "+${digits.substring(0, digits.length - 8)} ${digits.substring(digits.length - 8, digits.length - 4)} ${digits.substring(digits.length - 4)}"
    }
    return raw
}

val T9_MAP: Map<Char, String> = mapOf(
    '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl", '6' to "mno",
    '7' to "pqrs", '8' to "tuv", '9' to "wxyz",
)

fun t9Match(number: String, name: String): Boolean {
    if (number.isEmpty()) return true
    val letters = name.lowercase().replace(Regex("[^a-z]"), "")
    val keys = number.replace(Regex("[^2-9]"), "")
    if (keys.isEmpty() || letters.isEmpty()) return false
    if (letters.length < keys.length) return false
    for (i in 0..letters.length - keys.length) {
        var ok = true
        for (j in keys.indices) {
            val lettersFor = T9_MAP[keys[j]] ?: continue
            if (!lettersFor.contains(letters[i + j])) { ok = false; break }
        }
        if (ok) return true
    }
    return false
}

/* ---------------- scored contact search ---------------- */

data class PhoneIndex(val raw: String, val norm: String, val nat: String)

data class SearchIndexEntry(
    val contact: DeviceContact,
    val name: String,
    val nameLower: String,
    val words: List<String>,
    val initials: String,
    val companyLower: String,
    val emailLower: String,
    val noteLower: String,
    val letters: String,
    val phones: List<PhoneIndex>,
)

data class ScoredContact(val contact: DeviceContact, val score: Double, val nameLower: String)

fun buildSearchEntry(c: DeviceContact, nameOf: (DeviceContact) -> String, countryCode: String): SearchIndexEntry {
    val name = nameOf(c)
    val nameLower = name.lowercase()
    val words = nameLower.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val norm0 = { p: String -> normalizePhone(p, countryCode).replace(Regex("\\D"), "") }
    return SearchIndexEntry(
        contact = c,
        name = name,
        nameLower = nameLower,
        words = words,
        initials = words.mapNotNull { it.firstOrNull() }.joinToString(""),
        companyLower = "${c.company ?: ""} ${c.jobTitle ?: ""}".lowercase().trim(),
        emailLower = c.emails.joinToString(" ") { it.address.lowercase() },
        noteLower = (c.note ?: "").lowercase(),
        letters = nameLower.replace(Regex("[^a-z]"), ""),
        phones = c.phones.map { p ->
            val norm = norm0(p.number)
            val cc = DIAL_CODES[countryCode] ?: "251"
            val nat = if (norm.startsWith(cc) && norm.length > cc.length)
                norm.substring(cc.length).replaceFirst(Regex("^0+"), "") else norm
            PhoneIndex(p.number.replace(Regex("\\D"), ""), norm, nat)
        },
    )
}

private fun phoneScore(phones: List<PhoneIndex>, qd: String, qnat: String): Double {
    if (qd.length < 2) return 0.0
    var best = 0.0
    for (p in phones) {
        var s = 0.0
        if (p.raw == qd || p.norm == qd || (qnat.isNotEmpty() && p.nat == qnat)) s = 7.0
        else if (p.raw.startsWith(qd) || p.norm.startsWith(qd) || (qnat.isNotEmpty() && p.nat.startsWith(qnat))) s = 6.0
        else if (p.raw.endsWith(qd) || p.norm.endsWith(qd) || (qnat.isNotEmpty() && p.nat.endsWith(qnat))) s = 5.0
        else if (p.raw.contains(qd) || p.norm.contains(qd) || (qnat.isNotEmpty() && p.nat.contains(qnat))) s = 3.5
        if (s > best) best = s
        if (best >= 7.0) break
    }
    return best
}

/* v1.6.16 — FORGIVING NAME MATCHING. Users complained they could not find
   contacts "unless I type most part of its name". Two matchers were missing:

   1. CROSS-WORD PREFIX: `abebeke` now hits "Abebe Kebede" — the query is a
      prefix of the name with the spaces REMOVED. Typing continuously across
      the first-name/last-name boundary used to produce ZERO results.
   2. FUZZY SUBSEQUENCE: `abk` hits "Abebe Kebede" — every letter of the query
      appears in order (dialer-style fuzzy). Scattered initials/fragments now
      rank below real prefixes but still surface. */
private fun lettersPrefix(e: SearchIndexEntry, q: String): Boolean =
    e.letters.length >= q.length && e.letters.startsWith(q)

private fun subsequenceOf(hay: String, needle: String): Boolean {
    if (needle.isEmpty()) return true
    if (hay.length < needle.length) return false
    var i = 0
    for (ch in hay) {
        if (ch == needle[i]) {
            i++
            if (i == needle.length) return true
        }
    }
    return false
}

/** Per-word fuzzy subsequence ("kbk" inside "Kebede" — letters in order). */
private fun wordSubsequence(e: SearchIndexEntry, tok: String): Boolean =
    tok.length >= 3 && e.words.any { subsequenceOf(it, tok) }

private fun nameScore(e: SearchIndexEntry, tok: String): Double {
    var s = 0.0
    if (e.words.any { it.startsWith(tok) }) s = maxOf(s, 6.0)
    if (tok.length >= 2 && e.initials.startsWith(tok)) s = maxOf(s, 5.2)
    if (lettersPrefix(e, tok)) s = maxOf(s, 5.6) // v1.6.16 cross-word prefix
    if (e.words.any { it.contains(tok) }) s = maxOf(s, 4.5)
    if (e.nameLower.contains(tok)) s = maxOf(s, 4.0)
    if (wordSubsequence(e, tok)) s = maxOf(s, 4.2) // v1.6.16 per-word fuzzy
    else if (tok.length >= 3 && subsequenceOf(e.letters, tok)) s = maxOf(s, 3.4) // v1.6.16 name fuzzy
    return s
}

fun scoreEntry(e: SearchIndexEntry, q: String, qd: String, qnat: String, digitsOnly: Boolean, cc: String): Double {
    val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.size > 1) {
        var weakest = Double.MAX_VALUE
        for (tok in tokens) {
            val td = tok.replace(Regex("\\D"), "")
            var s = nameScore(e, tok)
            if (!Regex("^\\d+$").matches(tok)) {
                if (e.companyLower.contains(tok)) s = maxOf(s, 3.0)
                if (e.emailLower.contains(tok)) s = maxOf(s, 3.0)
            }
            if (td.length >= 2) s = maxOf(s, phoneScore(e.phones, td, nationalDigits(td, cc)))
            if (s <= 0.0) return 0.0
            if (s < weakest) weakest = s
        }
        return (if (weakest == Double.MAX_VALUE) 0.0 else weakest) + 0.4
    }

    var score = nameScore(e, q)
    if (digitsOnly && qd.length >= 3 && e.letters.isNotEmpty() && t9Match(qd, e.name)) score = maxOf(score, 5.5)
    if (!digitsOnly) {
        if (e.companyLower.contains(q)) score = maxOf(score, 3.0)
        if (e.emailLower.contains(q)) score = maxOf(score, 3.0)
        if (e.noteLower.contains(q)) score = maxOf(score, 2.0)
    }
    if (qd.length >= 2) score = maxOf(score, phoneScore(e.phones, qd, qnat))
    return score
}

/** Ranked contact search — use wherever a list of contacts is filtered.
 *
 *  v1.6.16: the per-contact SearchIndexEntry is CACHED per contact-list
 *  identity (like the RN edition's getContactSearchIndex) — with a real
 *  contact book every keystroke used to re-run the regex normalization of
 *  every phone of every contact, which read as typing lag in Search. */
private var searchIndexCacheKey: Triple<List<DeviceContact>, String, (DeviceContact) -> String>? = null
private var searchIndexCacheVal: List<SearchIndexEntry> = emptyList()

private fun cachedEntries(
    contacts: List<DeviceContact>,
    countryCode: String,
    nameOf: (DeviceContact) -> String,
): List<SearchIndexEntry> {
    val key = Triple(contacts, countryCode, nameOf)
    if (searchIndexCacheKey == key) return searchIndexCacheVal
    val built = contacts.map { buildSearchEntry(it, nameOf, countryCode) }
    searchIndexCacheKey = key
    searchIndexCacheVal = built
    return built
}

fun searchContacts(
    contacts: List<DeviceContact>,
    query: String,
    nameOf: (DeviceContact) -> String,
    countryCode: String,
    limit: Int = Int.MAX_VALUE,
): List<ScoredContact> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val qd = q.replace(Regex("\\D"), "")
    val qnat = nationalDigits(qd, countryCode)
    val digitsOnly = q == qd
    val out = mutableListOf<ScoredContact>()
    for (e in cachedEntries(contacts, countryCode, nameOf)) {
        val s = scoreEntry(e, q, qd, qnat, digitsOnly, countryCode)
        if (s > 0) out.add(ScoredContact(e.contact, s, e.nameLower))
    }
    out.sortWith(compareByDescending<ScoredContact> { it.score }.thenBy { it.nameLower })
    return out.take(limit)
}

/* ---------------- misc ---------------- */

fun maskAccount(acc: String?): String {
    if (acc.isNullOrEmpty()) return ""
    val clean = acc.replace(Regex("\\s+"), "")
    if (clean.length <= 4) return clean
    return "•••• •••• ${clean.takeLast(4)}"
}

fun bytesStr(n: Long): String {
    if (n < 1024) return "$n B"
    if (n < 1048576) return "${n / 1024} KB"
    if (n < 1073741824L) return fmtFixed(n / 1048576.0, 1) + " MB"
    return fmtFixed(n / 1073741824.0, 2) + " GB"
}

/** Multiplatform fixed-decimal formatter (java's String.format is JVM-only). */
internal fun fmtFixed(v: Double, decimals: Int): String {
    if (v.isNaN() || v.isInfinite()) return v.toString()
    if (decimals == 0) {
        val w = kotlin.math.round(kotlin.math.abs(v)).toLong()
        val sign = if (v < 0 && w != 0L) "-" else ""
        return sign + w.toString()
    }
    val factor = when (decimals) {
        1 -> 10.0; 2 -> 100.0; 3 -> 1000.0
        else -> 10.0.pow(decimals.toDouble())
    }
    // v1.6.13 FIX: the fraction used to be derived via Double.toString(), which
    // switches to scientific notation at 1e7 ("3.0E11") — every fmtFixed call
    // with ≥8 decimals produced garbage like "0.000003.0E11". Integer math
    // below can never do that.
    val r = kotlin.math.round(kotlin.math.abs(v) * factor)
    if (r >= 9.0e18) return v.toString() // overflow guard (absurd magnitudes)
    val whole = (r / factor).toLong()
    val fracDigits = r.toLong() - whole * factor.toLong()
    var frac = fracDigits.toString()
    if (frac.length < decimals) frac = frac.padStart(decimals, '0')
    if (frac.length > decimals) frac = frac.substring(0, decimals) // defensive
    val sign = if (v < 0 && r != 0.0) "-" else ""
    return "$sign$whole.$frac"
}

/** Multiplatform `%.<sig>g`-style formatter (significant digits, trailing zeros trimmed). */
fun fmtSig(n: Double, sig: Int): String {
    if (n == 0.0 || n.isNaN() || n.isInfinite()) return n.toString()
    val exp = kotlin.math.floor(kotlin.math.log10(kotlin.math.abs(n))).toInt()
    val decimals = (sig - 1 - exp).coerceIn(0, 15)
    val fixed = fmtFixed(n, decimals)
    return fixed.trimEnd('0').trimEnd('.')
}

fun clamp(n: Double, min: Double, max: Double): Double = min.coerceAtMost(max.coerceAtLeast(n))

/** Suspend WRITE_CONTACTS gate: true immediately when granted, otherwise
 *  shows the runtime dialog (main thread) and waits for the answer.
 *  v1.6.13 — contact mutations used to fail silently without this. */
suspend fun requestContactsWrite(): Boolean {
    if (Platform.contacts.hasWritePermission()) return true
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            Platform.contacts.requestWritePermission { granted ->
                if (cont.isActive) cont.resumeWith(Result.success(granted))
            }
        }
    }
}

/* Hash PIN — salted iterated SHA-256 (screen lock only; data is NOT encrypted). */
expect fun sha256Hex(input: String): String

fun hashPin(pin: String, salt: String? = null): PinHashResult {
    val s = salt ?: uid()
    var h = "$s:$pin"
    repeat(2000) { h = sha256Hex(h) }
    return PinHashResult(h, s)
}

data class PinHashResult(val hash: String, val salt: String)
