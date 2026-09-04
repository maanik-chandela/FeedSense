# Research Evaluation & Ground Truth (Milestone 8A)

This document describes the dedicated, non-destructive
research-evaluation layer and the human ground-truth
annotation workflow. It exists to make the ground-truth
dataset **trustworthy** and **reproducible**: the human's
answer is always an independent, auditable record, and the
AI prediction is never overwritten.

## 1. Principles

- **Ground truth is independent.** A human annotation lives
  in `ground_truths`; it is stored and compared against, but
  never written back into the `feed_items` AI columns or the
  frozen `ai_predictions` snapshot (`EvaluationRepository.saveAnnotation`,
  `EvaluationRepository.recordGroundTruth`).
- **Reproducibility.** Every evaluation row carries
  `feedItemId` + frozen `ai_predictions` (with `modelVersion`)
  + `groundTruth` + `annotatorId` + `datasetVersion` +
  timestamps, so a result can be recomputed identically.
- **No fabricated truth.** Interactions/skipped default to
  UNKNOWN. No code path converts an UNKNOWN into false.
- **Clean current record.** Re-editing an annotation *updates*
  the existing `(item, annotator)` row and *replaces* its
  evaluation result — it never appends a second, contradictory
  record. Full version history is future work.

## 2. Database version

- 8A-1: **v23 → v24** — added `evaluation_items`,
  `ai_predictions`, `ground_truths`, `evaluation_results`.
- 8A-2: **v24 → v25** (`MIGRATION_24_25`) — non-destructive
  `ALTER TABLE ... ADD COLUMN` on `ground_truths` adding the
  per-signal interaction tri-state columns. Purely additive;
  pre-existing rows report UNKNOWN for the new columns.
- 8A-3: **v25 → v26** (`MIGRATION_25_26`) — non-destructive
  `CREATE TABLE evaluation_runs` storing frozen `EvaluationRun`
  records: run metadata columns, the full serialized
  `configJson`, and the read-only `reportJson` (write-once
  evidence). Adds three indices (`dataset_id`, `run_id`,
  `created_at`). Frozen reports are appended, never mutated.

## 3. Tables

### evaluation_items
One row per content item queued for evaluation.
- `feedItemId`, `sessionId`, `projectId`, `modelVersion`
- `evaluationStatus` — the annotation status:
  - `NOT_EVALUATED` → UNREVIEWED
  - `PARTIALLY_EVALUATED` → IN_PROGRESS
  - `EVALUATED` → REVIEWED
  - `DISPUTED` → DISPUTED
- `datasetVersion` (assigned later by `assignDatasetVersion`
  to freeze a cohort)
- `createdAt`, `enqueuedAt`, `completedAt`

### ai_predictions
An **immutable** snapshot of the AI prediction at enqueue
time (category, confidence, modelVersion, secondary
categories, categoryScores, platform, contentType,
durationSeconds, skipped, interactionSignals, topic, tone,
uncertaintyLevel, needsReview). This freezes the AI answer so
later corrections to `feed_items` cannot silently change it.

### ground_truths (annotation answer)
- `evaluationItemId`, `annotatorId` (pseudonymous)
- `category`, `secondaryCategories` (0..n, deduped,
  primary never repeated)
- `ambiguity` — `CLEAR` / `AMBIGUOUS` / `MIXED` / `UNKNOWN`
  (UNKNOWN is a human judgment, not an AI error)
- `platform`, `contentType` (`SHORT_VIDEO`/`LONG_VIDEO`/
  `OTHER`/`UNKNOWN`), `durationSeconds`
- `skipped` — tri-state: `null`=UNKNOWN / `0`=no / `1`=yes
- interaction tri-state columns (`liked`, `commented`,
  `shared`, `saved`, `followed`, `paused`, `playing`):
  `null`=UNKNOWN / `0`=certain no / `1`=evidence yes
- `topic` (free-form, AI suggestion offered as a hint),
  `tone`, `notes`
- `recordedAt` (first-record time, stable across re-edits)

### evaluation_results
Computed verdict + agreement flags for `(snapshot, truth)`
(`CORRECT` / `INCORRECT` / `PARTIAL` / `UNKNOWN`), category /
secondary / topic / tone / platform / content-type agreement,
duration accuracy, skipped agreement, interaction-signal
disagreement. Replaced (not appended) on re-edit.

