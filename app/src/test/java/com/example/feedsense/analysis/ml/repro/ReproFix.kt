package com.example.feedsense.analysis.ml.repro

import com.example.feedsense.analysis.ml.ModelFormat

/*
 * Milestone 8B-15-2 test fixtures. Baseline identities used by
 * every reproducibility test; variants are produced with .copy()
 * so each test can prove equality / difference cleanly.
 */
object ReproFix {

    val model: ReproModelIdentity =
        ReproModelIdentity(
            modelId = "test-model",
            modelFamily = ModelFamily.MOBILENET_V4,
            modelArchitecture = "Test architecture",
            modelVersion = "v1",
            sourceName = "Test source",
            sourceLocation = "https://example.com/test",
            publisher = "Test publisher",
            license = "Apache-2.0",
            artifactFormat = ModelFormat.TFLITE,
            parametersMillions = 3.8,
            intendedTask = "IMAGE_CLASSIFICATION",
            releaseInfo = null,
            identityVersion = "1"
        )

    val artifact: ReproArtifactIdentity =
        ReproArtifactIdentity(
            artifactId = "test-artifact",
            fileName = "test.tflite",
            format = ReproArtifactFormat.TFLITE,
            byteSize = 100,
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sourceReference = "https://example.com/test",
            modelId = "test-artifact-model",
            artifactVersion = "v1",
            acquisitionTimestamp = "2026-09-06T00:00:00Z",
            availability = ArtifactAvailability.AVAILABLE,
            validationStatus = ArtifactValidationStatus.HASH_MATCHES
        )

    val runtime: ReproRuntimeIdentity =
        ReproRuntimeIdentity(
            runtimeName = ReproRuntimeName.LITERT,
            runtimeVersion = "0.0.1",
            executionBackend = ExecutionBackend.XNNPACK_CPU,
            supportedPlatform = SupportedPlatform.ANDROID,
            modelFormat = ReproArtifactFormat.TFLITE,
            runtimeConfiguration = "test config"
        )

    val quantization: ReproQuantizationIdentity =
        ReproQuantizationIdentity(
            depth = QuantizationDepth.INT8,
            method = QuantizationMethod.UNKNOWN,
            scope = QuantizationScope.UNKNOWN,
            calibrationMethod = null,
            calibrationDatasetId = null,
            toolName = null,
            toolVersion = null,
            sourceArtifactHash = null,
            resultingArtifactHash = null
        )

    val preprocessing: ReproPreprocessingIdentity =
        ReproPreprocessingIdentity(
            preprocessingVersion = "pp-v1",
            resizeMethod = ResizeMethod.BILINEAR,
            inputWidth = 224,
            inputHeight = 224,
            aspectRatioBehavior = AspectRatioBehavior.CENTER_CROP,
            cropBehavior = "center",
            colorFormat = "RGB",
            channelOrder = ChannelOrder.RGB,
            normalization = NormalizationParams(
                mean = listOf(0.5, 0.5, 0.5),
                std = listOf(0.5, 0.5, 0.5)
            ),
            alphaHandling = "opaque",
            orientationHandling = "portrait"
        )

    val outputMapping: ReproOutputMappingIdentity =
        ReproOutputMappingIdentity(
            outputMappingVersion = "map-v1",
            modelOutputLabels = listOf("classA", "classB"),
            labelOrdering = listOf("classA", "classB"),
            feedSenseCategoryMappingVersion = "cat-v1",
            mappingMode = OutputMappingMode.MANUAL_MAP,
            unknownBehavior = UnknownMappingBehavior.MAP_TO_UNKNOWN,
            topK = 3,
            outputSemantics = "single-label leader"
        )

    val privacy: ReproPrivacyIdentity =
        ReproPrivacyIdentity(
            privacySanitizationVersion = "privacy-v1",
            policyMode = PolicyModeRef.RESEARCH,
            evidenceSourceType = EvidenceSourceType.SAFE_FRAME
        )

    val provenance: ReproArtifactProvenance =
        ReproArtifactProvenance(
            steps = listOf(
                ReproProvenanceStep(
                    role = ProvenanceRole.ORIGINAL,
                    artifactId = "source",
                    conversion = null
                ),
                ReproProvenanceStep(
                    role = ProvenanceRole.DEPLOYMENT,
                    artifactId = "deployment",
                    conversion = null
                )
            ),
            terminalArtifactId = "deployment"
        )

    fun composite(): ReproCompositeIdentity =
        ReproCompositeIdentity(
            model = model,
            artifact = artifact,
            runtime = runtime,
            quantization = quantization,
            preprocessing = preprocessing,
            outputMapping = outputMapping,
            privacy = privacy,
            provenance = provenance
        )
}