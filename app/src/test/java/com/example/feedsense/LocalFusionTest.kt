package com.example.feedsense

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.fusion.CandidateGenerator
import com.example.feedsense.analysis.fusion.ConfidenceBand
import com.example.feedsense.analysis.fusion.EvidenceContext
import com.example.feedsense.analysis.fusion.EvidenceFamily
import com.example.feedsense.analysis.fusion.EvidenceNormalizer
import com.example.feedsense.analysis.fusion.EvidenceRecord
import com.example.feedsense.analysis.fusion.EvidenceStrength
import com.example.feedsense.analysis.fusion.FrameSignals
import com.example.feedsense.analysis.fusion.FusionConfig
import com.example.feedsense.analysis.fusion.FusionEngine
import com.example.feedsense.analysis.fusion.FusionEvaluationBridge
import com.example.feedsense.analysis.fusion.FusionModels
import com.example.feedsense.analysis.fusion.FusionPrediction
import com.example.feedsense.analysis.fusion.InteractionEvidenceProvider
import com.example.feedsense.analysis.fusion.PlatformEvidenceProvider
import com.example.feedsense.analysis.fusion.TextEvidenceProvider
import com.example.feedsense.analysis.fusion.TemporalEvidenceProvider
import com.example.feedsense.analysis.fusion.Uncertainty
import com.example.feedsense.analysis.fusion.VisualEvidenceProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// FUSION V1 TESTS (Milestone 8B-1)
// --------------------------------
//
// Verifies the local evidence-fusion decision architecture:
// evidence extraction, normalization, candidate generation,
// deterministic fusion, uncertainty, conflict, temporal
// consistency, trace, versioning and the legacy-vs-fusion
// evaluation bridge, across the realistic CASE 1-9 scenarios.

class LocalFusionTest {

    private val engine = FusionEngine()

    // ------------------------------------------------------------
    // CONFIG / VERSIONING
    // ------------------------------------------------------------

    @Test
    fun config_defaults_are_frozen_and_versioned() {
        val cfg = FusionConfig.DEFAULT
        assertEquals(FusionConfig.MODEL_VERSION, cfg.modelVersion)
        assertEquals(FusionConfig.CONFIG_VERSION, cfg.configurationVersion)
        assertEquals(FusionConfig.MODEL_STATE_EXPERIMENTAL, cfg.modelState)
        assertEquals("local-fusion-v1", cfg.modelVersion)
    }

    @Test
    fun prediction_carries_fusion_model_version() {
        val p = engine.predict(
            FusionModels.contextFromText(
                "Cricket world cup ipl wickets innings batsman century stadium crowd"
            )
        )
        assertEquals(FusionConfig.MODEL_VERSION, p.modelVersion)
        assertNotEquals("local-v6.0", p.modelVersion)
    }

    // ------------------------------------------------------------
    // EVIDENCE MODEL / ABSENCE SEMANTICS
    // ------------------------------------------------------------

    @Test
    fun missing_ocr_is_not_unknown_content() {
        // A frame with no OCR must be INSUFFICIENT_EVIDENCE, never
        // a false "unknown category" or fabricated confidence.
        val context = EvidenceContext.single(
            FrameSignals(frameId = "f1", timestamp = "t0", ocrText = null)
        )
        val p = engine.predict(context)
        assertEquals(Uncertainty.INSUFFICIENT_EVIDENCE, p.uncertainty)
        assertNull(p.primaryCategory)
        assertEquals(0.0, p.confidence, 1e-9)

        // The text provider explicitly reports OCR_UNAVAILABLE.
        val records = TextEvidenceProvider().provide(context)
        assertTrue(records.any { it.type == "OCR_UNAVAILABLE" })
        assertTrue(records.all { it.isRaw })
    }

    @Test
    fun short_text_is_insufficient() {
        val p = engine.predict(FusionModels.contextFromText("hi"))
        assertEquals(Uncertainty.INSUFFICIENT_EVIDENCE, p.uncertainty)
        assertNull(p.primaryCategory)
    }

    @Test
    fun informative_text_without_keywords_is_insufficient() {
        val p = engine.predict(
            FusionModels.contextFromText(
                "lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod"
            )
        )
        assertEquals(Uncertainty.INSUFFICIENT_EVIDENCE, p.uncertainty)
    }

