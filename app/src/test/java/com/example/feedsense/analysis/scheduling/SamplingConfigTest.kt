package com.example.feedsense.analysis.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-12.
 *
 * Configuration validation (§34), versioning (§35) and preset
 * correctness.
 */
class SamplingConfigTest {

    @Test
    fun defaultConfig_isValidAndVersioned() {
        val c = SamplingConfig.DEFAULT
        assertTrue(c.enabled)
        assertTrue(c.minimumAnalysisIntervalMs > 0)
        assertTrue(c.maximumAnalysisIntervalMs >= c.minimumAnalysisIntervalMs)
        assertEquals(SamplingVersion.SAMPLING_VERSION, c.configVersion)
    }

    @Test
    fun negativeMinimum_rejected() {
        assertInvalid {
            SamplingConfig(minimumAnalysisIntervalMs = -1)
        }
    }

    @Test
    fun maximumBelowMinimum_rejected() {
        assertInvalid {
            SamplingConfig(
                minimumAnalysisIntervalMs = 10_000,
                maximumAnalysisIntervalMs = 5_000
            )
        }
    }

    @Test
    fun maximumEqualMinimum_allowed() {
        val c = SamplingConfig.fixed(3_000)
        assertEquals(3_000, c.minimumAnalysisIntervalMs)
        assertEquals(3_000, c.maximumAnalysisIntervalMs)
    }

    @Test
    fun zeroIntervals_allowed() {
        val c = SamplingConfig(
            minimumAnalysisIntervalMs = 0,
            maximumAnalysisIntervalMs = 0
        )
        assertEquals(0, c.minimumAnalysisIntervalMs)
    }

    @Test
    fun negativeStaticContentDistance_rejected() {
        assertInvalid {
            SamplingConfig(staticContentDistance = -1)
        }
    }

    @Test
    fun highChangeBelowStatic_rejected() {
        assertInvalid {
            SamplingConfig(
                staticContentDistance = 10,
                highChangeDistance = 4
            )
        }
    }

    @Test
    fun transitionBelowStatic_rejected() {
        assertInvalid {
            SamplingConfig(
                staticContentDistance = 10,
                transitionDistance = 3
            )
        }
    }

    @Test
    fun pressureOutOfRange_rejected() {
        assertInvalid {
            SamplingConfig(highChangePressureThreshold = 1.5)
        }
        assertInvalid {
            SamplingConfig(highChangePressureThreshold = -0.1)
        }
    }

    @Test
    fun rollingWindowBounds_rejected() {
        assertInvalid { SamplingConfig(rollingWindowSize = 0) }
        assertInvalid { SamplingConfig(rollingWindowSize = 65) }
    }

    @Test
    fun fixedPreset_validatesItsInput() {
        assertInvalid { SamplingConfig.fixed(-1) }
    }

    @Test
    fun baselinePreset_isDisabledPassthrough() {
        assertTrue(!SamplingConfig.BASELINE.enabled)
    }

    @Test
    fun presetsDifferFromEachOther() {
        val set = setOf(
            SamplingConfig.DEFAULT,
            SamplingConfig.BASELINE,
            SamplingConfig.fixed(2_000),
            SamplingConfig.COVERAGE_ONLY
        )
        assertEquals(4, set.size)
        assertNotEquals(
            SamplingConfig.DEFAULT,
            SamplingConfig.COVERAGE_ONLY
        )
    }

    @Test
    fun configVersion_propagates() {
        assertEquals(
            "sampling-v1",
            SamplingConfig.DEFAULT.configVersion
        )
    }

    private inline fun assertInvalid(
        crossinline block: () -> SamplingConfig
    ) {
        try {
            block()
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}