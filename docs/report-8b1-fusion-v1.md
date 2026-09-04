# Milestone 8B-1 Report — Local Evidence-Fusion Decision Architecture (FUSION_V1)

> Status: **COMPLETE** (architectural foundation only; 8B-2 continued from this).

## 1. Milestone objective
Establish a local, offline, deterministic **evidence-fusion decision
architecture** (`FUSION_V1`, model version `local-fusion-v1`) replacing the
flat "screenshot → heuristics → category guess" pipeline with layered evidence
extraction → normalization → candidate generation → fusion → uncertainty →
decision → diagnostic trace. No training, no cloud AI, no production model
replacement.

## 2. What was built
A pure-Kotlin, in-memory, deterministic fusion layer under
`app/src/main/java/com/example/feedsense/analysis/fusion/` with formal layers A–G
(7 new files + 1 test file, 29 new tests).

## 3. Non-goals honored
Not trained, no NN download, no cloud/remote/telemetry, no auto-upload, no cloud
fallback, no schema change, no UI redesign, no self-learning, no silent
production replacement, no fabricated accuracy.

## 4. Model versioning
New model is introduced **behind** the legacy `local-v6.0`. Every fusion
prediction carries `modelVersion = local-fusion-v1`, `configVersion =
fusion-config-v1`, `modelState = EXPERIMENTAL`. The legacy prediction is never
overwritten; both remain comparable.

## 5. Legacy vs fusion coexistence
`FusionEvaluationBridge` materializes a fusion prediction as an immutable
`AiPredictionRecord` with `modelVersion = local-fusion-v1`, `source = AI`, so the
8A engine can compare LEGACY vs FUSION on identical frozen items.

## 6. Layers A–G implemented
- **A/B Evidence extraction + normalization**: `EvidenceProvider` contract,
  providers, `EvidenceNormalizer` (normalization, information quality, dedup).
- **C Candidate generation**: `CandidateGenerator`.
- **D Fusion**: `FusionEngine` scoring + domain-consensus.
- **E Uncertainty**: central states.
- **F Decision**: `FusionPrediction`.
- **G Diagnostic trace**: `FusionTrace`.

## 7. Providers
`TextEvidenceProvider`, `PlatformEvidenceProvider`, `InteractionEvidenceProvider`,
`TemporalEvidenceProvider`, `VisualEvidenceProvider` — the `EvidenceProvider`
contract is implemented by all; none existed before.

## 8. EvidenceProvider contract
Defined in `EvidenceContext.kt`; all five providers implement it; pure and
deterministic.

## 9. RAW vs INTERPRETATION
Every `EvidenceRecord` carries `value`/`valueSummary` (RAW) distinctly from the
candidate votes (INTERPRETATION) produced downstream by `CandidateGenerator`.
OCR text (RAW) ≠ topic candidate ≠ category candidate.

## 10. Provenance
Every record carries `id`, `frameIds`, compact `timestamp`, `source`.

## 11. Evidence strength
`EvidenceStrength` `NONE/WEAK/MODERATE/STRONG` is an integrity statement,
independent of prediction confidence, reusing the 8A-6 vocabulary.

## 12. Reliability
Centralized reliability per provider in `FusionConfig` (text 0.9, platform 0.8,
visual 0.0, temporal 0.6, interaction 0.5).

## 13. Missing OCR
Missing OCR ⇒ `OCR_UNAVAILABLE` (explicit virtual record), **never**
`CONTENT_IS_UNKNOWN`. Absence of one signal never destroys the prediction.

## 14. Platform never implies category
Platform is strictly CONTEXT; a STATIC platform with empty OCR yields
`INSUFFICIENT_EVIDENCE`, not a category guess.

## 15. Interaction PRESENT vs ACTIVE
`InteractionEvidenceProvider` reports `UI_PRESENT` when affordances are visible
and `UI_UNKNOWN` otherwise; it never fabricates `UI_ACTIVE` because the current
detector cannot observe active engagement.

## 16. Visual evidence interface
`VisualEvidenceProvider` is an explicit interface that always emits
`VISUAL_SIGNAL_UNAVAILABLE`; it does not fake OCR as vision. This is the future
vision-model seam.

## 17. Determinism
`identical_input + modelVersion + configuration ⇒ byte-identical output`. No
randomness, no time dependence, deterministic ordering and tie handling
(covered by tests).

## 18. Information quality
`InformationQuality` reports informative-text frames vs total and is tracked in
the trace.

## 19. Duplicate-evidence control
Identical evidence (same family + summary) contributes once; 5 identical OCR
frames are one confirmation, not five; tested that confidence does not inflate.

## 20. Normalization
`normalizedScoreOf` blends integrity strength + confidence, scaled by
centralized reliability, and applies per-family weight.

