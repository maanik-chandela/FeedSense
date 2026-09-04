package com.example.feedsense.analysis.evaluation

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.model.GroundTruth
import java.time.LocalDateTime

// --------------------------------
// ROOT CAUSE ANALYZER (Milestone 8A-6)
// --------------------------------
//
// Deterministic generator of the automatic root-cause
// assessment for a single error. It:
//
//   1. collects OBSERVED facts (prediction, truth, result,
//      frame evidence)
//   2. runs the attribution decision tree -> attribution class
//   3. proposes candidate causes with PRIMARY/CONTRIBUTING roles
//   4. marks every automatic candidate POSSIBLE (never
//      CONFIRMED; only a human confirms/rejects)
//   5. derives independent evidence strength + attribution
//      confidence (separate from AI prediction confidence)
//   6. records evidence REFERENCES (frame id, etc.), never raw
//      screen content
//
// Determinism: given the same inputs + the same diagnostic
// version, the same assessment is produced (stable ordering,
// no randomness).

class RootCauseAnalyzer private constructor() {

    companion object {

        const val DIAGNOSTIC_VERSION = RootCauseAssessment.DIAGNOSTIC_VERSION

        /**
         * Builds a root-cause assessment for one evaluation unit
         * (usually one with detected errors).
         *
         * @param unit            the evaluation triplet
         * @param frameViews      diagnostic frame views (may be empty)
         * @param representativeFrameId the feed item's representative frame
         * @param multipleTruths  all ground truths for the item, to
         *                        detect annotator disagreement
         * @param analysisId      stable id (defaults to a UUID)
         */
        fun analyze(
            unit: EvaluationUnit,
            frameViews: List<Evidence.FrameView> = emptyList(),
            representativeFrameId: String? = null,
            multipleTruths: List<GroundTruth> = emptyList(),
            analysisId: String =
                "rc-${unit.item.id}-${System.nanoTime().toString(16)}",
            now: LocalDateTime = LocalDateTime.now()
        ): RootCauseAssessment {

            val truth = unit.truth
            val prediction = unit.prediction
            val result = unit.result
            val itemId = unit.item.id

            // --------------------------------
            // OBSERVED FACTS
            // --------------------------------

            val truthValid =
                CategoryCatalog.normalize(truth.category) != null &&
                    truth.ambiguity != GroundTruth.AMBIGUITY_UNKNOWN
            val truthAmbiguous =
                truth.ambiguity == GroundTruth.AMBIGUITY_AMBIGUOUS ||
                    truth.ambiguity == GroundTruth.AMBIGUITY_MIXED

            val hasFrameEvidence = frameViews.isNotEmpty()
            val hasOcr = Evidence.hasOcr(frameViews)
            val evidenceSufficient =
                hasFrameEvidence && frameViews.size >= 2

            val segmentationCorrect =
                result.comparable != false &&
                    result.segmentationError == null

            val capabilityRepresentationError =
                (result.platformAgreement == false) ||
                    (result.durationInaccurate == true) ||
                    (result.skippedAgreement == false) ||
                    (result.interactionSignalsDisagreement ?: 0 > 0) ||
                    (result.topicAgreement == false) ||
                    (result.toneAgreement == false)

            val aiDisagrees =
                result.verdict == com.example.feedsense.model.EvaluationRecord.VERDICT_INCORRECT

            val categoryAmbiguous = truthAmbiguous

            val tree = AttributionDecisionTree.classify(
                AttributionDecisionTree.Input(
                    truthValid = truthValid,
                    truthAmbiguous = truthAmbiguous,
                    evidenceSufficient = evidenceSufficient,
                    segmentationCorrect = segmentationCorrect,
                    capabilityRepresentationError =
                        capabilityRepresentationError,
                    aiDisagreesDespiteEvidence = aiDisagrees,
                    categoryAmbiguous = categoryAmbiguous
                )
            )

            // --------------------------------
            // EVIDENCE REFERENCES
            // --------------------------------

            val references = buildList {
                frameViews.forEach { f ->
                    add(
                        Evidence.EvidenceRef(
                            type = RootCauseTypes.EVIDENCE_REPRESENTATIVE_FRAME,
                            referenceId = f.frameId,
                            kind = "frame",
                            note = "category=${f.category} " +
                                "conf=${f.confidence?.let { "%.2f".format(it) } ?: "n/a"}"
                        )
                    )
                }
                if (hasOcr) {
                    add(
                        Evidence.EvidenceRef(
                            type = RootCauseTypes.EVIDENCE_OCR_TEXT,
                            referenceId = frameViews.firstOrNull {
                                !it.ocrText.isNullOrBlank()
                            }?.frameId,
                            kind = "frame",
                            note = "OCR text present"
                        )
                    )
                }
                add(
                    Evidence.EvidenceRef(
                        type = RootCauseTypes.EVIDENCE_GROUND_TRUTH,
                        referenceId = truth.id,
                        kind = "truth",
                        note = "truth=${truth.category} " +
                            "ambiguity=${truth.ambiguity}"
                    )
                )
                add(
                    Evidence.EvidenceRef(
                        type = RootCauseTypes.EVIDENCE_MODEL_CONFIDENCE,
                        referenceId = prediction.id,
                        kind = "prediction",
                        note = "confidence=" +
                            (prediction.confidence?.let { "%.3f".format(it) }
                                ?: "n/a")
                    )
                )
                if (multipleTruths.size > 1) {
                    add(
                        Evidence.EvidenceRef(
                            type = RootCauseTypes.EVIDENCE_ANNOTATOR_NOTE,
                            referenceId = null,
                            kind = "annotation",
                            note = "${multipleTruths.size} annotators present"
                        )
                    )
                }
            }

            // --------------------------------
            // CANDIDATE CAUSES
            // --------------------------------

            val candidates = generateCandidateCauses(
                unit = unit,
                frameViews = frameViews,
                representativeFrameId = representativeFrameId,
                hasOcr = hasOcr,
                evidenceSufficient = evidenceSufficient,
                segmentationCorrect = segmentationCorrect,
                truthAmbiguous = truthAmbiguous,
                multipleTruths = multipleTruths,
                tree = tree
            )

            // --------------------------------
            // EVIDENCE STRENGTH (independent dimension)
            // --------------------------------

            val evidenceStrength = deriveEvidenceStrength(
                frameViews = frameViews,
                hasOcr = hasOcr,
                multipleTruths = multipleTruths
            )

            // --------------------------------
            // ATTRIBUTION CONFIDENCE (independent of AI)
            // --------------------------------

            val attributionConfidence = deriveAttributionConfidence(
                evidenceStrength = evidenceStrength,
                confirmedCount = candidates.count {
                    it.attributionStatus == RootCauseTypes.STATUS_CONFIRMED
                },
                possibleCount = candidates.size
            )

            return RootCauseAssessment(
                analysisId = analysisId,
                evaluationItemId = itemId,
                evaluationRecordId = result.id,
                datasetVersion = unit.item.datasetVersion,
                modelVersion = prediction.modelVersion,
                diagnosticVersion = DIAGNOSTIC_VERSION,
                capability = ErrorTypes.capabilityFor(unit.primaryErrorTypeOf()),
                errorType = unit.primaryErrorTypeOf(),
                causes = candidates,
                attributionStatus = if (candidates.any {
                        it.attributionStatus == RootCauseTypes.STATUS_CONFIRMED
                    }
                ) {
                    RootCauseTypes.STATUS_CONFIRMED
                } else {
                    RootCauseTypes.STATUS_POSSIBLE
                },
                evidenceStrength = evidenceStrength,
                attributionClass = tree.attributionClass,
                attributionConfidence = attributionConfidence,
                attributionNote = tree.note,
                humanReviewStatus = RootCauseAssessment.REVIEW_UNREVIEWED,
                reviewerId = null,
                reviewerNotes = null,
                reviewedAt = null,
                evidenceReferences = references,
                createdAt = now,
                updatedAt = now
            )
        }

        // --------------------------------
        // CANDIDATE CAUSE GENERATION
        // --------------------------------

        private fun generateCandidateCauses(
            unit: EvaluationUnit,
            frameViews: List<Evidence.FrameView>,
            representativeFrameId: String?,
            hasOcr: Boolean,
            evidenceSufficient: Boolean,
            segmentationCorrect: Boolean,
            truthAmbiguous: Boolean,
            multipleTruths: List<GroundTruth>,
            tree: AttributionDecisionTree.Result
        ): List<RootCauseAssessment.CandidateCause> {

            val result = unit.result
            val truth = unit.truth
            val prediction = unit.prediction
            val causes = mutableListOf<RootCauseAssessment.CandidateCause>()

            fun add(
                cause: String,
                role: String,
                cls: String = tree.attributionClass,
                strength: String = RootCauseTypes.EVIDENCE_WEAK,
                status: String = RootCauseTypes.STATUS_POSSIBLE,
                note: String
            ) {
                causes += RootCauseAssessment.CandidateCause(
                    cause = cause,
                    role = role,
                    attributionStatus = status,
                    evidenceStrength = strength,
                    attributionClass = cls,
                    attributionConfidence = RootCauseTypes.CONFIDENCE_LOW,
                    note = note
                )
            }

            // --- Inter-annotator disagreement is SPECIAL: it is
            // OBSERVED and thus can become SUPPORTED, but never
            // CONFIRMED by the machine.---
            if (multipleTruths.size > 1 &&
                AnnotationAgreement.agreementLevel(multipleTruths) ==
                RootCauseTypes.AGREEMENT_DISAGREEMENT
            ) {
                add(
                    cause = RootCauseTypes.CAUSE_ANNOTATION_DISAGREEMENT,
                    role = RootCauseTypes.ROLE_PRIMARY,
                    cls = RootCauseTypes.CLASS_ANNOTATION_RELATED,
                    strength = RootCauseTypes.EVIDENCE_STRONG,
                    status = RootCauseTypes.STATUS_SUPPORTED,
                    note = "${multipleTruths.size} annotators disagree; " +
                        "the AI match against one is not necessarily an error."
                )
            }

            // --- Primary cause selection ---
            // We assign the PRIMARY_Cause based on the decision
            // tree's decisive condition, then attach contributing
            // candidates.

            val primaryCause: String = when (tree.attributionClass) {
                RootCauseTypes.CLASS_ANNOTATION_RELATED ->
                    RootCauseTypes.CAUSE_ANNOTATION_DISAGREEMENT
                RootCauseTypes.CLASS_PIPELINE_RELATED -> when {
                    !segmentationCorrect ->
                        RootCauseTypes.CAUSE_SEGMENTATION_FAILURE
                    !hasOcr ->
                        RootCauseTypes.CAUSE_OCR_FAILURE
                    else -> RootCauseTypes.CAUSE_SYSTEM_PIPELINE_FAILURE
                }
                RootCauseTypes.CLASS_TAXONOMY_RELATED ->
                    RootCauseTypes.CAUSE_TAXONOMY_LIMITATION
                RootCauseTypes.CLASS_MODEL_RELATED -> when {
                    !hasOcr && frameViews.isEmpty() ->
                        RootCauseTypes.CAUSE_LOW_INFORMATION_FRAME
                    prediction.category == null ->
                        RootCauseTypes.CAUSE_MISSING_EVIDENCE
                    else -> RootCauseTypes.CAUSE_DATA_VS_PREDICTION
                }
                else -> RootCauseTypes.CAUSE_UNKNOWN_DATA
            }

            // Avoid adding a duplicate primary if disagreement
            // already claimed PRIMARY.
            val hasPrimary = causes.any {
                it.role == RootCauseTypes.ROLE_PRIMARY
            }
            if (!hasPrimary) {
                add(
                    cause = primaryCause,
                    role = RootCauseTypes.ROLE_PRIMARY,
                    strength = primaryStrength(primaryCause, frameViews, hasOcr),
                    note = "Decisive attribution from decision tree step " +
                        tree.step + "."
                )
            }

            // --- Contributing causes based on OBSERVED facts ---

            // Representative-frame failure (POSSIBLE only).
            val contribution = Evidence.frameContribution(
                frames = frameViews,
                representativeFrameId = representativeFrameId
            )
            if (contribution != null &&
                contribution.note.contains("differs", ignoreCase = true)
            ) {
                add(
                    cause = RootCauseTypes.CAUSE_REPRESENTATIVE_FRAME_FAILURE,
                    role = RootCauseTypes.ROLE_CONTRIBUTING,
                    cls = RootCauseTypes.CLASS_PIPELINE_RELATED,
                    strength = RootCauseTypes.EVIDENCE_MODERATE,
                    status = RootCauseTypes.STATUS_POSSIBLE,
                    note = contribution.note
                )
            }

            // Low information / missing OCR.
            if (frameViews.isNotEmpty() && !hasOcr) {
                add(
                    cause = RootCauseTypes.CAUSE_LOW_INFORMATION_FRAME,
                    role = RootCauseTypes.ROLE_CONTRIBUTING,
                    cls = RootCauseTypes.CLASS_DATA_RELATED,
                    strength = RootCauseTypes.EVIDENCE_WEAK,
                    note = "Frames present but no OCR text available."
                )
            }

            // Rapid content change: frame categories vary.
            val distinctCategories = frameViews.map { it.category }
                .distinct().size
            if (distinctCategories > 1) {
                add(
                    cause = RootCauseTypes.CAUSE_RAPID_CONTENT_CHANGE,
                    role = RootCauseTypes.ROLE_CONTRIBUTING,
                    cls = RootCauseTypes.CLASS_DATA_RELATED,
                    strength = RootCauseTypes.EVIDENCE_MODERATE,
                    note = "$distinctCategories distinct frame categories " +
                        "observed (rapid/emotional change possible)."
                )
            }

            // Category boundary ambiguity: prediction matches one of
            // the truth's secondary labels, but not the primary.
            val truthSet = buildSet {
                CategoryCatalog.normalize(truth.category)?.let { add(it) }
                truth.secondaryCategories.forEach {
                    CategoryCatalog.normalize(it)?.let { l -> add(l) }
                }
            }
            val predNorm = CategoryCatalog.normalize(prediction.category)
            if (predNorm != null && predNorm in truthSet &&
                result.categoryCorrect != true
            ) {
                add(
                    cause = RootCauseTypes.CAUSE_CATEGORY_BOUNDARY,
                    role = RootCauseTypes.ROLE_CONTRIBUTING,
                    cls = RootCauseTypes.CLASS_TAXONOMY_RELATED,
                    strength = RootCauseTypes.EVIDENCE_MODERATE,
                    note = "Predicted category is present as a truth " +
                        "secondary; boundary between labels."
                )
            }

            // Ambiguity.
            if (truthAmbiguous) {
                add(
                    cause = RootCauseTypes.CAUSE_VISUAL_AMBIGUITY,
                    role = RootCauseTypes.ROLE_CONTRIBUTING,
                    cls = RootCauseTypes.CLASS_TAXONOMY_RELATED,
                    strength = RootCauseTypes.EVIDENCE_MODERATE,
                    status = RootCauseTypes.STATUS_SUPPORTED,
                    note = "Ground truth itself is AMBIGUOUS/MIXED."
                )
            }

            // Platform-specific.
            if (result.platformAgreement == false) {
                if (!Evidence.hasPlatformText(frameViews)) {
                    add(
                        cause = RootCauseTypes.CAUSE_PLATFORM_TEXT_MISSING,
                        role = RootCauseTypes.ROLE_CONTRIBUTING,
                        cls = RootCauseTypes.CLASS_PIPELINE_RELATED,
                        strength = RootCauseTypes.EVIDENCE_MODERATE,
                        status = RootCauseTypes.STATUS_SUPPORTED,
                        note = "Truth platform present but no platform " +
                            "text found in frames."
                    )
                } else {
                    add(
                        cause = RootCauseTypes.CAUSE_PLATFORM_UI_CONFUSION,
                        role = RootCauseTypes.ROLE_CONTRIBUTING,
                        cls = RootCauseTypes.CLASS_MODEL_RELATED,
                        strength = RootCauseTypes.EVIDENCE_WEAK,
                        note = "Platform text present yet mis-detected."
                    )
                }
            }

            // Interaction ambiguity.
            if ((result.interactionSignalsDisagreement ?: 0) > 0) {
                add(
                    cause = RootCauseTypes.CAUSE_INTERACTION_UI_AMBIGUITY,
                    role = RootCauseTypes.ROLE_CONTRIBUTING,
                    cls = RootCauseTypes.CLASS_DATA_RELATED,
                    strength = RootCauseTypes.EVIDENCE_WEAK,
                    status = RootCauseTypes.STATUS_POSSIBLE,
                    note = "Interaction signal disagreement; UI may be " +
                        "ambiguous (possible, not asserted)."
                )
            }

            // Segmentation explicit.
            if (!segmentationCorrect) {
                add(
                    cause = RootCauseTypes.CAUSE_SEGMENTATION_FAILURE,
                    role = RootCauseTypes.ROLE_CONTRIBUTING,
                    cls = RootCauseTypes.CLASS_PIPELINE_RELATED,
                    strength = RootCauseTypes.EVIDENCE_MODERATE,
                    status = RootCauseTypes.STATUS_SUPPORTED,
                    note = "Result is not comparable or has a " +
                        "segmentation error."
                )
            }

            // Short interaction.
            if (truth.durationSeconds != null &&
                truth.durationSeconds < 10
            ) {
                add(
                    cause = RootCauseTypes.CAUSE_SHORT_INTERACTION,
                    role = RootCauseTypes.ROLE_CONTRIBUTING,
                    cls = if (!evidenceSufficient) {
                        RootCauseTypes.CLASS_PIPELINE_RELATED
                    } else {
                        RootCauseTypes.CLASS_MODEL_RELATED
                    },
                    strength = RootCauseTypes.EVIDENCE_MODERATE,
                    status = RootCauseTypes.STATUS_SUPPORTED,
                    note = "Content duration ${truth.durationSeconds}s " +
                        "(< 10s); short-interaction handling applies."
                )
            }

            // Ensure at least one cause exists (fallback).
            if (causes.isEmpty()) {
                add(
                    cause = RootCauseTypes.CAUSE_UNKNOWN_DATA,
                    role = RootCauseTypes.ROLE_PRIMARY,
                    strength = RootCauseTypes.EVIDENCE_NONE,
                    status = RootCauseTypes.STATUS_INCONCLUSIVE,
                    note = "No decisive OBSERVED cause; needs human review."
                )
            }

            // Deterministic order: PRIMARY first, then contributing
            // by cause id.
            return causes.sortedWith(
                compareBy<RootCauseAssessment.CandidateCause> {
                    if (it.role == RootCauseTypes.ROLE_PRIMARY) 0 else 1
                }.thenBy { it.cause }
            )
        }

        private fun primaryStrength(
            cause: String,
            frameViews: List<Evidence.FrameView>,
            hasOcr: Boolean
        ): String {
            return when (cause) {
                RootCauseTypes.CAUSE_ANNOTATION_DISAGREEMENT ->
                    RootCauseTypes.EVIDENCE_STRONG
                RootCauseTypes.CAUSE_SEGMENTATION_FAILURE ->
                    RootCauseTypes.EVIDENCE_STRONG
                RootCauseTypes.CAUSE_OCR_FAILURE ->
                    if (frameViews.isEmpty()) {
                        RootCauseTypes.EVIDENCE_WEAK
                    } else {
                        RootCauseTypes.EVIDENCE_MODERATE
                    }
                RootCauseTypes.CAUSE_TAXONOMY_LIMITATION ->
                    RootCauseTypes.EVIDENCE_MODERATE
                else -> RootCauseTypes.EVIDENCE_WEAK
            }
        }

        // --------------------------------
        // EVIDENCE STRENGTH (independent)
        // --------------------------------

        private fun deriveEvidenceStrength(
            frameViews: List<Evidence.FrameView>,
            hasOcr: Boolean,
            multipleTruths: List<GroundTruth>
        ): String {
            var signals = 0
            if (frameViews.isNotEmpty()) signals++
            if (hasOcr) signals++
            if (multipleTruths.size > 1) signals++
            return when {
                signals >= 3 -> RootCauseTypes.EVIDENCE_STRONG
                signals == 2 -> RootCauseTypes.EVIDENCE_MODERATE
                signals == 1 -> RootCauseTypes.EVIDENCE_WEAK
                else -> RootCauseTypes.EVIDENCE_NONE
            }
        }

        // --------------------------------
        // ATTRIBUTION CONFIDENCE (independent of AI)
        // --------------------------------

        private fun deriveAttributionConfidence(
            evidenceStrength: String,
            confirmedCount: Int,
            possibleCount: Int
        ): String {
            if (confirmedCount > 0) return RootCauseTypes.CONFIDENCE_HIGH
            return when (evidenceStrength) {
                RootCauseTypes.EVIDENCE_STRONG -> RootCauseTypes.CONFIDENCE_MEDIUM
                RootCauseTypes.EVIDENCE_MODERATE -> RootCauseTypes.CONFIDENCE_MEDIUM
                else -> RootCauseTypes.CONFIDENCE_LOW
            }
        }
    }
}

private fun EvaluationUnit.primaryErrorTypeOf(): String {
    val detected = ErrorAnalyzer.detect(this)
    return detected.maxByOrNull {
        ErrorSeverity.severityRank(it.severity)
    }?.errorType ?: ErrorTypes.ERROR_UNCOMPARABLE
}