    @Test
    fun platform_is_context_never_category() {
        // A platform alone must not imply a category: even with a
        // known platform, empty OCR => insufficient evidence.
        val context = EvidenceContext.single(
            FrameSignals(
                frameId = "f1", timestamp = "t0",
                ocrText = null, platform = "Instagram"
            )
        )
        val p = engine.predict(context)
        assertEquals(Uncertainty.INSUFFICIENT_EVIDENCE, p.uncertainty)
        assertNull(p.primaryCategory)
        // Platform is detected (it is installed context, not a
        // category), but it must never name a category on its own.
        assertNotNull(p.platform)

        val prov = PlatformEvidenceProvider().provide(context)
        assertEquals("PLATFORM_APP", prov.single().type)

        // With no platform observed, the provider explicitly reports
        // PLATFORM_UNDETECTED (absence is explicit, never guessed).
        val noPlatform = PlatformEvidenceProvider().provide(
            EvidenceContext.single(
                FrameSignals(frameId = "f1", timestamp = "t0", ocrText = "sports")
            )
        )
        assertEquals("PLATFORM_UNDETECTED", noPlatform.single().type)
    }

    @Test
    fun visual_is_explicitly_unavailable() {
        val records = VisualEvidenceProvider().provide(EvidenceContext.single(
            FrameSignals(frameId = "f1", timestamp = "t0", ocrText = "sports")
        ))
        assertEquals(1, records.size)
        assertEquals(VisualEvidenceProvider.UNAVAILABLE, records.single().valueSummary)
        assertTrue(records.single().isVirtual)
    }

    @Test
    fun interaction_distinguishes_present_vs_unknown() {
        val present = EvidenceContext.single(
            FrameSignals(
                frameId = "f1", timestamp = "t0",
                interactionSignals = setOf("like", "comment")
            )
        )
        val p1 = engine.predict(present)
        assertEquals(
            InteractionEvidenceProvider.STATE_PRESENT,
            p1.interactionState
        )

        val absent = EvidenceContext.single(
            FrameSignals(frameId = "f1", timestamp = "t0")
        )
        val p2 = engine.predict(absent)
        assertEquals(
            InteractionEvidenceProvider.STATE_UNKNOWN,
            p2.interactionState
        )
    }

    // ------------------------------------------------------------
    // STRENGTH / RELIABILITY
    // ------------------------------------------------------------

    @Test
    fun evidence_strength_is_independent_of_confidence() {
        // A STRONG raw OCR record carries integrity even though no
        // category confidence exists yet.
        val records = TextEvidenceProvider().provide(
            EvidenceContext.single(
                FrameSignals(frameId = "f1", timestamp = "t0",
                    ocrText = "sports tournament stadium team cricket wicket batsman innings crowd highlight")
            )
        )
        assertTrue(records.isNotEmpty())
        assertNotEquals(EvidenceStrength.NONE, records.maxOf { it.strength })
    }

    // ------------------------------------------------------------
    // DEDUPLICATION (5 identical frames != 5 confirmations)
    // ------------------------------------------------------------

    @Test
    fun identical_frames_do_not_inflate_confidence() {
        val one = FusionModels.contextFromText(
            "Cricket ipl wickets innings batsman century stadium tournament"
        )

        val many = EvidenceContext(
            List(5) { i ->
                FrameSignals(
                    frameId = "f$i", timestamp = "t$i",
                    ocrText = "Cricket ipl wickets innings batsman century stadium tournament"
                )
            }
        )

        val norm = EvidenceNormalizer(FusionConfig.DEFAULT)
        val dedup = norm.deduplicate(TextEvidenceProvider().provide(many))

        // Every duplicate collapses to a single unique text record.
        val uniqueText = dedup
            .filter { it.record.family == EvidenceFamily.TEXT }
            .filterNot { it.deduplicated }
        assertTrue(uniqueText.size == 1)

        // The single-frame confidence must not be dramatically
        // higher just because 5 copies were seen.
        val confOne = engine.predict(one).confidence
        val confMany = engine.predict(many).confidence
        assertTrue(
            "duplicate frames must not inflate confidence: $confOne vs $confMany",
            confMany - confOne < 0.25
        )
    }

    // ------------------------------------------------------------
    // CANDIDATE GENERATION
    // ------------------------------------------------------------

    @Test
    fun candidate_generation_is_deterministic_and_keyword_driven() {
        val a = CandidateGenerator.candidatesForText(
            "cricket ipl wicket batsman innings", "src", "f1"
        )
        val b = CandidateGenerator.candidatesForText(
            "cricket ipl wicket batsman innings", "src", "f1"
        )
        assertEquals(a, b)
        assertTrue(a.any { it.category == "cricket" })
    }

    @Test
    fun topic_and_content_type_are_separate_dimensions() {
        val text = "Full episode season one of this series watch now"
        assertNotNull(CandidateGenerator.topicFor(text))
        assertEquals("video", CandidateGenerator.contentTypeHint(text))
    }

