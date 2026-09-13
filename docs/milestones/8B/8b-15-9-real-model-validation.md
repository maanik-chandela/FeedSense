# 8B-15-9 — Real Model Artifact Validation & End-to-End Inference

> Durable milestone record. Status: **ENGINEERING COMPLETE / RESEARCH
> NOT VALIDATED**.
>
> A companion, more detailed narrative report lives at
> `docs/report-8b15-9-real-model-validation-v1.md`, and the milestone
> documentation at `docs/real-model-artifact-validation.md`. This file is the
> durable cross-reference and status register.

## Objective

Establish a scientifically reproducible bridge from real image →
preprocessing → real model runtime → raw output → decoder → taxonomy mapping →
evaluation boundary, using a pinned, named MobileNetV2 artifact. This was the
first milestone in FeedSense to connect the 8B-15 pipeline contracts through
an actual, named, pinned model artifact with a real TFLite runtime path.

## Implementation

- **Artifact identity:** `analysis/ml/runtime/litert/MobileNetV2Artifact.kt` —
  `mobilenet_v2_100_224`, version `2`, input `[1, 224, 224, 3]` (FLOAT32,
  NHWC), output `[1, 1001]`.
- **Label contract:** `analysis/ml/runtime/litert/MobileNetV2Labels.kt` —
  1001 ImageNet labels, verification status `DOWNLOADED_UNVERIFIED`.
- **Artifact loader:** `analysis/ml/runtime/litert/LiteRtArtifactLoader.kt`.
- **Runtime backend:** `analysis/ml/runtime/litert/LiteRtRuntimeBackend.kt` —
  real TFLite `Interpreter`.
- **Real-model preprocessing:** `analysis/ml/real/RealModelPreprocessing.kt`.
- **Pipeline config:** `analysis/ml/real/RealModelPipelineConfig.kt`.
- **Golden fixtures:** `analysis/ml/real/RealModelGoldenFixture.kt`.
- **Dependency:** `org.tensorflow:tensorflow-lite:2.16.1` in
  `app/build.gradle.kts` (VERIFIED — build resolves AAR and packages native
  lib).

## Tests

- `RealModelArtifactValidationTest` — 50 JVM tests.
- `RealModelPipelineContractTest` — 9 JVM tests.
- `RealModelFailureHandlingTest` — 14 JVM tests.
- `RealModelEndToEndTest` — 3 Android instrumented tests (real inference,
  determinism, timing). **Compiled, not executed** (requires device +
  artifact).

## Verified

- Input/output specifications (224×224×3 FLOAT32 NHWC → 1001-class).
- Preprocessing determinism (fixed config → fixed output).
- Taxonomy mapping contracts for representative labels; UNMAPPED stays unmapped.
- TFLite dependency integration (build compiles, native lib packages).
- Provenance handling; regression safety (existing suite passes).
- Validation infrastructure (all 11 failure classes are explicit and
  deterministic).

## Unverified

- Artifact SHA-256 (`NEEDS_VERIFICATION`) — `.tflite` not downloaded into the
  repository.
- Actual label file contents (ImageNetLabels.txt not downloaded;
  `DOWNLOADED_UNVERIFIED`).

## Not Measured

- Real inference latency.
- Real output values on golden inputs.
- Device timing (min/median/mean/p95/max).
- Memory behavior.

## Blocked

- Real TFLite inference — requires the actual `.tflite` artifact (SHA-256
  verified) and a device/emulator. JVM cannot instantiate the TFLite
  `Interpreter`.

## Limitations

- No real images tested; only synthetic deterministic golden fixtures.
- No accuracy, latency, or model-quality claims of any kind.
- No production/device-side integration of the ML path; the pipeline is
  standalone research infrastructure.
- No batch inference.

## Next Prerequisite (8B-16 — NOT started)

- Download the `.tflite` artifact and verify its SHA-256.
- Download and verify the ImageNet label file.
- Execute the real artifact on a device/emulator.
- Only then can 8B-16 (empirical model evaluation with real annotated data)
  begin.

## Integrity note

The distinction between **ENGINEERING COMPLETE** (pipeline infrastructure
exists and tests pass) and **RESEARCH VALIDATED** (real artifact executed and
measured) is preserved throughout this record. The repository does not claim
MobileNetV2 demonstrated real prediction accuracy.