package com.example.feedsense.analysis.privacy

import com.example.feedsense.analysis.privacy.PrivacyContentClass.PRIVATE_INFORMATION
import com.example.feedsense.analysis.privacy.PrivacyContentClass.RESEARCH_RELEVANT_CONTENT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-10.
 *
 * Privacy region taxonomy sanity checks.
 *
 * The taxonomy distinguishes PRIVATE information from
 * RESEARCH-relevant content. Sanitization removes the former
 * while preserving the latter.
 */
class PrivacyRegionTypeTest {

    @Test
    fun `taxonomy has exactly 8 controlled types`() {
        assertEquals(8, PrivacyRegionType.ALL.size)
    }

    @Test
    fun `all types have stable non-empty labels`() {
        PrivacyRegionType.ALL.forEach { type ->
            assertEquals(type.name, type.label)
            assertTrue(type.label.isNotBlank())
        }
    }

    @Test
    fun `private types are classified as private information`() {
        PrivacyRegionType.ALL.forEach { type ->
            assertSame(PRIVATE_INFORMATION, type.contentClass)
        }
    }

    @Test
    fun `research relevance is never privacy content`() {
        // FeedSense RESEARCH categories are explicitly NOT
        // private information. "IPL"/"RCB"/"YouTube"/
        // "Netflix" are a distinct content class.
        assertNotSame(
            PRIVATE_INFORMATION,
            RESEARCH_RELEVANT_CONTENT
        )
        assertTrue(
            PrivacyRegionType.ALL.all {
                it.contentClass == PRIVATE_INFORMATION
            }
        )
    }

    @Test
    fun `parsing via label round-trips`() {
        PrivacyRegionType.ALL.forEach { type ->
            assertEquals(
                type,
                PrivacyRegionType.fromLabel(type.label)
            )
        }
    }

    @Test
    fun `unknown label falls back to conservative_UNKNOWN`() {
        assertSame(
            PrivacyRegionType.UNKNOWN_SENSITIVE_REGION,
            PrivacyRegionType.fromLabel("MADE_UP_LABEL")
        )
        assertSame(
            PrivacyRegionType.UNKNOWN_SENSITIVE_REGION,
            PrivacyRegionType.fromLabel(null)
        )
    }

    @Test
    fun `PRIVATE vs RESEARCH distinction is explicit`() {
        // Regression guard: research content must never be
        // enumerated as a privacy region type.
        val researchLookalikes = listOf(
            "IPL", "RCB", "YOUTUBE", "NETFLIX", "CRICKET"
        )
        researchLookalikes.forEach { name ->
            val parsed =
                PrivacyRegionType.fromLabel(name)
            assertSame(
                PrivacyRegionType.UNKNOWN_SENSITIVE_REGION,
                parsed
            )
        }
    }

    @Test
    fun `privacy region validates confidence range`() {
        // Valid
        PrivacyRegion(
            type = PrivacyRegionType.NOTIFICATION,
            bounds = ProtectedRegion.NOTIFICATION_REGION,
            confidence = 1.0
        )
        PrivacyRegion(
            type = PrivacyRegionType.SYSTEM_UI,
            bounds = ProtectedRegion.NAVIGATION_BAR,
            confidence = 0.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `privacy region rejects out-of-range confidence`() {
        PrivacyRegion(
            type = PrivacyRegionType.SYSTEM_UI,
            bounds = ProtectedRegion.STATUS_BAR,
            confidence = 1.5
        )
    }

    @Test
    fun `region converts back to protected region preserving label`() {
        val region = PrivacyRegion(
            type = PrivacyRegionType.NOTIFICATION,
            bounds = ProtectedRegion.NOTIFICATION_REGION
        )
        val legacy = region.toProtectedRegion()
        assertEquals(region.label, legacy.label)
        assertEquals(region.bounds.x, legacy.x, 0.0)
        assertEquals(region.bounds.y, legacy.y, 0.0)
    }

    @Test
    fun `legacy protected region maps conservatively to typed region`() {
        // The legacy preset label "STATUS_BAR" is not a
        // taxonomy label, so generic mapping falls back to
        // the conservative UNKNOWN_SENSITIVE_REGION. Fixed
        // UI presets are typed as SYSTEM_UI/NOTIFICATION via
        // PRIVACY_SYSTEM_UI_REGIONS instead.
        val typed =
            ProtectedRegion.STATUS_BAR.toPrivacyRegion()
        assertEquals(
            PrivacyRegionType.UNKNOWN_SENSITIVE_REGION,
            typed.type
        )
        // The typed fixed-system regions DO carry the
        // expected taxonomy types.
        assertTrue(
            PRIVACY_SYSTEM_UI_REGIONS.any {
                it.type == PrivacyRegionType.SYSTEM_UI
            }
        )
        assertTrue(
            PRIVACY_SYSTEM_UI_REGIONS.any {
                it.type == PrivacyRegionType.NOTIFICATION
            }
        )
    }
}