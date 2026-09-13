# Changelog

## Milestone 8B-15-5 (model runtime adapter & inference boundary)

- **Runtime adapter boundary (standalone, research-grade):** established the
  boundary `validated preprocessed input → on-device runtime → raw model
  output` — WITHOUT connecting to production observations, baseline
  evaluation, recommendation logic, or any user-facing AI decision.
  - New `analysis/ml/runtime/` package (main):
    - `RuntimeErrorTaxonomy.kt` — `RuntimeFailureCode` (ARTIFACT_LOAD /
      RUNTIME_INIT / COMPATIBILITY_CHECK / INPUT_VALIDATION / INFERENCE /
      OUTPUT_VALIDATION / RESOURCE_LIFECYCLE / UNKNOWN phases),
      `RuntimeFailureSeverity` (FATAL / RECOVERABLE), `RuntimePhase`, and
      `RuntimeFailureFactory`.
    - `CompatibilityGate.kt` — explicit, stateless artifact/runtime/config/
      input compatibility checks (availability, format-vs-runtime, unknown
      runtime, dims/datatype/channels/integrity, provenance).
    - `ModelArtifactLoader.kt` — local-only model loading abstraction +
      `TestArtifactLoader` (TEST_ONLY); no downloads, no cloud fallback.
    - `AdapterRawModelOutput.kt` — runtime-independent raw output (tensor
      metadata + values + provenance + timing) + `OutputTensorValidator` with
      an explicit expected-output contract.
    - `ModelRuntimeAdapter.kt` — main adapter: init → infer → close, with
      deterministic output hashing and configurable `RuntimeInferenceBackend`.
    - `RuntimeLifecycle.kt` — UNLOADED/LOADING/READY/INFERRING/RELEASING/
      FAILED state machine with transition history.
    - `RuntimeThreading.kt`, `RuntimeInstrumentation.kt`,
      `RuntimeProvenance.kt` — threading config, timing collection, privacy
      provenance preservation.
    - `TestInferenceBackend.kt` — TEST_ONLY deterministic in-JVM backend.
  - **Honest constraints:** no LiteRT/TFLite library is in the build; all
    inference is exercised through the TEST_ONLY backend. MobileNetV4-Conv-S
    artifact remains PENDING (8B-15-2). No inference numbers claimed.
  - **Isolation preserved:** `FeedItem`, `buildFeedItem`, `SessionRepository`,
    `GroundTruth`, evaluation logic, and the baseline AI path are untouched.
    No category labels, recommendations, or session-level decisions are
    produced by this boundary.
  - Tests: 104 JVM tests in
    `app/src/test/java/com/example/feedsense/analysis/ml/runtime/` covering the
    error taxonomy, compatibility gate, artifact loader, adapter init/inference/
    lifecycle, deterministic output hashing, the golden
    preprocessing→adapter→output integration path, and baseline isolation.
  - Docs: `docs/runtime-adapter.md`.
  - Phase boundary honored: raw model output only; interpretation/evaluation is
    a later milestone (8B-15-6 NOT started).

## Milestone 8B-15-2 (model artifact & reproducibility identity)

- **Reproducibility contract (standalone, metadata-only):** established the
  deterministic, auditable identity of the ML configuration selected in 8B-15-1
  — WITHOUT touching the inference pipeline, baseline classifier,
  `SessionRepository`, `buildFeedItem`, `CategoryCatalog`, Room schema, or any
  production prediction field. No model integrated, no new dependency, no cloud,
  no training/fine-tuning, no fabricated measurements.
  - New `analysis/ml/repro/` package:
    - `ReproModelIdentity` — model id / family / architecture / version /
      origin / publisher / license (MODEL id distinct from ARTIFACT hash).
    - `ReproArtifactIdentity` + `ArtifactSha256` — SHA-256 over exact bytes;
      explicit `AVAILABLE`/`PENDING`/`NOT_AVAILABLE` states; a checksum may
      only exist for an available artifact.
    - `ReproRuntimeIdentity` — runtime version independent of the developer
      machine (LiteRT target, backend, platform, format).
    - `ReproQuantizationIdentity` — depth/method/scope/tool/calibration (INT8
      direction from 8B-15-1; no accuracy claims).
    - `ReproPreprocessingIdentity` — versioned metadata contract; unverified
      sizes are explicit nulls (8B-15-3 scope kept out).
    - `ReproOutputMappingIdentity` — output labels/ordering/top-K adapted to
      the existing taxonomy; versioned independently.
    - `ReproPrivacyIdentity` — 8B-13 privacy/policy versions only, no private
      payloads.
    - `ReproProvenance.kt` — ordered, distinguishable artifact provenance chain
      with `ReproConversionRecord` for format-changing transitions.
    - `ReproCompositeIdentity` + `ReproCanonicalSerializer` — deterministic
      composite identity with byte-stable canonical serialization (stable field
      order, enum labels, numeric/null formatting) and a canonical SHA-256; no
      addresses/hashCode/UUID/wall-clock/unordered-map dependence.
    - `ReproContractFactory` — derives the contract from the 8B-15-1 catalog,
      preserving SHORTLISTED (A1 MobileNetV4-Conv-S, primary) vs CONDITIONAL
      (A2 EfficientNet-Lite, fallback, license `UNKNOWN`).
  - **Honest artifact availability:** the `.tflite` artifact is not acquired
    yet → `ARTIFACT_PENDING`, no fabricated checksum/version
    (`REPRO_UNSPECIFIED`), output labels
    `REPRO_UNRESOLVED_LABEL_SET`; no validation claimed.
  - Tests: 102 JVM tests in
    `app/src/test/java/com/example/feedsense/analysis/ml/repro/` covering model/
    artifact/runtime/preprocessing/mapping identity equality + difference,
    SHA-256 (same bytes → same hash, changed bytes → changed hash, missing →
    PENDING), composite identity changes, canonical determinism (repeated runs),
    locale-independent formatting, and malformed-input rejection.
  - Docs: `docs/reproducibility.md`, `docs/architecture.md` (8B-15-2 section),
    `docs/research-evaluation.md` (§17.6).
  - Phase boundary honored: standalone reproduction metadata only; no Room
    migration; no production integration; integration is a later milestone.

