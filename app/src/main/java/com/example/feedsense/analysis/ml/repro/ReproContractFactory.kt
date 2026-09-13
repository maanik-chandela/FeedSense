package com.example.feedsense.analysis.ml.repro

import com.example.feedsense.analysis.ml.ModelFormat
import com.example.feedsense.analysis.ml.selection.CandidateStatus
import com.example.feedsense.analysis.ml.selection.ResearchDecisionCatalog

// --------------------------------
// REPRODUCIBILITY CONTRACT (8B-15-2)
// --------------------------------
//
// Derives the reproducibility contract from the 8B-15-1 decision
// catalog. It preserves the SHORTLISTED / CONDITIONAL distinction:
// A1 (MobileNetV4-Conv-S) is the primary SHORTLISTED candidate,
// A2 (EfficientNet-Lite) the documented CONDITIONAL fallback. The
// milestone never invents a final model selection.
//
// The exact ARTIFACT is NOT available in 8B-15-2 (8B-15-1 did not
// download or integrate any model). Accordingly the artifact slot
// is represented honestly as PENDING with no fabricated checksum;
// version fields carry the explicit marker
// REPRO_UNSPECIFIED until pinned against a real
// artifact.

/*
 * Marker used whenever a concrete value has not yet been pinned
 * to a real artifact / package. Always an explicit "we do not
 * know yet", never a fabricated value.
 */
const val REPRO_UNSPECIFIED = "<UNSPECIFIED>"

/*
 * Sentinel label list value meaning "the model's exact output
 * label set has not been verified against a real artifact" (that
 * is 8B-15-4 Output Compatibility work).
 */
const val REPRO_UNRESOLVED_LABEL = "<UNRESOLVED_LABEL_SET>"

/*
 * Selection status of a reproducibility contract entry,
 * mirroring the 8B-15-1 candidate status.
 */
enum class ReproSelectionStatus(val label: String) {
    SHORTLISTED("SHORTLISTED"),
    CONDITIONAL("CONDITIONAL"),
    REJECTED("REJECTED"),
    OBSERVED_ONLY("OBSERVED_ONLY")
}

/*
 * One candidate's reproducibility contract.
 */
data class ReproContractEntry(
    val candidateId: String,
    val selectionStatus: ReproSelectionStatus,
    val identity: ReproCompositeIdentity
)

/*
 * The full contract selection derived from the 8B-15-1 catalog:
 * a primary SHORTLISTED entry and the documented fallback.
 */
data class ReproContractSelection(
    val catalogVersion: String,
    val primary: ReproContractEntry,
    val fallback: ReproContractEntry?
)

/*
 * Derives the reproducibility contract from the machine-readable
 * 8B-15-1 decision. Pure / deterministic: no artifact bytes, no
 * network, no wall-clock values.
 */
object ReproContractFactory {

    const val CONTRACT_VERSION = "8b-15-2-v1"

    private const val A1_ID = "A1-mobilenet-v4-conv-s"
    private const val A2_ID = "A2-efficientnet-lite"
    private const val D1_ID = "D1-tflite-litert"

    private const val SOURCE_TF_MODELS = "https://github.com/tensorflow/models"
    private const val PUBLISHER_A1 = "Google (per arXiv 2404.10518 / tensorflow/models)"

    fun selection(): ReproContractSelection {
        val catalog = ResearchDecisionCatalog
        val a1 = catalog.candidate(A1_ID)
            ?: error("catalog missing $A1_ID")
        val a2 = catalog.candidate(A2_ID)
        val d1 = catalog.candidate(D1_ID)
            ?: error("catalog missing $D1_ID")
        require(a1.status == CandidateStatus.SHORTLISTED) {
            "catalog invariant: ${a1.candidateId} must stay SHORTLISTED"
        }
        require(d1.status == CandidateStatus.SHORTLISTED) {
            "catalog invariant: ${d1.candidateId} must stay SHORTLISTED"
        }

        val primary = ReproContractEntry(
            candidateId = a1.candidateId,
            selectionStatus = ReproSelectionStatus.SHORTLISTED,
            identity = contractForA1(
                architecture = a1.architecture,
                license = a1.license,
                parametersMillions = a1.parametersMillions
            )
        )
        val fallback = a2?.let {
            ReproContractEntry(
                candidateId = it.candidateId,
                selectionStatus = ReproSelectionStatus.CONDITIONAL,
                identity = contractForA2(
                    architecture = it.architecture,
                    license = it.license,
                    parametersMillions = it.parametersMillions
                )
            )
        }
        return ReproContractSelection(
            catalogVersion = ResearchDecisionCatalog.CATALOG_VERSION,
            primary = primary,
            fallback = fallback
        )
    }

