package com.callmony.platform

/* v1.6.18 — the Persist queue dispatcher: real disk IO on Android. */
actual val persistDispatcher: kotlinx.coroutines.CoroutineDispatcher =
    kotlinx.coroutines.Dispatchers.IO
