package com.callmony

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

/* v1.6.19 diagnostic #2 — boot the app the way a REAL UPGRADING USER boots
   it: settings already onboarded, 400 cached calls, entries/contactX/
   reminders/notes/banks present. Eliminates the fresh-install spinner
   artifact and exercises the true v1.6.17 → v1.6.18 upgrade boot path. */

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpgradeBootDiagTest {

    private fun seed() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()
        val day = 86_400_000L

        val settingsJson = """{"theme":"auto","language":"en","currency":"ETB","countryCode":"ET","clock24":false,"autolockMin":1,"customCategories":["Transport","Rent"],"onboarded":true,"blockedNumbers":["+251911223344"],"startTab":"recents","createdAt":${now - 90 * day}}"""

        val entriesJson = buildString {
            append("[")
            for (i in 1..25) {
                if (i > 1) append(",")
                append("""{"id":"e$i","ownerId":"c${i % 7}","type":${if (i % 2 == 0) "\"credit\"" else "\"debit\""},"amount":${100 + i},"date":"2026-09-${(i % 27) + 1}","note":"entry $i","items":[{"id":"it$i","name":"item $i","qty":2,"price":${10 + i}}],"payments":[{"id":"p$i","amount":${5 + i},"date":"2026-10-01"}],"refs":[]}""")
            }
            append("]")
        }

        val contactXJson = buildString {
            append("[")
            for (i in 1..30) {
                if (i > 1) append(",")
                append("""{"recordID":"c$i","favorite":${i % 5 == 0},"createdAt":${now - 60 * day}}""")
            }
            append("]")
        }

        val remindersJson = """[{"id":"r1","ownerId":"general","title":"Daily standup","kind":"todo","dueAt":${now - 3_600_000},"repeat":"daily","leadMinutes":[10,0],"createdAt":${now - 10 * day},"updatedAt":${now - day}},{"id":"r2","ownerId":"general","title":"Pay rent","kind":"payment","dueAt":${now - 5 * day},"repeat":"monthly","leadMinutes":[],"createdAt":${now - 40 * day},"updatedAt":${now - 5 * day}},{"id":"r3","ownerId":"general","title":"Old done","kind":"custom","dueAt":${now - 20 * day},"repeat":"none","leadMinutes":[],"done":true,"doneAt":${now - 19 * day},"createdAt":${now - 30 * day},"updatedAt":${now - 19 * day}},{"id":"r4","ownerId":"general","title":"Snoozed one","kind":"custom","dueAt":${now - 600_000},"snoozedUntil":${now + 600_000},"repeat":"none","leadMinutes":[],"createdAt":${now - 2 * day},"updatedAt":${now - 600_000}},{"id":"r5","ownerId":"general","title":"Upcoming","kind":"birthday","dueAt":${now + 3 * day},"repeat":"yearly","leadMinutes":[1440],"createdAt":${now - 3 * day},"updatedAt":${now - 3 * day}}]"""

        val notesJson = """[{"id":"n1","ownerId":"general","title":"Note","body":"note one","pinned":true,"createdAt":$now,"updatedAt":$now}]"""
        val banksJson = """[{"id":"b1","ownerId":"general","label":"CBE","bankName":"CBE","accountNumber":"1000","balance":5000,"currency":"ETB","createdAt":$now,"updatedAt":$now}]"""
        val listsJson = """[{"id":"l1","ownerId":"general","kind":"simple","title":"Groceries","items":[{"id":"li1","name":"Rice","qty":2,"unitPrice":90}],"createdAt":$now,"updatedAt":$now}]"""
        val groupsJson = """[{"id":"g1","name":"Work","createdAt":$now}]"""

        val p = ctx.getSharedPreferences("callmony-store", Context.MODE_PRIVATE)
        p.edit()
            .putString("callmony.settings", settingsJson)
            .putString("callmony.settings~m", settingsJson)
            .putString("callmony.entries", entriesJson)
            .putString("callmony.entries~m", entriesJson)
            .putString("callmony.contactX", contactXJson)
            .putString("callmony.contactX~m", contactXJson)
            .putString("callmony.reminders", remindersJson)
            .putString("callmony.reminders~m", remindersJson)
            .putString("callmony.notes", notesJson)
            .putString("callmony.notes~m", notesJson)
            .putString("callmony.banks", banksJson)
            .putString("callmony.banks~m", banksJson)
            .putString("callmony.lists", listsJson)
            .putString("callmony.lists~m", listsJson)
            .putString("callmony.groups", groupsJson)
            .putString("callmony.groups~m", groupsJson)
            .commit()

        // 400 cached system calls — type is an Int (1=in 2=out 3=missed)
        val callsJson = buildString {
            append("""{"savedAt":$now,"list":[""")
            for (i in 1..400) {
                if (i > 1) append(",")
                append("""{"id":"call$i","cachedName":"Caller ${i % 40}","number":"+25191${1000000 + i}","type":${1 + (i % 3)},"date":${now - i * 600_000L},"duration":${i % 300}}""")
            }
            append("]}")
        }
        val docs = java.io.File(ctx.filesDir, "documents")
        docs.mkdirs()
        java.io.File(docs, "calllog-cache.json").writeText(callsJson)
    }

    private fun dumpThreads(reason: String) {
        println("=== THREAD DUMP: $reason ===")
        Thread.getAllStackTraces().entries
            .sortedBy { it.key.name }
            .forEach { (thread, stack) ->
                println("--- ${thread.name} (${thread.state}) ---")
                stack.take(40).forEach { println("    $it") }
            }
        println("=== END THREAD DUMP ===")
    }

    @Test
    fun upgradeBoot() {
        seed()
        val watchman = Thread {
            try { Thread.sleep(15_000) } catch (_: InterruptedException) {}
            repeat(10) { n ->
                val ui = com.callmony.lib.UiStore
                val app = com.callmony.lib.AppStore.state
                println("=== UPG STATE $n: alarmQueue=${ui.alarmQueue.size} reminders=${app.reminders.size} " +
                    "onboarded=${app.settings.onboarded} hydrated=${app.hydrated} calls=${app.systemCalls.size} " +
                    "tab=${ui.tab} locked=${ui.locked} overlay=${ui.overlayStack.size} ===")
                try { Thread.sleep(1500) } catch (_: InterruptedException) {}
            }
            dumpThreads("upgrade boot stalled — full dump")
        }
        watchman.isDaemon = true
        watchman.start()

        val scenario = ActivityScenario.launch(MainActivity::class.java)

        repeat(120) { i ->
            shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
            Thread.sleep(100)
            val st = com.callmony.lib.AppStore.state
            println("=== upump $i hydrated=${st.hydrated} calls=${st.systemCalls.size} contacts=${st.deviceContacts.size} reminders=${st.reminders.size} ===")
            if (i == 30) dumpThreads("upgrade mid-boot snapshot")
        }

        scenario.onActivity { activity ->
            check(!activity.isDestroyed) { "Activity destroyed during boot" }
        }
        scenario.close()
        println("=== UPGRADE BOOT DIAG: completed all pumps without hanging ===")
    }
}
