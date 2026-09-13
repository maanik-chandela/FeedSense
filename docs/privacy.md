# Privacy-Safe Evidence Sanitization (Milestones 8B-10 / 8B-13)

Milestone 8B-10 adds a **production-safe, on-device evidence
sanitization layer** to FeedSense. It runs *capture -> sanitize ->
OCR/classification -> decision/evaluation* without changing the
baseline AI, FeedItem semantics, the frozen category schema, ground
truth, or the Room schema (v26).

Milestone 8B-13 adds the **deterministic on-device privacy-processing
pipeline** (`PrivacyProcessor`) that replaces the heuristic
sanitizer path with an explicit, versioned, tested
DETECT -> DECIDE -> TRANSFORM -> VALIDATE flow. See §12 onwards.

---

## 1. Basics

| |  |
|---|---|
| Milestone | 8B-10 |
| Pipeline role | Evidence-protection layer between capture and analysis |
| Baseline impact | NONE (see §9) |
| Cloud AI | NONE (fully on-device) |
| Raw frames | Retained on device by policy; never exported by default |
| Versioning | `privacy-v1`, `sanitizer-v1`, `pattern-detection-v1`, `text-redaction-v1` |
| Determinism | Same frame + policy + version ⇒ same sanitized output |

---

## 2. Threat model

FeedSense automatically screens real smartphone content. The
threat this layer mitigates is **screen privacy leakage**:

- **Unrelated private information** appearing on screen next to
  research-relevant content: notification previews, messages,
  personal identifiers (emails, phone numbers, card-like strings),
  banking/password/payment UI, profile photographs, location
  labels.
- **Accidental persistence** of that private information inside
  analysis outputs (OCR text, decision evidence, exports, logs).

The layer does **NOT** claim to defend against:

- Screen recording / screen sharing / other OS-level capture
  (`PrivacyPolicyLimitations.doesNotPreventLegalOscaptures`).
- Adversarially hidden content (content OCR never sees is content
  the sanitizer never handles).
- Perfect OCR coverage.

These limitations are surfaced in code and docs so FeedSense never
overstates its privacy guarantees.

---

## 3. Pipeline

```
RAW FRAME (capture)
      │
      ▼
SANITIZE   ── DETECT   fixed system UI + notification +
      │                   OCR-pattern + injected typed regions
      │   ── FILTER    drop regions the policy disables
      │   ── RASTERIZE PrivacyRegionApplier (8B-4 rasterizer)
      │   ── REDACT    redact private patterns in OCR text
      │   ── AUDIT     record status/counts/versions (never raw)
      │
      ▼
SANITIZED FRAME  +  REDACTED OCR TEXT  +  AUDIT  +  METADATA
      │
      ▼
ANALYSIS (OCR/a model)  →  DECISION  →  EVALUATION
      │
      ▼
REVIEW LOOP (human)     (raw frames retained on device)
```

Any failure returns `SANITIZATION_FAILED` instead of throwing;
capture continues (policy option `blockOnSanitizationFailure`)
while downstream use of that frame is blocked.

---

## 4. Region taxonomy

`PrivacyRegionType` — eight controlled types:

| Type | Content class |
|---|---|
| `SYSTEM_UI` | private information |
| `NOTIFICATION` | private information |
| `PRIVATE_TEXT` | private information |
| `PERSONAL_IDENTIFIER` | private information |
| `PERSONAL_IMAGE` | private information |
| `SENSITIVE_APPLICATION_UI` | private information |
| `LOCATION_INFORMATION` | private information |
| `UNKNOWN_SENSITIVE_REGION` | private information (conservative fallback) |

Design rule: a region type describes **WHAT is sensitive, not why**.
`PrivacyContentClass.RESEARCH_RELEVANT_CONTENT` is a distinct class —
research content ("IPL", "RCB", "YouTube", "Netflix") is **never**
classified as private. The taxonomy never touches the FeedSense
category schema. Unknown legacy labels map conservatively to
`UNKNOWN_SENSITIVE_REGION` (`fromLabel`), so version migration
never crashes.

---

## 5. Policy & versioning

`PrivacyPolicy` is an immutable, versioned description of how
sanitization behaves. Conservative defaults (the `RESEARCH` preset,
also `defaultPrivacyPolicy()`):

| Setting | Default |
|---|---|
| sanitize notifications | true |
| sanitize system UI | true |
| sanitize personal identifiers | true |
| sanitize private text | true |
| sanitize personal images | true |
| sanitize sensitive app UI | true |
| sanitize location | true |
| block on sanitization failure | false (capture continues) |
| retain raw frames | true |
| retain sanitized frames | true |
| export raw research content | false |

