package com.example.feedsense.analysis.ml.selection

import com.example.feedsense.analysis.ml.ModelFormat

// --------------------------------
// RESEARCH DECISION CATALOG (8B-15-1)
// --------------------------------
//
// The compiled, versioned evidence of this milestone:
//
//   - the candidate set (families A..F),
//   - the 20-criterion matrix (complete rows for every
//     candidate),
//   - the architecture decision record (ADR-0001).
//
// This is a RESEARCH catalog, not a runtime: it drives the
// 8B-15-2+ implementation decisions and is testable/stable.
// Figures are graded by EvidenceLevel; nothing here is invented.
//
// Source rule: every claim that is not FACT/HYPOTHESIS carries a
// SourceReference in the candidate's `sources`.

object ResearchDecisionCatalog {

    const val CATALOG_VERSION = "8b-15-1-v1"

    // --- helpers -------------------------------------------------
    private fun s(
        criterion: EvaluationCriterion,
        rating: CriterionRating,
        evidence: EvidenceLevel,
        rationale: String
    ) = CriterionScore(criterion, rating, evidence, rationale)

    private fun unk(criterion: EvaluationCriterion) = CriterionScore(
        criterion,
        CriterionRating.UNKNOWN,
        EvidenceLevel.UNKNOWN,
        "No evidence in 8B-15-1."
    )

    private fun src(url: String, title: String, claim: String) =
        SourceReference(url, title, VISITED, claim)

    // --- sources (visited 2026-09-06) ----------------------------
    private const val VISITED = "2026-09-06"

    private val SRC_MV4_PAPER = src(
        "https://arxiv.org/abs/2404.10518",
        "MobileNetV4: Universal Models for the Mobile Ecosystem",
        "Enables real-time inference on mobile HW; MNv4-Hybrid-Large 87% top-1, 3.8 ms on Pixel 8 EdgeTPU; MNv4-Conv-S 3.8M params / 0.2G MACs."
    )
    private val SRC_TF_MODELS = src(
        "https://github.com/tensorflow/models",
        "tensorflow/models",
        "Official MobileNetV4 implementation; Apache-2.0 license header on mobilenet.py."
    )
    private val SRC_ETL_BLOG = src(
        "https://blog.tensorflow.org/2020/03/higher-accuracy-on-vision-models-with-efficientnet-lite.html",
        "EfficientNet-Lite: Higher accuracy on vision models with TensorFlow Lite",
        "Lite0-Lite4 built for TFLite mobile CPU/GPU/EdgeTPU; integer-only quantized Lite4 exists."
    )
    private val SRC_LITERT = src(
        "https://ai.google.dev/edge/litert",
        "TensorFlow Lite / LiteRT",
        "Official runtime documentation; XNNPACK CPU, GPU delegate, vendor NPU support."
    )
    private val SRC_NNAPI_MIG = src(
        "https://developer.android.com/ndk/guides/neuralnetworks/migration-guide",
        "Android NNAPI deprecation & migration guide (2026-03-06)",
        "NNAPI is deprecated on Android; use LiteRT / GPU / vendor NPU paths instead."
    )
    private val SRC_ORT_MOBILE = src(
        "https://central.sonatype.com/artifact/com.microsoft.onnxruntime/onnxruntime-mobile",
        "onnxruntime-mobile (Maven Central)",
        "MIT; mobile package size-optimized but Maven versions end at 1.18.0 (2024)."
    )
    private val SRC_ORT_ANDROID = src(
        "https://central.sonatype.com/artifact/com.microsoft.onnxruntime/onnxruntime-android",
        "onnxruntime-android (Maven Central)",
        "Full runtime current at 1.29.0 (Aug 2026); large AAR (~41.6 MB)."
    )
    private val SRC_ET_REPO = src(
        "https://github.com/pytorch/executorch",
        "pytorch/executorch",
        "BSD-3-Clause runtime; XNNPACK/QNN/MediaTek/Vulkan backends; torch.export flow."
    )
    private val SRC_ET_MVN = src(
        "https://mvnrepository.com/artifact/org.pytorch/executorch-android",
        "executorch-android (Maven)",
        "26 versions; 1.4.0 Aug 2026; BSD 3-clause; QNN variant separate AAR."
    )
    private val SRC_ET_ANDROID = src(
        "https://docs.pytorch.org/executorch/stable/using-executorch-android.html",
        "Using ExecuTorch on Android",
        "Maven AAR from v1.0.0; fbjni + soloader also required; XNNPACK always built."
    )
    private val SRC_MVLM_REPO = src(
        "https://github.com/Meituan-AutoML/MobileVLM",
        "Meituan-AutoML/MobileVLM",
        "MobileVLM V2 1.7B/3B; Apache-2.0 LICENSE confirmed."
    )
    private val SRC_MVLM_PAPER = src(
        "https://arxiv.org/abs/2402.03766",
        "MobileVLM V2 (arXiv 2402.03766)",
        "On-device VLM design; efficient visual encoder to language model bridge."
    )
    private val SRC_QWEN_VL = src(
        "https://huggingface.co/Qwen/Qwen2.5-VL-3B-Instruct",
        "Qwen/Qwen2.5-VL-3B-Instruct",
        "3B/7B/32B/72B sizes; license of the specific artifact NOT verified in 8B-15-1."
    )
    private val SRC_SMOL_PAPER = src(
        "https://arxiv.org/abs/2504.05299",
        "SmolVLM: Redefining small and efficient multimodal models (COLM 2025)",
        "256M/500M/2.2B; 256M < 1 GB GPU RAM; ONNX exports; mobile app published."
    )
    private val SRC_SMOL_LIC = src(
        "https://www.forasoft.com/learn/ai-for-video-engineering/articles-ai/depth-anything-smolvlm-small-on-device-cv",
        "Fora Soft: Depth Anything + SmolVLM for small on-device CV",
        "States SmolVLM2 is Apache-2.0 across 256M/500M/2.2B sizes."
    )
    private val SRC_FEED_COPY = src(
        "file://docs/ml-inference.md",
        "FeedSense - On-Device ML Inference Foundation (8B-14)",
        "OnDeviceModel contract, provenance, and the parallel baseline-vs-ML comparison design."
    )

    // ============================================================
    // FAMILY A - compact image classifiers (CNN)
    // ============================================================

