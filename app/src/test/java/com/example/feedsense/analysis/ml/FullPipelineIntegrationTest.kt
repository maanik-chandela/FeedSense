package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.SchemaFreeze
import com.example.feedsense.analysis.ml.evaluation.EvaluationBoundaryEvaluator
import com.example.feedsense.analysis.ml.evaluation.EvaluationCandidateSnapshot
import com.example.feedsense.analysis.ml.evaluation.EvaluationEligibility
import com.example.feedsense.analysis.ml.evaluation.EvaluationBoundaryResult
import com.example.feedsense.analysis.ml.runtime.AdapterRawModelOutput
import com.example.feedsense.analysis.ml.runtime.InferenceExecutionStatus
import com.example.feedsense.analysis.ml.runtime.ModelRuntimeAdapter
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
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingResult
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingVersion
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FullPipelineIntegrationTest {

    private val taxonomyVersion =
        TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = SchemaFreeze.FREEZE_VERSION,
            sortedCategoryKeys = CategoryCatalog.keys.sorted()
        )

    private val testLabels = listOf(
        "sports", "entertainment", "education",
        "news", "lifestyle", "gaming",
        "politics", "technology", "music"
    )

    private val testMappings: List<ModelTaxonomyMapping> = listOf(
        ModelTaxonomyMapping(
            mappingId = "pipeline-direct-sports",
            modelArtifactId = "pipeline-test-model",
            modelArtifactVersion = "pipeline-v1",
            modelLabelIndex = 0,
            modelLabel = "sports",
            feedSenseTaxonomyKey = "sports",
            status = MappingStatus.DIRECT,
            rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingTableVersion = "pipeline-mapping-v1",
            taxonomyVersion = taxonomyVersion
        ),
        ModelTaxonomyMapping(
            mappingId = "pipeline-direct-entertainment",
            modelArtifactId = "pipeline-test-model",
            modelArtifactVersion = "pipeline-v1",
            modelLabelIndex = 1,
            modelLabel = "entertainment",
            feedSenseTaxonomyKey = "entertainment",
            status = MappingStatus.DIRECT,
            rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingTableVersion = "pipeline-mapping-v1",
            taxonomyVersion = taxonomyVersion
        ),
        ModelTaxonomyMapping(
            mappingId = "pipeline-direct-education",
            modelArtifactId = "pipeline-test-model",
            modelArtifactVersion = "pipeline-v1",
            modelLabelIndex = 2,
            modelLabel = "education",
            feedSenseTaxonomyKey = "education",
            status = MappingStatus.DIRECT,
            rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingTableVersion = "pipeline-mapping-v1",
            taxonomyVersion = taxonomyVersion
        ),
        ModelTaxonomyMapping(
            mappingId = "pipeline-mapped-news",
            modelArtifactId = "pipeline-test-model",
            modelArtifactVersion = "pipeline-v1",
            modelLabelIndex = 3,
            modelLabel = "news",
            feedSenseTaxonomyKey = "news",
            status = MappingStatus.MAPPED,
            rationale = MappingRationale.BROADER_FEEDSENSE_CATEGORY,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingTableVersion = "pipeline-mapping-v1",
            taxonomyVersion = taxonomyVersion,
            rationaleNotes = "News maps to news domain"
        ),
        ModelTaxonomyMapping(
            mappingId = "pipeline-unmapped-politics",
            modelArtifactId = "pipeline-test-model",
            modelArtifactVersion = "pipeline-v1",
            modelLabelIndex = 6,
            modelLabel = "politics",
            status = MappingStatus.UNMAPPED,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingTableVersion = "pipeline-mapping-v1",
            taxonomyVersion = taxonomyVersion,
            rationaleNotes = "No FeedSense equivalent"
        )
    )

    private val pipelineConfig = FullPipelineConfig(
        modelId = "pipeline-test-model",
        modelVersion = "pipeline-v1",
        interpretationConfig = OutputInterpretationConfig(
            interpretationVersion = "interp-v1",
            labels = testLabels,
            outputSemantics = OutputSemantics.RAW_SCORES,
            topK = 5
        ),
        taxonomyMappings = testMappings,
        taxonomyVersion = taxonomyVersion,
        mappingTableVersion = "pipeline-mapping-v1"
    )

    @Test
    fun `full pipeline - adapter output to evaluation boundary`() {
        val adapterOutput = createAdapterOutput(
            scores = floatArrayOf(0.85f, 0.05f, 0.03f, 0.02f, 0.01f, 0.01f, 0.01f, 0.01f, 0.01f)
        )

        val result = FullMlPipeline.run(
            adapterOutput = adapterOutput,
            config = pipelineConfig,
            evidenceId = "e2e-test-001",
            sessionId = "e2e-session"
        )

        assertTrue("decoded should succeed", result.decodedOutput is DecodedOutput.Success)
        assertTrue("inference should succeed", result.inferenceResult!!.succeeded)
        assertEquals("sports", result.inferenceResult!!.primaryCategory)

        assertEquals(5, result.mappingResults.size)

        val sportsMapping = result.mappingResults[0]
        assertTrue("sports should be mapped", sportsMapping is TaxonomyMappingResult.Mapped)
        assertEquals(
            "sports",
            (sportsMapping as TaxonomyMappingResult.Mapped).feedSenseTaxonomyKey
        )

        assertEquals(5, result.eligibilityResults.size)
        assertTrue("sports should be eligible", result.eligibilityResults[0].isEligible)
        assertTrue("education should be eligible", result.eligibilityResults[2].isEligible)

        assertEquals(5, result.evaluationResults.size)
        assertTrue("pipeline should have succeeded overall", result.succeeded)
        assertTrue("at least one mapped", result.mappedCount >= 1)
    }

    @Test
    fun `full pipeline - unmapped prediction produces unmapped eligibility`() {
        val adapterOutput = createAdapterOutput(
            scores = floatArrayOf(0.01f, 0.01f, 0.01f, 0.02f, 0.01f, 0.01f, 0.85f, 0.05f, 0.03f)
        )

        val result = FullMlPipeline.run(
            adapterOutput = adapterOutput,
            config = pipelineConfig,
            evidenceId = "e2e-unmapped-001"
        )

        assertTrue(result.succeeded)

        val politicsMapping = result.mappingResults.first {
            it.modelLabel == "politics"
        }
        assertTrue(
            "politics should be unmapped",
            politicsMapping is TaxonomyMappingResult.Unmapped
        )
        assertFalse(
            "politics should not be eligible",
            result.eligibilityResults[result.mappingResults.indexOf(politicsMapping)].isEligible
        )
    }

    @Test
    fun `full pipeline - failed adapter output produces failure result`() {
        val adapterOutput = AdapterRawModelOutput(
            artifactIdentity = RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE,
            runtimeIdentity = RuntimeTestFixtures.TEST_RUNTIME_LITERT,
            preprocessingVersion = "v1",
            inputSignature = "<failed>",
            outputTensors = emptyList(),
            rawOutputValues = emptyMap(),
            executionStatus = InferenceExecutionStatus.FAILURE,
            failure = RuntimeFailureFactory.runtimeUnavailable("test-runtime")
        )

        val result = FullMlPipeline.run(
            adapterOutput = adapterOutput,
            config = pipelineConfig,
            evidenceId = "e2e-fail-001"
        )

        assertFalse("pipeline should not have succeeded", result.succeeded)
        assertTrue("decoded should be failure", result.decodedOutput is DecodedOutput.Failure)
        assertEquals(InferenceStatus.INFERENCE_FAILURE, result.inferenceResult!!.status)
        assertEquals(0, result.mappingResults.size)
        assertEquals(0, result.eligibilityResults.size)
    }

    @Test
    fun `full pipeline - tensor size mismatch produces decode failure`() {
        val adapterOutput = createAdapterOutput(
            scores = floatArrayOf(0.5f, 0.5f)
        )

        val result = FullMlPipeline.run(
            adapterOutput = adapterOutput,
            config = pipelineConfig,
            evidenceId = "e2e-mismatch-001"
        )

        assertFalse("pipeline should not have succeeded", result.succeeded)
        assertTrue("decoded should be failure", result.decodedOutput is DecodedOutput.Failure)
        assertEquals(InferenceStatus.OUTPUT_INVALID, result.inferenceResult!!.status)
    }

    @Test
    fun `full pipeline - produces deterministic results`() {
        val adapterOutput = createAdapterOutput(
            scores = floatArrayOf(0.85f, 0.05f, 0.03f, 0.02f, 0.01f, 0.01f, 0.01f, 0.01f, 0.01f)
        )

        val r1 = FullMlPipeline.run(
            adapterOutput = adapterOutput,
            config = pipelineConfig,
            evidenceId = "e2e-det-001"
        )
        val r2 = FullMlPipeline.run(
            adapterOutput = adapterOutput,
            config = pipelineConfig,
            evidenceId = "e2e-det-001"
        )

        assertEquals(r1.mappingResults.size, r2.mappingResults.size)
        for (i in r1.mappingResults.indices) {
            assertEquals(r1.mappingResults[i], r2.mappingResults[i])
        }
        assertEquals(r1.eligibilityResults, r2.eligibilityResults)
    }

    @Test
    fun `chained preprocessing through evaluation boundary`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend(
            TestBackendBehavior.FixedOutput(
                floatArrayOf(0.9f, 0.06f, 0.04f),
                listOf(1, 3)
            )
        )
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfigWithContract(),
            artifactLoader = loader,
            backend = backend
        )
        adapter.initialize()

        val frame = com.example.feedsense.analysis.ml.preprocess.golden.GoldenFixtureLoader.loadFrame(
            com.example.feedsense.analysis.ml.preprocess.golden.GoldenFixtureDefinitions.ALL_FIXTURES.first {
                it.fixtureId == "basic-all-black-4x4"
            }
        )
        val evidence = com.example.feedsense.analysis.ml.preprocess.PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = PrivacySanitizationStatus.SANITIZED,
            evidenceId = "chained-test-001",
            sessionId = "chained-session",
            feedItemId = "chained-item"
        )
        val preprocessor = com.example.feedsense.analysis.ml.preprocess.DeterministicPreprocessor(
            RuntimeTestFixtures.CONFIG_4x4_DEFAULT
        )
        val preprocessResult = preprocessor.preprocess(
            evidence,
            capturedOrientation = com.example.feedsense.analysis.ml.preprocess.FrameOrientation.DEG_0
        )

        assertTrue("preprocessing should succeed",
            preprocessResult is com.example.feedsense.analysis.ml.preprocess.PreprocessResult.Success)

        val adapterOutput = adapter.infer(
            (preprocessResult as com.example.feedsense.analysis.ml.preprocess.PreprocessResult.Success).output
        )
        assertTrue("adapter should succeed", adapterOutput.succeeded)

        val chainedLabels = listOf("sports", "entertainment", "education")
        val config = FullPipelineConfig(
            modelId = "pipeline-test-model",
            modelVersion = "pipeline-v1",
            interpretationConfig = OutputInterpretationConfig(
                interpretationVersion = "interp-v1",
                labels = chainedLabels,
                outputSemantics = OutputSemantics.RAW_SCORES,
                topK = 3
            ),
            taxonomyMappings = chainedLabels.mapIndexed { index, label ->
                ModelTaxonomyMapping(
                    mappingId = "chained-$label",
                    modelArtifactId = "pipeline-test-model",
                    modelArtifactVersion = "pipeline-v1",
                    modelLabelIndex = index,
                    modelLabel = label,
                    feedSenseTaxonomyKey = label,
                    status = MappingStatus.DIRECT,
                    rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
                    provenance = MappingProvenance.PROJECT_DEFINED,
                    mappingTableVersion = "pipeline-mapping-v1",
                    taxonomyVersion = taxonomyVersion
                )
            },
            taxonomyVersion = taxonomyVersion,
            mappingTableVersion = "pipeline-mapping-v1"
        )

        val result = FullMlPipeline.run(
            adapterOutput = adapterOutput,
            config = config,
            evidenceId = "chained-test-001",
            sessionId = "chained-session"
        )

        assertTrue("full pipeline should succeed", result.succeeded)
        assertEquals("sports", result.inferenceResult!!.primaryCategory)
        assertTrue("at least one mapping", result.mappedCount >= 1)

        adapter.close()
    }

    @Test
    fun `pipeline stamps all version provenance`() {
        val adapterOutput = createAdapterOutput(
            scores = floatArrayOf(0.85f, 0.05f, 0.03f, 0.02f, 0.01f, 0.01f, 0.01f, 0.01f, 0.01f)
        )

        val result = FullMlPipeline.run(
            adapterOutput = adapterOutput,
            config = pipelineConfig,
            evidenceId = "e2e-provenance-001"
        )

        assertEquals("pipeline-test-model", result.inferenceResult!!.modelId)
        assertEquals("pipeline-v1", result.inferenceResult!!.modelVersion)
        assertEquals("interp-v1", result.inferenceResult!!.rawModelMetadata["interpretation.version"])
        assertEquals(FullPipelineResult.PIPELINE_VERSION, result.pipelineVersion)
    }

    @Test
    fun `evaluation snapshots contain outputInterpretationVersion`() {
        val adapterOutput = createAdapterOutput(
            scores = floatArrayOf(0.85f, 0.05f, 0.03f, 0.02f, 0.01f, 0.01f, 0.01f, 0.01f, 0.01f)
        )

        val result = FullMlPipeline.run(
            adapterOutput = adapterOutput,
            config = pipelineConfig,
            evidenceId = "e2e-snapshot-version"
        )

        assertTrue(result.evaluationResults.isNotEmpty())
        val snapshot = result.evaluationResults[0].candidateSnapshot
        assertEquals("interp-v1", snapshot.outputInterpretationVersion)
    }

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
}