## Milestone 8B-15-1 (model & runtime evaluation — research decision)

- **Research decision phase** (no implementation): selected the on-device ML
  architecture and runtime for the 8B-14 experimental ML path, with graded
  evidence and reproducible structures.
  - Decision: **LiteRT (TensorFlow Lite)** runtime + **MobileNetV4-Conv-S**
    (INT8) model, EfficientNet-Lite as documented fallback.
  - New `analysis/ml/selection/` package: `ResearchDecisionCatalog` (candidates
    A–F, 20-criterion matrix with qualitative ratings + evidence levels, the
    ADR), `EvidenceLevel` (FACT / MEASURED / DOCUMENTED_BY_SOURCE /
    ENGINEERING_ESTIMATE / HYPOTHESIS / UNKNOWN / REQUIRES_EXPERIMENT),
    `EvaluationCriterion` (20 criteria), `ModelCandidate`, `CandidateStatus`
    (SHORTLISTED / CONDITIONAL / REJECTED / OBSERVED_ONLY), `SourceReference`,
    `ArchitectureDecisionRecord` + `AdrStatus`/`AdrRisk`, and the deterministic
    `DecisionSerializer` (sorted keys, optional-field omission, no numeric
    grades).
  - Docs: `docs/ml-model-selection.md`, `docs/adrs/adr-0001-on-device-ml-runtime-and-model.md`,
    `docs/architecture.md` (8B-15-1 section), `docs/research-evaluation.md` (§17).
  - Tests: 57 JVM tests in
    `app/src/test/java/com/example/feedsense/analysis/ml/selection/` covering
    deterministic serialization, stable ordering, missing-field omission,
    unknown values, versioning, malformed-input rejection and reproducibility.
  - Phase boundary honored: no model integrated, no new production dependency,
    no baseline/taxonomy/Room/UI change; NNAPI-deprecation (Android 2026)
    documented and avoided.

## Milestone 8B-13 (on-device privacy processing & visual anonymization)

- Added the **deterministic on-device privacy-processing pipeline**
  (`analysis/privacy/PrivacyProcessor.kt`) that replaces the
  heuristic sanitizer path with an explicit, versioned, tested
  DETECT -> DECIDE -> TRANSFORM -> VALIDATE flow — WITHOUT modifying
  the baseline AI, FeedItem semantics, category schema,
  GroundTruth/prediction/evaluation records, Room schema (v26), or
  the 8B-10/8B-11/8B-12 layers. No cloud, no self-learning, no
  large vision model, no `FLAG_SECURE` bypass.
- New processing primitives:
  - `PrivacyFrame`: bare ARGB buffer, mutated IN PLACE on the
    common path (raw-frame boundary, `spec §18`); CROP and the
    benchmark are the only second-buffer sites.
  - `PrivacyPolicyMode` (`RESEARCH`/`BALANCED`/`STRICT`) +
    versioned `PrivacyRuleTable`: mode-specific transformation
    rules with deterministic priority; `STRICT` may `DROP_FRAME`
    (high risk, <10% research value); `RESEARCH`/`BALANCED` never.
  - `PrivacyTransformation`: `NONE`/`BLUR`/`PIXELATE`/`MASK`/
    `CROP`/`DROP_FRAME` with severity ordering (strongest wins on
    overlap); no-op BLUR/PIXELATE escalates to MASK so VALIDATE
    always has evidence.
  - `PrivacyOcrDetection` — `OcrLine(text, bounds)` integration:
    email/phone/card/account patterns become line-atomic
    `PERSONAL_IDENTIFIER` regions; URLs are policy opt-in;
    unbounded lines fall back conservatively to the whole frame;
    research content never matches.
  - `PrivacyRisk` (`NONE`/`LOW`/`MEDIUM`/`HIGH`/`UNKNOWN`) and
    `PrivacyConfidence` aggregators; OCR-unavailable is an explicit
    state that surfaces `UNKNOWN` risk and is never silently
    research-safe.
- **Evidence integration**: `EvidenceSanitizer` /
  `AndroidPrivacyRegionApplier` / `PrivacyTextRedactor` connect the
  processor to the 8B-10 vocabulary; `PrivacySanitizationStatus`
  (`SANITIZED`/`PARTIALLY_SANITIZED`/`SANITIZATION_FAILED`/
  `SANITIZATION_UNAVAILABLE`/`NOT_REQUIRED`/`UNKNOWN`) and
  `PrivacyEvidenceLoss` ride on `ItemEvidenceSnapshot` and surface
  through `EvidenceAwareProductionAdapter` (read-only, baseline
  preserved).
- **Root-cause attribution**: `RootCauseTypes` adds
  `CAUSE_PRIVACY_EVIDENCE_LOSS` and `CAUSE_CAPTURE_UNAVAILABLE` as
  first-class candidate causes with human-readable descriptions.
- **Versioning**: `PrivacySanitizationVersion` (`privacy-v1`
  policy, `processing-v1`, `rules-v1`, coverage/benchmark/evaluation
  versions); `PrivacyMetrics.METRICS_VERSION` carries the policy
  version.
- **Evaluation & performance**: `PrivacyBenchmark` measures the
  disabled-vs-enabled difference on deterministic synthetic frames
  (`SyntheticFrames`, no randomness); `PrivacyCoverageEvaluator`
  compares against human-listed regions and reports observed
  agreement only.
