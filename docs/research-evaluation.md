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

---

## 12. Production-Safe Evidence-Aware Evaluation Integration (8B-8)

8B-8 creates a **production-safe comparison/integration layer** that
allows the new evidence-aware system to operate on real FeedSense
observations without modifying the baseline observation-generation
path. The baseline AI result remains authoritative and is never
silently replaced.

### 12.1 Purpose and architecture

The adapter takes an existing FeedItem and its available 8B-6
evidence snapshot, runs the 8B-7 evidence-aware decision, and
returns a structured, immutable comparison between:

1. the existing baseline AI result, and
2. the new evidence-aware result.

```
FeedItem (production, immutable)
    │
    ├──────────────────► baseline (read verbatim)
    │
    └──► EvidenceAwareProductionAdapter
              │
              ▼
      ReadOnlyEvidenceAwareAdapter (8B-7)
              │
              ▼
      ItemDecisionEngine (8B-7)
              │
              ▼
  EvidenceAwareComparisonResult (8B-8)
              │
              ▼
     DecisionEvaluationBridge (8B-7)
              │
              ▼
          8A evaluation
```

### 12.2 Design constraints

- **Read-only**: the FeedItem is never modified.
- **No Room writes**: no entity is persisted.
- **Baseline immutability**: no prediction, observation, session
  state, or historical record is changed.
- **Independent from SessionRepository**: `buildFeedItem()` is not
  touched.
- **Category schema frozen**: no categories renamed, merged,
  deleted, or added.
- **Deterministic**: same inputs → same result.
- **Bounded batch**: explicit limits, no unbounded database scan.
- **Privacy-preserving**: no raw screenshots, OCR text, captions,
  messages, personal names, notification contents, private URLs,
  or personal identifiers are exported.
- **Fails safely**: adapter failures produce a safe
  UNKNOWN/UNRESOLVED state without modifying production.

### 12.3 Comparison states (`ComparisonState`)

A controlled, deterministic enum classifying the relationship
between the baseline and evidence-aware predictions:

- **AGREEMENT**: both systems named the same primary category.
- **CATEGORY_DISAGREEMENT**: both decided on different categories.
- **BASELINE_UNKNOWN**: baseline had no usable category.
- **EVIDENCE_AWARE_UNKNOWN**: evidence-aware abstained (UNKNOWN).
- **BOTH_UNKNOWN**: neither system had a usable category.
- **INSUFFICIENT_EVIDENCE**: evidence explicitly insufficient.
- **REVIEW_REQUIRED**: evidence-aware identifies human review need.

### 12.4 Difference reasons (`DifferenceReason`)

A controlled taxonomy of structured reasons for disagreement:

- `REPRESENTATIVE_FRAME_CONFLICT`
- `TEMPORAL_CONTEXT_CHANGED_DECISION`
- `OCR_CHANGED_DECISION`
- `PLATFORM_EVIDENCE_CHANGED_DECISION`
- `INTERACTION_EVIDENCE_CHANGED_DECISION`
- `SEGMENTATION_DIFFERENCE`
- `LOW_INFORMATION_EVIDENCE`
- `SHORT_INTERACTION`
- `CATEGORY_BOUNDARY`
- `INSUFFICIENT_EVIDENCE`
- `MODEL_DISAGREEMENT`
- `UNKNOWN` (preferred over fabrication)

Only reasons supported by actual evidence are included. `UNKNOWN`
is always preferred over inventing an explanation.

### 12.5 Evidence strength (`EvidenceStrength`)

A controlled vocabulary for evidence quality, explicitly separate
from AI confidence or prediction correctness:

- **NONE**: no usable evidence.
- **WEAK**: limited evidence, substantial risk.
- **MODERATE**: adequate evidence with gaps.
- **STRONG**: diverse, well-covered evidence.

**Critical distinction**: evidence strength ≠ prediction correctness.
STRONG evidence does NOT mean the prediction is correct. Only
ground truth (8A layer) establishes correctness.

### 12.6 Privacy guarantees

