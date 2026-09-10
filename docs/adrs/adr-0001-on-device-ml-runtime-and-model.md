# ADR-0001 — On-device classification runtime & model for the FeedSense ML path

- **Status:** ACCEPTED
- **Version:** 1.0 (supersedes: n/a)
- **Date:** 2026-09-06
- **Milestone:** 8B-15-1

> **Scope note on milestone numbering (added at 8B-15-2, does not change this
> decision):** 8B-15-2 was subsequently re-scoped to the **model artifact &
> reproducibility identity** milestone (`docs/reproducibility.md`,
> `analysis/ml/repro/`); it does **not** integrate the model behind
> `OnDeviceModel` or measure on-device latency. Those integration/measurement
> steps move to later milestones. The decision itself is unchanged by this note.

> This ADR is versioned: the decision is **never silently changed**. Any future
> revision must bump `version` and set `supersedes` to this version, leaving this
> record intact. The machine-readable copy lives in
> `ResearchDecisionCatalog.decision` and serializes deterministically via
> `DecisionSerializer` (see `docs/ml-model-selection.md`).

## Context

- FeedSense classifies short-form video/feed content locally using OCR text
  heuristics (baseline `local-v6.0`); categories are dominated by on-screen text
  (memes, ads, movie/series clips, captions).
- 8B-14 established the `OnDeviceModel` abstraction and a parallel
  baseline-vs-ML comparison; this ADR selects the real model/runtime behind it.
- Android guidance in 2026: **NNAPI is deprecated**; LiteRT (TFLite) XNNPACK/GPU +
  vendor NPUs are the supported acceleration paths.
- Hard constraints: local-only, no network, minSdk 24, privacy-bounded input
  (8B-13 safe frames only); the baseline heuristic is never overwritten by ML.

## Decision

Standardize the FeedSense on-device ML path on **LiteRT (TensorFlow Lite)** as the
runtime and **MobileNetV4-Conv-S** as the primary image-classification
architecture, with **EfficientNet-Lite** as a documented fallback.

This is a **research-phase decision**: no model is integrated or downloaded in
8B-15-1. Integration happens in 8B-15-2 behind the 8B-14 `OnDeviceModel` contract,
running alongside the baseline and never overwriting it. Quantization target: INT8.

## Alternatives considered

| Candidate | Disposition | Why not primary |
| --- | --- | --- |
| EfficientNet-Lite (A2) | Fallback | Packaged weight license unverified in this phase. |
| Compact ViT / MobileViT family (B1) | Rejected | No maintained packaged Android artifact found; attention heavier on mobile CPU at equal size. |
| ONNX Runtime Mobile (C1) | Conditional | Size-optimized Maven package stale (1.18.0, 2024); full AAR large; adopt only for ONNX-only model needs. |
| ExecuTorch (E1) | Conditional | Mature (BSD-3, v1.4.0) but heavier export/wiring; adopt when PyTorch-origin or QNN-accelerated (incl. VLM) models are selected. |
| Qwen2.5-VL (F1) | Observed only | Multimodal stage; multi-GB weights, decode latency, license and Android runtime path not verified. |
| SmolVLM/SmolVLM2 (F2) | Observed only | Real on-device evidence and Apache-2.0, but still LLM decode per frame; multimodal stage candidate. |

## Consequences

- No new production dependency lands in 8B-15-1; it arrives with 8B-15-2 integration.
- The ML path runs in parallel to the text-heuristic baseline; per-item agreement is
  surfaced by the 8B-14 comparison machinery; nothing overwrites the baseline record.
- NNAPI is intentionally not used; GPU/NPU delegation must be verified per
  representative device with CPU XNNPACK as default.
- The short-listed single-label classifier needs an explicit label-mapping layer to
  `CategoryCatalog` keys; multi-label support remains future
  (`ModelTaskType.MULTI_LABEL_IMAGE`).

## Risks

| Risk | Likelihood | Impact | Evidence | Mitigation |
| --- | --- | --- | --- | --- |
| Accuracy on FeedSense categories unknown for any candidate | High | High | REQUIRES_EXPERIMENT | Corpus experiment vs baseline before any promotion; publish agreement, not assumed accuracy. |
| Exported weight artifacts may carry licenses different from code | Medium | Medium | HYPOTHESIS | Verify license + SHA-256 at integration; record in `ModelMetadata.checksum`. |
| Caption/overlay-dominated categories may not be solved by vision alone | High | Medium | HYPOTHESIS | Pursue image+OCR hybrid (option 4) in the experiment; keep the text-heuristic baseline. |
| 4-bit quantization immature in LiteRT | Low | Low | HYPOTHESIS | Use INT8 for the CNN; revisit 4-bit only if larger models (VLM) enter via ExecuTorch. |
| Device fragmentation of GPU/NPU availability | Medium | Medium | UNKNOWN | Benchmark on 2–3 representative devices, CPU as default delegate. |

## Future validation (required before promotion)

1. 8B-15-2: build MobileNetV4-Conv-S TFLite (INT8) classifier + adapter behind
   `OnDeviceModel`; measure latency/RAM/energy (MEASURED).
2. Corpus experiment vs the baseline on FeedSense categories with real ground truth.
3. Verify packaged weight licenses and artifact checksums.
4. Re-check ONNX Runtime Mobile Maven cadence and EfficientNet-Lite license at
   8B-15-2 start.

## Related documents

- `docs/ml-model-selection.md` (research + 20-criterion matrix + sources)
- `docs/ml-inference.md` (8B-14 foundation)
- `docs/architecture.md`
- `docs/research-evaluation.md` (§17)
- `app/src/main/java/com/example/feedsense/analysis/ml/selection/` (machine-readable catalog)