package com.example.feedsense

import com.example.feedsense.analysis.dedup.AdaptiveFrameSampler
import com.example.feedsense.analysis.dedup.DeduplicationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-3.
 *
 * AdaptiveFrameSampler: bounded adaptive sampling,
 * interval adjustment, and force-next.
 */
class AdaptiveFrameSamplerTest {

    // --------------------------------
    // FIRST CALL
    // --------------------------------

    @Test
    fun `first call always samples`() {

        val sampler =
            AdaptiveFrameSampler()

        assertTrue(
            sampler.shouldSample(1000L)
        )
    }

    // --------------------------------
    // MINIMUM INTERVAL
    // --------------------------------

    @Test
    fun `does not sample before minimum interval`() {

        val config =
            DeduplicationConfig(
                minimumSamplingIntervalMs = 1000L,
                maximumSamplingIntervalMs = 5000L
            )

        val sampler =
            AdaptiveFrameSampler(config)

        sampler.shouldSample(1000L)

        assertFalse(
            sampler.shouldSample(1500L)
        )

        assertFalse(
            sampler.shouldSample(1999L)
        )
    }

    @Test
    fun `samples at minimum interval`() {

        val config =
            DeduplicationConfig(
                minimumSamplingIntervalMs = 1000L,
                maximumSamplingIntervalMs = 5000L
            )

        val sampler =
            AdaptiveFrameSampler(config)

        sampler.shouldSample(1000L)

        assertTrue(
            sampler.shouldSample(2000L)
        )
    }

    // --------------------------------
    // ADAPTIVE INCREASE
    // --------------------------------

    @Test
    fun `low change increases interval`() {

        val config =
            DeduplicationConfig(
                minimumSamplingIntervalMs = 1000L,
                maximumSamplingIntervalMs = 10000L
            )

        val sampler =
            AdaptiveFrameSampler(config)

        val t0 = 1000L

        sampler.shouldSample(t0)

        // Low change: interval should increase
        sampler.shouldSample(
            t0 + 1000, 0f
        )

        assertTrue(
            sampler.samplingIntervalMs > 1000L
        )

        // Should not sample at 2000ms anymore
        assertFalse(
            sampler.shouldSample(t0 + 2000)
        )
    }

    // --------------------------------
    // ADAPTIVE DECREASE
    // --------------------------------

    @Test
    fun `high change decreases interval`() {

        val config =
            DeduplicationConfig(
                minimumSamplingIntervalMs = 1000L,
                maximumSamplingIntervalMs = 10000L
            )

        val sampler =
            AdaptiveFrameSampler(config)

        val t0 = 1000L

        sampler.shouldSample(t0)

        // High change: interval should decrease
        // (but minimum is already 1000)
        sampler.shouldSample(
            t0 + 1000, 1f
        )

        assertEquals(
            1000L,
            sampler.samplingIntervalMs
        )
    }

    // --------------------------------
    // BOUNDS
    // --------------------------------

    @Test
    fun `interval never goes below minimum`() {

        val config =
            DeduplicationConfig(
                minimumSamplingIntervalMs = 2000L,
                maximumSamplingIntervalMs = 10000L
            )

        val sampler =
            AdaptiveFrameSampler(config)

        val t0 = 0L

        sampler.shouldSample(t0)

        // Multiple high-change samples
        for (i in 1..20) {
            sampler.shouldSample(
                t0 + i * 2000, 1f
            )
        }

        assertTrue(
            sampler.samplingIntervalMs >=
                    config.minimumSamplingIntervalMs
        )
    }

    @Test
    fun `interval never goes above maximum`() {

        val config =
            DeduplicationConfig(
                minimumSamplingIntervalMs = 1000L,
                maximumSamplingIntervalMs = 5000L
            )

        val sampler =
            AdaptiveFrameSampler(config)

        val t0 = 0L

        sampler.shouldSample(t0)

        // Multiple low-change samples
        for (i in 1..20) {
            sampler.shouldSample(
                t0 + i * 5000, 0f
            )
        }

        assertTrue(
            sampler.samplingIntervalMs <=
                    config.maximumSamplingIntervalMs
        )
    }

    // --------------------------------
    // FORCE NEXT
    // --------------------------------

    @Test
    fun `forceNext allows immediate next sample`() {

        val config =
            DeduplicationConfig(
                minimumSamplingIntervalMs = 5000L,
                maximumSamplingIntervalMs = 10000L
            )

        val sampler =
            AdaptiveFrameSampler(config)

        sampler.shouldSample(1000L)

        // Not enough time elapsed
        assertFalse(
            sampler.shouldSample(2000L)
        )

        // Force next
        sampler.forceNext()

        // Now should sample immediately
        assertTrue(
            sampler.shouldSample(2001L)
        )
    }

    // --------------------------------
    // RESET
    // --------------------------------

    @Test
    fun `reset clears state`() {

        val config =
            DeduplicationConfig(
                minimumSamplingIntervalMs = 1000L,
                maximumSamplingIntervalMs = 10000L
            )

        val sampler =
            AdaptiveFrameSampler(config)

        sampler.shouldSample(1000L)
        sampler.shouldSample(
            2000, 0f
        )

        // After many low-change samples, interval > min
        assertTrue(
            sampler.samplingIntervalMs > 1000L
        )

        sampler.reset()

        assertEquals(
            config.minimumSamplingIntervalMs,
            sampler.samplingIntervalMs
        )

        // First call after reset should sample
        assertTrue(
            sampler.shouldSample(10000L)
        )
    }

    // --------------------------------
    // MEDIUM CHANGE
    // --------------------------------

    @Test
    fun `medium change keeps current interval`() {

        val config =
            DeduplicationConfig(
                minimumSamplingIntervalMs = 1000L,
                maximumSamplingIntervalMs = 10000L
            )

        val sampler =
            AdaptiveFrameSampler(config)

        val t0 = 1000L

        sampler.shouldSample(t0)

        val intervalBefore =
            sampler.samplingIntervalMs

        // Medium change (between 0.05 and 0.3)
        sampler.shouldSample(
            t0 + 1000, 0.15f
        )

        assertEquals(
            intervalBefore,
            sampler.samplingIntervalMs
        )
    }

    // --------------------------------
    // CHANGE MAGNITUDE COMPUTATION
    // --------------------------------

    @Test
    fun `identical pixels have zero magnitude`() {

        val sampler =
            AdaptiveFrameSampler()

        val pixels =
            IntArray(100 * 100) {
                0xFF808080.toInt()
            }

        val magnitude =
            sampler.computeChangeMagnitude(
                pixels, pixels, 100, 100
            )

        assertEquals(0f, magnitude, 0.001f)
    }

    @Test
    fun `completely different pixels have magnitude 1`() {

        val sampler =
            AdaptiveFrameSampler()

        val light =
            IntArray(100 * 100) {
                0xFFFFFFFF.toInt()
            }

        val dark =
            IntArray(100 * 100) {
                0xFF000000.toInt()
            }

        val magnitude =
            sampler.computeChangeMagnitude(
                light, dark, 100, 100, 1
            )

        assertEquals(1f, magnitude, 0.001f)
    }

    @Test
    fun `mismatched sizes return 1`() {

        val sampler =
            AdaptiveFrameSampler()

        val a = IntArray(100)
        val b = IntArray(200)

        assertEquals(
            1f,
            sampler.computeChangeMagnitude(
                a, b, 10, 10
            ),
            0.001f
        )
    }
}