- **Tests (all JVM, on-device-safe)**: `PrivacyProcessorTest`,
  `PrivacyRuleTest` (incl. STRICT masks-everything), `PrivacyOcr
  DetectionTest`, `PrivacyFalsePositiveTest` (@-handles, creator
  usernames, subtitles, ad copy never flagged), `PrivacyFalse
  NegativeTest` (account-id detection, fake notification/private
  message covered), `PrivacyEdgeCaseTest` (zero-area/sub-pixel
  regions, empty OCR, multiple regions never crash), `Privacy
  BaselineIsolationTest` (privacy processing never mutates
  `GroundTruth`/`AiPredictionRecord`, safe metadata carries no raw
  text or coordinates, determinism).
- Docs: `docs/privacy.md` gained the 8B-13 section (§12) covering
  the pipeline, raw-frame boundary, taxonomy equivalence, policy
  modes, transformations, confidence/risk, unknown risk, OCR
  integration, evidence wiring, root causes, performance
  methodology, limitations, and the baseline-impact statement.

## Milestone 8B-12 (adaptive frame sampling & inference scheduling)

- Added a **standalone, research-grade scheduling layer** in
  `analysis/scheduling/` that decides, per deduplicated candidate
  frame, whether to spend an expensive inference analysis —
  WITHOUT modifying the baseline AI, FeedItem semantics, category
  schema, 8A/8B records, Room schema (v26), or the 8B-11
  deduplication layer. Not yet wired into
  `SessionRepository.buildFeedItem()`/`FrameAnalysisWorker`;
  consumes `FrameSimilarityResult` — never hashes or inspects
  pixels itself.
- New `analysis/scheduling/` package:
  - `SamplingConfig.kt`: validated, versioned (`sampling-v1`)
    config; min/max intervals (1000/8000 ms), static/high/transition
    distances (4/8/12), pressure threshold (0.5), bounded rolling
    window (8, validated 1..64); `DEFAULT` / `BASELINE`
    (disabled passthrough, Experiment A) / `fixed(intervalMs)` /
    `COVERAGE_ONLY` presets.
  - `SamplingContext.kt` / `SamplingDecision.kt` /
    `SamplingReason.kt` / `SamplingStats.kt`: metadata-first
    evidence and full decision attribution; actions `ANALYZE` /
    `FORCE_ANALYZE` / `SKIP` (no DEFER — `SKIP` + `MIN_INTERVAL`
    carries deferral); reasons `FIRST_FRAME`, `MIN_INTERVAL`,
    `MAX_INTERVAL`, `VISUAL_CHANGE`, `STATIC_CONTENT`,
    `TRANSITION_SIGNAL`, `INTERACTION_SIGNAL`, `SCHEDULED_SAMPLE`,
    `DISABLED_PASSTHROUGH`, `UNAVAILABLE_FALLBACK`.
  - `FrameSamplingScheduler.kt`: deterministic decision order
    (disabled passthrough -> invalid fallback -> first frame ->
    max ceiling -> min gate -> transition -> interaction ->
    adaptive cadence -> static skip); inclusive min/max boundaries;
    adaptive interval `min + (max-min) * (1 - pressure)` over a
    bounded rolling window of 8B-11 distances; `elapsed` captured
    before any state mutation; `@Synchronized`; O(window) state
    with `reset()`, `retainedWindowSize()`,
    `retainedWindowCapacity()`, `lastAnalysisTimestampMs()`.
  - `SamplingBenchmark.kt`: deterministic 11-scenario synthetic
    report (static-screen, static-reel, moving-video,
    rapid-scrolling, new-reel, advertisement, comments, modal,
    overlay, slow-transition, rapid-transition) at a 400 ms
    cadence for 30 s, plus fixed-cadence mode. Explicitly NOT a
    real-world accuracy benchmark.
- Tests: 51 new unit tests in
  `app/src/test/.../analysis/scheduling/` (scheduler behaviour,
  interval boundaries, pressure/transition/interaction paths,
  fallback, reset/session isolation, determinism, sequences
  A A A A B B C C / A B C D E / scroll-burst A A' A'' A''' B),
  config validation, stats semantics, benchmark invariants, and
  §37 invariants 1-5 (min-closeness, max-starvation ceiling,
  reset isolation, determinism, bounded state). Hand-calibrated
  expected sequences.
- Verification: full suite **1334 unit tests green, 0 failures**
  (1283 prior + 51 new); `assembleDebug` builds clean.
- Documents: `docs/scheduling.md`, this changelog entry, and
  research §16 (`docs/research-evaluation.md`). No battery/CPU
  savings claimed; `samplingRatio` is a diagnostic.

## Milestone 8B-11 (perceptual frame deduplication & visual change detection)

- Added a **standalone, research-grade frame-efficiency layer** in
  `analysis/efficiency/` that decides, per captured frame, whether
  it is visually different enough from the current on-screen
  reference to justify downstream processing — WITHOUT modifying
  the baseline AI, FeedItem semantics, category schema, 8A
  records, or Room schema (v26). Not yet wired into
  `SessionRepository.buildFeedItem()`/`FrameAnalysisWorker`;
  exposes a forward hook (`forwards`) for the 8B-12 scheduling
  contract.
