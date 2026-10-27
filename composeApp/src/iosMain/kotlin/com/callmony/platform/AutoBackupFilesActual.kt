package com.callmony.platform

/* iOS actual of the v1.6.34 auto-backup layer — a no-op: the app's
   container is included in the device's iCloud/iTunes backup, and iOS
   has no permission-free public folder an app may write on its own.
   The in-app export/import (Settings → Backup) remains the manual path. */

actual object AutoBackupFiles {
    actual fun write(text: String): Boolean = false
    actual fun read(): String? = null
    actual fun delete() {}
}