    /*
     * MobileNetV4-Conv-S (A1, SHORTLISTED) -> INT8 TFLite target.
     *
     * The artifact does not exist yet; it is PENDING and carries
     * no checksum. The runtime version is not pinned (no dependency
     * has landed) and is recorded as REPRO_UNSPECIFIED.
     */
    private fun contractForA1(
        architecture: String,
        license: String,
        parametersMillions: Double?
    ): ReproCompositeIdentity = ReproCompositeIdentity(
        model = ReproModelIdentity(
            modelId = "mobilenet-v4-conv-s",
            modelFamily = ModelFamily.MOBILENET_V4,
            modelArchitecture = architecture,
            modelVersion = REPRO_UNSPECIFIED,
            sourceName = "tensorflow/models",
            sourceLocation = SOURCE_TF_MODELS,
            publisher = PUBLISHER_A1,
            license = license,
            artifactFormat = ModelFormat.TFLITE,
            parametersMillions = parametersMillions,
            intendedTask = "IMAGE_CLASSIFICATION single-label video-frame category prediction",
            releaseInfo = null,
            identityVersion = "1"
        ),
        artifact = ReproArtifactIdentity(
            artifactId = "mobilenet-v4-conv-s-int8",
            fileName = null,
            format = ReproArtifactFormat.TFLITE,
            byteSize = null,
            sha256 = null,
            sourceReference = SOURCE_TF_MODELS,
            modelId = "mobilenet-v4-conv-s",
            artifactVersion = REPRO_UNSPECIFIED,
            acquisitionTimestamp = null,
            availability = ArtifactAvailability.PENDING,
            validationStatus = ArtifactValidationStatus.NOT_APPLICABLE
        ),
        runtime = ReproRuntimeIdentity(
            runtimeName = ReproRuntimeName.LITERT,
            runtimeVersion = REPRO_UNSPECIFIED,
            executionBackend = ExecutionBackend.XNNPACK_CPU,
            supportedPlatform = SupportedPlatform.ANDROID,
            modelFormat = ReproArtifactFormat.TFLITE,
            runtimeConfiguration = "LiteRT XNNPACK CPU default; GPU delegate + vendor NPU under verification (ADR-0001); NNAPI avoided."
        ),
        quantization = ReproQuantizationIdentity(
            depth = QuantizationDepth.INT8,
            method = QuantizationMethod.UNKNOWN,
            scope = QuantizationScope.UNKNOWN,
            calibrationMethod = null,
            calibrationDatasetId = null,
            toolName = null,
            toolVersion = null,
            sourceArtifactHash = null,
            resultingArtifactHash = null
        ),
        preprocessing = ReproPreprocessingIdentity(
            preprocessingVersion = "8b-15-2-v1",
            resizeMethod = ResizeMethod.UNKNOWN,
            inputWidth = null,
            inputHeight = null,
            aspectRatioBehavior = AspectRatioBehavior.UNKNOWN,
            cropBehavior = null,
            colorFormat = null,
            channelOrder = ChannelOrder.RGB,
            normalization = NormalizationParams(),
            alphaHandling = null,
            orientationHandling = null
        ),
        outputMapping = ReproOutputMappingIdentity(
            outputMappingVersion = "8b-15-2-v1",
            modelOutputLabels = listOf(REPRO_UNRESOLVED_LABEL),
            labelOrdering = listOf(REPRO_UNRESOLVED_LABEL),
            feedSenseCategoryMappingVersion = REPRO_UNSPECIFIED,
            mappingMode = OutputMappingMode.UNKNOWN,
            unknownBehavior = UnknownMappingBehavior.UNKNOWN,
            topK = 1,
            outputSemantics = "Single-label leader; manual adapter to CategoryCatalog keys (ADR-0001). Exact label set unverified until the artifact exists."
        ),
        privacy = ReproPrivacyIdentity(
            privacySanitizationVersion = "8b-13-v1",
            policyMode = PolicyModeRef.RESEARCH,
            evidenceSourceType = EvidenceSourceType.SAFE_FRAME
        ),
        provenance = ReproArtifactProvenance(
            steps = listOf(
                ReproProvenanceStep(
                    role = ProvenanceRole.ORIGINAL,
                    artifactId = "mobilenet-v4-conv-s-source",
                    conversion = null
                )
            ),
            terminalArtifactId = "mobilenet-v4-conv-s-source"
        )
    )