- New `analysis/efficiency/` package:
  - `FrameImage.kt`: minimal grayscale pixel contract + `RawPixelFrame`
    (pure JVM) — no color, no file handles, no raw-frame retention.
  - `PerceptualHash.kt`: `PerceptualHasher` (dHash default,
    pHash optional; `hashSize` 1..16, versioned `dhash-v1` /
    `phash-v1`); immutable word-packed `FrameHash` with `toHex()`;
    `HammingDistance` (`hamming-v1`) refusing cross-algorithm /
    cross-size comparisons. Box-averaged sampling for aliasing
    reduction and resolution invariance.
  - `FrameDeduplicationConfig.kt`: versioned config (`dedup-v1`)
    with defaults `maxHammingDistance=10`, forced-forward ceiling
    `maximumForwardIntervalMs=8000`, optional minimum frame
    interval; `DEFAULT` / `VISUAL_ONLY` / `DISABLED` / `STRICT`
    presets.
  - `FrameSimilarityResult.kt`: controlled decision vocabulary
    `DUPLICATE` / `SIMILAR` / `UNIQUE` (`shouldForward`/`forwards`)
    and reasons `FIRST_FRAME` / `COMPARED` / `TIME_WINDOW_GATED` /
    `FORCED_FORWARD` / `DISABLED_PASSTHROUGH`.
  - `FrameDeduplicator.kt`: stateful, `@Synchronized`, rolling
    reference = last accepted frame; inclusive reject boundary
    (distance == threshold rejects); time-window gating hashes
    nothing; forced-forward safety ceiling; O(1) state; `reset()`;
    injectable clock.
  - `FrameDedupStats.kt`: observed-only counts; shared denominator
    `deduplicationRate + forwardRate == 1`; `averageHashDistance`.
  - `EfficiencyBenchmark.kt`: reproducible hash-cost methodology
    (warmup + measured ms, median/min/max) — labeled methodology,
    not measurement claim.
  - `BitmapFrameImage.kt`: Android `Bitmap` adapter (main only),
    one transient pixel read, retains nothing.
- 43 new unit tests (pure JVM; synthetic deterministic frames):
  determinism; perceptual-vs-SHA-256 distinction; resolution
  invariance; 1x1/edge cases; Hamming across word boundaries +
  incompatibility guards; `classifyDistance` boundary; sequence
  `A A A B B C -> U D D U D D U`; rolling reference; forced-forward;
  minimum-interval gating; disabled pass-through; reset; O(1)
  memory over 10,000 frames; stats rates/snapshot;
  `EfficiencyBenchmark` plumbing; controlled engineering corpus
  (identical, resolution, brightness-shift, tiny-indicator,
  minor-motion, ad interstitial, dark/light inversion, caption,
  different-reel) with ordering + threshold-coherence invariants
  and explicit documented risks. Full suite now 1283 unit tests,
  all passing; `assembleDebug` green.
- Docs: `docs/perceptual-deduplication.md` (why, semantics, state
  machine, limitations, corpus, baseline strategy, 8B-12
  contract); `docs/research-evaluation.md` §15.
- Existing `analysis/dedup/` (7S/8B-3) and `analysis/PerceptualHash.kt`
  (7D) remain untouched; 8B-11 is an independent primitives layer.

## Milestone 8B-10 (privacy-safe evidence sanitization layer)

- Introduced a **production-safe, on-device evidence sanitization
  layer** that runs capture -> sanitize -> OCR/classification ->
  decision/evaluation WITHOUT changing the baseline AI, FeedItem
  semantics, the frozen category schema, ground truth, or the Room
  schema (v26). Raw frames remain on device for review; sanitized
  frames are the ONLY artifact passed to downstream analysis.
- New `analysis/privacy/` package:
  - `PrivacyRegionType.kt`: 8-type controlled taxonomy
    (`SYSTEM_UI`, `NOTIFICATION`, `PRIVATE_TEXT`,
    `PERSONAL_IDENTIFIER`, `PERSONAL_IMAGE`,
    `SENSITIVE_APPLICATION_UI`, `LOCATION_INFORMATION`,
    `UNKNOWN_SENSITIVE_REGION`) that is strictly private-adjacent
    and never touches the FeedSense category schema; unknown labels
    fall back conservatively to `UNKNOWN_SENSITIVE_REGION`.
  - `PrivacyRegion.kt`: typed detection regions with normalized
    geometry, detection signals, confidence bounds, and a bridge to
    the legacy geometric `ProtectedRegion` primitive.
  - `PrivacySanitizationStatus.kt`: six controlled statuses
    (`SANITIZED`, `PARTIALLY_SANITIZED`, `SANITIZATION_FAILED`,
    `SANITIZATION_UNAVAILABLE`, `NOT_REQUIRED`, `UNKNOWN`) with
    explicit `isSafeForResearchUse` and bidirectional legacy
    mapping to the 8B-4 rasterizer vocabulary.
  - `PrivacyPolicy.kt`: conservative, versioned operating policy
    (`RESEARCH`/`DEBUG`) - notifications, system UI, identifiers,
    private text, images, sensitive app UI, location on by default;
    never claims perfect anonymization.
  - `EvidenceSanitizer.kt`: deterministic 5-stage orchestrator
    (DETECT -> FILTER -> RASTERIZE -> REDACT -> AUDIT); never
    throws - failures return `SANITIZATION_FAILED` so capture
    continues; honesty check for "changed but no output file".
  - `PrivacyTextRedactor.kt`: `[REDACTED]`-token OCR redactor;
    URLs are NOT redacted by default (research-relevant evidence);
    research content ("IPL", "RCB", "YouTube", "Netflix") is
    preserved.
  - `SensitivePatternDetector.kt`: conservative pattern matching
    (email/phone/card/account/url) returning positions only.
  - `SanitizationAudit.kt`: privacy-safe audit trail (status,
    counts, versions; NEVER raw OCR/screenshot paths) plus a
    structured `PrivacyLogEntry` log line.
  - `EvidenceAvailability.kt`, `PrivacySanitizationVersion.kt`
    (privacy-v1 / sanitizer-v1 / pattern-detection-v1 /
    text-redaction-v1), `PrivacyMetrics.kt` (observed counters
    only; no fabricated rates), `PrivacyDataPolicy.kt`
    (storage states, retention classes, consent flags),
    `PrivacyEvidenceLoss.kt`
    (`NONE`, `EVIDENCE_LOST_DUE_TO_SANITIZATION`,
    `OCR_TEXT_REDACTED`, `CAPTURE_BLOCKED`),
    `RepresentativeFrame.kt` (sanitized-for-analysis,
    raw-for-review resolver), `PrivacyExportPolicy.kt` (export
    safe default = `SANITIZED_METADATA_ONLY`).