The comparison model must not contain:

- Raw screenshots or screenshot paths (when policy prohibits)
- Raw OCR text payloads
- Private captions or message contents
- Personal names
- Notification contents
- Arbitrary screen text
- Private URLs
- Personal identifiers

Only structured IDs, category labels, and controlled metadata are
retained. IDs may be retained for traceability; the comparison
points to evidence, not duplicates it.

### 12.7 Versioning

Every comparison carries explicit versions:

- `adapterVersion`: the production adapter version
  (`evidence-aware-production-adapter-v1`)
- `baselineModelVersion`: the original classifier version
- `evidenceAwareModelVersion`: the 8B-6 fusion version
- `decisionVersion`: the 8B-7 decision layer version
- `datasetVersion`: the evaluation dataset version (when available)
- `diagnosticVersion`: root-cause diagnostic version (when available)

### 12.8 Batch processing

The adapter supports bounded batch comparison:

- Explicit maximum (`MAX_BATCH_SIZE = 100`).
- Deterministic ordering (input order preserved).
- Limit is coerced to `[1, MAX_BATCH_SIZE]`.
- No unbounded database scan.
- No duplicate processing.
- No mutation of baseline.

### 12.9 Failure handling

Possible failures and their handling:

- FeedItem not found → safe UNKNOWN/UNRESOLVED state
- Evidence unavailable → INSUFFICIENT_EVIDENCE
- Invalid snapshot → safe UNKNOWN state
- Missing representative frame → gracefully handled
- Incompatible version → logged, safe failure
- Malformed prediction → safe UNKNOWN state
- 8B decision failure → safe UNKNOWN state

Adapter failures never crash the session pipeline. A structured
failure result is returned with `comparisonState` reflecting the
safe default.

### 12.10 8A bridge compatibility

8B-8 uses the existing `DecisionEvaluationBridge` from 8B-7 rather
than creating a second competing implementation:

```
EvidenceAwareComparisonResult
    → DecisionEvaluationBridge.toAiPredictionRecord()
        → 8A evaluation layer
```

The bridge converts an `ItemPredictionResult` into an immutable
`AiPredictionRecord` with `source = AI_EVIDENCE_AWARE` so the 8A
engine can compare baseline vs evidence-aware on identical items.

### 12.11 Human review support

When the baseline and evidence-aware result disagree, the comparison
exposes structured information for the 8A annotation/evaluation
workflow:

- `REVIEW_REQUIRED` state
- `humanReviewRecommended` flag
- Detailed difference reasons
- Evidence strength assessment

The adapter marks `REVIEW_REQUIRED` but must NOT automatically mark
the result as human-confirmed. Human adjudication remains
authoritative via the existing 8A annotation workflow.

### 12.12 Confidence ≠ accuracy

The comparison layer clearly distinguishes:

- AI confidence (classifier or evidence support score)
- Evidence strength (quality of available evidence)
- Prediction agreement (both systems said the same thing)
- Ground-truth correctness (only 8A ground truth establishes this)

`baselineConfidence = 0.92` does NOT mean `baselineAccuracy = 92%`.
`evidenceStrength = STRONG` does NOT mean `predictionCorrect = TRUE`.
Tests explicitly prevent accidental conflation.

### 12.13 Determinism

The same input produces the same logical result:

- Same FeedItem + same evidence + same versions = same comparison
- Deterministic ordering in batch operations
- Timestamps do not invalidate comparison semantics
- No nondeterministic fields in canonical comparison

### 12.14 Database changes

**No Room schema changes.** The 8B-8 adapter is a read-only
derivation layer over existing data. Database version remains **26**.

### 12.15 Future extensibility

The adapter architecture allows future experiments:

- Baseline AI vs Evidence-aware AI comparisons
- Category accuracy, macro F1, per-category precision/recall
- Calibration analysis
- Disagreement rate measurement
- Review rate tracking
- Uncertainty rate analysis
- Root-cause distribution studies
- Representative-frame failure rate
- Temporal-context benefit measurement
- OCR/platform/interaction evidence contribution
- Latency and compute cost analysis

