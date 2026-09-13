# PROJECT_STATE — Current State Snapshot

> Companion to `docs/PROJECT_CONTEXT.md`. Read both before modifying anything.
> This snapshot documents the state of the repository **as of milestone
> 8B-15-10**.

## Status labels

All claims below use exactly one of these labels:

- **VERIFIED** — supported by repository evidence (tests/build/code).
- **UNVERIFIED** — not yet established.
- **NOT MEASURED** — intentionally not measured.
- **BLOCKED** — cannot proceed until a prerequisite is met.
- **DO NOT ASSUME** — an explicitly non-inferred item.

---

## Milestone state

| Field | Value |
|-------|-------|
| Current branch | `feedsense-finalization` |
| Current milestone | **8B-15-10** (repository intelligence & research handoff) |
| Previous milestone | 8B-15-9 (real model artifact validation & end-to-end inference) |
| Previous milestone status | **COMPLETE** as an engineering/integration milestone |
| Next planned milestone | **8B-16** — empirical model evaluation (PLANNED, **not started**) |

## The current-state summary

- **REAL MODEL INTEGRATION INFRASTRUCTURE: VERIFIED**
- **REAL DEVICE/ARTIFACT INFERENCE: BLOCKED**
- **REAL MODEL ACCURACY: NOT ESTABLISHED**

---

## Current branch

`feedsense-finalization` (recorded from `git branch --show-current`). Recent
history includes `complete 8B-15-9 real model validation`.

## Current test baseline

> Recorded below from the repository claims at the time of writing; verify by
> running per `docs/TESTING.md`. No count is fabricated.

- 8B-15-9 report records (its own run): **2208 JVM tests, 0 failures**
  (2135 baseline + 73 new from 8B-15-9).
- 8B-15-8 report records (pre-8B-15-9): 2106 tests with 1 pre-existing failure
  in `ItemDecisionTest`.
- The 8B-15-10 validation run results are recorded in `docs/TESTING.md`.

If a count in this file ever disagrees with a live run, the live run wins.

## Current build status

- `./gradlew :app:assembleDebug` has been reported successful through 8B-15-9.
- The 8B-15-10 validation results are recorded in `docs/TESTING.md`.

## Known blockers

- **Real TFLite inference (BLOCKED).** Requires: (1) a real `.tflite` artifact
  downloaded/provided, (2) its SHA-256 verified, (3) its label file verified,
  (4) a device/emulator executing the artifact. Until then, all real-model
  claims are blocked.
- **8B-16 (empirical model evaluation) is BLOCKED** on the above; 8B-16 must
  not begin before the real-model execution prerequisites are satisfied.

## Known unverified claims

- Artifact SHA-256 of the MobileNetV2 `.tflite` (marked `NEEDS_VERIFICATION`).
- Actual ImageNet label file contents (`DOWNLOADED_UNVERIFIED`).

## Not measured

- Real inference latency, real output values, device timing, memory behavior.

## Important architectural invariants

1. Baseline predictions are never modified by the ML layer.
2. `AiPredictionRecord` snapshots are immutable; ground truth is never
   overwritten by AI.
3. The category taxonomy (`CategoryCatalog`) is frozen; unmapped model classes
   stay unmapped.
4. Nothing past the privacy layer sees raw content; privacy guarantees are not
   weakened.
5. Room schema v26 and the migration chain (10→26) are preserved.
6. Reproducibility identity (artifact hashes, versions) is never fabricated.
7. Evaluation, prediction, and ground truth remain three separate concerns.
8. Automated diagnostics cannot silently become human-confirmed findings.

## Current research priorities

1. Obtain and verify the real `.tflite` artifact; run it on a device/emulator.
2. (Then) build the real annotated corpus and run empirical model evaluation
   (8B-16).
3. Reproducible, privacy-preserving dataset construction and export.
4. Publication-grade recommendation research (long-term; see
   `docs/RESEARCH_VISION.md`).

## What must not be started from here

- **8B-16** must not begin until the real-model execution prerequisites are
  satisfied.
- No new UI redesign work; no unrelated application features.
- No attempt to fabricate real-model results.