- **Frame worker rewiring**: `FrameAnalysisWorker` now sanitizes
  via `EvidenceSanitizer` + `AndroidPrivacyRegionApplier` (reuses
  the 8B-4 rasterizer), writes sanitized frames to
  `filesDir/sanitized`, stores REDACTED OCR text as
  `analysis.visibleText`, records a `privacySanitization` metadata
  block in the frame-analysis JSON, and logs via the privacy-safe
  audit trail (`logPrivacySanitization`). Fail-open behavior keeps
  capture alive when sanitization fails.
- **Policy-aware export**: `ExportManager` defaults to
  `PrivacyExportPolicy.SAFE_DEFAULT`; `DatasetExporter`
  `buildJsonByPrivacyPolicy` omits raw `visibleText` and raw frame
  paths under the safe mode, and records `privacyExportMode` +
  `privacyExportPolicyVersion` in the manifest. Legacy `buildJson`
  behavior is unchanged.
- **Evidence-aware privacy surfacing**: `ItemEvidenceSnapshot` gained
  a defaulted `privacyEvidence` field (source-compatible),
  `EvidenceAwareProductionAdapter` populates `privacyPolicyVersion`,
  `privacyStatusLabel`, `privacyEvidenceLossLabels`, and appends
  `PRIVACY_REDACTION_AFFECTED_DECISION` /
  `EVIDENCE_LOST_DUE_TO_SANITIZATION` reasons only when the metadata
  actually supports them; `SanitizationResult` gained defaulted
  version/status/audit fields.
- **Verification**: 11 new privacy unit-test files plus the
  privacy/decisions integration test; full unit suite (1234 tests)
  and `assembleDebug` both green.

**Requirement statement (unchanged baseline):**

`8B-10 does not modify FeedSense baseline predictions. Privacy
sanitization is an evidence-protection layer and does not alter
FeedItem semantics or the baseline AI.`

## Milestone 8B-9 (controlled real-data experiment & paired evaluation harness)

- Introduced a **controlled real-data experiment** harness that runs
  a baseline-vs-evidence-aware paired comparison over a real,
  bounded observation population, reusing the existing 8B-8
  comparative layer and 8A evaluation infrastructure WITHOUT
  training, mutating the baseline, altering sessions, or changing
  any production pipeline. The authoritative baseline AI result is
  never modified (observational/evaluative only).
- New `analysis/evaluation/experiment/` package:
  - `ExperimentDefinition.kt`: immutable experiment definition with
    dataset selection modes (`SESSION`, `ITEM_SET`, `DATE_RANGE`,
    `EVALUATION_DATASET`, `ALL_EVALUATED`), an explicit bounded
    maximum (bounded execution), version provenance, and full
    validation.
  - `EvidenceAwareOutcome.kt`: frozen five-way paired outcome
    (`BOTH_CORRECT`, `BASELINE_ONLY_CORRECT`,
    `EVIDENCE_AWARE_ONLY_CORRECT`, `BOTH_WRONG`, `INCOMPLETE`) with
    explicit improvement/regression semantics; abstention collapses
    to `INCOMPLETE`, never into an accuracy claim.
  - `GroundTruthEligibility.kt`: `ELIGIBLE_FOR_ACCURACY` vs
    `EXCLUDED_FROM_ACCURACY` (comparable definitive ground truth),
    matching the 8B-8 eligibility rule.
  - `PairedEvaluationResult.kt`: derived immutable per-observation
    view (pair + eligibility + outcome).
  - `DatasetSelection.kt`: pure, deterministic dataset selection -
    mode filter, deterministic id-ordered cap, SAME-SAMPLE
    guarantee (one item, one authoritative truth, judged once),
    annotator scoping, and explicit exclusion accounting.
  - `LeakageGuard.kt`: train-on-test leakage protection against a
    nominated training/validation dataset cohort.
  - `ExperimentAnalyzer.kt`: five-way outcome tally, the
    McNemar-ready paired contingency (baselineCorrect /
    evidenceAwareCorrect / discordant) reusing the 8B-8 exact
    `PairedStats.mcnemar`, and the real-data sufficiency guard.
  - `DatasetQualityReport.kt`: dataset-composition / quality report
    (eligible vs excluded, ambiguity, platform/content/duration
    distribution) - counts only.
  - `ExperimentSummary.kt`: immutable aggregate bundle including the
    reused 8B-8 `ComparativeReport` (confusion & difference
    matrices, per-category, stratification, root cause, conclusion),
    a strict `INSUFFICIENT_REAL_DATA` / `SUFFICIENT_REAL_DATA` flag,
    and an explicit baseline-safety statement.
  - `ExperimentSnapshot.kt`: audit trail with a deterministic
    SHA-256 idempotency signature so re-running the identical
    experiment on the identical population is detectable.
  - `ExperimentExporter.kt`: privacy-safe JSON and per-observation
    CSV export (counts, metadata, opaque ids only - never raw
    content).
  - `ExperimentRunner.kt` + `ExperimentDataSource.kt` +
    `RoomExperimentDataSource.kt`: read-only orchestrator and data
    sources. The Room-backed source reuses the EXISTING
    `EvaluationDao` (no DAO / schema change, database stays **26**).
- Tests: `ExperimentHarnessTest.kt` (23 tests) covering definition
  validation, outcome mapping, eligibility, dataset selection
  (mode/bounds/same-sample/exclusions/annotator), leakage guard,
  dataset quality, analyzer tallies + McNemar-ready output,
  snapshot idempotency, end-to-end runner, insufficient-real-data
  status, and privacy-safe export.

## Milestone 8B-8 (production-safe evidence-aware evaluation integration)

- Introduced a **production-safe adapter** that compares the
  authoritative baseline AI prediction against the experimental
  8B evidence-aware decision for existing FeedItems, without
  modifying any production state, session repository, category
  schema, or baseline AI prediction.
