package com.example.feedsense.analysis

// --------------------------------
// SCHEMA FREEZE
// --------------------------------
//
// Milestone 8A. FROZEN — do not modify.
//
// This file freezes the canonical formats, taxonomy,
// confidence ranges, and review states that FeedSense
// v1.0 guarantees to downstream researchers.
//
// Every constant here is tested by SchemaFreezeTest.
// Any change to a frozen constant MUST bump the
// frozen version and update the test.
//
// What is frozen:
//
//   1. FeedItem schema (column names + types)
//   2. Taxonomy (category keys + hierarchy)
//   3. AI result JSON format
//   4. Confidence format (range + levels)
//   5. Review/validation format
//   6. Data provenance labels
//

object SchemaFreeze {

    // --------------------------------
    // FREEZE VERSION
    // --------------------------------
    //
    // Bump when any frozen artifact changes.
    //

    const val FREEZE_VERSION = "1.0.0"

    // --------------------------------
    // 1. FEED ITEM SCHEMA (FROZEN)
    // --------------------------------
    //
    // The canonical column set of the feed_items table
    // that v1.0 guarantees to researchers. Columns may
    // be ADDED in future versions but never removed or
    // renamed within a major version.
    //

    val FROZEN_FEED_ITEM_COLUMNS = setOf(
        "id",
        "sessionId",
        "startTime",
        "endTime",
        "durationSeconds",
        "category",
        "categoryDomain",
        "confidence",
        "topic",
        "tone",
        "contentType",
        "skipped",
        "representativeFramePath",
        "frameCount",
        "interactionSignals",
        "modelVersion",
        "frameFingerprint",
        "needsReview",
        "candidateCategories",
        "classificationReason",
        "updatedAt",
        "contentTransitions",
        "interactionEvidence",
        "secondaryCategory",
        "secondaryCategories",
        "categoryScores",
        "mixedContent",
        "pausedDurationSeconds",
        "activeWatchDurationSeconds",
        "uncertaintyLevel"
    )

    // --------------------------------
    // 2. TAXONOMY (FROZEN)
    // --------------------------------
    //
    // The canonical category keys recognized by
    // CategoryCatalog. Keys are stable lowercase
    // identifiers stored in the database.
    //
    // Hierarchy domains are ALSO frozen so roll-ups
    // by domain (sports, entertainment, education,
    // motivation, advertising) are stable.
    //

    val FROZEN_CATEGORY_KEYS = setOf(
        // Original 14
        "sports", "comedy", "motivation", "education",
        "news", "music", "gaming", "fitness",
        "technology", "fashion", "food", "lifestyle",
        "finance", "other",
        // 7U expansion
        "politics", "science", "health", "cooking",
        "travel", "business", "productivity",
        "self_improvement", "meme", "entertainment",
        "movie_clip", "series_clip", "music_video",
        "edit", "creator_edit", "youtuber_edit",
        "gameplay", "esports", "anime", "animation",
        "documentary", "podcast", "interview",
        "ranking", "top_list", "tutorial", "how_to",
        "review", "product_review", "advertisement",
        "sponsored_content", "influencer_content",
        "beauty", "relationships", "motivational_speech",
        "storytelling", "horror", "crime", "drama",
        "romance", "action", "reaction", "commentary",
        "discussion", "live_stream", "short_video",
        "long_video",
        // 7V subcategories
        "cricket", "football", "basketball", "tennis",
        "other_sport", "school", "university",
        "product_promotion", "unknown"
    )

    val FROZEN_DOMAIN_KEYS = setOf(
        "entertainment", "sports", "education",
        "motivation", "advertising"
    )

    val FROZEN_DOMAIN_HIERARCHY = mapOf(
        "entertainment" to setOf(
            "comedy", "meme", "movie_clip", "series_clip",
            "music", "music_video", "edit", "creator_edit",
            "youtuber_edit", "entertainment"
        ),
        "sports" to setOf(
            "sports", "cricket", "football", "basketball",
            "tennis", "other_sport", "fitness"
        ),
        "education" to setOf(
            "education", "school", "university", "tutorial",
            "how_to", "science", "technology"
        ),
        "motivation" to setOf(
            "motivation", "motivational_speech",
            "self_improvement", "productivity"
        ),
        "advertising" to setOf(
            "advertisement", "sponsored_content",
            "product_review", "product_promotion"
        )
    )

    // --------------------------------
    // 3. AI RESULT JSON FORMAT (FROZEN)
    // --------------------------------
    //
    // The JSON structure produced by the local and cloud
    // analyzers and stored in captured_frames.analysisResult.
    // Researchers parsing this JSON depend on these keys.
    //