## 21. Conflicting evidence
Candidate-aware conflict detection surfaces `CONFLICTING_EVIDENCE` when the
legacy classifier verdict contradicts the leading text candidate or when two
frames lead to different domains — never a silent selection.

## 22. Reliability scaling
Weights are per-family (text 1.0, platform 0.3, visual 0.8, temporal 0.4,
interaction 0.2); reliability is per-provider; both documented, NOT tuned
against the frozen test set.

## 23. Provider weight not record-count
Because `fused = mean(per-frame votes) * textWeight`, and dedup collapses
identical records, a provider that merely produces more records cannot dominate.

## 24. No provider dominance
A single verbose frame cannot outvote others (mean over frames); platform and
interaction never name categories.

## 25. Central config
`FusionConfig` holds every threshold, weight, band, and policy. No scattered
magic numbers.

## 26. Deterministic tie handling
When the top two candidates are within `ambiguityGapRatio`, the engine returns
`AMBIGUOUS` and never picks arbitrarily.

## 27. Central confidence bands
`HIGH ≥ 0.7`, `MEDIUM ≥ 0.45`, `LOW ≥ 0.2`, else `UNKNOWN` — all in
`FusionConfig`, mapped via `ConfidenceBand.fromConfidence`.

## 28. Uncertainty states
`CONFIDENT`, `LOW_CONFIDENCE`, `AMBIGUOUS`, `INSUFFICIENT_EVIDENCE`,
`CONFLICTING_EVIDENCE` — all implemented.

## 29. Confidence-uncertainty separation
Strength is integrity, confidence is the final share-based estimate, and
uncertainty is the decision state — kept separate and deterministic.

## 30. Multi-label
`FusionPrediction` keeps dimensions separate (category, topic, tone, content
type). `secondaryCategories` (top 3) and primary category are maintained.

## 31. Dimension separation
Tested: topic and content type are derived independently; category never folded
into tone or content type.

## 32. Hierarchical categories
Reuses `CategoryCatalog` hierarchical keys (`ranking`, `movie_clip`,
`series_clip`, `sponsored_content`, `meme`, `creator_edit`, etc.) via `normalize`
/ `domainOf` / `parentOf`; domain-consensus resolves leaf ties within a domain.

## 33. Absence vs unknown
Empty/blank OCR and keyword-less informative text both yield
`INSUFFICIENT_EVIDENCE`; absence is explicit, never fabricated into a label.

## 34. Short interactions
A single-frame (~1s Reels) interaction is fully analyzable; temporal evidence
reports `TEMPORAL_INSUFFICIENT` honestly. Performance: pure in-memory, no
re-OCR, no UI-thread work, bounded trace.

---

## Verification

- **Build command (exact from milestone):**
  ```
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin --offline
  ```
  **Exit 0 — all pass.**
- **Unit tests:** 630 total (601 prior + 29 new `LocalFusionTest`), **0 failures**.
- **assembleDebug:** success.
- **compileDebugAndroidTestKotlin:** success.
- **Room schema:** unchanged, DB version stays **26**.
- **Frozen data:** not modified (no ground truth / evaluation item / frozen AI
  prediction / hard-case touched).
- **Production AI:** not modified; `local-v6.0` unchanged; fusion runs behind it.

## Real-data accuracy
The evaluation corpus is not populated on-device, so real-data accuracy is
reported honestly as
**`INSUFFICIENT_REAL_DATA_FOR_ACCURACY_CONCLUSIONS`** — no fabricated numbers.
The legacy-vs-fusion comparison path is implemented (`FusionEvaluationBridge`)
and exercised by unit tests against the 8A evaluation engine.

## Files added (8B-1)
- `analysis/fusion/FusionConfig.kt`
- `analysis/fusion/EvidenceModel.kt`
- `analysis/fusion/EvidenceContext.kt`
- `analysis/fusion/TextEvidenceProvider.kt`
- `analysis/fusion/PlatformEvidenceProvider.kt`
- `analysis/fusion/InteractionEvidenceProvider.kt`
- `analysis/fusion/VisualEvidenceProvider.kt`
- `analysis/fusion/TemporalEvidenceProvider.kt`
- `analysis/fusion/EvidenceNormalizer.kt`
- `analysis/fusion/CandidateGenerator.kt`
- `analysis/fusion/FusionEngine.kt`
- `analysis/fusion/FusionPrediction.kt`
- `analysis/fusion/FusionTrace.kt`
- `analysis/fusion/FusionEvaluationBridge.kt`
- `analysis/fusion/FusionModels.kt`
- `test/.../LocalFusionTest.kt` (29 tests)

## Stopped at 8B-1
Per instruction, 8B-2 is NOT started here; 8B-2 builds on this milestone and is
reported separately in `report-8b2-temporal-v1.md`.
