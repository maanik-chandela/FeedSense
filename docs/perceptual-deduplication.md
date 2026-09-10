# Perceptual Frame Deduplication & Visual Change Detection (8B-11)

Research-grade, standalone frame-efficiency layer: it decides,
per captured frame, whether the frame is *visually different
enough* from the current on-screen reference to justify
downstream processing.

Scope. This is an **efficiency/heuristic** layer, not an AI
model and not ground truth. It does not semantically understand
"What changed" (new reel, ad, caption, overlay…), and it is not
wired into `SessionRepository.buildFeedItem()` or the baseline
analysis. It is a standalone, versioned, fully-tested primitive
that a future scheduling layer (8B-12) can call with
`result.forwards == true`.

---

## 1. Why frame-deduplication exists

- **Repeated identical frames are noise.** Continuous frame
  capture of a mostly-static screen (a paused reel, a standing
  notification shade, a text page) produces long runs of
  near-identical images. Downstream OCR/classification would
  re-process the same content over and over, at battery, memory,
  and privacy cost for zero new information.
- **Repeated processing is also a privacy multiplier.** Every
  extra frame a classifier touches is another frame whose pixels
  pass through a model. Suppressing near-duplicates reduces that
  surface without touching frame content.
- **Capture pipelines produce duplicate frames naturally.**
  Buffered capture, network retries, and preview-like sampling
  all yield repeated frames that have no analytic value.

The goal is therefore: *forward the first instance of a visual
state, suppress repeats, and force a refresh on a static screen
so the pipeline never starves.*

## 2. Why a *perceptual* hash, not SHA-256

A cryptographic hash (SHA-256) has a useful property for
integrity: changing a single pixel changes every digest bit.
That is exactly the wrong property for deduplication:

| Property | SHA-256 | Perceptual (dHash/pHash) |
|---|---|---|
| One pixel changed | digest completely different | distance ~0 |
| Same photo, 2x resolution | different | distance 0 |
| Slight brightness shift | different | distance 0 |
| Reversed video | different | 64/64 bits differ |

Perceptual hashes map *visually similar* images to *similar*
values, so similarity is a small Hamming distance instead of an
exact-integer equality. This is tested explicitly
(`PerceptualHashTest.perceptualVsCryptographicHash_behaveOppositely`).

**Non-reversibility.** A `FrameHash` is a lossy summary (a few
dozen bits) of a frame. It contains no pixels and cannot be used
to reconstruct the screen. It is safe to report for research.

## 3. Algorithm: dHash (default) and pHash

- **dHash (`dhash-v1`):** box-average the frame to a
  `(hashSize+1) x hashSize` luminance grid, then emit one bit per
  horizontal gradient (`right > left`). Default 8x8 -> 64 bits.
  Cheap, robust to resolution changes. Uses box averaging
  (reduces aliasing) rather than nearest-neighbour sampling.
- **pHash (`phash-v1`):** box-average to a canonical 32x32 grid,
  take a separable 2D DCT, read the low-frequency `hashSize`
  block (DC excluded), threshold at the block median. Not the
  default; available for RQ4 (dHash vs pHash) experiments.

Both are deterministic: the same frame and same configuration
always produce the same `FrameHash`.

## 4. Versioning

- `FrameHashAlgorithm { DHASH, PHASH }` with per-algorithm
  versions `dhash-v1` / `phash-v1` and `hamming-v1` for the
  distance function.
- Every `FrameHash` and every `FrameDedupVersion.DEDUP_VERSION`
  (`dedup-v1`) value is attached to every result, so any
  experiment can be attributed to the exact algorithm/behaviour
  version that produced it.

## 5. Three-way decision semantics

| Distance `d` vs threshold `T` | Decision | Forwarded? |
|---|---|---|
| `d == 0` | `DUPLICATE` | no |
| `0 < d <= T` | `SIMILAR` | no |
| `d > T` | `UNIQUE` | **yes** |

The reject boundary is **inclusive** (`d == T` -> `SIMILAR`),
which is the conservative direction for an efficiency layer: a
frame on the boundary is *not* meaningfully different enough to
justify reprocessing.

## 6. Rolling reference semantics

The reference is the **last accepted frame**, not the first
frame:

- `UNIQUE` (forwarded) AND forced-forward both become the new
  reference.
- `DUPLICATE` / `SIMILAR` / `TIME_WINDOW_GATED` do **not**
  replace the reference.

Why rolling? A scene that drifts gradually (auto-playing reel
that changes slightly every frame) would be compared to the
session start and rejected forever under a fixed first-frame
reference. Rolling lets slow transitions propagate while still
suppressing true repeats. Verified by
`FrameDeduplicatorTest.rollingReference_tracksSlowTransitions`.

## 7. Time-window gating

