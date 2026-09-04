package com.example.feedsense.analysis.evidence.decision

import com.example.feedsense.analysis.evidence.temporal.CandidateCategoryScore
import com.example.feedsense.analysis.evidence.temporal.ConflictLevel
import com.example.feedsense.analysis.evidence.temporal.CoverageState
import com.example.feedsense.analysis.evidence.temporal.DetectedTransition
import com.example.feedsense.analysis.evidence.temporal.EvidenceReference
import com.example.feedsense.analysis.evidence.temporal.ItemEvidenceSnapshot
import com.example.feedsense.analysis.evidence.temporal.PossibleInternalTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-7.
 *
 * Evidence-aware item-level decision tests.
 *
 * Tests build ItemEvidenceSnapshot instances directly to
 * isolate the 8B-7 decision layer from 8B-6 fusion
 * mechanics, giving deterministic control over coverage,
 * candidate scores, conflicts, and transitions.
 *
 * Test matrix:
 *   - DECIDED with strong/multi-source support
 *   - AMBIGUOUS with close / equal candidate scores
 *   - INSUFFICIENT_EVIDENCE with low coverage
 *   - UNKNOWN with no usable category / unmappable
 *   - representative-frame conflict handling
 *   - repeated-OCR (persistence) handling
 *   - missing evidence handling
 *   - decision-uncertainty interaction
 *   - adversarial inputs (negative, huge, NaN)
 *   - rapid-transition items
 *   - short-video / privacy-masked items
 *   - reproducibility (determinism)
 *   - property-style invariants
 *   - baseline-vs-evidence-aware comparison hook
 *   - evaluation bridge to 8A
 *   - observation text generation
 */
class ItemDecisionTest {

    private val engine = ItemDecisionEngine
    private val config = ItemDecisionConfig.DEFAULT

    // --------------------------------------------------
    // SNAPSHOT BUILDER
    // --------------------------------------------------

    private fun snapshot(
        candidates: List<CandidateCategoryScore> =
            emptyList(),
        coverageRatio: Double = 0.9,
        coverageState: CoverageState =
            CoverageState.HIGH_COVERAGE,
        itemDurationMs: Long = 10000,
        observedDurationMs: Long = 9000,
        usableEvidencePoints: Int = 20,
        uniqueEvidenceIdentities: Int = 8,
        evidenceTypesPresent: List<String> =
            listOf("OCR", "visual", "layout", "audio"),
        conflictLevel: ConflictLevel = ConflictLevel.NONE,
        transitionDetected: Boolean = false,
        internalTransitions:
            List<PossibleInternalTransition> = emptyList(),
        representativeFrameOutlier: Boolean? = null,
        ambiguityScore: Double = 0.0,
        isAmbiguous: Boolean = false,
        isInsufficientProof: Boolean = false,
        supportingEvidence:
            Map<String, List<EvidenceReference>> = emptyMap(),
        contradictingEvidence:
            Map<String, List<EvidenceReference>> = emptyMap()
    ): ItemEvidenceSnapshot {
        val evidenceTypes = if (candidates.isEmpty()) {
            emptyList()
        } else {
            evidenceTypesPresent
        }
        return ItemEvidenceSnapshot(
            sessionId = "s1",
            feedItemId = "item1",
            snapshotVersion = "item-evidence-snapshot-v1",
            fusionVersion = "temporal-fusion-v1",
            fusionConfigVersion = "temporal-fusion-config-v1",
            totalEvidencePoints = 30,
            usableEvidencePoints = usableEvidencePoints,
            analyzedFrameCount = 25,
            totalFrameCount = 30,
            itemDurationMs = itemDurationMs,
            coverageState = coverageState,
            coverageRatio = coverageRatio,
            observedDurationMs = observedDurationMs,
            hasGap = false,
            gapCount = 0,
            gapTotalDurationMs = 0,
            uniqueEvidenceIdentities =
                uniqueEvidenceIdentities,
            evidenceTypesPresent = evidenceTypes,
            extractorNames =
                listOf("ocr-extractor", "visual-extractor"),
            candidateCategories = candidates,
            supportingEvidenceByCategory =
                supportingEvidence,
            contradictingEvidenceByCategory =
                contradictingEvidence,
            conflictLevel = conflictLevel,
            conflictDescription = null,
            contradictingCategoryCount = 0,
            ambiguityScore = ambiguityScore,
            isAmbiguous = isAmbiguous,
            ambiguityReason = null,
            isInsufficientEvidence = isInsufficientProof,
            insufficientEvidenceReason = null,
            detectedTransitions = if (transitionDetected) {
                listOf(
                    DetectedTransition(
                        fromCategory = "ads",
                        toCategory = "sports",
                        atRelativeTimeMs = 5000,
                        atFrameIndex = 12,
                        isSharp = true,
                        gapBetweenSegments = false,
                        signals = listOf("exam", "cricket")
                    )
                )
            } else {
                emptyList()
            },
            transitionDetected = transitionDetected,
            possibleInternalTransitions = internalTransitions,
            limitations = emptyList(),
            representativeFrameAgreement =
                if (representativeFrameOutlier == true) false
                else null,
            representativeFrameOutlier =
                representativeFrameOutlier,
            createdAtMs = 0L,
            fusionDurationMs = 0L
        )
    }

