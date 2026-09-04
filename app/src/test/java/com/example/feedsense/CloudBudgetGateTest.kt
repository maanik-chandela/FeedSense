package com.example.feedsense

import com.example.feedsense.analysis.CloudBudgetGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBudgetGateTest {

    private val gate =
        CloudBudgetGate()

    private fun decide(
        monthly: Double = 0.0,
        daily: Double = 0.0,
        requests: Int = 0,
        duplicate: Boolean = false
    ): CloudBudgetGate.BudgetDecision {

        return gate.decide(
            monthlyEstimatedRupees = monthly,
            dailyEstimatedRupees = daily,
            requestCountToday = requests,
            isDuplicate = duplicate
        )
    }

    @Test
    fun withinBudgetIsAllowed() {

        val decision =
            decide(monthly = 5.0, daily = 0.5, requests = 1)

        assertTrue(decision.allowed)
        assertEquals(
            CloudBudgetGate.REASON_OK,
            decision.reason
        )
    }

    @Test
    fun monthlyWarningIsStillAllowed() {

        val decision =
            decide(monthly = 33.0, daily = 0.5, requests = 1)

        assertTrue(decision.allowed)
        assertEquals(
            CloudBudgetGate.REASON_WARNING,
            decision.reason
        )
    }

    @Test
    fun aggressiveCutoffBlocks() {

        val decision =
            decide(monthly = 42.0, daily = 0.5, requests = 1)

        assertFalse(decision.allowed)
        assertEquals(
            CloudBudgetGate.REASON_AGGRESSIVE_CUTOFF,
            decision.reason
        )
    }

    @Test
    fun safetyCeilingHardShutsDown() {

        val decision =
            decide(monthly = 52.0, daily = 0.5, requests = 1)

        assertFalse(decision.allowed)
        assertEquals(
            CloudBudgetGate.REASON_CEILING,
            decision.reason
        )
    }

    @Test
    fun dailyBudgetBlocks() {

        val decision =
            decide(monthly = 5.0, daily = 2.0, requests = 1)

        assertFalse(decision.allowed)
        assertEquals(
            CloudBudgetGate.REASON_DAILY_BUDGET,
            decision.reason
        )
    }

    @Test
    fun dailyRequestLimitBlocks() {

        val decision =
            decide(
                monthly = 5.0,
                daily = 1.0,
                requests =
                    CloudBudgetGate.MAX_REQUESTS_PER_DAY
            )

        assertFalse(decision.allowed)
        assertEquals(
            CloudBudgetGate.REASON_DAILY_REQUEST_LIMIT,
            decision.reason
        )
    }

    @Test
    fun duplicateRequestIsBlocked() {

        val decision =
            decide(monthly = 0.0, daily = 0.0, requests = 0)

        assertTrue(decision.allowed)

        val duplicate =
            decide(monthly = 0.0, daily = 0.0, requests = 0, duplicate = true)

        assertFalse(duplicate.allowed)
        assertEquals(
            CloudBudgetGate.REASON_DUPLICATE,
            duplicate.reason
        )
    }

    @Test
    fun ceilingTakesPriorityOverEverything() {

        // Even a duplicate is reported as ceiling first.
        val decision =
            decide(
                monthly = 60.0,
                daily = 10.0,
                requests = 999,
                duplicate = true
            )

        assertFalse(decision.allowed)
        assertEquals(
            CloudBudgetGate.REASON_CEILING,
            decision.reason
        )
    }

    @Test
    fun aggressiveCutoffTakesPriorityOverDuplicate() {

        val decision =
            decide(
                monthly = 45.0,
                daily = 0.5,
                requests = 1,
                duplicate = true
            )

        assertFalse(decision.allowed)
        assertEquals(
            CloudBudgetGate.REASON_AGGRESSIVE_CUTOFF,
            decision.reason
        )
    }

    @Test
    fun zeroStateIsAllowed() {

        val decision =
            decide()

        assertTrue(decision.allowed)
    }
}
