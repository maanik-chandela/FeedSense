# On-Device ML Inference Foundation (Milestone 8B-14)

> Status: **COMPLETE**
>
> A clean, replaceable **on-device ML inference foundation** for FeedSense: a
> model-independent abstraction that a future real model (TFLite / ONNX /
> ExecuTorch) can plug into, plus a deterministic fake model that exercises every
> contract today. The existing heuristic/Fusion baseline is **untouched** and the
> two paths run side by side and can be compared per item.

## 1. Objective

Build the foundation for adding an on-device image-classification model **without
modifying** the baseline classifiers, the Fusion layer, the 8A evaluation layer,
Room, or the UI. Everything introduced here is versioned, deterministic,
reproducible, and privacy-bounded.

## 2. Explicit non-goals honored

- **No** TFLite / ONNX / ExecuTorch dependency was added in this milestone.
- **No** cloud inference, telemetry, or uploads. `ModelRuntime.ON_DEVICE` is the
  only runtime.
- **No** Room schema change (DB stays unchanged).
- **No** UI change.
- **No** modification of baseline predictions or the 8A `AiPredictionRecord`
  written by the baseline.
- Model "unavailable" is **explicit** (`MODEL_UNAVAILABLE`) — it is never coerced
  into category `other`.

## 3. Architecture

```
safeFrame (privacy-approved, 8B-13 PrivacyProcessor)
        ↓
DefaultModelPreprocessor  (frame → ModelInput, PREPROCESS_VERSION)
        ↓
ModelInput (framework-agnostic NHWC tensor: FLOAT32 / INT8 / UINT8)
        ↓
OnDeviceModel  (interface; RuntimeAdapter behind)
   - FakeOnDeviceModel (deterministic, used for tests + exercising contracts)
   - future real runtimes adopt the same interface
        ↓
ModelInferenceResult  (status + ranked top-K + provenance + latencies + failure)
        ↓            \
MlEvaluationBridge     BaselineMlComparison
        ↓                 ↓
new AiPredictionRecord (source=ML_MODEL)     descriptive baseline-vs-ML diff
```

The engine never sees a raw screenshot: it consumes only the `PrivacyFrame`
produced by the 8B-13 privacy pipeline (`OnDeviceInferenceEngine.analyzeSafeFrame`).

## 4. Key components (`app/src/main/java/com/example/feedsense/analysis/ml/`)

| File | Responsibility |
| --- | --- |
| `ModelIdentity` | runtime/format/task/quantization vocabulary + immutable `ModelMetadata` (id, version, checksum, input/output specs). Enforces local-only. |
| `ModelInputSpec` / `ModelOutputSpec` | explicit forward/backward contracts; prevent silent behavior change (spec mismatch fails fast). |
| `ModelPreprocessor` / `DefaultModelPreprocessor` | safe frame → tensor conversion; deterministic nearest-neighbor resize, ARGB→RGB, scale/normalize, optional INT8/UINT8 quantization; `preprocess-v1`. |
| `OnDeviceModel` / `RuntimeAdapter` | the inference contract and its runtime boundary. `infer()` never throws; failures are explicit results. |
| `ModelInferenceResult` / `InferenceStatus` / `InferenceFailure` | result + status vocabulary (SUCCESS / MODEL_UNAVAILABLE / INVALID_INPUT / PREPROCESSING_FAILURE / INFERENCE_FAILURE / OUTPUT_INVALID / RESOURCE_LIMIT / NOT_RUN). |
| `ModelOutputValidator` | deterministic validation of raw output: reject non-finite/out-of-range confidences and out-of-taxonomy labels; dedup + deterministic ordering; top-K cap. |
| `OnDeviceInferenceEngine` | composition root: preprocess → load-once → infer → provenance-stamped result; explicit `MODEL_UNAVAILABLE`, preprocessor-failure paths. |
| `ModelRegistry` | registers packaged models only. **Never downloads**; network acquisition is not a thing here. |
| `InferenceClock` / `StepInferenceClock` | injectable time source; deterministic latency instrumentation (no fabricated measurements). |
| `FakeModel` | `FakeOnDeviceModel` + `FakeRuntimeAdapter`; deterministic behaviors (fixed scores, input-hash buckets, unavailable, load failure, inference crash, resource limit, malformed output). |
| `MlEvaluationBridge` | materializes a **new** `AiPredictionRecord` with `source="ML_MODEL"` and the exact `modelVersion`; never mutates the baseline record. |
| `BaselineMlComparison` | read-only descriptive comparison (AGREE / DISAGREE / BASELINE_ONLY_DECIDED / ML_ONLY_DECIDED / NEITHER_DECIDED / ML_UNAVAILABLE). |
| `ModelResultSerializer` | deterministic, privacy-safe metadata serialization (sorted keys, fixed-precision confidences). Cannot see pixels/OCR/private content. |