    private fun candidate(
        category: String,
        support: Double,
        evidenceTypes: List<String> =
            listOf("OCR", "visual", "layout"),
        supportingCount: Int = 5,
        contradictingCount: Int = 0,
        persistence: String = "SUSTAINED",
        lastSeenRelativeMs: Long = 9000,
        firstSeenRelativeMs: Long = 1000
    ): CandidateCategoryScore {
        return CandidateCategoryScore(
            category = category,
            supportScore = support,
            supportingCount = supportingCount,
            contradictingCount = contradictingCount,
            evidenceTypes = evidenceTypes,
            persistenceLevel = persistence,
            firstSeenRelativeMs = firstSeenRelativeMs,
            lastSeenRelativeMs = lastSeenRelativeMs,
            temporalSpreadMs =
                (lastSeenRelativeMs - firstSeenRelativeMs)
                    .coerceAtLeast(0)
        )
    }

    private fun ref(
        type: String = "OCR",
        value: String = "IPL"
    ): EvidenceReference {
        return EvidenceReference(
            pointId = "p1",
            evidenceType = type,
            extractorName = "ocr-extractor",
            quality = "HIGH",
            valueSummary = value,
            relativeTimeMs = 2000,
            frameIndex = 2
        )
    }

    // --------------------------------------------------
    // TEST 1: STRONG, MULTI-SOURCE DECIDED
    // --------------------------------------------------

