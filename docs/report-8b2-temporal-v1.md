# Milestone 8B-2 Report — Temporal Evidence Fusion & Multi-Frame Content Understanding (TEMPORAL_V1)

> Status: **COMPLETE**
> Builds directly on 8B-1 (`local-fusion-v1`). This milestone introduces
> deterministic **temporal evidence reasoning** across the frames of a
> FeedItem. It is **not** a trained temporal neural network and does **not**
> constitute learned video understanding.

## 1. Milestone objective
Make FeedSense understand a content item as a **temporal sequence** rather than
an isolated screenshot. FeedItem remains the bounded temporal reasoning unit; the
engine reasons about what appears consistently, what changes, when it changes,
how long each signal persists, whether a frame is transitional, whether
temporary overlays caused disagreement, and whether the content itself changes
category. All 100% local, deterministic, versioned, offline — no cloud AI, no
training, no self-learning.

## 2. Explicit non-goals honored
- No cloud AI / remote inference / telemetry / uploads.
- No training, no weight modification, no self-learning, no threshold tuning on
  ground truth.
- No production model destruction (legacy `local-v6.0` and 8B-1
  `local-fusion-v1` remain intact and untouched; no historical prediction is
  overwritten).
- No unnecessary Room schema change (DB remains version 26).
- No fabricated accuracy claims.

## 3. Previous architecture (8B-1)
8B-1 established the local evidence-fusion decision architecture:
`EvidenceProvider` contract (Text/Platform/Interaction/Visual/Temporal),
`EvidenceNormalizer` (normalize, info quality, dedup), `CandidateGenerator`,
`FusionEngine` (fusion + uncertainty + decision), `FusionTrace`, and the
`FusionEvaluationBridge` for the 8A engine. Its temporal capability was a single
`TEMPORAL_CONSISTENT` record — a disambiguation boost, not cross-frame temporal
reasoning. 8B-2 **reuses** all of that and adds a dedicated temporal layer under
`analysis/fusion/temporal/`.

## 4. New temporal architecture
```
evidence per frame (8B-1 FusionEngine reuse)
        ↓
FrameTimeline (chronological ordering, gaps, intervals)
        ↓
category assignment per frame
        ↓
TemporalPersistence (time-aware support, duration-aware)
        ↓
TemporalOcrAggregator (dedup + OCR evolution)
        ↓
TemporalConsistencyAssessor (NONE/WEAK/MODERATE/STRONG/CONFLICTING)
        ↓
TemporalTransitionDetector (transitions + segment boundaries)
        ↓
fuse temporal candidates (temporal dominance + base fusion)
        ↓
uncertainty (UNKNOWN/AMBIGUOUS/CONFLICTING_EVIDENCE/...)
        ↓
representative-frame validation (outlier detection)
        ↓
TemporalFusionPrediction + TemporalFusionTrace
```
Deterministic fusion order (8B-2 §39) is documented in `TemporalFusionEngine`.

## 5. Model versioning
New model is introduced behind 8B-1:
- `modelVersion = "local-fusion-v1-temporal"`
- `configVersion = "temporal-config-v1"`
- `modelState = "EXPERIMENTAL"`

Historical predictions (`local-v6.0`, `local-fusion-v1`) are untouched. The
temporal model, like 8B-1, never silently reprocesses old predictions; any
future semantic change bumps `TemporalConfig` so results remain attributable
(8B-2 §82/83).

## 6. Files changed / added (8B-2)
New files under `app/src/main/java/com/example/feedsense/analysis/fusion/temporal/`:
- `TemporalConfig.kt` — centralized thresholds (8B-2 §84/85)
- `FrameTimeline.kt` — chronological ordering, gaps, missing/invalid timestamps
- `TemporalEvidence.kt` — structured temporal evidence representation
- `TemporalPersistence.kt` — time-aware persistence + support quality
- `TemporalConsistency.kt` — consistency/stability/conflict assessment
- `TemporalTransition.kt` — transition + segment-boundary + transition-frame detection
- `TemporalOcrAggregator.kt` — OCR dedup + evolution chains
- `VisualFingerprint.kt` — fingerprint provider interface + implementations
- `TemporalFusionEngine.kt` — main deterministic orchestrator + incremental API
- `TemporalFusionPrediction.kt` — temporal prediction + trajectory summaries
- `TemporalFusionTrace.kt` — compact diagnostic trace (8B-2 §76/77)
- `TemporalEvaluationBridge.kt` — 8A compatibility (8B-2 §72/82)

New test file:
- `test/.../TemporalFusionTest.kt` (44 tests)

No existing files modified except appending references to the 8B-1 report.

## 7. Temporal evidence representation
`TemporalEvidence`, `CategoryTrajectory`, `TemporalTransition`,
`TemporalOcrSummary`, `TemporalSupport`, `TemporalConsistency`,
`CategoryStability`, `TemporalConflictLevel`. No raw screenshots are stored;
screenshots remain references and fingerprints are compact derived
representations (8B-2 §8/19/77).

