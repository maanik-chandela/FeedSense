# Golden Fixtures & Preprocessing Reproducibility (8B-15-4)

This document is the research-facing reproducibility record for the
deterministic preprocessing golden fixture corpus. It answers: which
preprocessing version produced the pinned outputs, what the golden hashes
mean, how a researcher regenerates them, and what these fixtures do **not**
claim.

Scope: model **input** preprocessing only. Inference, output compatibility
and label mapping remain out of scope for 8B-15-4.

- Implementation: `analysis/ml/preprocess/golden/` (main) +
  `GoldenFixtureRegressionTest`, `GoldenPixelExactTest`,
  `GoldenCorpusRegressionTest`, `GoldenFixtureBaselineIsolationTest`,
  `GoldenPerformanceSanityTest` (JVM unit tests).
- Preprocessing contract (authoritative): `docs/preprocessing-contract.md`
  (8B-15-3) — this milestone records goldens **for** that contract and does
  not re-design it.

## 1. Corpus identity

| Field | Value | Meaning |
|---|---|---|
| Corpus version | `golden-corpus-v1` | Version of the fixture *definitions* |
| Preprocessing version | `preprocess-v2` | Output version the goldens pin |
| Corpus version C | independent of P | A preprocessing change and a fixture change are distinguishable |
| Fixture version | `1` (per fixture) | Per-fixture definition version |
| Hashing scheme | SHA-256 of `width\|height\|channels\|tensorType\|data` | See §5 |

The corpus version and the preprocessing version are deliberately different
strings: upgrading the preprocessing contract must never silently rewrite
fixtures, and adding fixtures must never pretend the preprocessing changed.

## 2. Fixture corpus contents

44 fixtures in 12 categories (see `GoldenFixtureDefinitions.ALL_FIXTURES`):

- **BASIC_IMAGE** — all-black / all-white / portrait / landscape / square
- **ORIENTATION** — 0°, 90°, 180°, 270° capture metadata normalization
- **DIMENSION** — 1×1, 2×2, odd 5×7, larger/smaller-than-target
- **PIXEL_PATTERN** — RGB channel isolation, high-contrast edges, known
  gradient, grayscale-like
- **CHANNEL_ORDER** — RGB vs BGR
- **ALPHA** — DISCARD (opaque/translucent), COMPOSITE, PRESERVE (RGBA)
- **RESIZE_CROP_PADDING** — RESIZE, CENTER_CROP, PAD (black bars), BILINEAR
- **NORMALIZATION** — scale-only and per-channel mean/std boundaries
- **DATATYPE_LAYOUT** — FLOAT32 / INT8 / UINT8, NHWC
- **PRIVACY** — accepted statuses (SANITIZED, NOT_REQUIRED) and rejected
  statuses (SANITIZATION_FAILED, SANITIZATION_UNAVAILABLE, UNKNOWN)
- **NEGATIVE** — documentation-only config → `CONFIG_NOT_RUNNABLE`
- **PERFORMANCE_SANITY** — bounded-latency smoke fixtures

Every frame is **synthetic and deterministic** (`GoldenFixtureLoader`):
no real screenshots, no network, no filesystem state, no Android runtime
dependency. The corpus is repository-contained and CI-safe.

## 3. Model artifact & runtime identity (honest)

8B-15-1 selected a shortlist only; no `.tflite` artifact exists in the repo.

- **Model artifact**: `MobilenetV4Conv` → `ReproArtifactIdentity` with
  `availability = PENDING`, `artifactVersion = <UNSPECIFIED>`, **no fabricated
  SHA-256** (an unavailable artifact carries no checksum by contract).
- **Runtime**: LiteRT (TensorFlow Lite). `ReproRuntimeIdentity` records
  `runtimeName`, runtime version `<UNSPECIFIED>` until the dependency lands.
- **Reference preprocessing** (`PreprocessingConfig.mobileNetV4ConvReference`):
  the 224×224×3, NHWC, FLOAT32 config that the shortlisted converter family
  expects. It is exercised in the baseline-isolation test but is NOT a claim
  that a real artifact exists.

The golden corpus therefore verifies the **deterministic input contract**
now and can be cross-checked against the real model artifact when one lands.

## 4. Expected-output policy

- Every success fixture pins, where applicable, the exact output **tensor
  SHA-256**, the preprocessed-input **fingerprint**, and the shape/datatype/
  layout/sanitization fields.
- Every failure fixture pins the **expected `PreprocessFailureCode`** (and
  optional message fragment): `PRIVACY_REJECTED`, `CONFIG_NOT_RUNNABLE`,
  `PREPROCESSING_FAILURE`, etc.
- The comparator (`GoldenComparator`) never auto-updates. A mismatch is a
  hard failure with structured diagnostics (`GoldenDiagnostics.summary()`):
  expected vs actual width/height/channels, tensor type, hashes, fingerprint
  and first-differing-element statistics.

## 5. Hash scheme (documented, not arbitrary)

`GoldenHasher.serializeTensorToBytes`:

```
| width  : int32 little-endian |
| height : int32 little-endian |
| channels: int32 little-endian |
| tensorType: 1 byte (FLOAT32=1, INT8=2, UINT8=3) |
| data: FLOAT32 -> IEEE-754 32-bit little-endian bits per element
       INT8/UINT8 -> raw quantized byte sequence
```

Then SHA-256 of the concatenation, hex-encoded lowercase. Properties:

- Same logical tensor ⇒ same hash (the model input path, *not* JVM memory
  layout, RegistryWeakHash never involved).
- Different datatype/layout ⇒ different hash (layout mistakes change bytes).
- Deterministic across runs and across machines for the same tensor.
- The hash covers the **model input tensor only**; the fingerprint
  (`fingerprint`) additionally binds config + source + privacy metadata.

## 6. Float tolerance policy

- Default `ComparisonPolicy.EXACT`: byte-identical tensor required.
- `FLOAT_TOLERANT` (tolerance `1e-6f`) exists for future use where
  floating-point arithmetic may legitimately differ across platforms. It is
  **documented per fixture**, never silently applied.
- `GoldenComparator.diffFloats` reports first differing index, total
  differing elements and max absolute difference for diagnostics.

## 7. Golden update policy

Pinned values are computed by a **temporary harness, reviewed, committed** and
then the harness is deleted. The live `GoldenHashRegistry` entries are the
single source of truth referenced by the fixtures.

- A deliberate preprocessing contract change: regenerate hashes with the
  harness, update the registry + fixture expectations, delete the harness,
  keep the 64-char SHA-256 format, and commit the reviewable diff.
- Any other hash change (i.e. an unintended byte-level drift) **must fail
  CI**. There is no `--update-goldens` flag.

The `every pinned golden hash resolves in the registry` test enforces that
the registry and the corpus cannot drift out of sync.

## 8. Platform limitations

- Goldens are JVM unit tests (no Robolectric/device): they verify the
  deterministic preprocessing implementation and its contract, not
  screen-capture or on-device runtime behavior.
- Bilinear interpolation results are pinned for the JVM maths library; a
  different vendor implementation may round differently and would require a
  documented `FLOAT_TOLERANT` override (not currently pinned).
- No claims are made about model **accuracy**, classification quality, or
  on-device inference latency. Performance sanity tests only bound the
  preprocessing path on the CI host.

## 9. Verification commands

```
./gradlew :app:testDebugUnitTest --tests "com.example.feedsense.analysis.ml.preprocess.golden.*"
./gradlew :app:testDebugUnitTest   # full JVM suite (regression against pinned goldens)
./gradlew :app:assembleDebug        # app still builds
```