8B-8 itself does not invent these statistics; it provides the
infrastructure to measure them.

---

## 13. Controlled Real-Data Experiment & Paired Evaluation Harness (8B-9)

8B-9 builds a **controlled real-data experiment** layer on top of
the 8B-8 comparative and 8A evaluation infrastructure. It lets
FeedSense run bounded, auditable, baseline-vs-evidence-aware paired
comparisons over a real observation population while keeping the
authoritative baseline strictly unmodified and the experiment purely
**observational / evaluative** - never training, never mutating a
FeedItem, session, prediction, or evaluation record, never changing
any production pipeline.

### 13.1 Purpose and scope

The harness answers the research question "does the evidence-aware
path help or hurt relative to the authoritative baseline on real
content?" through a **paired design**: the SAME ground truth is
judged against BOTH the baseline and the evidence-aware prediction,
so the comparison is apples-to-apples. It does not invent accuracy:
it surfaces exactly how much real, eligible, non-leaked data exists,
and refuses to claim a conclusion when that is insufficient.

### 13.2 Real-data observability (no assumptions)

The harness is strict about what counts as "real":

- It consumes **real evaluation items**, **real immutable baseline
  prediction snapshots**, and **real human ground truth** from the
  8A layer.
- It reuses the 8B-7 evidence-aware decision path to derive the
  evidence-aware prediction for each real item.
- When real paired data is absent or below the frozen sample-size
  guard, the run reports `INSUFFICIENT_REAL_DATA` and explicitly
  refuses to assert a comparative conclusion (`INSUFFICIENT_REAL_DATA_FOR_COMPARATIVE_CONCLUSIONS`).

### 13.3 Baseline safety statement (see also 12.2)

Every experiment carries an explicit **baseline safety statement**
that is asserted by construction:

- baseline prediction unmodified
- FeedItems unmodified
- sessions unmodified
- predictions (baseline snapshots) unmodified
- evaluation records unmodified
- no production pipeline changes
- observational/evaluative only

The runner never writes to any table; every input consumed by the
experiment is read-only. Tests assert `baselineSafety.safe == true`.

### 13.4 Experiment definition and validation

`ExperimentDefinition` is immutable and fully describes a run:
experiment id/name/version, dataset mode, selection parameters,
a bounded maximum, an optional annotator scope, the reused 8B-8
comparison config, and version provenance (baseline / evidence-aware
/ decision). Construction validates:
- name non-blank and `maxItems >= 1`
- date range ordering (`startDate <= endDate`)
- mode-consistent selection (e.g. SESSION requires a sessionId)

### 13.5 Dataset selection modes

The population is chosen by one of five modes:
- `SESSION` - all evaluated items of one research session
- `ITEM_SET` - an explicit bounded set of item ids
- `DATE_RANGE` - items enqueued within a time window
- `EVALUATION_DATASET` - a frozen curated dataset cohort
- `ALL_EVALUATED` - every item with a comparable ground truth

### 13.6 Bounded execution

Every run is explicitly bounded by `maxItems`. Selection applies a
deterministic id-ordered cap and never performs an unbounded scan,
mirroring the 8B-8 `MAX_BATCH_SIZE` discipline. Items dropped by the
cap are counted (`excludedCapCount`), never silently discarded.

### 13.7 Same-sample guarantee

`DatasetSelection` guarantees SAME-SAMPLE semantics: each item is
used exactly once with a single authoritative ground truth, and is
judged once. Multiple records per item are reduced to the earliest
comparable (non-`UNKNOWN`) record, optionally scoped to one
annotator (pseudonymous `annotatorId`). This prevents double
counting and keeps the paired contingency exact.

### 13.8 Ground-truth eligibility

Each observation is classified `ELIGIBLE_FOR_ACCURACY` (comparable
definitive truth) or `EXCLUDED_FROM_ACCURACY` (e.g. `AMBIGUITY_UNKNOWN`).
Eligibility is identical to the 8B-8 comparable-truth rule, so the
accuracy denominator is consistent across layers. Excluded
observations are still carried and reported (abstention / dataset
quality) but never enter any accuracy denominator.

