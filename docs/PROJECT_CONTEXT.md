# PROJECT_CONTEXT — The Canonical AI / Project Handoff Document

> **Read this first.** This document is the durable source of truth for how to
> approach the FeedSense repository. It is written for new developers,
> researchers, and AI coding agents, and does not depend on any previous chat
> history.

- Repository home: [github.com/maanik-chandela/FeedSense](https://github.com/maanik-chandela/FeedSense)
- Current branch: `feedsense-finalization`
- Current milestone: **8B-15-10**
- Next planned milestone: **8B-16** (PLANNED, not started)

---

## 1. What is FeedSense?

FeedSense is an **Android research platform for studying recommendation
systems and user exposure/interaction behavior** on personalized social media
feeds.

It is **not** primarily a consumer app. FeedSense is intended to become a
serious empirical research instrument capable of supporting:

- controlled data collection
- screen/content exposure capture
- structured observation records
- human ground-truth annotation
- AI-assisted content understanding
- model evaluation
- root-cause analysis
- reproducible experiments
- privacy-preserving datasets
- empirical recommendation-system research
- eventual research publication

The platform is **Android-first** and **on-device-first**. Research integrity
is more important than artificial feature completeness.

## 2. Why does it exist?

Personalized recommender systems shape what billions of people see daily, but
empirically studying them is hard:

- feeds are opaque, personalized, and change over time
- capturing real exposure needs on-device observation, not simulated data
- privacy constraints make naive data collection unacceptable
- ground truth is expensive and must be kept independent of the AI being
  evaluated
- results only mean something if the experiment is reproducible from the exact
  model, preprocessing, runtime, and dataset identity

FeedSense exists to make that kind of research possible on a normal Android
device, with privacy by design and reproducibility by construction.

## 3. Long-term research vision

The eventual research direction (see `docs/RESEARCH_VISION.md`) covers
themes such as recommendation exposure measurement, recommendation transition
analysis, personalized content understanding, human-AI annotation workflows,
temporal exposure patterns, interaction-aware analysis, privacy-preserving
research datasets, and cross-platform recommendation comparison.

Everything in that vision is categorized as **IMPLEMENTED**, **PLANNED**, or
**LONG-TERM RESEARCH DIRECTION**. The repository never claims future
capabilities already exist.

## 4. Strategy: Android-first, on-device-first

- Data is captured and processed on the device.
- The core pipeline has **no cloud-inference requirement**.
- Privacy processing, OCR, ML inference, and evaluation are designed to run on
  the device.
- Everything is deterministic and versioned so a result can be reproduced
  later.

## 5. Major architecture (summary)

The pipeline in one line:

```
Screen → Capture → Frame → Privacy Processing → OCR/Analysis → Model
→ Decoder → Taxonomy Mapping → Evaluation → Human Verification → Research Dataset
```

The layers, each documented under `docs/`:

| Layer | Where | Document |
|-------|-------|----------|
| Capture | `capture/` (MediaProjection foreground service) | `CAPTURE_ARCHITECTURE.md` |
| Storage | Room database `FeedSenseDatabase` v26, `model/`, `dao/` | `DATA_MODEL.md` |
| OCR/Analysis | `analysis/` (OCR + heuristic classification) | `ANALYSIS_PIPELINE.md` |
| AI/ML | `analysis/ml/` (preprocess, runtime, real pipeline) | `MODEL_PIPELINE.md`, `MODEL_RUNTIME.md` |
| Taxonomy | `analysis/CategoryCatalog.kt`, `analysis/ml/taxonomy/` | `TAXONOMY.md` |
| Human Ground Truth | `model/GroundTruth.kt`, annotation workflow | `GROUND_TRUTH.md` |
| Evaluation | `analysis/evaluation/`, `model/EvaluationRecord.kt` | `EVALUATION.md` |
| Root Cause Analysis | `analysis/evidence/`, 8A-6 | `ROOT_CAUSE_ANALYSIS.md` |
| Privacy | `analysis/privacy/` (8B-10/8B-13) | `PRIVACY.md` |
| Reproducibility | `analysis/ml/repro/` | `REPRODUCIBILITY.md` |

See `docs/ARCHITECTURE.md` for the full application architecture.

## 6. Current development state

- The 7-series built the app foundation (capture, OCR, feed items,
  interactions, Room schema v26).
- The **8A series** established the human-ground-truth and evaluation
  foundation (independent ground truth, immutable AI snapshots, reproducible
  evaluation).
- The **8B series** expanded the research/evaluation architecture:
  evidence-aware decisions, privacy/anonymization, perceptual dedup, adaptive
  sampling, the on-device ML inference foundation, and the complete
  **8B-15 model pipeline** (selection, reproducibility, preprocessing, golden
  fixtures, runtime adapter, output interpretation, taxonomy mapping,
  evaluation boundary, and real-model artifact validation).
- **8B-15-10** (this milestone) turns the repository itself into a durable,
  research-grade handoff.

The precise current state — every claim classed as `VERIFIED`, `UNVERIFIED`,
`NOT MEASURED`, `BLOCKED`, or `DO NOT ASSUME` — is in `docs/PROJECT_STATE.md`.

### The single most important status note

- **REAL MODEL INTEGRATION INFRASTRUCTURE: VERIFIED** (the MobileNetV2/TFLite
  pipeline, contracts, and validation infrastructure are complete and tested).
- **REAL DEVICE/ARTIFACT INFERENCE: BLOCKED** (no `.tflite` artifact is in the
  repository, and no device/emulator execution has happened).
- **REAL MODEL ACCURACY: NOT ESTABLISHED** (no metrics, no accuracy claims).

No document, commit, or release may state that real model inference has been
validated until a real artifact has been executed on a device/emulator and its
SHA-256 verified.

## 7. The key distinction: baseline, AI prediction, human ground truth, evaluation, diagnostic output

FeedSense deliberately keeps these separate:

| Concept | Meaning | Never allowed |
|---------|---------|---------------|
| **Baseline** | The heuristic/evidence classifiers (`local-v6.0`, `local-fusion-v1`, `temporal-v1`) | Never modified by the ML layer |
| **AI prediction** | An immutable snapshot of what the AI predicted (`AiPredictionRecord`) | Never silently overwritten by later edits |
| **Human ground truth** | What a human judged (`GroundTruth`) | Never overwritten by AI or the evaluation result |
| **Evaluation result** | The verdict comparing prediction vs truth (`EvaluationRecord`, `EvaluationRun`) | Never merged into a single field on the feed item |
| **Diagnostic / root-cause output** | Observations, hypotheses, confirmed conclusions made explicit (8A-6) | Automated diagnostics cannot silently become human-confirmed findings |

A human annotation is independent research evidence. The AI answer is frozen.
The verdict is derived and recomputable.

## 8. Research integrity principles

1. **AI predictions and human ground truth remain separate.**
2. **Ground truth is never silently overwritten by AI.**
3. **Human annotations are independent research evidence.**
4. **Evaluation must be reproducible** (frozen inputs, methodology versions,
   frozen run reports).
5. **Root-cause analysis distinguishes observations from hypotheses from
   confirmed conclusions.**
6. **Privacy-safe evidence handling** (sanitization before analysis; evidence
   loss is surfaced honestly).
7. **No fabricated claims.** Every claim must be traceable to repository
   evidence or explicitly marked `UNVERIFIED` / `NOT MEASURED` / `BLOCKED`.
8. **A component must never be described as "accurate", "validated",
   "production-ready", or "complete" unless the repository contains evidence
   supporting that claim.**

## 9. The repository is the canonical source of truth

When repository evidence and any previous context (chat history, external
notes) disagree, **the actual current repository wins**. Do not invent a
reconciliation.

- Milestone history: `CHANGELOG.md`
- Milestone indexes: `docs/milestones/`
- Architecture decisions: `docs/adrs/`, `docs/decisions.md`
- The exact source code and tests are authoritative.

---

## If You Are a New AI Agent

**Do not start coding immediately.** First establish the current milestone,
the relevant architecture, the invariants, the existing tests, the known
limitations, and the expected verification. Recommended reading order:

1. `README.md` — what the project is and how to build/test it.
2. `docs/PROJECT_CONTEXT.md` — this document.
3. `docs/PROJECT_STATE.md` — current state, status labels, blockers, invariants.
4. `docs/RESEARCH_VISION.md` — long-term research direction.
5. `docs/RESEARCH_ROADMAP.md` — completed and planned milestones.
6. The relevant architecture document for your task
   (`docs/ARCHITECTURE.md` and one of the pipeline documents).
7. The relevant milestone documentation under `docs/milestones/`.
8. The relevant source code.
9. The existing tests.

### AI agent operating rules

1. Read `PROJECT_CONTEXT.md` first.
2. Read `PROJECT_STATE.md` second.
3. Identify the current milestone.
4. Read relevant architecture documents.
5. Inspect existing tests before modifying implementation.
6. Do not redo completed milestones.
7. Do not silently change research invariants.
8. Do not fabricate metrics.
9. Do not claim real-model validation without real execution.
10. Do not overwrite human ground truth.
11. Do not weaken privacy guarantees.
12. Do not remove tests to make builds pass.
13. Prefer additive changes.
14. Run relevant tests (see `docs/TESTING.md`).
15. Run the build.
16. Review the diff.
17. Update documentation/state.
18. Report `VERIFIED` / `UNVERIFIED` / `NOT MEASURED` / `BLOCKED` separately.
19. Stop at the requested milestone.

### What must never be changed casually

- The baseline classifier behavior and its predictions.
- The frozen category taxonomy (`CategoryCatalog`).
- The immutability of `AiPredictionRecord` snapshots.
- The independence of human `GroundTruth`.
- Privacy guarantees and the sanitization boundary (no raw content past the
  privacy layer).
- Room schema migration chain and schema exports (v26).
- Reproducibility identity semantics (artifact SHA-256, versioning).
- The rule that unmapped model classes stay unmapped.
- The rule that evaluation and ground truth are never merged.

## 10. Documentation map

| Document | Contents |
|----------|----------|
| `docs/PROJECT_CONTEXT.md` | This file (canonical handoff) |
| `docs/PROJECT_STATE.md` | Current snapshot, status labels, invariants |
| `docs/RESEARCH_VISION.md` | Research direction, IMPLEMENTED/PLANNED/DIRECTION |
| `docs/RESEARCH_ROADMAP.md` | Milestone-oriented roadmap |
| `docs/ARCHITECTURE.md` | Application architecture & data flow |
| `docs/DATA_MODEL.md` | Persisted entities and relationships |
| `docs/CAPTURE_ARCHITECTURE.md` | MediaProjection capture, storage, restrictions |
| `docs/ANALYSIS_PIPELINE.md` | Frame ingestion, OCR, analysis states, workers |
| `docs/MODEL_PIPELINE.md` | The full 8B-15 model path, boundary by boundary |
| `docs/MODEL_RUNTIME.md` | MobileNetV2/TFLite runtime implementation |
| `docs/PREPROCESSING_CONTRACT.md` | Deterministic preprocessing contract |
| `docs/TAXONOMY.md` | Category taxonomy and model-native mapping |
| `docs/EVALUATION.md` | Evaluation philosophy and boundary |
| `docs/GROUND_TRUTH.md` | Annotation workflow and independence |
| `docs/ROOT_CAUSE_ANALYSIS.md` | Deterministic root-cause framework |
| `docs/PRIVACY.md` | Privacy-by-design architecture |
| `docs/REPRODUCIBILITY.md` | Reproducibility identity and status |
| `docs/TESTING.md` | Testing philosophy and commands |
| `docs/SECURITY.md` | Security rules and lessons |

Legacy docs that remain authoritative for their topics: `docs/research.md`,
`docs/research-evaluation.md`, `docs/privacy.md`, `docs/reproducibility.md`,
`docs/preprocessing-contract.md`, `docs/golden-fixtures.md`,
`docs/runtime-adapter.md`, `docs/evaluation-boundary.md`,
`docs/real-model-artifact-validation.md`, `docs/ml-inference.md`,
`docs/ml-model-selection.md`, `docs/scheduling.md`,
`docs/perceptual-deduplication.md`, `docs/roadmap.md`,
`docs/data-model.md`, `docs/architecture.md`, `docs/develop.md`,
`docs/decisions.md`, `docs/ideas.md`, `docs/adrs/`.