## 4. Validation rules (`AnnotationValidator`)

- A primary category **or** an explicit `UNKNOWN` ambiguity is
  required.
- Ambiguity must be chosen from the frozen set.
- Secondary categories are normalized to canonical keys,
  de-duplicated, and the primary is never a secondary.
- Platform / tone / content-type come from the single
  source-of-truth taxonomies (`PlatformDetector`,
  `TextHeuristicClassifier.TONE_VALUES()`, `CategoryCatalog`,
  `GroundTruth.VALID_*`).
- Interaction/skipped stay tri-state — UNKNOWN is never
  auto-filled with false.

## 5. Evidence viewed during annotation

- `representativeFramePath` + item metadata + the frozen
  `ai_predictions` snapshot.
- **Best-effort windowed frames** (documented limitation):
  the capture layer keeps no persistent `feedItemId` on frames,
  so "session frames within this item's time window" is a
  deterministic approximation using `capturedAt BETWEEN
  [startTime, endTime]`. The capture pipeline is NOT altered,
  and the limitation is surfaced rather than hidden.

## 6. Local-only & anonymity

All evaluation data is stored on-device in the local Room
database. Annotator identity is an opaque generated id with no
profile data attached, so a human's labels cannot be tied back
to a real person.

## 7. Evaluation engine & metric definitions (8A-3)

The engine is a **read-only, research-grade** evaluator
(`evaluation/EvaluationEngine.kt`). It compares frozen
`ai_predictions` vs `ground_truths`, produces a 13-section
`EvaluationReport`, and freezes it as a JSON blob in an
`evaluation_runs` row. It **never** trains, tunes, writes back
to predictions/truth, or fabricates truth.

### 7.1 Eligibility & honesty

- Eligible units default to `EvaluationItem.STATUS_EVALUATED`;
  `STATUS_NOT_EVALUATED` (UNREVIEWED) is always excluded;
  `DISPUTED` is included only when
  `config.includeDisputed = true` (`Eligibility.select`).
- Each metric is one of four states and is **never** silently
  collapsed:
  - `DEFINED(value, numerator, denominator, CI?)`
  - `INSUFFICIENT_SAMPLE_SIZE(numerator, denominator)`
  - `NOT_APPLICABLE`
  - `UNDEFINED`
- UNKNOWN/AMBIGUOUS/MIXED/PARTIAL/UNREVIEWED items are **not**
  coerced to false and **not** counted as evaluated; they are
  surfaced per-section (`unknown`, `excludedAmbiguous`) and in
  `sampleSummary` / `verdictDistribution`.
- Verdict semantics: `definitiveUnits` are units with a
  definitive CORRECT/INCORRECT verdict. Category, multilabel,
  and calibration are computed only over such units; the rest
  are reported, never dropped.
- `runId` and `createdAt` may be supplied for deterministic
  reproducibility; a `METHOD_VERSION` and `SCHEMA_VERSION`
  (`EvaluatorConfig`) record the exact algorithm + schema used.

### 7.2 Per-section definitions

- **Category (`CategoryMetrics`).** One-vs-rest per class
  (TP/FP/FN/TN, precision/recall/F1, support) + a full
  confusion matrix + macro / micro / weighted aggregates.
  Micro accuracy = correct / definitive units.
- **MultiLabel (`MultiLabelMetrics`).** Over the item's label
  set: exact match (all-or-nothing), hamming loss, micro and
  macro precision/recall/F1.
- **Platform / content-type / duration-bucket classification.**
  Exact-match agreement rate (Wilson CI) over definitive units.
- **Duration (`DurationMetrics`).** MAE, median absolute error,
  RMSE, signed-error bias, within-tolerance rates (1/3/5/10s),
  and absolute-error percentiles.
- **Skip (`SkipMetrics`).** Tri-state truth; UNKNOWN is never
  coerced to false; binary precision/recall/specificity over
  decided units only.
- **Interaction (`InteractionMetrics`).** One binary metric per
  signal in `GroundTruth.INTERACTION_SIGNAL_KEYS`, likewise
  tri-state-honest.
