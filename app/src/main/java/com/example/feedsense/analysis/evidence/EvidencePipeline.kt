package com.example.feedsense.analysis.evidence

import java.io.File

/*
 * Milestone 8B-5.
 *
 * Evidence pipeline.
 *
 * Orchestrates evidence extraction and fusion.
 * This is the main entry point for the evidence
 * layer.
 *
 * Architecture:
 *   Frame → Extractors → Evidence[] → Fusion → Result
 *
 * Design:
 *   - Deterministic: same input → same output
 *   - Injectable: testable without Android
 *   - Composable: extractors can be added/removed
 *   - Auditable: evidence snapshot preserved
 *
 * Usage:
 *   val pipeline = EvidencePipeline()
 *   val result = pipeline.process(
 *       frameFile = file,
 *       ocrText = "IPL 2026 cricket",
 *       platform = "Instagram"
 *   )
 *
 * Limitations:
 *   - v1 is text-heavy (visual/layout are lightweight)
 *   - No automatic content detection
 *   - Weights are heuristic, not learned
 */
class EvidencePipeline(
    private val extractors: List<EvidenceExtractor> =
        DEFAULT_EXTRACTORS,
    private val fusionEngine: EvidenceFusionEngine =
        EvidenceFusionEngine(),
    private val metrics: EvidenceMetrics =
        EvidenceMetrics()
) {

    /*
     * Process a frame through the evidence pipeline.
     *
     * @param frameFile The captured frame image.
     * @param frameWidth Width in pixels (0 if unknown).
     * @param frameHeight Height in pixels (0 if unknown).
     * @param ocrText Pre-extracted OCR text if available.
     * @param platform Pre-detected platform if available.
     * @param durationMs Content duration in milliseconds.
     * @param frameCount Number of frames in the item.
     * @return EvidenceFusionResult with fused predictions.
     */
    fun process(
        frameFile: File,
        frameWidth: Int = 0,
        frameHeight: Int = 0,
        ocrText: String? = null,
        platform: String? = null,
        durationMs: Long = 0L,
        frameCount: Int = 0
    ): EvidenceFusionResult {

        metrics.recordReceived()

        val startTimeNs =
            System.nanoTime()

        return try {
            // --------------------------------
            // EXTRACT EVIDENCE
            // --------------------------------

            val allEvidence =
                mutableListOf<Evidence>()

            for (extractor in extractors) {
                try {
                    val evidence =
                        extractor.extract(
                            frameFile,
                            frameWidth,
                            frameHeight,
                            ocrText,
                            platform
                        )
                    allEvidence.addAll(evidence)
                } catch (_: Exception) {
                    // Extractor failure: skip, continue
                }
            }

            // --------------------------------
            // TEMPORAL EVIDENCE
            // --------------------------------
            //
            // Add temporal evidence from explicit
            // context if available.

            if (durationMs > 0 || frameCount > 0) {
                val temporalEvidence =
                    TemporalEvidenceExtractor
                        .fromTemporalContext(
                            durationMs = durationMs,
                            frameCount = frameCount
                        )
                allEvidence.addAll(temporalEvidence)
            }

            // --------------------------------
            // FUSE EVIDENCE
            // --------------------------------

            val result =
                fusionEngine.fuse(allEvidence)

            val elapsedNs =
                System.nanoTime() - startTimeNs

            metrics.recordTiming(elapsedNs)
            metrics.recordFused(
                result.evidenceCount,
                result.independentSources
            )

            result

        } catch (exception: Exception) {

            val elapsedNs =
                System.nanoTime() - startTimeNs

            metrics.recordTiming(elapsedNs)
            metrics.recordFailed()

            EvidenceFusionResult.EMPTY
        }
    }

    companion object {

        /*
         * Default extractors in processing order.
         *
         * Order matters: earlier extractors may provide
         * data that later extractors reuse (e.g., OCR
         * text is reused by platform and interaction
         * extractors).
         */
        val DEFAULT_EXTRACTORS =
            listOf(
                OcrEvidenceExtractor(),
                PlatformEvidenceExtractor(),
                VisualEvidenceExtractor(),
                LayoutEvidenceExtractor(),
                InteractionEvidenceExtractor()
            )
    }
}