- `evidence/decision/ComparisonState.kt`: controlled enum of 7
  comparison states (`AGREEMENT`, `CATEGORY_DISAGREEMENT`,
  `BASELINE_UNKNOWN`, `EVIDENCE_AWARE_UNKNOWN`, `BOTH_UNKNOWN`,
  `INSUFFICIENT_EVIDENCE`, `REVIEW_REQUIRED`) replacing
  ad-hoc boolean logic with deterministic, auditable
  classification.
- `evidence/decision/DifferenceReason.kt`: controlled enum of 12
  structured difference reasons (`REPRESENTATIVE_FRAME_CONFLICT`,
  `TEMPORAL_CONTEXT_CHANGED_DECISION`, `OCR_CHANGED_DECISION`,
  `PLATFORM_EVIDENCE_CHANGED_DECISION`,
  `INTERACTION_EVIDENCE_CHANGED_DECISION`, `SEGMENTATION_DIFFERENCE`,
  `LOW_INFORMATION_EVIDENCE`, `SHORT_INTERACTION`,
  `CATEGORY_BOUNDARY`, `INSUFFICIENT_EVIDENCE`,
  `MODEL_DISAGREEMENT`, `UNKNOWN`) with an explicit preference for
  `UNKNOWN` over fabricated explanations.
- `evidence/decision/EvidenceAwareComparisonResult.kt`: immutable,
  auditable, privacy-preserving comparison result data class
  carrying identity (feedItemId, sessionId, evaluationItemId),
  versioning (adapterVersion, baselineModelVersion,
  evidenceAwareModelVersion, decisionVersion, datasetVersion),
  baseline fields (verbatim), evidence-aware fields, comparison
  dimensions (categoryChanged, confidenceChanged, contentTypeChanged,
  platformChanged, reviewStatusChanged, decisionAgreement), evidence
  strength assessment (NONE/WEAK/MODERATE/STRONG), and 8A bridge
  compatibility flag.
- `evidence/decision/EvidenceAwareProductionAdapter.kt`: read-only
  production adapter that composes the existing 8B-7
  `ReadOnlyEvidenceAwareAdapter` and `ItemDecisionEngine` to produce
  an `EvidenceAwareComparisonResult`. Key guarantees: FeedItem is
  never modified, no Room entity is written, no baseline prediction
  overwritten, no privacy-sensitive content exported (no screenshots,
  OCR text, captions, messages, personal names), deterministic (same
  inputs → same result), bounded batch processing with explicit
  limits (MAX_BATCH_SIZE=100), and safe failure handling (adapter
  errors produce a safe UNKNOWN/UNRESOLVED state without modifying
  production).
- 45 comprehensive unit tests (`EvidenceAwareProductionAdapterTest`)
  covering: baseline agreement, category disagreement, baseline
  unknown, evidence-aware unknown, both unknown, insufficient
  evidence, review required, no mutation of FeedItem, frozen
  AiPredictionRecord unchanged, version propagation, determinism,
  privacy (no raw evidence in serialized output), confidence ≠
  accuracy distinction, evidence strength ≠ correctness, OCR/platform/
  interaction/temporal evidence influence, representative frame
  conflict, segmentation difference, short interaction, category
  boundary, model disagreement fallback, missing evidence honesty,
  failure isolation, batch limit, batch ordering, 8A bridge
  compatibility, and session/dataset version propagation.
- Evidence strength assessment (`EvidenceStrength`: NONE/WEAK/
  MODERATE/STRONG) is explicitly separate from AI confidence and
  prediction correctness; tests prevent accidental conflation.
- No Room schema changes; database version remains **26**; no
  modification to `SessionRepository.buildFeedItem()`, category
  taxonomy, or baseline AI predictions.
- Total unit tests: 1079 (8B-7) → **1124 (8B-8)**, 0 failures, 0
  skipped. `assembleDebug` and `compileDebugAndroidTestKotlin`
  succeed.

## Milestone 8B-2 (temporal evidence fusion & multi-frame content understanding)

- Introduced a **deterministic temporal evidence reasoning layer**
  (`TEMPORAL_V1`, model version `local-fusion-v1-temporal`) that
  understands a content item as a **temporal sequence** rather than
  an isolated screenshot. Builds on 8B-1 and is NOT a trained
  temporal neural network. No cloud AI, no training, no
  self-learning; legacy `local-v6.0` and 8B-1 `local-fusion-v1`
  predictions are untouched. FeedItem remains the bounded temporal
  reasoning unit.
- `fusion/temporal/FrameTimeline.kt`: deterministic chronological
  ordering (timestamp ASC, stable tie-break by frameId), gap
  detection, missing/invalid timestamp handling, out-of-order
  insertion safety, frame intervals.
- `fusion/temporal/TemporalPersistence.kt`: time-aware persistence
  (duration, continuity, informative frames) with an explicit
  single-frame penalty so a transient overlay cannot outrank
  sustained multi-frame support (sections 11/12/46/47).
- `fusion/temporal/TemporalOcrAggregator.kt`: OCR deduplication
  (repeated text is one confirmation) and OCR evolution chains
  ("IPL" → "IPL FINAL" → "IPL FINAL HIGHLIGHTS") (sections 20–22).
- `fusion/temporal/TemporalConsistency.kt`: temporal consistency
  (NONE/WEAK/MODERATE/STRONG/CONFLICTING), category stability,
  and temporal conflict level; missing frames never count as
  contradictory evidence (sections 13/14/24/44).
- `fusion/temporal/TemporalTransition.kt`: transition detection,
  segment-boundary signals, and transition-frame candidates;
  transition is kept distinct from conflict (sections 16/26/52/27).
- `fusion/temporal/VisualFingerprint.kt`: fingerprint provider
  interface with unavailable-state safety; compact, local, never
  uploaded (sections 18/19/67).