`minimumFrameIntervalMs` rejects frames that arrive too soon
after the last accepted frame — *cheaply, without hashing* —
with reason `TIME_WINDOW_GATED`. Default `0` = pure
visual-similarity behaviour. This is a throttle, not a hash.

## 8. Forced-forward safety ceiling

`maximumForwardIntervalMs` (default 8000 ms) guarantees a static
screen still refreshes its evidence: when elapsed time since the
last accepted frame reaches the ceiling, the frame is **forced**
through as `UNIQUE` with `forcedForward=true` and reason
`FORCED_FORWARD`, even if visually identical to the reference.
Without this, a session that stays on one static screen would
process exactly one frame forever.

## 9. First frame

The first frame of a session has no reference and is always
`UNIQUE`/`FIRST_FRAME`, so it is eligible for downstream
processing. It also establishes the first reference.

## 10. Disabled pass-through

With `enabled = false` every frame is forwarded `UNIQUE`
(`DISABLED_PASSTHROUGH`) and **no hashing occurs**. This is the
safe "off" state: deduplication can never accidentally block
evidence.

## 11. Determinism

State transitions, decisions, reasons, and counters are
functions of {config, frames, injected clock}. The same inputs in
the same order produce the identical result. The clock is
injectable (`nowMs: () -> Long`) precisely so that the state
machine (interval gating, forced-forward) is testable
deterministically.

## 12. Memory and battery

- **Memory:** retained reference state is O(1): one hash, one
  timestamp, and counters — independent of session length.
  Verified by `FrameDeduplicatorTest.staticSequence_boundedState_andHighDedupRate`
  (10,000 frames -> 1 retained reference).
- **Per-frame cost:** one grayscale read + one hash + (when a
  reference exists) one XOR-popcount. No raw frame is retained.
- **Battery claim policy:** no battery/CPU improvement is claimed
  in this milestone. `EfficiencyBenchmark` produces *measured*
  hash times (methodology below) but battery-level claims would
  require controlled on-device population measurement, which is
  deferred.

## 13. Metrics (observed counts only)

`FrameDedupStats` (immutable snapshot, no raw data):

- `framesSeen` — every frame handed to the layer.
- `hashComputations` — hashes actually computed (first frame
  counts, gated/disabled frames do not).
- `framesAccepted` / `framesRejected` — forwarded / suppressed.
- `forcedForwardCount` — safety-ceiling refreshes.
- `hashDistanceSum` -> `averageHashDistance` (0 when nothing was
  compared).

Rates share one denominator (`framesSeen`):

```
deduplicationRate = framesRejected / framesSeen
forwardRate        = framesAccepted / framesSeen
```

By construction `deduplicationRate + forwardRate == 1.0` (every
seen frame is either forwarded or suppressed). These are
diagnostic counters, documented as such, not accuracy claims.

## 14. Privacy ordering and no raw persistence

- The hasher reads only a transient luminance matrix; neither the
  grayscale matrix nor any raw frame is persisted for hashing
  purposes.
- The only retained artifacts are non-reversible, lossy hashes
  (research-safe).
- The Android adapter (`BitmapFrameImage`) performs one native
  pixel read into a transient `IntArray`, reduces to grayscale in
  memory, and retains nothing.
- Reporting hashes + counters + decisions carries no OCR text, no
  pixels, and no frame paths.

## 15. Known limitations (documented, not hidden)

- **dHash sees horizontal gradients only.** Vertical change that
  preserves every horizontal ordering is invisible.
- **Luminance inversion** (dark/light mode flip) reverses every
  gradient -> maximum distance, so a dark-mode change is treated
  as a completely new frame (documented risk in the corpus).
- **Small UI elements** (a tiny indicator, a subtle badge) often
  stay inside the SIMILAR band and are suppressed. This is the
  intended trade-off of perceptual deduplication: efficiency in
  exchange for missing small deltas. Recorded explicitly as
  `ControlledFrameCorpusTest` documented risks, so the behaviour
  is intentional and visible rather than accidental.
- **Global brightness shifts** (+N everywhere) do not change any
  gradient and are treated as duplicates — which is desired for a
  brightness-normal frame stream.
- Correlated "noise" (sensor grain, compression) can inflate
  distances and trigger false `UNIQUE`s on otherwise-static
  content.

## 16. False-dedup risks

The dangerous failure mode of any deduplication layer is
**suppressing a genuinely new frame** (a real new reel, an
important error dialog) because it found the reference "similar
enough". Mitigations in this layer:

1. Conservative inclusive boundary (`d == T` rejects, never
   forwards).
2. Forced-forward ceiling (8 s) guarantees processing cannot
   starve on any static screen.
3. `enabled = false` pass-through is a full kill-switch.
4. No pixels are ever dropped from *capture* — dedup only gates
   what reaches downstream analysis; raw frames remain for review
   (consistent with the privacy layer).

