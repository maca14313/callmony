package com.callmony.lib

import com.callmony.platform.AutoBackupFiles
import com.callmony.platform.Platform

/* v1.6.34 — the auto-backup LOGIC (the file access itself is
   AutoBackupFiles; see that file for the why). Two triggers post the
   export, both throttled to once per 10 minutes:
     - MainActivity.onStop (the app just left the screen — the classic
       "save on close" moment)
     - hydrate-complete +30 s in the shell (a boot-time safety net: even
       if the process is killed before onStop ever fires, every launch
       still lands a backup of the store it just hydrated)
   THE EMPTY-STORE GUARD is the safety-critical rule: a store with nothing
   in it NEVER overwrites the backup file. A fresh install (no data yet)
   or a not-yet-restored session must not be able to destroy the one copy
   the reinstall is about to offer back. The danger-zone wipe removes the
   backup explicitly (Db.wipeStorage) — that is the only path that may. */
object AutoBackup {

    private const val AT_KEY = "callmony.autoBackup.at"
    private const val THROTTLE_MS = 10 * 60_000L

    /** Everything meaningfully user-owned (settings alone don't count — a
     *  fresh onboarding must never overwrite a real backup). */
    private fun storeHasData(s: AppState): Boolean =
        s.entries.isNotEmpty() || s.notes.isNotEmpty() || s.lists.isNotEmpty() ||
            s.reminders.isNotEmpty() || s.banks.isNotEmpty() || s.groups.isNotEmpty() ||
            s.messages.isNotEmpty() || s.contactX.isNotEmpty()

    /** Write the full-store backup if due. Background threads only (the
     *  JSON build reads every collection). Returns true when a backup was
     *  actually written this call. */
    fun exportIfDue(force: Boolean = false): Boolean {
        val s = AppStore.state
        // NEVER export an unhydrated store — a boot that hasn't read its
        // data yet looks empty, and empty never overwrites the backup.
        if (!s.hydrated) return false
        if (!storeHasData(s)) return false
        val last = Platform.kv.read(AT_KEY)?.toLongOrNull() ?: 0L
        if (!force && last > 0 && now() - last < THROTTLE_MS) return false
        val text = Backup.exportAll()
        if (!AutoBackupFiles.write(text)) return false
        Platform.kv.write(AT_KEY, now().toString())
        return true
    }

    /** The last successful auto-backup time (epoch ms, 0 when never). */
    fun lastExportAt(): Long = Platform.kv.read(AT_KEY)?.toLongOrNull() ?: 0L

    /** The auto-backup file's text (null when the platform has none) —
     *  used by the post-hydrate restore offer. Background threads only. */
    fun readBackupText(): String? = AutoBackupFiles.read()

    /** Parse + validate the backup for the restore offer: the DTO (with
     *  its exportedAt) when the file holds real data, null otherwise. */
    fun parseForOffer(text: String?): Pair<PersistedDataDto, PersistedData>? {
        if (text == null) return null
        val dto = Backup.parseDto(text) ?: return null
        val data = dto.toData()
        val hasAnything = data.entries.isNotEmpty() || data.notes.isNotEmpty() ||
            data.lists.isNotEmpty() || data.reminders.isNotEmpty() ||
            data.banks.isNotEmpty() || data.contactX.isNotEmpty()
        if (!hasAnything) return null
        return dto to data
    }
}