### 13.9 Five-way paired outcome

`EvidenceAwareOutcome` is the frozen, non-collapsing classification
of each eligible observation:
- `BOTH_CORRECT`
- `BASELINE_ONLY_CORRECT` (evidence-aware regression)
- `EVIDENCE_AWARE_ONLY_CORRECT` (evidence-aware improvement)
- `BOTH_WRONG`
- `INCOMPLETE` (at least one side abstained / unjudgeable)

`INCOMPLETE` is kept separate from accuracy so abstention is never
mistaken for accuracy. Mapping from the finer 8B-8 `ComparisonOutcome`
is deterministic.

### 13.10 Same truth, both systems (paired integrity)

Both sides of each paired observation are judged against the SAME
ground truth via the immutable 8B-8 `PairedPrediction`. The baseline
record is consumed verbatim; the evidence-aware record is
materialized by the read-only 8B-7 `DecisionEvaluationBridge`. Both
verdicts use the SAME 8A `EvaluationRecord.fromComponents` rules - no
double standard.

### 13.11 Confusion matrices

Class confusion (truth category x predicted primary category) is
computed for BOTH systems over the SAME eligible pairs by reusing
the 8B-8 `TransitionMatrices.classConfusion`, yielding a palpable
"who confused what" comparison.

### 13.12 Difference / coverage-transition matrix

The `TransitionMatrices.coverageTransition` (CORRECT / WRONG /
UNKNOWN per side) is reused as the experiment's difference matrix.
The leading diagonal is "no change"; off-diagonal cells expose
evidence-aware improvements and regressions explicitly.

### 13.13 Per-category statistics

Per-category precision / recall / F1 for both systems are computed
by reusing the 8B-8 `ComparativeMetrics.perCategory` over the same
eligible population, with the same explicit support guards
(`INSUFFICIENT` rather than a manufactured number).

### 13.14 McNemar-ready output

The experiment exposes a **McNemar-ready** paired contingency -
`baselineCorrect`, `evidenceAwareCorrect`, and the discordant counts
`baselineOnlyCorrect` / `evidenceAwareOnlyCorrect` - computed by
reusing the 8B-8 exact `PairedStats.mcnemar`. The exact binomial
test and its sample-size guards match the comparative layer exactly,
so no statistic is invented below the discordant-pair minimum.

### 13.15 Real-data status (`INSUFFICIENT_REAL_DATA`)

The run computes a strict real-data flag following the 8B-8
effect-size guard: when eligible paired observations are below the
frozen minimum, the status is `INSUFFICIENT_REAL_DATA` and the
comparative report emits `INSUFFICIENT_REAL_DATA_FOR_COMPARATIVE_CONCLUSIONS`
with an explicit note that the supplied numbers must NOT be read as
a systemic claim.

### 13.16 Dataset quality report

`DatasetQualityReport` describes population composition and
strength: eligible vs excluded counts, truth-ambiguity distribution,
platform / content-type / duration distributions, and distinct feed
items - counts and controlled metadata only.

### 13.17 Leakage protection

`LeakageGuard` checks the experiment population against a nominated
training/validation dataset cohort
(`ExperimentDefinition.trainingDatasetVersion`) and reports any
overlapping item ids as `LEAKAGE DETECTED`. When no training cohort
is nominated the check is explicitly reported as skipped (not
silently assumed clean).

### 13.18 Audit trail

`ExperimentSnapshot` records every run: the definition, the exact
selected population, version provenance, and an ordered list of
immutable audit events.

### 13.19 Idempotency

Each snapshot carries a deterministic **SHA-256 idempotency
signature** derived from the definition plus the exact selected
population. Re-running the identical experiment on the identical
population yields the identical signature, so duplicate or drifted
runs are detectable.

### 13.20 Privacy-safe export

