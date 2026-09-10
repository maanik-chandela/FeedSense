# 8A Series Overview

The 8A series established the human-ground-truth and evaluation foundation for
FeedSense. Its principles are carried forward throughout all later work:

1. **AI predictions and human ground truth remain separate.** The AI snapshot
   (`ai_predictions`) is immutable; the human annotation (`ground_truths`) is
   independent evidence; the comparison (`evaluation_results`) is derived.
2. **Ground truth must never be silently overwritten by AI.** A human
   annotation is stored and compared against, never written back into
   `feed_items` AI columns.
3. **Human annotations are independent research evidence.** Annotator identity
   is an opaque id; re-editing updates the clean current record and replaces
   its evaluation result.
4. **Evaluation must be reproducible.** Every evaluation row carries the frozen
   prediction + truth + dataset version + methodology version; frozen
   `EvaluationRun` reports are appended, never mutated.
5. **Root-cause analysis distinguishes observations, hypotheses, and confirmed
   conclusions.** Automated diagnostics cannot silently become human-confirmed
   findings.
6. **Privacy-safe evidence handling.** All evaluation data is stored
   on-device; annotator identity carries no profile data.

## Milestones

| Milestone | Content | DB version |
|-----------|---------|-----------|
| 8A-1 | Evaluation layer: `evaluation_items`, `ai_predictions`, `ground_truths`, `evaluation_results` | 23→24 |
| 8A-2 | Annotation workflow (tri-state interactions, ambiguity, annotation UI) | 24→25 |
| 8A-3 | Evaluation engine & metrics (`EvaluationRun`, frozen reports) | 25→26 |
| 8A-4 | Dataset construction | 26 |
| 8A-5 | Error analysis & failure taxonomy | 26 |
| 8A-6 | Root-cause validation | 26 |

Authoritative documentation: `docs/research-evaluation.md` (8A),
`docs/root-cause-analysis.md` (8A-6 overview), and the source packages
`analysis/evaluation/` and `analysis/evidence/`.