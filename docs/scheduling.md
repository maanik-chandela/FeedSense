# Adaptive Frame Sampling & Inference Scheduling (8B-12)

Standalone, research-grade scheduling layer: it decides **which
deduplicated candidate frames deserve an expensive inference
analysis**, deferring the rest until they actually matter. It is
purely an **orchestration / efficiency** layer. It does not retrain
or change any AI, does not modify FeedItem semantics, and does not
touch `SessionRepository.buildFeedItem()`, `FrameAnalysisWorker`,
the category taxonomy, or the Room schema.

The layer answers exactly one question (spec §4):

> Given the current candidate frame and everything the app knows
> about recent analysis, should FeedSense spend expensive
> computation on this frame?

---

## 1. Why scheduling exists

- **Inference is the most expensive step.** After 8B-11 has
  suppressed near-duplicates, the remaining candidates are still
  far more numerous than anything an always-on classifier needs.
  Every candidate carries OCR / model cost plus a privacy surface.
- **Patterns matter, not raw volume.** A static screen needs
  coverage *rarely* (change is low); a rapid scroll changes
  constantly and needs coverage *often*. A fixed cadence cannot
  express both cheaply.
- **No frame may starve.** Even the most static screen must be
  re-analyzed within a bounded ceiling (`maximumAnalysisIntervalMs`),
  otherwise a slow real-world change (a reel ending, content
  rotating) is never picked up.

The goal is therefore: *sample adaptively — fast when the screen
visibly changes, slow and safe when it does not, and never beyond
a starvation ceiling.*

## 2. Inputs and outputs

Input (`SamplingContext`) is **metadata only**, never pixels:

| field | meaning |
| --- | --- |
| `timestampMs` | capture timestamp (>= 0 in normal operation) |
| `similarityResult` | a structured 8B-11 `FrameSimilarityResult` (distance/decision/version), or null when the pipeline could not dedupe this frame |
| `interactionActive` | user is actively involved (touch/scroll/fling) — a scheduling hint, not a label |
| `contentTransitionSuspected` | an explicit suspicion flag from the pipeline, if any |
| `sectorState` | informational sector descriptor; never used for decisions |

The scheduler **never hashes and never inspects content**. 8B-11
owns perceptual similarity; 8B-12 only consumes `visualChangeDistance`
and the structured result. Raw screenshots or OCR text are never
passed to the scheduler.

Output (`SamplingDecision`) is fully attributable:

| field | meaning |
| --- | --- |
| `action` | `ANALYZE` / `FORCE_ANALYZE` / `SKIP` |
| `reason` | the single reason that explain the action (see §7) |
| `candidateIndex` / `analysisIndex` | positional provenance |
| `elapsedSinceLastAnalysisMs` | captured **before** any state mutation |
| `similarityDistance` / `decision` / `algorithmVersion` | the 8B-11 evidence passed through unchanged |
| `effectiveIntervalMs` | the adaptive interval that applied |
| `forced` | whether this analysis was a safety-ceiling action |
| `algorithmVersion` / `configVersion` | both `sampling-v1` |

## 3. The intervals

- `minimumAnalysisIntervalMs` (default 1000): the hard gate. No
  analysis fires sooner than this, even for the strongest signal.
  The gate is **inclusive** (`elapsed >= min` is eligible;
  `elapsed < min` defers).
- `maximumAnalysisIntervalMs` (default 8000): the safety ceiling,
  also **inclusive**. When `elapsed >= max`, the scheduler
  **forces** an analysis (`FORCE_ANALYZE` / `MAX_INTERVAL`) so a
  frame can never starve. On a static screen, this is what keeps
  coverage alive.
- `adaptiveIntervalMs`: the target cadence between the two,
  `min + (max - min) * (1 - pressure)`, where pressure is high
  visual change (see §4). High change pulls the cadence toward
  `min`; static content pushes it toward `max` (never beyond).

## 4. Visual-change handling (pressure)

The scheduler builds a bounded rolling window (default 8) of the
most recent 8B-11 distances and derives a single scalar "change
pressure" in [0, 1]:

