package com.example.feedsense

import com.example.feedsense.analysis.evidence.Evidence
import com.example.feedsense.analysis.evidence.EvidenceQuality
import com.example.feedsense.analysis.evidence.EvidenceSource
import com.example.feedsense.analysis.evidence.EvidenceType
import com.example.feedsense.analysis.evidence.PrivacyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-5.
 *
 * Evidence model: construction, quality, types,
 * provenance, and privacy state.
 */
class EvidenceTest {

    // --------------------------------
    // BASIC CONSTRUCTION
    // --------------------------------

    @Test
    fun `evidence constructs correctly`() {

        val evidence = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.HIGH,
            value = "ipl_cricket",
            supportingCategories =
                listOf("sports"),
            metadata = mapOf(
                "keywordCount" to "3"
            )
        )

        assertEquals(
            EvidenceType.OCR_TEXT,
            evidence.type
        )

        assertEquals(
            EvidenceSource.OCR,
            evidence.source
        )

        assertEquals(
            EvidenceQuality.HIGH,
            evidence.quality
        )

        assertEquals(
            "ipl_cricket",
            evidence.value
        )

        assertEquals(
            listOf("sports"),
            evidence.supportingCategories
        )

        assertEquals(
            "3",
            evidence.metadata["keywordCount"]
        )
    }

    // --------------------------------
    // USABILITY
    // --------------------------------

    @Test
    fun `HIGH quality evidence is usable`() {

        val evidence = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.HIGH,
            value = "test"
        )

        assertTrue(evidence.isUsable)
    }

    @Test
    fun `MEDIUM quality evidence is usable`() {

        val evidence = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.MEDIUM,
            value = "test"
        )

        assertTrue(evidence.isUsable)
    }

    @Test
    fun `LOW quality evidence is usable`() {

        val evidence = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.LOW,
            value = "test"
        )

        assertTrue(evidence.isUsable)
    }

    @Test
    fun `NONE quality evidence is not usable`() {

        val evidence = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.NONE,
            value = "no_text"
        )

        assertFalse(evidence.isUsable)
    }

    // --------------------------------
    // SUPPORT / CONTRADICTION
    // --------------------------------

    @Test
    fun `evidence with supporting categories`() {

        val evidence = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.HIGH,
            value = "test",
            supportingCategories =
                listOf("sports", "gaming")
        )

        assertTrue(evidence.hasSupport)
        assertFalse(evidence.hasContradiction)
    }

    @Test
    fun `evidence with contradicting categories`() {

        val evidence = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.HIGH,
            value = "test",
            contradictingCategories =
                listOf("entertainment")
        )

        assertFalse(evidence.hasSupport)
        assertTrue(evidence.hasContradiction)
    }

    @Test
    fun `evidence with no support or contradiction`() {

        val evidence = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.LOW,
            value = "test"
        )

        assertFalse(evidence.hasSupport)
        assertFalse(evidence.hasContradiction)
    }

    // --------------------------------
    // PRIVACY STATE
    // --------------------------------

    @Test
    fun `default privacy state is UNKNOWN`() {

        val evidence = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.HIGH,
            value = "test"
        )

        assertEquals(
            PrivacyState.UNKNOWN,
            evidence.privacyState
        )
    }

    @Test
    fun `SANITIZED privacy state`() {

        val evidence = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.HIGH,
            value = "test",
            privacyState = PrivacyState.SANITIZED
        )

        assertEquals(
            PrivacyState.SANITIZED,
            evidence.privacyState
        )
    }

    // --------------------------------
    // PROVENANCE
    // --------------------------------

    @Test
    fun `evidence source has version`() {

        assertEquals(
            "evidence-v1",
            EvidenceSource.VERSION
        )
    }

    @Test
    fun `OCR source is correct`() {

        assertEquals(
            "OcrEvidenceExtractor",
            EvidenceSource.OCR.extractorName
        )

        assertEquals(
            "evidence-v1",
            EvidenceSource.OCR.extractorVersion
        )
    }

    // --------------------------------
    // EQUALITY
    // --------------------------------

    @Test
    fun `equal evidence are equal`() {

        val e1 = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.HIGH,
            value = "test"
        )

        val e2 = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.HIGH,
            value = "test"
        )

        assertEquals(e1, e2)
    }

    @Test
    fun `different quality not equal`() {

        val e1 = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.HIGH,
            value = "test"
        )

        val e2 = Evidence(
            type = EvidenceType.OCR_TEXT,
            source = EvidenceSource.OCR,
            quality = EvidenceQuality.LOW,
            value = "test"
        )

        assertFalse(e1 == e2)
    }
}