- **Topic / Tone (`TopicToneMetrics`).** Exact-string agreement
  rate over decided truth; UNKNOWN count surfaced. Documented
  limitation: free-text semantic similarity is not evaluated.
- **Confidence / calibration (`CalibrationMetrics`).** Fixed
  `bucketWidth` bins; per-bucket empirical accuracy + mean
  confidence + Wilson CI, and ECE = Σ_bucket weight ×
  |accuracy − meanConfidence|. Only over definitive
  primary-category verdicts.
- **Eligibility / exclusion (`Eligibility`).** Counts eligible
  vs excluded with a reason per excluded unit (`UNREVIEWED`,
  `DISPUTED_EXCLUDED`, `MISSING_PREDICTION`, `MISSING_TRUTH`,
  `MISSING_RESULT`).
- **Dataset balance (`DatasetBalanceReport`).** Counts, class
  entropy (normalized), and imbalance ratio.
- **Errors (`ErrorRecord`).** Per-incorrect-item detail for
  audit.
- **Sample summary / verdict distribution.** Overview counts of
  definitive, partial, unknown, unreviewed, corrected.

### 7.3 Reproducibility

Freezing `configJson` + `reportJson` per run and recording
`methodVersion` + `schemaVersion` + `runId`, together with
deterministic aggregation, lets any run be recomputed bit-for-
bit from the same inputs. See `ReportJson.kt` (lossless) and
`ConfigJson.kt`.

## 8. Evaluation dataset construction (8A-4)

This section describes how a **curated, reproducible
evaluation dataset** is built from the raw research data
stored in the tables above.

### 8.1 Raw research data vs. evaluation dataset

- **RAW RESEARCH DATA**: everything in `evaluation_items`,
  `ai_predictions`, `ground_truths`, `evaluation_results`.
  May contain incomplete annotations, unknown values,
  disputed annotations, old model predictions, missing
  evidence, and malformed records.
- **EVALUATION DATASET**: a curated snapshot selected
  according to explicit rules (see `DatasetConstructionPolicy`).

Raw data is **never deleted** simply because it is not
suitable for a particular evaluation. Items that fail
inclusion are marked with an exclusion reason; the underlying
records are preserved intact.

### 8.2 Dataset versioning

Every evaluation dataset has a unique version label (e.g.
`evaluation-v1`, `evaluation-v2`). Versions are **never
overwritten**. Creating a materially different dataset
produces a new version. Each manifest records:

- dataset version
- creation timestamp
- source data range (session IDs, project IDs)
- model version represented
- number of candidate items
- number of included / excluded items
- exclusion reasons
- category / platform / content-type / duration / skip /
  interaction / confidence distributions
- model version distribution

### 8.3 Dataset provenance

Every evaluation item in the dataset remains traceable to its
source: `evaluationItemId → feedItemId → sessionId → projectId
→ AI prediction reference → ground-truth reference → model
version → annotator identity`. References are preferred over
copying screenshots; evidence paths are kept local.

### 8.4 Capability-specific eligibility

Different evaluation tasks require different data. The
`CapabilityEligibility` object determines per-capability
whether an item is usable:

- **CATEGORY**: requires valid category ground truth (not
  `AMBIGUITY_UNKNOWN`)
- **PLATFORM**: requires non-blank platform ground truth
- **CONTENT_TYPE**: requires valid content type ground truth
- **DURATION**: requires non-negative duration ground truth
- **SKIP**: requires known skipped (true/false, not null)
- **LIKE / COMMENT / SHARE / SAVE / FOLLOW / PAUSE / PLAYING**:
  requires the corresponding tri-state field to be decided
- **TOPIC**: requires non-blank topic ground truth
- **TONE**: requires non-blank tone ground truth
- **MULTI_LABEL**: requires valid category + non-UNKNOWN ambiguity
- **CALIBRATION**: requires valid category + confidence value

An item with unknown `liked` can still be used for CATEGORY
evaluation; it simply cannot be used for LIKE evaluation. No
data is lost unnecessarily.

### 8.5 UNKNOWN values

UNKNOWN values are preserved as UNKNOWN. They are **never**
converted to TRUE or FALSE. Dataset reports show how many
items are unknown for each field (annotation completeness).

### 8.6 Disputed annotations

DISPUTED items are **classified explicitly**:

