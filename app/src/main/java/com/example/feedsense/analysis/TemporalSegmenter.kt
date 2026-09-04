package com.example.feedsense.analysis

import com.example.feedsense.model.CapturedFrame
import java.time.Duration
import kotlin.math.abs
import org.json.JSONObject

// --------------------------------
// TEMPORAL SEGMENTATION
// --------------------------------
//
// Milestone 7D (Level 3).
//
// Frames are interpreted as a SEQUENCE, not as
// independent screenshots. Each transition between two
// consecutive frames is classified as either a
// continuation of the current content piece or the
// start of a new one.
//
// Signals used:
//
// - timestamp gap          (GAP)
// - perceptual fingerprint (FINGERPRINT)
// - category continuity    (CATEGORY)
// - OCR text similarity    (CONTINUATION override)
//
// Output: a list of ItemSegments where every frame
// carries a lifecycle state:
//
//   START          first frame of a content piece
//   CONTINUATION   middle frame
//   CONTENT_CHANGE a new piece began here
//   END            last frame of a content piece
//
// This improves on the 7C time + category heuristics by
// separating back-to-back content with fingerprints and
// by refusing to split identical text when the visuals
// barely differ.
//

enum class FrameLifecycleState {
    START,
    CONTINUATION,
    CONTENT_CHANGE,
    END
}

enum class SplitReason {
    GAP,
    FINGERPRINT,
    PLATFORM,
    CATEGORY,
    VISUAL_TEXT,
    /*
     * Milestone 7X. Same category, but the topic clearly
     * changed: back-to-back pieces on different subjects
     * must not merge just because they share a label.
     */
    TOPIC,
    /*
     * Milestone 7X. Same category, but a different creator
     * handle is on screen: a different creator is a
     * different piece.
     */
    CREATOR
}

data class ItemSegment(
    val frames: List<CapturedFrame>,
    val states: List<FrameLifecycleState>,
    val startReason: SplitReason?
)

