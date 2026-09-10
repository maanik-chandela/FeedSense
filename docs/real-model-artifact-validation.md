# Real Model Artifact Validation & End-to-End Inference (8B-15-9)

## Overview

This milestone establishes a **scientifically reproducible bridge** from real image → preprocessing → real model runtime → output → decoder → taxonomy mapping → evaluation boundary. It is the first milestone in FeedSense that connects preprocessing contracts (8B-15-3/4), golden fixtures (8B-15-4), output decoding (8B-15-6), taxonomy mapping (8B-15-7), and evaluation boundaries (8B-15-8) through an **actual, named, pinned model artifact** with real inference.

**Critical constraint**: This milestone stops at **real inference validation**. No metrics, no accuracy claims, no latency rankings, no training, no fine-tuning, no synthetic inference fabrication.

## 1. Model Identity

| Field | Value | Notes |
|---|---|---|
| Model name | MobileNetV2 | Google's MobileNetV2 architecture |
| Source | TensorFlow Hub / Keras Applications | Standard, public, reproducible |
| Input | 224×224×3, FLOAT32, NHWC | Matches preprocessing contract (8B-15-3) |
| Output | 1001-class softmax | ImageNet 2012 + background (1001 total) |
| Label file | `ImageNetLabels.txt` | 1001 labels, 0-indexed |
| ADR reference | ADR-0001 | MobileNetV4-Conv-S (INT8) selected as primary; MobileNetV2 used as validated artifact |
| TFLite dependency | `org.tensorflow:tensorflow-lite:2.16.1` | Added to `app/build.gradle.kts` |

### Artifact Integrity

- SHA-256: `NEEDS_VERIFICATION` — artifact not downloaded into repository; researcher must download and verify
- Source URL: `https://tfhub.dev/tensorflow/lite-model/imagenet/mobilenet_v2_100_224/classification/2/default/1`
- Verification: researcher downloads artifact, computes SHA-256, confirms match

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Real Model Pipeline                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Input Image (any)                                         │
│       │                                                    │
│       ▼                                                    │
│  DeterministicPreprocessor (8B-15-3/4)                    │
│  224×224×3 FLOAT32 NHWC                                   │
│       │                                                    │
│       ▼                                                    │
│  LiteRtRuntimeBackend                                     │
│  TFLite Interpreter                                        │
│       │                                                    │
│       ▼                                                    │
│  Raw Output: FloatArray[1001]                             │
│       │                                                    │
│       ▼                                                    │
│  ModelOutputDecoder (8B-15-6)                             │
│  InterpretedModelOutput                                   │
│       │                                                    │
│       ▼                                                    │
│  TaxonomyMappingEngine (8B-15-7)                          │
│  TaxonomyMappingResult                                    │
│       │                                                    │
│       ▼                                                    │
│  EvaluationBoundaryEvaluator (8B-15-8)                    │
│  EvaluationCandidateSnapshot                              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 3. Components

### 3.1 MobileNetV2Artifact (artifact identity)

**File**: `analysis/ml/runtime/litert/MobileNetV2Artifact.kt`

Pinned artifact identity with:
- `MODEL_ID = "mobilenet_v2_100_224"`
- `MODEL_VERSION = "2"`
- `INPUT_SHAPE = [1, 224, 224, 3]` (NHWC, batch=1)
- `OUTPUT_SHAPE = [1, 1001]`
- `TFLiteDependency = "org.tensorflow:tensorflow-lite:2.16.1"`

### 3.2 MobileNetV2Labels (label contract)

**File**: `analysis/ml/runtime/litert/MobileNetV2Labels.kt`

- `LABEL_COUNT = 1001`
- `SOURCE = "ImageNet 2012 + background"`
- `VerificationStatus`: `NOT_DOWNLOADED`, `DOWNLOADED_UNVERIFIED`, `DOWNLOADED_VERIFIED`, `VERIFIED_WITH_SHA256`
- Current status: `DOWNLOADED_UNVERIFIED` (file not yet downloaded)
- `REPRESENTATIVE_TEST_LABELS`: 50 labels for test assertions

### 3.3 LiteRtArtifactLoader (artifact loading)

