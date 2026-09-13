package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * Result of one PrivacyProcessor.process() call.
 *
 * The SAFE frame is the in-memory ARGB buffer - an object that
 * lives only inside the caller's process, never serialized. It
 * is either:
 *   - the very buffer that was passed in (mutated in place on
 *     the common path; the raw copy is gone),
 *   - a NEW buffer when CROP removed a band,
 *   - null when the frame was dropped (DROP_FRAME).
 *
 * All SERIALIZABLE data lives on the decision, which is
 * guaranteed free of raw bytes, text, and coordinates.
 */
data class AnonymizationResult(
    val decision: PrivacyDecision,
    val safeFrame: PrivacyFrame?
) {

    /*
     * Privacy-safe, ordered metadata for export (spec §20).
     * Maps only; no JSON dependency is needed in the pure-JVM
     * core, and raw content can never appear here.
     */
    fun toSafeMetadataMap(): Map<String, String> {
        val transformationLog = decision.transformations
            .groupBy { it.regionType }
            .entries
            .sortedBy { it.key.label }
            .joinToString("|") { (type, applied) ->
                val strongest = applied.maxByOrNull { it.transformation.severity }
                val count = applied.size
                "${type.label}:${strongest?.transformation?.label}($count)"
            }

        return linkedMapOf(
            "frameId" to decision.frameId,
            "policyVersion" to decision.policyVersion,
            "processingVersion" to decision.processingVersion,
            "rulesVersion" to decision.rulesVersion,
            "ocrAvailability" to decision.ocrAvailability.label,
            "regionsDetected" to decision.regionsDetected.toString(),
            "regionsProcessed" to decision.regionsEnabled.toString(),
            "risk" to decision.risk.label,
            "confidence" to decision.confidence.label,
            "status" to decision.status.label,
            "dropped" to decision.dropped.toString(),
            "evidenceLoss" to decision.evidenceLoss.label,
            "transformations" to transformationLog
        )
    }

    /*
     * A single-line, privacy-safe log representation. Guaranteed
     * free of raw content (spec §19).
     */
    fun toSafeLogLine(): String {
        return toSafeMetadataMap().entries
            .joinToString(prefix = "privacy_processing ", separator = ", ") {
                "${it.key}=${it.value}"
            }
    }

    /*
     * Convenience: attaches the decision-level metadata to an
     * 8B-10 audit so the existing evidence pipeline can consume
     * the processing outcome without copying pixels.
     */
    fun toSanitizationAudit(): SanitizationAudit {
        return SanitizationAudit(
            frameId = decision.frameId,
            sanitizationVersion = decision.processingVersion,
            policyVersion = decision.policyVersion,
            status = decision.status,
            regionsDetected = decision.regionsDetected,
            processedRegionCount = decision.regionsEnabled,
            regionTypeCounts = decision.transformations
                .groupingBy { it.regionType }
                .eachCount(),
            redactedTextSegments = 0,
            redactionAffectedDecision = decision.dropped,
            evidenceLostDueToSanitization =
                decision.evidenceLoss != PrivacyEvidenceLoss.NONE,
            availability = availabilityFor(decision),
            processingTimestampMs = decision.timestampMs
        )
    }

    /*
     * Maps the processing outcome to the 8B-10 availability
     * vocabulary. A privacy-dropped frame had content (unlike a
     * FLAG_SECURE surface), so it remains CONTENT_DETECTED.
     */
    private fun availabilityFor(
        decision: PrivacyDecision
    ): EvidenceAvailability {
        if (decision.regionsDetected == 0 &&
            decision.ocrAvailability == OcrAvailability.OCR_UNAVAILABLE
        ) {
            return EvidenceAvailability.UNKNOWN_AVAILABILITY
        }
        return EvidenceAvailability.CONTENT_DETECTED
    }
}