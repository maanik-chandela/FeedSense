package com.example.feedsense.analysis.ml.evaluation

import com.example.feedsense.analysis.ml.taxonomy.MappingFailureCode
import com.example.feedsense.analysis.ml.taxonomy.MappingStatus
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingResult

// --------------------------------
// EVALUATION ELIGIBILITY (8B-15-8)
// --------------------------------
//
// An explicit, deterministic eligibility decision for
// whether a prediction candidate can participate in
// structural comparison against ground truth.
//
// Eligibility is INDEPENDENT from prediction confidence.
// A prediction with high model confidence can still be
// ineligible because its taxonomy mapping is ambiguous.
// A prediction with low model confidence can still be
// structurally eligible.
//
// Do not use probability thresholds in this decision.
// Do not use model confidence to alter ground truth.

/*
 * Explicit eligibility status for evaluation comparison.
 */
enum class EligibilityStatus(val label: String) {
    ELIGIBLE("ELIGIBLE"),
    NOT_ELIGIBLE_UNMAPPED("NOT_ELIGIBLE_UNMAPPED"),
    NOT_ELIGIBLE_AMBIGUOUS("NOT_ELIGIBLE_AMBIGUOUS"),
    NOT_ELIGIBLE_INVALID("NOT_ELIGIBLE_INVALID"),
    NOT_ELIGIBLE_UNSUPPORTED("NOT_ELIGIBLE_UNSUPPORTED"),
    NOT_ELIGIBLE_REJECTED("NOT_ELIGIBLE_REJECTED"),
    NOT_ELIGIBLE_MISSING_OUTPUT("NOT_ELIGIBLE_MISSING_OUTPUT"),
    NOT_ELIGIBLE_MISSING_TAXONOMY("NOT_ELIGIBLE_MISSING_TAXONOMY"),
    NOT_ELIGIBLE_MISSING_MAPPING("NOT_ELIGIBLE_MISSING_MAPPING"),
    NOT_ELIGIBLE_VERSION_MISMATCH("NOT_ELIGIBLE_VERSION_MISMATCH")
}

/*
 * Deterministic eligibility decision for a single
 * prediction candidate.
 *
 * The decision is derived entirely from structural properties:
 *   - mapping status (DIRECT/MAPPED/UNMAPPED/AMBIGUOUS/etc.)
 *   - taxonomy version compatibility
 *   - prediction output availability
 *   - mapping table availability
 *
 * It does NOT depend on:
 *   - model confidence or prediction score
 *   - ground truth content
 *   - human annotation decisions
 *   - evaluation metrics
 */