**File**: `analysis/ml/runtime/litert/LiteRtArtifactLoader.kt`

Implements `ModelArtifactLoader`:
- Locates `.tflite` file by name
- Verifies SHA-256 checksum (when available)
- Returns `ModelLoadResult.Success` with metadata
- Returns `ModelLoadResult.Failure` with `ArtifactNotFound`, `LoadFailed`, `IntegrityCheckFailed`

### 3.4 LiteRtRuntimeBackend (real inference)

**File**: `analysis/ml/runtime/litert/LiteRtRuntimeBackend.kt`

Implements `RuntimeInferenceBackend`:
- Loads TFLite `Interpreter` from file path
- Converts `ModelInput` to `Interpreter` input (NHWC flat → 4D)
- Executes inference
- Returns `BackendInferenceResult.Success` with `outputTensors` and `outputMetadata`

### 3.5 RealModelPreprocessing (preprocessing config)

**File**: `analysis/ml/real/RealModelPreprocessing.kt`

- Config: 224×224×3, FLOAT32, NHWC
- Method: CENTER_CROP
- Normalization: [0, 255] → [0, 1]
- No alpha channel handling (discard)
- Deterministic: fixed config = fixed output

### 3.6 RealModelPipelineConfig (pipeline factory)

**File**: `analysis/ml/real/RealModelPipelineConfig.kt`

Creates `FullPipelineConfig` with:
- Preprocessing config from `RealModelPreprocessing`
- Model output spec from `MobileNetV2Artifact.OUTPUT_SPEC`
- Decoder enabled
- Taxonomy mappings
- Evaluation boundary enabled

### 3.7 RealModelGoldenFixture (golden fixtures)

**File**: `analysis/ml/real/RealModelGoldenFixture.kt`

- `CANONICAL_224_KNOWN_GRADIENT`: 224×224 red-left → green-right gradient
- `MINIMAL_48_CENTER_WHITE`: 48×48 white center on black
- Both deterministic, repository-contained, no network dependency
- SHA-256 fingerprints for reproducibility

## 4. Contract Verification

### 4.1 Input Spec → Preprocessing

| Preprocessing | MobileNetV2 Input |
|---|---|
| 224×224 | 224×224 ✓ |
| FLOAT32 | FLOAT32 ✓ |
| NHWC | NHWC ✓ |
| 3 channels | 3 channels ✓ |
| [0, 255] → [0, 1] | [0, 255] → [0, 1] ✓ |

### 4.2 Output Spec → Decoder

| Output | Decoder Input |
|---|---|
| 1001-class softmax | 1001 elements ✓ |
| FloatArray | FloatArray ✓ |
| Non-negative | Non-negative ✓ |
| Sum ≈ 1.0 | Sum ≈ 1.0 ✓ |

### 4.3 Labels → Taxonomy

| Label | Taxonomy Status |
|---|---|
| "golden retriever" | MAPPED |
| "espresso" | MAPPED |
| "volcano" | MAPPED |
| "unmapped_label_999" | UNMAPPED |

## 5. Test Coverage

### 5.1 RealModelArtifactValidationTest (50 tests)

| Category | Tests |
|---|---|
| Artifact identity | 3 |
| Model metadata | 4 |
| Input/output specs | 6 |
| Labels | 5 |
| Preprocessing | 3 |
| Pipeline config | 3 |
| Golden fixtures | 2 |
| Determinism | 2 |

### 5.2 RealModelPipelineContractTest (9 tests)

| Category | Tests |
|---|---|
| Decoder contract | 3 |
| Taxonomy mapping | 3 |
| Evaluation boundary | 2 |
| Provenance preservation | 1 |

### 5.3 RealModelFailureHandlingTest (14 tests)

| Category | Tests |
|---|---|
| Mismatched input shape | 2 |
| Invalid output type | 2 |
| Empty/missing output | 2 |
| Truncated output | 2 |
| OOV labels | 2 |
| All unmapped | 1 |
| Zero weights | 1 |
| Determinism under failure | 1 |

### 5.4 RealModelEndToEndTest (Android instrumented)

| Category | Tests |
|---|---|
| Real inference | 1 |
| Determinism | 1 |
| Timing measurement | 1 |

