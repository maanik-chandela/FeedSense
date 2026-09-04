package com.example.feedsense

import com.example.feedsense.analysis.AnalysisDisposition
import com.example.feedsense.analysis.AnalysisSource
import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.ConfidenceGate
import com.example.feedsense.analysis.ConfidenceLevel
import com.example.feedsense.analysis.FrameAnalysisResult
import com.example.feedsense.analysis.SchemaFreeze
import com.example.feedsense.analysis.TrustLevel
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// SCHEMA FREEZE TEST
// --------------------------------
//
// Milestone 8A. Validates that the frozen schemas,
// taxonomy, formats, and constants are stable.
//
// If ANY of these tests fail, the schema has been
// broken and the freeze version must be bumped.
//

class SchemaFreezeTest {

    // --------------------------------
    // FREEZE VERSION
    // --------------------------------

    @Test
    fun freezeVersionIsSet() {
        assertEquals("1.0.0", SchemaFreeze.FREEZE_VERSION)
    }

    // --------------------------------
    // 8A: FEED ITEM SCHEMA FREEZE
    // --------------------------------

    @Test
    fun feedItemSchema_isFrozen() {

        val expectedColumns = SchemaFreeze.FROZEN_FEED_ITEM_COLUMNS

        // All frozen columns must be present
        assertTrue(expectedColumns.contains("id"))
        assertTrue(expectedColumns.contains("sessionId"))
        assertTrue(expectedColumns.contains("startTime"))
        assertTrue(expectedColumns.contains("endTime"))
        assertTrue(expectedColumns.contains("durationSeconds"))
        assertTrue(expectedColumns.contains("category"))
        assertTrue(expectedColumns.contains("categoryDomain"))
        assertTrue(expectedColumns.contains("confidence"))
        assertTrue(expectedColumns.contains("topic"))
        assertTrue(expectedColumns.contains("tone"))
        assertTrue(expectedColumns.contains("contentType"))
        assertTrue(expectedColumns.contains("skipped"))
        assertTrue(expectedColumns.contains("representativeFramePath"))
        assertTrue(expectedColumns.contains("frameCount"))
        assertTrue(expectedColumns.contains("interactionSignals"))
        assertTrue(expectedColumns.contains("modelVersion"))
        assertTrue(expectedColumns.contains("frameFingerprint"))
        assertTrue(expectedColumns.contains("needsReview"))
        assertTrue(expectedColumns.contains("candidateCategories"))
        assertTrue(expectedColumns.contains("classificationReason"))
        assertTrue(expectedColumns.contains("updatedAt"))
        assertTrue(expectedColumns.contains("contentTransitions"))
        assertTrue(expectedColumns.contains("interactionEvidence"))
        assertTrue(expectedColumns.contains("secondaryCategory"))
        assertTrue(expectedColumns.contains("secondaryCategories"))
        assertTrue(expectedColumns.contains("categoryScores"))
        assertTrue(expectedColumns.contains("mixedContent"))
        assertTrue(expectedColumns.contains("pausedDurationSeconds"))
        assertTrue(expectedColumns.contains("activeWatchDurationSeconds"))
        assertTrue(expectedColumns.contains("uncertaintyLevel"))

        assertEquals(30, expectedColumns.size)
    }

    @Test
    fun feedItemConstants_areComplete() {

        // Content types
        assertEquals("UNKNOWN", FeedItem.CONTENT_UNKNOWN)
        assertEquals("SHORT_VIDEO", FeedItem.CONTENT_SHORT_VIDEO)
        assertEquals("LONG_VIDEO", FeedItem.CONTENT_LONG_VIDEO)
        assertEquals(3, SchemaFreeze.FROZEN_CONTENT_TYPES.size)

        // Uncertainty levels
        assertEquals("HIGH", FeedItem.UNCERTAINTY_HIGH)
        assertEquals("MEDIUM", FeedItem.UNCERTAINTY_MEDIUM)
        assertEquals("LOW", FeedItem.UNCERTAINTY_LOW)
        assertEquals(3, SchemaFreeze.FROZEN_UNCERTAINTY_LEVELS.size)

        // Content transitions
        assertEquals("CONTENT_STARTED", FeedItem.TRANSITION_CONTENT_STARTED)
        assertEquals("CONTENT_CONTINUED", FeedItem.TRANSITION_CONTENT_CONTINUED)
        assertEquals("CONTENT_CHANGED", FeedItem.TRANSITION_CONTENT_CHANGED)
        assertEquals("CONTENT_SKIPPED", FeedItem.TRANSITION_CONTENT_SKIPPED)
        assertEquals("CONTENT_ENDED", FeedItem.TRANSITION_CONTENT_ENDED)
        assertEquals(5, SchemaFreeze.FROZEN_CONTENT_TRANSITIONS.size)
    }

