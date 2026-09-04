package com.example.feedsense

import com.example.feedsense.analysis.privacy.PrivacySanitizationConfig
import com.example.feedsense.analysis.privacy.RedactionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-4.
 *
 * PrivacySanitizationConfig: defaults, presets,
 * validation, and equality.
 */
class PrivacySanitizationConfigTest {

    // --------------------------------
    // DEFAULTS
    // --------------------------------

    @Test
    fun `default config is enabled`() {

        assertTrue(
            PrivacySanitizationConfig.DEFAULT
                .enabled
        )
    }

    @Test
    fun `default config has 3 regions`() {

        assertEquals(
            3,
            PrivacySanitizationConfig.DEFAULT
                .regionCount
        )
    }

    @Test
    fun `default config uses REDACT mode`() {

        assertEquals(
            RedactionMode.REDACT,
            PrivacySanitizationConfig.DEFAULT
                .redactionMode
        )
    }

    @Test
    fun `default config has privacy-v1 version`() {

        assertEquals(
            "privacy-v1",
            PrivacySanitizationConfig.DEFAULT
                .sanitizationVersion
        )
    }

    // --------------------------------
    // DISABLED PRESET
    // --------------------------------

    @Test
    fun `DISABLED config is disabled`() {

        assertFalse(
            PrivacySanitizationConfig.DISABLED
                .enabled
        )
    }

    @Test
    fun `DISABLED config has no regions`() {

        assertEquals(
            0,
            PrivacySanitizationConfig.DISABLED
                .regionCount
        )
    }

    // --------------------------------
    // CONSERVATIVE PRESET
    // --------------------------------

    @Test
    fun `CONSERVATIVE config has 5 regions`() {

        assertEquals(
            5,
            PrivacySanitizationConfig.CONSERVATIVE
                .regionCount
        )
    }

    @Test
    fun `CONSERVATIVE config is enabled`() {

        assertTrue(
            PrivacySanitizationConfig.CONSERVATIVE
                .enabled
        )
    }

    // --------------------------------
    // REGION LABELS
    // --------------------------------

    @Test
    fun `region labels are correct`() {

        val config =
            PrivacySanitizationConfig.DEFAULT

        val labels = config.regionLabels

        assertTrue(labels.contains("STATUS_BAR"))
        assertTrue(
            labels.contains("NOTIFICATION")
        )
        assertTrue(
            labels.contains("NAVIGATION")
        )
    }

    // --------------------------------
    // EQUALITY
    // --------------------------------

    @Test
    fun `equal configs are equal`() {

        val c1 = PrivacySanitizationConfig()
        val c2 = PrivacySanitizationConfig()

        assertEquals(c1, c2)
    }

    @Test
    fun `different enabled states not equal`() {

        val c1 = PrivacySanitizationConfig(
            enabled = true
        )

        val c2 = PrivacySanitizationConfig(
            enabled = false
        )

        assertFalse(c1 == c2)
    }
}