Version constants (§1) are bumped whenever the behavior of that
component changes; two runs with identical inputs, versions, and
policy MUST produce identical output.

---

## 6. Sanitization statuses & evidence availability

`PrivacySanitizationStatus` — six controlled statuses:

| Status | Safe for research | Meaning |
|---|---|---|
| `SANITIZED` | yes | sensitive regions detected and transformed |
| `PARTIALLY_SANITIZED` | yes | some regions handled, others couldn't be |
| `SANITIZATION_FAILED` | no | sanitizer errored; NOT safe |
| `SANITIZATION_UNAVAILABLE` | no | sanitizer could not run |
| `NOT_REQUIRED` | yes | no sensitive content detected |
| `UNKNOWN` | no | outcome not known |

`EvidenceAvailability` — `CONTENT_DETECTED`,
`NO_CONTENT_DETECTED`, `CAPTURE_BLOCKED` (OS flag-secure-like
truncation), `UNKNOWN_AVAILABILITY`.

Under the default `RESEARCH` policy the fixed system UI regions are
always present, so a valid frame `SANITIZED` (or
`PARTIALLY_SANITIZED`) — `NOT_REQUIRED` requires a policy that
disables those regions.

---

## 7. Evidence loss honesty

Sanitization is a **lossy** transform. The evidence-aware pipeline
carries this loss candidly:

- `PrivacyEvidenceLoss`: `NONE`, `EVIDENCE_LOST_DUE_TO_SANITIZATION`,
  `OCR_TEXT_REDACTED`, `CAPTURE_BLOCKED`.
- `PrivacyEvidenceMetadata` is attached to `ItemEvidenceSnapshot`
  (defaulted field, source-compatible).
- `EvidenceAwareProductionAdapter` surfaces
  `privacyPolicyVersion`, `privacyStatusLabel`,
  `privacyEvidenceLossLabels`, and appends
  `PRIVACY_REDACTION_AFFECTED_DECISION` /
  `EVIDENCE_LOST_DUE_TO_SANITIZATION` reasons **only when the
  metadata actually supports them**.

A decision made from sanitized evidence is strictly weaker than one
made from raw evidence; this layer records that honestly instead of
hiding it.

---

## 8. What stays private

- **Logs**: only the audit trail is logged, via
  `SanitizationAudit.toPrivacyLogEntry().toLogLine()` — status,
  counts, versions, booleans. Raw OCR text, screenshots, and raw
  frame paths are never logged.
- **Exports**: `ExportManager` defaults to
  `PrivacyExportPolicy.SAFE_DEFAULT`. Under
  `SANITIZED_METADATA_ONLY` (default) raw `visibleText` and raw
  frame paths are omitted; the manifest records
  `privacyExportMode` + `privacyExportPolicyVersion`. Raw text is
  exported only under an explicit `DEBUG_RAW_TEXT` policy.
- **Metrics**: `PrivacyMetrics` records observed counters only
  (`privacy.frames_sanitized`, `privacy.regions_processed`, ...).
  No detection rates or percentages are fabricated.
- **Audit**: `SanitizationAudit` is structurally free of raw
  content by construction.

---

## 9. Baseline impact statement (REQUIRED)

> `8B-10 does not modify FeedSense baseline predictions. Privacy
> sanitization is an evidence-protection layer and does not alter
> FeedItem semantics or the baseline AI.`

Concretely: `FeedItem`, `AiPredictionRecord`, `GroundTruth`, the
category taxonomy, and Room schema v26 are unchanged; the baseline
prediction is read verbatim and never overwritten; the evidence-
aware adapter is read-only and returns a comparison, not a
mutation.

---

## 10. Real-world validation status

Sanitization correctness is validated by **on-device JVM unit
tests** that exercise the orchestrator with real pattern detectors
and a fake rasterizer, and by the existing device regression suite
for the 8B-4 rasterizer. No fabricated detection percentages are
claimed. Any real detection rates will be reported here only after
a controlled on-device population measurement (see
`docs/research-evaluation.md`); until then, all detection behavior
is defined conservatively and deterministically by the detector
patterns in §5–§7 and is not claimed as measured accuracy.

---

## 11. Relations to other milestones

- **8B-4**: `AndroidPrivacyRegionApplier` reuses the existing
  rasterizer (`PrivacySanitizer`) through the legacy status
  vocabulary.
- **8B-6/7/8**: `PrivacyEvidenceMetadata` rides on
  `ItemEvidenceSnapshot`; the adapter and difference reasons carry
  privacy context.
- **8A**: evaluation still consumes baseline predictions and
  evidence-aware outcomes; sanitization only shapes what evidence
  reaches them.
