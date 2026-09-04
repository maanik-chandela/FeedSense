package com.example.feedsense.analysis.evaluation.comparative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-8.
 *
 * Statistical-layer tests: McNemar exact test, sample-size
 * guards, effect size.
 */
class PairedStatsTest {

    @Test
    fun `insufficient discordant pairs gives no p-value`() {
        val r = PairedStats.mcnemar(
            baselineOnlyCorrect = 1,
            eightBOnlyCorrect = 1,
            minimumDiscordant = 5
        )
        assertFalse(r.sufficient)
        assertNull(r.pValue)
        assertEquals(2, r.discordantPairs)
    }

    @Test
    fun `sufficient and clearly improved gives significant result`() {
        // c=10, b=2, n=12 -> p small.
        val r = PairedStats.mcnemar(2, 10, 5)
        assertTrue(r.sufficient)
        assertNotNull(r.pValue)
        assertTrue(r.pValue!! < 0.05)
        assertEquals("EIGHT_B_FAVORED",
            PairedStats.direction(2, 10, r.sufficient))
    }

    @Test
    fun `symmetric discordant gives not significant result`() {
        val r = PairedStats.mcnemar(3, 3, 5)
        assertTrue(r.sufficient)
        assertEquals(6, r.discordantPairs)
        assertEquals(1.0, r.pValue!!, 1e-9)
        assertEquals(
            "NO_DISCERNIBLE_DIRECTION",
            PairedStats.direction(3, 3, r.sufficient)
        )
    }

    @Test
    fun `zero discordant rule`() {
        // 0 discordant pairs; minimum is 0 allowed by config.
        val r = PairedStats.mcnemar(0, 0, 0)
        assertTrue(r.sufficient)
        assertNotNull(r.pValue)
    }

    @Test
    fun `effect size insufficient below minimum`() {
        val e = PairedStats.effectSize(
            baselineCorrect = 2,
            eightBCorrect = 2,
            totalEligible = 3,
            baselineOnlyCorrect = 0,
            eightBOnlyCorrect = 0,
            minimumForEffect = 10
        )
        assertFalse(e.sufficient)
        assertNull(e.difference)
        assertNull(e.oddsRatio)
    }

    @Test
    fun `effect size computed when sufficient`() {
        val e = PairedStats.effectSize(
            baselineCorrect = 4,
            eightBCorrect = 10,
            totalEligible = 10,
            baselineOnlyCorrect = 1,
            eightBOnlyCorrect = 7,
            minimumForEffect = 10
        )
        assertTrue(e.sufficient)
        assertEquals(0.4, e.baselineAccuracy!!, 1e-9)
        assertEquals(1.0, e.eightBAccuracy!!, 1e-9)
        assertEquals(0.6, e.difference!!, 1e-9)
        assertNotNull(e.differenceCi)
        assertNotNull(e.oddsRatio)
    }

    @Test
    fun `effect size with balanced discordant gives odds ratio one`() {
        val e = PairedStats.effectSize(
            baselineCorrect = 5,
            eightBCorrect = 5,
            totalEligible = 12,
            baselineOnlyCorrect = 2,
            eightBOnlyCorrect = 2,
            minimumForEffect = 10
        )
        assertEquals(1.0, e.oddsRatio!!, 1e-9)
    }

    @Test
    fun `two sided exact betwen symmetric and extreme differ`() {
        val symmetric = PairedStats.mcnemar(5, 5, 5)
        val extreme = PairedStats.mcnemar(0, 10, 5)
        assertTrue(extreme.pValue!! < symmetric.pValue!!)
    }
}
