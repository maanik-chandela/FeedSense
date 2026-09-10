# Preprocessing Contract (8B-15-3)

Deterministic, versioned, privacy-bounded conversion of
sanitation-approved frame evidence into model-ready tensor input.

Scope: this document is the researched contract for the selected
model family. It does **not** integrate inference (8B-15-4) and
does not modify the baseline.

---

## 1. Pipeline order (contract §3)

Given a privacy-approved `PreprocessingEvidence`, the pipeline runs
in a fixed, deterministic order (implemented in
`DeterministicPreprocessor`):

1. **Input validation** — structural + privacy gate
   (`InputValidation`). Malformed evidence fails explicitly; it is
   never resized into a plausible-looking image.
2. **Configuration gate** — a runnable config requires verified
   dimensions. `UNKNOWN/UNKNOWN` documentation-only configs fail
   with `CONFIG_NOT_RUNNABLE` instead of guessing.
3. **Orientation** (`OrientationTransform.orient`, §6).
4. **Aspect-ratio spatial stage** (`SpatialTransform.applyAspectPolicy`,
   §7–§9).
5. **Color + alpha** (`PixelConversionStage`, §10–§11).
6. **Normalization** (§12): scale to [0,1] then optional
   per-channel mean/std.
7. **Quantization + layout** (§14–§15): optional INT8/UINT8,
   NHWC channel-interleaved.
8. **Fingerprint + provenance** (`PreprocessingFingerprint`,
   `PreprocessedInput`).

There is **no** training augmentation anywhere in this path: this is
inference preprocessing only.

## 2. Privacy boundary (contract §4)

- Preprocessing consumes **only** `PreprocessingEvidence` built by
  `PreprocessingEvidenceFactory.approve`.
- `PrivacySanitizationStatus.SANITIZED / PARTIALLY_SANITIZED /
  NOT_REQUIRED` are accepted; `SANITIZATION_FAILED /
  SANITIZATION_UNAVAILABLE / UNKNOWN` are rejected at construction
  (explicit `IllegalArgumentException`, never silent continuation).
- No restore of masked pixels is possible: the raw frame is never
  visible in this layer.
- The privacy contract (sanitization version, policy mode, status)
  is **stamped** into `PreprocessedInput` and the fingerprint, so a
  tensor is always traceable to its privacy provenance without
  storing content.

## 3. Model input contract (contract §2, §6, §8–§15)

Reference contract used by the 8B-15-3 milestone for the selected
MobileNetV4-Conv family (LiteRT/TFLite, XNNPACK CPU, Android;

see `docs/ml-model-selection.md`, `docs/reproducibility.md`):

| Property        | Value                                              |
| --------------- | -------------------------------------------------- |
| width × height  | 224 × 224 (verified family reference)              |
| channels        | 3 (RGB)                                            |
| layout          | NHWC                                               |
| normalization   | scale [0,1] via 1/255, then ImageNet mean/std      |
| mean            | 0.485, 0.456, 0.406                                |
| std             | 0.229, 0.224, 0.225                                |
| tensor type     | float32 reference; INT8 is the 8B-15-1 target      |
| orientation     | `NORMALIZE_TO_0` (DEG_0/90/180/270 → upright)      |
| aspect-ratio    | `CENTER_CROP` for feed frames (default)            |
| resize          | bilinear (reference default)                       |
| alpha           | DISCARD (opaque screen captures)                   |
| padding         | NO_PADDING (crop path)                             |
| batch           | 1                                                  |
| version         | `preprocess-v2`                                    |

The config is expressed by `PreprocessingConfig.mobileNetV4ConvReference()`
and validated at construction by `PreprocessingConfigValidator`.

### Fields that are honest UNKNOWN (contract §16 rules)

The exact values below are **not** fabricated: they require the
actual `.tflite` artifact (currently `PENDING`, 8B-15-2).

| Value                        | Status | Resolution needed                                   |
| ---------------------------- | ------ | --------------------------------------------------- |
| real artifact SHA-256        | UNKNOWN| acquire artifact; record content hash                 |
| exact INT8 input scale/zeroPoint | UNKNOWN | read quantization parameters from the artifact  |
| exact input tensor type      | UNKNOWN| read input tensor from artifact metadata             |
| dynamic vs static dimensions | UNKNOWN| read tensor signature from artifact                  |
| fine-tuned normalization     | UNKNOWN| pin against the artifact's training preprocessing     |

## 4. Aspect-ratio and spatial policy (contract §7–§9)

`AspectRatioPolicy`:

- `STRETCH` — direct resize (distorts; used only when distorting
  is the explicit intent).
- `CENTER_CROP` — crop a window of target aspect, anchored by
  `CropAnchor` (CENTER/TOP/BOTTOM), then resize. For vertical feed
  frames, TOP/BOTTOM anchors preserve caption/subtitle regions;
  CENTER is the default.
- `PAD` — fit inside target aspect without distortion and pad to
  `paddingValue` (opaque required; validator-enforced).
- `RESIZE` — pure resize, used when aspect ratios already match.

Coordinate conventions (fixed, documented, tested):

- NEAREST: `sx = ox * srcW / dstW` (integer floor).
- BILINEAR: half-pixel centers `sx = (ox + 0.5) * srcW / dstW - 0.5`
  — matches the LiteRT/TFLite `RESIZE_BILINEAR` default
  (align_corners=false), i.e. the convention of the target runtime.

A real bug was fixed here (see `git log` for the diff): the
`sourceAspectTaller` comparison used the wrong inequality so
`CENTER_CROP` never cropped and silently stretched. The current
form is cross-multiplication (`srcW * targetH < targetW * srcH`),
verified by pixel tests.

