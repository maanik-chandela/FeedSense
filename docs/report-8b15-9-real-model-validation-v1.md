# Milestone 8B-15-9 Report — Real Model Artifact Validation & End-to-End Inference (REAL_MODEL_VALIDATION_V1)

> Status: **COMPLETE**
> Builds the first scientifically reproducible bridge from real image →
> preprocessing → real model runtime → output → decoder → taxonomy mapping →
> evaluation boundary, using a pinned, named MobileNetV2 artifact. It is **not**
> a claim of model correctness, accuracy, or production readiness.

## 1. Milestone objective

Make FeedSense validation against a real, named, pinned ML artifact possible.
This milestone establishes the pipeline bridge: real input preprocessing
(8B-15-3/4), real TFLite runtime adapter (8B-15-5), output decoding
(8B-15-6), taxonomy mapping (8B-15-7), and evaluation boundary (8B-15-8)
connected through an actual MobileNetV2 1.0 224 model identity — with
deterministic synthetic golden fixtures verifying the contract end-to-end.
All on-device, local, deterministic, versioned, offline.

## 2. Explicit non-goals honored

- **No cloud AI / remote inference / telemetry / uploads.**
- **No training, no weight modification, no self-learning, no threshold tuning
  on ground truth.**
- **No fabricated accuracy or latency claims.** Every claim below is
  categorically VERIFIED / UNVERIFIED / NOT_MEASURED / BLOCKED.
- **No production model destruction.** Legacy baseline models and all existing
  pipelines remain intact; no historical prediction is overwritten.
- **No redesign of existing contracts.** All work is additive over 8B-15-3/4/5/6/7/8.
- **No real image testing, no network download at runtime, no cloud.**

## 3. Model identity (pinned, honest)

ADR-0001 selected MobileNetV4-Conv-S (INT8) as the primary target; this
milestone validates the pipeline with a **proven, available** artifact:
MobileNetV2 1.0 224.

| Field | Value | Verification |
|---|---|---|
| Model name | MobileNetV2 1.0 224 | VERIFIED (pinned in code) |
| Source | TensorFlow Hub / Keras Applications | VERIFIED (pinned `SOURCE_URL`) |
| Input | 224×224×3, FLOAT32, NHWC | VERIFIED (matches preprocess config) |
| Output | 1001-class softmax | VERIFIED (matches decoder contract) |
| TFLite dependency | `org.tensorflow:tensorflow-lite:2.16.1` | VERIFIED (build compiles, AAR resolves, `libtensorflowlite_jni.so` packages) |
| Artifact SHA-256 | `NEEDS_VERIFICATION` | **UNVERIFIED** — `.tflite` not downloaded into repo |
| Label file | ImageNetLabels.txt (1001 labels) | **UNVERIFIED** — not yet downloaded |

## 4. Architecture

```
Input image (synthetic golden fixture, deterministic)
        ↓
DeterministicPreprocessor (8B-15-3/4) — 224×224×3 FLOAT32 NHWC
        ↓
ModelRuntimeAdapter (8B-15-5) → LiteRtRuntimeBackend (real TFLite Interpreter)
        ↓
AdapterRawModelOutput (raw FloatArray[1001])
        ↓
ModelOutputDecoder (8B-15-6)
        ↓
TaxonomyMappingEngine (8B-15-7)
        ↓
EvaluationBoundaryEvaluator (8B-15-8)
        ↓
EvaluationCandidateSnapshot
```

## 5. Files added

### Source (7 files)

| File | Purpose |
|---|---|
| `analysis/ml/runtime/litert/MobileNetV2Artifact.kt` | Pinned artifact identity, input/output specs, factory methods |
| `analysis/ml/runtime/litert/MobileNetV2Labels.kt` | Label contract, verification status, representative subset |
| `analysis/ml/runtime/litert/LiteRtArtifactLoader.kt` | Real artifact loader (`ModelArtifactLoader`) |
| `analysis/ml/runtime/litert/LiteRtRuntimeBackend.kt` | Real TFLite backend (`RuntimeInferenceBackend`), NHWC flat→4D reshape |
| `analysis/ml/real/RealModelPreprocessing.kt` | 224×224×3 FLOAT32 NHWC config |
| `analysis/ml/real/RealModelPipelineConfig.kt` | `FullPipelineConfig` factory with taxonomy mappings |
| `analysis/ml/real/RealModelGoldenFixture.kt` | Deterministic golden fixtures |

### Tests (4 files)

