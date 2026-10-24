package com.callmony.lib

import com.callmony.i18n.t
import com.callmony.model.CalcList
import com.callmony.model.GENERAL_OWNER
import com.callmony.model.DeviceContact
import com.callmony.model.EntryItem
import com.callmony.model.LedgerEntry
import com.callmony.model.ListItem
import com.callmony.platform.Platform

/* Exporters — vCard 3.0, CSV (Google Contacts compatible), statements and
   bill texts. Port of src/lib/exporters.ts. */

private fun esc(s: String): String = s.replace(Regex("([,;\\\\])"), "\\\\$1").replace("\n", "\\n")

/* ---------------- vCard ---------------- */

fun contactToVcard(c: DeviceContact): String {
    val lines = mutableListOf("BEGIN:VCARD", "VERSION:3.0")
    lines.add("N:${esc(c.familyName)};${esc(c.givenName)};${esc(c.middleName ?: "")};;")
    lines.add("FN:${esc(c.displayName)}")
    c.company?.let { lines.add("ORG:${esc(it)}") }
    c.jobTitle?.let { lines.add("TITLE:${esc(it)}") }
    c.phones.forEach { lines.add("TEL;TYPE=${(it.label.ifEmpty { "mobile" }).uppercase()}:${esc(it.number)}") }
    c.emails.forEach { lines.add("EMAIL;TYPE=${(it.label.ifEmpty { "internet" }).uppercase()}:${esc(it.address)}") }
    c.addresses.forEach { lines.add("ADR;TYPE=${(it.label.ifEmpty { "home" }).uppercase()}:;;${esc(it.formatted)};;;;") }
    c.birthday?.let { lines.add("BDAY:$it") }
    c.note?.let { lines.add("NOTE:${esc(it)}") }
    lines.add("END:VCARD")
    return lines.joinToString("\n")
}

fun shareContactVcard(c: DeviceContact) {
    Platform.share.shareText(contactToVcard(c), "${c.displayName}.vcf")
}

fun exportContactsVcard(): Boolean {
    val contacts = AppStore.state.deviceContacts
    if (contacts.isEmpty()) return false
    Platform.share.shareText(contacts.joinToString("\n") { contactToVcard(it) }, "callmony-contacts.vcf")
    return true
}

/* ---------------- CSV ---------------- */

private fun csvCell(v: Any?): String {
    val s = v?.toString() ?: ""
    return if (Regex("[\",\\n]").containsMatchIn(s)) "\"${s.replace("\"", "\"\"")}\"" else s
}

private fun csvRow(cells: List<Any?>): String = cells.joinToString(",") { csvCell(it) }

fun contactsToCsv(contacts: List<DeviceContact>): String {
    val head = csvRow(
        listOf(
            "Name", "Given Name", "Middle Name", "Family Name", "Phone 1 - Type", "Phone 1 - Value",
            "Phone 2 - Type", "Phone 2 - Value", "Email 1 - Type", "Email 1 - Value",
            "Organization Name", "Organization Title", "Birthday", "Notes",
        )
    )
    val rows = contacts.map { c ->
        csvRow(
            listOf(
                c.displayName, c.givenName, c.middleName ?: "", c.familyName,
                c.phones.getOrNull(0)?.label ?: "", c.phones.getOrNull(0)?.number ?: "",
                c.phones.getOrNull(1)?.label ?: "", c.phones.getOrNull(1)?.number ?: "",
                c.emails.getOrNull(0)?.label ?: "", c.emails.getOrNull(0)?.address ?: "",
                c.company ?: "", c.jobTitle ?: "", c.birthday ?: "", c.note ?: "",
            )
        )
    }
    return (listOf(head) + rows).joinToString("\n")
}

fun exportContactsCsv(): Boolean {
    val contacts = AppStore.state.deviceContacts
    if (contacts.isEmpty()) return false
    Platform.share.shareText(contactsToCsv(contacts), "callmony-contacts.csv")
    return true
}

fun entriesToCsv(entries: List<LedgerEntry>): String {
    val head = csvRow(listOf("Date", "Type", "Kind", "Amount", "Paid", "Remaining", "Category", "Method", "Due date", "Note", "Items"))
    val rows = entries.map { e ->
        csvRow(
            listOf(
                e.date, e.type, e.kind ?: "", e.amount,
                e.payments.sumOf { it.amount },
                entryRemaining(e), e.category ?: "", e.method ?: "", e.dueDate ?: "",
                e.note ?: "",
                e.items.joinToString("; ") { "${it.name} ${it.qty}${it.unit?.let { u -> " $u" } ?: ""} × ${it.price}" },
            )
        )
    }
    return (listOf(head) + rows).joinToString("\n")
}

fun exportTransactionsCsv(): Boolean {
    val entries = AppStore.state.entries.filter { it.deletedAt == null }
    if (entries.isEmpty()) return false
    Platform.share.shareText(entriesToCsv(entries), "callmony-transactions.csv")
    return true
}

/* ---------------- statement (per owner) ---------------- */

fun statementText(ownerName: String, entries: List<LedgerEntry>): String {
    val currency = AppStore.state.settings.currency
    val sorted = entries.sortedWith(compareBy<LedgerEntry> { it.date }.thenBy { it.createdAt })
    var running = 0.0
    val lines = sorted.map { e ->
        val signed = entrySignedRemaining(e)
        running = Math.round((running + signed) * 100) / 100.0
        val itemBits = e.items.map { "  ${it.name} ${it.qty}${it.unit?.let { u -> " $u" } ?: ""} × ${it.price} = ${it.qty * it.price}" }
        "${fd(e.date)}  ${e.kind ?: e.type}  ${money(e.amount, currency)}" +
            (if (itemBits.isNotEmpty()) "\n${itemBits.joinToString("\n")}" else "") +
            "\n  → balance ${money(running, currency)}"
    }
    val total = sorted.sumOf { entrySignedRemaining(it) }
    return (listOf("callmony · $ownerName", "—".repeat(28)) + lines + listOf("—".repeat(28), "Net: ${money(total, currency)}")).joinToString("\n")
}

fun shareStatement(ownerName: String, ownerId: String): Boolean {
    val entries = AppStore.state.entries.filter { it.ownerId == ownerId && it.deletedAt == null }
    if (entries.isEmpty()) return false
    Platform.share.shareText(statementText(ownerName, entries), "$ownerName-statement.txt")
    return true
}

/* ---------------- list bill text ---------------- */

fun listShareText(l: CalcList): String {
    val currency = l.currency ?: AppStore.state.settings.currency
    val totals = listTotals(l)
    val lines = l.items.map { i ->
        "${if (i.checked) "✓" else "•"} ${i.name} ${i.qty}${i.unit?.let { u -> " $u" } ?: ""} × ${i.unitPrice} = ${lineTotal(i)}"
    }
    return (
        listOf("callmony · ${l.title}", "—".repeat(24)) + lines + listOf("—".repeat(24)) +
            listOf(
                "Subtotal: ${money(totals.subtotal, currency)}",
                "Total: ${money(totals.grand, currency)}",
                "Paid: ${money(totals.paid, currency)} · Pending: ${money(totals.pending, currency)}",
            )
        ).joinToString("\n")
}

/* ---------------- owner display name (useOwnerName) ---------------- */

fun ownerNameOf(ownerId: String): String = when {
    ownerId == GENERAL_OWNER -> t("hdr.general")
    ownerId.startsWith("c:") -> {
        val c = AppStore.contactByRecord(ownerId.substring(2))
        if (c != null) displayName(c) else t("cd.notFound")
    }
    else -> t("cd.notFound")
}
