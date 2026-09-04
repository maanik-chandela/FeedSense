package com.example.feedsense.analysis.fusion

import com.example.feedsense.model.FeedItem

// --------------------------------
// FUSION MODELS / FACTORIES (Milestone 8B-1)
// --------------------------------
//
// Assembles FrameSignals from existing model snapshots so the
// fusion model can run against the SAME inputs the legacy model
// already consumed, without re-running OCR or touching storage.

object FusionModels {

    /**
     * Builds a single-frame context from a FeedItem snapshot,
     * reusing whatever signals the pipeline already stored. Used
     * for the legacy-vs-fusion comparison path (8B-1 section 4).
     */
    fun frameSignalsFromFeedItem(
        item: FeedItem,
        frameId: String = "frame-" + item.id
    ): FrameSignals {
        return FrameSignals(
            frameId = frameId,
            timestamp = item.startTime.toString(),
            ocrText = null,
            platform = item.platform,
            interactionSignals = item.interactionEvidence.toSet(),
            existingCategory = item.category,
            existingConfidence = item.confidence,
            contentType = item.contentType,
            topic = item.topic,
            tone = item.tone
        )
    }

    /**
     * Builds a context directly from OCR text, for the explicit
     * analysis path where freshly-captured frame signals are used.
     */
    fun contextFromText(
        ocrText: String,
        platform: String? = null,
        interactionSignals: Set<String> = emptySet()
    ): EvidenceContext {
        return EvidenceContext.single(
            FrameSignals(
                frameId = "frame-1",
                timestamp = "t0",
                ocrText = ocrText,
                platform = platform,
                interactionSignals = interactionSignals
            )
        )
    }
}
