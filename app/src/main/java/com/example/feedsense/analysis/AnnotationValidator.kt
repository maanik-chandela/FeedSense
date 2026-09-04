package com.example.feedsense.analysis

import com.example.feedsense.model.GroundTruth

// --------------------------------
// ANNOTATION VALIDATOR (Milestone 8A-2)
// --------------------------------
//
// Pure, UI-independent validation + normalization of a
// human annotation draft before it is committed to the
// ground_truths table.
//
// Rules enforced (Steps 6, 9, 12, 14 of the 8A-2 spec):
//   P1  primary category must be a valid catalog category
//       OR the evaluator explicitly marks it UNKNOWN by
//       choosing ambiguity = UNKNOWN (UNKNOWN is a human
//       judgment, NOT an AI error).
//   P2  ambiguity must be chosen (one of VALID_AMBIGUITY).
//   P3  secondary categories are normalized to canonical
//       keys and de-duplicated; the primary is never
//       repeated as a secondary.
//   P4  platform (when given) must come from the canonical
//       platform list.
//   P5  content type must be in VALID_CONTENT_TYPES.
//   P6  tone (when given) must come from the canonical tone
//       list.
//   P7  interaction + skipped fields stay tri-state
//       (null=UNKNOWN / true / false). The validator and UI
//       NEVER convert an UNKNOWN into false.
//
// All taxonomy membership checks delegate to the handled
// catalogs so there is one source of truth.

object AnnotationValidator {

    class ValidationException(
        val errors: List<FieldError>
    ) : IllegalArgumentException(
        errors.joinToString(", ") {
            "${it.field}: ${it.message}"
        }
    )

    data class FieldError(
        val field: String,
        val message: String
    )

    data class Result(
        val errors: List<FieldError>,
        val normalized: GroundTruth?
    ) {
        val isValid: Boolean
            get() = errors.isEmpty()
    }

    val FIELD_PRIMARY_CATEGORY = "category"
    val FIELD_SECONDARY_CATEGORIES = "secondaryCategories"
    val FIELD_AMBIGUITY = "ambiguity"
    val FIELD_PLATFORM = "platform"
    val FIELD_CONTENT_TYPE = "contentType"
    val FIELD_TONE = "tone"

    /*
     * Validates and normalizes a draft GroundTruth.
     *
     * The draft must already carry evaluationItemId and
     * annotatorId set by the caller; this function focuses
     * on the *human answer* fields.
     */
    fun validate(
        draft: GroundTruth
    ): Result {

        val errors = mutableListOf<FieldError>()

        // ---- P1/P2: primary or explicit unknown ----
        val primary =
            CategoryCatalog.normalize(draft.category)

        if (primary == null &&
            draft.ambiguity != GroundTruth.AMBIGUITY_UNKNOWN) {

            errors += FieldError(
                FIELD_PRIMARY_CATEGORY,
                "Choose a primary category or mark the " +
                    "item as UNKNOWN."
            )
        }

        if (draft.ambiguity.isBlank() ||
            draft.ambiguity !in GroundTruth.VALID_AMBIGUITY) {

            errors += FieldError(
                FIELD_AMBIGUITY,
                "Ambiguity must be one of " +
                    GroundTruth.VALID_AMBIGUITY.joinToString(", ") +
                    "."
            )
        }

        // ---- P3: normalize + dedup secondaries ----
        val secondary = buildList {
            draft.secondaryCategories.forEach { raw ->
                CategoryCatalog.normalize(raw)
                    ?.takeIf { it != primary }
                    ?.let { if (it !in this) add(it) }
            }
        }

        // ---- P4: platform ----
        val platform = draft.platform?.takeIf {
            it.isNotBlank()
        }
        if (platform != null &&
            platform !in PlatformDetector().platformNames()) {

            errors += FieldError(
                FIELD_PLATFORM,
                "Platform \"$platform\" is not in the " +
                    "canonical platform list."
            )
        }

        // ---- P5: content type ----
        val contentType =
            draft.contentType?.takeIf { it.isNotBlank() }
        if (contentType != null &&
            contentType !in GroundTruth.VALID_CONTENT_TYPES) {

            errors += FieldError(
                FIELD_CONTENT_TYPE,
                "Content type \"$contentType\" is " +
                    "not a supported content type."
            )
        }

        // ---- P6: tone ----
        val tone = draft.tone?.takeIf { it.isNotBlank() }
        if (tone != null &&
            tone !in TextHeuristicClassifier.TONE_VALUES()) {

            errors += FieldError(
                FIELD_TONE,
                "Tone \"$tone\" is not in the canonical " +
                    "tone list."
            )
        }

        // ---- P7: tri-state integrity ----
        // The Boolean? fields are already tri-state by type
        // (null=UNKNOWN, true/false). The UI defaults every
        // interaction to null (UNKNOWN) and never fills it
        // with false; nothing extra to reject here because
        // false must be an explicit evaluator choice.

        val normalized =
            if (errors.isEmpty()) {
                draft.copy(
                    category = primary,
                    secondaryCategories = secondary,
                    ambiguity = draft.ambiguity,
                    platform = platform,
                    contentType = contentType,
                    tone = tone,
                    interactionSignals =
                        signalsFor(draft)
                )
            } else {
                null
            }

        return Result(errors, normalized)
    }

    /*
     * Derives the legacy pipe-friendly interaction-signal
     * list from the tri-state fields: only signals the
     * evaluator marked TRUE appear, matching how the AI's
     * interactionSignals is expressed. FALSE and UNKNOWN
     * (null) contribute nothing, preserving honesty.
     */
    fun signalsFor(
        draft: GroundTruth
    ): List<String> {
        return GroundTruth.signalsMarkedTrue(draft)
    }
}