- **INCLUDED** (when `includeDisputed = true`)
- **EXCLUDED_DISPUTED** (when `includeDisputed = false`, the
  default)

Disputed items are never silently included as definitive
truth, nor silently deleted.

### 8.7 Ambiguous content

Ambiguous content (half comedy/half motivation, meme with
political commentary, ad embedded in entertainment, etc.) is
**scientifically valuable** and is retained. It is not removed
by default. Construction policy can exclude it explicitly, and
the manifest reports its count.

### 8.8 Mixed content

Mixed content (primary + secondary categories from the
`CategoryCatalog`) is preserved as-is. Primary `SPORTS` +
secondary `COMEDY` remains distinguishable from primary
`SPORTS` only. No flattening occurs.

### 8.9 Distribution reporting

The `DatasetReporter` generates:

- **Category distribution** across all catalog categories
- **Platform distribution** across all detectable platforms
- **Content-type distribution** (SHORT_VIDEO/LONG_VIDEO/OTHER/
  UNKNOWN)
- **Duration distribution** (0-5s, 5-15s, 15-30s, 30-60s,
  60-120s, 120s+)
- **Skip distribution** (watched/skipped/unknown)
- **Interaction distribution** (per signal: true/false/unknown)
- **Confidence distribution** (10% buckets)
- **Model version distribution**

### 8.10 Quality flags

Quality flags (warnings, never reasons to delete data):

- `INSUFFICIENT_SAMPLE_SIZE`
- `LOW_CATEGORY_SUPPORT`
- `LOW_PLATFORM_SUPPORT`
- `HIGH_UNKNOWN_RATE`
- `CATEGORY_IMBALANCE`
- `PLATFORM_IMBALANCE`
- `DURATION_IMBALANCE`
- `DUPLICATE_RISK`
- `HIGH_AMBIGUITY_RATE`
- `LOW_CATEGORY_COVERAGE`
- `LOW_PLATFORM_COVERAGE`

**No single composite "dataset quality score" is created.** The
quality is multidimensional, and the flags report separate
dimensions.

### 8.11 Dataset coverage

- **Category coverage**: how many of the catalog's categories
  are represented, and how many have meaningful support
  (≥ `minimumCategorySupport`).
- **Platform coverage**: how many supported platforms are
  observed, and how many have meaningful support. Platforms
  the system cannot detect are NOT penalized.

### 8.12 Annotation completeness

Completeness is reported separately per field (category,
platform, content type, duration, skip, topic, tone, and each
interaction signal). Example: "Category known: 94%, Platform
known: 91%, Like known: 62%".

### 8.13 Duplicate detection

- **Exact duplicates** (same `feedItemId`) are detected at
  cohort level and reported as `DUPLICATE_RISK`.
- **Frame-level duplicate detection** (perceptual hash based
  deduplication) is **documented as not yet available** in
  this milestone. The existing `frameFingerprint` field on
  FeedItem/CapturedFrame is preserved for future use.

### 8.14 Deterministic construction

Dataset construction is fully deterministic: the same source
data + config always produces the same membership, ordering,
and manifest structure. Ordering is by session start time, then
item enqueue time, then item ID. No random sampling is used.

### 8.15 No cherry-picking

The dataset builder never selects only high-confidence,
easy, or popular examples. Difficult, ambiguous, and
mixed-content examples are essential to honest evaluation.

### 8.16 No AI-based curation

Dataset construction uses **human-defined rules only**. No AI
model decides which examples to include.

### 8.17 Dataset validator

`DatasetValidator` checks:

- missing source references
- missing AI predictions
- missing ground truth
- invalid taxonomy values
- impossible durations (negative)
- invalid timestamps
- malformed confidence values (<0 or >1)
- duplicate dataset IDs
- unsupported category / platform / content-type values
- contradictory fields

Invalid values are flagged, never silently repaired.

### 8.18 Exclusion audit

Every excluded item carries a reason:

- `EXCLUDED_STATUS:<status>` — item status is not a candidate
- `EXCLUDED_DISPUTED` — status is DISPUTED and policy excludes
- `EXCLUDED_MISSING_PREDICTION`
- `EXCLUDED_MISSING_TRUTH`
- `EXCLUDED_AMBIGUOUS`
- `EXCLUDED_MIXED`
- `EXCLUDED_DUPLICATE_CANDIDATE`