    @Test
    fun feedItemDefaultValues_areStable() {

        val item = FeedItem(
            sessionId = "s1",
            startTime = java.time.LocalDateTime.now(),
            representativeFramePath = "/tmp/test.png"
        )

        assertTrue(item.id.take(36).replace("-", "").length > 0)
        assertEquals(0, item.durationSeconds)
        assertNull(item.category)
        assertNull(item.categoryDomain)
        assertNull(item.confidence)
        assertNull(item.topic)
        assertNull(item.tone)
        assertEquals(FeedItem.CONTENT_UNKNOWN, item.contentType)
        assertFalse(item.skipped)
        assertEquals(0, item.frameCount)
        assertTrue(item.interactionSignals.isEmpty())
        assertNull(item.modelVersion)
        assertNull(item.frameFingerprint)
        assertFalse(item.needsReview)
        assertTrue(item.candidateCategories.isEmpty())
        assertNull(item.classificationReason)
        assertTrue(item.contentTransitions.isEmpty())
        assertTrue(item.interactionEvidence.isEmpty())
        assertNull(item.secondaryCategory)
        assertTrue(item.secondaryCategories.isEmpty())
        assertTrue(item.categoryScores.isEmpty())
        assertFalse(item.mixedContent)
        assertEquals(0, item.pausedDurationSeconds)
        assertEquals(0, item.activeWatchDurationSeconds)
        assertEquals(FeedItem.UNCERTAINTY_LOW, item.uncertaintyLevel)
    }

    // --------------------------------
    // 8A: TAXONOMY FREEZE
    // --------------------------------

    @Test
    fun taxonomy_isComplete() {

        val catalogKeys = CategoryCatalog.keys.toSet()
        val frozenKeys = SchemaFreeze.FROZEN_CATEGORY_KEYS

        // Every frozen key must exist in the catalog
        frozenKeys.forEach { key ->
            assertTrue(
                "Frozen key '$key' missing from catalog",
                catalogKeys.contains(key)
            )
        }

        // Every catalog key must be in the frozen set
        catalogKeys.forEach { key ->
            assertTrue(
                "Catalog key '$key' not in frozen set",
                frozenKeys.contains(key)
            )
        }

        assertEquals(frozenKeys.size, catalogKeys.size)
    }

    @Test
    fun taxonomy_hierarchy_isFrozen() {

        val hierarchy = SchemaFreeze.FROZEN_DOMAIN_HIERARCHY

        // Entertainment domain
        assertTrue(hierarchy.containsKey("entertainment"))
        val entertainment = hierarchy["entertainment"]!!
        assertTrue(entertainment.contains("comedy"))
        assertTrue(entertainment.contains("meme"))
        assertTrue(entertainment.contains("movie_clip"))
        assertTrue(entertainment.contains("series_clip"))
        assertTrue(entertainment.contains("music"))
        assertTrue(entertainment.contains("music_video"))
        assertTrue(entertainment.contains("edit"))
        assertTrue(entertainment.contains("creator_edit"))
        assertTrue(entertainment.contains("youtuber_edit"))

        // Sports domain
        assertTrue(hierarchy.containsKey("sports"))
        val sports = hierarchy["sports"]!!
        assertTrue(sports.contains("sports"))
        assertTrue(sports.contains("cricket"))
        assertTrue(sports.contains("football"))
        assertTrue(sports.contains("basketball"))
        assertTrue(sports.contains("tennis"))
        assertTrue(sports.contains("other_sport"))
        assertTrue(sports.contains("fitness"))

        // Education domain
        assertTrue(hierarchy.containsKey("education"))
        val education = hierarchy["education"]!!
        assertTrue(education.contains("education"))
        assertTrue(education.contains("tutorial"))
        assertTrue(education.contains("how_to"))
        assertTrue(education.contains("science"))
        assertTrue(education.contains("technology"))

        // Motivation domain
        assertTrue(hierarchy.containsKey("motivation"))
        val motivation = hierarchy["motivation"]!!
        assertTrue(motivation.contains("motivation"))
        assertTrue(motivation.contains("motivational_speech"))
        assertTrue(motivation.contains("self_improvement"))
        assertTrue(motivation.contains("productivity"))

        // Advertising domain
        assertTrue(hierarchy.containsKey("advertising"))
        val advertising = hierarchy["advertising"]!!
        assertTrue(advertising.contains("advertisement"))
        assertTrue(advertising.contains("sponsored_content"))
        assertTrue(advertising.contains("product_review"))
        assertTrue(advertising.contains("product_promotion"))
    }

