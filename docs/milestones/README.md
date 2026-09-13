# Milestone Documentation

This directory contains durable milestone documentation for the FeedSense
research platform.

- `8A/` — the 8A series (human ground truth & evaluation foundation).
- `8B/` — the 8B series (research/evaluation architecture expansion).

Each subdirectory contains an overview index plus durable milestone reports
for milestones that need an explicit engineering/research status record.

Milestone history (chronological, from `CHANGELOG.md`):

| Milestone | Subject |
|-----------|---------|
| v0.0.1 | Repository created |
| 7A–7D | Local AI foundation, review queue, feed items, interaction/content understanding |
| 7F–7W | Feature iterations, Room schema export, cloud usage audit, perceptual fingerprint dedup |
| 8 | Manual observation support |
| 8A-1 | Evaluation layer (4 new tables) |
| 8A-2 | Annotation workflow |
| 8A-3 | Evaluation engine & metrics |
| 8A-4 | Dataset construction |
| 8A-5 | Error analysis & failure taxonomy |
| 8A-6 | Root-cause validation |
| 8B-1 | Local evidence-fusion architecture |
| 8B-2 | Temporal evidence fusion |
| 8B-8 | Evidence-aware production adapter |
| 8B-9 | Controlled real-data experiment |
| 8B-10 | Privacy-safe evidence sanitization |
| 8B-11 | Perceptual frame deduplication |
| 8B-12 | Adaptive frame sampling |
| 8B-13 | Deterministic privacy processing |
| 8B-14 | On-device ML inference foundation |
| 8B-15-1 | Model & runtime selection (research decision) |
| 8B-15-2 | Model artifact & reproducibility identity |
| 8B-15-3 | Preprocessing contract |
| 8B-15-4 | Golden fixtures |
| 8B-15-5 | Runtime adapter & inference boundary |
| 8B-15-6 | Inference output interpretation (contract scaffolding) |
| 8B-15-7 | Taxonomy mapping |
| 8B-15-8 | Evaluation boundary |
| 8B-15-9 | Real model artifact validation & end-to-end inference |
| 8B-15-10 | Repository intelligence & research handoff (this milestone) |

Per-milestone detail lives in `docs/` (e.g. `docs/real-model-artifact-validation.md`)
and in the milestone subdirectories. The authoritative status vocabulary used
throughout is:

- `VERIFIED` — supported by repository evidence (tests/build/code).
- `UNVERIFIED` — not yet established.
- `NOT MEASURED` — intentionally not measured.
- `BLOCKED` — cannot proceed until a prerequisite is met.
- `DO NOT ASSUME` — do not infer what documentation does not state.