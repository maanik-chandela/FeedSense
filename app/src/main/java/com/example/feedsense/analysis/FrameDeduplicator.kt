package com.example.feedsense.analysis

import org.json.JSONObject

/*
 * Milestone 7S.
 *
 * Frame deduplication: identical screen content in the
 * same session must be analyzed once. The worker computes
 * the perceptual fingerprint BEFORE running the pipeline
 * and asks this class whether a previous frame already
 * classified the same content. When it has, the stored
 * classification is reused and the expensive local/cloud
 * pipeline is skipped (battery + CPU + duplicate cloud
 * requests).
 *
 * This class is pure so the reuse decision and the
 * result-JSON rewriting are unit-testable.
 */
class FrameDeduplicator {

    companion object {

        const val STATUS_REUSED_ANALYSIS = "REUSED_ANALYSIS"
        const val REUSED_MESSAGE_PREFIX = "reused-analysis"
    }

    /*
     * True when a prior frame with the same fingerprint
     * already produced a stored classification.
     */
    fun isReusable(
        prior: CapturedFrameLike?,
        currentFrameId: String
    ): Boolean {
        return prior != null &&
                prior.id != currentFrameId &&
                !prior.analysisResult.isNullOrBlank()
    }

    /*
     * Rewrites a stored classification JSON so it can be
     * stored on the duplicate frame. Classification truth
     * (category, topic, tone, confidence, source, ...) is
     * carried over because the content is identical; only
     * per-file fields and the status change.
     *
     * Returns null when the stored JSON cannot be parsed -
     * callers must fall back to the real pipeline then.
     */
    fun reuseResultJson(
        priorResult: String,
        fileName: String,
        fileSizeBytes: Long,
        fingerprint: String
    ): JSONObject? {

        return try {

            val prior =
                JSONObject(
                    priorResult
                )

            val reused =
                JSONObject()

            // ---------------------------
            // Per-frame fields
            // ---------------------------
            reused.put("status", STATUS_REUSED_ANALYSIS)
            reused.put("fileName", fileName)
            reused.put("fileSizeBytes", fileSizeBytes)
            reused.put("frameFingerprint", fingerprint)
            reused.put(
                "message",
                prior.optString("message", "") + " | " +
                        REUSED_MESSAGE_PREFIX + ":content-identical"
            )

            // ---------------------------
            // Classification fields
            // ---------------------------
            copyField(prior, reused, "screenType")
            copyField(prior, reused, "width")
            copyField(prior, reused, "height")
            copyField(prior, reused, "application")
            copyField(prior, reused, "activity")
            copyField(prior, reused, "visibleText")
            copyField(prior, reused, "confidence")
            copyField(prior, reused, "classificationReason")
            copyField(prior, reused, "contentCategory")
            copyField(prior, reused, "secondaryCategories")
            copyField(prior, reused, "topic")
            copyField(prior, reused, "tone")
            copyField(prior, reused, "contentType")
            copyField(prior, reused, "estimatedDurationSeconds")
            copyField(prior, reused, "interactionSignals")
            copyField(prior, reused, "interactionEvidence")
            copyField(prior, reused, "ambiguityScore")
            copyField(prior, reused, "modelVersion")
            copyField(prior, reused, "source")
            copyField(prior, reused, "disposition")
            copyField(prior, reused, "needsReview")
            copyField(prior, reused, "uncertain")

            reused

        } catch (exception: Exception) {

            null
        }
    }

    private fun copyField(
        from: JSONObject,
        to: JSONObject,
        key: String
    ) {
        if (from.has(key)) {
            to.put(key, from.get(key))
        }
    }
}

/*
 * Minimal view of a stored frame so the deduplicator does
 * not depend on the Room entity (easier unit testing).
 */
interface CapturedFrameLike {

    val id: String

    val analysisResult: String?
}
