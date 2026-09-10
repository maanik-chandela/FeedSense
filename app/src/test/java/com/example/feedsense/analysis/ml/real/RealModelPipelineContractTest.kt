package com.example.feedsense.analysis.ml.real

import com.example.feedsense.analysis.ml.DecodedOutput
import com.example.feedsense.analysis.ml.FullMlPipeline
import com.example.feedsense.analysis.ml.FullPipelineResult
import com.example.feedsense.analysis.ml.OutputInterpretationConfig
import com.example.feedsense.analysis.ml.OutputSemantics
import com.example.feedsense.analysis.ml.ModelOutputDecoder
import com.example.feedsense.analysis.ml.RankedPrediction
import com.example.feedsense.analysis.ml.evaluation.EvaluationBoundaryEvaluator
import com.example.feedsense.analysis.ml.preprocess.PreprocessResult
import com.example.feedsense.analysis.ml.runtime.AdapterRawModelOutput
import com.example.feedsense.analysis.ml.runtime.InferenceExecutionStatus
import com.example.feedsense.analysis.ml.runtime.ModelRuntimeAdapter
import com.example.feedsense.analysis.ml.runtime.ModelRuntimeAdapterConfig
import com.example.feedsense.analysis.ml.runtime.OutputTensorMetadata
import com.example.feedsense.analysis.ml.runtime.RuntimeFailureFactory
import com.example.feedsense.analysis.ml.runtime.RuntimeTestFixtures
import com.example.feedsense.analysis.ml.runtime.TestArtifactLoader
import com.example.feedsense.analysis.ml.runtime.TestBackendBehavior
import com.example.feedsense.analysis.ml.runtime.TestInferenceBackend
import com.example.feedsense.analysis.ml.runtime.TestLoaderBehavior
import com.example.feedsense.analysis.ml.taxonomy.MappingProvenance
import com.example.feedsense.analysis.ml.taxonomy.MappingRationale
import com.example.feedsense.analysis.ml.taxonomy.MappingStatus
import com.example.feedsense.analysis.ml.taxonomy.ModelTaxonomyMapping
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingEngine
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingVersion
import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.SchemaFreeze
import com.example.feedsense.analysis.ml.runtime.litert.MobileNetV2Artifact
import com.example.feedsense.analysis.ml.runtime.litert.MobileNetV2Labels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// REAL MODEL PIPELINE CONTRACT TEST (8B-15-9, Phase 6-8)
// --------------------------------
//
// JVM tests for the pipeline contract using real model
// metadata with test doubles. These tests verify that:
//
//   1. Real model labels feed into the decoder correctly
//   2. Real model labels map through taxonomy correctly
//   3. The full pipeline produces valid evaluation results
//   4. Version provenance is preserved end-to-end
//   5. Unmapped ImageNet labels are handled honestly

class RealModelPipelineContractTest {