`ExperimentExporter` emits JSON (a full count/metadata summary plus
the reused 8B-8 comparative body) and a per-observation CSV
(evaluation item id, feed item id, eligibility, outcome). Only
COUNTS, version metadata, structured outcome labels, and opaque
traceable ids are exported. Raw content is NEVER written: no
screenshots, OCR text, captions, messages, personal names,
notification contents, private URLs, or personal identifiers
(matching the 8B-8 privacy guarantees).

### 13.21 Reuse of existing infrastructure (no duplication)

The harness reuses, without duplication:
- 8B-8 `PairedPrediction`, `PairedPredictionBuilder`,
  `ComparativeEvaluator`, `ComparativeReport`,
  `TransitionMatrices` (confusion + transition/difference matrices),
  `ComparativeMetrics` (overall + per-category), `PairedStats`
  (exact McNemar + effect size), `StratifiedComparison`,
  `ErrorRootCauseComparison`
- 8B-7 `DecisionEvaluationBridge` and the evidence-aware decision
  path
- 8A `EvaluationRecord` comparison rules and ground-truth semantics

### 13.22 Read-only data sources

`ExperimentDataSource` is a read-only abstraction; a default
in-memory implementation supports tests and in-hand data, while
`RoomExperimentDataSource` reuses the EXISTING `EvaluationDao` for
items / prediction snapshots / ground truths. Evidence-aware
decisions are supplied per run (they are derived on demand, not
persisted).

### 13.23 Configuration guards reused

The 8B-8 `ComparativeConfig` sample-size guards
(minimumMcNemarDiscordantPairs, minimumForEffectSize,
minimumForConfidenceInterval, minimumStratumSupport) are reused
verbatim; no guard is relaxed to manufacture a result.

### 13.24 Determinism

Same definition + same population + same versions + same decisions =
same outcome, same confusion/difference matrices, same McNemar
contingency, and same idempotency signature. Selection ordering is
deterministic (id-ordered).

### 13.25 Failure semantics

Items gracefully excluded from pairing are surfaced with an explicit
reason (no truth / no baseline / no evidence-aware decision) rather
than assumed correct or silently dropped. A missing evidence-aware
decision excludes the item from the evidence-aware side and counts
it in the exclusion accounting - it is never assumed correct.

### 13.26 No accuracy claims without real ground truth

The harness never emits an accuracy number for an observation
without a comparable real ground truth. All accuracy denominators
derive from `ELIGIBLE_FOR_ACCURACY` observations only; every
proportion is guarded and carries an explicit state.

### 13.27 Database changes

**No Room schema changes.** The experiment reads existing data
through the existing `EvaluationDao` (no new queries, no new
tables). Database version remains **26**. `SessionRepository`,
`FeedItem`, and the production prediction path are not modified.

### 13.28 Walkthrough of a run

1. Build an immutable `ExperimentDefinition` (mode, bounds, scope,
   versions).
2. Read real items / baseline snapshots / ground truths through the
   read-only data source.
3. `DatasetSelection` applies the mode, the deterministic cap, and
   the same-sample rule; assemble `PairedPrediction`s.
4. `LeakageGuard` (optionally) checks against a training cohort.
5. `DatasetQualityReport` describes the population.
6. `ComparativeEvaluator` produces the reusable 8B-8 report
   (metrics, matrices, per-category, stratification, root cause,
   conclusion).
7. `ExperimentAnalyzer` tallies the five-way outcome and computes
   the McNemar-ready contingency + real-data status.
8. `ExperimentSnapshot` records the audit trail and idempotency
   signature; `ExperimentExporter` produces privacy-safe JSON/CSV.
9. The `ExperimentSummary` is returned, with the real-data status
   and baseline safety statement asserted.

The harness is a complement to the 8B-9 statistical-robustness layer
(which stresses these comparisons with bootstrap / sensitivity
analyses); the two share the same paired design and the same
guards.


## 14. Privacy-safe evidence sanitization review (8B-10)

Milestone 8B-10 adds the evidence-protection layer described in
`docs/privacy.md`. For evaluation this means:

