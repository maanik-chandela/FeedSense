package com.example.feedsense.repository

import com.example.feedsense.analysis.CloudBudgetGate
import com.example.feedsense.dao.CloudUsageDao
import com.example.feedsense.model.CloudUsageRecord
import com.example.feedsense.model.CloudUsageState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// --------------------------------
// CLOUD USAGE MANAGER
// --------------------------------
//
// Milestone 7Q.
//
// Single point through which every cloud AI request is
// gated and recorded:
//
//   1. The calendar keys (month/day) roll over
//      automatically, resetting the counters.
//   2. The CloudBudgetGate decides allow/block.
//   3. EVERY attempt - allowed or blocked - is written
//      to cloud_usage_records with its estimated cost.
//   4. Only allowed requests are counted against the
//      budget.
//
// The in-memory seenFiles set stops the same content
// from being sent to the cloud twice within one app
// run (retry/rebuild protection). Nothing here ever
// touches the network.
//

class CloudUsageRepository(
    private val dao: CloudUsageDao,
    private val gate: CloudBudgetGate = CloudBudgetGate()
) {

    private val seenFiles =
        mutableSetOf<String>()

    /*
     * Asks the gate for permission AND records the
     * attempt. Only allowed attempts advance the budget.
     * Returns the decision so the caller can react.
     */
    suspend fun authorizeAndRecord(
        sessionId: String?,
        frameId: String?,
        feedItemId: String?,
        filePath: String?,
        requestType: String,
        modelVersion: String?
    ): CloudBudgetGate.BudgetDecision {

        val today =
            LocalDate.now()

        val monthKey =
            today.format(MONTH_KEY_FORMAT)

        val dayKey =
            today.format(DAY_KEY_FORMAT)

        val state =
            resetForNewPeriod(
                dao.getState(),
                monthKey,
                dayKey
            )

        val isDuplicate =
            filePath != null &&
                    !seenFiles.add(filePath)

        val decision =
            gate.decide(
                monthlyEstimatedRupees =
                    state.monthlyEstimatedRupees,
                dailyEstimatedRupees =
                    state.dailyEstimatedRupees,
                requestCountToday =
                    state.requestCount,
                isDuplicate = isDuplicate
            )

        dao.insert(
            CloudUsageRecord(
                sessionId = sessionId,
                frameId = frameId,
                feedItemId = feedItemId,
                filePath = filePath,
                requestedAt = LocalDateTime.now(),
                requestType = requestType,
                estimatedCostRupees =
                    if (decision.allowed) {
                        CloudBudgetGate
                            .ESTIMATED_COST_PER_REQUEST_RUPEES
                    } else {
                        0.0
                    },
                allowed = decision.allowed,
                reason = decision.reason,
                modelVersion = modelVersion
            )
        )

        if (decision.allowed) {

            dao.upsertState(
                state.copy(
                    monthKey = monthKey,
                    dayKey = dayKey,
                    monthlyEstimatedRupees =
                        state.monthlyEstimatedRupees +
                                CloudBudgetGate
                                .ESTIMATED_COST_PER_REQUEST_RUPEES,
                    dailyEstimatedRupees =
                        state.dailyEstimatedRupees +
                                CloudBudgetGate
                                .ESTIMATED_COST_PER_REQUEST_RUPEES,
                    requestCount = state.requestCount + 1,
                    lastUpdated = LocalDateTime.now()
                )
            )
        }

        return decision
    }

    /*
     * Rolls the counters over when the calendar period
     * changes. Uses the stored keys, so a stale process
     * that wakes up a month later resets correctly.
     */
    private suspend fun resetForNewPeriod(
        state: CloudUsageState?,
        monthKey: String,
        dayKey: String
    ): CloudUsageState {

        if (state == null) {

            val fresh =
                CloudUsageState(
                    id = CloudUsageState.STATE_ID,
                    monthKey = monthKey,
                    dayKey = dayKey
                )

            dao.upsertState(fresh)

            return fresh
        }

        if (
            state.monthKey != monthKey ||
            state.dayKey != dayKey
        ) {

            val reset =
                state.copy(
                    monthKey = monthKey,
                    dayKey = dayKey,
                    monthlyEstimatedRupees = 0.0,
                    dailyEstimatedRupees = 0.0,
                    requestCount = 0,
                    lastUpdated = LocalDateTime.now()
                )

            dao.upsertState(reset)

            return reset
        }

        return state
    }

    suspend fun getState(): CloudUsageState? {
        return dao.getState()
    }

    fun getRecentRecords(
        limit: Int
    ): kotlinx.coroutines.flow.Flow<List<CloudUsageRecord>> {
        return dao.getRecentRecords(limit)
    }

    suspend fun getTotalEstimatedCost(): Double {
        return dao.getTotalEstimatedCost()
    }

    companion object {

        private val MONTH_KEY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM")

        private val DAY_KEY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