    @Test
    fun taxonomy_domains_areComplete() {

        assertEquals(5, SchemaFreeze.FROZEN_DOMAIN_KEYS.size)
        assertTrue(SchemaFreeze.FROZEN_DOMAIN_KEYS.contains("entertainment"))
        assertTrue(SchemaFreeze.FROZEN_DOMAIN_KEYS.contains("sports"))
        assertTrue(SchemaFreeze.FROZEN_DOMAIN_KEYS.contains("education"))
        assertTrue(SchemaFreeze.FROZEN_DOMAIN_KEYS.contains("motivation"))
        assertTrue(SchemaFreeze.FROZEN_DOMAIN_KEYS.contains("advertising"))
    }

    @Test
    fun taxonomy_allCategoriesHaveDisplayNames() {

        SchemaFreeze.FROZEN_CATEGORY_KEYS.forEach { key ->
            val name = CategoryCatalog.displayName(key)
            assertTrue(
                "Category '$key' has empty displayName",
                name.isNotEmpty()
            )
        }
    }

    // --------------------------------
    // 8A: AI RESULT FORMAT FREEZE
    // --------------------------------

    @Test
    fun aiResultFormat_isFrozen() {

        val frozenKeys = SchemaFreeze.FROZEN_AI_RESULT_KEYS

        assertEquals(28, frozenKeys.size)

        assertTrue(frozenKeys.contains("status"))
        assertTrue(frozenKeys.contains("contentCategory"))
        assertTrue(frozenKeys.contains("categoryDomain"))
        assertTrue(frozenKeys.contains("secondaryCategories"))
        assertTrue(frozenKeys.contains("categoryScores"))
        assertTrue(frozenKeys.contains("confidence"))
        assertTrue(frozenKeys.contains("topic"))
        assertTrue(frozenKeys.contains("tone"))
        assertTrue(frozenKeys.contains("contentType"))
        assertTrue(frozenKeys.contains("interactionSignals"))
        assertTrue(frozenKeys.contains("interactionEvidence"))
        assertTrue(frozenKeys.contains("ambiguityScore"))
        assertTrue(frozenKeys.contains("modelVersion"))
        assertTrue(frozenKeys.contains("source"))
        assertTrue(frozenKeys.contains("disposition"))
        assertTrue(frozenKeys.contains("needsReview"))
        assertTrue(frozenKeys.contains("classificationReason"))
        assertTrue(frozenKeys.contains("visibleText"))
    }

    @Test
    fun frameAnalysisResult_hasAllFrozenFields() {

        val result = FrameAnalysisResult(
            status = "ANALYZED",
            fileName = "test.png",
            width = 1080,
            height = 1920,
            fileSizeBytes = 1024,
            message = "test",
            contentCategory = "sports",
            categoryDomain = "sports",
            secondaryCategories = listOf("entertainment"),
            categoryScores = mapOf("sports" to 0.9, "entertainment" to 0.3),
            confidence = 0.92,
            topic = "cricket",
            tone = "energetic",
            contentType = "SHORT_VIDEO",
            interactionSignals = listOf("like_indicator"),
            interactionEvidence = listOf("like_indicator|HIGH|2345 likes"),
            ambiguityScore = 0.1,
            modelVersion = "local-v6.0",
            source = "LOCAL",
            disposition = "LOCAL_ACCEPTED",
            needsReview = false,
            classificationReason = "hits: sports=3",
            visibleText = "cricket highlights"
        )

        assertNotNull(result.status)
        assertNotNull(result.contentCategory)
        assertNotNull(result.categoryDomain)
        assertNotNull(result.confidence)
        assertNotNull(result.topic)
        assertNotNull(result.tone)
        assertNotNull(result.contentType)
        assertNotNull(result.modelVersion)
        assertNotNull(result.source)
        assertNotNull(result.disposition)
    }

    // --------------------------------
    // 8A: CONFIDENCE FORMAT FREEZE
    // --------------------------------

    @Test
    fun confidenceRange_isFrozen() {

        assertEquals(0.0, SchemaFreeze.CONFIDENCE_MIN, 0.001)
        assertEquals(1.0, SchemaFreeze.CONFIDENCE_MAX, 0.001)
        assertEquals(0.8, SchemaFreeze.CONFIDENCE_HIGH_THRESHOLD, 0.001)
        assertEquals(0.6, SchemaFreeze.CONFIDENCE_MEDIUM_THRESHOLD, 0.001)
        assertEquals(0.7, SchemaFreeze.CONFIDENCE_AMBIGUITY_THRESHOLD, 0.001)
    }