## 8. Timeline implementation
`FrameTimeline.build()` sorts frames by `timestamp ASC` with stable secondary
ordering (frameId) for ties. It parses millis/seconds/ISO timestamps, flags
missing timestamps (assigned a sequential fallback), derives `deltaTime`, and
detects gaps (`maxFrameGapSeconds`). Handles out-of-order insertion, identical
timestamps, and empty input without crashing (8B-2 §9/10/88).

## 9. Persistence calculation
`TemporalPersistence.calculate()` measures support by **time**, not frame count:
`totalDurationMs`, `continuousDurationMs`, `informativeFrameCount`,
`PersistenceLevel`, gaps. `supportQuality` combines duration, multiplicity and
continuity with an explicit single-frame penalty so a lone transient frame
cannot outrank sustained multi-frame support (8B-2 §11/12/15/46/47).

## 10. Evidence deduplication
`TemporalOcrAggregator.deduplicateTexts()` collapses near-identical OCR by token
similarity (`ocrDuplicateSimilarityThreshold`); N identical frames are one
confirmation, not N (8B-2 §21, matches the 8B-1 dedup policy). `invariantB`
verifies that duplicating an identical frame does **not** linearly multiply
confidence.

## 11. OCR aggregation & evolution
`TemporalOcrAggregator` produces `uniqueTexts`, `evolutionChains`
(`"IPL" → "IPL FINAL" → "IPL FINAL HIGHLIGHTS"` straightens the same semantic
hypothesis, 8B-2 §20/22), and `semanticTopics` (top frequent tokens).

## 12. Temporal consistency
`TemporalConsistencyAssessor` returns `NONE/WEAK/MODERATE/STRONG/CONFLICTING`
based on support ratio; missing frames never count as contradictions (8B-2
§13/14). `invariantD` verifies missing evidence is not treated as contradictory.

## 13. Conflict detection
`TemporalConflictLevel` (`NONE/LOW/MEDIUM/HIGH`) plus an explicit
OCR-vs-classifier conflict signal in `calculateTemporalUncertainty`; a platform
difference never resolves a category conflict (8B-2 §43/44). `test9` and the
`CONFLICTING_EVIDENCE` state preserve disagreement.

## 14. Transition detection
`TemporalTransitionDetector` builds `TemporalTransition` (from/to/at/time/sharp/
signals), segment boundaries, and identifies `TRANSITION_CANDIDATE` frames
(blank/gap/OCR-disappearance/neighbor-differs). Transition ≠ conflict (8B-2
§16/26/52).

## 15. Segment-boundary detection
`segmentBoundaries` produced when evidence changes sharply (category change,
platform change, timeline gap, OCR disappearance) (8B-2 §27). 8A-6 can later
decide the cause; the temporal layer assigns no blame.

## 16. Representative-frame validation
`validateRepresentativeFrame()` compares the representative signal against the
temporal consensus; produces a `representativeFrameOutlier` + trace signal
without overwriting the representative frame (8B-2 §28). An improved scoring
abstraction (information quality, category agreement, non-transition status) is
available for future use (§29/30).

## 17. Visual fingerprint interface
`VisualFingerprintProvider` interface with `fingerprint()`/`compare()`/
unavailable state, plus `UnavailableFingerprintProvider` (safe no-op) and
`SimpleFingerprintProvider`. No large dependency introduced; fingerprints are
compact and never uploaded (8B-2 §18/19/67). The existing 7C perceptual-hash
column remains the storage seam.

## 18. Interaction temporal handling
Interaction signals come from frames as already captured; the temporal engine
preserves them via the base 8B-1 `InteractionEvidenceProvider` output
(`interactionState`) and does not convert visible icons into fabricated
interaction events (8B-2 §31).

## 19. Very short content
`test11`/`test13` and `veryShortContentThresholdSeconds` confirm that 1–3 frame,
~1–3 s content is still classified, with confidence bounded by a single-frame
penalty and the short-content branch. No crashes, no fabricated confidence (8B-2
§32/33).

## 20. Long content & memory bounds
`test14` runs 100 frames through the bounded pipeline (compact summaries, only
references retained). `maxInMemoryFrameSummaries` bounds incremental buffering and
`predictIncremental()` keeps memory bounded; no `O(n²)` temporal comparisons
(8B-2 §34/35/64).

## 21. Incremental processing
`predictIncremental(previousFrames, newFrame, previousPrediction?)` supports the
streaming model (`frame arrives → evidence extracted → temporal state updated →
prediction updated`) while retaining idempotent, deterministic finalization
(8B-2 §36/37/61).

## 22. Finalization
`buildPrediction()` emits: final category, secondary candidates, confidence,
uncertainty, temporal consistency, stability, conflict level, transition state,
representative-frame quality, and diagnostic trace (8B-2 §38).

