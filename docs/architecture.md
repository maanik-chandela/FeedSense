# Architecture

The project consists of five major components.

1. Android Application
2. AI Engine
3. Backend
4. Database
5. Dashboard

Each module is independent and communicates using APIs.

## On-device ML inference foundation (8B-14)

The AI Engine contains two independent, coexisting prediction paths:

- **Baseline**: heuristic/evidence Fusion classifiers (`analysis/`,
  `analysis/fusion/`, `analysis/evidence/`) — `local-v6.0`,
  `local-fusion-v1`, `temporal-v1`. Never modified by the ML layer.
- **On-device ML**: a model-independent abstraction under
  `analysis/ml/` (`OnDeviceModel`, `ModelPreprocessor`,
  `OnDeviceInferenceEngine`, `ModelRegistry`, `FakeOnDeviceModel`).
  A future TFLite/ONNX/ExecuTorch model plugs in behind the same
  interfaces. The ML path consumes only privacy-approved frames from
  `analysis/privacy/` (8B-13) and produces separate
  `source="ML_MODEL"` `AiPredictionRecord`s (8B-14).

Both paths are deterministic, versioned, and comparable per item via
`BaselineMlComparison`. See `docs/ml-inference.md` for the full 8B-14
design.
## Model & runtime selection (8B-15-1)

Research decision phase: which on-device ML model/runtime FeedSense standardizes
on for the **experimental** ML path (8B-14).

- **Decision (ADR-0001, v1.0):** runtime = **LiteRT (TensorFlow Lite)**, model =
  **MobileNetV4-Conv-S** (INT8), fallback = EfficientNet-Lite. See
  `docs/adrs/adr-0001-on-device-ml-runtime-and-model.md`.
- **Full research:** `docs/ml-model-selection.md` — candidate families A–F
  (CNN / compact-ViT / ONNX Runtime / LiteRT / ExecuTorch / VLM), 20-criterion
  decision matrix, architecture options 1–6, quantization (FP16/INT8/4-bit),
  RQ1–RQ7, sources.
- **Machine-readable catalog:** `analysis/ml/selection/` (`ResearchDecisionCatalog`,
  evidence-graded candidates + ADR, deterministic `DecisionSerializer`).
- **Phase boundary honored:** no model integrated, no new production dependency,
  no baseline/taxonomy/schema/UI change. Integration is a later milestone (after
  8B-15-2's reproducibility-identity work) behind the 8B-14
  `OnDeviceModel` contract; the ML path never overwrites the baseline.
- **Platform note (2026):** Android NNAPI is deprecated; LiteRT uses XNNPACK CPU +
  GPU delegate + vendor NPUs; FeedSense will not depend on NNAPI.

## Model artifact & reproducibility identity (8B-15-2)

Deterministic, auditable identity for the experimental ML configuration selected in
8B-15-1, so any future research claim maps back to one immutable configuration.

- **Package:** `analysis/ml/repro/` — `ReproModelIdentity` (which model/version/
  origin/license), `ReproArtifactIdentity` + `ArtifactSha256` (exact bytes →
  SHA-256; `PENDING`/`NOT_AVAILABLE` states, never a fabricated checksum),
  `ReproRuntimeIdentity` (runtime independent of developer machine),
  `ReproQuantizationIdentity` (depth/method/scope, no quality claims),
  `ReproPreprocessingIdentity` (versioned metadata contract),
  `ReproOutputMappingIdentity` (adapts model output to the *existing* taxonomy),
  `ReproPrivacyIdentity` (8B-13 versions only, no private payloads),
  `ReproProvenance.kt` (ordered, distinguishable conversion chain),
  `ReproCompositeIdentity` + `ReproCanonicalSerializer` (byte-deterministic
  canonical form + SHA-256 composite identity).
- **Contract factory:** `ReproContractFactory` derives the contract from the
  8B-15-1 catalog, preserving SHORTLISTED (A1) vs CONDITIONAL (A2, fallback).
- **Honest availability:** the artifact is not acquired yet →
  `ARTIFACT_PENDING`, version fields `REPRO_UNSPECIFIED`, output labels
  `REPRO_UNRESOLVED_LABEL_SET`.
- **No production impact:** no model integrated, no new dependency, no Room
  migration, no change to baseline/session/buildFeedItem/taxonomy. See
  `docs/reproducibility.md`.