    /*
     * EfficientNet-Lite (A2, CONDITIONAL fallback). License is
     * UNKNOWN in 8B-15-1 and is preserved as UNKNOWN here.
     */
    private fun contractForA2(
        architecture: String,
        license: String,
        parametersMillions: Double?
    ): ReproCompositeIdentity = ReproCompositeIdentity(
        model = ReproModelIdentity(
            modelId = "efficientnet-lite",
            modelFamily = ModelFamily.EFFICIENTNET_LITE,
            modelArchitecture = architecture,
            modelVersion = REPRO_UNSPECIFIED,
            sourceName = "TensorFlow blog (2020-03-16)",
            sourceLocation = "https://blog.tensorflow.org/2020/03/higher-accuracy-on-vision-models-with-efficientnet-lite.html",
            publisher = null,
            license = license,
            artifactFormat = ModelFormat.TFLITE,
            parametersMillions = parametersMillions,
            intendedTask = "IMAGE_CLASSIFICATION single-label video-frame category prediction",
            releaseInfo = null,
            identityVersion = "1"
        ),
        artifact = ReproArtifactIdentity(
            artifactId = "efficientnet-lite-int8",
            fileName = null,
            format = ReproArtifactFormat.TFLITE,
            byteSize = null,
            sha256 = null,
            sourceReference = "https://blog.tensorflow.org/2020/03/higher-accuracy-on-vision-models-with-efficientnet-lite.html",
            modelId = "efficientnet-lite",
            artifactVersion = REPRO_UNSPECIFIED,
            acquisitionTimestamp = null,
            availability = ArtifactAvailability.PENDING,
            validationStatus = ArtifactValidationStatus.NOT_APPLICABLE
        ),
        runtime = ReproRuntimeIdentity(
            runtimeName = ReproRuntimeName.LITERT,
            runtimeVersion = REPRO_UNSPECIFIED,
            executionBackend = ExecutionBackend.XNNPACK_CPU,
            supportedPlatform = SupportedPlatform.ANDROID,
            modelFormat = ReproArtifactFormat.TFLITE,
            runtimeConfiguration = null
        ),
        quantization = ReproQuantizationIdentity(
            depth = QuantizationDepth.INT8,
            method = QuantizationMethod.UNKNOWN,
            scope = QuantizationScope.UNKNOWN,
            calibrationMethod = null,
            calibrationDatasetId = null,
            toolName = null,
            toolVersion = null,
            sourceArtifactHash = null,
            resultingArtifactHash = null
        ),
        preprocessing = ReproPreprocessingIdentity(
            preprocessingVersion = "8b-15-2-v1",
            resizeMethod = ResizeMethod.UNKNOWN,
            inputWidth = null,
            inputHeight = null,
            aspectRatioBehavior = AspectRatioBehavior.UNKNOWN,
            cropBehavior = null,
            colorFormat = null,
            channelOrder = ChannelOrder.RGB,
            normalization = NormalizationParams(),
            alphaHandling = null,
            orientationHandling = null
        ),
        outputMapping = ReproOutputMappingIdentity(
            outputMappingVersion = "8b-15-2-v1",
            modelOutputLabels = listOf(REPRO_UNRESOLVED_LABEL),
            labelOrdering = listOf(REPRO_UNRESOLVED_LABEL),
            feedSenseCategoryMappingVersion = REPRO_UNSPECIFIED,
            mappingMode = OutputMappingMode.UNKNOWN,
            unknownBehavior = UnknownMappingBehavior.UNKNOWN,
            topK = 1,
            outputSemantics = null
        ),
        privacy = ReproPrivacyIdentity(
            privacySanitizationVersion = "8b-13-v1",
            policyMode = PolicyModeRef.RESEARCH,
            evidenceSourceType = EvidenceSourceType.SAFE_FRAME
        ),
        provenance = null
    )
}