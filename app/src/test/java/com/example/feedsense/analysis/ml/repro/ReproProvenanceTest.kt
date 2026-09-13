package com.example.feedsense.analysis.ml.repro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/*
 * Milestone 8B-15-2.
 *
 * PROVENANCE: an ordered chain of distinguishable transitions
 * (source -> download -> conversion -> quantization -> deployment)
 * that is never collapsed into one "modelVersion" field. A
 * converted/quantized step must carry an explicit conversion
 * record.
 */
class ReproProvenanceTest {

    @Test
    fun `same provenance produces equal identity`() {
        assertEquals(ReproFix.provenance, ReproFix.provenance.copy())
    }

    @Test
    fun `changed step order produces different provenance`() {
        val swapped = ReproArtifactProvenance(
            steps = ReproFix.provenance.steps.reversed(),
            terminalArtifactId = "deployment"
        )
        assertNotEquals(ReproFix.provenance, swapped)
    }

    @Test
    fun `same provenance produces identical canonical serialization`() {
        assertEquals(
            ReproCanonicalSerializer.serializeProvenance(ReproFix.provenance),
            ReproCanonicalSerializer.serializeProvenance(ReproFix.provenance.copy())
        )
    }

    @Test
    fun `terminal artifact must name one of the steps`() {
        try {
            val bad = ReproArtifactProvenance(
                steps = ReproFix.provenance.steps,
                terminalArtifactId = "not-a-step"
            )
            throw AssertionError("foreign terminal artifact must be rejected, got $bad")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `converted step requires an explicit conversion record`() {
        try {
            ReproArtifactProvenance(
                steps = listOf(
                    ReproProvenanceStep(
                        role = ProvenanceRole.CONVERTED,
                        artifactId = "converted",
                        conversion = null
                    )
                ),
                terminalArtifactId = "converted"
            )
            throw AssertionError("CONVERTED step without conversion must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `quantized step does not need a format-changing conversion record`() {
        // Quantization is same-format (TFLite -> TFLite) and its
        // details live in ReproQuantizationIdentity, so a QUANTIZED
        // lineage step with no conversion record is valid.
        val chain = ReproArtifactProvenance(
            steps = listOf(
                ReproProvenanceStep(ProvenanceRole.ORIGINAL, "source"),
                ReproProvenanceStep(
                    role = ProvenanceRole.QUANTIZED,
                    artifactId = "quantized",
                    conversion = null
                ),
                ReproProvenanceStep(ProvenanceRole.DEPLOYMENT, "deployment")
            ),
            terminalArtifactId = "deployment"
        )
        assertEquals("deployment", chain.terminalArtifactId)
    }

    @Test
    fun `conversion must change the artifact format`() {
        try {
            ReproConversionRecord(
                sourceFormat = ReproArtifactFormat.TFLITE,
                destinationFormat = ReproArtifactFormat.TFLITE,
                conversionTool = "tf-converter",
                conversionToolVersion = "2.20"
            )
            throw AssertionError("same-format conversion must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `full chain serializes with every transition distinguishable`() {
        val chain = ReproArtifactProvenance(
            steps = listOf(
                ReproProvenanceStep(ProvenanceRole.ORIGINAL, "source"),
                ReproProvenanceStep(ProvenanceRole.DOWNLOADED, "downloaded"),
                ReproProvenanceStep(
                    role = ProvenanceRole.CONVERTED,
                    artifactId = "converted",
                    conversion = ReproConversionRecord(
                        sourceFormat = ReproArtifactFormat.ONNX,
                        destinationFormat = ReproArtifactFormat.TFLITE,
                        conversionTool = "tf-converter",
                        conversionToolVersion = "2.20"
                    )
                ),
                ReproProvenanceStep(ProvenanceRole.QUANTIZED, "quantized"),
                ReproProvenanceStep(ProvenanceRole.DEPLOYMENT, "deployment")
            ),
            terminalArtifactId = "deployment"
        )
        val rendered = ReproCanonicalSerializer.serializeProvenance(chain)
        assert(rendered.contains("source"))
        assert(rendered.contains("step0"))
        assert(rendered.contains("role=ORIGINAL"))
        assert(rendered.contains("conversion{"))
    }
}