package com.example.feedsense.analysis.evidence

import com.example.feedsense.analysis.InteractionDetector
import java.io.File

/*
 * Milestone 8B-5.
 *
 * Interaction evidence extractor.
 *
 * Converts existing interaction signals into evidence.
 * Reuses the existing InteractionDetector.
 *
 * Possible signals:
 *   - liked=true/false/UNKNOWN
 *   - skipped=true/false/UNKNOWN
 *   - commented=true/false/UNKNOWN
 *   - shared=true/false/UNKNOWN
 *   - saved=true/false/UNKNOWN
 *
 * Important honesty rule:
 *   - Unknown remains UNKNOWN
 *   - liked=false does NOT mean user disliked it
 *   - skipped=true does NOT mean user rejected it
 *   - Never convert missing evidence into negative fact
 *
 * Design:
 *   - Reuses existing InteractionDetector
 *   - Converts InteractionSignal to Evidence
 *   - Preserves confidence levels
 *
 * Limitations:
 *   - Depends on OCR text for interaction indicators
 *   - Some interactions may not be visible on screen
 *   - Visual interaction cues not yet implemented
 */
class InteractionEvidenceExtractor :
    EvidenceExtractor {

    private val interactionDetector =
        InteractionDetector()

    override fun extract(
        frameFile: File,
        frameWidth: Int,
        frameHeight: Int,
        existingText: String?,
        existingPlatform: String?
    ): List<Evidence> {

        val signals =
            interactionDetector.detectWithEvidence(
                existingText
            )

        if (signals.isEmpty()) {
            return listOf(
                Evidence(
                    type =
                        EvidenceType.INTERACTION,
                    source =
                        EvidenceSource.INTERACTION,
                    quality =
                        EvidenceQuality.NONE,
                    value = "no_interactions",
                    metadata = mapOf(
                        "interactionsDetected" to
                                "false"
                    )
                )
            )
        }

        return signals.map { signal ->
            val quality = when {
                signal.confidence ==
                        com.example.feedsense
                            .analysis
                            .ConfidenceLevel.HIGH ->
                    EvidenceQuality.HIGH
                signal.confidence ==
                        com.example.feedsense
                            .analysis
                            .ConfidenceLevel.MEDIUM ->
                    EvidenceQuality.MEDIUM
                else ->
                    EvidenceQuality.LOW
            }

            Evidence(
                type = EvidenceType.INTERACTION,
                source =
                    EvidenceSource.INTERACTION,
                quality = quality,
                value =
                    "${signal.signal}=${signal.evidence}",
                metadata = mapOf(
                    "signal" to signal.signal,
                    "confidence" to
                            signal.confidence.name,
                    "evidence" to signal.evidence
                )
            )
        }
    }
}