## 23. Configuration & thresholds
All thresholds are centralized in `TemporalConfig` with names, rationales, units
and version: `maxTemporalWindowSeconds`, `maxFrameGapSeconds`,
`strong/moderatePersistenceDurationSeconds`, `ocrDuplicateSimilarityThreshold`,
`ocrEvolutionPrefixRatio`, `transitionDisagreementRatio`,
`transitionMinConsecutiveFrames`, `strong/moderateConsistencyRatio`,
`ambiguityGapRatio`, `minEvidenceMass`, confidence bands,
`representativeFrameOutlierThreshold`, `fingerprintChangeThreshold`,
`veryShortContentThresholdSeconds`, `maxInMemoryFrameSummaries`,
`secondaryCategoryMinRatio`. No magic numbers in the logic (8B-2 §84/85).

## 24. Database changes
**None.** DB remains version 26; no schema change required. Temporal
state lives in pure in-memory logic (8B-2 §63/89). No migration, no identity-hash
risk.

## 25. Unit tests added
`TemporalFusionTest.kt` with 44 tests implementing the required 8B-2 §70 cases
(stable sports/meme, noisy frame, rep-frame outlier, OCR progression, OCR
duplication, real transition, overlay, conflicting sources, missing frame,
single-frame, empty, short reel, long video, mixed content, duplicate frames,
platform transition, determinism) plus the §71 invariants (A–E) and supplementary
tests for timeline, OCR, persistence, consistency, fingerprint, bridge,
incremental, conflict level, transition frames, and trace structure.

## 26. Test counts
- Previous (8A-6) baseline: **601** unit tests.
- After 8B-1: **630** (601 + 29 LocalFusionTest).
- After 8B-2: **674** (630 + 44 TemporalFusionTest).
- Failures: **0**. Skipped: **0**. All pre-existing tests still pass.

## 27. Build results
- `:app:testDebugUnitTest` — **BUILD SUCCESSFUL** (674 tests, 0 failures).
- `:app:assembleDebug` — **BUILD SUCCESSFUL**.
- `:app:compileDebugAndroidTestKotlin` — **BUILD SUCCESSFUL**.

## 28. Performance observations
Temporal aggregation is single-pass over the sorted timeline (O(n)); OCR dedup
uses token-set similarity (linear-ish, bounded comparison set). No `O(n²)`
temporal comparisons. 100-frame synthetic content processed without issue.
Memory is bounded via compact summaries and `maxInMemoryFrameSummaries`. No
behavioral UI/worker changes introduced; the temporal layer is pure logic.

## 29. Real evaluation corpus
The evaluation corpus is **not populated on-device**, so real-data accuracy is
reported honestly as
**`INSUFFICIENT_REAL_DATA_FOR_TEMPORAL_ACCURACY_CONCLUSIONS`**. No fabricated
metrics. The temporal path integrates with the 8A framework via
`TemporalEvaluationBridge` (legacy vs 8B-1 fusion vs temporal fusion comparison)
and is exercised by unit tests.

## 30. Root-cause compatibility
Temporal outputs (consistency, stability, conflict, transitions, segment
boundaries, rep-frame outlier, OCR dedup/evolution, information quality) are
shaped so 8A-6 can consume them for diagnosing segmentation failure,
representative-frame failure, temporal error, OCR failure, low-information
frames, multi-content, overlay/modal, category ambiguity, and data/prediction
mismatch — without duplicating root-cause logic inside the temporal engine (8B-2
§75).

## 31. Privacy analysis
No raw screenshots, no complete OCR transcripts, no private content are placed
in persistent traces. Only evidence ids, hashes, compact summaries, category
labels and metadata are exposed (8B-2 §77). User-facing observations remain
human-readable; diagnostics belong to research/debug views (8B-2 §78). All local.

## 32. Known limitations / remaining weaknesses
- No live vision/perceptual-hash provider wired into the temporal layer yet; the
  interface exists but the default is the safe `UnavailableFingerprintProvider`.
- Representative-frame scoring abstraction is prepared but the current validation
  relies on the single-frame base prediction vs temporal consensus.
- The temporal model is EXPERIMENTAL and, like 8B-1, must pass real-data
  evaluation before any promotion.
- No real labeled corpus on-device to measure accuracy.
- `assignCategoriesPerFrame` benefits from a vision signal to strengthen
  transition/visual-boundary detection (future 8B-3 work).

## 33. Exact recommended scope for 8B-3
8B-3 should **not** be started automatically. Recommended next scope (per §96):
1. Wire a real lightweight perceptual-hash `VisualFingerprintProvider` into the
   temporal layer (reuse the existing 7C `frameFingerprint` column) for robust
   back-to-back content separation.
2. Persist compact temporal summaries (persistence, stability, transition,
   rep-frame quality) to a research-facing store without a breaking Room
   migration (schema-additive, versioned, identity-hash verified) so the research
   dashboard §79 can graph category-over-time.
3. Materialize a populated real evaluation corpus (via existing 8A dataset
   construction) and run the legacy-vs-fusion-vs-temporal comparison to report
   real metrics; never fabricate.

## 34. Final statement
> 8B-2 introduces deterministic temporal evidence reasoning. It is not a trained
> temporal neural network and does not constitute learned video understanding.
