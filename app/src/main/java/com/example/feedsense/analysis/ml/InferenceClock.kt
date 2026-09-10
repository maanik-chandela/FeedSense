package com.example.feedsense.analysis.ml

// --------------------------------
// INFERENCE CLOCK (8B-14)
// --------------------------------
//
// Injectable time source for latency instrumentation.
//
// Deterministic tests inject a fixed/scripted clock so
// latency claims are reproducible without sleep calls and
// without fabricating performance values (8B-14 sections 27,
// 47). Production uses the system clock.
//
// Never used for privacy/threshold decisions.

fun interface InferenceClock {
    fun nowMs(): Long
}

/*
 * Default wall-clock implementation.
 */
object SystemInferenceClock : InferenceClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

/*
 * Test/scripted clock: returns the provided "now" until
 * explicitly advanced, then advances by the given step each
 * read. Fully deterministic.
 */
class StepInferenceClock(
    private val initialMs: Long = 0L,
    private val stepMs: Long = 1L
) : InferenceClock {

    private var current = initialMs

    override fun nowMs(): Long {
        val value = current
        current += stepMs
        return value
    }
}