package com.example.feedsense

import com.example.feedsense.analysis.ConfidenceLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfidenceLevelTest {

    @Test
    fun highBand_mapsToHigh() {

        assertEquals(
            ConfidenceLevel.HIGH,
            ConfidenceLevel.from(0.8)
        )

        assertEquals(
            ConfidenceLevel.HIGH,
            ConfidenceLevel.from(0.95)
        )
    }

    @Test
    fun mediumBand_mapsToMedium() {

        assertEquals(
            ConfidenceLevel.MEDIUM,
            ConfidenceLevel.from(0.6)
        )

        assertEquals(
            ConfidenceLevel.MEDIUM,
            ConfidenceLevel.from(0.79)
        )
    }

    @Test
    fun lowBand_mapsToLow() {

        assertEquals(
            ConfidenceLevel.LOW,
            ConfidenceLevel.from(0.59)
        )

        assertEquals(
            ConfidenceLevel.LOW,
            ConfidenceLevel.from(0.0)
        )
    }

    @Test
    fun missingConfidence_mapsToLow() {

        assertEquals(
            ConfidenceLevel.LOW,
            ConfidenceLevel.from(null)
        )
    }
}