- **Milestone 7**: `RetentionPolicySettings` classifies artifacts
  for the existing retention worker; it does not replace deletion.

---

## 12. Milestone 8B-13 — deterministic on-device privacy processing

`PrivacyProcessor` is the 8B-13 production pipeline. It is an
**on-device, best-effort visual-anonymization layer**: no cloud, no
self-learning, no large vision model, no `FLAG_SECURE` bypass. It
consumes regions supplied by the detection layer (system-UI
geometry, OCR patterns) and never runs OCR or downloads models
itself.

### 12.1 Pipeline: DETECT -> DECIDE -> TRANSFORM -> VALIDATE

```
DETECT    regions arrive typed (PrivacyRegionType) from
          8B-10 SystemUIRegionDetector + PrivacyOcrDetection;
          nothing here runs OCR.
DECIDE    risk + confidence computed; policy filters disabled
          classes; rule table resolves each transformation;
          STRICT may decide DROP_FRAME.
TRANSFORM deterministic pixel transforms applied weakest-first,
          so the stronger transform wins on overlap.
VALIDATE  per-region checksums prove the transform changed the
          pixels; a no-op BLUR/PIXELATE escalates to MASK;
          unresolved regions surface PARTIALLY_SANITIZED.
```

Determinism: only frame pixels, regions, policy, rules, OCR
availability, and the injected clock are read. No randomness and no
wall clock in the decision path; the timestamp is injectable
(`nowMs`).

### 12.2 Raw-frame boundary

A `PrivacyFrame` is a bare ARGB buffer with no paths, bytes, or
metadata. It is mutated **in place** on the common path — no second
full-frame copy — so the raw lifetime ends with the single
`process()` call. `CROP` and the benchmark are the only places a
second buffer may exist (`duplicated()` is explicit). After
processing, the buffer IS the safe representation; callers must not
alias it as raw.

### 12.3 Region taxonomy and the 13-concept list

The 8B-13 spec names 13 sensitive-content concepts. FeedSense maps
them 1:1 onto the existing eight `PrivacyRegionType`s (no competing
taxonomy, spec "reuse existing abstractions"):

| Spec concept | Region type |
|---|---|
| status/navigation bars, system chrome | `SYSTEM_UI` |
| notification previews, notifications | `NOTIFICATION` |
| chat / direct messages / private text | `PRIVATE_TEXT` |
| email addresses, phone numbers, usernames | `PERSONAL_IDENTIFIER` |
| account identifiers | `PERSONAL_IDENTIFIER` |
| payment-card-like sequences | `PERSONAL_IDENTIFIER` |
| profile & private photographs, faces | `PERSONAL_IMAGE` |
| banking / password / payment UI | `SENSITIVE_APPLICATION_UI` |
| auth screens | `SENSITIVE_APPLICATION_UI` |
| maps / location labels | `LOCATION_INFORMATION` |
| street addresses, GPS-derived content | `LOCATION_INFORMATION` |
| unknown-but-possibly-sensitive regions | `UNKNOWN_SENSITIVE_REGION` |
| URLs (opt-in only) | `PERSONAL_IDENTIFIER` (policy-gated) |

The pattern level (`SensitivePatternDetector`) keeps distinct
kinds — `EMAIL_ADDRESS`, `PHONE_NUMBER`, `PAYMENT_CARD`,
`ACCOUNT_ID`, `URL` — so granularity is preserved without widening
the protected vocabulary.

### 12.4 Policy modes

`PrivacyPolicyMode` selects the deterministic rule table and drop
behavior. Rules carry a priority; on overlap the highest-priority
rule wins and the strongest transform is applied last.

| Mode | Behavior | DROP_FRAME |
|---|---|---|
| `RESEARCH` | blur system chrome/notifications/images; mask private text, identifiers, sensitive app UI, location | never |
| `BALANCED` | blur private text (keeps some evidence); blur notifications/images; mask identifiers/sensitive UI; pixelate location | never |
| `STRICT` | mask every sensitive class (incl. personal images), crop UI bands | yes, when high risk and <10% research value remains |

`PrivacyRuleTable.DEFAULT` = RESEARCH; `PrivacyPolicy.DISABLED`
leaves everything untouched and explicitly reports the frame unsafe.

### 12.5 Transformation modes

`NONE` (no change), `BLUR` (box blur, structure kept), `PIXELATE`
(crisp mosaic), `MASK` (deterministic marking pattern; nothing
readable), `CROP` (full-width/height band removed), `DROP_FRAME`
(whole frame discarded; only safe metadata emitted). A no-op
BLUR/PIXELATE on uniform content escalates to MASK so VALIDATE has
real evidence.

### 12.6 Confidence and risk

