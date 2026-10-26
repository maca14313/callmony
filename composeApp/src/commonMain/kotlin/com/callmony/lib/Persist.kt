package com.callmony.lib

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import com.callmony.platform.persistDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/* v1.6.18 — THE FREEZE / LAG / "APP CLOSING ITSELF" FIX.
 *
 * Root cause of the v1.6.17 reports: every store mutation (toggle favourite,
 * edit an entry, mark a reminder, save a contact patch …) ran its ENTIRE
 * persistence inline on the MAIN THREAD — JSON serialization of the whole
 * collection plus a synchronous SharedPreferences commit() that writes the
 * value TWICE (row + safety mirror). Each click cost tens-to-hundreds of
 * milliseconds of blocked UI; the contact cache alone (the full device
 * address book, re-serialized after every in-app contact edit) could block
 * for hundreds of ms, and on slower devices stacked commits pushed past
 * Android's 5-second input-dispatch limit → ANR → "the app closes itself".
 *
 * The fix: a single-writer IO queue. All persistence work (serialize +
 * commit) is posted here and executed by ONE background coroutine on
 * Dispatchers.IO, strictly FIFO, so:
 *   - the main thread never touches the disk again (lag + ANR gone);
 *   - writes can never interleave (one writer) — no corrupted rows;
 *   - read-modify-write sequences posted as ONE block are atomic
 *     relative to every other queued block (used by the alarm receivers).
 *
 * The queue stays in synchronous mode (jobs run inline) until the real app
 * flips it — unit tests, which assert read-after-write round-trips, keep
 * their deterministic behavior, and headless receivers that run before any
 * UI still behave exactly like v1.6.17. */
object Persist {

    private var asyncMode = false
    private var drainStarted = false
    private val jobs = Channel<() -> Unit>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + persistDispatcher)

    /** Called once from the app entry points (Application.onCreate /
     *  iOS platform init). Idempotent. */
    fun enableAsync() {
        asyncMode = true
        ensureDrain()
    }

    /** Enqueue a persistence job (or run it inline in synchronous mode).
     *  Never throws — same contract as the old try/catch save bodies. */
    fun post(job: () -> Unit) {
        if (!asyncMode) {
            try { job() } catch (_: Exception) {}
            return
        }
        ensureDrain()
        jobs.trySend(job)
    }

    /** Post a job and WAIT for it to finish (bounded). Only ever call this
     *  from a background thread (receivers, tests) — the whole point is to
     *  keep the caller off the main thread while its block runs on IO in
     *  order with everything else. */
    fun postAndWait(timeoutMs: Long = 8000, job: () -> Unit) {
        if (!asyncMode) {
            try { job() } catch (_: Exception) {}
            return
        }
        val done = CompletableDeferred<Unit>()
        post {
            try { job() } finally { done.complete(Unit) }
        }
        runBlocking { withTimeoutOrNull(timeoutMs) { done.await() } }
    }

    /** Wait until every job posted so far has landed on disk (bounded).
     *  Background threads only. Returns false on timeout. */
    fun flush(timeoutMs: Long = 4000): Boolean {
        if (!asyncMode) return true
        val done = CompletableDeferred<Unit>()
        post {
            try { } finally { done.complete(Unit) }
        }
        return runBlocking { withTimeoutOrNull(timeoutMs) { done.await(); true } } ?: false
    }

    private fun ensureDrain() {
        if (drainStarted) return
        // No lock: enableAsync() runs once from the app entry (main thread)
        // before any post. A rare cross-thread double-start costs at most one
        // extra consumer on the channel — each job still runs exactly once.
        drainStarted = true
        scope.launch {
            for (j in jobs) {
                try { j() } catch (_: Exception) {}
            }
        }
    }
}