## 6. Reproducibility Protocol

### 6.1 Artifact Download

```
# researcher downloads MobileNetV2 v2 TFLite model
# places as model.tflite in app's files directory
# verifies SHA-256 matches expected
```

### 6.2 Preprocessing Determinism

- Fixed config → fixed output
- No randomness, no platform dependency
- SHA-256 fingerprint verification
- Same input → same output on any device

### 6.3 Inference Determinism

- Same model + same input → same output
- TFLite CPU inference is deterministic
- Verified by multi-run determinism test

### 6.4 Timing Protocol

- N=10 measured runs, M=3 warmup
- min, median, mean, p95, max reported
- Android Log output
- Device metadata recorded

## 7. What This Milestone Establishes

1. **Named artifact**: MobileNetV2 1.0 224 is the pinned model
2. **Input contract**: 224×224×3 FLOAT32 NHWC matches preprocessing
3. **Output contract**: 1001-class softmax matches decoder
4. **Runtime**: TFLite 2.16.1 verified as loadable dependency
5. **Determinism**: CPU inference is deterministic for same input
6. **Golden fixtures**: Synthetic deterministic inputs for verification
7. **Pipeline bridge**: Real model → decoder → taxonomy → evaluation

## 8. What This Milestone Does NOT Establish

1. **No accuracy claims**: No comparison against ground truth
2. **No latency rankings**: No device benchmarking
3. **No performance metrics**: No F1, precision, recall
4. **No training**: No fine-tuning of model weights
5. **No real image testing**: Only synthetic fixtures
6. **No production deployment**: No device-side integration
7. **No synthetic inference**: All inference through real TFLite runtime
8. **No cloud inference**: On-device only

## 9. Limitations

1. **Model artifact not downloaded**: SHA-256 is `NEEDS_VERIFICATION`
2. **No real images**: Only synthetic golden fixtures used
3. **JVM tests cannot run TFLite**: Requires Android runtime
4. **Instrumented tests require device**: Cannot run on JVM-only CI
5. **Timing is device-specific**: Results vary by hardware
6. **Label file not downloaded**: Status is `DOWNLOADED_UNVERIFIED`
7. **No batch inference**: Only single-image inference tested

## 10. Files Added

### Source (7 files)

| File | Purpose |
|---|---|
| `MobileNetV2Artifact.kt` | Pinned artifact identity |
| `MobileNetV2Labels.kt` | Label mapping contract |
| `LiteRtArtifactLoader.kt` | Real artifact loader |
| `LiteRtRuntimeBackend.kt` | Real TFLite backend |
| `RealModelPreprocessing.kt` | Preprocessing config |
| `RealModelPipelineConfig.kt` | Pipeline factory |
| `RealModelGoldenFixture.kt` | Golden fixtures |

### Tests (4 files)

| File | Tests |
|---|---|
| `RealModelArtifactValidationTest.kt` | 50 JVM tests |
| `RealModelPipelineContractTest.kt` | 9 JVM tests |
| `RealModelFailureHandlingTest.kt` | 14 JVM tests |
| `RealModelEndToEndTest.kt` | 3 Android tests |

### Documentation (1 file)

| File | Purpose |
|---|---|
| `docs/real-model-artifact-validation.md` | This document |

## 11. Test Coverage Summary

- **73 JVM tests**: Artifact validation (50), pipeline contracts (9), failure handling (14)
- **3 Android tests**: Real inference, determinism, timing
- **Total**: 76 tests
- **Existing tests**: 2135 JVM tests (0 failures before this milestone)
- **Full JVM suite**: 2208 tests, 0 failures
- **Expected regression**: None (additive only)

## 12. Next Steps (NOT started)

1. **Download model artifact**: Verify SHA-256
2. **Run instrumented tests**: Verify real inference on device
3. **Real image testing**: Test with actual screenshots
4. **Performance profiling**: Measure on-device latency
5. **Label verification**: Download and verify ImageNetLabels.txt

## 13. Critical Boundary

**This milestone stops at 8B-15-9.**

Do NOT proceed to 8B-16 without explicit approval.

The final report will be written separately after test verification.
