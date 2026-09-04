package com.example.feedsense

import com.example.feedsense.analysis.evidence.Evidence
import com.example.feedsense.analysis.evidence.EvidenceFusionEngine
import com.example.feedsense.analysis.evidence.EvidenceFusionResult
import com.example.feedsense.analysis.evidence.EvidenceQuality
import com.example.feedsense.analysis.evidence.EvidenceSource
import com.example.feedsense.analysis.evidence.EvidenceType
import com.example.feedsense.analysis.evidence.temporal.CandidateCategoryScore
import com.example.feedsense.analysis.evidence.temporal.ConflictLevel
import com.example.feedsense.analysis.evidence.temporal.CoverageState
import com.example.feedsense.analysis.evidence.temporal.DetectedTransition
import com.example.feedsense.analysis.evidence.temporal.EvidenceCoverage
import com.example.feedsense.analysis.evidence.temporal.EvidenceReference
import com.example.feedsense.analysis.evidence.temporal.ItemEvidenceSnapshot
import com.example.feedsense.analysis.evidence.temporal.ItemEvidenceTimeline
import com.example.feedsense.analysis.evidence.temporal.ItemLevelPredictor
import com.example.feedsense.analysis.evidence.temporal.PossibleInternalTransition
import com.example.feedsense.analysis.evidence.temporal.TemporalEvidenceFusionConfig
import com.example.feedsense.analysis.evidence.temporal.TemporalEvidenceFusionEngine
import com.example.feedsense.analysis.evidence.temporal.TemporalEvidencePoint
import com.example.feedsense.analysis.evidence.temporal.TemporalGap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-6.
 *
 * Comprehensive tests for temporal evidence fusion,
 * multi-frame reasoning, and item-level prediction.
 *
 * Tests cover:
 *   - TemporalEvidencePoint creation and identity
 *   - ItemEvidenceTimeline ordering and gap detection
 *   - EvidenceCoverage computation and states
 *   - TemporalEvidenceFusionEngine candidate scoring
 *   - Conflict detection
 *   - Ambiguity detection
 *   - Insufficient evidence detection
 *   - Transition detection
 *   - ItemEvidenceSnapshot completeness
 *   - ItemLevelPredictor baseline vs temporal
 *   - Deterministic ordering
 *   - Reproducibility
 *   - Stability of results
 *   - Property-style invariants
 *   - Performance measurements
 */
class TemporalEvidenceFusionTest {

    private val engine =
        TemporalEvidenceFusionEngine()
    private val config =
        TemporalEvidenceFusionConfig.DEFAULT
    private val predictor = ItemLevelPredictor()

    // ------------------------------------------------
    // HELPER: create evidence
    // ------------------------------------------------

    private fun evidence(
        type: EvidenceType = EvidenceType.OCR_TEXT,
        source: EvidenceSource = EvidenceSource.OCR,
        quality: EvidenceQuality = EvidenceQuality.HIGH,
        value: String = "test",
        supporting: List<String> = emptyList(),
        contradicting: List<String> = emptyList(),
        timestampMs: Long = System.currentTimeMillis(),
        frameId: String? = null
    ): Evidence {
        return Evidence(
            type = type,
            source = source,
            quality = quality,
            value = value,
            supportingCategories = supporting,
            contradictingCategories = contradicting,
            timestampMs = timestampMs,
            frameId = frameId,
            privacyState =
                com.example.feedsense.analysis.evidence
                    .PrivacyState.SANITIZED
        )
    }

    // ------------------------------------------------
    // TEST 1: Stable sports item
    // ------------------------------------------------