- **What evidence reaches evaluation changes, not how it is
  judged.** The evidence-aware pipelines (8B-6/7/8/9) still run the
  same deterministic decision and paired-comparison logic; the
  inputs they see are sanitized frames plus redacted OCR text.
- **Baseline integrity holds.** `FeedItem`, `AiPredictionRecord`,
  `GroundTruth`, the category taxonomy, and Room schema v26 are
  unchanged. Baseline predictions are read verbatim.

  `8B-10 does not modify FeedSense baseline predictions. Privacy
  sanitization is an evidence-protection layer and does not alter
  FeedItem semantics or the baseline AI.`

- **Evaluation carries privacy metadata.** `ItemEvidenceSnapshot`
  now carries a defaulted `PrivacyEvidenceMetadata (NONE)`, and
  `EvidenceAwareProductionAdapter` surfaces `privacyPolicyVersion`,
  `privacyStatusLabel`, `privacyEvidenceLossLabels`, plus
  evidence-backed `PRIVACY_REDACTION_AFFECTED_DECISION` /
  `EVIDENCE_LOST_DUE_TO_SANITIZATION` reasons. Legacy snapshot
  construction (without the field) is source-compatible and yields
  `NONE`.
- **Honest validation language.** Sanitization behavior is
  validated by on-device JVM unit tests (deterministic patterns and
  a fake rasterizer) and the existing 8B-4 device rasterizer tests.
  No fabricated detection rates or percentages are reported. A
  measured detection rate may be added here only after a controlled
  on-device population measurement.
- **Privacy-safe measurement.** `PrivacyMetrics` records observed
  counts only; the 8B-9 experiment harness exports privacy-safe
  JSON. Raw OCR text and raw frame paths are never written to
  evaluation exports under the default `SANITIZED_METADATA_ONLY`
  policy.


## 15. Perceptual frame deduplication review (8B-11)

Milestone 8B-11 adds a standalone, research-grade visual-change
layer (`analysis/efficiency/`) described in
`docs/perceptual-deduplication.md`. For evaluation this means:

- **Evaluation logic is untouched.** The deduplication layer is
  not wired into `SessionRepository.buildFeedItem()`, the
  evidence pipelines (8B-6/7/8/9), the baseline AI, the category
  taxonomy, or Room schema v26. It is a forward future hook for a
  8B-12 scheduling layer via `FrameSimilarityResult.forwards`.
- **Baseline integrity holds.** `FeedItem`, `AiPredictionRecord`,
  `GroundTruth`, the frozen category schema, and the database
  schema are unchanged. Baseline predictions are read verbatim.

  `8B-11 does not modify FeedSense baseline predictions. Perceptual
  frame deduplication is an efficiency heuristic layer and does not
  alter FeedItem semantics or the baseline AI.`

- **Decision semantics are versioned and attributable.** Every
  result carries the algorithm (`dHash-v1`/`pHash-v1`), the
  distance function version (`hamming-v1`), the config version
  (`dedup-v1`), the timestamp, and the sequence index. Any future
  efficiency study can be attributed exactly.
- **Decision vocabulary is controlled.** `FrameDecision`
  (`DUPLICATE` / `SIMILAR` / `UNIQUE`) and `FrameEvaluationReason`
  (`FIRST_FRAME` / `COMPARED` / `TIME_WINDOW_GATED` /
  `FORCED_FORWARD` / `DISABLED_PASSTHROUGH`) form the only
  vocabulary a consumer may rely on. `downstreamOnly = forwards`.
- **Honest validation language.** Behaviour is validated on pure
  JVM synthetic frames: determinism, threshold boundary
  semantics, rolling-reference state machine, safety ceiling,
  time gating, O(1) memory over 10,000 frames, and a controlled
  engineering corpus (identical / resolution / brightness /
  tiny-indicator / minor-motion / ad interstitial / inversion /
  caption / different-reel) with an explicit ordering invariant.
  The corpus is labelled NOT real-world accuracy; known
  limitations (small-element suppression, luminance inversion)
  are recorded as documented risks, not hidden.