class TemporalSegmenter(
    private val maxMergeGapMs: Long =
        DEFAULT_MAX_MERGE_GAP_MS,

    private val nullCategoryGapMs: Long =
        DEFAULT_NULL_CATEGORY_GAP_MS,

    private val hashSplitDistance: Int =
        DEFAULT_HASH_SPLIT_DISTANCE,

    private val ocrSimilarityThreshold: Double =
        DEFAULT_OCR_SIMILARITY_THRESHOLD,

    /*
     * Milestone 7X. Text similarity must be BELOW this
     * for a creator-handle change to split same-category
     * content. Looser than the topic bar because the
     * handle is a small part of the caption.
     */
    private val creatorTextSimilarityThreshold: Double =
        DEFAULT_CREATOR_TEXT_SIMILARITY,

    /*
     * Milestone 7F (Part 1). A same-category pair is
     * split when the visuals changed moderately AND the
     * visible text clearly changed. This catches
     * back-to-back Reels in the same category that the
     * raw fingerprint threshold alone would not separate.
     */
    private val combinedSplitDistance: Int =
        DEFAULT_COMBINED_SPLIT_DISTANCE,

    private val combinedTextSimilarityThreshold: Double =
        DEFAULT_COMBINED_TEXT_SIMILARITY
) {

    fun segment(
        frames: List<CapturedFrame>
    ): List<ItemSegment> {

        if (frames.isEmpty()) {
            return emptyList()
        }

        val segments =
            mutableListOf<ItemSegment>()

        val current =
            mutableListOf<CapturedFrame>()

        val states =
            mutableListOf<FrameLifecycleState>()

        var startReason: SplitReason? = null

        for (frame in frames) {

            if (current.isEmpty()) {

                current.add(frame)
                states.add(FrameLifecycleState.START)
                continue
            }

            val previous =
                current.last()

            val transition =
                transition(
                    previous,
                    frame
                )

            if (transition.split) {

                /*
                 * Mark the outgoing item's last frame as
                 * END. A single-frame item keeps START:
                 * it is the whole piece.
                 */
                if (states.size > 1) {
                    states[states.lastIndex] =
                        FrameLifecycleState.END
                }

                segments.add(
                    ItemSegment(
                        frames = current.toList(),
                        states = states.toList(),
                        startReason = startReason
                    )
                )

                current.clear()
                states.clear()

                current.add(frame)
                states.add(FrameLifecycleState.START)
                startReason = transition.reason

            } else {

                current.add(frame)
                states.add(FrameLifecycleState.CONTINUATION)
            }
        }

        if (current.isNotEmpty()) {

            /*
             * The very first frame of an item is START
             * by definition. Only mark END on the last
             * frame when the item has more than one.
             */
            if (states.size > 1) {
                states[states.lastIndex] =
                    FrameLifecycleState.END
            }

            segments.add(
                ItemSegment(
                    frames = current.toList(),
                    states = states.toList(),
                    startReason = startReason
                )
            )
        }

        return segments
    }

    private data class Transition(
        val split: Boolean,
        val reason: SplitReason?
    )

    private fun transition(
        previous: CapturedFrame,
        current: CapturedFrame
    ): Transition {

        // --------------------------------
        // 1. TIME GAP
        // --------------------------------

        val gapMs =
            abs(
                Duration
                    .between(
                        previous.capturedAt,
                        current.capturedAt
                    )
                    .toMillis()
            )

        if (gapMs > maxMergeGapMs) {
            return Transition(
                split = true,
                reason = SplitReason.GAP
            )
        }

        // --------------------------------
        // 2. PERCEPTUAL FINGERPRINT
        // --------------------------------

        val previousHash =
            fingerprintOf(previous)

        val currentHash =
            fingerprintOf(current)

        val distance =
            if (
                previousHash != null &&
                currentHash != null
            ) {
                PerceptualHash()
                    .hammingDistance(
                        previousHash,
                        currentHash
                    )
            } else {
                null
            }

        if (
            distance != null &&
            distance >= hashSplitDistance
        ) {
            return Transition(
                split = true,
                reason = SplitReason.FINGERPRINT
            )
        }

        // --------------------------------
        // 2b. PLATFORM (7F Part 1)
        // --------------------------------
        //
        // The on-screen platform is direct evidence of a
        // new content piece: two different platforms in a
        // row can never be the same Reel, even when the
        // category and visuals look similar.

        val previousPlatform =
            platformOf(previous)

        val currentPlatform =
            platformOf(current)

        if (
            previousPlatform != null &&
            currentPlatform != null &&
            previousPlatform != currentPlatform
        ) {
            return Transition(
                split = true,
                reason = SplitReason.PLATFORM
            )
        }

        // --------------------------------
        // 3. CATEGORY CONTINUITY
        // --------------------------------

        val previousCategory =
            categoryOf(previous)

        val currentCategory =
            categoryOf(current)

        return when {

            previousCategory == null ||
                    currentCategory == null ->

                if (gapMs <= nullCategoryGapMs) {
                    Transition(false, null)
                } else {
                    Transition(true, SplitReason.GAP)
                }

            previousCategory == currentCategory -> {

                // --------------------------------
                // 3a. TOPIC + CREATOR CONTINUITY
                // (7X)
                // --------------------------------
                //
                // Same category is NOT the same content.
                // Continuity is understood at a finer
                // grain than the label:
                //
                //   - a topic change (e.g. a cricket clip
                //     followed by a football clip) starts
                //     a new piece even when the visuals
                //     only changed moderately,
                //   - a different creator handle on screen
                //     (e.g. @teamA -> @teamB) is a
                //     different creator and therefore a
                //     different piece.
                //
                // Both require the visible text to have
                // clearly changed, so a single-frame
                // classification wobble never splits a
                // stable piece. When the topic stays the
                // same (or is unknown) the general
                // combined visual + text rule still
                // catches back-to-back reels.

                val topicChanged =
                    topicContinuityChanged(
                        previous,
                        current
                    )

                val creatorChanged =
                    creatorContinuityChanged(
                        previous,
                        current
                    )

                val textChanged =
                    textSimilarity(
                        previous,
                        current
                    ) < combinedTextSimilarityThreshold

                /*
                 * Milestone 7X. Creator changes are read at
                 * a looser text bar: the handle is usually
                 * a small fraction of the caption, so
                 * near-identical captions must still split
                 * when the on-screen creator differs. A
                 * compilation piece keeps its caption (and
                 * stays together); a new creator with a new
                 * caption splits.
                 */
                val creatorTextChanged =
                    textSimilarity(
                        previous,
                        current
                    ) < creatorTextSimilarityThreshold

                if (topicChanged && textChanged) {
                    Transition(
                        split = true,
                        reason = SplitReason.TOPIC
                    )
                } else if (
                    creatorChanged &&
                    creatorTextChanged
                ) {
                    Transition(
                        split = true,
                        reason = SplitReason.CREATOR
                    )
                } else if (
                    distance != null &&
                    distance >= combinedSplitDistance &&
                    textChanged
                ) {
                    Transition(
                        split = true,
                        reason = SplitReason.VISUAL_TEXT
                    )
                } else {
                    Transition(false, null)
                }
            }

            else -> {

                // --------------------------------
                // 4. OCR SIMILARITY OVERRIDE
                // --------------------------------
                //
                // Categories differ, but if the visible
                // text is essentially identical AND the
                // visuals are not radically different,
                // this is very likely the same content
                // piece (e.g. a slow pan over a text
                // overlay). Do not split it.
                //
                val similarity =
                    textSimilarity(
                        previous,
                        current
                    )

                if (
                    similarity >=
                    ocrSimilarityThreshold
                ) {
                    Transition(false, null)
                } else {
                    Transition(true, SplitReason.CATEGORY)
                }
            }
        }
    }

    private fun textSimilarity(
        first: CapturedFrame,
        second: CapturedFrame
    ): Double {

        val wordsA =
            wordSet(first)

        val wordsB =
            wordSet(second)

        if (
            wordsA.isEmpty() ||
            wordsB.isEmpty()
        ) {
            return 0.0
        }

        val intersection =
            wordsA.intersect(wordsB).size

        val union =
            wordsA.union(wordsB).size

        if (union == 0) {
            return 0.0
        }

        return intersection.toDouble() /
                union.toDouble()
    }

    private fun wordSet(
        frame: CapturedFrame
    ): Set<String> {

        val text =
            visibleTextOf(frame)
                ?: return emptySet()

        return text
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.length > 1 }
            .toSet()
    }

    private fun categoryOf(
        frame: CapturedFrame
    ): String? {

        return optString(
            frame,
            "contentCategory"
        )
    }

    /*
     * Milestone 7X. Topic continuity: both frames must
     * carry a topic and they must differ for a change to
     * be reported. A single missing topic (unknown frame)
     * never counts as a change.
     */
    private fun topicContinuityChanged(
        previous: CapturedFrame,
        current: CapturedFrame
    ): Boolean {

        val previousTopic =
            topicOf(previous)

        val currentTopic =
            topicOf(current)

        return previousTopic != null &&
            currentTopic != null &&
            previousTopic != currentTopic
    }

    /*
     * Milestone 7X. Creator continuity: the creator
     * handle on screen (e.g. "@cricketfan") is read from
     * the OCR text. A change is only reported when BOTH
     * frames expose a handle and they differ, so content
     * without an on-screen handle never triggers this.
     */
    private fun creatorContinuityChanged(
        previous: CapturedFrame,
        current: CapturedFrame
    ): Boolean {

        val previousCreator =
            creatorOf(previous)

        val currentCreator =
            creatorOf(current)

        return previousCreator != null &&
            currentCreator != null &&
            previousCreator != currentCreator
    }

    private fun topicOf(
        frame: CapturedFrame
    ): String? {

        return optString(
            frame,
            "topic"
        )
    }

    /*
     * Milestone 7X. First "@handle" on screen,
     * normalized to lowercase for comparison.
     */
    private fun creatorOf(
        frame: CapturedFrame
    ): String? {

        val text =
            visibleTextOf(frame)
                ?: return null

        return CREATOR_HANDLE_REGEX
            .find(text)
            ?.value
            ?.lowercase()
    }

    private fun fingerprintOf(
        frame: CapturedFrame
    ): String? {

        return optString(
            frame,
            "frameFingerprint"
        )
    }

    /*
     * Milestone 7F (Part 1). The platform on screen is
     * stored in the "application" field of the analysis
     * result (populated by LocalFrameAnalyzer from the
     * OCR text via PlatformDetector).
     */
    private fun platformOf(
        frame: CapturedFrame
    ): String? {

        return optString(
            frame,
            "application"
        )
    }

    private fun visibleTextOf(
        frame: CapturedFrame
    ): String? {

        return optString(
            frame,
            "visibleText"
        )
    }

    private fun optString(
        frame: CapturedFrame,
        key: String
    ): String? {

        val result =
            frame.analysisResult
                ?: return null

        return try {

            val json =
                JSONObject(result)

            json
                .optString(
                    key,
                    ""
                )
                .trim()
                .takeIf {
                    it.isNotEmpty()
                }

        } catch (_: Exception) {

            null
        }
    }

    companion object {

        /*
         * Milestone 7X. Creator handle on screen
         * (e.g. "@cricketfan"). Requires at least two
         * characters so a bare "@" is never a handle.
         */
        private val CREATOR_HANDLE_REGEX =
            Regex("@[A-Za-z0-9_]{2,}")

        const val DEFAULT_MAX_MERGE_GAP_MS =
            10_000L

        const val DEFAULT_NULL_CATEGORY_GAP_MS =
            3_000L

        const val DEFAULT_HASH_SPLIT_DISTANCE =
            24

        /*
         * OCR similarity (Jaccard) at or above this
         * keeps frames together even when the category
         * prediction wobbles.
         */
        const val DEFAULT_OCR_SIMILARITY_THRESHOLD =
            0.6

        /*
         * Milestone 7X. Text similarity must be BELOW
         * this for a creator-handle change to fire. A
         * handle is a small fraction of a caption, so the
         * bar is looser than the topic bar (0.2): a new
         * creator splits even when the caption template is
         * the same, while a genuinely shared caption keeps
         * the piece together.
         */
        const val DEFAULT_CREATOR_TEXT_SIMILARITY =
            0.7

        /*
         * Milestone 7F (Part 1). Fingerprint distance at
         * or above this (but below the hard fingerprint
         * split) participates in the combined visual +
         * text split for same-category content.
         */
        const val DEFAULT_COMBINED_SPLIT_DISTANCE =
            16

        /*
         * Milestone 7F (Part 1). Text similarity must be
         * BELOW this for the combined same-category split
         * to fire, so identical text is never split.
         */
        const val DEFAULT_COMBINED_TEXT_SIMILARITY =
            0.2
    }
}
