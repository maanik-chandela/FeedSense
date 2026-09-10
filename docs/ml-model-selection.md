# On-Device Model & Runtime Selection (Milestone 8B-15-1)

> Status: **COMPLETE — research decision phase**
>
> This milestone is a **decision record**, not an implementation. It answers: *which
> on-device ML architecture and runtime should FeedSense standardize on, and with
> what evidence?* The existing heuristic/Fusion baseline (`local-v6.0`), the
> taxonomy, the Room schema, and the 8A evaluation layer are **untouched**.
> No model is integrated or downloaded here; integration is 8B-15-2+ behind the
> 8B-14 `OnDeviceModel` contract, running **in parallel** to the baseline and
> never overwriting it.

The machine-readable source of truth for this document is
`app/src/main/java/com/example/feedsense/analysis/ml/selection/`
(`ResearchDecisionCatalog`, `DecisionSerializer`) plus its tests. The catalog is
versioned (`8b-15-1-v1`) and its serialization is deterministic, so this decision
is reproducible.

---

## 1. Objective

Select the **model architecture** and **on-device runtime** that FeedSense will
use as the parallel experimental ML path, by:

1. enumerating and researching candidate **architectures** (families A–F) and
   **runtimes** (LiteRT/TFLite, ONNX Runtime, ExecuTorch),
2. scoring each against a fixed 20-criterion matrix with evidence levels,
3. recording the outcome as a versioned ADR (`docs/adrs/adr-0001-…md`), and
4. leaving an explicit list of **required experiments** for 8B-15-2+.

## 2. Constraints and non-goals honored

- **No implementation** of a real model in this milestone. No new production
  dependency is added (research only).
- **Local-only.** `ModelRuntime.ON_DEVICE` is the only runtime; no cloud, no
  network acquisition, no telemetry.
- **minSdk 24** stays. Privacy-bounded input: the ML path consumes only the
  8B-13 safe frame, never a raw screenshot.
- **Baseline is control.** ML predictions are compared per item (8B-14 machinery)
  and never write into the baseline prediction record.
- **No fabricated figures.** Every claim is graded with an `EvidenceLevel`;
  numbers that were not measured or sourced are `UNKNOWN`/`REQUIRES_EXPERIMENT`.
- No taxonomy change, no Room schema change, no UI change.

## 3. Problem context: what FeedSense actually classifies

FeedSense categories are dominated by **on-screen text**: memes, ads, movie/series
clips, motivational speeches, ranking videos, educational/reaction content,
subtitles, creator handles and platform UI. The baseline classifier is a
text-heuristic over on-device OCR (`TextHeuristicClassifier`, baseline `local-v6.0`)
with an evidence/fusion layer. Any vision model candidate must therefore be judged
against content that is *largely text-driven*, which is exactly where a
vision-only ImageNet-style classifier is weakest and where OCR+vision hybrids shine.

## 4. Research questions

| # | Question |
| --- | --- |
| RQ1 | Which model **architecture classes** are viable on-device for single-label video-frame category prediction at minSdk 24? |
| RQ2 | Which **runtime** (LiteRT/TFLite, ONNX Runtime, ExecuTorch) best matches size, latency, maturity and license constraints? |
| RQ3 | Is an **image-only** model sufficient for FeedSense categories, or is the caption/OCR text path required too? |
| RQ4 | Which **quantization depth** (FP16 / INT8 / 4-bit) is reachable per candidate, and what is the practical floor? |
| RQ5 | What is the achievable **accuracy/latency/energy** trade-off versus the text-heuristic baseline as control? |
| RQ6 | How must the selected path integrate the **8B-14 contract**, provenance and the **8B-13 privacy boundary**? |
| RQ7 | Which **uncertainties must be resolved experimentally** before any promotion? |

Answers are synthesized in §7–§10; the code catalog carries the per-cell evidence.

## 5. Evidence vocabulary (used everywhere)

`FACT` · `MEASURED` · `DOCUMENTED_BY_SOURCE` · `ENGINEERING_ESTIMATE` ·
`HYPOTHESIS` · `UNKNOWN` · `REQUIRES_EXPERIMENT`.