- **No fabricated efficiency claims.** `EfficiencyBenchmark`
  reports measured hash timings as methodology only; tests assert
  plumbing, not wall-clock numbers, to stay flake-free. Battery /
  CPU improvement is NOT claimed — a controlled on-device
  population measurement is required first.
- **Privacy-safe persistence.** The layer retains only lossy,
  non-reversible perceptual hashes plus counters/decisions; it
  never writes raw frames, OCR text, or frame paths. This is
  consistent with the 8B-10 privacy policy ordering (reference
  hashes are research-safe).


## 16. Adaptive frame sampling & inference scheduling review (8B-12)

Milestone 8B-12 adds a standalone, research-grade scheduling
layer (`analysis/scheduling/`) described in
`docs/scheduling.md`. For evaluation this means:

- **Evaluation logic is untouched.** The scheduling layer is not
  wired into `SessionRepository.buildFeedItem()`, the evidence
  pipelines (8B-6/7/8/9), the baseline AI, the category taxonomy,
  the 8B-11 deduplication layer, or Room schema v26. It consumes
  `FrameSimilarityResult` structurally and never hashes or inspects
  pixels itself.
- **Baseline integrity holds.** `FeedItem`, `AiPredictionRecord`,
  `GroundTruth`, the frozen category schema, and the database
  schema are unchanged. Baseline predictions are read verbatim.

  `8B-12 does not modify FeedSense baseline predictions. Adaptive
  frame sampling is an orchestration/efficiency layer only; it
  changes which frames are analyzed in a future experiment, never
  what a given analysis scores.`

- **Decision semantics are versioned and attributable.** Every
  `SamplingDecision` carries `algorithmVersion` and
  `configVersion` (`sampling-v1`), the candidate/analysis index,
  the pre-mutation elapsed time, the adaptive interval that
  applied, and the passed-through 8B-11 evidence (distance,
  decision, algorithm version). Any future scheduling study can be
  attributed exactly.
- **Decision vocabulary is controlled.** Actions are exactly
  `ANALYZE` / `FORCE_ANALYZE` / `SKIP`; reasons are exactly the ten
  documented values. There is no DEFER action — `SKIP` +
  `MIN_INTERVAL` carries deferral semantics.
- **The scheduler is purely reactive and memory-bounded.** State is
  O(window) (last-analysis timestamp, counters, a bounded 8B-11
  distance ring); `reset()` isolates sessions; all state access is
  synchronized. `SamplingStats.samplingRatio` is a diagnostic
  (analyzed/candidates), NOT accuracy and NOT a battery claim.
- **Honest validation language.** Behaviour is validated on pure
  deterministic sets with injected timestamps: interval boundary
  semantics (inclusive min/max), pressure/roll-window behaviour,
  transition/interaction precedence after the minimum gate,
  fallback on negative timestamps, reset/session isolation,
  determinism, bounded state, and property invariants §37.1-§37.5.
  Sequences (A A A A B B C C, A B C D E, scroll-burst) were
  hand-calibrated against the running implementation. The
  `SamplingBenchmark` corpus is synthetic and asserts problem
  *shape* (static < moving < rapid, ceiling coverage on static,
  no forcing under rapid scroll); it is explicitly NOT real-world
  accuracy.
- **No fabricated efficiency claims.** No battery/CPU saving is
  claimed; `samplingRatio` is reported as a diagnostic for the
  planned on-device experiments A-D and hypotheses H1-H4 (§13 of
  `docs/scheduling.md`), which remain future work pending wiring
  into the capture pipeline.
- **Privacy-safe.** The scheduling layer holds no pixels, no OCR
  text, no raw frame paths; its only retained evidence is lossy,
  non-reversible 8B-11 distances. This is consistent with the
  8B-10 privacy policy ordering (sampling is a privacy
  multiplier — fewer frames cross a model).
