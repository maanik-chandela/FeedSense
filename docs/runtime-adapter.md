# Runtime Adapter & Inference Boundary — 8B-15-5

## Overview

This milestone establishes the research-grade boundary between:

**validated preprocessed model input** → **on-device runtime** → **raw model output**

The output is: `ModelInput → RuntimeAdapter → RawModelOutput`

NOT: `ModelInput → recommendation/category decision`

## Selected Runtime

**LiteRT (TensorFlow Lite)** — as decided in ADR-0001 (8B-15-1).

| Property | Value |
| --- | --- |
| Runtime name | LiteRT |
| Execution backend | XNNPACK_CPU (default) |
| Platform | Android |
| Model format | TFLite |

**Note:** No real LiteRT runtime library is included in the build in this
milestone. The adapter boundary is tested with a TEST_ONLY stub. The real
runtime integration is a later milestone.

## Selected Model Artifact

**MobileNetV4-Conv-S** — as decided in ADR-0001 (8B-15-1).

| Property | Value |
| --- | --- |
| Model family | MobileNetV4-Conv-S |
| Architecture | Convolutional |
| Quantization target | INT8 |
| Artifact status | PENDING (not yet acquired) |

## Runtime Identity

Defined in `analysis/ml/repro/ReproRuntimeIdentity.kt` (8B-15-2).

The adapter reuses `ReproRuntimeIdentity` from the existing reproducibility
infrastructure. Key fields:
- `runtimeName`: LITERT, TFLITE, ONNX_RUNTIME, EXECUTORCH, OTHER, UNKNOWN
- `runtimeVersion`: semver string
- `executionBackend`: XNNPACK_CPU, GPU_DELEGATE, VENDOR_NPU, CPU, OTHER, UNKNOWN
- `supportedPlatform`: ANDROID, OTHER, UNKNOWN
- `modelFormat`: TFLITE, ONNX, EXECUTORCH, OTHER, UNKNOWN

## Artifact Identity

Defined in `analysis/ml/repro/ReproArtifactIdentity.kt` (8B-15-2).

The adapter reuses `ReproArtifactIdentity` from the existing reproducibility
infrastructure. Key fields:
- `artifactId`: declared identifier
- `format`: TFLITE, ONNX, EXECUTORCH, OTHER, UNKNOWN
- `sha256`: SHA-256 hex of exact bytes (only when AVAILABLE)
- `availability`: AVAILABLE, PENDING, NOT_AVAILABLE
- `validationStatus`: NOT_CHECKED, HASH_MATCHES, HASH_MISMATCH, NOT_APPLICABLE

## Compatibility Gate

The `CompatibilityGate` performs an explicit check before inference to ensure
all components of the ML pipeline are contract-compatible:

1. **Artifact availability**: artifact must be AVAILABLE (not PENDING/NOT_AVAILABLE)
2. **Artifact format vs runtime format**: must be compatible
3. **Runtime platform**: runtime name must not be UNKNOWN
4. **Preprocessing dimensions**: config width/height must match input dimensions
5. **Preprocessing datatype**: config tensor type must match input tensor type
6. **Preprocessing channels**: config channels must match input channels
7. **Input tensor structural integrity**: dimensions positive, float array size matches

The gate produces a `CompatibilityCheckResult` (Pass or Fail with specific failure).

## Input Contract

The adapter accepts `PreprocessedInput` from 8B-15-3. It does NOT:
- resize, crop, rotate, normalize, remove alpha, change channels
- accept raw screenshot bytes
- reinterpret pixel data

Those responsibilities belong to preprocessing (8B-15-3).

## Output Representation

`AdapterRawModelOutput` contains:
- Model artifact identity
- Runtime identity
- Preprocessing version
- Input signature
- Output tensor metadata (shape, datatype, value range)
- Raw output values (float arrays)
- Execution status
- Structured failure information
- Performance timing (load, warm-up, inference, validation)
- Deterministic hash of output values
- Inference timestamp

The raw output does NOT contain:
- Category classification
- Recommendation labels
- Topic detection
- Confidence thresholds
- User preference inference

## Error Taxonomy

Structured failure codes organized by phase:

| Phase | Codes |
| --- | --- |
| ARTIFACT_LOAD | ARTIFACT_MISSING, ARTIFACT_CORRUPT, ARTIFACT_IDENTITY_MISMATCH |
| RUNTIME_INIT | RUNTIME_UNAVAILABLE, RUNTIME_INIT_FAILURE, UNSUPPORTED_BACKEND |
| COMPATIBILITY_CHECK | INCOMPATIBLE_PREPROCESSING, WRONG_DIMENSIONS, WRONG_DATATYPE, WRONG_LAYOUT, WRONG_CHANNEL_COUNT, PREPROCESSING_VERSION_MISMATCH |
| INPUT_VALIDATION | INVALID_INPUT_TENSOR, INVALID_PROVENANCE |
| INFERENCE | INFERENCE_EXECUTION_FAILURE, INFERENCE_RESOURCE_EXHAUSTION, INFERENCE_NOT_READY |
| OUTPUT_VALIDATION | INVALID_OUTPUT_TENSOR, INVALID_OUTPUT_VALUES |
| RESOURCE_LIFECYCLE | LIFECYCLE_VIOLATION, RESOURCE_RELEASE_FAILURE |
| UNKNOWN | UNKNOWN |

Severity classification: FATAL (configuration error) vs RECOVERABLE (transient).

