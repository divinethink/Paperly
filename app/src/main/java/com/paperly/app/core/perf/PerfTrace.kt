package com.paperly.app.core.perf

import android.os.Trace

/**
 * Named spans for system traces (Perfetto / "System Tracing" on the device) — the P0 measurement harness.
 * Span names are fixed strings only; never pass document titles, paths or any user content.
 */
object PerfTrace {
    inline fun <T> span(name: String, block: () -> T): T {
        val cookie = name.hashCode()
        Trace.beginAsyncSection(name, cookie)
        try {
            return block()
        } finally {
            Trace.endAsyncSection(name, cookie)
        }
    }
}