    @Test
    fun confidenceLevels_areFrozen() {

        assertEquals(3, SchemaFreeze.FROZEN_CONFIDENCE_LEVELS.size)
        assertTrue(SchemaFreeze.FROZEN_CONFIDENCE_LEVELS.containsKey("HIGH"))
        assertTrue(SchemaFreeze.FROZEN_CONFIDENCE_LEVELS.containsKey("MEDIUM"))
        assertTrue(SchemaFreeze.FROZEN_CONFIDENCE_LEVELS.containsKey("LOW"))
    }

    @Test
    fun confidenceGate_thresholdsMatchFrozen() {

        val gate = ConfidenceGate()

        // High confidence -> LOCAL_ACCEPTED
        val highResult = FrameAnalysisResult(
            status = "ANALYZED", fileName = "f.png",
            width = 100, height = 100, fileSizeBytes = 100,
            message = "test", contentCategory = "comedy",
            confidence = SchemaFreeze.CONFIDENCE_HIGH_THRESHOLD,
            source = "LOCAL", modelVersion = "local-v6.0"
        )
        assertEquals(
            AnalysisDisposition.LOCAL_ACCEPTED,
            gate.evaluate(highResult)
        )

        // Medium confidence -> MEDIUM_CONFIDENCE
        val mediumResult = highResult.copy(
            confidence = SchemaFreeze.CONFIDENCE_MEDIUM_THRESHOLD
        )
        assertEquals(
            AnalysisDisposition.MEDIUM_CONFIDENCE,
            gate.evaluate(mediumResult)
        )

        // Below medium -> NEEDS_CLOUD
        val lowResult = highResult.copy(
            confidence = SchemaFreeze.CONFIDENCE_MEDIUM_THRESHOLD - 0.1
        )
        assertEquals(
            AnalysisDisposition.NEEDS_CLOUD,
            gate.evaluate(lowResult)
        )

        // Ambiguous -> AMBIGUOUS
        val ambiguousResult = highResult.copy(
            confidence = 0.75,
            ambiguityScore = SchemaFreeze.CONFIDENCE_AMBIGUITY_THRESHOLD
        )
        assertEquals(
            AnalysisDisposition.AMBIGUOUS,
            gate.evaluate(ambiguousResult)
        )

        // Null confidence -> LOCAL_ACCEPTED
        val nullResult = highResult.copy(confidence = null)
        assertEquals(
            AnalysisDisposition.LOCAL_ACCEPTED,
            gate.evaluate(nullResult)
        )
    }

    @Test
    fun confidenceLevelMapping_isFrozen() {

        assertEquals(
            ConfidenceLevel.HIGH,
            ConfidenceLevel.from(SchemaFreeze.CONFIDENCE_HIGH_THRESHOLD)
        )
        assertEquals(
            ConfidenceLevel.MEDIUM,
            ConfidenceLevel.from(SchemaFreeze.CONFIDENCE_MEDIUM_THRESHOLD)
        )
        assertEquals(
            ConfidenceLevel.LOW,
            ConfidenceLevel.from(SchemaFreeze.CONFIDENCE_MEDIUM_THRESHOLD - 0.1)
        )
    }

    // --------------------------------
    // 8A: REVIEW FORMAT FREEZE
    // --------------------------------

    @Test
    fun validationStates_areFrozen() {

        assertEquals(4, SchemaFreeze.FROZEN_VALIDATION_STATES.size)
        assertEquals("PENDING", LabeledReference.VALIDATION_PENDING)
        assertEquals("VALIDATED", LabeledReference.VALIDATION_VALIDATED)
        assertEquals("REJECTED", LabeledReference.VALIDATION_REJECTED)
        assertEquals("SKIPPED", LabeledReference.VALIDATION_SKIPPED)
    }

    @Test
    fun labelSources_areFrozen() {

        assertEquals(2, SchemaFreeze.FROZEN_LABEL_SOURCES.size)
        assertEquals("HUMAN", LabeledReference.LABEL_SOURCE_HUMAN)
        assertEquals("CLOUD", LabeledReference.LABEL_SOURCE_CLOUD)
    }