## Model Loading

The `ModelArtifactLoader` interface:
- Locates the expected local artifact
- Verifies artifact identity
- Verifies checksum when defined
- Loads the model using the selected runtime
- Exposes model metadata
- Fails deterministically when artifact is missing/corrupt/incompatible

The adapter NEVER downloads models from the internet.

## Local-Only Guarantee

The adapter operates without network connectivity:
- No HTTP requests
- No remote model endpoints
- No telemetry
- No analytics uploads
- No remote artifact downloads
- No cloud fallback

## Lifecycle

```
UNLOADED → LOADING → READY
              ↓
            FAILED → UNLOADED (release)

READY → RELEASING → UNLOADED
READY → INFERRING → READY
```

Rules:
- Inference only from READY
- Load only from UNLOADED or FAILED
- Release from any state
- Repeated initialization from READY is a no-op
- Inference after close fails cleanly

## Threading

Configurable via `RuntimeThreadingConfig`:
- `inferenceThreadCount`: threads for inference (null = runtime default)
- `initializationThreadCount`: threads for loading (null = runtime default)
- `allowMainThreadInference`: MUST be false for production
- `useDedicatedInferenceThread`: recommended for production

Default configuration: runtime defaults, no main thread inference.

## Determinism

The adapter computes a deterministic SHA-256 hash of the serialized output
tensor values. Same input always produces the same hash. The test backend
verifies this property through repeated inference.

The adapter uses an injectable `InferenceClock` for deterministic timing in
tests.

## Performance Instrumentation

Measures (via `RuntimeTimingCollector`):
- Model load duration
- Initialization duration
- Warm-up duration (if applicable)
- Inference duration
- Output validation duration
- Compatibility check duration
- Input validation duration

Does NOT yet establish performance claims.

## Privacy Provenance

The adapter preserves the evidence chain:
- Privacy sanitization version
- Policy mode
- Sanitization status
- Evidence identifier
- Session identifier
- Feed item identifier
- Preprocessing fingerprint

The adapter NEVER accepts:
- Arbitrary raw screenshot bytes
- Inputs without privacy metadata
- Unsourced evidence

## Resource Safety

- Resources released on close (idempotent)
- Repeated close is safe
- Inference after close fails cleanly
- Native resources encapsulated by the backend
- No unbounded caches

## Test Infrastructure

### TEST_ONLY Model Stub

`TestArtifactLoader` + `TestInferenceBackend` provide a deterministic,
fully-in-JVM inference backend for testing without any real runtime dependency.

Labeled TEST_ONLY — not a real model.

### Test Files

| Test | Coverage |
| --- | --- |
| RuntimeErrorTaxonomyTest | All failure codes, severity, structured failures |
| CompatibilityGateTest | Artifact, runtime, preprocessing, input checks |
| ModelArtifactLoaderTest | Loading, verification, lifecycle |
| ModelRuntimeAdapterTest | Init, inference, failure, lifecycle, output validation |
| RuntimeLifecycleTest | State machine transitions, edge cases |
| RuntimeDeterminismTest | Same input → same output, hash stability |
| GoldenAdapterIntegrationTest | Full pipeline: fixture → preprocess → adapter → output |
| RuntimeBaselineIsolationTest | No changes to FeedItem, evaluation, baseline |

## Known Limitations

1. **No real runtime**: LiteRT is not in the build. All inference is tested with
   a TEST_ONLY stub. Real runtime integration is a later milestone.
2. **No real model**: MobileNetV4-Conv-S artifact is PENDING. The adapter
   boundary is verified structurally.
3. **No warm-up**: The warm-up path exists but is not exercised without a real
   runtime.
4. **No GPU/NPU delegation**: Threading config supports it, but the test backend
   runs on JVM threads only.

## Files Added

### Main Source (`app/src/main/java/.../analysis/ml/runtime/`)

| File | Purpose |
| --- | --- |
| RuntimeErrorTaxonomy.kt | Structured failure codes, severity, phase, factory |
| CompatibilityGate.kt | Artifact/runtime/config/input compatibility checking |
| ModelArtifactLoader.kt | Model loading abstraction + TestArtifactLoader |
| AdapterRawModelOutput.kt | Raw output representation + OutputTensorValidator |
| ModelRuntimeAdapter.kt | Main adapter: init → infer → close lifecycle |
| RuntimeLifecycle.kt | Lifecycle state machine |
| RuntimeThreading.kt | Threading configuration |
| RuntimeInstrumentation.kt | Performance timing collection |
| RuntimeProvenance.kt | Privacy provenance tracking |
| TestInferenceBackend.kt | TEST_ONLY deterministic inference backend |

### Test Source (`app/src/test/java/.../analysis/ml/runtime/`)

| File | Purpose |
| --- | --- |
| RuntimeTestFixtures.kt | Shared test fixtures |
| RuntimeErrorTaxonomyTest.kt | Error taxonomy tests |
| CompatibilityGateTest.kt | Compatibility gate tests |
| ModelArtifactLoaderTest.kt | Loader tests |
| ModelRuntimeAdapterTest.kt | Adapter tests |
| RuntimeLifecycleTest.kt | Lifecycle tests |
| RuntimeDeterminismTest.kt | Determinism tests |
| GoldenAdapterIntegrationTest.kt | Golden integration tests |
| RuntimeBaselineIsolationTest.kt | Baseline isolation tests |
