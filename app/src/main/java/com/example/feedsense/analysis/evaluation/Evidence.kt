package com.example.feedsense.analysis.evaluation

import java.time.LocalDateTime

// --------------------------------
// EVIDENCE MODEL (Milestone 8A-6)
// --------------------------------
//
// Evidence references EXISTING objects (frameId, captureId,
// evaluationItemId, predictionId) rather than duplicating
// screenshots or OCR text. No raw screenshots are stored or
// serialized here; screen content is only ever referenced by
// id so privacy stays local and images remain on-device.
//
// The temporal window is BOUNDED (before + after the item),
// never an entire session.

object Evidence {

    // A single evidence item: a type + a reference to existing
    // data + a short human summary (no raw screen pixels).
    data class EvidenceRef(
        val type: String,
        val referenceId: String?,     // frameId / captureId / etc.
        val kind: String,             // "frame" / "prediction" / "truth" / ...
        val note: String
    )

    // A single frame's diagnostic view extracted from a frame.
    // Decoupled from CapturedFrame's JSON so the analyzer is
    // unit-testable and the DAO adapter can build it from real
    // frames later.
    data class FrameView(
        val frameId: String,
        val capturedAt: LocalDateTime,
        val category: String? = null,
        val confidence: Double? = null,
        val ocrText: String? = null,
        val platformText: String? = null,
        val needsReview: Boolean = false
    )

    // The bounded temporal window around a failing item.
    data class TemporalWindow(
        val itemId: String,
        val before: List<FrameView>,
        val current: List<FrameView>,
        val after: List<FrameView>
    )

    // One frame's contribution to the predicted category,
    // used to surface possible representative-frame failure.
    data class FrameContribution(
        val frameId: String,
        val category: String?,
        val confidence: Double?,
        val note: String
    )

    /**
     * A bounded temporal window of frames ordered
     * chronologically. The window is capped so extremely
     * long sessions never load in full.
     */
    fun temporalWindow(
        itemId: String,
        frames: List<FrameView>,
        representativeFrameId: String?,
        itemStart: LocalDateTime?,
        itemEnd: LocalDateTime?,
        beforeCount: Int = 3,
        afterCount: Int = 3
    ): TemporalWindow? {
        if (frames.isEmpty()) return null
        val sorted = frames.sortedBy { it.capturedAt }

        val current = sorted.filter { view ->
            (itemStart == null || !view.capturedAt.isBefore(itemStart) ||
                view.frameId == representativeFrameId)
        }.let { inRange ->
            if (inRange.isNotEmpty()) {
                inRange
            } else {
                // Fall back to all frames if the item window is
                // empty (e.g. a frame mismatched the window).
                sorted
            }
        }

        val currentIds = current.map { it.frameId }.toSet()
        val before = sorted.filter {
            it.frameId !in currentIds &&
                (itemStart == null || it.capturedAt.isBefore(itemStart))
        }.takeLast(beforeCount)
        val after = sorted.filter {
            it.frameId !in currentIds &&
                (itemEnd == null || !it.capturedAt.isBefore(itemEnd))
        }.take(afterCount)

        return TemporalWindow(
            itemId = itemId,
            before = before,
            current = current,
            after = after
        )
    }

    /**
     * Aggregates frame-level category/confidence contribution.
     * Returns the most-confident non-null frame category, with
     * a note describing whether the representative frame
     * disagreed with the majority, so a representative-frame
     * failure can be surfaced as POSSIBLE (never automatic).
     */
    fun frameContribution(
        frames: List<FrameView>,
        representativeFrameId: String?
    ): FrameContribution? {
        val withCategory = frames.filter { it.category != null }
        if (withCategory.isEmpty()) return null

        val byCategory = withCategory.groupBy { it.category }
        val topCategory = byCategory.maxByOrNull { it.value.size }!!.key
        val repFrame = withCategory.firstOrNull {
            it.frameId == representativeFrameId
        }
        val repCategory = repFrame?.category

        val note = if (repCategory != null && repCategory != topCategory) {
            "Representative frame category ($repCategory) " +
                "differs from the majority frame category " +
                "($topCategory); possible representative-frame " +
                "failure - POSSIBLE, not asserted."
        } else {
            "Representative frame aligns with the majority frame " +
                "category ($topCategory)."
        }

        return FrameContribution(
            frameId = representativeFrameId ?: repFrame?.frameId ?: "",
            category = topCategory,
            confidence = repFrame?.confidence,
            note = note
        )
    }

    /**
     * Whether OCR evidence is missing/empty for the frames.
     */
    fun hasOcr(frames: List<FrameView>): Boolean {
        return frames.any { !it.ocrText.isNullOrBlank() }
    }

    /**
     * Whether platform text evidence is present.
     */
    fun hasPlatformText(frames: List<FrameView>): Boolean {
        return frames.any { !it.platformText.isNullOrBlank() }
    }

    fun ocrSample(frames: List<FrameView>): String? {
        return frames.firstNotNullOfOrNull { it.ocrText }
            ?.take(200)
    }
}