Because this layer is not yet integrated, none of these
mitigations are claimed as "protection against missed events" —
they are design properties stated for the record.

## 17. Relation to the existing 7S / 8B-3 dedup layer

`analysis/dedup/` (Milestone 7S/8B-3) and `analysis/PerceptualHash.kt`
(7D) are the **existing production reuse layer** and are
untouched by 8B-11. This milestone adds a standalone, research
primitive with different semantics (rolling reference, decision
taxonomy, safety ceiling, versioning, explicit stats) so that the
research layer can evolve independently. Both layers follow the
"perceptual, not cryptographic" principle; they are independent
implementations (8B-11 documents its divergences, e.g.
box-averaging).

## 18. Benchmark methodology

`EfficiencyBenchmark.run(frame, iterations, warmup, algorithm,
hashSize)`:

- Warm-up iterations (default 20) discard JIT/GC warm-up.
- Measured iterations (default 200) time a single
  `PerceptualHasher.compute` via `System.nanoTime`.
- Report count, average, median, min, max (ms) + image
  dimensions + algorithm/version.

These numbers are **methodology, not claims**: they depend on
CPU/heap/thermal state and image size. Tests assert only plumbing
(`EfficiencyBenchmarkTest` — no wall-clock thresholds, to stay
flake-free on any machine). Device numbers belong to a later
on-device measurement pass (8B-12 contract).

## 19. Baseline comparison strategy

A future consumer can compare decision rates against the
baseline by running the layer in the CONFIDENTIAL side-by-side
mode described in §22: count how many frames the baseline would
have processed vs. how many this layer forwards, using the same
inputs, and report `deduplicationRate` / `forwardRate` with the
config and versions included. No accuracy deltas are implied
until such a run is performed.

## 20. Test strategy

All tests run pure-JVM with synthetic frames (`TestFrames`), no
Robolectric, no file I/O:

- **Hash:** determinism, size bounds (0/17 rejected), toHex
  stability, resolution invariance, 1x1 edge case, uniform-frame
  (no gradients), perceptual-vs-SHA-256 distinction.
- **Distance:** XOR popcount across word boundaries, algorithm /
  size mismatch rejection, word-length mismatch.
- **Deduplicator:** first-frame, identical->DUPLICATE,
  similar->rejected-without-reference-replacement, different->
  UNIQUE, `classifyDistance` boundary (inclusive), sequence
  `A A A B B C -> U D D U D D U`, forced-forward, time gating,
  disabled pass-through, reset, O(1) state over 10,000 frames,
  rolling reference, frame-id propagation, config validation.
  The hasher is pinned to `hashSize=1` so every frame maps to a
  controllable 1-bit hash (no mocks needed).
- **Stats:** empty zero-rates, denominator sharing
  (`rate+forward+rate == 1`) and `snapshot` correctness.
- **Corpus:** the controlled engineering corpus (below),
  including its ordering invariant.

## 21. The controlled engineering corpus

`ControlledFrameCorpusTest` builds a small deterministic
"screens" set **for engineering study, not for accuracy claims**:

- same-shot-identical -> `DUPLICATE`
- same content at 2 pixel densities -> `DUPLICATE` (hash
  invariant to resolution; asserted structurally)
- global brightness shift (non-clamping) -> `DUPLICATE`
- tiny single-cell indicator change -> `SIMILAR` (DOCUMENTED_RISK)
- minor 2x2 block motion -> `SIMILAR`
- ad interstitial (full black) -> `UNIQUE`
- dark/light inversion -> `UNIQUE` (DOCUMENTED_RISK)
- large caption band -> `UNIQUE`
- different reel -> `UNIQUE` (seed chosen so the distance provably
  exceeds the threshold)

Invariants asserted on the corpus:

- **Ordering:** every `SIMILAR` distance < every `UNIQUE` distance
  (the property that makes a threshold meaningful).
- **Coherence:** `SIMILAR` => distance in `(0, T]`;
  `UNIQUE` => distance `> T`.

Any future real-world accuracy measurement must be a separate,
population-based on-device study; this corpus is explicitly not
that.

## 22. 8B-12 (future) integration contract

The layer is deliberately un-integrated. A consumer such as the
8B-12 scheduling layer should:

1. Call `evaluate(frame, frameId)` per captured frame.
2. Process downstream **only when `result.forwards` is true**.
3. Respect the safety ceiling: never disable forced-forward for
   a production consumer.
4. Record the full `FrameSimilarityResult` (decision, reason,
   hashes, distance, threshold, algorithm, versions, timestamp,
   sequenceIndex) for audit.
5. Report `FrameDedupStats` snapshot alongside any efficiency
   study, with config + versions, and never claim accuracy or
   battery deltas without a controlled measurement.