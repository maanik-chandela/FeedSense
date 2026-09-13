package com.example.feedsense.analysis.ml.selection

import com.example.feedsense.analysis.ml.ModelFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-1.
 *
 * ModelCandidate is a research RECORD: it must be total enough
 * to serialize (blank fields break reproducibility) but never
 * carries fabricated numeric precision.
 */
class ModelCandidateTest {

    private fun base(): ModelCandidate {
        return ModelCandidate(
            candidateId = "x1",
            familyId = "A",
            familyName = "Test family",
            name = "Test model",
            architecture = "test",
            runtimes = listOf(ModelFormat.TFLITE),
            status = CandidateStatus.REJECTED,
            statusReason = "test-only",
            license = "UNKNOWN",
            licenseEvidence = EvidenceLevel.UNKNOWN
        )
    }

    @Test
    fun `blank candidateId is rejected`() {
        assertThrows { base().copy(candidateId = "  ") }
    }

    @Test
    fun `blank license is rejected`() {
        assertThrows { base().copy(license = "") }
    }

    @Test
    fun `blank statusReason is rejected`() {
        assertThrows { base().copy(statusReason = " ") }
    }

    @Test
    fun `shortlisted candidate cannot carry an unverified license`() {
        assertThrows {
            base().copy(status = CandidateStatus.SHORTLISTED, license = "UNKNOWN")
        }
    }

    @Test
    fun `shortlisted candidate with verified license is accepted`() {
        val ok = base().copy(
            status = CandidateStatus.SHORTLISTED,
            license = "Apache-2.0",
            licenseEvidence = EvidenceLevel.DOCUMENTED_BY_SOURCE
        )
        assertEquals(CandidateStatus.SHORTLISTED, ok.status)
    }

    @Test
    fun `duplicate runtimes are rejected`() {
        assertThrows {
            base().copy(runtimes = listOf(ModelFormat.TFLITE, ModelFormat.TFLITE))
        }
    }

    @Test
    fun `empty runtimes are rejected`() {
        assertThrows { base().copy(runtimes = emptyList()) }
    }

    @Test
    fun `androidMinApi below feedsense minSdk 24 is rejected`() {
        assertThrows { base().copy(androidMinApi = 21) }
        assertEquals(24, base().copy(androidMinApi = 24).androidMinApi)
    }

    @Test
    fun `negative parameters are rejected`() {
        assertThrows { base().copy(parametersMillions = -1.0) }
    }

    @Test
    fun `scores map keyed only by defined criteria is accepted`() {
        val scored = base().copy(
            scores = mapOf(
                EvaluationCriterion.MODEL_SIZE_MB to CriterionScore(
                    EvaluationCriterion.MODEL_SIZE_MB,
                    CriterionRating.STRONG,
                    EvidenceLevel.ENGINEERING_ESTIMATE,
                    "small"
                )
            )
        )
        assertTrue(scored.hasScore(EvaluationCriterion.MODEL_SIZE_MB))
        assertTrue(!scored.hasScore(EvaluationCriterion.PEAK_RAM))
    }

    @Test
    fun `source references must be absolute with a title and date`() {
        val good = SourceReference(
            "https://example.com/doc",
            "Example",
            "2026-09-06",
            "claim"
        )
        assertEquals("https://example.com/doc", good.url)
        // repo-internal references use file:// (allowed)
        SourceReference("file://docs/ml-inference.md", "8B-14", "2026-09-06", "claim")

        assertThrows {
            SourceReference("relative/path", "Example", "2026-09-06", "claim")
        }
        assertThrows {
            SourceReference("https://example.com/doc", " ", "2026-09-06", "claim")
        }
        assertThrows {
            SourceReference("https://example.com/doc", "Example", "", "claim")
        }
    }

    @Test
    fun `published metrics must be non-blank and render deterministically`() {
        assertThrows {
            PublishedMetric("", "3.8", EvidenceLevel.DOCUMENTED_BY_SOURCE, "ctx")
        }
        assertThrows {
            PublishedMetric("params", "", EvidenceLevel.DOCUMENTED_BY_SOURCE, "ctx")
        }
        val metric = PublishedMetric(
            "params",
            "3.8",
            EvidenceLevel.DOCUMENTED_BY_SOURCE,
            "arXiv"
        )
        assertEquals("params: 3.8 (DOCUMENTED_BY_SOURCE, arXiv)", metric.rendered)
    }

    @Test
    fun `collection helpers filter stably`() {
        val a = base().copy(candidateId = "a-2")
        val b = base().copy(candidateId = "a-1")
        val list = listOf(a, b)

        assertEquals(a, list.byId("a-2"))
        assertEquals(null, list.byId("missing"))
        assertEquals(listOf(b, a), list.byFamily("A"))
    }

    private fun assertThrows(block: () -> Any?) {
        var threw = false
        try {
            block()
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("expected IllegalArgumentException", threw)
    }
}