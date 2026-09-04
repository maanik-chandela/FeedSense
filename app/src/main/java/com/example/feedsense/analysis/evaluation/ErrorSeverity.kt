package com.example.feedsense.analysis.evaluation

// --------------------------------
// ERROR SEVERITY (Milestone 8A-5)
// --------------------------------
//
// Assigns a LOW / MEDIUM / HIGH / CRITICAL severity to an
// individual detected error, and decides whether a finding is
// material to the research conclusions (research impact flag).
//
// Severity is deterministic and derived from:
//   - the error type's intrinsic seriousness
//   - whether the error undermines a core capability verdict
//   - whether it involves overconfidence (an overconfident
//     error is worse research news than a low-confidence miss
//     because it means the model is silently wrong at a high
//     confidence it reports)
//
// The research-impact flag is conservative: a finding is only
// flagged material when it is likely to change how the 8A-3
// headline metric for that capability should be read. It is
// never used to inflate or deflate real numbers.

object ErrorSeverity {

    const val LOW = "LOW"
    const val MEDIUM = "MEDIUM"
    const val HIGH = "HIGH"
    const val CRITICAL = "CRITICAL"

    /*
     * Base severity of an error type (before any contextual
     * bump for overconfidence or capability criticality).
     */
    fun baseSeverity(error: String): String {
        return when (error) {
            ErrorTypes.ERROR_MISSED_DETECTION -> HIGH
            ErrorTypes.ERROR_WRONG_CLASS -> HIGH
            ErrorTypes.ERROR_EVIDENCE -> HIGH
            ErrorTypes.ERROR_OVERCONFIDENT_ERROR -> HIGH
            ErrorTypes.ERROR_FALSE_NEGATIVE -> MEDIUM
            ErrorTypes.ERROR_FALSE_POSITIVE -> MEDIUM
            ErrorTypes.ERROR_MULTI_LABEL_MISMATCH -> MEDIUM
            ErrorTypes.ERROR_UNDERCONFIDENT_CORRECT -> MEDIUM
            ErrorTypes.ERROR_DURATION -> MEDIUM
            ErrorTypes.ERROR_TEMPORAL -> MEDIUM
            ErrorTypes.ERROR_SEGMENTATION -> MEDIUM
            ErrorTypes.ERROR_PLATFORM -> MEDIUM
            ErrorTypes.ERROR_CONTENT_TYPE -> MEDIUM
            ErrorTypes.ERROR_INTERACTION -> MEDIUM
            ErrorTypes.ERROR_SKIP -> MEDIUM
            ErrorTypes.ERROR_TOPIC -> LOW
            ErrorTypes.ERROR_TONE -> LOW
            ErrorTypes.ERROR_AMBIGUOUS_CONTENT -> LOW
            ErrorTypes.ERROR_UNCOMPARABLE -> LOW
            else -> LOW
        }
    }

    /*
     * Applies contextual modifiers: an overconfident error is
     * raised one step, and errors on the core content-detection
     * capability (category/content type/platform) are raised
     * when they co-occur with overconfidence or missed detection.
     */
    fun effectiveSeverity(
        error: String,
        overconfident: Boolean,
        capability: String
    ): String {
        var severity = baseSeverity(error)
        if (overconfident && severity != CRITICAL) {
            severity = bump(severity)
        }
        if (capability == ErrorTypes.CAP_CATEGORY &&
            (error == ErrorTypes.ERROR_WRONG_CLASS ||
                error == ErrorTypes.ERROR_MISSED_DETECTION) &&
            severity != CRITICAL
        ) {
            severity = bump(severity)
        }
        return severity
    }

    fun severityRank(severity: String): Int {
        return when (severity) {
            LOW -> 0
            MEDIUM -> 1
            HIGH -> 2
            CRITICAL -> 3
            else -> -1
        }
    }

    private fun bump(severity: String): String {
        return when (severity) {
            LOW -> MEDIUM
            MEDIUM -> HIGH
            HIGH -> CRITICAL
            else -> severity
        }
    }

    /*
     * Whether a detected error is material to the research
     * conclusions. Conservative by design:
     *   - never true when there is no real data
     *   - true only for the most serious error types on core
     *     capabilities, or any CRITICAL-severity finding
     */
    fun isResearchImpactful(
        error: String,
        effectiveSeverity: String,
        realDataAvailable: Boolean
    ): Boolean {
        if (!realDataAvailable) return false
        if (effectiveSeverity == CRITICAL) return true
        if (effectiveSeverity != HIGH) return false
        return error == ErrorTypes.ERROR_WRONG_CLASS ||
            error == ErrorTypes.ERROR_MISSED_DETECTION ||
            error == ErrorTypes.ERROR_EVIDENCE
    }
}