## 5. Color, alpha, datatype, layout, batch (contract §10–§15)

- Channel extraction is explicit (Android ARGB is never treated as
  the model layout): RGB/BGR/RGBA/GRAYSCALE orders read
  `a,r,g,b` and reorder deterministically.
- `AlphaPolicy`: `DISCARD` (default, opaque captures), `COMPOSITE`
  over a configured background (default opaque white),
  `PRESERVE` (RGBA only). DISCARD cannot combine with RGBA
  (validator-enforced).
- `PreprocessingTensorType` FLOAT32/INT8/UINT8. Quantization is
  `q = round(sample / scale) + zeroPoint`, clamped to the byte
  range; `floats` always carry the (de)quantized values so
  downstream code never interprets raw bytes. FLOAT16 is a
  documented future type and is not silently substituted.
- Layout is NHWC only; NCHW would require a new preprocessing
  version (validator-enforced). Batch size 1 only.
- Quantized inputs require an explicit positive `scale` and finite
  `zeroPoint` — the mapping is never assumed.

## 6. Canonical identity and versioning (contract §16–§17)

- Every config is immutable and versioned (`PreprocessingVersion.V2`).
  The `canonicalKey` is a byte-stable rendering of **all** contract
  fields (dimensions, policies, interpolation, orientation, color,
  order, alpha, tensor type, `scale`, `zeroPoint`, `mean`, `std`,
  layout, batch, `paddingValue`, `cropAnchor`). Any change produces
  a different key — a silent config drift detector.
- `PreprocessingConfigSerializer` renders the full config for
  export (`serialize`) and its SHA-256 (`sha256Hex`).
- Compatibility is checked in two layers
  (`ModelCompatibilityValidator`):
  - **A. Declared-contract check** — config vs an 8B-14
    `ModelMetadata.inputSpec`. A mismatch is `INCOMPATIBLE` with
    `ERROR` issues (dimensions, channels, pixel format, tensor type,
    normalization, layout). No silent adaptation.
  - **B. Reproduction check** — config vs the 8B-15-2
    `ReproArtifactIdentity` + `ReproRuntimeIdentity` + quantization.
    Verifiable items (runtime platform, runtime format, quantization
    depth) are `ERROR` when mismatched; artifact-dependent items are
    `UNVERIFIED` while the artifact is PENDING — never claimed as
    compatible.
- `PreprocessingMetadata` pairs a config with its artifact/runtime/
  model identity for research reproducibility and is export-safe
  (identifiers and contract labels only; no pixels, no OCR, no
  private coordinates).

## 7. Determinism (contract §18) and edge cases (contract §19)

Proof obligations (all covered by unit tests):

- identical source pixels + identical config + identical
  orientation + identical privacy version ⇒ byte-identical tensor and
  fingerprint;
- no dependence on time, locale, timezone, randomness, or unordered
  collections;
- the evidence frame buffer is never mutated;
- output tensors never alias the frame buffer.

Edge cases executed in tests: 1×1 frames, already-at-target frames,
odd dimensions, portrait / landscape, 90/180/270 capture
orientation, all-black / all-white, translucent sources (alpha
discard policy).

## 8. Baseline isolation (contract §22)

- The preprocess package does not reference `FeedItem`,
  `GroundTruth`, `AiPredictionRecord`, taxonomy or evaluation
  records (source-level regression test).
- Baseline records construct/compare identically before and after
  preprocessing work.
- No production integration: the config-driven boundary is ready to
  drive a future runtime (8B-15-4) but nothing executes inference,
  and `FrameAnalysisWorker` / session / capture paths are untouched.

## 9. Artifact-defined vs project-defined vs unknown (summary)

| Origin     | Items                                                                 |
| ---------- | --------------------------------------------------------------------- |
| Project-defined | privacy boundary, pipeline order, orientation policy, aspect policy, alpha policy, layout NHWC, batch 1, determinism, fingerprinting, versioning |
| Verified reference | 224×224, RGB, scale 1/255, ImageNet mean/std (authoritative family sources) |
| UNKNOWN until artifact | real SHA-256, INT8 scale/zeroPoint, input tensor type, dynamic dims, fine-tuned normalization |

## 10. Tests

- `PreprocessingPixelTest` — orientation, resize (nearest/bilinear),
  crop + anchors, padding, color orders, alpha, normalization,
  quantization, layout, final shape.
- `PreprocessingConfigTest` — config validation, versioning,
  canonical-key sensitivity, serialization determinism,
  metadata determinism.
- `PreprocessingCompatibilityTest` — declared-contract checker and
  reproduction-contract checker (COMPATIBLE / INCOMPATIBLE /
  UNVERIFIED).
- `PreprocessingPipelineTest` — end-to-end determinism, shape,
  fingerprint stability, privacy stamping, `CONFIG_NOT_RUNNABLE`,
  rejection paths, edge-case battery.
- `PreprocessingBaselineIsolationTest` — frame non-mutation, tensor
  snapshot isolation, baseline record stability, source-level
  isolation.

Run with the project JDK (Android Studio JBR):

    JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
      ./gradlew :app:testDebugUnitTest --offline

## 11. Limitations / next milestone

- The INT8 input mapping and exact artifact identity remain UNKNOWN
  until the `.tflite` is acquired (8B-15-2 PENDING); the reference
  config and the `UNVERIFIED` compatibility items reflect that
  honestly.
- `preprocess-v2` is authored against the selected family. If a
  future artifact demands different normalization or a different
  input tensor type, it must be a new version (`preprocess-v3`),
  never a silent edit.
- Inference, output compatibility and label mapping are 8B-15-4 and
  are deliberately not started here.