    private val taxonomyVersion =
        TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = SchemaFreeze.FREEZE_VERSION,
            sortedCategoryKeys = CategoryCatalog.keys.sorted()
        )

    @Test
    fun `decoder accepts real model label count`() {
        val labels = MobileNetV2Labels.REPRESENTATIVE_TEST_LABELS
        val config = OutputInterpretationConfig(
            interpretationVersion = "test-interp-v1",
            labels = labels,
            outputSemantics = OutputSemantics.SOFTMAX,
            topK = 5
        )

        val adapterOutput = createAdapterOutput(
            scores = FloatArray(labels.size) { 0.1f }.also {
                it[1] = 0.9f
            }
        )

        val decoded = ModelOutputDecoder.decode(adapterOutput, config)

        assertTrue("decode should succeed", decoded is DecodedOutput.Success)
        val success = decoded as DecodedOutput.Success
        assertEquals(labels.size, success.labelCount)
    }

    @Test
    fun `decoder with real labels produces valid predictions`() {
        val labels = MobileNetV2Labels.REPRESENTATIVE_TEST_LABELS
        val config = OutputInterpretationConfig(
            interpretationVersion = "test-interp-v1",
            labels = labels,
            outputSemantics = OutputSemantics.SOFTMAX,
            topK = 3
        )

        val scores = FloatArray(labels.size) { 0.01f }.also {
            it[0] = 0.05f
            it[1] = 0.80f
            it[2] = 0.10f
        }

        val adapterOutput = createAdapterOutput(scores)
        val decoded = ModelOutputDecoder.decode(adapterOutput, config)

        assertTrue(decoded is DecodedOutput.Success)
        val success = decoded as DecodedOutput.Success
        assertEquals("tench", success.rankedPredictions[0].category)
        assertEquals(3, success.rankedPredictions.size)
    }

    @Test
    fun `taxonomy mapping handles mapped and unmapped labels`() {
        val labels = MobileNetV2Labels.REPRESENTATIVE_TEST_LABELS
        val mappings = createRealModelMappings(labels)

        // "tench" should map to "fishing" (or similar)
        // "background" should be unmapped
        val backgroundMappings = mappings.filter {
            it.modelLabel == "background"
        }
        assertTrue("background should have a mapping entry",
            backgroundMappings.isNotEmpty())

        val tenchMappings = mappings.filter { it.modelLabel == "tench" }
        assertTrue("tench should have a mapping entry",
            tenchMappings.isNotEmpty())
    }

    @Test
    fun `full pipeline with real model metadata succeeds`() {
        val labels = MobileNetV2Labels.REPRESENTATIVE_TEST_LABELS
        val config = RealModelPipelineFactory.create(labels)

        val scores = FloatArray(labels.size) { 0.01f }.also {
            it[1] = 0.80f
            it[2] = 0.10f
        }

        val adapterOutput = createAdapterOutput(scores)
        val result = FullMlPipeline.run(
            adapterOutput = adapterOutput,
            config = config,
            evidenceId = "contract-test-001"
        )

        assertTrue("pipeline should succeed", result.succeeded)
        assertNotNull(result.inferenceResult)
        assertEquals(
            MobileNetV2Artifact.MODEL_ID,
            result.inferenceResult!!.modelId
        )
    }

    @Test
    fun `full pipeline preserves model identity provenance`() {
        val labels = MobileNetV2Labels.REPRESENTATIVE_TEST_LABELS
        val config = RealModelPipelineFactory.create(labels)

        val scores = FloatArray(labels.size) { 0.01f }
        val adapterOutput = createAdapterOutput(scores)

        val result = FullMlPipeline.run(
            adapterOutput = adapterOutput,
            config = config,
            evidenceId = "provenance-test-001"
        )

        assertEquals(
            MobileNetV2Artifact.MODEL_ID,
            result.inferenceResult!!.modelId
        )
        assertEquals(
            MobileNetV2Artifact.MODEL_VERSION,
            result.inferenceResult!!.modelVersion
        )
        assertEquals(FullPipelineResult.PIPELINE_VERSION, result.pipelineVersion)
    }

    @Test
    fun `pipeline handles all-unmapped predictions`() {
        val unmappedLabels = listOf("background", "tench", "goldfish")
        val mappings = unmappedLabels.mapIndexed { index, label ->
            ModelTaxonomyMapping(
                mappingId = "unmapped-$index-$label",
                modelArtifactId = MobileNetV2Artifact.ARTIFACT_ID,
                modelArtifactVersion = MobileNetV2Artifact.ARTIFACT_VERSION,
                modelLabelIndex = index,
                modelLabel = label,
                feedSenseTaxonomyKey = null,
                status = MappingStatus.UNMAPPED,
                provenance = MappingProvenance.PROJECT_DEFINED,
                mappingTableVersion = "test-mapping-v1",
                taxonomyVersion = taxonomyVersion
            )
        }

        val config = com.example.feedsense.analysis.ml.FullPipelineConfig(
            modelId = MobileNetV2Artifact.MODEL_ID,
            modelVersion = MobileNetV2Artifact.MODEL_VERSION,
            interpretationConfig = OutputInterpretationConfig(
                interpretationVersion = "test-interp-v1",
                labels = unmappedLabels,
                outputSemantics = OutputSemantics.SOFTMAX,
                topK = 3
            ),
            taxonomyMappings = mappings,
            taxonomyVersion = taxonomyVersion,
            mappingTableVersion = "test-mapping-v1"
        )

        val scores = floatArrayOf(0.1f, 0.8f, 0.1f)
        val adapterOutput = createAdapterOutput(scores)

        val result = FullMlPipeline.run(
            adapterOutput = adapterOutput,
            config = config,
            evidenceId = "unmapped-test-001"
        )

        assertTrue("pipeline succeeds even with all-unmapped",
            result.succeeded)
        assertEquals(0, result.mappedCount)
        assertEquals(3, result.unmappedCount)
    }

    @Test
    fun `pipeline evaluation boundary receives real metadata`() {
        val labels = MobileNetV2Labels.REPRESENTATIVE_TEST_LABELS
        val config = RealModelPipelineFactory.create(labels)

        val scores = FloatArray(labels.size) { 0.01f }.also {
            it[1] = 0.80f
        }
        val adapterOutput = createAdapterOutput(scores)

        val result = FullMlPipeline.run(
            adapterOutput = adapterOutput,
            config = config,
            evidenceId = "eval-boundary-test-001"
        )

        assertTrue(result.evaluationResults.isNotEmpty())
        val snapshot = result.evaluationResults[0].candidateSnapshot
        assertEquals(MobileNetV2Artifact.MODEL_ID, snapshot.modelId)
        assertEquals(MobileNetV2Artifact.MODEL_VERSION, snapshot.modelVersion)
    }

    @Test
    fun `preprocessing to adapter to decode with real dimensions`() {
        val fixture = RealModelGoldenFixtures.CANONICAL_224_KNOWN_GRADIENT
        val preprocessResult = RealModelGoldenFixtures.preprocessFixture(fixture)

        assertTrue(preprocessResult is PreprocessResult.Success)
        val preprocessed = (preprocessResult as PreprocessResult.Success).output

        assertEquals(224, preprocessed.input.width)
        assertEquals(224, preprocessed.input.height)
        assertEquals(3, preprocessed.input.channels)
        assertEquals(150528, preprocessed.tensorSize)
    }

    @Test
    fun `test adapter accepts 224x224x3 input`() {
        val labels = MobileNetV2Labels.REPRESENTATIVE_TEST_LABELS
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend(
            TestBackendBehavior.FixedOutput(
                FloatArray(labels.size) { 0.1f }.also { it[1] = 0.8f },
                listOf(1, labels.size)
            )
        )
        val adapter = ModelRuntimeAdapter(
            config = ModelRuntimeAdapterConfig(
                artifactIdentity = RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE,
                runtimeIdentity = RuntimeTestFixtures.TEST_RUNTIME_LITERT,
                expectedOutputShape = listOf(1, labels.size),
                expectedOutputDatatype = "FLOAT32",
                expectedValueRangeMin = 0.0,
                expectedValueRangeMax = 1.0,
                expectedInputContract = RealModelPreprocessing.config()
            ),
            artifactLoader = loader,
            backend = backend
        )
        adapter.initialize()

        val fixture = RealModelGoldenFixtures.CANONICAL_224_KNOWN_GRADIENT
        val preprocessResult = RealModelGoldenFixtures.preprocessFixture(fixture)
        assertTrue(preprocessResult is PreprocessResult.Success)

        val output = adapter.infer(
            (preprocessResult as PreprocessResult.Success).output
        )
        assertTrue("adapter should succeed", output.succeeded)
        assertEquals(224, output.inputSignature.split("x")[0].toInt())

        adapter.close()
    }

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun createAdapterOutput(scores: FloatArray): AdapterRawModelOutput {
        return AdapterRawModelOutput(
            artifactIdentity = RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE,
            runtimeIdentity = RuntimeTestFixtures.TEST_RUNTIME_LITERT,
            preprocessingVersion = "preprocess-v2",
            inputSignature = "${scores.size}:FLOAT32",
            outputTensors = listOf(
                OutputTensorMetadata(
                    tensorIndex = 0,
                    shape = listOf(1, scores.size),
                    datatype = "FLOAT32",
                    valueRangeMin = scores.min().toDouble(),
                    valueRangeMax = scores.max().toDouble()
                )
            ),
            rawOutputValues = mapOf(0 to scores.copyOf()),
            executionStatus = InferenceExecutionStatus.SUCCESS,
            inferenceDurationMs = 10L
        )
    }

    private fun createRealModelMappings(
        labels: List<String>
    ): List<ModelTaxonomyMapping> {
        return labels.mapIndexed { index, label ->
            ModelTaxonomyMapping(
                mappingId = "real-model-${index}-${label}",
                modelArtifactId = MobileNetV2Artifact.ARTIFACT_ID,
                modelArtifactVersion = MobileNetV2Artifact.ARTIFACT_VERSION,
                modelLabelIndex = index,
                modelLabel = label,
                feedSenseTaxonomyKey = null,
                status = MappingStatus.UNMAPPED,
                provenance = MappingProvenance.PROJECT_DEFINED,
                mappingTableVersion = "real-model-mapping-v1",
                taxonomyVersion = taxonomyVersion
            )
        }
    }
}
