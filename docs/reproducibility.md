# Model Artifact & Reproducibility Identity (Milestone 8B-15-2)

> Status: **COMPLETE — reproducibility identity contract**
>
> This milestone establishes the **deterministic, auditable identity** of the
> experimental ML model/runtime selected in 8B-15-1, so a future research claim
> such as *"FeedSense achieved X using model Y"* can be mapped back to one exact,
> immutable, reproducible configuration.
>
> **Scope boundary (8B-15-2):** identity and provenance METADATA only. No
> inference pipeline, no input/output compatibility work (8B-15-3/8B-15-4), no
> training/fine-tuning, no cloud inference, no Room migration, and **no change to
> the baseline classifier, `SessionRepository`, `buildFeedItem`, the FeedSense
> taxonomy, or any production prediction field**. The selected model artifact is
> **not physically available yet** and is represented honestly as `PENDING`
> (`ARTIFACT_PENDING`).

---

## 1. Why exact artifact identity matters

FeedSense is intended to become research-grade. An experiment that reports "X
performance with model Y" is meaningless unless the exact *model*, *runtime*,
*preprocessing*, *quantization*, *output mapping*, and *privacy* configuration
can be re-established later. Subtle, silent changes — a re-converted artifact, a
newer runtime package, a different resize — would otherwise make historical
results no longer reproducible, or worse, silently change their meaning.

8B-15-2 makes the configuration **explicit, versioned, and immutable**, and ties
it to a **cryptographic artifact check** so that "the model" is never
ambiguous.

## 2. Model identity vs artifact identity

These two concepts are deliberately **separate**:

| Concept | Answers | Carried by |
| --- | --- | --- |
| **MODEL identity** | Which model, which version, where it originated, who publishes it, license | `analysis/ml/repro/ReproModelIdentity` |
| **ARTIFACT identity** | Which exact bytes; cryptographic checksum | `analysis/ml/repro/ReproArtifactIdentity` + SHA-256 |

Two artifacts that both claim model identity `mobilenet-v4-conv-s:v1` but differ
in bytes are **distinguished by their artifact SHA-256**. The
`identityVersion` field versions the identity *schema* itself; a change to it
means a new identity, never a silent mutation.

## 3. Selected model & runtime (from 8B-15-1)

The 8B-15-1 decision record (`ResearchDecisionCatalog`, ADR-0001) remains the
single source of truth. It selected a **shortlist**, not a final model — this
milestone preserves that distinction:

- **Runtime: LiteRT (TensorFlow Lite), D1 — SHORTLISTED.**
- **Model: MobileNetV4-Conv-S (INT8 target), A1 — SHORTLISTED (primary).**
- **Fallback: EfficientNet-Lite, A2 — CONDITIONAL** (packaged-weight license
  unverified in 8B-15-1; preserved as `UNKNOWN`).

No final selection is invented.

## 4. Artifact availability (honest)

8B-15-1 did **not** download or integrate any model; there is no physical
`.tflite` artifact in the repository. Per the milestone contract:

- The artifact slot is represented as `ArtifactAvailability.PENDING`
  (`ARTIFACT_PENDING`), with **no fabricated checksum**, byte size, or version.
- `modelVersion`, `artifactVersion`, `runtimeVersion` and related fields carry
  the explicit marker `REPRO_UNSPECIFIED = "<UNSPECIFIED>"`.
- Output label mapping carries `REPRO_UNRESOLVED_LABEL =
  "<UNRESOLVED_LABEL_SET>"` (resolved in 8B-15-4 against a real artifact).
- No validation is claimed: `ArtifactValidationStatus.NOT_APPLICABLE`.

## 5. SHA-256 implementation

`ArtifactSha256` (`analysis/ml/repro/ReproArtifactIdentity.kt`):

- Algorithm: **SHA-256**, hex-encoded, lowercase.
- `hash(bytes)` hashes the **exact artifact bytes**.
- `verify(bytes, hex)` compares in constant time.
- Rules honored: same bytes → same hash; different bytes → different hash
  (overwhelming probability); a recorded checksum is only valid when the
  artifact is `AVAILABLE`.
- Whether the hash applies before/after extraction or compression is the
  caller's responsibility and must be recorded in provenance; the contract
  never treats a ZIP hash as the extracted model hash without saying so.

## 6. Provenance & conversion

`ReproArtifactProvenance` (`analysis/ml/repro/ReproProvenance.kt`) is an ordered
chain of distinguishable transitions:

```
original source model
  -> downloaded artifact
  -> converted artifact   (format change, requires ReproConversionRecord)
  -> quantized artifact   (same-format; details in ReproQuantizationIdentity)
  -> deployment artifact
```

Format-changing steps (`CONVERTED`) **must** carry a `ReproConversionRecord`
(source/destination format, tool + version, configuration, operator
compatibility assumption, timestamps, source/destination hashes). The chain is
never collapsed into a single `modelVersion` field.

Since no artifact exists yet, the primary contract's provenance is a
source-only chain (`ORIGINAL` step pointing at `tensorflow/models`); the
fallback has no chain.

## 7. Quantization identity

`ReproQuantizationIdentity` records **depth** (`NONE`/`FP32`/`FP16`/`INT8`/
`INT4`/…), **method**, **scope**, calibration dataset identity, tool/version,
and source/resulting artifact hashes. It **does not** claim any accuracy or
quality — those belong to experimental milestones.

The 8B-15-1 contract records `depth = INT8`; the exact method/scope are
`UNKNOWN` until a real artifact is produced and converted.

