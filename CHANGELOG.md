# Changelog

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
