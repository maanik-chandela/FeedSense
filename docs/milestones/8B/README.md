# 8B Series Overview

The 8B series expanded the FeedSense research/evaluation architecture on top
of the 8A ground-truth foundation. Every 8B milestone is additive: none has
modified the heuristic baseline, the frozen category taxonomy, Room schema
v26, human ground truth, or historical evaluations.

## 8B-1 — Local evidence-fusion architecture

Evidence-fusion engine that combines structured evidence into research
decisions. Documentation: `docs/report-8b1-fusion-v1.md`,
`docs/research-evaluation.md`.

## 8B-2 — Temporal evidence fusion

Temporal evidence fusion across time. Documentation:
`docs/report-8b2-temporal-v1.md`.

## 8B-8 — Evidence-aware production adapter

Evidence-aware decision adapter for production use (8A-6 root-cause vocabulary
with evidence strengths). Source: `analysis/evidence/decision/`.

## 8B-9 — Controlled real-data experiment

Deterministic experiment runner over real data. Source:
`analysis/evaluation/experiment/`.

## 8B-10 — Privacy-safe evidence sanitization

Evidence-protection layer between capture and analysis. Documentation:
`docs/privacy.md`.

## 8B-11 — Perceptual frame deduplication

Frame deduplication layer. Documentation:
`docs/perceptual-deduplication.md`.

## 8B-12 — Adaptive frame sampling

Adaptive frame sampling scheduler. Documentation: `docs/scheduling.md`.

## 8B-13 — Deterministic privacy processing

Deterministic on-device privacy-processing pipeline (`PrivacyProcessor`).
Documentation: `docs/privacy.md` (§12).

## 8B-14 — On-device ML inference foundation

Model-independent on-device ML abstraction. Documentation:
`docs/ml-inference.md`.

## 8B-15-1 — Model & runtime selection

Research decision phase. Selected LiteRT (TensorFlow Lite) runtime and
MobileNetV4-Conv-S (INT8) as the shortlist (ADR-0001). Documentation:
`docs/ml-model-selection.md`, `docs/adrs/adr-0001-on-device-ml-runtime-and-model.md`.

## 8B-15-2 — Model artifact & reproducibility identity

Deterministic, auditable identity for the ML configuration. Documentation:
`docs/reproducibility.md`.

## 8B-15-3 — Preprocessing contract

Deterministic, versioned, privacy-bounded preprocessing. Documentation:
`docs/preprocessing-contract.md`.

## 8B-15-4 — Golden fixtures

Preprocessing golden fixture corpus. Documentation: `docs/golden-fixtures.md`.

## 8B-15-5 — Runtime adapter & inference boundary

Boundary: validated preprocessed input → on-device runtime → raw model output.
Documentation: `docs/runtime-adapter.md`.

## 8B-15-6 — Inference output interpretation

Inference output interpretation and research prediction contract
(scaffolding; the direct decoder path used by 8B-15-9 is exercised through
`ModelOutputDecoder`).

## 8B-15-7 — Taxonomy mapping

Model-native output → FeedSense taxonomy mapping contract. Source:
`analysis/ml/taxonomy/`.

## 8B-15-8 — Taxonomy-mapped prediction evaluation boundary

Standalone, deterministic evaluation boundary. Documentation:
`docs/evaluation-boundary.md`.

## 8B-15-9 — Real model artifact validation & end-to-end inference

First bridge connecting preprocessing, runtime, decoder, taxonomy, and
evaluation boundary through a pinned MobileNetV2 artifact.
**Engineering/integration: COMPLETE. Real inference: BLOCKED** (no `.tflite`
artifact or device in the repository).
Documentation: `docs/real-model-artifact-validation.md`,
`docs/report-8b15-9-real-model-validation-v1.md`,
`docs/milestones/8B/8b-15-9-real-model-validation.md`.

## 8B-15-10 — Repository intelligence & research handoff

This milestone: repository-level documentation for humans and AI agents.
See `docs/PROJECT_CONTEXT.md` and `docs/PROJECT_STATE.md`.