- `fusion/temporal/TemporalFusionEngine.kt`: pure deterministic
  orchestrator over the required fusion order + incremental
  (`predictIncremental`) streaming API + bounded memory (sections
  35–39).
- `fusion/temporal/TemporalFusionPrediction.kt` +
  `TemporalFusionTrace.kt`: temporal prediction and compact
  diagnostic trace (categories, persistence, stability,
  transitions, rep-frame quality) with privacy preserved (76/77).
- `fusion/temporal/TemporalEvaluationBridge.kt`: 8A-compatible
  comparison for legacy vs fusion vs temporal (sections 72/82).
- `test/.../TemporalFusionTest.kt`: 44 tests covering the required
  temporal cases + property invariants. Test counts: 601 (8A-6) →
  630 (8B-1) → **674 (8B-2)**, 0 failures, 0 skipped.
- DB remains version 26; no schema change, no migration.

## Milestone 8B-1 (local evidence-fusion decision architecture)

- Introduced a **local, offline evidence-fusion decision
  architecture** (`FUSION_V1`, model version `local-fusion-v1`)
  that replaces the flat "screenshot → heuristics → category guess"
  pipeline with layered evidence extraction → normalization →
  candidate generation → fusion → uncertainty → decision →
  diagnostic trace. This is an **architectural foundation only**:
  no training, no cloud AI, no production model replacement. The
  production pipeline and its `local-v6.0` predictions are
  untouched; the new model is introduced BEHIND the legacy one so
  both remain comparable (8B-1 section 4/5/55).
- `fusion/FusionConfig.kt`: the single central config object —
  modelVersion, configurationVersion, model STATE (EXPERIMENTAL
  by default), provider weights, reliability, thresholds,
  confidence bands, ambiguity rule, duplicate-evidence policy, and
  temporal-consistency settings. All thresholds live here; there
  are no scattered magic numbers, and weights are documented
  reasoning, not tuned against the frozen test set (section 43/66).
- `fusion/EvidenceModel.kt`: structured `EvidenceRecord` with
  provenance (frame ids, compact timestamp, source), RAW-vs-
  interpretation clarity, integrity `EvidenceStrength`
  (`NONE/WEAK/MODERATE/STRONG`, independent of prediction
  confidence, reusing the 8A-6 vocabulary), and centralized
  reliability.
- `fusion/EvidenceContext.kt`: pure, deterministic inputs plus the
  `EvidenceProvider` contract (layers A–B).
- Providers: `TextEvidenceProvider` (OCR → RAW text evidence;
  explicit `OCR_UNAVAILABLE`, never `CONTENT_IS_UNKNOWN`),
  `PlatformEvidenceProvider` (platform is CONTEXT, never a
  category vote), `InteractionEvidenceProvider` (distinguishes
  `UI_PRESENT` from `UI_UNKNOWN`, never fabricates `UI_ACTIVE`),
  `VisualEvidenceProvider` (explicit interface; always emits
  `VISUAL_SIGNAL_UNAVAILABLE` — no fake vision), and
  `TemporalEvidenceProvider` (consistency boost, not a category
  source).
- `fusion/CandidateGenerator.kt`: Layer C reads RAW text and emits
  INTERPRETATION candidates using the frozen `CategoryCatalog`
  taxonomy with word-aware matching; the legacy
  `TextHeuristicClassifier` verdict can be wrapped as one equal
  CLASSIFIER voter, never the sole authority.
- `fusion/EvidenceNormalizer.kt`: normalization, information
  quality, and duplicate-evidence control (identical frames are
  one confirmation, not eight — 5 identical OCR frames do not
  inflate confidence).
- `fusion/FusionEngine.kt`: deterministic fusion (layers D–F) with
  candidate scoring, domain-consensus resolution (leaves of one
  domain are finer granularity, not conflicting content), central
  uncertainty states (`CONFIDENT/LOW_CONFIDENCE/AMBIGUOUS/
  INSUFFICIENT_EVIDENCE/CONFLICTING_EVIDENCE`), deterministic tie
  handling (never arbitrary), cross-frame temporal conflict, and
  compact diagnostic trace (Layer G).
- `fusion/FusionPrediction.kt` + `FusionTrace.kt`: kept dimensions
  separate (category vs topic vs tone vs content type), with a
  compact structural trace (no full OCR transcripts).
- `fusion/FusionEvaluationBridge.kt`: converts a FusionPrediction
  to an immutable `AiPredictionRecord` with `modelVersion =
  local-fusion-v1` + `source = AI` so the 8A evaluation engine can
  compare LEGACY vs FUSION on identical frozen items without
  overwriting the legacy prediction.
- No Room schema change (DB version stays 26); pure Kotlin
  in-memory structures only.
- Added 29 unit tests (`LocalFusionTest`) covering evidence model,
  absence semantics, dedup, candidate generation, all uncertainty
  states, determinism, temporal consistency, trace, versioning,
  the evaluation bridge, and realistic CASE 1–9. Total unit tests:
  630 (601 prior + 29), 0 failures; `assembleDebug` and
  `compileDebugAndroidTestKotlin` succeed.

## Milestone 8A-6 (root-cause validation & failure attribution)

- Introduced a root-cause validation / failure-attribution /
  error-analysis quality-control framework that determines whether
  suspected reasons behind FeedSense errors are actually supported
  by evidence, without changing the production AI and without
  training (builds on 8A-5; no redesign).
- `RootCauseTypes`: frozen controlled vocabularies — candidate root
  causes (`OCR_FAILURE` … `OTHER`), attribution statuses
  (`UNASSESSED`/`POSSIBLE`/`SUPPORTED`/`CONFIRMED`/`REJECTED`/
  `INCONCLUSIVE`/`NOT_APPLICABLE`), evidence strengths
  (`NONE`/`WEAK`/`MODERATE`/`STRONG`, independent of AI
  confidence), attribution classes (`MODEL`/`DATA`/`PIPELINE`/
  `TAXONOMY`/`ANNOTATION`/`UNKNOWN`), cause roles
  (`PRIMARY`/`CONTRIBUTING`), evidence types, and controlled
  diagnostic/segmentation/duration vocabularies.