- **Standalone verification status.** Full suite 1334 unit tests
  green, 0 failures; `assembleDebug` builds clean. The same
  pre-existing `ItemDecisionTest.test28_manyInputsRemainDeterministic`
  time-straddle flake documented in §14.1/§15 remains (green in
  clean full runs); 8B-12 added no new flaky tests.

## 17. Model & runtime selection review (8B-15-1)

Research-phase review record. This milestone does NOT change any evaluation
data, dataset construction, or eligibility rules; it selects the on-device model
architecture and runtime for the **experimental** ML path added by 8B-14 and
records the evidence so the choice is attributable and reproducible.

### 17.1 Scope

- Decide, with graded evidence, which on-device architecture and runtime the
  experimental ML path standardizes on. Nothing in this record modifies the
  text-heuristic baseline (`local-v6.0`), `GroundTruth`, `AiPredictionRecord`,
  evaluation tables, or annotations.

### 17.2 Decision and rationale

- **Runtime: LiteRT (TensorFlow Lite); Model: MobileNetV4-Conv-S (INT8);
  fallback: EfficientNet-Lite.** Rationale and 20-criterion matrix:
  `docs/ml-model-selection.md`; ADR: `docs/adrs/adr-0001-on-device-ml-runtime-and-model.md`.
- Rejected/held: compact ViTs (rejected, no packaged Android artifact); ONNX
  Runtime Mobile and ExecuTorch (conditional only, each with a stated trigger);
  VLMs (Qwen2.5-VL, SmolVLM/SmolVLM2 — observed only, multimodal stage).

### 17.3 Evidence discipline

- Every claim carries an `EvidenceLevel` (FACT / MEASURED / DOCUMENTED_BY_SOURCE /
  ENGINEERING_ESTIMATE / HYPOTHESIS / UNKNOWN / REQUIRES_EXPERIMENT). No
  fabricated latency/accuracy numbers are emitted; unresolved figures are marked
  `UNKNOWN` / `REQUIRES_EXPERIMENT` and listed in §17.4.

### 17.4 Required experiments before promotion

1. Later milestone (after the 8B-15-2 reproducibility-identity work): build the
   TFLite classifier behind `OnDeviceModel`; measure latency/RAM/energy on
   representative devices (MEASURED).
2. Corpus experiment vs the baseline text heuristic on FeedSense categories with
   real ground truth, incl. an image+OCR hybrid comparison.
3. Verification gates: packaged weight licenses and SHA-256 checksums recorded
   in the reproducibility contract; re-check ONNX Runtime mobile cadence and
   EfficientNet-Lite license when the artifact is acquired.

### 17.5 Reproducibility

- `analysis/ml/selection/ResearchDecisionCatalog` pins catalog/ADR versions,
  candidate ids, per-cell evidence and sources; `DecisionSerializer` output is
  byte-stable (tests in
  `app/src/test/java/com/example/feedsense/analysis/ml/selection/`). Full JVM
  suite green; `assembleDebug` clean.

### 17.6 Model artifact & reproducibility identity (8B-15-2)

Follow-up milestone: establishes the **deterministic, auditable identity** of the
8B-15-1 configuration without touching the evaluation layer or the baseline.

- **New standalone package** `analysis/ml/repro/`: model identity vs artifact
  identity, `ArtifactSha256` (SHA-256 over exact bytes), runtime / quantization /
  preprocessing / output-mapping / privacy identities, provenance + conversion
  chain, and a composite `ReproCompositeIdentity` with byte-deterministic
  canonical serialization (`ReproCanonicalSerializer`).
- **Derived from 8B-15-1** via `ReproContractFactory` (A1 SHORTLISTED primary,
  A2 CONDITIONAL fallback preserved).
- **Honest artifact state:** the `.tflite` artifact is not available →
  `ARTIFACT_PENDING`, no fabricated checksum/version; output labels unresolved.
- **No evaluation change, no schema change, no production integration.** See
  `docs/reproducibility.md`; 102 JVM tests in
  `app/src/test/java/com/example/feedsense/analysis/ml/repro/`.