Rules: a `DOCUMENTED_BY_SOURCE` claim carries a `SourceReference`; an
`ENGINEERING_ESTIMATE` states its assumption; `UNKNOWN` cells are never
rationalized into a guess.

## 6. Candidate families (A–F)

| Family | Candidates | Disposition |
| --- | --- | --- |
| **A** Compact CNN classifiers | MobileNetV4-Conv-S · EfficientNet-Lite | **SHORTLISTED** (A1) · CONDITIONAL (A2) |
| **B** Compact vision transformers | MobileViT family / EdgeNeXt | REJECTED |
| **C** ONNX Runtime Mobile | onnxruntime-mobile / onnxruntime-android | CONDITIONAL |
| **D** Runtime: TFLite / LiteRT | TensorFlow Lite (LiteRT) | **SHORTLISTED** |
| **E** Runtime: ExecuTorch | ExecuTorch (XNNPACK/QNN/Vulkan) | CONDITIONAL |
| **F** Small multimodal / VLM | Qwen2.5-VL · SmolVLM/SmolVLM2 · (MobileVLM documented) | OBSERVED_ONLY |

Key evidence per candidate (`DOCUMENTED_BY_SOURCE` unless noted):

- **A1 MobileNetV4-Conv-S** — 3.8M params / 0.2G MACs (arXiv 2404.10518);
  Hybrid-Large 87% ImageNet-1K, **3.8 ms on Pixel 8 EdgeTPU**;
  official code in `tensorflow/models` (Apache-2.0 header). Conv-S's own
  FeedSense accuracy is `UNKNOWN`.
- **A2 EfficientNet-Lite** — Lite0–Lite4 tuned for TFLite CPU/GPU/EdgeTPU;
  **integer-only quantized Lite4 exists** (TF blog 2020-03-16). Packaged-weight
  license `UNKNOWN` → CONDITIONAL.
- **B1 Compact ViT** — no maintained packaged Android artifact found;
  attention heavier on mobile CPU at equal size. REJECTED.
- **C1 ONNX Runtime** — MIT; `onnxruntime-mobile` Maven is **stale at 1.18.0
  (May 2024)** while `onnxruntime-android` is current at **1.29.0 (Aug 2026)**
  but large (AAR ~41.6 MB). NNAPI EP exists but NNAPI is deprecated. CONDITIONAL.
- **D1 LiteRT/TFLite** — Apache-2.0; XNNPACK CPU default, maintained GPU
  delegate + vendor NPU; covers minSdk 24; dominant, mature ecosystem.
  **SHORTLISTED.**
- **E1 ExecuTorch** — BSD-3-Clause; Android AAR on Maven (org.pytorch,
  **1.4.0 Aug 2026**, v1.0.0 since Oct 2025); XNNPACK always built, QNN
  (Snapdragon HTP)/MediaTek/Vulkan delegates; official Android demos (Llama,
  LLaVA/vision); weight-only **INT4 is a first-class export path**. Heavier
  export/wiring than TFLite → CONDITIONAL.
- **F1 Qwen2.5-VL** — 3B/7B/32B/72B; ai-hub has an optimized 7B. 3B ≈ 6 GB
  FP16 / ~1.5 GB INT4 weights (estimate); LLM decode latency; specific artifact
  license **not verified**. OBSERVED_ONLY.
- **F2 SmolVLM/SmolVLM2** — 256M/500M/2.2B; **256M runs in <1 GB GPU RAM**
  (arXiv 2504.05299); SmolVLM2 stated Apache-2.0 across sizes; 256M reached
  ~80 decode tok/s on an M4 Max laptop (WebGPU); HF published a mobile app. Still
  an LLM decode per frame → OBSERVED_ONLY. MobileVLM V2 (Apache-2.0, 1.7B/3B)
  documents the same category of approach.

> Android platform context (2026): **NNAPI is deprecated**
> (developer.android.com migration guide, 2026-03-06). FeedSense will not depend
> on NNAPI; the short-listed LiteRT path uses XNNPACK CPU + GPU delegate + vendor
> NPUs.

