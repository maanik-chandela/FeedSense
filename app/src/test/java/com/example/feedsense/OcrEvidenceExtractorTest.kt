package com.example.feedsense

import com.example.feedsense.analysis.evidence.EvidenceQuality
import com.example.feedsense.analysis.evidence.EvidenceType
import com.example.feedsense.analysis.evidence.OcrEvidenceExtractor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-5.
 *
 * OcrEvidenceExtractor: text normalization,
 * keyword evidence, quality assessment.
 */
class OcrEvidenceExtractorTest {

    private val extractor = OcrEvidenceExtractor()

    private fun createTestFile(): File {
        val dir = File(
            System.getProperty("java.io.tmpdir"),
            "ocr_evidence_test"
        )
        dir.mkdirs()
        return File(dir, "test.png").apply {
            createNewFile()
        }
    }

    // --------------------------------
    // EMPTY / NULL TEXT
    // --------------------------------

    @Test
    fun `null text returns NONE quality`() {

        val file = createTestFile()

        val evidence = extractor.extract(
            frameFile = file,
            existingText = null
        )

        assertEquals(1, evidence.size)
        assertEquals(
            EvidenceQuality.NONE,
            evidence[0].quality
        )

        assertEquals(
            "no_text",
            evidence[0].value
        )

        file.delete()
    }

    @Test
    fun `blank text returns NONE quality`() {

        val file = createTestFile()

        val evidence = extractor.extract(
            frameFile = file,
            existingText = "   "
        )

        assertEquals(1, evidence.size)
        assertEquals(
            EvidenceQuality.NONE,
            evidence[0].quality
        )

        file.delete()
    }

    // --------------------------------
    // KEYWORD MATCHING
    // --------------------------------

    @Test
    fun `sports keywords produce sports evidence`() {

        val file = createTestFile()

        val evidence = extractor.extract(
            frameFile = file,
            existingText =
                "IPL 2026 cricket match score"
        )

        assertTrue(evidence.size > 1)

        val sportsEvidence = evidence.find {
            it.value == "keyword:sports"
        }

        assertNotNull(sportsEvidence)
        assertTrue(
            sportsEvidence!!
                .supportingCategories
                .contains("sports")
        )

        file.delete()
    }

    @Test
    fun `education keywords produce education evidence`() {

        val file = createTestFile()

        val evidence = extractor.extract(
            frameFile = file,
            existingText =
                "tutorial learn study math physics"
        )

        val eduEvidence = evidence.find {
            it.value == "keyword:education"
        }

        assertNotNull(eduEvidence)
        assertTrue(
            eduEvidence!!
                .supportingCategories
                .contains("education")
        )

        file.delete()
    }

    @Test
    fun `multiple categories detected`() {

        val file = createTestFile()

        val evidence = extractor.extract(
            frameFile = file,
            existingText =
                "cricket match tutorial learn"
        )

        val categories = evidence
            .filter {
                it.value.startsWith("keyword:")
            }
            .flatMap {
                it.supportingCategories
            }
            .distinct()

        assertTrue(categories.contains("sports"))
        assertTrue(
            categories.contains("education")
        )

        file.delete()
    }

    // --------------------------------
    // QUALITY ASSESSMENT
    // --------------------------------

    @Test
    fun `many keywords produce HIGH quality`() {

        val file = createTestFile()

        val evidence = extractor.extract(
            frameFile = file,
            existingText =
                "cricket football match score goal " +
                        "player team tournament stadium"
        )

        val mainEvidence = evidence[0]
        assertEquals(
            EvidenceQuality.HIGH,
            mainEvidence.quality
        )

        file.delete()
    }

    @Test
    fun `few keywords produce lower quality`() {

        val file = createTestFile()

        val evidence = extractor.extract(
            frameFile = file,
            existingText = "tutorial"
        )

        val mainEvidence = evidence[0]
        assertTrue(
            mainEvidence.quality ==
                    EvidenceQuality.LOW ||
                    mainEvidence.quality ==
                    EvidenceQuality.MEDIUM
        )

        file.delete()
    }

    @Test
    fun `no keywords produce NONE quality`() {

        val file = createTestFile()

        val evidence = extractor.extract(
            frameFile = file,
            existingText =
                "hello world test placeholder"
        )

        val mainEvidence = evidence[0]
        assertEquals(
            EvidenceQuality.NONE,
            mainEvidence.quality
        )

        file.delete()
    }

    // --------------------------------
    // TEXT NORMALIZATION
    // --------------------------------

    @Test
    fun `normalization handles punctuation`() {

        val file = createTestFile()

        val evidence = extractor.extract(
            frameFile = file,
            existingText =
                "IPL!!! 2026... cricket??"
        )

        val sportsEvidence = evidence.find {
            it.value == "keyword:sports"
        }

        assertNotNull(sportsEvidence)

        file.delete()
    }

    @Test
    fun `normalization handles whitespace`() {

        val file = createTestFile()

        val evidence = extractor.extract(
            frameFile = file,
            existingText =
                "  cricket   match   score  "
        )

        val sportsEvidence = evidence.find {
            it.value == "keyword:sports"
        }

        assertNotNull(sportsEvidence)

        file.delete()
    }

    // --------------------------------
    // METADATA
    // --------------------------------

    @Test
    fun `evidence contains text length`() {

        val file = createTestFile()

        val evidence = extractor.extract(
            frameFile = file,
            existingText = "cricket match"
        )

        val mainEvidence = evidence[0]
        assertNotNull(
            mainEvidence.metadata["textLength"]
        )

        file.delete()
    }

    @Test
    fun `evidence contains keyword hits`() {

        val file = createTestFile()

        val evidence = extractor.extract(
            frameFile = file,
            existingText = "cricket match score"
        )

        val mainEvidence = evidence[0]
        assertNotNull(
            mainEvidence.metadata["keywordHits"]
        )

        file.delete()
    }
}