- `pressure = 0` when the rolling average is at or below
  `staticContentDistance` (4) — the screen is static;
- `pressure = 1` when the average is at or above
  `highChangeDistance` (8) — the screen is clearly churning;
- linear between the two bounds.

The average is recomputed with the *current* candidate's distance
included, deterministically, before each decision. Because the
window only stores 8B-11 magnitudes, a single outlier barely moves
pressure; a sustained change moves it quickly.

When pressure crosses `highChangePressureThreshold` (0.5), a
cadence analysis is labeled **`VISUAL_CHANGE`**; otherwise it is
**`SCHEDULED_SAMPLE`**. The label does not change the action — it
attributes why the cadence fired.

## 5. Content transitions

A single-frame distance large enough to be a content break counts
as a transition. The scheduler treats `distance >= transitionDistance`
(12) — or an explicit `contentTransitionSuspected` flag — as a
transition signal, but **only after the minimum gate has elapsed**.
Transitions are exactly the moments (new reel, ad interstitial,
comments modal, overlay) where an analysis is most informative, so
they preempt the adaptive cadence. One frame crossing 12 is not
"high change" pressure — that needs a *sustained* average — so the
two stems are genuinely different evidence.

## 6. Interaction signals

When `interactionActive` is set and the minimum gate has elapsed,
the scheduler analyzes (`INTERACTION_SIGNAL`, same priority as a
transition). The interaction flag is a pure scheduling hint — it is
not stored, not written to any record, and has no truth value.

## 7. Decision logic (deterministic, in order)

1. **Disabled** config → `ANALYZE` (`DISABLED_PASSTHROUGH`). The
   `BASELINE` preset reproduces Experiment A (analyze everything).
2. **Invalid/negative timestamp** → `ANALYZE`
   (`UNAVAILABLE_FALLBACK`, forced). We cannot reason about
   intervals, so evidence is never silently discarded.
3. **First frame** → `ANALYZE` (`FIRST_FRAME`).
4. `elapsed >= max` → `FORCE_ANALYZE` (`MAX_INTERVAL`, forced).
5. `elapsed < min` → `SKIP` (`MIN_INTERVAL`, deferred).
6. Transition signal → `ANALYZE` (`TRANSITION_SIGNAL`).
7. Interaction signal → `ANALYZE` (`INTERACTION_SIGNAL`).
8. `elapsed >= adaptive` → `ANALYZE` (`VISUAL_CHANGE` if
   pressure >= 0.5 else `SCHEDULED_SAMPLE`).
9. Otherwise → `SKIP` (`STATIC_CONTENT`).

Steps 1-2 are listed for completeness; steps 3-5 are the
gate/ceiling structure; steps 6-9 are the adaptive core. The
order is fixed and fully deterministic — identical inputs produce
identical decisions on every run (verified by tests).

## 8. Reasons (the full vocabulary)

`FIRST_FRAME` · `MIN_INTERVAL` · `MAX_INTERVAL` ·
`VISUAL_CHANGE` · `STATIC_CONTENT` · `TRANSITION_SIGNAL` ·
`INTERACTION_SIGNAL` · `SCHEDULED_SAMPLE` ·
`DISABLED_PASSTHROUGH` · `UNAVAILABLE_FALLBACK`.

There is deliberately **no `DEFER` action**: `SKIP` +
`MIN_INTERVAL` carries the full deferral semantics, keeping the
action vocabulary minimal. Skipped candidates never update
`lastAnalyzedAtMs`, so deferral is naturally re-evaluated at the
next candidate.

## 9. Configuration

`SamplingConfig` (validated at construction):

| field | default | bound |
| --- | --- | --- |
| `enabled` | `true` | — |
| `minimumAnalysisIntervalMs` | 1000 | >= 0 |
| `maximumAnalysisIntervalMs` | 8000 | >= min |
| `staticContentDistance` | 4 | >= 0 |
| `highChangeDistance` | 8 | >= static |
| `transitionDistance` | 12 | >= static |
| `highChangePressureThreshold` | 0.5 | [0, 1] |
| `rollingWindowSize` | 8 | 1..64 |
| `configVersion` | `sampling-v1` | fixed |

