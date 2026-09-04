package com.example.feedsense

import com.example.feedsense.analysis.dedup.DeduplicationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-3.
 *
 * DeduplicationConfig: validation, defaults, and presets.
 */
class DeduplicationConfigTest {

    // --------------------------------
    // DEFAULTS
    // --------------------------------

    @Test
    fun `default config is enabled`() {

        assertTrue(
            DeduplicationConfig.DEFAULT.enabled
        )
    }

    @Test
    fun `default definitelySameThreshold is 0`() {

        assertEquals(
            0,
            DeduplicationConfig.DEFAULT
                .definitelySameThreshold
        )
    }

    @Test
    fun `default nearDuplicateThreshold is 5`() {

        assertEquals(
            5,
            DeduplicationConfig.DEFAULT
                .nearDuplicateThreshold
        )
    }

    @Test
    fun `default minimumSamplingIntervalMs is 1000`() {

        assertEquals(
            1000L,
            DeduplicationConfig.DEFAULT
                .minimumSamplingIntervalMs
        )
    }

    @Test
    fun `default maximumSamplingIntervalMs is 10000`() {

        assertEquals(
            10000L,
            DeduplicationConfig.DEFAULT
                .maximumSamplingIntervalMs
        )
    }

    @Test
    fun `default forceKeepIntervalMs is 15000`() {

        assertEquals(
            15000L,
            DeduplicationConfig.DEFAULT
                .forceKeepIntervalMs
        )
    }

    // --------------------------------
    // PRESETS
    // --------------------------------

    @Test
    fun `DISABLED preset is disabled`() {

        assertFalse(
            DeduplicationConfig.DISABLED.enabled
        )
    }

    @Test
    fun `CONSERVATIVE has wider threshold`() {

        assertTrue(
            DeduplicationConfig.CONSERVATIVE
                .nearDuplicateThreshold >
                    DeduplicationConfig.DEFAULT
                        .nearDuplicateThreshold
        )
    }

    @Test
    fun `CONSERVATIVE has shorter force-keep`() {

        assertTrue(
            DeduplicationConfig.CONSERVATIVE
                .forceKeepIntervalMs <
                    DeduplicationConfig.DEFAULT
                        .forceKeepIntervalMs
        )
    }

    // --------------------------------
    // VALIDATION
    // --------------------------------

    @Test
    fun `negative definitelySameThreshold throws`() {

        try {
            DeduplicationConfig(
                definitelySameThreshold = -1
            )
            assertTrue(
                "expected exception",
                false
            )
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains(">= 0"))
        }
    }

    @Test
    fun `nearDuplicate below definitelySame throws`() {

        try {
            DeduplicationConfig(
                definitelySameThreshold = 5,
                nearDuplicateThreshold = 3
            )
            assertTrue(
                "expected exception",
                false
            )
        } catch (e: IllegalArgumentException) {
            assertTrue(
                e.message!!
                    .contains(">=")
            )
        }
    }

    @Test
    fun `zero minimumSamplingIntervalMs throws`() {

        try {
            DeduplicationConfig(
                minimumSamplingIntervalMs = 0L
            )
            assertTrue(
                "expected exception",
                false
            )
        } catch (e: IllegalArgumentException) {
            assertTrue(
                e.message!!.contains("> 0")
            )
        }
    }

    @Test
    fun `maximum below minimum throws`() {

        try {
            DeduplicationConfig(
                minimumSamplingIntervalMs = 5000L,
                maximumSamplingIntervalMs = 1000L
            )
            assertTrue(
                "expected exception",
                false
            )
        } catch (e: IllegalArgumentException) {
            assertTrue(
                e.message!!
                    .contains(">=")
            )
        }
    }

    @Test
    fun `zero forceKeepIntervalMs throws`() {

        try {
            DeduplicationConfig(
                forceKeepIntervalMs = 0L
            )
            assertTrue(
                "expected exception",
                false
            )
        } catch (e: IllegalArgumentException) {
            assertTrue(
                e.message!!.contains("> 0")
            )
        }
    }

    // --------------------------------
    // CUSTOM VALID CONFIG
    // --------------------------------

    @Test
    fun `custom valid config creates successfully`() {

        val config = DeduplicationConfig(
            enabled = false,
            definitelySameThreshold = 2,
            nearDuplicateThreshold = 6,
            minimumSamplingIntervalMs = 500L,
            maximumSamplingIntervalMs = 8000L,
            forceKeepIntervalMs = 12000L
        )

        assertFalse(config.enabled)
        assertEquals(2, config.definitelySameThreshold)
        assertEquals(6, config.nearDuplicateThreshold)
        assertEquals(500L, config.minimumSamplingIntervalMs)
        assertEquals(8000L, config.maximumSamplingIntervalMs)
        assertEquals(12000L, config.forceKeepIntervalMs)
    }

    // --------------------------------
    // EQUALITY
    // --------------------------------

    @Test
    fun `same parameters produce equal configs`() {

        val a = DeduplicationConfig(
            nearDuplicateThreshold = 7
        )

        val b = DeduplicationConfig(
            nearDuplicateThreshold = 7
        )

        assertEquals(a, b)
    }
}