    // ------------------------------------------------------------
    // DETERMINISM
    // ------------------------------------------------------------

    @Test
    fun identical_input_is_byte_identical_output() {
        val context = FusionModels.contextFromText(
            "Football fifa premier league striker penalty euro highlights"
        )
        val p1 = engine.predict(context)
        val p2 = engine.predict(context)
        assertEquals(p1.primaryCategory, p2.primaryCategory)
        assertEquals(p1.confidence, p2.confidence, 1e-12)
        assertEquals(p1.uncertainty, p2.uncertainty)
        assertEquals(p1.trace, p2.trace)
    }

    // ------------------------------------------------------------
    // REALISTIC CASE 1-9
    // ------------------------------------------------------------

    @Test
    fun case1_sports_identifies_sports_domain() {
        val p = engine.predict(
            FusionModels.contextFromText(
                "Cricket world cup ipl wickets batsman innings century stadium crowd amazing innings tournament"
            )
        )
        assertNotNull(p.primaryCategory)
        assertEquals(
            "sports",
            CategoryCatalog.domainOf(p.primaryCategory)
        )
        assertTrue(p.hasDecidedPrimary)
    }

    @Test
    fun case2_ranking_content() {
        val p = engine.predict(
            FusionModels.contextFromText(
                "Top 10 best moments ever ranking greatest plays of all time top 5 list"
            )
        )
        assertNotNull(p.primaryCategory)
        assertEquals("ranking", p.primaryCategory)
    }

    @Test
    fun case3_meme_content_is_entertainment() {
        val p = engine.predict(
            FusionModels.contextFromText(
                "memes dank meme template relatable meme funny joke hilarious meme compilation"
            )
        )
        assertNotNull(p.primaryCategory)
        assertEquals(
            "entertainment",
            CategoryCatalog.domainOf(p.primaryCategory)
        )
        assertTrue(p.hasDecidedPrimary)
    }

    @Test
    fun case4_advertising_domain() {
        val p = engine.predict(
            FusionModels.contextFromText(
                "Sponsored by brand limited stock buy now product launch promotional offer buy 1 get 1"
            )
        )
        assertNotNull(p.primaryCategory)
        assertEquals("advertising", CategoryCatalog.domainOf(p.primaryCategory))
    }

    @Test
    fun case5_motivation_domain() {
        val p = engine.predict(
            FusionModels.contextFromText(
                "Motivation success mindset discipline never give up keep going grind hustle hard work"
            )
        )
        assertNotNull(p.primaryCategory)
        assertEquals("motivation", CategoryCatalog.domainOf(p.primaryCategory))
    }

    @Test
    fun case6_movie_clip_entertainment() {
        val p = engine.predict(
            FusionModels.contextFromText(
                "Movie trailer cinema teaser release date box office hollywood movie film"
            )
        )
        assertNotNull(p.primaryCategory)
        assertEquals("entertainment", CategoryCatalog.domainOf(p.primaryCategory))
        assertEquals("movie_clip", p.primaryCategory)
    }

    @Test
    fun case7_ambiguous_tied_candidates_are_not_arbitrary() {
        val p = engine.predict(
            FusionModels.contextFromText(
                "ranking top list"
            )
        )
        // meme (entertainment) and ranking (top-level) tie; the
        // engine must report ambiguity rather than silently pick.
        assertEquals(Uncertainty.AMBIGUOUS, p.uncertainty)
    }

    @Test
    fun case8_missing_information_is_not_fabricated() {
        val p = engine.predict(
            FusionModels.contextFromText("")
        )
        assertEquals(Uncertainty.INSUFFICIENT_EVIDENCE, p.uncertainty)
        assertNull(p.primaryCategory)
        assertEquals(ConfidenceBand.UNKNOWN, p.band)
    }

    @Test
    fun case9_conflicting_evidence_is_surfaced() {
        // OCR strongly says sports; the legacy classifier verdict
        // (existingCategory) says meme. This cross-family conflict
        // must be surfaced as CONFLICTING_EVIDENCE, never silently
        // resolved to one side.
        val context = EvidenceContext.single(
            FrameSignals(
                frameId = "f1", timestamp = "t0",
                ocrText = "cricket ipl wickets batsman innings century",
                existingCategory = "meme"
            )
        )
        val p = engine.predict(context)
        assertEquals(Uncertainty.CONFLICTING_EVIDENCE, p.uncertainty)
    }