## 7. Architecture options for the ML path (1–6)

1. **Single-frame image classifier** — one frame → one category. Cheapest, matches
   the short-listed CNN candidates.
2. **Multi-frame (frame-per-sample) classifier** — independent frames, aggregated
   vote later. Straightforward extension of 1.
3. **Temporal model** — codec-aware difference or sequence model (8B-11/8B-12
   sampling already reduces frame count); higher cost, only justified if 1–2
   prove unstable.
4. **Image + OCR hybrid** — vision classifier fused with the existing text-heuristic
   output. **Strongest fit for FeedSense's text-driven categories**; candidate for
   the 8B-15-2+ experiment.
5. **Multimodal / VLM** — single model natively reads text+image (F candidates).
   Future stage; not this milestone.
6. **Hybrid cascade** — cheap gate (motion/dedup) → option 1/2 → option 4/5 only
   on frames that earn the cost (mirrors 8B-12 scheduling).

## 8. Quantization depth (RQ4)

| Depth | LiteRT | ONNX Runtime | ExecuTorch | Notes |
| --- | --- | --- | --- | --- |
| FP16 | supported | supported | supported | weight-level; little APK savings |
| INT8 | **first-class, integer-only** | QDQ supported | PTQ/QAT supported | practical floor for the CNN shortlist |
| 4-bit/INT4 | no first-class path | no practical mobile path | **weight-only INT4 first-class** | revisit only for larger models |

## 9. The 20-criterion decision matrix

Ratings: **S**=STRONG, **A**=ADEQUATE, **W**=WEAK, **U**=UNKNOWN, N=N/A. The
full per-cell rationale + evidence lives in `ResearchDecisionCatalog` and
serializes deterministically.

| # | Criterion | A1 | A2 | B1 | C1 | D1 | E1 | F1 | F2 |
| --- | --- | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| 1 | Taxonomy alignment | A | A | A | A | A | A | A | A |
| 2 | Text/caption overlay robustness | W | W | W | W | W | W | S | S |
| 3 | Generalization to feed content | U | U | U | U | U | U | U | U |
| 4 | Model size (MB) | S | S | W | S | S | A | W | A |
| 5 | Runtime APK impact | S | S | A | W | S | W | W | A |
| 6 | Peak inference RAM | U | U | U | U | U | U | W | A |
| 7 | Per-inference latency | S | A | W | A | S | A | W | A |
| 8 | Energy per inference | U | U | U | U | U | U | W | W |
| 9 | Temporal / multi-frame support | W | W | W | W | W | W | S | S |
| 10 | CPU-only feasibility | S | S | W | S | S | S | W | A |
| 11 | Accelerator options | S | S | A | A | S | S | W | A |
| 12 | Android API compatibility | S | S | A | A | S | A | W | W |
| 13 | FP16 quantization | A | A | U | A | A | A | A | A |
| 14 | INT8 quantization | S | S | A | A | S | A | A | A |
| 15 | 4-bit / INT4 quantization | W | W | W | W | W | A | A | A |
| 16 | Runtime maturity & ecosystem | S | S | W | A | S | A | W | W |
| 17 | License compatibility | S | W | W | S | S | S | W | A |
| 18 | Integration effort | S | S | W | A | S | W | W | W |
| 19 | Determinism & reproducibility | A | A | A | A | A | A | W | W |
| 20 | Privacy-bounded pipeline | S | S | A | S | S | S | A | A |

## 10. Decision summary

- **Runtime: LiteRT (TensorFlow Lite), D1 — SHORTLISTED.** Apache-2.0, mature
  ecosystem, XNNPACK/GPU/NPU, minSdk 24, best fit for the 8B-14 contract. ONNX
  Runtime (C1) and ExecuTorch (E1) are documented CONDITIONAL alternatives.
- **Model: MobileNetV4-Conv-S, A1 — SHORTLISTED.** EfficientNet-Lite (A2) is the
  documented fallback (CONDITIONAL pending weight-license verification).
