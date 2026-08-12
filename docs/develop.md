# 2026-08-05

Completed:
- Home Screen
- Create Project Screen
- Data Model

Learned:
- State in Jetpack Compose
- Reusable Composables

Next:
- Jetpack Navigation

# 2026-08-13 — Milestone 7 (7A–7D)

Completed:
- 7A Local AI foundation + hybrid frame analysis pipeline
  (FrameAnalyzer, LocalFrameAnalyzer, ConfidenceGate, FrameAnalysisPipeline,
  CloudFrameAnalyzer placeholder, structured FrameAnalysisResult)
- 7B AI review queue for uncertain frames
  (LabeledReference entity, human validation, ReviewScreen/ReviewViewModel)
- 7C Feed items from frames
  (FeedItem entity, time+category segmentation, skipped detection,
  auto [AI] observations, FeedItemBuilderWorker)
- 7D Interaction + content understanding
  (InteractionDetector evidence-based signals, topic+tone classification
  (heuristic-v2), PerceptualHash 64-bit dHash segmentation, FeedItem
  fingerprint, DB v10)

Fixes:
- Converters.toStringList round-trip crash (empty list stored as "" read back
  as null -> Room "Expected NON-NULL" crash)
- Schema identity mismatch on device (stale v9 DB from an intermediate build;
  bumped to v10)

Learned:
- Room stores empty string for emptyList() via pipe converter; read side must
  return emptyList(), never null, for non-null fields
- dHash is gradient-based: uniform images hash identically regardless of color

Next:
- 7E local AI learning from user corrections
