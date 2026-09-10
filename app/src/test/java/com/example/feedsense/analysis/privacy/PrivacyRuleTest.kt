package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Milestone 8B-13.
 *
 * Rule table behavior across the three policy modes.
 */
class PrivacyRuleTest {

    @Test
    fun `research mode leaves NOTHING un-anonymized that is enabled`() {
        val table = PrivacyRuleTable.DEFAULT
        PrivacyRegionType.ALL.forEach { type ->
            val transform = table.transformationFor(type)
            assertEquals(
                "research table must decide every type",
                true,
                transform != PrivacyTransformation.NONE
            )
        }
    }

    @Test
    fun `research mode blurs chrome and masks identifiers`() {
        assertEquals(
            PrivacyTransformation.BLUR,
            PrivacyRuleTable.DEFAULT.transformationFor(PrivacyRegionType.SYSTEM_UI)
        )
        assertEquals(
            PrivacyTransformation.BLUR,
            PrivacyRuleTable.DEFAULT.transformationFor(PrivacyRegionType.NOTIFICATION)
        )
        assertEquals(
            PrivacyTransformation.MASK,
            PrivacyRuleTable.DEFAULT.transformationFor(PrivacyRegionType.PRIVATE_TEXT)
        )
        assertEquals(
            PrivacyTransformation.MASK,
            PrivacyRuleTable.DEFAULT.transformationFor(PrivacyRegionType.PERSONAL_IDENTIFIER)
        )
    }

    @Test
    fun `balanced mode blurs private text instead of masking`() {
        assertEquals(
            PrivacyTransformation.BLUR,
            PrivacyRuleTable.forPolicyMode(PrivacyPolicyMode.BALANCED)
                .transformationFor(PrivacyRegionType.PRIVATE_TEXT)
        )
        assertEquals(
            PrivacyTransformation.MASK,
            PrivacyRuleTable.forPolicyMode(PrivacyPolicyMode.BALANCED)
                .transformationFor(PrivacyRegionType.PERSONAL_IDENTIFIER)
        )
    }

    @Test
    fun `strict mode masks everything`() {
        val table = PrivacyRuleTable.forPolicyMode(PrivacyPolicyMode.STRICT)
        PrivacyRegionType.ALL.forEach { type ->
            assertEquals(
                PrivacyTransformation.MASK,
                table.transformationFor(type)
            )
        }
        // STRICT masks even under unknown-risk conditions.
        assertEquals(
            PrivacyTransformation.MASK,
            table.transformationFor(
                PrivacyRegionType.UNKNOWN_SENSITIVE_REGION,
                unknownRisk = true
            )
        )
    }

    @Test
    fun `unknown-risk resolution respects appliedWhenUnknownRisk`() {
        val research = PrivacyRuleTable.DEFAULT
        assertEquals(
            PrivacyTransformation.BLUR,
            research.transformationFor(PrivacyRegionType.UNKNOWN_SENSITIVE_REGION)
        )
        // Under OCR-unavailable unknown-risk, research still blurs
        // (never claims safety, never leaves content).
        assertEquals(
            PrivacyTransformation.BLUR,
            research.transformationFor(
                PrivacyRegionType.UNKNOWN_SENSITIVE_REGION,
                unknownRisk = true
            )
        )
    }

    @Test
    fun `priority wins when several rules share a type`() {
        val table = PrivacyRuleTable.custom(
            listOf(
                PrivacyRule(
                    PrivacyRegionType.NOTIFICATION,
                    PrivacyTransformation.BLUR,
                    priority = 2
                ),
                PrivacyRule(
                    PrivacyRegionType.NOTIFICATION,
                    PrivacyTransformation.NONE,
                    priority = 10
                )
            )
        )
        assertEquals(
            PrivacyTransformation.NONE,
            table.transformationFor(PrivacyRegionType.NOTIFICATION)
        )
    }

    @Test
    fun `empty custom rules are rejected`() {
        try {
            PrivacyRuleTable.custom(emptyList())
            throw AssertionError("should have thrown")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `policy mode gating for drops is controlled`() {
        assertEquals(false, PrivacyPolicyMode.RESEARCH.allowsFrameDrop)
        assertEquals(false, PrivacyPolicyMode.BALANCED.allowsFrameDrop)
        assertEquals(true, PrivacyPolicyMode.STRICT.allowsFrameDrop)
    }
}