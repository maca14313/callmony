@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.callmony.lib

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/* v1.6.20 — iOS actual of the diagnostics report store: plain text files in
   the app's Documents directory (same file pattern as IosKv). iOS has no
   watchdog/firewall capture path — the diagnostics surface there is the
   in-app overlay + Settings list fed by this store. */

actual fun createDiagStorage(): DiagStorage = object : DiagStorage {

    private val fm = NSFileManager.defaultManager

    private fun dirPath(): String =
        ((fm.URLsForDirectory(NSDocumentDirectory, inDomains = NSUserDomainMask)
            .firstOrNull() as? NSURL)?.path ?: ".") + "/diag"

    private fun pathFor(id: Long): String = dirPath() + "/$id.txt"

    override fun save(report: DiagReport) {
        try {
            fm.createDirectoryAtPath(dirPath(), true, null, null)
            val text = buildString {
                appendLine("kind=" + report.kind)
                appendLine("seen=" + if (report.seen) 1 else 0)
                appendLine("phase=" + report.phase)
                appendLine("--headline--")
                appendLine(report.headline)
                appendLine("--stack--")
                appendLine(report.stack)
                appendLine("--detail--")
                appendLine(report.detail)
            }
            (text as NSString).writeToFile(pathFor(report.id), true, NSUTF8StringEncoding, null)
        } catch (_: Exception) {}
    }

    override fun all(): List<DiagReport> = try {
        val names = fm.contentsOfDirectoryAtPath(dirPath(), null)
            ?.mapNotNull { it as? String } ?: emptyList()
        names.sortedDescending().take(12).mapNotNull { name ->
            try {
                val raw = NSString.stringWithContentsOfFile(dirPath() + "/" + name, NSUTF8StringEncoding, null)
                    ?: return@mapNotNull null
                val lines = raw.split("\n")
                val head = lines.takeWhile { !it.startsWith("--") }
                fun section(tag: String): String = lines.dropWhile { it != tag }.drop(1)
                    .takeWhile { !it.startsWith("--") }.joinToString("\n")
                val kv = head.mapNotNull { l ->
                    val i = l.indexOf('=')
                    if (i <= 0) null else l.substring(0, i) to l.substring(i + 1)
                }.toMap()
                DiagReport(
                    id = name.removeSuffix(".txt").toLongOrNull() ?: return@mapNotNull null,
                    kind = kv["kind"] ?: "freeze",
                    seen = kv["seen"] == "1",
                    phase = kv["phase"] ?: "?",
                    headline = section("--headline--"),
                    stack = section("--stack--"),
                    detail = section("--detail--"),
                )
            } catch (_: Exception) { null }
        }
    } catch (_: Exception) { emptyList() }

    override fun markSeen(id: Long) {
        try {
            val p = pathFor(id)
            val raw = NSString.stringWithContentsOfFile(p, NSUTF8StringEncoding, null) ?: return
            val next = raw.replaceFirst(Regex("seen=0"), "seen=1")
            (next as NSString).writeToFile(p, true, NSUTF8StringEncoding, null)
        } catch (_: Exception) {}
    }

    override fun clear() {
        try {
            fm.contentsOfDirectoryAtPath(dirPath(), null)
                ?.mapNotNull { it as? String }?.forEach { name ->
                    fm.removeItemAtPath(dirPath() + "/" + name, null)
                }
        } catch (_: Exception) {}
    }
}