### 8.19 Dataset manifest

A machine-readable JSON manifest records the full dataset
definition, statistics, provenance, coverage, and quality
flags. It round-trips losslessly through JSON for inspection
and export.

### 8.20 Privacy

All dataset data remains local. The manifest uses opaque
internal IDs rather than filesystem paths where possible. No
personal credentials, tokens, or unrelated device data are
exported. Screenshots stay local and are never uploaded.

### 8.21 Known limitations

- **Frame-level duplicate detection** relies on listed
  limitations; perceptual-hash dedup is a future capability.
- **Single-annotator assumption**: inter-annotator agreement
  (Cohen's kappa, Fleiss' kappa, Krippendorff's alpha) is not
  computed until multiple annotators meaningfully label the
  same items.
- **Temporal splits** (TRAIN/DEV/TEST) are architecture-
  prepared (timestamps + ordering preserved) but not yet
  implemented.
- **User/session independence**: current single-researcher
  deployment cannot separate multiple users' data streams.
- **No synthetic data**: the dataset reflects only real
  captured research data.

### 8.22 Database changes (8A-4)

**No Room schema changes** were introduced in 8A-4. The
dataset construction, validation, reporting, and manifest
logic operates over the existing `evaluation_items`,
`ai_predictions`, `ground_truths`, and `evaluation_results`
tables. Database version remains **26**.

---

## 9. Error Analysis, Failure Taxonomy & Diagnostics (8A-5)

Milestone 8A-5 explains **when and why** the FeedSense AI is
wrong, without changing the production model, without retraining,
and without tuning any headline-metric threshold. It is a
research-grade diagnostic layer on top of the already-frozen
8A-1..8A-4 evaluation records.

### 9.1 Scope and non-goals

- **GOAL**: classify each error, attribute it to a controlled
  root cause, prioritize it, and detect conservative patterns.
- **NON-GOAL**: modifying the classifier, detectors, confidence,
  segmentation, capture, or model version.
- **NON-GOAL**: training, tuning thresholds for headline
  accuracy, cherry-picking, synthetic data, or fabricating
  findings.

### 9.2 Data model and persistence

Following the established 8A-3/8A-4 pattern, 8A-5 is **pure
logic with no new Room table**. The output is a frozen,
serializable `ErrorAnalysisReport` (JSON) that can be embedded
alongside the 8A-3 `EvaluationReport` in `evaluation_runs`.
The database version remains **26**; no migration is required.

### 9.3 Controlled taxonomy

- **16 capabilities**: `CATEGORY`, `PLATFORM`, `CONTENT_TYPE`,
  `DURATION`, `SKIP`, `LIKE`, `COMMENT`, `SHARE`, `SAVE`,
  `FOLLOW`, `PAUSE`, `PLAY`, `TOPIC`, `TONE`, `SEGMENTATION`,
  `CALIBRATION` (`ErrorTypes.ALL_CAPABILITIES`).
- **19 error types**: `WRONG_CLASS`, `FALSE_POSITIVE`,
  `FALSE_NEGATIVE`, `MISSED_DETECTION`, `OVERCONFIDENT_ERROR`,
  `UNDERCONFIDENT_CORRECT`, `AMBIGUOUS_CONTENT`,
  `MULTI_LABEL_MISMATCH`, `TEMPORAL_ERROR`, `SEGMENTATION_ERROR`,
  `EVIDENCE_ERROR`, `PLATFORM_ERROR`, `CONTENT_TYPE_ERROR`,
  `DURATION_ERROR`, `INTERACTION_ERROR`, `TOPIC_ERROR`,
  `TONE_ERROR`, `SKIP_ERROR`, `UNCOMPARABLE`
  (`ErrorTypes.ALL_ERROR_TYPES`).

### 9.4 Root-cause separation (OBSERVED / HYPOTHESIS / CONFIRMED)

A finding carries a controlled root-cause label and one of
three evidence levels:

- **OBSERVED** — deterministically derived from stored
  prediction/truth/result; written by `ErrorAnalyzer`.
- **HYPOTHESIS** — an aggregate pattern inferred across many
  OBSERVED records (correlation, not cause); written by the
  pattern report.
- **CONFIRMED** — only ever set by a human analyst; 8A-5
  **never** writes CONFIRMED on its own.

### 9.5 Automatic detection (`ErrorAnalyzer`)

For each evaluation unit, the analyzer reads the
prediction/truth/result triplet and emits the applicable error
types. Honesty rules:

- UNKNOWN ground truth is **never** treated as an error.
- UNCOMPARABLE units are reported as such, not as model failures.
- `EVIDENCE_ERROR` is only a conservative OBSERVED proxy (missing
  recorded confidence), never a ground-truth claim.
- Error counts are derived **from the same `EvaluationRecord`
  fields** the 8A-3 engine uses, so 8A-5 never conflicts with
  8A-3 headline metrics.

### 9.6 Severity and research impact

`ErrorSeverity` assigns LOW / MEDIUM / HIGH / CRITICAL
deterministically, with a one-step bump for overconfident
errors and for errors on the core content-detection capability.
`isResearchImpactful` conservatively flags only the most serious
errors on core capabilities, and is always false when no real
data is available.

### 9.7 Confusion matrices, confidence-vs-correctness, patterns

- `ErrorConfusionMatrix` builds per-capability predicted-vs-truth
  matrices (named to avoid collision with 8A-3's `ConfusionMatrix`).
- `ConfidenceAnalysis` buckets predictions by confidence and
  reports correctness per bucket, surfacing over/under-confidence
  diagnostics.
- `ErrorPatternReport` emits HYPOTHESIS patterns only when at
  least `MIN_PATTERN_SAMPLE` (5) affected units support them.

### 9.8 Anti-fabrication guard

With zero real data the report emits the explicit finding
`INSUFFICIENT_REAL_DATA_FOR_STATISTICAL_CONCLUSIONS`, an empty
set of patterns, and a CRITICAL `NO_REAL_DATA` quality flag. On
small cohorts it emits `SMALL_COHORT` / `FEW_ERRORS` warnings
rather than inventing conclusions.

### 9.9 Export

`ErrorAnalysisReport.toJson()` is a lossless, deterministic JSON
serialization covering every section (roll-ups, error queue,
confusion matrices, confidence analysis, patterns, severity
distribution, quality flags).

### 9.10 Privacy

All analysis is local and operates only on already-stored data.
No PII is added, transmitted, or aggregated anywhere in the
error-analysis layer.

### 9.11 Database changes (8A-5)

**No Room schema changes.** The error-analysis layer is pure
logic over existing tables and adds no new entities,
columns, indexes, or migrations. Database version remains
**26**.

---

## 10. Root-Cause Validation & Failure Attribution (8A-6)

8A-6 answers *"was the suspected reason behind an error actually
supported by evidence?"* It validates the candidate causes the
analyst suspects for each detected error, without changing the
production AI and without training. It builds on 8A-5 (the error
taxonomy and detected-error types) and reuses the 8A-3
`EvaluationUnit`.

### 10.1 Design constraints

- **No production AI change**: the classifier, confidence,
  detectors, segmentation, capture, representative-frame
  selection, `modelVersion` and thresholds are all untouched.
- **No training**: candidate causes are marked as potential
  training candidates but nothing is trained in this milestone.
- **No fabrication**: with no real data the report emits
  `INSUFFICIENT_REAL_DATA_FOR_ROOT_CAUSE_CONCLUSIONS`-style
  markers, never invented statistics.
- **Local only**: all attribution is on-device, no cloud AI.
- **No schema change**: DB version stays **26**; assessments are
  serialized JSON (pure logic), like 8A-5.

### 10.2 Evidence levels (OBSERVED / HYPOTHESIS / CONFIRMED)

8A-6 carries the same honesty discipline as 8A-5:

- **OBSERVED** — deterministic facts derived from stored data
  (e.g. frames exist, OCR present, annotators disagree).
- **HYPOTHESIS** — a possible cause correlated with the symptoms;
  the automatic analyzer marks most candidates `POSSIBLE`.
- **CONFIRMED** — only a human sets this during review; the
  machine **never** auto-confirms a root cause.

### 10.3 Candidate root causes

Frozen controlled vocabulary in `RootCauseTypes.ALL_CANDIDATE_CAUSES`:
`OCR_FAILURE`, `VISUAL_AMBIGUITY`, `SEMANTIC_AMBIGUITY`,
`CATEGORY_BOUNDARY`, `PLATFORM_TEXT_MISSING`, `PLATFORM_UI_CONFUSION`,
`RAPID_CONTENT_CHANGE`, `SHORT_INTERACTION`,
`REPRESENTATIVE_FRAME_FAILURE`, `SEGMENTATION_FAILURE`,
`INTERACTION_UI_AMBIGUITY`, `LOW_INFORMATION_FRAME`,
`OVERLAY_OR_MODAL`, `MULTI_CONTENT_FRAME`, `TAXONOMY_LIMITATION`,
`UNKNOWN_DATA`, `SYSTEM_PIPELINE_FAILURE`, plus
`ANNOTATION_DISAGREEMENT`, `MISSING_EVIDENCE`,
`CLASSIFIER_CALIBRATION`, `DATA_VS_PREDICTION_MISMATCH`,
`TEMPORAL_TIMING_ERROR` and `OTHER`.

### 10.4 Attribution statuses

`UNASSESSED`, `POSSIBLE`, `SUPPORTED`, `CONFIRMED`, `REJECTED`,
`INCONCLUSIVE`, `NOT_APPLICABLE`. Automatic generation may
produce `POSSIBLE` (hypothesis) or `SUPPORTED` (observed, e.g.
inter-annotator disagreement) but **never** `CONFIRMED`.

### 10.5 Evidence strength vs AI confidence

`evidenceStrength` (`NONE`/`WEAK`/`MODERATE`/`STRONG`) is a
**separate dimension** from AI prediction confidence. It is
derived from the number of independent evidence signals
(frames present, OCR present, multiple annotators). This
prevents "high model confidence" from being mistaken for
"well-supported root cause".

### 10.6 Attribution classes (data vs model, etc.)

`MODEL_RELATED`, `DATA_RELATED`, `PIPELINE_RELATED`,
`TAXONOMY_RELATED`, `ANNOTATION_RELATED`, `UNKNOWN`. The class is
decided by the deterministic `AttributionDecisionTree`, which
stops at the first decisive OBSERVED condition and otherwise
falls back to `UNKNOWN` (deferring to human review).

### 10.7 Primary vs contributing cause

Each assessment has at most one `PRIMARY_CAUSE` (chosen from the
decisive decision-tree branch) and zero or more
`CONTRIBUTING_CAUSE`s (e.g. representative-frame failure,
rapid content change, short interaction, category boundary).

### 10.8 Evidence references and bounded windows

`Evidence.EvidenceRef` references **existing objects**
(`frameId`, `predictionId`, `truthId`) and never embeds raw
screenshots or OCR payloads. Temporal analysis uses the bounded
`Evidence.temporalWindow` (before/current/after within a capped
count), never the whole session.

### 10.9 Representative-frame failure

`Evidence.frameContribution` compares the representative frame's
category to the majority frame category. A disagreement is
surfaced as `REPRESENTATIVE_FRAME_FAILURE` marked `POSSIBLE`,
never asserted.

### 10.10 Inter-annotator agreement

`AnnotationAgreement` computes percent agreement and Cohen's
kappa over the primary label. With fewer than 2 raters it returns
`INSUFFICIENT_DATA_FOR_INTER_ANNOTATOR_STATISTICS` and both
metrics are null. A genuine annotator disagreement can be marked
`SUPPORTED` but never `CONFIRMED` by the machine.

### 10.11 Segmentation / duration / short-interaction diagnosis

- **Segmentation**: `SEGMENTATION_FAILURE` when a result is not
  comparable or has an explicit segmentation error.
- **Duration**: a `DURATION_ERROR`/`TEMPORAL_ERROR` is attributed
  from `EvaluationRecord.durationErrorSeconds` etc., with the
  bounded window distinguishing capture timing from segmentation.
- **Short interaction**: content with `durationSeconds < 10`
  surfaces `SHORT_INTERACTION`, classified as pipeline-related
  when evidence is insufficient, else model-related.

### 10.12 Human review and audit

`RootCauseAssessment` carries `humanReviewStatus`
(`UNREVIEWED`/`IN_PROGRESS`/`COMPLETE`), `reviewerId`,
`reviewerNotes`, and an immutable `auditTrail`
(`withAudit` appends, never mutates).

### 10.13 Determinism and versioning

The analyzer is deterministic for given inputs:
`RootCauseAnalyzer` sorts candidate causes stably (PRIMARY first,
then cause id) and `RootCauseReport` orders by priority then id.
Every assessment records `diagnosticVersion`
(`diagnostic-v1`) so conclusions are never silently recomputed
with new semantics.

### 10.14 No self-learning

8A-6 never updates its own weights from data; `assessment` is a
pure read-only conclusion. Training-candidate marking
(`RootCauseReport.trainingCandidates`) only flags ids for later,
out-of-band review; nothing is trained.

### 10.15 Hard-case queue and priority

`RootCauseReport.priorityScore` (higher = more urgent) drives the
`reviewQueue`: `INCONCLUSIVE`/`UNKNOWN` ranks above
`POSSIBLE`/`MODEL`. CONFIRMED and REJECTED assessments are
excluded from the queue.

### 10.16 Export (references only)

`RootCauseReport.toCsv()` and `toJson()` serialize per-cause rows
using only object ids and structured fields — never raw screen
content. Privacy is preserved (no cloud, no PII).

### 10.17 Quality-control role

8A-6 is not a metric that awards the model; it is a
quality-control layer that decides **whether a suspicion is
defensible**, and if not, marks it `INCONCLUSIVE`/`UNKNOWN` for
human adjudication. With no real data it reports
`INSUFFICIENT_REAL_DATA_FOR_ROOT_CAUSE_CONCLUSIONS` and returns an
empty review queue and zero inferred classes.

### 10.18 Database changes (8A-6)

**No Room schema changes.** The root-cause layer is pure logic
over existing tables; assessments persist as serialized JSON
alongside 8A-3/8A-5 reports. Database version remains **26**.

## 11. Local Evidence-Fusion Decision Architecture (8B-1)

8B-1 is an **architectural milestone**, not a trained model. It
establishes a local, deterministic, evidence-fusion decision layer
(`FUSION_V1`, model version `local-fusion-v1`) that replaces the
flat "screenshot → heuristics → category guess" path with layered
evidence extraction → normalization → candidate generation →
fusion → uncertainty → decision → diagnostic trace. It introduces
NO cloud AI, NO training, NO telemetry, and does NOT change or
replace the production `local-v6.0` pipeline. Both predictions
remain comparable (section 4/5/55).

### 11.1 Non-goals (explicit)

- No training, no NN download, no cloud/remote inference, no
  telemetry, no auto-upload, no cloud fallback.
- No Room schema changes (DB version stays **26**).
- No redesign of the UI/dashboard, no self-learning, no
  auto-modification from corrections, and no silent replacement of
  production: the fusion model is introduced as a new version
  behind the legacy one, and every prediction carries its model
  version.

### 11.2 Scope and intent

The decision architecture is a **foundation** for future
multimodal work: a real vision model, if later adopted, plugs into
the existing `VisualEvidenceProvider` seam without reworking the
fusion/uncertainty/decision layers.

### 11.3 Data and performance

- Pure Kotlin in-memory structures; no re-OCR (consumes whatever
  the pipeline already stored — if OCR text is absent the system
  reports `OCR_UNAVAILABLE`, never `CONTENT_IS_UNKNOWN`).
- Deterministic: identical input + modelVersion + configuration ⇒
  byte-identical output (no randomness, no time dependence).
- Compact diagnostic trace (framesConsidered, per-provider
  strength, candidates with scores, temporalAgreement, final
  category, confidenceBand, uncertainty); no full OCR transcripts
  or screenshots in frequently queried rows.

### 11.4 Evaluation plugin

`FusionEvaluationBridge` materializes a fusion prediction as an
immutable `AiPredictionRecord` with `modelVersion = local-fusion-v1`
and `source = AI`, so the 8A evaluation engine can compare LEGACY
vs FUSION on identical frozen evaluation items. With no populated
real corpus, accuracy conclusions are reported honestly as
`INSUFFICIENT_REAL_DATA_FOR_ACCURACY_CONCLUSIONS`; the framework and
its unit tests demonstrate the comparison path.
