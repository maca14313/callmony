package com.callmony.platform

/* v1.6.34 — THE NO-DATA-LOSS LAYER (user report: "every time I install
   the app the data is all gone"). The app's own storage (SharedPreferences
   + files) is wiped when the app is UNINSTALLED — and on budget OEM
   installers a "reinstall" (uninstall + install) is exactly what happens
   on every update, while the cloud Auto Backup rarely restores sideloaded
   apps in time. This layer mirrors the FULL store to a file the system
   deliberately KEEPS on uninstall:
     - Android 10+ : MediaStore.Downloads → Downloads/Callmony/
                     callmony-backup.json (public, user-visible, survives
                     uninstall — shared storage is the documented home for
                     files that must outlive the app; the row's owner
                     package matches again after a reinstall, so the fresh
                     install can read it back with no permission prompt)
     - Android 9-  : not available (no permission-free public location);
                     the in-app export/import remains the path
     - iOS         : no-op (the app container is backed up with the device)
   The logic (when to export, the empty-store guard, the restore offer)
   lives in com.callmony.lib.AutoBackup — this is only the raw file access. */
expect object AutoBackupFiles {

    /** Overwrite (or create) the auto-backup file. False when the platform
     *  has no supported location or the write failed. */
    fun write(text: String): Boolean

    /** The auto-backup file's text, or null when there is none / it can't
     *  be read on this platform. */
    fun read(): String?

    /** Delete the auto-backup file (danger-zone wipe must remove it too —
     *  otherwise a wiped app would resurrect itself from Downloads). */
    fun delete()
}