    private val candidateA1 = ModelCandidate(
        candidateId = "A1-mobilenet-v4-conv-s",
        familyId = "A",
        familyName = "Compact image classification - CNN",
        name = "MobileNetV4 - Conv-S",
        architecture = "MobileNetV4 (Conv-S variant, IBM-NAS architecture)",
        runtimes = listOf(ModelFormat.TFLITE),
        status = CandidateStatus.SHORTLISTED,
        statusReason = "Best-documented lightweight CNN path into TFLite with official code, modern architecture, and strong latency evidence.",
        license = "Apache-2.0",
        licenseEvidence = EvidenceLevel.DOCUMENTED_BY_SOURCE,
        parametersMillions = 3.8,
        parametersEvidence = EvidenceLevel.DOCUMENTED_BY_SOURCE,
        modelSizeMegabytes = null,
        sizeEvidence = EvidenceLevel.ENGINEERING_ESTIMATE,
        latencyMs = 3.8,
        latencyEvidence = EvidenceLevel.DOCUMENTED_BY_SOURCE,
        latencyContext = "MNv4-Hybrid-Large on Pixel 8 EdgeTPU (the lighter Conv-S is expected faster; not separately published)",
        androidMinApi = 24,
        metrics = listOf(
            PublishedMetric("ImageNet-1K top-1 (Hybrid-Large)", "~87%", EvidenceLevel.DOCUMENTED_BY_SOURCE, "arXiv 2404.10518"),
            PublishedMetric("Params (Conv-S)", "3.8M", EvidenceLevel.DOCUMENTED_BY_SOURCE, "arXiv 2404.10518"),
            PublishedMetric("MACs (Conv-S)", "0.2G", EvidenceLevel.DOCUMENTED_BY_SOURCE, "arXiv 2404.10518"),
            PublishedMetric("Conv-S ImageNet top-1", "not retrieved", EvidenceLevel.UNKNOWN, "not verified in 8B-15-1")
        ),
        scores = buildMap {
            put(EvaluationCriterion.TAXONOMY_ALIGNMENT, s(EvaluationCriterion.TAXONOMY_ALIGNMENT, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Top-1 single-label output fits the primary-category decision; multi-label needs an extra head."))
            put(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, s(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Vision-only; ignores captions/subtitles that dominate meme/ad/clip categories."))
            put(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, s(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "ImageNet is not a FeedSense corpus; needs a corpus experiment."))
            put(EvaluationCriterion.MODEL_SIZE_MB, s(EvaluationCriterion.MODEL_SIZE_MB, CriterionRating.STRONG, EvidenceLevel.ENGINEERING_ESTIMATE, "3.8M params -> roughly 3.8 MB INT8 weights plus buffers."))
            put(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, s(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, CriterionRating.STRONG, EvidenceLevel.ENGINEERING_ESTIMATE, "TFLite interpreter + XNNPACK is a few MB per ABI."))
            put(EvaluationCriterion.PEAK_RAM, unk(EvaluationCriterion.PEAK_RAM))
            put(EvaluationCriterion.INFERENCE_LATENCY, s(EvaluationCriterion.INFERENCE_LATENCY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Hybrid-Large: 3.8 ms Pixel 8 EdgeTPU; Conv-S expected faster but not separately published."))
            put(EvaluationCriterion.ENERGY_PER_INFERENCE, s(EvaluationCriterion.ENERGY_PER_INFERENCE, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "Needs on-device measurement."))
            put(EvaluationCriterion.TEMPORAL_MULTI_FRAME, s(EvaluationCriterion.TEMPORAL_MULTI_FRAME, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Single-frame CNN; temporal/multi-frame needs an aggregation layer, not native."))
            put(EvaluationCriterion.CPU_ONLY_FEASIBILITY, s(EvaluationCriterion.CPU_ONLY_FEASIBILITY, CriterionRating.STRONG, EvidenceLevel.ENGINEERING_ESTIMATE, "XNNPACK CPU path handles MobileNetV4-class models comfortably; no accelerator required."))
            put(EvaluationCriterion.ACCELERATOR_OPTIONS, s(EvaluationCriterion.ACCELERATOR_OPTIONS, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "LiteRT GPU delegate + vendor NPUs; deprecated NNAPI avoided."))
            put(EvaluationCriterion.ANDROID_API_COMPATIBILITY, s(EvaluationCriterion.ANDROID_API_COMPATIBILITY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "TFLite supports API 19+; FeedSense minSdk 24 is fully covered."))
            put(EvaluationCriterion.FP16_SUPPORT, s(EvaluationCriterion.FP16_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "TFLite FP16 weight quantization supported."))
            put(EvaluationCriterion.INT8_SUPPORT, s(EvaluationCriterion.INT8_SUPPORT, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "INT8 / integer-only quantization is first-class in TFLite."))
            put(EvaluationCriterion.FOUR_BIT_SUPPORT, s(EvaluationCriterion.FOUR_BIT_SUPPORT, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "No first-class stable INT4 path in TFLite; treat as unsupported."))
            put(EvaluationCriterion.RUNTIME_MATURITY, s(EvaluationCriterion.RUNTIME_MATURITY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Dominant on-device runtime with stable releases and a huge ecosystem."))
            put(EvaluationCriterion.LICENSE_COMPATIBILITY, s(EvaluationCriterion.LICENSE_COMPATIBILITY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Code Apache-2.0; verify the specific exported weight file at integration time."))
            put(EvaluationCriterion.INTEGRATION_EFFORT, s(EvaluationCriterion.INTEGRATION_EFFORT, CriterionRating.STRONG, EvidenceLevel.ENGINEERING_ESTIMATE, "TFLite converter + interpreter adapter onto OnDeviceModel; best-documented path."))
            put(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, s(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Versioned artifact + checksum + provenance already exist (8B-14); kernel determinism to verify."))
            put(EvaluationCriterion.PRIVACY_BOUNDED, s(EvaluationCriterion.PRIVACY_BOUNDED, CriterionRating.STRONG, EvidenceLevel.FACT, "Local-only runtime; consumes the 8B-13 safe frame; no network path."))
        },
        sources = listOf(SRC_MV4_PAPER, SRC_TF_MODELS, SRC_LITERT, SRC_FEED_COPY),
        notes = "Primary candidate. 8B-15-2 converts the official model to TFLite and measures on device before any promotion."
    )

    private val candidateA2 = ModelCandidate(
        candidateId = "A2-efficientnet-lite",
        familyId = "A",
        familyName = "Compact image classification - CNN",
        name = "EfficientNet-Lite (Lite0 / Lite4)",
        architecture = "EfficientNet-Lite (0-4), TFLite-tuned variant of EfficientNet-B0",
        runtimes = listOf(ModelFormat.TFLITE),
        status = CandidateStatus.CONDITIONAL,
        statusReason = "Documented fallback if A1 conversion or license verification fails; package weight license must be verified first.",
        license = "UNKNOWN",
        licenseEvidence = EvidenceLevel.UNKNOWN,
        parametersMillions = null,
        parametersEvidence = EvidenceLevel.UNKNOWN,
        modelSizeMegabytes = null,
        sizeEvidence = EvidenceLevel.ENGINEERING_ESTIMATE,
        latencyMs = null,
        latencyEvidence = EvidenceLevel.UNKNOWN,
        latencyContext = null,
        androidMinApi = 24,
        metrics = listOf(
            PublishedMetric("Variants", "Lite0..Lite4", EvidenceLevel.DOCUMENTED_BY_SOURCE, "TF blog 2020-03-16"),
            PublishedMetric("Integer-only Lite4", "available", EvidenceLevel.DOCUMENTED_BY_SOURCE, "TF blog 2020-03-16"),
            PublishedMetric("Lite0/Lite4 ImageNet top-1", "not verified", EvidenceLevel.UNKNOWN, "not retrieved in 8B-15-1")
        ),
        scores = buildMap {
            put(EvaluationCriterion.TAXONOMY_ALIGNMENT, s(EvaluationCriterion.TAXONOMY_ALIGNMENT, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Single-label classifier; same mapping story as A1."))
            put(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, s(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Vision-only."))
            put(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, s(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "Needs a corpus experiment."))
            put(EvaluationCriterion.MODEL_SIZE_MB, s(EvaluationCriterion.MODEL_SIZE_MB, CriterionRating.STRONG, EvidenceLevel.ENGINEERING_ESTIMATE, "Lite0-class ~4-5 MB INT8 weights."))
            put(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, s(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, CriterionRating.STRONG, EvidenceLevel.ENGINEERING_ESTIMATE, "Same TFLite runtime as A1."))
            put(EvaluationCriterion.PEAK_RAM, unk(EvaluationCriterion.PEAK_RAM))
            put(EvaluationCriterion.INFERENCE_LATENCY, s(EvaluationCriterion.INFERENCE_LATENCY, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Designed for mobile CPU/GPU/EdgeTPU; no verified ms figure in 8B-15-1."))
            put(EvaluationCriterion.ENERGY_PER_INFERENCE, s(EvaluationCriterion.ENERGY_PER_INFERENCE, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "Needs on-device measurement."))
            put(EvaluationCriterion.TEMPORAL_MULTI_FRAME, s(EvaluationCriterion.TEMPORAL_MULTI_FRAME, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Single-frame CNN."))
            put(EvaluationCriterion.CPU_ONLY_FEASIBILITY, s(EvaluationCriterion.CPU_ONLY_FEASIBILITY, CriterionRating.STRONG, EvidenceLevel.ENGINEERING_ESTIMATE, "TFLite CPU path fine for this class."))
            put(EvaluationCriterion.ACCELERATOR_OPTIONS, s(EvaluationCriterion.ACCELERATOR_OPTIONS, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "LiteRT GPU + vendor NPUs."))
            put(EvaluationCriterion.ANDROID_API_COMPATIBILITY, s(EvaluationCriterion.ANDROID_API_COMPATIBILITY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "TFLite covers minSdk 24."))
            put(EvaluationCriterion.FP16_SUPPORT, s(EvaluationCriterion.FP16_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "TFLite FP16 supported."))
            put(EvaluationCriterion.INT8_SUPPORT, s(EvaluationCriterion.INT8_SUPPORT, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Integer-only Lite4 published specifically for this."))
            put(EvaluationCriterion.FOUR_BIT_SUPPORT, s(EvaluationCriterion.FOUR_BIT_SUPPORT, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Same TFLite limitation as A1."))
            put(EvaluationCriterion.RUNTIME_MATURITY, s(EvaluationCriterion.RUNTIME_MATURITY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Same mature ecosystem."))
            put(EvaluationCriterion.LICENSE_COMPATIBILITY, s(EvaluationCriterion.LICENSE_COMPATIBILITY, CriterionRating.WEAK, EvidenceLevel.UNKNOWN, "Packaged weight license not verified in 8B-15-1; keep CONDITIONAL."))
            put(EvaluationCriterion.INTEGRATION_EFFORT, s(EvaluationCriterion.INTEGRATION_EFFORT, CriterionRating.STRONG, EvidenceLevel.ENGINEERING_ESTIMATE, "Same converter + adapter story as A1."))
            put(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, s(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Same as A1."))
            put(EvaluationCriterion.PRIVACY_BOUNDED, s(EvaluationCriterion.PRIVACY_BOUNDED, CriterionRating.STRONG, EvidenceLevel.FACT, "Local-only runtime."))
        },
        sources = listOf(SRC_ETL_BLOG, SRC_LITERT, SRC_FEED_COPY),
        notes = "Fallback; a condition is verifying the license of the exact exported weight artifact."
    )

    // ============================================================
    // FAMILY B - compact vision transformers
    // ============================================================

    private val candidateB1 = ModelCandidate(
        candidateId = "B1-compact-vit-mobilevit-family",
        familyId = "B",
        familyName = "Compact vision transformers (ViT)",
        name = "MobileViT family / compact ViTs (MobileViT v2, EdgeNeXt)",
        architecture = "Hybrid CNN + transformer patch encoder for image classification",
        runtimes = listOf(ModelFormat.TFLITE, ModelFormat.ONNX),
        status = CandidateStatus.REJECTED,
        statusReason = "No maintained packaged Android artifact was found in research; attention is heavier on mobile CPU than CNN at equal size; no advantage for the primary candidate role.",
        license = "UNKNOWN",
        licenseEvidence = EvidenceLevel.UNKNOWN,
        androidMinApi = 24,
        metrics = emptyList(),
        scores = buildMap {
            put(EvaluationCriterion.TAXONOMY_ALIGNMENT, s(EvaluationCriterion.TAXONOMY_ALIGNMENT, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Single-label classifier."))
            put(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, s(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Vision-only."))
            put(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, s(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "Needs a corpus experiment."))
            put(EvaluationCriterion.MODEL_SIZE_MB, s(EvaluationCriterion.MODEL_SIZE_MB, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Attention heads push size above CNN for similar accuracy."))
            put(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, s(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, CriterionRating.ADEQUATE, EvidenceLevel.ENGINEERING_ESTIMATE, "Runs via converted TFLite/ONNX; no extra runtime."))
            put(EvaluationCriterion.PEAK_RAM, unk(EvaluationCriterion.PEAK_RAM))
            put(EvaluationCriterion.INFERENCE_LATENCY, s(EvaluationCriterion.INFERENCE_LATENCY, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Attention ops are heavier on mobile CPU at equal parameter count."))
            put(EvaluationCriterion.ENERGY_PER_INFERENCE, s(EvaluationCriterion.ENERGY_PER_INFERENCE, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "Needs measurement."))
            put(EvaluationCriterion.TEMPORAL_MULTI_FRAME, s(EvaluationCriterion.TEMPORAL_MULTI_FRAME, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Image classifier, not temporal."))
            put(EvaluationCriterion.CPU_ONLY_FEASIBILITY, s(EvaluationCriterion.CPU_ONLY_FEASIBILITY, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Attention ops are less CPU-efficient on mobile."))
            put(EvaluationCriterion.ACCELERATOR_OPTIONS, s(EvaluationCriterion.ACCELERATOR_OPTIONS, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Inherits delegate support from converted runtime."))
            put(EvaluationCriterion.ANDROID_API_COMPATIBILITY, s(EvaluationCriterion.ANDROID_API_COMPATIBILITY, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Inherited from TFLite/ONNX runtime."))
            put(EvaluationCriterion.FP16_SUPPORT, s(EvaluationCriterion.FP16_SUPPORT, CriterionRating.UNKNOWN, EvidenceLevel.UNKNOWN, "Not verified for this family."))
            put(EvaluationCriterion.INT8_SUPPORT, s(EvaluationCriterion.INT8_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Quantizes once converted, like any TFLite graph."))
            put(EvaluationCriterion.FOUR_BIT_SUPPORT, s(EvaluationCriterion.FOUR_BIT_SUPPORT, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "No practical INT4 path via TFLite."))
            put(EvaluationCriterion.RUNTIME_MATURITY, s(EvaluationCriterion.RUNTIME_MATURITY, CriterionRating.WEAK, EvidenceLevel.DOCUMENTED_BY_SOURCE, "No maintained packaged MobileViT Android artifact found."))
            put(EvaluationCriterion.LICENSE_COMPATIBILITY, s(EvaluationCriterion.LICENSE_COMPATIBILITY, CriterionRating.WEAK, EvidenceLevel.UNKNOWN, "Per-model licenses unverified."))
            put(EvaluationCriterion.INTEGRATION_EFFORT, s(EvaluationCriterion.INTEGRATION_EFFORT, CriterionRating.WEAK, EvidenceLevel.ENGINEERING_ESTIMATE, "Export + custom head + fewer Android references."))
            put(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, s(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Converted graph is deterministic in principle."))
            put(EvaluationCriterion.PRIVACY_BOUNDED, s(EvaluationCriterion.PRIVACY_BOUNDED, CriterionRating.ADEQUATE, EvidenceLevel.FACT, "Runs locally if adopted."))
        },
        sources = listOf(),
        notes = "Rejected in 8B-15-1; revisit only with a specific released, license-verified artifact."
    )

    // ============================================================
    // FAMILY C - ONNX runtime mobile
    // ============================================================

    private val candidateC1 = ModelCandidate(
        candidateId = "C1-onnx-runtime-mobile",
        familyId = "C",
        familyName = "ONNX Runtime Mobile",
        name = "onnxruntime-mobile / onnxruntime-android",
        architecture = "ONNX Runtime (XNNPACK CPU / NNAPI EP) running converted ONNX graphs",
        runtimes = listOf(ModelFormat.ONNX),
        status = CandidateStatus.CONDITIONAL,
        statusReason = "Viable but the size-optimized mobile Maven package is stale (1.18.0, May 2024) while the full Android AAR is current but large (~41.6 MB). Adopt only if a needed model exists solely as ONNX.",
        license = "MIT",
        licenseEvidence = EvidenceLevel.DOCUMENTED_BY_SOURCE,
        androidMinApi = 24,
        metrics = listOf(
            PublishedMetric("onnxruntime-mobile latest Maven", "1.18.0 (May 2024)", EvidenceLevel.DOCUMENTED_BY_SOURCE, "Maven Central"),
            PublishedMetric("onnxruntime-android latest Maven", "1.29.0 (Aug 2026)", EvidenceLevel.DOCUMENTED_BY_SOURCE, "Maven Central"),
            PublishedMetric("onnxruntime-android AAR size", "~41.6 MB", EvidenceLevel.DOCUMENTED_BY_SOURCE, "Maven Central metadata")
        ),
        scores = buildMap {
            put(EvaluationCriterion.TAXONOMY_ALIGNMENT, s(EvaluationCriterion.TAXONOMY_ALIGNMENT, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Same output-to-taxonomy mapping as any classifier."))
            put(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, s(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Vision-only runtime."))
            put(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, s(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "Model-dependent; needs a corpus experiment."))
            put(EvaluationCriterion.MODEL_SIZE_MB, s(EvaluationCriterion.MODEL_SIZE_MB, CriterionRating.STRONG, EvidenceLevel.ENGINEERING_ESTIMATE, "Payload is the converted model; comparable to TFLite."))
            put(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, s(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, CriterionRating.WEAK, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Optimized mobile artifact stale; full AAR ~41.6 MB."))
            put(EvaluationCriterion.PEAK_RAM, unk(EvaluationCriterion.PEAK_RAM))
            put(EvaluationCriterion.INFERENCE_LATENCY, s(EvaluationCriterion.INFERENCE_LATENCY, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "XNNPACK CPU path competitive; no verified FeedSense figure."))
            put(EvaluationCriterion.ENERGY_PER_INFERENCE, s(EvaluationCriterion.ENERGY_PER_INFERENCE, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "Needs measurement."))
            put(EvaluationCriterion.TEMPORAL_MULTI_FRAME, s(EvaluationCriterion.TEMPORAL_MULTI_FRAME, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Same as other CNNs."))
            put(EvaluationCriterion.CPU_ONLY_FEASIBILITY, s(EvaluationCriterion.CPU_ONLY_FEASIBILITY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "XNNPACK is the default Android CPU path."))
            put(EvaluationCriterion.ACCELERATOR_OPTIONS, s(EvaluationCriterion.ACCELERATOR_OPTIONS, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "NNAPI EP exists but NNAPI is deprecated; other EPs limited in mobile builds."))
            put(EvaluationCriterion.ANDROID_API_COMPATIBILITY, s(EvaluationCriterion.ANDROID_API_COMPATIBILITY, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "XNNPACK works at minSdk 24; NNAPI EP needs API 27+."))
            put(EvaluationCriterion.FP16_SUPPORT, s(EvaluationCriterion.FP16_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "ORT supports FP16 graphs."))
            put(EvaluationCriterion.INT8_SUPPORT, s(EvaluationCriterion.INT8_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "INT8/QDQ supported; tooling less one-click than TFLite."))
            put(EvaluationCriterion.FOUR_BIT_SUPPORT, s(EvaluationCriterion.FOUR_BIT_SUPPORT, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "No practical INT4 path in mobile ORT."))
            put(EvaluationCriterion.RUNTIME_MATURITY, s(EvaluationCriterion.RUNTIME_MATURITY, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Full ORT current, but the size-optimized mobile package lags 2+ years."))
            put(EvaluationCriterion.LICENSE_COMPATIBILITY, s(EvaluationCriterion.LICENSE_COMPATIBILITY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "ORT is MIT."))
            put(EvaluationCriterion.INTEGRATION_EFFORT, s(EvaluationCriterion.INTEGRATION_EFFORT, CriterionRating.ADEQUATE, EvidenceLevel.ENGINEERING_ESTIMATE, "ONNX->ORT conversion + adapter; stale mobile package adds risk."))
            put(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, s(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Determinism in principle; verify per graph."))
            put(EvaluationCriterion.PRIVACY_BOUNDED, s(EvaluationCriterion.PRIVACY_BOUNDED, CriterionRating.STRONG, EvidenceLevel.FACT, "Local-only runtime."))
        },
        sources = listOf(SRC_ORT_MOBILE, SRC_ORT_ANDROID, SRC_NNAPI_MIG),
        notes = "Keep as the escape hatch for ONNX-only model needs."
    )

    // ============================================================
    // FAMILY D - TFLite / LiteRT runtime
    // ============================================================

    private val candidateD1 = ModelCandidate(
        candidateId = "D1-tflite-litert",
        familyId = "D",
        familyName = "On-device runtime: TensorFlow Lite / LiteRT",
        name = "TensorFlow Lite (LiteRT)",
        architecture = "On-device inference runtime (XNNPACK CPU, GPU delegate, vendor NPUs)",
        runtimes = listOf(ModelFormat.TFLITE),
        status = CandidateStatus.SHORTLISTED,
        statusReason = "The established Android on-device runtime: Apache-2.0, mature tooling, XNNPACK/GPU/NPU delegation, and best-fit with the 8B-14 contract. NNAPI delegate is deprecated and intentionally not used.",
        license = "Apache-2.0",
        licenseEvidence = EvidenceLevel.DOCUMENTED_BY_SOURCE,
        androidMinApi = 24,
        metrics = emptyList(),
        scores = buildMap {
            put(EvaluationCriterion.TAXONOMY_ALIGNMENT, s(EvaluationCriterion.TAXONOMY_ALIGNMENT, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Neutral runtime; mapping belongs to the model."))
            put(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, s(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Vision runtime; captions handled by the OCR path."))
            put(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, s(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "Model-dependent; carriable by any runtime."))
            put(EvaluationCriterion.MODEL_SIZE_MB, s(EvaluationCriterion.MODEL_SIZE_MB, CriterionRating.STRONG, EvidenceLevel.ENGINEERING_ESTIMATE, "Payload is the model; a few MB for CNN class."))
            put(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, s(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, CriterionRating.STRONG, EvidenceLevel.ENGINEERING_ESTIMATE, "Interpreter + XNNPACK roughly 1-3 MB per ABI."))
            put(EvaluationCriterion.PEAK_RAM, unk(EvaluationCriterion.PEAK_RAM))
            put(EvaluationCriterion.INFERENCE_LATENCY, s(EvaluationCriterion.INFERENCE_LATENCY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "XNNPACK FP32/INT8 + GPU delegate are widely benchmarked fast for CNN class."))
            put(EvaluationCriterion.ENERGY_PER_INFERENCE, s(EvaluationCriterion.ENERGY_PER_INFERENCE, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "Needs measurement."))
            put(EvaluationCriterion.TEMPORAL_MULTI_FRAME, s(EvaluationCriterion.TEMPORAL_MULTI_FRAME, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Temporal logic is app-side, not runtime-provided."))
            put(EvaluationCriterion.CPU_ONLY_FEASIBILITY, s(EvaluationCriterion.CPU_ONLY_FEASIBILITY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "XNNPACK CPU is the default path."))
            put(EvaluationCriterion.ACCELERATOR_OPTIONS, s(EvaluationCriterion.ACCELERATOR_OPTIONS, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Maintained GPU delegate + vendor NPUs; deprecated NNAPI avoided."))
            put(EvaluationCriterion.ANDROID_API_COMPATIBILITY, s(EvaluationCriterion.ANDROID_API_COMPATIBILITY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Covers minSdk 24 on all 2026 Android guidance."))
            put(EvaluationCriterion.FP16_SUPPORT, s(EvaluationCriterion.FP16_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "FP16 weight quantization supported."))
            put(EvaluationCriterion.INT8_SUPPORT, s(EvaluationCriterion.INT8_SUPPORT, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "INT8 / integer-only is first-class."))
            put(EvaluationCriterion.FOUR_BIT_SUPPORT, s(EvaluationCriterion.FOUR_BIT_SUPPORT, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "No first-class stable INT4 path."))
            put(EvaluationCriterion.RUNTIME_MATURITY, s(EvaluationCriterion.RUNTIME_MATURITY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Dominant runtime, stable releases, huge ecosystem."))
            put(EvaluationCriterion.LICENSE_COMPATIBILITY, s(EvaluationCriterion.LICENSE_COMPATIBILITY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "LiteRT Apache-2.0."))
            put(EvaluationCriterion.INTEGRATION_EFFORT, s(EvaluationCriterion.INTEGRATION_EFFORT, CriterionRating.STRONG, EvidenceLevel.ENGINEERING_ESTIMATE, "Best-documented Android path; adapter onto OnDeviceModel."))
            put(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, s(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Versioned artifact + provenance fit 8B-14; kernel determinism to verify."))
            put(EvaluationCriterion.PRIVACY_BOUNDED, s(EvaluationCriterion.PRIVACY_BOUNDED, CriterionRating.STRONG, EvidenceLevel.FACT, "Local-only runtime; no network path."))
        },
        sources = listOf(SRC_LITERT, SRC_NNAPI_MIG, SRC_FEED_COPY),
        notes = "Primary runtime decision; NNAPI delegate deliberately excluded."
    )

    // ============================================================
    // FAMILY E - ExecuTorch
    // ============================================================

    private val candidateE1 = ModelCandidate(
        candidateId = "E1-executorch",
        familyId = "E",
        familyName = "On-device runtime: ExecuTorch",
        name = "ExecuTorch",
        architecture = "PyTorch-native on-device runtime (torch.export -> XNNPACK/QNN/MediaTek/Vulkan)",
        runtimes = listOf(ModelFormat.EXECUTORCH),
        status = CandidateStatus.CONDITIONAL,
        statusReason = "Mature enough (BSD-3, Maven AAR 1.4.0 Aug 2026) but export tooling and AAR wiring are heavier than TFLite; adopt when PyTorch-origin models or QNN-accelerated models (e.g. future VLM) are selected.",
        license = "BSD-3-Clause",
        licenseEvidence = EvidenceLevel.DOCUMENTED_BY_SOURCE,
        androidMinApi = 24,
        metrics = listOf(
            PublishedMetric("executorch-android latest", "1.4.0 (Aug 2026)", EvidenceLevel.DOCUMENTED_BY_SOURCE, "Maven Central"),
            PublishedMetric("Backends", "XNNPACK, Qualcomm AI Engine / QNN, MediaTek, Vulkan", EvidenceLevel.DOCUMENTED_BY_SOURCE, "docs.pytorch.org/executorch"),
            PublishedMetric("Android demos", "Llama, LLaVA/vision apps", EvidenceLevel.DOCUMENTED_BY_SOURCE, "pytorch/executorch")
        ),
        scores = buildMap {
            put(EvaluationCriterion.TAXONOMY_ALIGNMENT, s(EvaluationCriterion.TAXONOMY_ALIGNMENT, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Mapping depends on the model."))
            put(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, s(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Vision runtime."))
            put(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, s(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "Model-dependent."))
            put(EvaluationCriterion.MODEL_SIZE_MB, s(EvaluationCriterion.MODEL_SIZE_MB, CriterionRating.ADEQUATE, EvidenceLevel.ENGINEERING_ESTIMATE, "Payload is the exported .pte model."))
            put(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, s(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, CriterionRating.WEAK, EvidenceLevel.ENGINEERING_ESTIMATE, "AAR + fbjni + soloader + backend libs; heavier than TFLite baseline."))
            put(EvaluationCriterion.PEAK_RAM, unk(EvaluationCriterion.PEAK_RAM))
            put(EvaluationCriterion.INFERENCE_LATENCY, s(EvaluationCriterion.INFERENCE_LATENCY, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "XNNPACK/QNN delegates and official demos exist; no FeedSense figure."))
            put(EvaluationCriterion.ENERGY_PER_INFERENCE, s(EvaluationCriterion.ENERGY_PER_INFERENCE, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "Needs measurement."))
            put(EvaluationCriterion.TEMPORAL_MULTI_FRAME, s(EvaluationCriterion.TEMPORAL_MULTI_FRAME, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Not native to the runtime."))
            put(EvaluationCriterion.CPU_ONLY_FEASIBILITY, s(EvaluationCriterion.CPU_ONLY_FEASIBILITY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "XNNPACK CPU delegate is always built into the Android AAR."))
            put(EvaluationCriterion.ACCELERATOR_OPTIONS, s(EvaluationCriterion.ACCELERATOR_OPTIONS, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "QNN for Snapdragon HTP, MediaTek, Vulkan delegates."))
            put(EvaluationCriterion.ANDROID_API_COMPATIBILITY, s(EvaluationCriterion.ANDROID_API_COMPATIBILITY, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Maven AAR since v1.0.0 (Oct 2025); younger integration surface."))
            put(EvaluationCriterion.FP16_SUPPORT, s(EvaluationCriterion.FP16_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "FP16 exports supported."))
            put(EvaluationCriterion.INT8_SUPPORT, s(EvaluationCriterion.INT8_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "PTQ/QAT INT8 supported."))
            put(EvaluationCriterion.FOUR_BIT_SUPPORT, s(EvaluationCriterion.FOUR_BIT_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Weight-only INT4 (torchao) is a first-class export path."))
            put(EvaluationCriterion.RUNTIME_MATURITY, s(EvaluationCriterion.RUNTIME_MATURITY, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "v1.4.0 with 26 Maven versions; younger ecosystem than TFLite."))
            put(EvaluationCriterion.LICENSE_COMPATIBILITY, s(EvaluationCriterion.LICENSE_COMPATIBILITY, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "BSD-3-Clause runtime."))
            put(EvaluationCriterion.INTEGRATION_EFFORT, s(EvaluationCriterion.INTEGRATION_EFFORT, CriterionRating.WEAK, EvidenceLevel.ENGINEERING_ESTIMATE, "torch.export + quantization pipeline + manual AAR wiring."))
            put(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, s(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Deterministic in principle; per-backend variation to verify."))
            put(EvaluationCriterion.PRIVACY_BOUNDED, s(EvaluationCriterion.PRIVACY_BOUNDED, CriterionRating.STRONG, EvidenceLevel.FACT, "Local-only runtime."))
        },
        sources = listOf(SRC_ET_REPO, SRC_ET_MVN, SRC_ET_ANDROID),
        notes = "Keep warm for the multimodal stage (F) and for QNN-accelerated models."
    )

    // ============================================================
    // FAMILY F - multimodal / VLM (observed, not selected)
    // ============================================================

    private val candidateF1 = ModelCandidate(
        candidateId = "F1-qwen2.5-vl-family",
        familyId = "F",
        familyName = "Small multimodal / VLM",
        name = "Qwen2.5-VL (3B / 7B)",
        architecture = "Transformer VLM with visual encoder + language decoder",
        runtimes = listOf(ModelFormat.ONNX, ModelFormat.EXECUTORCH),
        status = CandidateStatus.OBSERVED_ONLY,
        statusReason = "Multimodal future-stage candidate. 3B-class weights (multi-GB) and LLM decode latency are out of scope for the 8B-15-2 classifier; license and Android runtime path not verified.",
        license = "Apache-2.0 (claimed; not verified)",
        licenseEvidence = EvidenceLevel.HYPOTHESIS,
        parametersMillions = 3071.0,
        parametersEvidence = EvidenceLevel.DOCUMENTED_BY_SOURCE,
        modelSizeMegabytes = null,
        sizeEvidence = EvidenceLevel.ENGINEERING_ESTIMATE,
        androidMinApi = null,
        metrics = listOf(
            PublishedMetric("Sizes", "3B / 7B / 32B / 72B", EvidenceLevel.DOCUMENTED_BY_SOURCE, "HuggingFace Qwen/Qwen2.5-VL"),
            PublishedMetric("Optimized 7B", "available in Qualcomm ai-hub", EvidenceLevel.DOCUMENTED_BY_SOURCE, "Qualcomm AI Hub")
        ),
        scores = buildMap {
            put(EvaluationCriterion.TAXONOMY_ALIGNMENT, s(EvaluationCriterion.TAXONOMY_ALIGNMENT, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Classification via prompting is flexible but brittle to map."))
            put(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, s(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, CriterionRating.STRONG, EvidenceLevel.HYPOTHESIS, "Reads captions/subtitles as text+image; ideal for meme/ad/clip categories."))
            put(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, s(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "Prompt-based; needs a corpus experiment."))
            put(EvaluationCriterion.MODEL_SIZE_MB, s(EvaluationCriterion.MODEL_SIZE_MB, CriterionRating.WEAK, EvidenceLevel.ENGINEERING_ESTIMATE, "3B params ~6 GB FP16 / ~1.5 GB INT4 weights."))
            put(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, s(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, CriterionRating.WEAK, EvidenceLevel.ENGINEERING_ESTIMATE, "Needs ExecuTorch/ORT or LLM runtime stack."))
            put(EvaluationCriterion.PEAK_RAM, s(EvaluationCriterion.PEAK_RAM, CriterionRating.WEAK, EvidenceLevel.ENGINEERING_ESTIMATE, "Decode needs hundreds of MB to GBs of RAM."))
            put(EvaluationCriterion.INFERENCE_LATENCY, s(EvaluationCriterion.INFERENCE_LATENCY, CriterionRating.WEAK, EvidenceLevel.ENGINEERING_ESTIMATE, "LLM decode latency per frame far exceeds CNN."))
            put(EvaluationCriterion.ENERGY_PER_INFERENCE, s(EvaluationCriterion.ENERGY_PER_INFERENCE, CriterionRating.WEAK, EvidenceLevel.ENGINEERING_ESTIMATE, "Large model, high battery cost."))
            put(EvaluationCriterion.TEMPORAL_MULTI_FRAME, s(EvaluationCriterion.TEMPORAL_MULTI_FRAME, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "VLMs handle multi-frame video context; option 4/5-friendly."))
            put(EvaluationCriterion.CPU_ONLY_FEASIBILITY, s(EvaluationCriterion.CPU_ONLY_FEASIBILITY, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "3B on CPU is slow; needs NPU/GPU."))
            put(EvaluationCriterion.ACCELERATOR_OPTIONS, s(EvaluationCriterion.ACCELERATOR_OPTIONS, CriterionRating.WEAK, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Emerging paths only (QNN via ExecuTorch, ai-hub 7B)."))
            put(EvaluationCriterion.ANDROID_API_COMPATIBILITY, s(EvaluationCriterion.ANDROID_API_COMPATIBILITY, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Depends on ExecuTorch/ORT stacks; no native Android path."))
            put(EvaluationCriterion.FP16_SUPPORT, s(EvaluationCriterion.FP16_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Released in BF16/FP16 weights."))
            put(EvaluationCriterion.INT8_SUPPORT, s(EvaluationCriterion.INT8_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "INT8/PTQ deployment is common for this family."))
            put(EvaluationCriterion.FOUR_BIT_SUPPORT, s(EvaluationCriterion.FOUR_BIT_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "INT4 weight-only is standard VLM deployment practice."))
            put(EvaluationCriterion.RUNTIME_MATURITY, s(EvaluationCriterion.RUNTIME_MATURITY, CriterionRating.WEAK, EvidenceLevel.UNKNOWN, "No stable packaged Android runtime verified."))
            put(EvaluationCriterion.LICENSE_COMPATIBILITY, s(EvaluationCriterion.LICENSE_COMPATIBILITY, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Qwen2.5 line claims Apache-2.0; artifact page not verified."))
            put(EvaluationCriterion.INTEGRATION_EFFORT, s(EvaluationCriterion.INTEGRATION_EFFORT, CriterionRating.WEAK, EvidenceLevel.ENGINEERING_ESTIMATE, "Prompting + structured output + runtime bring-up."))
            put(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, s(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Decode sampling is non-deterministic without greedy/temperature control."))
            put(EvaluationCriterion.PRIVACY_BOUNDED, s(EvaluationCriterion.PRIVACY_BOUNDED, CriterionRating.ADEQUATE, EvidenceLevel.FACT, "Can run fully local; larger RAM/thermal envelope."))
        },
        sources = listOf(SRC_QWEN_VL),
        notes = "Observed only. Revisit for multimodal architecture (option 5) in a later milestone."
    )

    private val candidateF2 = ModelCandidate(
        candidateId = "F2-smolvlm family",
        familyId = "F",
        familyName = "Small multimodal / VLM",
        name = "SmolVLM / SmolVLM2 (256M / 500M / 2.2B)",
        architecture = "Compact VLM: SigLIP vision encoder + pixel-shuffle compression + small Llama-3-style decoder",
        runtimes = listOf(ModelFormat.ONNX, ModelFormat.EXECUTORCH),
        status = CandidateStatus.OBSERVED_ONLY,
        statusReason = "Edge-targeted and Apache-2.0, with real on-device evidence, but still an LLM decode per frame: too heavy and too slow for the initial classifier. A candidate for the multimodal stage.",
        license = "Apache-2.0",
        licenseEvidence = EvidenceLevel.DOCUMENTED_BY_SOURCE,
        parametersMillions = 256.0,
        parametersEvidence = EvidenceLevel.DOCUMENTED_BY_SOURCE,
        androidMinApi = null,
        metrics = listOf(
            PublishedMetric("256M memory", "runs in < 1 GB GPU RAM", EvidenceLevel.DOCUMENTED_BY_SOURCE, "arXiv 2504.05299"),
            PublishedMetric("Throughput (256M)", "80 decode tok/s on M4 Max via WebGPU; phone figures unpublished", EvidenceLevel.DOCUMENTED_BY_SOURCE, "arXiv 2504.05299"),
            PublishedMetric("Mobile demo", "HF iPhone app runs 500M variant locally", EvidenceLevel.DOCUMENTED_BY_SOURCE, "HF SmolVLM / Fora Soft")
        ),
        scores = buildMap {
            put(EvaluationCriterion.TAXONOMY_ALIGNMENT, s(EvaluationCriterion.TAXONOMY_ALIGNMENT, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "Prompt-based classification, brittle mapping."))
            put(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, s(EvaluationCriterion.TEXT_OVERLAY_ROBUSTNESS, CriterionRating.STRONG, EvidenceLevel.HYPOTHESIS, "Multimodal text+image context fits FeedSense content."))
            put(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, s(EvaluationCriterion.FEED_CONTENT_GENERALIZATION, CriterionRating.UNKNOWN, EvidenceLevel.REQUIRES_EXPERIMENT, "Needs a corpus experiment."))
            put(EvaluationCriterion.MODEL_SIZE_MB, s(EvaluationCriterion.MODEL_SIZE_MB, CriterionRating.ADEQUATE, EvidenceLevel.ENGINEERING_ESTIMATE, "256M/500M params ~0.5-1 GB FP16; smaller quantized."))
            put(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, s(EvaluationCriterion.RUNTIME_PACKAGE_SIZE, CriterionRating.ADEQUATE, EvidenceLevel.ENGINEERING_ESTIMATE, "Smaller than 3B-class but still an LLM stack."))
            put(EvaluationCriterion.PEAK_RAM, s(EvaluationCriterion.PEAK_RAM, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "256M < 1 GB GPU RAM; phone RAM feasible but tight."))
            put(EvaluationCriterion.INFERENCE_LATENCY, s(EvaluationCriterion.INFERENCE_LATENCY, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Edge-targeted per paper; phone figures unpublished."))
            put(EvaluationCriterion.ENERGY_PER_INFERENCE, s(EvaluationCriterion.ENERGY_PER_INFERENCE, CriterionRating.WEAK, EvidenceLevel.ENGINEERING_ESTIMATE, "LLM decode is battery-hungry at frame rate."))
            put(EvaluationCriterion.TEMPORAL_MULTI_FRAME, s(EvaluationCriterion.TEMPORAL_MULTI_FRAME, CriterionRating.STRONG, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Video tasks (Video-MME) and SmolVLM2 video input."))
            put(EvaluationCriterion.CPU_ONLY_FEASIBILITY, s(EvaluationCriterion.CPU_ONLY_FEASIBILITY, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "256M may run on modern CPU at reduced rate."))
            put(EvaluationCriterion.ACCELERATOR_OPTIONS, s(EvaluationCriterion.ACCELERATOR_OPTIONS, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Optimized ONNX exports published with the model."))
            put(EvaluationCriterion.ANDROID_API_COMPATIBILITY, s(EvaluationCriterion.ANDROID_API_COMPATIBILITY, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "No first-class Android runtime verified."))
            put(EvaluationCriterion.FP16_SUPPORT, s(EvaluationCriterion.FP16_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "FP16 weights available."))
            put(EvaluationCriterion.INT8_SUPPORT, s(EvaluationCriterion.INT8_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "ONNX exports include quantized variants."))
            put(EvaluationCriterion.FOUR_BIT_SUPPORT, s(EvaluationCriterion.FOUR_BIT_SUPPORT, CriterionRating.ADEQUATE, EvidenceLevel.HYPOTHESIS, "INT4 weight-only standard for VLMs."))
            put(EvaluationCriterion.RUNTIME_MATURITY, s(EvaluationCriterion.RUNTIME_MATURITY, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "HF ecosystem strong; packaged Android runtime path unverified."))
            put(EvaluationCriterion.LICENSE_COMPATIBILITY, s(EvaluationCriterion.LICENSE_COMPATIBILITY, CriterionRating.ADEQUATE, EvidenceLevel.DOCUMENTED_BY_SOURCE, "Apache-2.0 stated for SmolVLM2 across sizes."))
            put(EvaluationCriterion.INTEGRATION_EFFORT, s(EvaluationCriterion.INTEGRATION_EFFORT, CriterionRating.WEAK, EvidenceLevel.ENGINEERING_ESTIMATE, "ONNX/ExecuTorch conversion + decode pipeline."))
            put(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, s(EvaluationCriterion.DETERMINISM_REPRODUCIBILITY, CriterionRating.WEAK, EvidenceLevel.HYPOTHESIS, "Decode sampling needs explicit control."))
            put(EvaluationCriterion.PRIVACY_BOUNDED, s(EvaluationCriterion.PRIVACY_BOUNDED, CriterionRating.ADEQUATE, EvidenceLevel.FACT, "Local-capable."))
        },
        sources = listOf(SRC_SMOL_PAPER, SRC_SMOL_LIC, SRC_MVLM_REPO, SRC_MVLM_PAPER),
        notes = "MobileVLM V2 (1.7B/3B, Apache-2.0) documents the same category of approach; neither is selected in 8B-15-1."
    )

    // ============================================================
    // CATALOG
    // ============================================================

    val criteria: List<EvaluationCriterion> =
        EvaluationCriterion.entries.toList()

    val candidates: List<ModelCandidate> = listOf(
        candidateA1, candidateA2, candidateB1, candidateC1,
        candidateD1, candidateE1, candidateF1, candidateF2
    ).sortedBy { it.candidateId }

    val decision: ArchitectureDecisionRecord
        get() = adr0001

    /*
     * Stable family order A..F even after sorting by id.
     */
    val familiesInOrder: List<String> =
        listOf("A", "B", "C", "D", "E", "F")

    init {
        validate()
    }

    fun candidate(candidateId: String): ModelCandidate? =
        candidates.byId(candidateId)

    fun candidatesByFamily(familyId: String): List<ModelCandidate> =
        candidates.byFamily(familyId)

    /*
     * Full matrix row for a candidate: any criterion without an
     * explicit score is completed with UNKNOWN/NOT_APPLICABLE so
     * rendering never fabricates a rating.
     */
    fun completeScores(candidate: ModelCandidate): Map<EvaluationCriterion, CriterionScore> {
        val completed = linkedMapOf<EvaluationCriterion, CriterionScore>()
        for (c in criteria) {
            completed[c] = candidate.scores[c] ?: unk(c)
        }
        return completed
    }

    private fun validate() {
        val ids = candidates.map { it.candidateId }
        require(ids.toSet().size == ids.size) {
            "duplicate candidateIds in catalog"
        }
        for (f in familiesInOrder) {
            require(candidates.any { it.familyId == f }) {
                "family '$f' is declared but has no candidate"
            }
        }
        for (c in candidates) {
            require(c.scores.keys.all { it in criteria }) {
                "unknown criterion on ${c.candidateId}"
            }
            if (c.status == CandidateStatus.SHORTLISTED) {
                var scored = 0
                for (crit in criteria) {
                    if (c.hasScore(crit)) scored++
                }
                require(scored == criteria.size) {
                    "SHORTLISTED ${c.candidateId} must score all 20 criteria; got $scored/20"
                }
            }
        }
    }

    // ============================================================
    // ADR-0001
    // ============================================================

    private val adr0001: ArchitectureDecisionRecord = ArchitectureDecisionRecord(
        adrId = "adr-0001",
        title = "On-device classification runtime & model for the FeedSense ML path",
        status = AdrStatus.ACCEPTED,
        version = "1.0",
        supersedes = null,
        date = "2026-09-06",
        milestoneId = "8B-15-1",
        context = listOf(
            "FeedSense classifies short-form video/feed content locally using OCR text heuristics (baseline local-v6.0); categories are dominated by on-screen text (memes, ads, movie/series clips, captions).",
            "8B-14 established the OnDeviceModel abstraction and a parallel baseline-vs-ML comparison; this milestone selects the real model/runtime to sit behind it.",
            "Android guidance in 2026: the NNAPI is deprecated; LiteRT (TFLite) XNNPACK/GPU + vendor NPUs are the supported acceleration paths.",
            "Hard constraints: local-only, no network, minSdk 24, privacy-bounded input (8B-13 safe frames only); the baseline heuristic is never overwritten by ML."
        ),
        decision = "Standardize the FeedSense on-device ML path on LiteRT (TensorFlow Lite) as the runtime and MobileNetV4-Conv-S as the primary image-classification architecture, with EfficientNet-Lite as a documented fallback. This is a research-phase decision: no model is integrated or downloaded in 8B-15-1; integration happens in 8B-15-2 behind the 8B-14 OnDeviceModel contract, running alongside the baseline and never overwriting it.",
        alternatives = listOf(
            DecisionAlternative(
                "A2-efficientnet-lite",
                "EfficientNet-Lite (fallback)",
                "Not primary: packaged weight license unverified in this phase; kept as documented fallback."
            ),
            DecisionAlternative(
                "B1-compact-vit-mobilevit-family",
                "Compact ViT (MobileViT family)",
                "Rejected: no maintained packaged Android artifact found; attention is heavier on mobile CPU at equal size."
            ),
            DecisionAlternative(
                "C1-onnx-runtime-mobile",
                "ONNX Runtime Mobile",
                "Conditional: size-optimized mobile Maven package is stale (1.18.0, 2024) while the full AAR is large; adopt only for ONNX-only model needs."
            ),
            DecisionAlternative(
                "E1-executorch",
                "ExecuTorch",
                "Conditional later: mature (BSD-3, v1.4.0) but heavier export/wiring; adopt when PyTorch-origin or QNN-accelerated (incl. VLM) models are selected."
            ),
            DecisionAlternative(
                "F1-qwen2.5-vl-family",
                "Qwen2.5-VL (3B/7B)",
                "Observed only: multimodal stage candidate; multi-GB weights, decode latency, unverified license and Android runtime path."
            ),
            DecisionAlternative(
                "F2-smolvlm family",
                "SmolVLM/SmolVLM2 (256M/500M/2.2B)",
                "Observed only: real on-device evidence and Apache-2.0, but still LLM decode per frame; multimodal stage candidate."
            )
        ),
        consequences = listOf(
            "No new production dependency is added in 8B-15-1; the runtime dependency lands with 8B-15-2 integration.",
            "The ML path runs in parallel to the text-heuristic baseline; per-item agreement is surfaced by the 8B-14 comparison machinery and nothing overwrites the baseline record.",
            "NNAPI is intentionally not used; GPU/NPU delegation must be verified per representative device with CPU XNNPACK as default.",
            "The short-listed single-label classifier needs an explicit label-mapping layer to CategoryCatalog keys; multi-label support remains future (ModelTaskType.MULTI_LABEL_IMAGE)."
        ),
        risks = listOf(
            AdrRisk(
                "Accuracy on FeedSense categories is unknown for any candidate.",
                ImpactLevel.HIGH,
                ImpactLevel.HIGH,
                EvidenceLevel.REQUIRES_EXPERIMENT,
                "Run a corpus experiment vs the baseline text heuristic before any promotion; publish agreement, not assumed accuracy."
            ),
            AdrRisk(
                "Specific packaged weight artifacts (exported .tflite) may carry licenses that differ from the code.",
                ImpactLevel.MEDIUM,
                ImpactLevel.MEDIUM,
                EvidenceLevel.HYPOTHESIS,
                "Verify license + SHA-256 checksum of every packaged artifact at integration time; record in ModelMetadata.checksum."
            ),
            AdrRisk(
                "Caption/overlay-dominated categories may not be solved by vision alone.",
                ImpactLevel.HIGH,
                ImpactLevel.MEDIUM,
                EvidenceLevel.HYPOTHESIS,
                "Pursue the image+OCR hybrid architecture (option 4) in the experiment and keep the text-heuristic baseline."
            ),
            AdrRisk(
                "4-bit quantization is immature in TFLite; INT8 is the practical floor for the CNN.",
                ImpactLevel.LOW,
                ImpactLevel.LOW,
                EvidenceLevel.HYPOTHESIS,
                "Use INT8 for the classifier; revisit 4-bit only if larger models (VLM) enter via ExecuTorch."
            ),
            AdrRisk(
                "Device fragmentation of GPU/NPU availability and performance.",
                ImpactLevel.MEDIUM,
                ImpactLevel.MEDIUM,
                EvidenceLevel.UNKNOWN,
                "Benchmark on 2-3 representative devices with CPU as the default delegate."
            )
        ),
        futureValidation = listOf(
            "8B-15-2: build MobileNetV4-Conv-S TFLite classifier + adapter behind OnDeviceModel; measure latency/RAM/energy (MEASURED).",
            "Corpus experiment vs the baseline on FeedSense categories with real ground truth.",
            "Verify packaged weight licenses and artifact checksums before any promotion.",
            "Re-check ONNX Runtime Mobile Maven cadence and EfficientNet-Lite license at 8B-15-2 start."
        ),
        relatedDocuments = listOf(
            "docs/ml-model-selection.md",
            "docs/ml-inference.md (8B-14)",
            "docs/architecture.md",
            "docs/research-evaluation.md" 
        )
    )
}