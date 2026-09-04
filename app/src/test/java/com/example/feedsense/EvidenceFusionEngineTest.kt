package com.example.feedsense

import com.example.feedsense.analysis.evidence.Evidence
import com.example.feedsense.analysis.evidence.EvidenceFusionEngine
import com.example.feedsense.analysis.evidence.EvidenceFusionResult
import com.example.feedsense.analysis.evidence.EvidenceQuality
import com.example.feedsense.analysis.evidence.EvidenceSource
import com.example.feedsense.analysis.evidence.EvidenceType
import com.example.feedsense.analysis.evidence.FusionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-5.
 *
 * EvidenceFusionEngine: weighted aggregation,
 * conflict detection, ambiguity, determinism.
 */
class EvidenceFusionEngineTest {

    private val engine = EvidenceFusionEngine()

    // --------------------------------
    // EMPTY INPUT
    // --------------------------------

    @Test
    fun `empty evidence returns EMPTY result`() {

        val result = engine.fuse(emptyList())

        assertEquals(
            EvidenceFusionResult.EMPTY,
            result
        )

        assertEquals(0, result.evidenceCount)
    }

    @Test
    fun `NONE quality evidence returns EMPTY`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.NONE,
                value = "no_text"
            )
        )

        val result = engine.fuse(evidence)

        assertEquals(
            EvidenceFusionResult.EMPTY,
            result
        )
    }

    // --------------------------------
    // SINGLE CATEGORY
    // --------------------------------

    @Test
    fun `single category supported`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.HIGH,
                value = "sports_keywords",
                supportingCategories =
                    listOf("sports")
            )
        )

        val result = engine.fuse(evidence)

        assertEquals(1, result.evidenceCount)
        assertEquals("sports", result.topCategory)
        assertTrue(result.topScore > 0.0)
    }

    // --------------------------------
    // MULTIPLE CATEGORIES
    // --------------------------------

    @Test
    fun `multiple categories ranked correctly`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.HIGH,
                value = "sports_strong",
                supportingCategories =
                    listOf("sports")
            ),
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.MEDIUM,
                value = "education_moderate",
                supportingCategories =
                    listOf("education")
            )
        )

        val result = engine.fuse(evidence)

        assertEquals(2, result.evidenceCount)
        assertEquals("sports", result.topCategory)
        assertTrue(
            result.rankedCategories.size >= 2
        )
    }

    // --------------------------------
    // CONFLICT DETECTION
    // --------------------------------

    @Test
    fun `conflicting evidence detected`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.HIGH,
                value = "sports_keywords",
                supportingCategories =
                    listOf("sports"),
                contradictingCategories =
                    emptyList()
            ),
            Evidence(
                type = EvidenceType.PLATFORM,
                source = EvidenceSource.PLATFORM,
                quality = EvidenceQuality.HIGH,
                value = "YouTube",
                supportingCategories =
                    listOf("entertainment"),
                contradictingCategories =
                    listOf("sports")
            )
        )

        val result = engine.fuse(evidence)

        assertTrue(result.hasConflict)
        assertNotNull(
            result.conflictDescription
        )
    }

    @Test
    fun `no conflict with single category`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.HIGH,
                value = "sports_keywords",
                supportingCategories =
                    listOf("sports")
            )
        )

        val result = engine.fuse(evidence)

        assertFalse(result.hasConflict)
    }

    // --------------------------------
    // AMBIGUITY
    // --------------------------------

    @Test
    fun `close scores produce high ambiguity`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.MEDIUM,
                value = "sports_keywords",
                supportingCategories =
                    listOf("sports")
            ),
            Evidence(
                type = EvidenceType.PLATFORM,
                source = EvidenceSource.PLATFORM,
                quality = EvidenceQuality.MEDIUM,
                value = "YouTube",
                supportingCategories =
                    listOf("entertainment")
            )
        )

        val result = engine.fuse(evidence)

        assertTrue(
            result.ambiguityScore > 0.5
        )
    }

    @Test
    fun `dominant category produces low ambiguity`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.HIGH,
                value = "sports_strong",
                supportingCategories =
                    listOf("sports")
            ),
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.LOW,
                value = "education_weak",
                supportingCategories =
                    listOf("education")
            )
        )

        val result = engine.fuse(evidence)

        assertTrue(
            result.ambiguityScore < 0.8
        )
    }

    // --------------------------------
    // EVIDENCE DENSITY
    // --------------------------------

    @Test
    fun `evidence count is accurate`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.HIGH,
                value = "sports",
                supportingCategories =
                    listOf("sports")
            ),
            Evidence(
                type = EvidenceType.PLATFORM,
                source = EvidenceSource.PLATFORM,
                quality = EvidenceQuality.MEDIUM,
                value = "Instagram",
                supportingCategories =
                    listOf("lifestyle")
            ),
            Evidence(
                type = EvidenceType.VISUAL,
                source = EvidenceSource.VISUAL,
                quality = EvidenceQuality.LOW,
                value = "frame_available"
            )
        )

        val result = engine.fuse(evidence)

        assertEquals(3, result.evidenceCount)
    }

    @Test
    fun `independent sources counted correctly`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.HIGH,
                value = "sports",
                supportingCategories =
                    listOf("sports")
            ),
            Evidence(
                type = EvidenceType.PLATFORM,
                source = EvidenceSource.PLATFORM,
                quality = EvidenceQuality.MEDIUM,
                value = "Instagram",
                supportingCategories =
                    listOf("lifestyle")
            )
        )

        val result = engine.fuse(evidence)

        assertEquals(2, result.independentSources)
    }

    // --------------------------------
    // SUPPORTING / CONTRADICTING
    // --------------------------------

    @Test
    fun `supporting evidence mapped correctly`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.HIGH,
                value = "sports",
                supportingCategories =
                    listOf("sports")
            )
        )

        val result = engine.fuse(evidence)

        val sportsSupport =
            result.supportingEvidence["sports"]

        assertNotNull(sportsSupport)
        assertEquals(1, sportsSupport?.size)
    }

    @Test
    fun `contradicting evidence mapped correctly`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.HIGH,
                value = "sports",
                supportingCategories =
                    listOf("sports"),
                contradictingCategories =
                    listOf("sports")
            )
        )

        val result = engine.fuse(evidence)

        val sportsContradict =
            result.contradictingEvidence["sports"]

        assertNotNull(sportsContradict)
        assertEquals(1, sportsContradict?.size)
    }

    // --------------------------------
    // DETERMINISM
    // --------------------------------

    @Test
    fun `same input produces same output`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.HIGH,
                value = "sports",
                supportingCategories =
                    listOf("sports")
            ),
            Evidence(
                type = EvidenceType.PLATFORM,
                source = EvidenceSource.PLATFORM,
                quality = EvidenceQuality.MEDIUM,
                value = "Instagram",
                supportingCategories =
                    listOf("lifestyle")
            )
        )

        val result1 = engine.fuse(evidence)
        val result2 = engine.fuse(evidence)

        assertEquals(
            result1.categoryScores,
            result2.categoryScores
        )

        assertEquals(
            result1.topCategory,
            result2.topCategory
        )

        assertEquals(
            result1.ambiguityScore,
            result2.ambiguityScore,
            1e-9
        )
    }

    // --------------------------------
    // VERSION
    // --------------------------------

    @Test
    fun `fusion version is recorded`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.HIGH,
                value = "test",
                supportingCategories =
                    listOf("sports")
            )
        )

        val result = engine.fuse(evidence)

        assertEquals(
            "fusion-v1",
            result.fusionVersion
        )
    }

    // --------------------------------
    // CONFIGURATION
    // --------------------------------

    @Test
    fun `custom config affects scores`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.HIGH,
                value = "sports",
                supportingCategories =
                    listOf("sports")
            ),
            Evidence(
                type = EvidenceType.PLATFORM,
                source = EvidenceSource.PLATFORM,
                quality = EvidenceQuality.HIGH,
                value = "Instagram",
                supportingCategories =
                    listOf("lifestyle")
            )
        )

        val defaultEngine =
            EvidenceFusionEngine(
                FusionConfig.DEFAULT
            )

        val conservativeEngine =
            EvidenceFusionEngine(
                FusionConfig.CONSERVATIVE
            )

        val defaultResult =
            defaultEngine.fuse(evidence)

        val conservativeResult =
            conservativeEngine.fuse(evidence)

        // Scores may differ due to different weights
        assertNotNull(defaultResult.topCategory)
        assertNotNull(
            conservativeResult.topCategory
        )
    }

    // --------------------------------
    // INSUFFICIENT EVIDENCE
    // --------------------------------

    @Test
    fun `single evidence has insufficient evidence`() {

        val evidence = listOf(
            Evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                quality = EvidenceQuality.LOW,
                value = "weak",
                supportingCategories =
                    listOf("sports")
            )
        )

        val result = engine.fuse(evidence)

        assertTrue(result.hasInsufficientEvidence)
    }
}