- `PrivacyConfidence` (`NONE/LOW/MEDIUM/HIGH`) is derived from the
  detection signal; `AppliedTransformation` records signal +
  confidence per applied transform.
- `PrivacyRisk` (`NONE/LOW/MEDIUM/HIGH/UNKNOWN`) aggregates region
  types + OCR availability and is **research diagnostic**, never a
  PII-detection claim and never AI confidence.

### 12.7 Unknown-risk handling

`OCR_UNAVAILABLE` is an explicit privacy state. With no signal, risk
is `UNKNOWN` and `isSafeForResearchUse` is **false** — the frame is
never silently "safe". An `UNKNOWN_SENSITIVE_REGION` under
OCR-unavailable is also protected by the rule table
(`appliedWhenUnknownRisk`), never left as NONE.

### 12.8 OCR integration

`OcrLine(text, bounds)` carries one detected text line plus its
normalized geometry. `PrivacyOcrDetection` finds explicit private
forms (email/phone/card/account; URL opt-in) and emits one
`PERSONAL_IDENTIFIER` region **per flagged line**, line-atomic.
Unbounded lines conservatively fall back to the whole frame.
Research content ("IPL", "YouTube", "Netflix") is never a pattern.

### 12.9 Evidence-aware integration

`EvidenceSanitizer` / `AndroidPrivacyRegionApplier` /
`PrivacyTextRedactor` connect processing to the 8B-10 evidence
vocabulary. `PrivacyEvidenceLoss` (`NONE`,
`EVIDENCE_LOST_DUE_TO_SANITIZATION`, `OCR_TEXT_REDACTED`,
`CAPTURE_BLOCKED`) and the six `PrivacySanitizationStatus`es ride on
`ItemEvidenceSnapshot` and are surfaced by
`EvidenceAwareProductionAdapter`. Raw-frame availability is mapped
honestly — a privacy-dropped frame stays `CONTENT_DETECTED`, while a
no-signal OCR-unavailable frame is `UNKNOWN_AVAILABILITY`.

### 12.10 Root-cause attribution

The 8A-6 root-cause vocabulary adds two privacy-specific candidate
causes: `CAUSE_PRIVACY_EVIDENCE_LOSS` (evidence lost to privacy
processing) and `CAUSE_CAPTURE_UNAVAILABLE` (blocked/secure surface)
— attributed explicitly instead of falling into the generic
pipeline/evidence buckets.

### 12.11 Versioning

`PrivacySanitizationVersion`: `privacy-v1` policy,
`processing-v1`, `rules-v1`, plus coverage/benchmark/evaluation
versions. `PrivacyMetrics.METRICS_VERSION` carries the policy
version. Versions bump only when behavior changes; identical
inputs + versions ⇒ identical output.

### 12.12 Performance methodology

`PrivacyBenchmark` measures the side-by-side disabled-vs-enabled
difference on deterministic synthetic frames (`SyntheticFrames`,
no randomness). Wall clock is measured outside the decision path so
it never affects the decision; timing is reported as observed
counters, not fabricated percentages.

### 12.13 Coverage evaluation

`PrivacyCoverageEvaluator` compares processor decisions against
human-listed regions, keyed to the versioned benchmark/taxonomy
constants. It reports observed agreement only and never fabricates
detection rates.

### 12.14 What stays private

Processing metadata (`toSafeMetadataMap` / `toSafeLogLine`) is
structurally free of raw text, coordinates, and frame paths; the
audit (`SanitizationAudit`) is free of raw content by construction;
exports/logs follow the 8B-10 policy (§8). `AppliedTransformation`
stores only region type + transform + signal + confidence.

### 12.15 Honest limitations

- Detection is best-effort: content OCR misses, or layouts hidden
  adversarially, are content the sanitizer never sees.
- Email-FORM tokens are matched wherever they appear (e.g.
  brand-style `kitchen@example.com`); the response is bounded to the
  containing line/region and never full-frame by default.
- OCR detection only matches identifier FORMS. It never claims a
  plain message "is private" from keywords — keyword-based region
  classification is the caller's/geometry layer's job.
- Sanitization does not defend against OS-level captures and never
  bypasses `FLAG_SECURE`.

### 12.16 Baseline impact statement (8B-13)

> `8B-13 processes only frame pixels, regions, policy, rules, and
> OCR availability. It never mutates GroundTruth, AiPredictionRecord,
> FeedItem, the category taxonomy, evaluation records, or Room
> schema v26.`

Tests enforce privacy/baseline isolation: the pipeline leaves a
GroundTruth record and the frozen `AiPredictionRecord` exactly as
they were, emits only safe metadata, and is deterministic for
identical inputs + policy (see `PrivacyBaselineIsolationTest`).