    @Test
    fun correctionSources_areFrozen() {

        assertEquals(4, SchemaFreeze.FROZEN_CORRECTION_SOURCES.size)
        assertEquals("USER", ModelFeedback.SOURCE_USER)
        assertEquals("CLOUD_REFERENCE", ModelFeedback.SOURCE_CLOUD_REFERENCE)
        assertEquals("LOCAL_MODEL", ModelFeedback.SOURCE_LOCAL_MODEL)
        assertEquals("SYSTEM", ModelFeedback.SOURCE_SYSTEM)
    }

    // --------------------------------
    // 8A: DATA PROVENANCE FREEZE
    // --------------------------------

    @Test
    fun dataProvenance_isFrozen() {

        val provenance = SchemaFreeze.FROZEN_DATA_PROVENANCE

        assertEquals(6, provenance.size)
        assertTrue(provenance.contains("OBSERVED"))
        assertTrue(provenance.contains("INFERRRED"))
        assertTrue(provenance.contains("AI_PREDICTED"))
        assertTrue(provenance.contains("USER_CONFIRMED"))
        assertTrue(provenance.contains("CLOUD_VALIDATED"))
        assertTrue(provenance.contains("UNKNOWN"))
    }

    // --------------------------------
    // 8A: TRUST LEVELS FREEZE
    // --------------------------------

    @Test
    fun trustLevels_areFrozen() {

        assertEquals(4, SchemaFreeze.FROZEN_TRUST_LEVELS.size)
        assertEquals(4, SchemaFreeze.FROZEN_TRUST_LEVELS["USER_CONFIRMED"])
        assertEquals(3, SchemaFreeze.FROZEN_TRUST_LEVELS["CLOUD_VALIDATED"])
        assertEquals(2, SchemaFreeze.FROZEN_TRUST_LEVELS["LOCAL_HIGH_CONFIDENCE"])
        assertEquals(1, SchemaFreeze.FROZEN_TRUST_LEVELS["LOCAL_LOW_CONFIDENCE"])
    }

    @Test
    fun trustLevelAuthorityOrder_isFrozen() {

        assertTrue(TrustLevel.USER_CONFIRMED.authority > TrustLevel.CLOUD_VALIDATED.authority)
        assertTrue(TrustLevel.CLOUD_VALIDATED.authority > TrustLevel.LOCAL_HIGH_CONFIDENCE.authority)
        assertTrue(TrustLevel.LOCAL_HIGH_CONFIDENCE.authority > TrustLevel.LOCAL_LOW_CONFIDENCE.authority)
    }

    // --------------------------------
    // 8A: INTERACTION SIGNALS FREEZE
    // --------------------------------

    @Test
    fun interactionSignals_areFrozen() {

        assertEquals(7, SchemaFreeze.FROZEN_INTERACTION_SIGNALS.size)
        assertTrue(SchemaFreeze.FROZEN_INTERACTION_SIGNALS.contains("like_indicator"))
        assertTrue(SchemaFreeze.FROZEN_INTERACTION_SIGNALS.contains("comment_indicator"))
        assertTrue(SchemaFreeze.FROZEN_INTERACTION_SIGNALS.contains("share_indicator"))
        assertTrue(SchemaFreeze.FROZEN_INTERACTION_SIGNALS.contains("save_indicator"))
        assertTrue(SchemaFreeze.FROZEN_INTERACTION_SIGNALS.contains("follow_indicator"))
        assertTrue(SchemaFreeze.FROZEN_INTERACTION_SIGNALS.contains("playback_paused"))
        assertTrue(SchemaFreeze.FROZEN_INTERACTION_SIGNALS.contains("playback_playing"))
    }

    // --------------------------------
    // 8A: ANALYSIS SOURCE / DISPOSITION FREEZE
    // --------------------------------

    @Test
    fun analysisSources_areComplete() {

        val sources = AnalysisSource.entries.map { it.name }.toSet()
        assertTrue(sources.contains("LOCAL"))
        assertTrue(sources.contains("CLOUD"))
        assertTrue(sources.contains("HUMAN"))
        assertTrue(sources.contains("UNKNOWN"))
    }

    @Test
    fun analysisDispositions_areComplete() {

        val dispositions = AnalysisDisposition.entries.map { it.name }.toSet()
        assertTrue(dispositions.contains("LOCAL_ACCEPTED"))
        assertTrue(dispositions.contains("MEDIUM_CONFIDENCE"))
        assertTrue(dispositions.contains("AMBIGUOUS"))
        assertTrue(dispositions.contains("NEEDS_CLOUD"))
        assertTrue(dispositions.contains("NEEDS_REVIEW"))
    }
}