    @Test
    fun test1_strongMultiSource_decided() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.85)
            )
        )
        val r = engine.decide(s, config)

        assertEquals(DecisionState.DECIDED, r.decisionState)
        assertEquals("sports", r.primaryCategory)
        assertEquals(UncertaintyState.LOW, r.uncertainty)
        assertTrue(r.supportScore > 0.7)
        assertFalse(r.abstained)
        assertTrue(r.madeCategoryDecision)
        assertNotNull(r.primarySupport)
        assertEquals("sports", r.primarySupport!!.category)
    }

    // --------------------------------------------------
    // TEST 2: CLOSE CANDIDATES -> AMBIGUOUS
    // --------------------------------------------------

    @Test
    fun test2_closeCandidates_ambiguous() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.55),
                candidate("ads", 0.5)
            )
        )
        val r = engine.decide(s, config)

        assertEquals(DecisionState.AMBIGUOUS, r.decisionState)
        assertEquals(UncertaintyState.HIGH, r.uncertainty)
        // Ambiguous preserves the leader as a candidate.
        assertEquals("sports", r.primaryCategory)
        assertTrue(r.uncertaintyReasons
            .contains("ambiguous_close_candidates"))
    }

    // --------------------------------------------------
    // TEST 3: EQUAL SCORES -> AMBIGUOUS (no forced pick)
    // --------------------------------------------------

    @Test
    fun test3_equalScores_ambiguous_notArbitrarilyChosen() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.6),
                candidate("ads", 0.6)
            )
        )
        val r = engine.decide(s, config)

        assertEquals(DecisionState.AMBIGUOUS, r.decisionState)
        assertEquals(0.0, r.decisionMargin, 0.001)
        assertTrue(r.uncertaintyReasons
            .contains("ambiguous_close_candidates"))
    }

    // --------------------------------------------------
    // TEST 4: LOW COVERAGE -> INSUFFICIENT_EVIDENCE
    // --------------------------------------------------

    @Test
    fun test4_lowCoverage_insufficientEvidence() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.9)
            ),
            coverageRatio = 0.02,
            coverageState =
                CoverageState.LOW_COVERAGE
        )
        val r = engine.decide(s, config)

        assertEquals(
            DecisionState.INSUFFICIENT_EVIDENCE,
            r.decisionState
        )
        assertEquals(
            UncertaintyState.UNRESOLVED,
            r.uncertainty
        )
        assertNull(r.primaryCategory)
        assertTrue(r.abstained)
    }

    // --------------------------------------------------
    // TEST 5: NO CANDIDATES -> UNKNOWN
    // --------------------------------------------------

    @Test
    fun test5_noCandidates_unknown() {
        val s = snapshot(candidates = emptyList())
        val r = engine.decide(s, config)

        assertEquals(DecisionState.UNKNOWN, r.decisionState)
        assertNull(r.primaryCategory)
        assertTrue(r.abstained)
    }

    // --------------------------------------------------
    // TEST 6: UNMAPPABLE WEAK SUPPORT -> UNKNOWN
    // --------------------------------------------------

    @Test
    fun test6_weakUnmappableSupport_unknown() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.05)
            ),
            coverageRatio = 0.9
        )
        val r = engine.decide(s, config)

        assertEquals(DecisionState.UNKNOWN, r.decisionState)
        assertNull(r.primaryCategory)
        assertTrue(r.abstained)
    }

    // --------------------------------------------------
    // TEST 7: HIGH CONFLICT -> AMBIGUOUS
    // --------------------------------------------------

    @Test
    fun test7_highConflict_ambiguous() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.8),
                candidate("news", 0.1)
            ),
            conflictLevel = ConflictLevel.HIGH
        )
        val r = engine.decide(s, config)

        assertEquals(DecisionState.AMBIGUOUS, r.decisionState)
        assertTrue(r.temporalConflict == ConflictLevel.HIGH)
        assertTrue(r.uncertaintyReasons
            .contains("conflicting_evidence"))
    }

    // --------------------------------------------------
    // TEST 8: REPRESENTATIVE-FRAME OUTLIER FLAGGED
    // --------------------------------------------------

    @Test
    fun test8_representativeFrameOutlier_surfaced() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.85)
            ),
            representativeFrameOutlier = true
        )
        val r = engine.decide(s, config)

        // Outlier is surfaced as metadata, not silently
        // folded into a single label.
        assertEquals(UncertaintyState.MODERATE, r.uncertainty)
        assertTrue(r.representativeFrameOutlier == true)
        assertTrue(r.uncertaintyReasons
            .contains("representative_frame_outlier"))
    }

    // --------------------------------------------------
    // TEST 9: REPEATED OCR (PERSISTENCE) NOT INFLATED
    // --------------------------------------------------

    @Test
    fun test9_repeatedOcr_persistenceNotInflated() {
        // High supportingCount from duplicated OCR but a
        // single evidence type source.
        val s = snapshot(
            candidates = listOf(
                candidate(
                    "sports",
                    support = 0.6,
                    evidenceTypes = listOf("OCR"),
                    supportingCount = 40,
                    persistence = "BRIEF"
                )
            ),
            uniqueEvidenceIdentities = 1
        )
        val r = engine.decide(s, config)

        val support = r.primarySupport!!
        // Multiple OCR frames do not count as multiple
        // independent sources.
        assertEquals(1, support.sourceCount)
        // Low unique identities forces ambiguity rather
        // than a confident DECIDED.
        assertEquals(DecisionState.AMBIGUOUS, r.decisionState)
        assertTrue(r.uncertaintyReasons
            .contains("low_source_diversity"))
    }

    // --------------------------------------------------
    // TEST 10: MISSING EVIDENCE -> INSUFFICIENT
    // --------------------------------------------------

    @Test
    fun test10_missingEvidence_insufficient() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.8)
            ),
            usableEvidencePoints = 0,
            coverageRatio = 0.0,
            coverageState = CoverageState.NO_COVERAGE
        )
        val r = engine.decide(s, config)

        assertEquals(
            DecisionState.INSUFFICIENT_EVIDENCE,
            r.decisionState
        )
        assertEquals(UncertaintyState.UNRESOLVED, r.uncertainty)
    }

    // --------------------------------------------------
    // TEST 11: SHORT / PRIVACY-MASKED ITEM
    // --------------------------------------------------

    @Test
    fun test11_shortPrivacyMasked_insufficient() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.8)
            ),
            itemDurationMs = 600,
            observedDurationMs = 10,
            coverageRatio = 0.017,
            coverageState =
                CoverageState.LOW_COVERAGE,
            usableEvidencePoints = 1
        )
        val r = engine.decide(s, config)

        assertEquals(
            DecisionState.INSUFFICIENT_EVIDENCE,
            r.decisionState
        )
        assertNull(r.primaryCategory)
    }

    // --------------------------------------------------
    // TEST 12: RAPID INTERNAL TRANSITION SURFACED
    // --------------------------------------------------

    @Test
    fun test12_rapidTransition_internalTransitionSurfaced() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.75)
            ),
            transitionDetected = true,
            internalTransitions = listOf(
                PossibleInternalTransition(
                    fromCategory = "ads",
                    toCategory = "sports",
                    atRelativeTimeMs = 3000,
                    durationInSegmentMs = 2500,
                    evidenceCount = 4,
                    reason = "ads overlay then sport content"
                )
            )
        )
        val r = engine.decide(s, config)

        assertTrue(r.internalTransitionDetected)
        assertTrue(r.transitionDetected)
        assertTrue(r.uncertaintyReasons
            .contains("possible_internal_transition"))
        // Primary category is not artificially split.
        assertEquals("sports", r.primaryCategory)
    }

    // --------------------------------------------------
    // TEST 13: ADVERSARIAL NEGATIVE / HUGE / NaN SCORES
    // --------------------------------------------------

    @Test
    fun test13_adversarialScores_handled() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", -0.5),
                candidate("news", 1e9)
            ),
            coverageRatio = 0.9
        )
        val r = engine.decide(s, config)

        // Nonsensical candidate is not trusted.
        assertNotNull(r)
        // A negative or absurd score must not yield a
        // fabricated confident category.
        assertFalse(
            r.decisionState == DecisionState.DECIDED &&
                r.uncertainty == UncertaintyState.LOW
        )
    }

    @Test
    fun test13b_nanScore_noCrash() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", Double.NaN)
            ),
            coverageRatio = 0.9
        )
        val r = engine.decide(s, config)
        assertNotNull(r)
    }

    // --------------------------------------------------
    // TEST 14: DETERMINISM / REPRODUCIBILITY
    // --------------------------------------------------

    @Test
    fun test14_reproducibility_sameInputSameResult() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.85)
            )
        )
        val r1 = engine.decide(s, config)
        val r2 = engine.decide(s, config)

        assertEquals(r1.decisionState, r2.decisionState)
        assertEquals(r1.uncertainty, r2.uncertainty)
        assertEquals(r1.primaryCategory, r2.primaryCategory)
        assertEquals(r1.supportScore, r2.supportScore, 0.0)
    }

    // --------------------------------------------------
    // TEST 15: PROPERTY - DECISION/MARGIN CONSISTENCY
    // --------------------------------------------------

    @Test
    fun test15_property_supportSortedDescending() {
        val s = snapshot(
            candidates = listOf(
                candidate("fitness", 0.3),
                candidate("ads", 0.7),
                candidate("news", 0.2)
            )
        )
        val r = engine.decide(s, config)

        val scores = r.candidateCategories
            .map { it.totalSupport }
        assertEquals(scores.sortedDescending(), scores)
        // Primary support equals top candidate.
        assertEquals(
            scores.first(),
            r.supportScore,
            0.001
        )
    }

    // --------------------------------------------------
    // TEST 16: PROPERTY - PRIMARY MATCHES TOP CANDIDATE
    // --------------------------------------------------

    @Test
    fun test16_property_primaryMatchesTopWhenDecided() {
        val s = snapshot(
            candidates = listOf(
                candidate("gaming", 0.5),
                candidate("news", 0.2)
            ),
            coverageRatio = 0.5,
            coverageState =
                CoverageState.PARTIAL_COVERAGE
        )
        val r = engine.decide(s, config)

        if (r.decisionState == DecisionState.DECIDED ||
            r.decisionState == DecisionState.AMBIGUOUS) {
            assertEquals("gaming", r.primaryCategory)
        }
    }

    // --------------------------------------------------
    // TEST 17: DECIDED WITH PARTIAL COVERAGE -> MODERATE
    // --------------------------------------------------

    @Test
    fun test17_partialCoverage_moderateUncertainty() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.8)
            ),
            coverageRatio = 0.4,
            coverageState =
                CoverageState.PARTIAL_COVERAGE
        )
        val r = engine.decide(s, config)

        // Coverage >= minCoverageForDecided (0.1) so it can
        // decide, but not full, so uncertainty is not LOW.
        assertEquals(DecisionState.DECIDED, r.decisionState)
        assertNotEquals(UncertaintyState.LOW, r.uncertainty)
    }

    // --------------------------------------------------
    // TEST 18: BASELINE VS EVIDENCE-AWARE COMPARISON
    // --------------------------------------------------

    @Test
    fun test18_comparison_agree() {
        val baseline = BaselinePrediction(
            primaryCategory = "sports",
            classifierConfidence = 0.9,
            uncertaintyLevel = "LOW",
            needsReview = false
        )
        val decided = engine.decide(
            snapshot(
                candidates = listOf(candidate("sports", 0.85))
            ),
            config
        )

        val cmp = PredictionComparison.of(baseline, decided)
        assertTrue(cmp.categoriesAgree)
        assertEquals(ComparisonKind.AGREE, cmp.comparisonKind)
    }

    @Test
    fun test18b_comparison_baselineOnlyDecided() {
        val baseline = BaselinePrediction(
            primaryCategory = "ads",
            classifierConfidence = 0.8
        )
        val abstained = engine.decide(
            snapshot(
                candidates = listOf(candidate("sports", 0.9)),
                coverageRatio = 0.02,
                coverageState =
                    CoverageState.LOW_COVERAGE
            ),
            config
        )

        val cmp = PredictionComparison.of(baseline, abstained)
        assertFalse(cmp.categoriesAgree)
        assertEquals(
            ComparisonKind.BASELINE_ONLY_DECIDED,
            cmp.comparisonKind
        )
        assertTrue(cmp.baselineDecidedButEvidenceAwereAbstained)
    }

    @Test
    fun test18c_comparison_disagree() {
        val baseline = BaselinePrediction(
            primaryCategory = "ads"
        )
        val decided = engine.decide(
            snapshot(
                candidates = listOf(candidate("sports", 0.85))
            ),
            config
        )
        val cmp = PredictionComparison.of(baseline, decided)
        assertEquals("ads", cmp.baselinePrimary)
        assertEquals("sports", cmp.evidenceAwarePrimary)
        assertEquals(ComparisonKind.DISAGREE, cmp.comparisonKind)
    }

    // --------------------------------------------------
    // TEST 19: EVALUATION BRIDGE TO 8A
    // --------------------------------------------------

    @Test
    fun test19_bridge_toAiPredictionRecord() {
        val decided = engine.decide(
            snapshot(
                candidates = listOf(candidate("sports", 0.85))
            ),
            config
        )
        val record =
            DecisionEvaluationBridge.toAiPredictionRecord(
                decision = decided,
                evaluationItemId = "eval-1"
            )

        assertEquals("sports", record.category)
        assertEquals("AI_EVIDENCE_AWARE", record.source)
        assertEquals(0.85, record.confidence!!, 0.001)
        assertEquals("LOW", record.uncertaintyLevel)
        assertEquals("item1", record.feedItemId)
        assertFalse(record.needsReview)
    }

    @Test
    fun test19b_bridge_abstained_needsReview() {
        val abstained = engine.decide(
            snapshot(
                candidates = listOf(candidate("sports", 0.9)),
                coverageRatio = 0.02,
                coverageState =
                    CoverageState.LOW_COVERAGE
            ),
            config
        )
        val record =
            DecisionEvaluationBridge.toAiPredictionRecord(
                decision = abstained,
                evaluationItemId = "eval-2"
            )
        assertNull(record.category)
        assertTrue(record.needsReview)
        assertEquals("HIGH", record.uncertaintyLevel)
    }

    // --------------------------------------------------
    // TEST 20: OBSERVATION TEXT
    // --------------------------------------------------

    @Test
    fun test20_observation_decided() {
        val decided = engine.decide(
            snapshot(
                candidates = listOf(candidate("sports", 0.85))
            ),
            config
        )
        val text = DecisionObservationText.format(decided)
        assertTrue(text.startsWith("AI: Decided sports"))
        assertTrue(text.contains("DECIDED"))
        assertTrue(text.contains("LOW"))
    }

    @Test
    fun test20b_observation_insufficient() {
        val abstained = engine.decide(
            snapshot(
                candidates = listOf(candidate("sports", 0.9)),
                coverageRatio = 0.0,
                coverageState = CoverageState.NO_COVERAGE
            ),
            config
        )
        val text = DecisionObservationText.format(abstained)
        assertTrue(text.startsWith("AI: Insufficient evidence"))
        assertTrue(text.contains("INSUFFICIENT_EVIDENCE"))
    }

    // --------------------------------------------------
    // TEST 21: VERSIONING
    // --------------------------------------------------

    @Test
    fun test21_versioning() {
        val decided = engine.decide(
            snapshot(candidates = listOf(candidate("sports", 0.85))),
            config
        )
        assertEquals("item-decision-v1", decided.decisionVersion)
        assertEquals("temporal-fusion-v1", decided.fusionVersion)
        assertNotNull(decided.modelVersion)
    }

    // --------------------------------------------------
    // TEST 22: CONFIG VALIDATION
    // --------------------------------------------------

    @Test
    fun test22_configVersion() {
        assertEquals(
            "item-decision-config-v1",
            ItemDecisionConfig.CONFIG_VERSION
        )
        assertEquals(
            "item-decision-config-v1",
            config.configVersion
        )
        assertEquals("item-decision-v1", config.decisionVersion)
    }

    // --------------------------------------------------
    // TEST 23: CONSERVATIVE CONFIG ABSTAINS MORE
    // --------------------------------------------------

    @Test
    fun test23_conservativeConfig_moreConservative() {
        // Margin ratio = (0.6 - 0.42) / 0.6 = 0.30:
        //   - DEFAULT (minMarginRatio=0.25)   -> can decide
        //   - CONSERVATIVE (minMarginRatio=0.35)
        //     -> the lead is too thin, abstains as AMBIGUOUS
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.6),
                candidate("news", 0.42)
            ),
            coverageRatio = 0.5,
            coverageState =
                CoverageState.PARTIAL_COVERAGE,
            uniqueEvidenceIdentities = 5,
            evidenceTypesPresent =
                listOf("OCR", "visual")
        )
        val default = engine.decide(s, ItemDecisionConfig.DEFAULT)
        val conserv = engine.decide(
            s,
            ItemDecisionConfig.CONSERVATIVE
        )

        assertEquals(DecisionState.DECIDED, default.decisionState)
        // Conservative abstains (ambiguous) where default
        // decided, because the margin threshold is stricter.
        assertEquals(DecisionState.AMBIGUOUS, conserv.decisionState)
    }

    // --------------------------------------------------
    // TEST 24: SUPPORTING / CONTRADICTING EVIDENCE
    // --------------------------------------------------

    @Test
    fun test24_supportingAndContradicting_surfaced() {
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.85)
            ),
            supportingEvidence = mapOf(
                "sports" to listOf(ref("OCR", "IPL"))
            ),
            contradictingEvidence = mapOf(
                "sports" to listOf(ref("OCR", "ad overlay"))
            )
        )
        val r = engine.decide(s, config)
        assertTrue(r.supportingEvidence.containsKey("sports"))
        assertTrue(r.contradictingEvidence.containsKey("sports"))
        assertTrue(r.supportingEvidence["sports"]!!
            .any { it.contains("IPL") })
    }

    // --------------------------------------------------
    // TEST 25: MUST NEVER FABRICATE A CATEGORY
    // --------------------------------------------------

    @Test
    fun test25_neverFabricatesCategory() {
        // Even with decent coverage, if the strongest
        // candidate is below minSupport, never decide.
        val weak = snapshot(
            candidates = listOf(
                candidate("sports", 0.1)
            ),
            coverageRatio = 0.9
        )
        val r = engine.decide(weak, config)
        assertNull(r.primaryCategory)
        assertEquals(DecisionState.UNKNOWN, r.decisionState)
    }

    // --------------------------------------------------
    // TEST 26: CRITICAL CONTRADICTION MARGIN HANDLING
    // --------------------------------------------------

    @Test
    fun test26_secondOverTop_noNegativeDecided() {
        // Second candidate above top -> not a clean decide.
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.4),
                candidate("ads", 0.7)
            ),
            coverageRatio = 0.9
        )
        val r = engine.decide(s, config)
        assertEquals(0.7, r.supportScore, 0.0001)
        // sorted descending => ads is leader
        assertEquals("ads", r.primaryCategory)
    }

    // --------------------------------------------------
    // TEST 27: PROPERTY - AREA SOMETIMES REQUIRES ABSTENTION
    // --------------------------------------------------

    @Test
    fun test27_emptySnapshot_unknownEmpty() {
        val empty = snapshot(candidates = emptyList())
        val r = engine.decide(empty, config)
        assertEquals(DecisionState.UNKNOWN, r.decisionState)
        assertEquals(UncertaintyState.UNRESOLVED, r.uncertainty)
        assertTrue(r.uncertaintyReasons.contains("no_evidence"))
    }

    // --------------------------------------------------
    // TEST 28: DETERMINISM ACROSS MANY INPUTS
    // --------------------------------------------------

    @Test
    fun test28_manyInputsRemainDeterministic() {
        for (i in 0 until 50) {
            val s = snapshot(
                candidates = listOf(
                    candidate("cat$i", 0.5 + (i % 5) * 0.1)
                ),
                coverageRatio = 0.1 + (i % 5) * 0.2,
                coverageState = CoverageState.PARTIAL_COVERAGE
            )
            val a = engine.decide(s, config)
            val b = engine.decide(s, config)
            assertEquals(a, b)
        }
    }
}