Presets: `DEFAULT`; `BASELINE` (`enabled=false` → the pass-through
control of Experiment A); `fixed(intervalMs)` (`min == max`, so
cadence samples coincide with the ceiling — the interval is exact
and every sample is a safety-ceiling action, a documented
coincidence); `COVERAGE_ONLY` (`min = max = 8000`, analysis every
8s regardless of content).

## 10. versioning

The entire contract is version-stamped. `SamplingVersion` is
`sampling-v1`; every `SamplingDecision` carries
`algorithmVersion` and `configVersion`, and every
`SamplingConfig` carries `configVersion`. A future `sampling-v2`
can change any judgment rule without ambiguity about which rule
produced a stored decision.

## 11. State & memory

The scheduler holds only: the last analysis timestamp, two
monotone counters (candidate / analysis index), five analysis
counters, and a bounded `LongArray` of size `rollingWindowSize`
carrying recent 8B-11 distances. That is **O(window)** regardless
of how long the session runs (`retainedWindowSize() <=
retainedWindowCapacity()`, asserted by tests). `reset()` clears
everything; two schedulers are fully session-isolated. All methods
mutating/reading the schedule are `@Synchronized`.

## 12. Privacy boundary

The scheduling layer strengthens the privacy posture rather than
weakening it: - the scheduler itself holds no pixels, no OCR text,
no raw paths, no sector content (`sectorState` is informational and
unused); - sampling reduces how often frames pass through expensive
models (a privacy multiplier, per the 8B-10 policy ordering); - the
rolling window stores lossy, non-reversible 8B-11 distances only.

## 13. Baseline comparison & the Experiments

`SamplingStats` is a diagnostic mirror. It records observed counts
only (`candidateCount`, `analyzedCount`, `skippedCount`,
`deferredCount`, `forcedAnalysisCount`, `transitionAnalysisCount`,
`interactionAnalysisCount`) and derives `samplingRatio =
analyzedCount / candidateCount`. The ratio is a **diagnostic**, not
an accuracy number and not a battery claim.

| experiment | config | expectation |
| --- | --- | --- |
| A | `BASELINE` (disabled) | every candidate analyzed |
| B | `DEFAULT` static screen | ceiling-forced, rare cadence |
| C | `DEFAULT` sustained change | VISUAL_CHANGE cadence near `min` |
| D | `COVERAGE_ONLY` | pure interval cadence, transitions ignored |

Hypotheses (controlled, on-device, logged with the same
stats schema): **H1** static screens analyze far less than
`COVERAGE_ONLY`; **H2** sustained change keeps the cadence at
`min`; **H3** transitions capture content breaks faster than the
byte-revolving cadence would; **H4** `samplingRatio` is stable in
production (does not drift silently).

## 14. Limitations

- **Not accuracy.** The scheduler labels which frames are worth
  *analyzing*; it says nothing about whether the *analysis* is
  correct. `samplingRatio` is a diagnostic, and no battery or CPU
  saving is claimed without a controlled on-device population
  measurement.
- **No salience understanding.** Content with very small visual
  deltas that `dHash` also misses (small icons, caption changes)
  may not change the pressure scalar enough to mobilize the
  cadence — mitigated by the ceiling, which guarantees coverage.
- **Synthetic validation.** The benchmark corpus is synthetic and
  asserts problem *shape* (static < moving < rapid), not real-user
  behaviour.

## 15. The synthetic benchmark

`SamplingBenchmark` drives eleven deterministic scenarios (400 ms
cadence, 30 s): static-screen, static-reel, moving-video,
rapid-scrolling, new-reel, advertisement, comments, modal, overlay,
slow-transition, rapid-transition — plus a fixed-cadence variant.
It is self-checking infrastructure only. The invariant that static
screens analyze less than moving video, that rapid scrolling hits
the `min` cadence, that the static screen still uses the ceiling,
and that rapid scrolling never needs forcing are all asserted by
tests.