- `Evidence`: evidence references that point only at existing
  objects (frame/prediction/truth ids — never raw screenshots or
  OCR payloads) plus a BOUNDED `temporalWindow` (before/current/
  after, capped) and a `frameContribution` helper that surfaces
  representative-frame failure as `POSSIBLE`, never asserted.
- `AttributionDecisionTree`: a deterministic, documented diagnostic
  workflow that classifies each error into an attribution class by
  the first decisive OBSERVED condition, falling back to `UNKNOWN`
  for human adjudication.
- `RootCauseAnalyzer`: deterministic per-unit generation of the
  full `RootCauseAssessment` (causes, roles, evidence strength,
  attribution class, attribution confidence, evidence references,
  versions). Machine output is never `CONFIRMED`; only observed
  facts (`SUPPORTED`, e.g. annotator disagreement) or hypotheses
  (`POSSIBLE`) are produced.
- `RootCauseAssessment`: versioned (`diagnostic-v1`), JSON-serializable
  model with human review fields and an immutable `auditTrail`.
- `AnnotationAgreement`: percent agreement + Cohen's kappa over the
  primary label, with the
  `INSUFFICIENT_DATA_FOR_INTER_ANNOTATOR_STATISTICS` guard when
  fewer than 2 raters exist.
- `RootCauseReport`: aggregation with association table, data- vs
  model- classification, deterministic priority score and hard-case
  review queue, TRAINING-CANDIDATE marking (no training), and
  references-only CSV/JSON export.
- No Room schema changes; database version remains **26**; all
  attribution is local/on-device (no cloud AI, no self-learning).
- 27 new unit tests (previous: 574, total: 601, all passing) and
  a determinism fix to a pre-existing UUID-hash-flaky 8A-5 test.

## Milestone 8A-5 (error analysis & failure taxonomy)

- Introduced a research-grade error-analysis, failure-taxonomy,
  and diagnostic-evaluation layer that explains when/why the
  FeedSense AI is wrong, without changing the production model,
  without retraining, and without tuning headline thresholds.
- `ErrorTypes`: frozen controlled taxonomy of 16 capabilities
  and 19 error types, with OBSERVED / HYPOTHESIS / CONFIRMED
  root-cause evidence levels and a controlled root-cause
  catalogue (8A-5 itself only ever writes OBSERVED/HYPOTHESIS).
- `ErrorAnalyzer`: deterministic per-unit detection of every
  applicable error type per capability, honoring "UNKNOWN truth
  is never an error", "UNCOMPARABLE is not a model failure", and
  metric consistency with 8A-3 (counts derived from the same
  EvaluationRecord fields).
- `ErrorSeverity`: LOW / MEDIUM / HIGH / CRITICAL mapping with a
  bump for overconfidence and core-capability errors, and a
  conservative `isResearchImpactful` guard (false when no real
  data).
- `ErrorConfusionMatrix`: per-capability predicted-vs-truth
  matrices (named to avoid collision with 8A-3's ConfusionMatrix).
- `ConfidenceAnalysis`: confidence-vs-correctness bucketing that
  surfaces overconfident errors and underconfident-correct cases.
- `ErrorPatternReport`: sample-gated HYPOTHESIS patterns +
  honest quality flags; never fabricates findings on small or
  empty cohorts (emits
  `INSUFFICIENT_REAL_DATA_FOR_STATISTICAL_CONCLUSIONS`).
- `ErrorAnalysisEngine` + `ErrorAnalysisReport`: pure orchestrator
  and a lossless JSON-serializable report (roll-ups, ordered error
  queue awaiting human diagnosis, confusion matrices, confidence
  analysis, patterns, severity distribution, quality flags).
- No Room schema changes; database version remains **26**.
- 38 new unit tests (previous: 536, total: 574, all passing).

## Milestone 8A-4 (evaluation dataset construction)

- Introduced a controlled, inspectable, reproducible process
  for constructing and validating an evaluation dataset from
  raw research data.
- `DatasetConstructionPolicy`: centralized tunables for what
  qualifies as a dataset (statuses, disputed handling,
  ambiguity handling, duplicate detection, ordering).
- `CapabilityEligibility`: per-capability eligibility so an
  item with unknown `liked` can still be used for category
  evaluation.
- `DatasetConstructor`: deterministic, pure construction that
  classifies every raw item as included or excluded-with-reason.
- `DatasetValidator`: structured validation (missing
  references, impossible durations, malformed confidence,
  invalid taxonomy, duplicate IDs, contradictions).
- `DatasetReporter`: distribution profiling (category,
  platform, content type, duration, skip, interaction,
  confidence, model version), coverage, completeness, and
  quality flags. No fake composite quality score.
- `DatasetManifest`: machine-readable, deterministic JSON
  manifest with full provenance + statistics + exclusion
  reasons + quality warnings.
- Preserves UNKNOWN, DISPUTED, AMBIGUOUS, and MIXED content;
  never converts UNKNOWN to false; no cherry-picking; no
  AI-based curation; no synthetic data.
- No Room schema changes; database version remains **26**.
- 56 new unit tests (previous: 480, total: 536, all passing).

## Milestone 8A-2 (annotation workflow)

- Non-destructive DB v24 → v25: per-signal interaction
  tri-state columns on `ground_truths`.
- Human ground-truth annotation form + annotation queue UI.
- Independent ground truth: re-edits update the current
  (item, annotator) record; the frozen AI snapshot is never
  overwritten.
- `DISPUTED` annotation status; tri-state skipped / interactions;
  taxonomy-driven validation focused on UNKNOWN honesty.
- Ground-truth schema documented in `docs/research-evaluation.md`.

## Version 0.0.1

- Repository created
- Initial folder structure
- Documentation added
