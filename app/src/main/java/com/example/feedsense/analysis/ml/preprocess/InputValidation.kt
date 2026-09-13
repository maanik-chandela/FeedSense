package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.privacy.PrivacyFrame

// --------------------------------
// INPUT VALIDATION BOUNDARY (8B-15-3)
// --------------------------------
//
// A deterministic validation boundary before any pixel work. The
// rule is explicit: NEVER convert malformed evidence into a
// plausible-looking image and continue. Invalid evidence fails
// with an explicit, deterministic reason.

/*
 * Outcome of validating a preprocessing input.
 */
sealed class ValidationOutcome {
    object Valid : ValidationOutcome()

    data class Invalid(val reason: String) : ValidationOutcome()
}

/*
 * The deterministic structural validator.
 */
object InputValidation {

    /*
     * Validates the complete evidence boundary: privacy approval
     * PLUS structural integrity of the frame.
     */
    fun validateEvidence(evidence: PreprocessingEvidence): ValidationOutcome {
        if (!evidence.privacy.isSafeForResearchUse) {
            return ValidationOutcome.Invalid(
                "privacy status not approved: ${evidence.privacy.sanitizationStatus.label}"
            )
        }
        return validateFrame(evidence.frame)
    }

    /*
     * Structural validation of the privacy-approved frame itself.
     * The PrivacyFrame constructor already enforces positive
     * dimensions and a matching buffer; this boundary re-checks
     * explicitly so a corrupted/aliased buffer can never slip
     * through as valid.
     */
    fun validateFrame(frame: PrivacyFrame): ValidationOutcome {
        if (frame.width <= 0 || frame.height <= 0) {
            return ValidationOutcome.Invalid(
                "frame dimensions must be positive, got ${frame.width}x${frame.height}"
            )
        }
        if (frame.pixels == null || frame.pixels.size == 0) {
            return ValidationOutcome.Invalid("frame pixel buffer is null or empty")
        }
        val expected = frame.width * frame.height
        if (frame.pixels.size != expected) {
            return ValidationOutcome.Invalid(
                "frame pixel buffer size ${frame.pixels.size} does not match " +
                    "dimensions $expected"
            )
        }
        return ValidationOutcome.Valid
    }

    /*
     * Explicit validation of an arbitrary dimension/buffer-length
     * pair, so malformed bounds are caught without constructing a
     * PrivacyFrame (which would itself require consistency).
     */
    fun validateDimensions(
        width: Int,
        height: Int,
        bufferSize: Int
    ): ValidationOutcome {
        if (width <= 0 || height <= 0) {
            return ValidationOutcome.Invalid(
                "dimensions must be positive, got $width x $height"
            )
        }
        if (bufferSize != width * height) {
            return ValidationOutcome.Invalid(
                "buffer size $bufferSize does not match dimensions ${width * height}"
            )
        }
        return ValidationOutcome.Valid
    }
}