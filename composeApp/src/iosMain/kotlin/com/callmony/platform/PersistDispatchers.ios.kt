package com.callmony.platform

/* v1.6.18 — the Persist queue dispatcher: background pool on iOS. */
actual val persistDispatcher: kotlinx.coroutines.CoroutineDispatcher =
    kotlinx.coroutines.Dispatchers.Default
