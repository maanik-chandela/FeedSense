package com.example.feedsense

import com.example.feedsense.analysis.ModelRateCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelRateCalculatorTest {

    @Test
    fun cloudFallbackRateComputesShare() {

        assertEquals(
            10.0,
            ModelRateCalculator.cloudFallbackRate(
                cloudFallback = 1,
                totalClassifications = 10
            ),
            0.001
        )
    }

    @Test
    fun localAcceptanceRateComputesShare() {

        assertEquals(
            80.0,
            ModelRateCalculator.localAcceptanceRate(
                highConfidence = 8,
                totalClassifications = 10
            ),
            0.001
        )
    }

    @Test
    fun reviewRateComputesShare() {

        assertEquals(
            30.0,
            ModelRateCalculator.reviewRate(
                needsReview = 3,
                totalClassifications = 10
            ),
            0.001
        )
    }

    @Test
    fun correctionRateUsesCorrectionsAndConfirmations() {

        assertEquals(
            40.0,
            ModelRateCalculator.correctionRate(
                corrections = 2,
                confirmations = 3
            ),
            0.001
        )
    }

    @Test
    fun emptyDatasetIsZeroNotDivisionByZero() {

        assertEquals(
            0.0,
            ModelRateCalculator.cloudFallbackRate(0, 0),
            0.001
        )

        assertEquals(
            0.0,
            ModelRateCalculator.localAcceptanceRate(0, 0),
            0.001
        )

        assertEquals(
            0.0,
            ModelRateCalculator.reviewRate(0, 0),
            0.001
        )

        assertEquals(
            0.0,
            ModelRateCalculator.correctionRate(0, 0),
            0.001
        )
    }

    @Test
    fun negativeInputsAreClampedToZero() {

        assertEquals(
            0.0,
            ModelRateCalculator.correctionRate(-1, 0),
            0.001
        )
    }
}