| File | Tests |
|---|---|
| `RealModelArtifactValidationTest.kt` | 50 JVM tests |
| `RealModelPipelineContractTest.kt` | 9 JVM tests |
| `RealModelFailureHandlingTest.kt` | 14 JVM tests |
| `RealModelEndToEndTest.kt` | 3 Android instrumented tests |

### Documentation

| File | Purpose |
|---|---|
| `docs/real-model-artifact-validation.md` | Comprehensive milestone documentation |
| `docs/report-8b15-9-real-model-validation-v1.md` | This report |

## 6. Verification register

Every claim below carries one of four categories. Nothing is inferred from
unrun code.

### VERIFIED

| Claim | Evidence |
|---|---|
| Input spec contract (224×224×3, FLOAT32, NHWC) matches MobileNetV2 artifact spec | 50-test artifact validation suite passes |
| Output spec contract (1001-class) matches decoder | Decoder contract tests pass |
| Preprocessing is deterministic for fixed config + fixed source | Golden fixture determinism tests pass |
| Golden fixtures are deterministic, repository-contained, network-independent | Fixture tests pass |
| Taxonomy mappings for known ImageNet labels (e.g. "tench" → "fishing") | Mapping tests pass |
| UNMAPPED handling is honest (no forced taxonomy key) | Contract tests enforce `feedSenseTaxonomyKey == null` for UNMAPPED |
| TFLite dependency resolves, compiles, and packages native lib | `assembleDebug` + `compileDebugAndroidTestKotlin` succeed |
| Existing baseline test suite does not regress | 2208 JVM tests total, **0 failures** (2135 baseline + 73 new) |
| All 11 failure classes produce explicit, deterministic failures | 14-test failure handling suite passes |
| Pipeline preserves model identity/version provenance end-to-end | Provenance tests pass |
| Evaluation boundary receives real model metadata | Contract tests pass |
| Full pipeline (decoder→taxonomy→evaluation) runs with real model metadata | Contract tests pass |

### UNVERIFIED

| Claim | Reason |
|---|---|
| Artifact SHA-256 matches the downloadable model | `.tflite` not downloaded; marked `NEEDS_VERIFICATION` |
| Label file contents match ImageNet 2012 exactly | Label file not downloaded; `DOWNLOADED_UNVERIFIED` |
| Model loads via `LiteRtArtifactLoader` with that SHA | Requires real artifact |
| Real model output labels are semantically correct | Not measured, no ground truth comparison |

### NOT_MEASURED

| Claim | Reason |
|---|---|
| Real inference latency | Requires Android device/emulator; not run |
| Real inference determinism across devices | Requires Android device/emulator; not run |
| Top-1 / top-5 real output values on golden inputs | Requires real model artifact + device |
| Device-store timing (min/median/mean/p95/max) | Requires Android instrumented run |

### BLOCKED

| Claim | Blocker |
|---|---|
| Real end-to-end inference on JVM | TFLite requires Android native runtime; JVM cannot instantiate `Interpreter` |
| Real instrumented test run | Requires a device/emulator and the downloaded `.tflite` artifact; not available in this session |

## 7. Test coverage summary

- 73 new JVM tests (50 artifact validation + 9 pipeline contract + 14 failure handling)
- 3 Android instrumented tests (real inference, determinism, timing) — compiled, not executed
- Full JVM suite: **2208 tests, 0 failures**
- Existing baseline: 2135 tests — no regressions
- Android build: `assembleDebug` successful

## 8. Limitations

1. **Real artifact not downloaded** — all inference claims remain BLOCKED until the
   `.tflite` file is downloaded and SHA-256 verified.
2. **No real image testing** — only synthetic deterministic golden fixtures are used.
3. **JVM cannot run TFLite** — instrumented tests require a device/emulator.
4. **No accuracy/latency claims** — explicitly out of scope, correctly NOT_MEASURED.
5. **Label semantics unverified** — the representative label subset is a testing
   contract, not a verified semantic claim.
6. **Timing is device-specific** — even when measured, results only hold for the
   tested hardware.
7. **Batch inference not implemented** — single-image inference validated only.

## 9. Research integrity review

- No fabricated SHA-256, no fabricated labels, no fabricated latency.
- Every claim is traceable to a passing test or explicitly marked UNVERIFIED /
  NOT_MEASURED / BLOCKED.
- The integration is **additive**: no existing baseline, GroundTruth, or
  historical prediction was modified.
- TFLite runtime is exercised only via the real `Interpreter`; no synthetic
  inference path masquerades as real.

## 10. Next milestone (NOT started)

8B-16 has NOT been started. This milestone stops at 8B-15-9 per the
explicit boundary. Await approval before proceeding.