    val FROZEN_AI_RESULT_KEYS = setOf(
        "status",
        "fileName",
        "width",
        "height",
        "fileSizeBytes",
        "message",
        "screenType",
        "application",
        "activity",
        "visibleText",
        "confidence",
        "classificationReason",
        "uncertain",
        "contentCategory",
        "categoryDomain",
        "secondaryCategories",
        "categoryScores",
        "topic",
        "tone",
        "contentType",
        "estimatedDurationSeconds",
        "interactionSignals",
        "interactionEvidence",
        "ambiguityScore",
        "modelVersion",
        "source",
        "disposition",
        "needsReview"
    )

    // --------------------------------
    // 4. CONFIDENCE FORMAT (FROZEN)
    // --------------------------------
    //
    // Confidence is a Double in [0.0, 1.0].
    //
    // Thresholds are frozen so reviewers and researchers
    // can interpret confidence values consistently.
    //

    const val CONFIDENCE_MIN = 0.0
    const val CONFIDENCE_MAX = 1.0
    const val CONFIDENCE_HIGH_THRESHOLD = 0.8
    const val CONFIDENCE_MEDIUM_THRESHOLD = 0.6
    const val CONFIDENCE_AMBIGUITY_THRESHOLD = 0.7

    val FROZEN_CONFIDENCE_LEVELS = mapOf(
        "HIGH" to ">= 0.8",
        "MEDIUM" to ">= 0.6 and < 0.8",
        "LOW" to "< 0.6"
    )

    // --------------------------------
    // 5. REVIEW FORMAT (FROZEN)
    // --------------------------------
    //
    // Validation states and label sources that the
    // review system produces. Researchers can filter
    // by these values.
    //

    val FROZEN_VALIDATION_STATES = setOf(
        "PENDING",      // awaiting human review
        "VALIDATED",    // human or cloud validated
        "REJECTED",     // not classifiable
        "SKIPPED"       // removed from queue
    )

    val FROZEN_LABEL_SOURCES = setOf(
        "HUMAN",        // validated by a human
        "CLOUD"         // from cloud reference
    )

    val FROZEN_CORRECTION_SOURCES = setOf(
        "USER",            // human correction
        "CLOUD_REFERENCE", // teacher model
        "LOCAL_MODEL",     // self-evaluation
        "SYSTEM"           // internal rule
    )

    // --------------------------------
    // 6. DATA PROVENANCE (FROZEN)
    // --------------------------------
    //
    // Every data point shown to researchers carries one
    // of these provenance labels. This is the critical
    // distinction for research integrity.
    //

    val FROZEN_DATA_PROVENANCE = setOf(
        "OBSERVED",          // directly captured (frames, timestamps)
        "INFERRRED",         // derived by pipeline (segments, durations)
        "AI_PREDICTED",      // local AI classification
        "USER_CONFIRMED",    // validated/corrected by human
        "CLOUD_VALIDATED",   // validated by cloud teacher
        "UNKNOWN"            // cannot determine provenance
    )

    // --------------------------------
    // CONTENT TYPE CONSTANTS (FROZEN)
    // --------------------------------

    val FROZEN_CONTENT_TYPES = setOf(
        "UNKNOWN", "SHORT_VIDEO", "LONG_VIDEO"
    )

    // --------------------------------
    // UNCERTAINTY LEVELS (FROZEN)
    // --------------------------------

    val FROZEN_UNCERTAINTY_LEVELS = setOf(
        "HIGH", "MEDIUM", "LOW"
    )

    // --------------------------------
    // CONTENT TRANSITIONS (FROZEN)
    // --------------------------------

    val FROZEN_CONTENT_TRANSITIONS = setOf(
        "CONTENT_STARTED",
        "CONTENT_CONTINUED",
        "CONTENT_CHANGED",
        "CONTENT_SKIPPED",
        "CONTENT_ENDED"
    )

    // --------------------------------
    // INTERACTION SIGNALS (FROZEN)
    // --------------------------------

    val FROZEN_INTERACTION_SIGNALS = setOf(
        "like_indicator",
        "comment_indicator",
        "share_indicator",
        "save_indicator",
        "follow_indicator",
        "playback_paused",
        "playback_playing"
    )

    // --------------------------------
    // TRUST LEVELS (FROZEN)
    // --------------------------------

    val FROZEN_TRUST_LEVELS = mapOf(
        "USER_CONFIRMED" to 4,
        "CLOUD_VALIDATED" to 3,
        "LOCAL_HIGH_CONFIDENCE" to 2,
        "LOCAL_LOW_CONFIDENCE" to 1
    )
}