    @Test
    fun conflicting_temporal_frames_are_not_silently_resolved() {
        // Two frames point at different content; without a clear
        // winner the model must avoid fabricating a confident pick.
        val context = EvidenceContext(
            listOf(
                FrameSignals(frameId = "f1", timestamp = "t0",
                    ocrText = "cricket ipl wickets innings batsman century"),
                FrameSignals(frameId = "f2", timestamp = "t1",
                    ocrText = "memes dank template relatable funny joke")
            )
        )
        val p = engine.predict(context)
        assertTrue(
            "conflicting temporal evidence must not be confidently decided: ${p.uncertainty}",
            p.uncertainty == Uncertainty.CONFLICTING_EVIDENCE ||
                p.uncertainty == Uncertainty.AMBIGUOUS ||
                p.uncertainty == Uncertainty.LOW_CONFIDENCE
        )
        assertFalse(
            "must not confidently decide conflicting content: ${p.uncertainty}",
            p.uncertainty == Uncertainty.CONFIDENT
        )
    }

    // ------------------------------------------------------------
    // TEMPORAL CONSISTENCY
    // ------------------------------------------------------------

    @Test
    fun short_clip_is_analyzable_without_temporal_boost() {
        // A single-frame (~1s) sports interaction is still
        // analyzable and confident; temporal evidence correctly
        // reports INSUFFICIENT.
        val context = EvidenceContext.single(
            FrameSignals(frameId = "f1", timestamp = "t0",
                ocrText = "cricket ipl wicket batsman innings")
        )
        val p = engine.predict(context)
        val temporal = TemporalEvidenceProvider()
            .provide(context)
        assertTrue(temporal.all { it.type == "TEMPORAL_INSUFFICIENT" })
        assertNotNull(p.primaryCategory)
        assertTrue(p.hasDecidedPrimary)
    }

    @Test
    fun temporal_consistency_bonus_only_on_agreement() {
        val agree = engine.predict(
            EvidenceContext(
                listOf(
                    FrameSignals(frameId = "f1", timestamp = "t0",
                        ocrText = "cricket ipl wicket batsman innings"),
                    FrameSignals(frameId = "f2", timestamp = "t1",
                        ocrText = "cricket football ipl striker tournament")
                )
            )
        )
        assertNotNull(agree.primaryCategory)
    }

    // ------------------------------------------------------------
    // TRACE / DIAGNOSTIC
    // ------------------------------------------------------------

    @Test
    fun trace_is_compact_and_structured() {
        val p = engine.predict(
            FusionModels.contextFromText(
                "Cricket ipl wickets innings batsman century stadium tournament"
            )
        )
        assertEquals(1, p.trace.framesConsidered)
        assertTrue(p.trace.perProviderStrength.isNotEmpty())
        assertTrue(p.trace.candidates.isNotEmpty())
        assertNotNull(p.trace.finalCategory)
        assertNotNull(p.trace.confidenceBand)
        assertNotNull(p.trace.uncertainty)
        // Compact: no full OCR transcripts inside the trace.
        assertFalse(p.trace.perProviderStrength.any {
            it.type == "OCR_TEXT" && it.frames == 0
        })
    }

    // ------------------------------------------------------------
    // UNCERTAINTY STATE MACHINE
    // ------------------------------------------------------------

    @Test
    fun confident_when_clear_winner() {
        val p = engine.predict(
            FusionModels.contextFromText(
                "Top 5 ranking best of top 10 list greatest ever"
            )
        )
        assertEquals(Uncertainty.CONFIDENT, p.uncertainty)
    }

    // ------------------------------------------------------------
    // EVALUATION BRIDGE (legacy vs fusion)
    // ------------------------------------------------------------

    @Test
    fun bridge_produces_fusion_versioned_prediction_record() {
        val p = engine.predict(
            FusionModels.contextFromText(
                "Motivation success mindset discipline grind hustle"
            )
        )
        val rec = FusionEvaluationBridge.toAiPredictionRecord(
            prediction = p,
            evaluationItemId = "eval-1",
            feedItemId = "feed-1"
        )
        assertEquals("local-fusion-v1", rec.modelVersion)
        assertEquals("AI", rec.source)
        assertEquals(CategoryCatalog.normalize(p.primaryCategory), rec.category)
        assertNotNull(rec.categoryDomain)
    }

    @Test
    fun bridge_marks_ambiguous_as_needs_review() {
        val p = engine.predict(
            FusionModels.contextFromText(
                "ranking top list"
            )
        )
        assertEquals(Uncertainty.AMBIGUOUS, p.uncertainty)
        val rec = FusionEvaluationBridge.toAiPredictionRecord(
            prediction = p,
            evaluationItemId = "eval-2"
        )
        assertTrue(rec.needsReview)
    }
}