- **Architecture: start with option 1/2 (single-frame CNN), evaluate option 4
  (image+OCR hybrid) in the experiment** — the content is text-driven.
- **Quantization: INT8** for the CNN; 4-bit is not first-class in LiteRT and is
  not needed at this scale.
- **Rejected/observed:** compact ViTs (B1, rejected), VLMs (F1/F2, observed only,
  multimodal stage), ONNX Runtime and ExecuTorch as *primary* runtime (conditional
  only).
- Full rationale, alternatives and risks: **`docs/adrs/adr-0001-ml-runtime-and-model.md`**
  and the machine-readable `ResearchDecisionCatalog`.

## 11. Required experiments before any promotion (RQ7)

> **Scope note (re-scoping of 8B-15-2):** Milestone **8B-15-2** was subsequently
> re-scoped to the **model artifact & reproducibility identity** milestone (see
> `docs/reproducibility.md`) — it establishes the deterministic, auditable
> identity of the selected configuration, and does **not** export/integrate the
> model or measure on-device latency. Those integration/measurement items below
> move to later milestones (8B-15-3+). The 8B-15-1 decision itself is unchanged.

1. **Artifact & reproducibility identity (8B-15-2, done):** a standalone
   `analysis/ml/repro/` contract records model/artifact/runtime/quantization/
   preprocessing/output-mapping/privacy identity, provenance, SHA-256 strategy,
   and a composite deterministic canonical identity. The `.tflite` artifact is
   **not available** and is reported as `ARTIFACT_PENDING`, per
   `docs/reproducibility.md`.
2. **Later milestones:** export MobileNetV4-Conv-S to `.tflite` (INT8), add the
   adapter behind `OnDeviceModel`, and **measure** latency/RAM/energy on 2–3
   representative devices (MEASURED evidence replaces the current estimates).
3. **Corpus experiment** vs the text-heuristic baseline (`local-v6.0`) on real
   FeedSense categories with real ground truth (8B-9 harness); report agreement,
   not assumed accuracy.
4. **Hybrid check:** compare option 2 vs option 4 (vision + OCR fusion) to answer
   RQ3.
5. **Verification gates:** packaged weight licenses + SHA-256 checksums recorded
   in `ModelMetadata.checksum`; EfficientNet-Lite license checked at artifact
   acquisition; ONNX Runtime Mobile Maven cadence re-checked.

## 12. Reproducibility of this decision

- `ResearchDecisionCatalog` pins `CATALOG_VERSION = 8b-15-1-v1`, the ADR version
  (`1.0`), candidate ids, and per-cell evidence.
- `DecisionSerializer` emits byte-stable JSON (sorted keys, escaped values,
  no numeric grades); tests prove determinism, stable ordering, missing-field
  omission, unknown-value handling, version embedding and reproducibility keys.
- All 57 JVM tests pass (6 new test files under
  `app/src/test/java/com/example/feedsense/analysis/ml/selection/`) and the debug
  app assembles cleanly.

## 13. Sources

Collection date `2026-09-06`. The catalog carries a `SourceReference` per claim;
key sources:

- MobileNetV4 — arXiv 2404.10518 · tensorflow/models (Apache-2.0)
- EfficientNet-Lite — TensorFlow blog 2020-03-16
- LiteRT/TFLite — ai.google.dev/edge/litert
- NNAPI deprecation — developer.android.com/ndk/guides/neuralnetworks/migration-guide
- ONNX Runtime packages — central.sonatype.com (onnxruntime-mobile, onnxruntime-android)
- ExecuTorch — pytorch/executorch (BSD-3-Clause) · org.pytorch:executorch-android (Maven) · docs.pytorch.org/executorch
- Qwen2.5-VL — huggingface.co/Qwen/Qwen2.5-VL-3B-Instruct (license UNVERIFIED in this phase)
- SmolVLM — arXiv 2504.05299 (COLM 2025)
- MobileVLM — Meituan-AutoML/MobileVLM (Apache-2.0) · arXiv 2402.03766