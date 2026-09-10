# 8B-15-10 — Repository Intelligence & Research Handoff

> Milestone record. Status: **COMPLETE** (see the final report at the commit
> that closes this milestone).

## Objective

Make the FeedSense GitHub repository professionally understandable to a new
developer, researcher, or AI coding agent **without requiring access to
previous chat history**. The repository is the durable source of truth for
FeedSense's purpose, research vision, architecture, completed milestones,
current development state, model pipeline, evaluation methodology, privacy
guarantees, reproducibility requirements, testing expectations, security
requirements, known limitations, and next recommended work.

## Scope

- `README.md` and `docs/` were updated/extended to a canonical,
  research-grade documentation set.
- Milestone documentation for 8A/8B and a durable 8B-15-9 record were added
  under `docs/milestones/`.
- **No production logic was modified.** Capture, repository, ViewModel, Room
  entity, model runtime, decoder, taxonomy mapping, evaluation boundary,
  ground-truth system, and privacy pipeline code are untouched.

## Entry points

- `README.md` — project-level overview and how to build/test.
- `docs/PROJECT_CONTEXT.md` — the canonical AI/project handoff document. Read
  first.
- `docs/PROJECT_STATE.md` — current-state snapshot (milestone, status labels,
  blockers, invariants).

## Files changed

- New: `docs/PROJECT_CONTEXT.md`, `docs/PROJECT_STATE.md`,
  `docs/RESEARCH_VISION.md`, `docs/RESEARCH_ROADMAP.md`, `docs/ARCHITECTURE.md`,
  `docs/DATA_MODEL.md`, `docs/CAPTURE_ARCHITECTURE.md`,
  `docs/ANALYSIS_PIPELINE.md`, `docs/MODEL_PIPELINE.md`, `docs/MODEL_RUNTIME.md`,
  `docs/PREPROCESSING_CONTRACT.md`, `docs/TAXONOMY.md`, `docs/EVALUATION.md`,
  `docs/GROUND_TRUTH.md`, `docs/ROOT_CAUSE_ANALYSIS.md`, `docs/PRIVACY.md`,
  `docs/REPRODUCIBILITY.md`, `docs/TESTING.md`, `docs/SECURITY.md`,
  `docs/milestones/` (README, 8A/README.md, 8B/README.md,
  8B/8b-15-9-real-model-validation.md, 8B/8b-15-10-repository-intelligence.md).
- Updated: `README.md`.

## Status vocabulary

All status documents use these explicit labels exclusively:

- **VERIFIED** — supported by repository evidence.
- **UNVERIFIED** — not yet established.
- **NOT MEASURED** — intentionally not measured.
- **BLOCKED** — cannot proceed until a prerequisite is met.
- **DO NOT ASSUME** — an explicitly non-inferred item.

## Current canonical state (as of this milestone)

- **REAL MODEL INTEGRATION INFRASTRUCTURE:** VERIFIED
- **REAL DEVICE/ARTIFACT INFERENCE:** BLOCKED
- **REAL MODEL ACCURACY:** NOT ESTABLISHED

## Validation

- JVM unit test suite (`:app:testDebugUnitTest`), debug build
  (`:app:assembleDebug`), and Android test-compile
  (`:app:compileDebugAndroidTestKotlin`) were run; results recorded in
  `docs/TESTING.md`.
- Secret scan of new/modified files: performed.
- Cross-document consistency check: performed.