## 5. Versioning & provenance

Every result is attributable to the exact stack that produced it:

- `modelVersion` + `modelChecksum` — the artifact.
- `preprocessVersion` — `preprocess-v1` (DefaultModelPreprocessor).
- `privacyVersion` — the 8B-13 `PrivacySanitizationVersion.PROCESSING`
  (`privacy-processing-v1`) that produced the safe frame.
- `inputSpecVersion` / `outputSpecVersion` — the I/O contract.
- `evidenceId` / `experimentId` / `timestampMs` — the evidence and experiment.

`InferenceProvenance.summaryKey()` collapses these into a compact experiment key
(e.g. `model-v1/preprocess-v1/privacy-v1/input-v1/output-v1`) so future experiments
can compare across model/preprocess versions.

## 6. Failure semantics

- One ML failure **never** crashes the analysis pipeline; every failure is an
  explicit result while the baseline remains fully available.
- `MODEL_UNAVAILABLE` is explicit and attributable (model id/version still passed).
- `INVALID_INPUT` (input does not match spec) is distinct from
  `OUTPUT_INVALID` (model produced malformed output) — never collapsed.
- Latencies come from the injected clock; nothing is fabricated.

## 7. Baseline coexistence

- The heuristic baseline and the ML path are independent snapshots.
- `MlEvaluationBridge` writes `source="ML_MODEL"` records; the 8A layer can
  compare them against the frozen baseline `LOCAL` records on identical items.
- `BaselineMlComparison` exposes agreement/disagreement without mutating either
  side — correctness is still answered by 8A against ground truth, not here.

## 8. Privacy boundary

- Input is the **privacy-approved** frame only; the engine has no raw-frame path.
- `ModelInferenceResult` and the serializer never carry pixels, OCR text, or
  private content. Serialization is allow-listed metadata only.

## 9. Tests

`app/src/test/java/com/example/feedsense/analysis/ml/`:

- `ModelOutputValidatorTest` — valid output, NaN/∞/out-of-range/unknown
  rejections, ties, dedup, top-K cap, determinism.
- `DefaultModelPreprocessorTest` — RGB extraction, scaling, normalization,
  INT8 quantization, ARGB, deterministic latency.
- `FakeOnDeviceModelTest` — lifecycle, determinism, explicit failure statuses,
  spec-mismatch, close semantics.
- `OnDeviceInferenceEngineTest` — end-to-end provenance, latency splitting,
  determinism, explicit failures, spec-mismatch fails fast.
- `MlEvaluationBridgeTest` — `ML_MODEL` records, exact version, null on failure,
  baseline never mutated.
- `BaselineMlComparisonTest` — all comparison kinds, inputs unchanged.
- `ModelResultSerializerTest` — byte-stable JSON, rounded confidences, no
  sensitive keys, sorted keys.
- `ModelRegistryTest` — register/get/availability, no lifecycle stealing.
- `PrivacyBoundaryIntegrationTest` — 8B-13 `PrivacyProcessor` output feeds the
  engine, privacy version attribution, allow-listed serialization.

## 10. Limitations (honest)

- The fake model is **not** a real classifier; it exercises contracts only.
- No accuracy/intent metrics are claimed for ML output yet.
- No real device benchmarks were run; multilabel/quantization support is
  structural, primed for a future model + extension.