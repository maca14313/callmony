package com.callmony.lib

/* ---------------- v1.6.38 — the contact EDIT merge planner ---------------- */

/* The user report: "there is something wrong with create, edit contacts".
   Root causes (all in the save path):
   1. The editor passed Pair(label, number) while the platform layer
      destructures Pair(number, label) — every created/edited contact
      literally got the phone number "mobile" and the email "home".
   2. The old update did a "loss-free UNION" merge: a changed number was
      ADDED beside the old one, a deleted row resurrected on the next
      address-book refresh, and the company field was never written at
      all. Edits looked undone a minute later.
   The edit is now a true REPLACE of what the editor shows, computed by
   this PURE planner: rows are matched by normalized value, matched rows
   keep their Data-row identity (label updated in place), rows the editor
   dropped are DELETED, editor rows that are genuinely new are INSERTED.
   Pure + deterministic so the exact merge is harness-testable without
   a content provider. */

/** One row that exists on the device (a phone number or an email address). */
data class MergeRow(
    val dataId: String, // the ContactsContract.Data row id
    val value: String,  // the phone number / email address
    val label: String,  // its current label (mobile / work / home / …)
)

/** What to write back: deletions, in-place label updates, fresh inserts. */
data class MergePlan(
    val deleteIds: List<String>,               // Data row ids to delete
    val relabel: List<Pair<String, String>>,   // (dataId, newLabel)
    val inserts: List<Pair<String, String>>,   // (value, label) to insert
)

/** Phone-number normalization — keep digits and a leading +, drop the rest
 *  (the same rule the old union merge used, now for MATCHING only). */
fun normalizePhoneForMerge(raw: String): String =
    raw.replace(Regex("[^\\d+]"), "")

/** Email normalization — trim + lowercase (emails match case-insensitively). */
fun normalizeEmailForMerge(raw: String): String = raw.trim().lowercase()

/** Plan the phone merge: the editor's (number, label) list is the truth. */
fun planPhoneMerge(existing: List<MergeRow>, wanted: List<Pair<String, String>>): MergePlan {
    val wantedNorm = wanted.map { normalizePhoneForMerge(it.first) to it.second }
    val matchedWanted = HashSet<Int>()
    val delete = mutableListOf<String>()
    val relabel = mutableListOf<Pair<String, String>>()
    existing.forEach { row ->
        val norm = normalizePhoneForMerge(row.value)
        val idx = wantedNorm.indexOfFirst { it.first == norm && norm.isNotEmpty() }
        if (idx >= 0) {
            matchedWanted.add(idx)
            if (wantedNorm[idx].second != row.label) relabel.add(row.dataId to wantedNorm[idx].second)
        } else {
            delete.add(row.dataId)
        }
    }
    val inserts = wantedNorm.indices.filter { it !in matchedWanted }
        .map { wanted[it].first to wanted[it].second }
    return MergePlan(delete, relabel, inserts)
}

/** Plan the email merge: the editor's (address, label) list is the truth. */
fun planEmailMerge(existing: List<MergeRow>, wanted: List<Pair<String, String>>): MergePlan {
    val wantedNorm = wanted.map { normalizeEmailForMerge(it.first) to it.second }
    val matchedWanted = HashSet<Int>()
    val delete = mutableListOf<String>()
    val relabel = mutableListOf<Pair<String, String>>()
    existing.forEach { row ->
        val norm = normalizeEmailForMerge(row.value)
        val idx = wantedNorm.indexOfFirst { it.first == norm && norm.isNotEmpty() }
        if (idx >= 0) {
            matchedWanted.add(idx)
            if (wantedNorm[idx].second != row.label) relabel.add(row.dataId to wantedNorm[idx].second)
        } else {
            delete.add(row.dataId)
        }
    }
    val inserts = wantedNorm.indices.filter { it !in matchedWanted }
        .map { wanted[it].first to wanted[it].second }
    return MergePlan(delete, relabel, inserts)
}
