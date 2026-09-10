# Evaluation Boundary (8B-15-8)

## Overview

This milestone defines the **Taxonomy-Mapped Prediction Evaluation Boundary** — the standalone, deterministic evaluation boundary that takes the output of the prediction pipeline and produces a research-safe representation ready for future comparison against human ground truth.

This milestone defines **what should be compared** and **how**, NOT whether the prediction is correct.

## Pipeline

```
Model Output
→ Output Interpretation (8B-15-6, not yet implemented)
→ Taxonomy Mapping (8B-15-7)
→ Eligibility Decision (8B-15-8)
→ Structural Comparison (8B-15-8)
```

Since 8B-15-6 was not implemented, the current pipeline goes directly from `ModelInferenceResult` (8B-14) to `TaxonomyMappingEngine` (8B-15-7).

## Architecture

### EvaluationCandidateSnapshot

An immutable historical snapshot that freezes exactly what prediction state enters evaluation. All version identities are frozen so that future changes to the mapping registry do not alter the meaning of a past candidate.

### EvaluationEligibility

An explicit, deterministic eligibility decision. Eligibility is **independent from prediction confidence**. A prediction with high model confidence can still be ineligible because its taxonomy mapping is ambiguous. A prediction with low model confidence can still be structurally eligible.

Eligibility states:
- `ELIGIBLE` — structurally eligible for comparison
- `NOT_ELIGIBLE_UNMAPPED` — no taxonomy equivalent
- `NOT_ELIGIBLE_AMBIGUOUS` — multiple taxonomy candidates
- `NOT_ELIGIBLE_INVALID` — validation/compatibility failure
- `NOT_ELIGIBLE_UNSUPPORTED` — outside known label set
- `NOT_ELIGIBLE_REJECTED` — explicitly rejected mapping
- `NOT_ELIGIBLE_MISSING_OUTPUT` — no model prediction
- `NOT_ELIGIBLE_MISSING_TAXONOMY` — no taxonomy version
- `NOT_ELIGIBLE_MISSING_MAPPING` — no mapping provided
- `NOT_ELIGIBLE_VERSION_MISMATCH` — taxonomy version mismatch

### EvaluationComparisonInput

A standalone comparison contract that clearly identifies what is being compared from the prediction side and the ground-truth side. Ground truth is a read-only reference; it is never modified.

### EvaluationComparisonResult

A deterministic structural comparison classification:

- `MATCH` — same taxonomy identity/version and same taxonomy ID
- `MISMATCH` — both sides valid but taxonomy IDs differ
- `UNCOMPARABLE` — structural incompatibility prevents comparison
- `PENDING_REVIEW` — requires human adjudication

### EvaluationDecisionReason

A stable, machine-readable reason-code taxonomy for all decisions.

## Key Design Principles

1. **Evaluation eligibility does not mean the prediction is correct.**
2. **Model confidence does not determine evaluation eligibility.**
3. **Ground truth remains independent of model output.**
4. **UNCOMPARABLE is not equivalent to MISMATCH.**
5. **This milestone does not calculate evaluation metrics.**
6. **Historical mappings remain identifiable.**
7. **Changing the mapping registry after snapshot creation does not change frozen candidate meaning.**

## What This Milestone Establishes

**Prediction → Mapping → Eligibility → Comparison**

## What This Milestone Does NOT Establish

**Comparison → Accuracy/F1/etc.**

- No metric aggregation
- No confusion matrix computation
- No agreement coefficients
- No model threshold tuning

## Mapping-State Handling

The evaluator handles all 8B-15-7 mapping outcomes:

| Mapping Status | Eligibility |
|---|---|
| DIRECT | ELIGIBLE |
| MAPPED | ELIGIBLE |
| UNMAPPED | NOT_ELIGIBLE_UNMAPPED |
| AMBIGUOUS | NOT_ELIGIBLE_AMBIGUOUS |
| UNSUPPORTED | NOT_ELIGIBLE_UNMAPPED (via engine) |
| REJECTED | NOT_ELIGIBLE_REJECTED |
| INVALID | NOT_ELIGIBLE_INVALID |
| Missing | NOT_ELIGIBLE_MISSING_MAPPING |

## Taxonomy Version Compatibility

The evaluator never assumes `taxonomyId == taxonomyId` is sufficient. It validates:
- Taxonomy identity
- Taxonomy version
- Mapping version
- Prediction interpretation version (when available)

## Deterministic Serialization

All boundary types support deterministic serialization:
- Stable field ordering (sorted keys)
- Stable enum serialization (`.label`, never ordinal)
- Stable null handling
- SHA-256 hashing for auditability
- No random IDs in serialization
- No timestamps affecting serialization

## Files Added

### Source (7 files)

| File | Purpose |
|---|---|
| `EvaluationCandidateSnapshot.kt` | Immutable frozen snapshot |
| `EvaluationEligibility.kt` | Eligibility decision + factory |
| `EvaluationDecisionReason.kt` | Reason code taxonomy |
| `EvaluationComparisonInput.kt` | Comparison input contract |
| `EvaluationComparisonStatus.kt` | Comparison status + evaluator |
| `EvaluationBoundaryEvaluator.kt` | Core orchestrator |
| `EvaluationBoundarySerializer.kt` | Deterministic serialization |

### Tests (7 files)

| File | Tests |
|---|---|
| `EvaluationBoundaryFixtures.kt` | Golden deterministic fixtures |
| `EvaluationBoundaryIntegrationTest.kt` | Full chain integration tests |
| `EvaluationEligibilityTest.kt` | Eligibility decision tests |
| `EvaluationComparisonTest.kt` | Comparison logic tests |
| `EvaluationBoundaryDeterminismTest.kt` | Determinism + serialization tests |
| `EvaluationBoundarySerializerTest.kt` | Serializer tests |
| `EvaluationBoundaryBaselineIsolationTest.kt` | Baseline isolation verification |

### Documentation (1 file)

| File | Purpose |
|---|---|
| `docs/evaluation-boundary.md` | This document |

## Test Coverage

- 91 evaluation boundary tests (all passing)
- 2105 existing tests (1 pre-existing failure in ItemDecisionTest, unrelated)
- Full JVM test suite: 2106 tests, 1 pre-existing failure
- Android build: `assembleDebug` successful

## Limitations

1. **8B-15-6 (prediction interpretation) was not implemented.** The pipeline goes directly from `ModelInferenceResult` to taxonomy mapping.
2. **The taxonomy engine (8B-15-7) converts UNSUPPORTED status to Unmapped results**, so the evaluator cannot distinguish between truly unmapped labels and unsupported labels when routing through the engine. Direct eligibility creation from `MappingStatus.UNSUPPORTED` produces `NOT_ELIGIBLE_UNSUPPORTED`.
3. **No database persistence.** All boundary types are in-memory only.
4. **No real model inference.** Tests use deterministic fixtures.
5. **The existing `EvaluationRecord` (8A-1) verdict system is independent** from the new comparison status. They operate at different abstraction levels.

## 8B-15-9 Was NOT Started

This milestone stops after the evaluation boundary and comparison contract. The next milestone (8B-15-9) was not started.