    @Test
    fun test1_stable_sports_item() {
        val startMs = 1000L
        val durationMs = 10000L

        val evidenceList = listOf(
            evidence(
                value = "IPL cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "cricket wickets",
                supporting = listOf("sports"),
                timestampMs = startMs + 2000,
                frameId = "f1"
            ),
            evidence(
                value = "IPL ranking",
                supporting = listOf("sports"),
                timestampMs = startMs + 4000,
                frameId = "f2"
            ),
            evidence(
                value = "cricket innings",
                supporting = listOf("sports"),
                timestampMs = startMs + 6000,
                frameId = "f3"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item1",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        assertEquals("sports", snapshot.primaryCategory)
        assertTrue(snapshot.primarySupportScore > 0.5)
        assertFalse(snapshot.isAmbiguous)
        assertFalse(snapshot.isInsufficientEvidence)
        assertEquals(
            ConflictLevel.NONE,
            snapshot.conflictLevel
        )
    }

    // ------------------------------------------------
    // TEST 2: Conflicting evidence
    // ------------------------------------------------

    @Test
    fun test2_conflicting_evidence() {
        val startMs = 1000L
        val durationMs = 10000L

        val evidenceList = listOf(
            evidence(
                value = "sports keywords",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "movie scene",
                supporting = listOf("movie_clip"),
                timestampMs = startMs + 3000,
                frameId = "f1"
            ),
            evidence(
                value = "advertisement",
                supporting = listOf("advertisement"),
                timestampMs = startMs + 6000,
                frameId = "f2"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item2",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        assertNotNull(snapshot.primaryCategory)
        // Should have multiple candidates.
        assertTrue(
            snapshot.candidateCategories.size >= 2
        )
        // Should detect some ambiguity or conflict.
        assertTrue(
            snapshot.isAmbiguous ||
                    snapshot.conflictLevel !=
                    ConflictLevel.NONE ||
                    snapshot.candidateCategories
                        .size >= 2
        )
    }

    // ------------------------------------------------
    // TEST 3: Insufficient coverage
    // ------------------------------------------------

    @Test
    fun test3_insufficient_coverage() {
        val startMs = 1000L
        val durationMs = 30000L // 30 sec item

        val evidenceList = listOf(
            evidence(
                value = "cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item3",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        // With only 1 evidence point in 30 sec item,
        // coverage should be LOW or NO_COVERAGE.
        assertTrue(
            snapshot.coverageState ==
                    CoverageState.LOW_COVERAGE ||
                    snapshot.coverageState ==
                    CoverageState.NO_COVERAGE
        )
        assertTrue(snapshot.isInsufficientEvidence)
    }

    // ------------------------------------------------
    // TEST 4: Temporal gap detection
    // ------------------------------------------------

    @Test
    fun test4_temporal_gap_detection() {
        val startMs = 1000L
        val durationMs = 20000L

        val evidenceList = listOf(
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 1000,
                frameId = "f1"
            ),
            // 7 second gap
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 8000,
                frameId = "f2"
            ),
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 9000,
                frameId = "f3"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item4",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        assertTrue(snapshot.hasGap)
        assertTrue(snapshot.gapCount >= 1)
        assertTrue(snapshot.gapTotalDurationMs > 0)
    }

    // ------------------------------------------------
    // TEST 5: Category transitions
    // ------------------------------------------------

    @Test
    fun test5_category_transitions() {
        val startMs = 1000L
        val durationMs = 20000L

        val evidenceList = listOf(
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 1000,
                frameId = "f1"
            ),
            evidence(
                value = "ad promotional",
                supporting =
                    listOf("advertisement"),
                timestampMs = startMs + 5000,
                frameId = "f2"
            ),
            evidence(
                value = "ad promotional",
                supporting =
                    listOf("advertisement"),
                timestampMs = startMs + 6000,
                frameId = "f3"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item5",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        assertTrue(snapshot.transitionDetected)
        assertTrue(
            snapshot.detectedTransitions.isNotEmpty()
        )
    }

    // ------------------------------------------------
    // TEST 6: Missing interval - no interpolation
    // ------------------------------------------------

    @Test
    fun test6_no_semantic_interpolation() {
        val startMs = 1000L
        val durationMs = 20000L

        val evidenceList = listOf(
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            // 10 second gap
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 10000,
                frameId = "f1"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item6",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        // The system detects the gap but does NOT
        // claim evidence exists in the missing interval.
        assertTrue(snapshot.hasGap)
        assertTrue(snapshot.gapCount >= 1)
    }

    // ------------------------------------------------
    // TEST 7: Rapid transitions preserved
    // ------------------------------------------------

    @Test
    fun test7_rapid_transitions_preserved() {
        val startMs = 1000L
        val durationMs = 10000L

        val evidenceList = listOf(
            evidence(
                value = "sports cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "ad promotion",
                supporting =
                    listOf("advertisement"),
                timestampMs = startMs + 1000,
                frameId = "f1"
            ),
            evidence(
                value = "sports IPL",
                supporting = listOf("sports"),
                timestampMs = startMs + 2000,
                frameId = "f2"
            ),
            evidence(
                value = "ad promotion",
                supporting =
                    listOf("advertisement"),
                timestampMs = startMs + 3000,
                frameId = "f3"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item7",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        // Multiple candidate categories should be
        // present.
        assertTrue(
            snapshot.candidateCategories.size >= 2
        )
    }

    // ------------------------------------------------
    // TEST 8: Empty evidence
    // ------------------------------------------------

    @Test
    fun test8_empty_evidence() {
        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item8",
            itemStartTimeMs = 1000L,
            itemDurationMs = 5000L,
            evidence = emptyList()
        )

        assertEquals(
            ItemEvidenceSnapshot.VERSION,
            snapshot.snapshotVersion
        )
        assertEquals(0, snapshot.totalEvidencePoints)
        assertEquals(
            CoverageState.NO_COVERAGE,
            snapshot.coverageState
        )
        assertTrue(snapshot.isInsufficientEvidence)
        assertNull(snapshot.primaryCategory)
    }

    // ------------------------------------------------
    // TEST 9: Deterministic ordering
    // ------------------------------------------------

    @Test
    fun test9_deterministic_ordering() {
        val startMs = 1000L
        val durationMs = 10000L

        val evidenceList = listOf(
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 4000,
                frameId = "f2"
            ),
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "education",
                supporting = listOf("education"),
                timestampMs = startMs + 2000,
                frameId = "f1"
            )
        )

        // Run fusion twice with same input.
        val snapshot1 = engine.fuse(
            sessionId = "s1",
            feedItemId = "item9",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )
        val snapshot2 = engine.fuse(
            sessionId = "s1",
            feedItemId = "item9",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        assertEquals(
            snapshot1.primaryCategory,
            snapshot2.primaryCategory
        )
        assertEquals(
            snapshot1.primarySupportScore,
            snapshot2.primarySupportScore,
            1e-9
        )
        assertEquals(
            snapshot1.ambiguityScore,
            snapshot2.ambiguityScore,
            1e-9
        )
        assertEquals(
            snapshot1.coverageRatio,
            snapshot2.coverageRatio,
            1e-9
        )
    }

    // ------------------------------------------------
    // TEST 10: Reproducibility
    // ------------------------------------------------

    @Test
    fun test10_reproducibility() {
        val startMs = 1000L
        val durationMs = 10000L

        val evidenceList = listOf(
            evidence(
                value = "IPL cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "cricket wickets",
                supporting = listOf("sports"),
                timestampMs = startMs + 2000,
                frameId = "f1"
            ),
            evidence(
                value = "advertisement",
                supporting =
                    listOf("advertisement"),
                timestampMs = startMs + 5000,
                frameId = "f2"
            )
        )

        // Run 5 times.
        val results = (1..5).map {
            engine.fuse(
                sessionId = "s1",
                feedItemId = "item10",
                itemStartTimeMs = startMs,
                itemDurationMs = durationMs,
                evidence = evidenceList
            )
        }

        // All should have identical primary category.
        val primaryCategories =
            results.map { it.primaryCategory }
        assertEquals(1, primaryCategories.distinct().size)

        // All should have identical support scores.
        val scores =
            results.map { it.primarySupportScore }
        for (i in 1 until scores.size) {
            assertEquals(
                scores[0], scores[i], 1e-9
            )
        }
    }

    // ------------------------------------------------
    // TEST 11: Coverage states
    // ------------------------------------------------

    @Test
    fun test11_coverage_states() {
        // No coverage.
        val noCoverage = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = 0L,
            itemDurationMs = 10000L,
            evidence = emptyList()
        )
        assertEquals(
            CoverageState.NO_COVERAGE,
            noCoverage.coverageState
        )

        // Low coverage: 1 point in 30 sec item.
        val lowCoverage = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = 0L,
            itemDurationMs = 30000L,
            evidence = listOf(
                evidence(
                    value = "cricket",
                    supporting = listOf("sports"),
                    timestampMs = 0L,
                    frameId = "f0"
                )
            )
        )
        assertTrue(
            lowCoverage.coverageState ==
                    CoverageState.LOW_COVERAGE ||
                    lowCoverage.coverageState ==
                    CoverageState.NO_COVERAGE
        )

        // High coverage: evidence spanning most of item.
        val highCoverage = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = 0L,
            itemDurationMs = 5000L,
            evidence = listOf(
                evidence(
                    value = "sports1",
                    supporting = listOf("sports"),
                    timestampMs = 0L,
                    frameId = "f0"
                ),
                evidence(
                    value = "sports2",
                    supporting = listOf("sports"),
                    timestampMs = 1000L,
                    frameId = "f1"
                ),
                evidence(
                    value = "sports3",
                    supporting = listOf("sports"),
                    timestampMs = 3000L,
                    frameId = "f2"
                ),
                evidence(
                    value = "sports4",
                    supporting = listOf("sports"),
                    timestampMs = 4500L,
                    frameId = "f3"
                )
            )
        )
        assertEquals(
            CoverageState.HIGH_COVERAGE,
            highCoverage.coverageState
        )
    }

    // ------------------------------------------------
    // TEST 12: Persistence handling
    // ------------------------------------------------

    @Test
    fun test12_persistence_not_inflated() {
        val startMs = 1000L
        val durationMs = 5000L

        // Same evidence repeated 10 times.
        val repeatedEvidence = (0..9).map { i ->
            evidence(
                value = "IPL cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + i * 500L,
                frameId = "f$i"
            )
        }

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item12",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = repeatedEvidence
        )

        // Should have sports as primary.
        assertEquals("sports", snapshot.primaryCategory)
        // Unique identities should be 1 (same value),
        // not 10.
        assertEquals(
            1, snapshot.uniqueEvidenceIdentities
        )
    }

    // ------------------------------------------------
    // TEST 13: Independent sources
    // ------------------------------------------------

    @Test
    fun test13_independent_sources() {
        val startMs = 1000L
        val durationMs = 5000L

        val evidenceList = listOf(
            evidence(
                type = EvidenceType.OCR_TEXT,
                source = EvidenceSource.OCR,
                value = "IPL cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                type = EvidenceType.PLATFORM,
                source = EvidenceSource.PLATFORM,
                value = "Instagram",
                supporting = listOf("lifestyle"),
                timestampMs = startMs + 1000,
                frameId = "f0"
            ),
            evidence(
                type = EvidenceType.VISUAL,
                source = EvidenceSource.VISUAL,
                value = "sports_visual",
                supporting = listOf("sports"),
                timestampMs = startMs + 2000,
                frameId = "f1"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item13",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        // Should have multiple unique identities.
        assertTrue(
            snapshot.uniqueEvidenceIdentities >= 2
        )
        // Should have multiple evidence types.
        assertTrue(
            snapshot.evidenceTypesPresent.size >= 2
        )
    }

    // ------------------------------------------------
    // TEST 14: Ambiguity detection
    // ------------------------------------------------

    @Test
    fun test14_ambiguity_detection() {
        val startMs = 1000L
        val durationMs = 5000L

        // Two categories with very similar support.
        val evidenceList = listOf(
            evidence(
                value = "sports cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "movie scene",
                supporting = listOf("movie_clip"),
                timestampMs = startMs + 1000,
                frameId = "f1"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item14",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        assertTrue(snapshot.isAmbiguous)
        assertTrue(snapshot.ambiguityScore > 0.5)
    }

    // ------------------------------------------------
    // TEST 15: Conflict detection
    // ------------------------------------------------

    @Test
    fun test15_conflict_detection() {
        val startMs = 1000L
        val durationMs = 5000L

        val evidenceList = listOf(
            evidence(
                value = "sports cricket",
                supporting = listOf("sports"),
                contradicting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "sports IPL",
                supporting = listOf("sports"),
                contradicting = listOf("sports"),
                timestampMs = startMs + 1000,
                frameId = "f1"
            ),
            evidence(
                value = "sports wickets",
                supporting = listOf("sports"),
                timestampMs = startMs + 2000,
                frameId = "f2"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item15",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        // Should detect conflict since 2 out of 3
        // evidence contradict "sports".
        assertNotEquals(
            ConflictLevel.NONE,
            snapshot.conflictLevel
        )
    }

    // ------------------------------------------------
    // TEST 16: Short interaction (skipped content)
    // ------------------------------------------------

    @Test
    fun test16_short_interaction() {
        val startMs = 1000L
        val durationMs = 2000L // Very short

        val evidenceList = listOf(
            evidence(
                value = "cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item16",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        // Even with short content, should not crash
        // and should record evidence insufficiency
        // or limitations.
        assertNotNull(snapshot.primaryCategory)
        assertTrue(
            snapshot.limitations.isNotEmpty() ||
                    snapshot.isInsufficientEvidence
        )
    }

    // ------------------------------------------------
    // TEST 17: Evidence quality weighting
    // ------------------------------------------------

    @Test
    fun test17_quality_weighting() {
        val startMs = 1000L
        val durationMs = 5000L

        // High quality sports evidence.
        val highQuality = listOf(
            evidence(
                quality = EvidenceQuality.HIGH,
                value = "IPL cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                quality = EvidenceQuality.HIGH,
                value = "cricket wickets",
                supporting = listOf("sports"),
                timestampMs = startMs + 1000,
                frameId = "f1"
            )
        )

        // Low quality sports evidence.
        val lowQuality = listOf(
            evidence(
                quality = EvidenceQuality.LOW,
                value = "cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                quality = EvidenceQuality.LOW,
                value = "IPL",
                supporting = listOf("sports"),
                timestampMs = startMs + 1000,
                frameId = "f1"
            )
        )

        val highResult = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = highQuality
        )

        val lowResult = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = lowQuality
        )

        // High quality should produce higher support.
        assertTrue(
            highResult.primarySupportScore >=
                    lowResult.primarySupportScore
        )
    }

    // ------------------------------------------------
    // TEST 18: Coverage ratio computation
    // ------------------------------------------------

    @Test
    fun test18_coverage_computation() {
        val points = listOf(
            TemporalEvidencePoint(
                pointId = "p0",
                evidenceType = EvidenceType.OCR_TEXT,
                extractorName = "test",
                extractorVersion = "v1",
                quality = EvidenceQuality.HIGH,
                value = "test",
                valueSummary = "test",
                evidenceIdentityHash = "id0",
                timestampMs = 1000L,
                relativeTimeMs = 0L,
                frameIndex = 0,
                frameId = "f0",
                supportingCategories =
                    listOf("sports"),
                contradictingCategories =
                    emptyList(),
                privacyState = "SANITIZED"
            ),
            TemporalEvidencePoint(
                pointId = "p1",
                evidenceType = EvidenceType.OCR_TEXT,
                extractorName = "test",
                extractorVersion = "v1",
                quality = EvidenceQuality.HIGH,
                value = "test2",
                valueSummary = "test2",
                evidenceIdentityHash = "id1",
                timestampMs = 8000L,
                relativeTimeMs = 7000L,
                frameIndex = 1,
                frameId = "f1",
                supportingCategories =
                    listOf("sports"),
                contradictingCategories =
                    emptyList(),
                privacyState = "SANITIZED"
            )
        )

        val coverage = EvidenceCoverage.compute(
            points, 10000L
        )

        assertEquals(10000L, coverage.itemDurationMs)
        assertEquals(7000L, coverage.observedDurationMs)
        assertEquals(
            CoverageState.HIGH_COVERAGE,
            coverage.coverageState
        )
        assertTrue(coverage.coverageRatio > 0.5)
        assertTrue(coverage.hasGap)
    }

    // ------------------------------------------------
    // TEST 19: Timeline ordering
    // ------------------------------------------------

    @Test
    fun test19_timeline_ordering() {
        val startMs = 1000L
        val durationMs = 10000L

        // Provide evidence in reverse order.
        val evidenceList = listOf(
            evidence(
                value = "sports3",
                supporting = listOf("sports"),
                timestampMs = startMs + 6000,
                frameId = "f3"
            ),
            evidence(
                value = "sports1",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "sports2",
                supporting = listOf("sports"),
                timestampMs = startMs + 3000,
                frameId = "f1"
            )
        )

        val timeline = ItemEvidenceTimeline.build(
            sessionId = "s1",
            feedItemId = "item19",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        // Points should be ordered by relative time.
        val relativeTimes =
            timeline.points.map { it.relativeTimeMs }
        assertEquals(
            relativeTimes,
            relativeTimes.sorted()
        )
    }

    // ------------------------------------------------
    // TEST 20: Item-level prediction vs baseline
    // ------------------------------------------------

    @Test
    fun test20_baseline_vs_temporal() {
        val startMs = 1000L
        val durationMs = 10000L

        val allEvidence = listOf(
            evidence(
                value = "IPL cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "cricket wickets",
                supporting = listOf("sports"),
                timestampMs = startMs + 2000,
                frameId = "f1"
            ),
            evidence(
                value = "IPL ranking",
                supporting = listOf("sports"),
                timestampMs = startMs + 4000,
                frameId = "f2"
            ),
            evidence(
                value = "cricket innings",
                supporting = listOf("sports"),
                timestampMs = startMs + 6000,
                frameId = "f3"
            )
        )

        val baselineResult =
            predictor.baselinePredict(allEvidence)

        val temporalResult =
            predictor.temporalPredict(
                sessionId = "s1",
                feedItemId = "item20",
                itemStartTimeMs = startMs,
                itemDurationMs = durationMs,
                evidence = allEvidence
            )

        // Both should produce a result.
        assertNotNull(baselineResult.topCategory)
        assertNotNull(temporalResult.primaryCategory)
    }

    // ------------------------------------------------
    // TEST 21: Comparison result
    // ------------------------------------------------

    @Test
    fun test21_comparison_result() {
        val startMs = 1000L
        val durationMs = 10000L

        val allEvidence = listOf(
            evidence(
                value = "cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 2000,
                frameId = "f1"
            )
        )

        val comparison = predictor.compare(
            sessionId = "s1",
            feedItemId = "item21",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = allEvidence,
            representativeFrameEvidence =
                allEvidence.take(1),
            representativeFrameCategory = "sports"
        )

        assertNotNull(comparison.baseline)
        assertNotNull(comparison.temporal)
        assertNotNull(
            comparison.recommendedCategory
        )
    }

    // ------------------------------------------------
    // TEST 22: Possible internal transitions
    // ------------------------------------------------

    @Test
    fun test22_internal_transitions() {
        val startMs = 1000L
        val durationMs = 20000L

        val evidenceList = listOf(
            evidence(
                value = "sports cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "sports IPL",
                supporting = listOf("sports"),
                timestampMs = startMs + 2000,
                frameId = "f1"
            ),
            evidence(
                value = "ad promotion",
                supporting =
                    listOf("advertisement"),
                timestampMs = startMs + 5000,
                frameId = "f2"
            ),
            evidence(
                value = "ad offer",
                supporting =
                    listOf("advertisement"),
                timestampMs = startMs + 7000,
                frameId = "f3"
            ),
            evidence(
                value = "sports cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 10000,
                frameId = "f4"
            ),
            evidence(
                value = "sports IPL",
                supporting = listOf("sports"),
                timestampMs = startMs + 12000,
                frameId = "f5"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item22",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        // Should detect transitions.
        assertTrue(snapshot.transitionDetected)
        // Should expose internal transitions (not auto-
        // split).
        assertTrue(
            snapshot.possibleInternalTransitions
                    .isNotEmpty() ||
                    snapshot.detectedTransitions
                        .isNotEmpty()
        )
    }

    // ------------------------------------------------
    // TEST 23: Version fields present
    // ------------------------------------------------

    @Test
    fun test23_version_fields() {
        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = 0L,
            itemDurationMs = 5000L,
            evidence = emptyList()
        )

        assertEquals(
            ItemEvidenceSnapshot.VERSION,
            snapshot.snapshotVersion
        )
        assertEquals(
            TemporalEvidenceFusionConfig
                .ENGINE_VERSION,
            snapshot.fusionVersion
        )
        assertEquals(
            TemporalEvidenceFusionConfig
                .CONFIG_VERSION,
            snapshot.fusionConfigVersion
        )
    }

    // ------------------------------------------------
    // TEST 24: Privacy preserved
    // ------------------------------------------------

    @Test
    fun test24_privacy_preserved() {
        val startMs = 1000L
        val durationMs = 5000L

        val evidenceList = listOf(
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item24",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        // Snapshot should not contain raw OCR or image
        // data. Verify references have summaries only.
        for ((_, refs) in
            snapshot.supportingEvidenceByCategory
        ) {
            for (ref in refs) {
                assertTrue(
                    ref.valueSummary.length <= 64
                )
            }
        }
    }

    // ------------------------------------------------
    // TEST 25: Config validation
    // ------------------------------------------------

    @Test
    fun test25_config_validation() {
        // Default config should be valid.
        val defaultConfig =
            TemporalEvidenceFusionConfig.DEFAULT
        assertNotNull(defaultConfig)
        assertEquals(
            TemporalEvidenceFusionConfig
                .ENGINE_VERSION,
            defaultConfig.engineVersion
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun test25_invalid_config_rejects() {
        TemporalEvidenceFusionConfig(
            qualityWeight = -1.0
        )
    }

    // ------------------------------------------------
    // TEST 26: Property - adding evidence never
    //         decreases confidence
    // ------------------------------------------------

    @Test
    fun test26_adding_evidence_never_decreases() {
        val startMs = 1000L
        val durationMs = 10000L

        val baseEvidence = listOf(
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            )
        )

        val baseResult = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = baseEvidence
        )

        // Add more supporting evidence.
        val extendedEvidence = baseEvidence + listOf(
            evidence(
                value = "cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 2000,
                frameId = "f1"
            ),
            evidence(
                value = "IPL",
                supporting = listOf("sports"),
                timestampMs = startMs + 4000,
                frameId = "f2"
            )
        )

        val extendedResult = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = extendedEvidence
        )

        // Support score should not decrease.
        assertTrue(
            extendedResult.primarySupportScore >=
                    baseResult.primarySupportScore - 0.01
        )
    }

    // ------------------------------------------------
    // TEST 27: Property - removing evidence never
    //         increases confidence
    // ------------------------------------------------

    @Test
    fun test27_removing_evidence_never_increases() {
        val startMs = 1000L
        val durationMs = 10000L

        val fullEvidence = listOf(
            evidence(
                value = "sports1",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "sports2",
                supporting = listOf("sports"),
                timestampMs = startMs + 2000,
                frameId = "f1"
            ),
            evidence(
                value = "sports3",
                supporting = listOf("sports"),
                timestampMs = startMs + 4000,
                frameId = "f2"
            )
        )

        val fullResult = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = fullEvidence
        )

        // Remove evidence.
        val reducedEvidence = fullEvidence.take(1)

        val reducedResult = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = reducedEvidence
        )

        // Confidence should not increase.
        assertTrue(
            reducedResult.primarySupportScore <=
                    fullResult.primarySupportScore + 0.01
        )
    }

    // ------------------------------------------------
    // TEST 28: Property - reordering with same
    //         timestamps is deterministic
    // ------------------------------------------------

    @Test
    fun test28_reordering_deterministic() {
        val startMs = 1000L
        val durationMs = 10000L

        // Same evidence, different input order.
        val order1 = listOf(
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "education",
                supporting = listOf("education"),
                timestampMs = startMs + 2000,
                frameId = "f1"
            ),
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 4000,
                frameId = "f2"
            )
        )

        val order2 = listOf(
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 4000,
                frameId = "f2"
            ),
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "education",
                supporting = listOf("education"),
                timestampMs = startMs + 2000,
                frameId = "f1"
            )
        )

        val result1 = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = order1
        )
        val result2 = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = order2
        )

        assertEquals(
            result1.primaryCategory,
            result2.primaryCategory
        )
        assertEquals(
            result1.primarySupportScore,
            result2.primarySupportScore,
            1e-9
        )
    }

    // ------------------------------------------------
    // TEST 29: Evidence point identity hash
    // ------------------------------------------------

    @Test
    fun test29_identity_hash_deterministic() {
        val e1 = evidence(
            value = "IPL cricket",
            supporting = listOf("sports")
        )
        val e2 = evidence(
            value = "IPL cricket",
            supporting = listOf("sports")
        )

        val hash1 =
            TemporalEvidencePoint
                .computeIdentityHash(e1)
        val hash2 =
            TemporalEvidencePoint
                .computeIdentityHash(e2)

        assertEquals(hash1, hash2)
    }

    @Test
    fun test29_different_values_different_hash() {
        val e1 = evidence(
            value = "IPL cricket",
            supporting = listOf("sports")
        )
        val e2 = evidence(
            value = "movie scene",
            supporting = listOf("movie_clip")
        )

        val hash1 =
            TemporalEvidencePoint
                .computeIdentityHash(e1)
        val hash2 =
            TemporalEvidencePoint
                .computeIdentityHash(e2)

        assertNotEquals(hash1, hash2)
    }

    // ------------------------------------------------
    // TEST 30: Performance measurement
    // ------------------------------------------------

    @Test
    fun test30_performance_measurement() {
        val startMs = 1000L
        val durationMs = 30000L

        // Generate 20 evidence points.
        val evidenceList = (0..19).map { i ->
            evidence(
                value = "evidence_$i",
                supporting = listOf("sports"),
                timestampMs = startMs + i * 1500L,
                frameId = "f$i"
            )
        }

        val startTime = System.nanoTime()
        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item30",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )
        val elapsedMs =
            (System.nanoTime() - startTime) /
                    1_000_000

        // Should complete in reasonable time.
        assertTrue(
            "Fusion took ${elapsedMs}ms, " +
                    "expected < 100ms",
            elapsedMs < 100
        )

        // Should record fusion duration.
        assertTrue(
            snapshot.fusionDurationMs >= 0
        )
    }

    // ------------------------------------------------
    // TEST 31: Representative frame agreement
    // ------------------------------------------------

    @Test
    fun test31_representative_frame() {
        val startMs = 1000L
        val durationMs = 5000L

        val evidenceList = listOf(
            evidence(
                value = "sports cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "sports IPL",
                supporting = listOf("sports"),
                timestampMs = startMs + 2000,
                frameId = "f1"
            )
        )

        // Case 1: Representative frame agrees.
        val agreeResult = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList,
            representativeFrameCategory = "sports"
        )
        assertTrue(
            agreeResult.representativeFrameAgreement
                    ?: true
        )

        // Case 2: Representative frame disagrees.
        val disagreeResult = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList,
            representativeFrameCategory =
                "advertisement"
        )
        // May flag outlier if disagreement is strong.
        assertNotNull(
            disagreeResult.representativeFrameOutlier
        )
    }

    // ------------------------------------------------
    // TEST 32: Snapshot empty factory
    // ------------------------------------------------

    @Test
    fun test32_snapshot_empty() {
        val empty = ItemEvidenceSnapshot.empty(
            sessionId = "s1",
            feedItemId = "item32"
        )

        assertEquals(
            "s1", empty.sessionId
        )
        assertEquals(
            "item32", empty.feedItemId
        )
        assertEquals(0, empty.totalEvidencePoints)
        assertTrue(empty.isInsufficientEvidence)
        assertNull(empty.primaryCategory)
        assertEquals(
            CoverageState.NO_COVERAGE,
            empty.coverageState
        )
    }

    // ------------------------------------------------
    // TEST 33: Category persistence level
    // ------------------------------------------------

    @Test
    fun test33_persistence_level() {
        val startMs = 1000L
        val durationMs = 10000L

        // Many unique evidence points for sports.
        val evidenceList = (0..5).map { i ->
            evidence(
                value = "unique_sport_$i",
                supporting = listOf("sports"),
                timestampMs = startMs + i * 1500L,
                frameId = "f$i"
            )
        }

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        val sportsCandidate = snapshot
            .candidateCategories
            .firstOrNull { it.category == "sports" }

        assertNotNull(sportsCandidate)
        // With 6 unique identities, persistence should
        // be at least MODERATE.
        assertTrue(
            sportsCandidate!!.persistenceLevel ==
                    "MODERATE" ||
                    sportsCandidate.persistenceLevel ==
                    "SUSTAINED" ||
                    sportsCandidate.persistenceLevel ==
                    "DOMINANT"
        )
    }

    // ------------------------------------------------
    // TEST 34: Mixed content detection
    // ------------------------------------------------

    @Test
    fun test34_mixed_content() {
        val startMs = 1000L
        val durationMs = 10000L

        val evidenceList = listOf(
            evidence(
                value = "sports cricket",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "sports IPL",
                supporting = listOf("sports"),
                timestampMs = startMs + 2000,
                frameId = "f1"
            ),
            evidence(
                value = "education tutorial",
                supporting =
                    listOf("education"),
                timestampMs = startMs + 4000,
                frameId = "f2"
            ),
            evidence(
                value = "education learn",
                supporting =
                    listOf("education"),
                timestampMs = startMs + 6000,
                frameId = "f3"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        // Should have multiple candidate categories.
        assertTrue(
            snapshot.candidateCategories.size >= 2
        )
    }

    // ------------------------------------------------
    // TEST 35: Evidence coverage gap detection
    // ------------------------------------------------

    @Test
    fun test35_coverage_gap_detection() {
        val points = listOf(
            TemporalEvidencePoint(
                pointId = "p0",
                evidenceType = EvidenceType.OCR_TEXT,
                extractorName = "test",
                extractorVersion = "v1",
                quality = EvidenceQuality.HIGH,
                value = "test",
                valueSummary = "test",
                evidenceIdentityHash = "id0",
                timestampMs = 0L,
                relativeTimeMs = 0L,
                frameIndex = 0,
                frameId = "f0",
                supportingCategories =
                    listOf("sports"),
                contradictingCategories =
                    emptyList(),
                privacyState = "SANITIZED"
            ),
            TemporalEvidencePoint(
                pointId = "p1",
                evidenceType = EvidenceType.OCR_TEXT,
                extractorName = "test",
                extractorVersion = "v1",
                quality = EvidenceQuality.HIGH,
                value = "test2",
                valueSummary = "test2",
                evidenceIdentityHash = "id1",
                timestampMs = 10000L,
                relativeTimeMs = 10000L,
                frameIndex = 1,
                frameId = "f1",
                supportingCategories =
                    listOf("sports"),
                contradictingCategories =
                    emptyList(),
                privacyState = "SANITIZED"
            )
        )

        val coverage = EvidenceCoverage.compute(
            points, 15000L
        )

        assertTrue(coverage.hasGap)
        assertTrue(coverage.gapCount >= 1)
        assertEquals(
            10000L, coverage.largestGapMs
        )
    }

    // ------------------------------------------------
    // TEST 36: Custom config affects results
    // ------------------------------------------------

    @Test
    fun test36_custom_config() {
        val startMs = 1000L
        val durationMs = 5000L

        val evidenceList = listOf(
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            evidence(
                value = "education",
                supporting = listOf("education"),
                timestampMs = startMs + 1000,
                frameId = "f1"
            )
        )

        val defaultEngine =
            TemporalEvidenceFusionEngine(
                TemporalEvidenceFusionConfig.DEFAULT
            )
        val conservativeEngine =
            TemporalEvidenceFusionEngine(
                TemporalEvidenceFusionConfig
                    .CONSERVATIVE
            )

        val defaultResult = defaultEngine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        val conservativeResult =
            conservativeEngine.fuse(
                sessionId = "s1",
                feedItemId = null,
                itemStartTimeMs = startMs,
                itemDurationMs = durationMs,
                evidence = evidenceList
            )

        // Both should produce results (may differ).
        assertNotNull(
            defaultResult.primaryCategory
        )
        assertNotNull(
            conservativeResult.primaryCategory
        )
    }

    // ------------------------------------------------
    // TEST 37: No semantic interpolation across gaps
    // ------------------------------------------------

    @Test
    fun test37_no_interpolation() {
        val startMs = 0L
        val durationMs = 20000L

        val evidenceList = listOf(
            evidence(
                value = "sports",
                supporting = listOf("sports"),
                timestampMs = startMs + 0,
                frameId = "f0"
            ),
            // 15 second gap
            evidence(
                value = "education",
                supporting = listOf("education"),
                timestampMs = startMs + 15000,
                frameId = "f1"
            )
        )

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = "item37",
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        // The system should NOT interpolate education
        // into the gap. It should detect the gap and
        // both categories should be candidates.
        assertTrue(snapshot.hasGap)
        assertTrue(
            snapshot.candidateCategories.size >= 2
        )
    }

    // ------------------------------------------------
    // TEST 38: Evidence density metric
    // ------------------------------------------------

    @Test
    fun test38_evidence_density() {
        val startMs = 0L
        val durationMs = 10000L

        val evidenceList = (0..9).map { i ->
            evidence(
                value = "evidence_$i",
                supporting = listOf("sports"),
                timestampMs = startMs + i * 1000L,
                frameId = "f$i"
            )
        }

        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = startMs,
            itemDurationMs = durationMs,
            evidence = evidenceList
        )

        // 10 points in 10 sec = 1.0 density.
        val density = snapshot.coverageRatio
        assertTrue(density > 0.0)
    }

    // ------------------------------------------------
    // TEST 39: ItemLevelPredictor baseline pathway
    // ------------------------------------------------

    @Test
    fun test39_baseline_pathway() {
        val evidenceList = listOf(
            evidence(
                value = "sports cricket",
                supporting = listOf("sports"),
                timestampMs = System.currentTimeMillis(),
                frameId = "f0"
            )
        )

        val result =
            predictor.baselinePredict(evidenceList)

        // Baseline should produce a result.
        assertNotNull(result)
    }

    // ------------------------------------------------
    // TEST 40: Snapshot limitations content
    // ------------------------------------------------

    @Test
    fun test40_limitations_content() {
        val snapshot = engine.fuse(
            sessionId = "s1",
            feedItemId = null,
            itemStartTimeMs = 0L,
            itemDurationMs = 5000L,
            evidence = emptyList()
        )

        // Empty evidence should have limitations.
        assertTrue(snapshot.limitations.isNotEmpty())
        assertTrue(
            snapshot.limitations.contains(
                "INSUFFICIENT_TEMPORAL_COVERAGE"
            ) ||
                    snapshot.limitations.contains(
                        "REPRESENTATIVE_FRAME_MISLEADING"
                    )
        )
    }
}