data class EvaluationEligibility(
    val status: EligibilityStatus,
    val reason: EvaluationDecisionReason,
    val reasonDetail: String? = null,
    val mappingStatus: MappingStatus? = null,
    val expectedTaxonomyVersion: String? = null,
    val actualTaxonomyVersion: String? = null,
    val failureCode: MappingFailureCode? = null
) {
    val isEligible: Boolean
        get() = status == EligibilityStatus.ELIGIBLE

    val isNotEligible: Boolean
        get() = status != EligibilityStatus.ELIGIBLE

    companion object {

        // --------------------------------
        // PUBLIC API: from mapping result
        // --------------------------------

        /*
         * Derive eligibility from a taxonomy mapping result.
         *
         * This is the core deterministic decision function.
         * The same mapping result always produces the same
         * eligibility decision.
         */
        fun fromMappingResult(
            mappingResult: TaxonomyMappingResult,
            expectedTaxonomyVersion: String? = null
        ): EvaluationEligibility {

            return when (mappingResult) {
                is TaxonomyMappingResult.Mapped -> {
                    checkMappedEligibility(
                        mappingResult,
                        expectedTaxonomyVersion
                    )
                }

                is TaxonomyMappingResult.Unmapped -> {
                    EvaluationEligibility(
                        status = EligibilityStatus.NOT_ELIGIBLE_UNMAPPED,
                        reason = EvaluationDecisionReason.UNMAPPED_MODEL_LABEL,
                        reasonDetail = mappingResult.reason,
                        mappingStatus = MappingStatus.UNMAPPED
                    )
                }

                is TaxonomyMappingResult.Ambiguous -> {
                    EvaluationEligibility(
                        status = EligibilityStatus.NOT_ELIGIBLE_AMBIGUOUS,
                        reason = EvaluationDecisionReason.AMBIGUOUS_MAPPING,
                        reasonDetail = mappingResult.ambiguityReason,
                        mappingStatus = MappingStatus.AMBIGUOUS
                    )
                }

                is TaxonomyMappingResult.Invalid -> {
                    resolveInvalidEligibility(mappingResult)
                }
            }
        }

        // --------------------------------
        // PUBLIC API: explicit states
        // --------------------------------

        fun missingOutput(): EvaluationEligibility {
            return EvaluationEligibility(
                status = EligibilityStatus.NOT_ELIGIBLE_MISSING_OUTPUT,
                reason = EvaluationDecisionReason.MISSING_PREDICTION,
                reasonDetail = "No model prediction available"
            )
        }

        fun missingTaxonomy(): EvaluationEligibility {
            return EvaluationEligibility(
                status = EligibilityStatus.NOT_ELIGIBLE_MISSING_TAXONOMY,
                reason = EvaluationDecisionReason.MISSING_TAXONOMY_VERSION,
                reasonDetail = "Taxonomy version not specified"
            )
        }

        fun missingMapping(): EvaluationEligibility {
            return EvaluationEligibility(
                status = EligibilityStatus.NOT_ELIGIBLE_MISSING_MAPPING,
                reason = EvaluationDecisionReason.MISSING_MAPPING,
                reasonDetail = "No taxonomy mapping provided"
            )
        }

        fun versionMismatch(
            expected: String,
            actual: String
        ): EvaluationEligibility {
            return EvaluationEligibility(
                status = EligibilityStatus.NOT_ELIGIBLE_VERSION_MISMATCH,
                reason = EvaluationDecisionReason.TAXONOMY_VERSION_MISMATCH,
                reasonDetail = "Expected taxonomy version " +
                    "'$expected', got '$actual'",
                expectedTaxonomyVersion = expected,
                actualTaxonomyVersion = actual
            )
        }

        // --------------------------------
        // INTERNAL
        // --------------------------------

        private fun checkMappedEligibility(
            mapped: TaxonomyMappingResult.Mapped,
            expectedTaxonomyVersion: String?
        ): EvaluationEligibility {

            if (expectedTaxonomyVersion != null &&
                expectedTaxonomyVersion != mapped.taxonomyVersion
            ) {
                return EvaluationEligibility(
                    status = EligibilityStatus.NOT_ELIGIBLE_VERSION_MISMATCH,
                    reason = EvaluationDecisionReason.TAXONOMY_VERSION_MISMATCH,
                    reasonDetail = "Mapping taxonomy version " +
                        "'${mapped.taxonomyVersion}' does not match " +
                        "expected '$expectedTaxonomyVersion'",
                    mappingStatus = mapped.status,
                    expectedTaxonomyVersion = expectedTaxonomyVersion,
                    actualTaxonomyVersion = mapped.taxonomyVersion
                )
            }

            val reason = when (mapped.status) {
                MappingStatus.DIRECT ->
                    EvaluationDecisionReason.VALID_DIRECT_MAPPING
                MappingStatus.MAPPED ->
                    EvaluationDecisionReason.VALID_MAPPED_MAPPING
                else ->
                    EvaluationDecisionReason.VALID_DIRECT_MAPPING
            }

            return EvaluationEligibility(
                status = EligibilityStatus.ELIGIBLE,
                reason = reason,
                reasonDetail = "Prediction mapped to " +
                    "'${mapped.feedSenseTaxonomyKey}' " +
                    "via ${mapped.status.label}",
                mappingStatus = mapped.status
            )
        }

        private fun resolveInvalidEligibility(
            invalid: TaxonomyMappingResult.Invalid
        ): EvaluationEligibility {

            val status = when (invalid.failureCode) {
                MappingFailureCode.INCOMPATIBLE_MAPPING ->
                    EligibilityStatus.NOT_ELIGIBLE_REJECTED
                else ->
                    EligibilityStatus.NOT_ELIGIBLE_INVALID
            }

            val reason = when (invalid.failureCode) {
                MappingFailureCode.INVALID_MODEL_INDEX,
                MappingFailureCode.MISSING_TAXONOMY_KEY,
                MappingFailureCode.INVALID_TAXONOMY_KEY,
                MappingFailureCode.OBSOLETE_TAXONOMY_VERSION,
                MappingFailureCode.WRONG_ARTIFACT_IDENTITY,
                MappingFailureCode.DUPLICATE_MAPPING,
                MappingFailureCode.CONFLICTING_MAPPING,
                MappingFailureCode.MAPPING_VERSION_MISMATCH,
                MappingFailureCode.VALIDATION_ERROR ->
                    EvaluationDecisionReason.INVALID_MAPPING
                MappingFailureCode.INCOMPATIBLE_MAPPING ->
                    EvaluationDecisionReason.REJECTED_MAPPING
            }

            return EvaluationEligibility(
                status = status,
                reason = reason,
                reasonDetail = invalid.failureReason,
                mappingStatus = null,
                failureCode = invalid.failureCode
            )
        }
    }
}