## 8. Runtime identity

`ReproRuntimeIdentity` records runtime name/version, execution backend
(XNNPACK CPU, GPU delegate, vendor NPU), supported platform, model format, and
configuration. The runtime version is **independent of the developer machine**:
the eventual APK runtime version is pinned separately when the dependency
lands. Currently `runtimeVersion = "<UNSPECIFIED>"`.

## 9. Preprocessing identity

`ReproPreprocessingIdentity` is a **versioned metadata contract** capturing
resize method, input width/height, aspect-ratio/crop behavior, color format,
channel order, normalization (mean/std), alpha and orientation handling.
`preprocessingVersion` is the reproducibility component. Unset input dimensions
are explicitly `null` ("unverified"), never a fabricated size.

## 10. Output mapping identity

`ReproOutputMappingIdentity` captures model output labels, label ordering,
`feedSenseCategoryMappingVersion`, mapping mode, unknown/unmapped behavior, and
top-K interpretation. It **adapts** the model output to the existing FeedSense
taxonomy — it never redefines FeedSense categories.

## 11. Privacy identity

`ReproPrivacyIdentity` records the privacy/sanitization **version** and policy
mode (reusing the 8B-13 `RESEARCH`/`BALANCED`/`STRICT` vocabulary) and evidence
source type. It stores **metadata only** — never raw private content, OCR text,
screenshots, or coordinates.

## 12. Composite reproducibility identity

`ReproCompositeIdentity`
(`analysis/ml/repro/ReproCompositeIdentity.kt`) combines every component into
one deterministic identity:

```
ReproducibilityIdentity =
  ModelIdentity
+ ArtifactIdentity
+ RuntimeIdentity
+ QuantizationIdentity
+ PreprocessingIdentity
+ OutputMappingIdentity
+ PrivacyVersion
+ Provenance
```

Any of the following produce a **different** composite identity: different
artifacts/bytes, runtime version, preprocessing version/configuration, output
mapping, model version, policy mode, or provenance. Critically, the identity
never uses object memory addresses, `hashCode()`, random UUIDs, wall-clock
time, or unordered map serialization.

## 13. Canonical serialization

`ReproCanonicalSerializer` produces a **byte-deterministic** rendering with:

- stable field ordering (declared, flat key list — no unordered JSON map),
- stable enum representations (explicit `.label`, never ordinal/`name()`),
- stable numeric formatting (`toString()`, never locale-formatted),
- stable null handling (absent optionals serialize as explicit `null`),
- stable collection ordering (declared order).

`canonicalHash` is the SHA-256 of the canonical representation, so two
logically identical configurations yield byte-identical serialization and
identical hashes.

## 14. Immutability / versioning

Reproducibility metadata is **immutable once associated with a result**.
Changes create **new** identities:

| Change | Result |
| --- | --- |
| Model version changes | new `modelVersion` |
| Artifact bytes change | new hash → new artifact identity |
| Runtime changes | new runtime identity |
| Preprocessing changes | new `preprocessingVersion` |
| Output mapping changes | new `outputMappingVersion` |

## 15. Licensing

License identity discovered in 8B-15-1 is preserved:

- **MobileNetV4-Conv-S (A1):** `Apache-2.0` (code; `DOCUMENTED_BY_SOURCE`).
- **EfficientNet-Lite (A2):** `UNKNOWN` (packaged-weight license not verified in
  8B-15-1) — preserved honestly, never inferred.
- The exported `.tflite` weight artifact may carry a different license than the
  code (ADR-0001 risk); this must be verified when the artifact is acquired.

## 16. Artifact verification & security

Model artifacts are executable computational inputs and must be treated
carefully. The contract records the need for a **trusted source**, **checksum
verification**, artifact/format validation, provenance, and controlled
conversion. Because no artifact exists yet, **no validation is claimed**.

## 17. Scope boundary of the milestone

- The reproducibility layer is **standalone** (`analysis/ml/repro/`), wired only
  to the existing immutable 8B-15-1 catalog via `ReproContractFactory`.
- **No** model integrated, **no** dependency added, **no** Room migration.
  Persisting the metadata is deliberately deferred until a later milestone.
- **No** training/fine-tuning, cloud inference, or fabricated measurements.
- Baseline behavior, `SessionRepository`, `buildFeedItem`, `CategoryCatalog`,
  `FrameAnalysisWorker` and all production prediction fields are untouched.

## 18. Tests

`app/src/test/java/com/example/feedsense/analysis/ml/repro/` — 102 JVM tests
covering: model identity equality/difference, artifact SHA-256 (same → same,
changed → changed, missing → `PENDING`), runtime identity, quantization,
preprocessing, output mapping, privacy, provenance, composite identity
(changed artifact/runtime/preprocessing/mapping → different), canonical
determinism (repeated runs byte-identical), locale-independent numeric
formatting, and malformed-input rejection.

## 19. Unresolved questions

1. Exact artifact (`.tflite`, INT8) — **not available**; `ARTIFACT_PENDING`.
2. Exact model/artifact/runtime version — `REPRO_UNSPECIFIED` until acquisition.
3. Preprocessing configuration — 8B-15-3 (Input Compatibility).
4. Output label set & mapping — 8B-15-4 (Output Compatibility).
5. Quantization method/scope and calibration — resolved at conversion time.
6. EfficientNet-Lite packaged-weight license — still `UNKNOWN`.
7. The exported `.tflite` weight license (vs code license) — verify on
   acquisition.

These are **follow-ups** for later milestones, deliberately not implemented here.