package com.example.feedsense

import com.example.feedsense.analysis.CloudBudgetGate
import com.example.feedsense.dao.CloudUsageDao
import com.example.feedsense.model.CloudUsageRecord
import com.example.feedsense.model.CloudUsageState
import com.example.feedsense.repository.CloudUsageRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudUsageRepositoryTest {

    private class FakeCloudUsageDao : CloudUsageDao {

        var state: CloudUsageState? = null
        val records = mutableListOf<CloudUsageRecord>()

        override suspend fun insert(record: CloudUsageRecord) {
            records += record
        }

        override suspend fun upsertState(state: CloudUsageState) {
            this.state = state
        }

        override suspend fun getState(): CloudUsageState? = state

        override fun getRecentRecords(limit: Int): Flow<List<CloudUsageRecord>> =
            flowOf(records.takeLast(limit))

        override suspend fun getAllowedRecords(): List<CloudUsageRecord> =
            records.filter { it.allowed }

        override suspend fun getAllowedRequestCount(): Int =
            records.count { it.allowed }

        override suspend fun getTotalEstimatedCost(): Double =
            records.filter { it.allowed }.sumOf { it.estimatedCostRupees }
    }

    private val dao = FakeCloudUsageDao()
    private val repository = CloudUsageRepository(dao)

    @Test
    fun allowedRequestIsRecordedAndCounted() {

        val decision =
            runBlocking {
                repository.authorizeAndRecord(
                    sessionId = "s",
                    frameId = "f",
                    feedItemId = null,
                    filePath = "/tmp/1.png",
                    requestType = "classification",
                    modelVersion = "local-v5.1"
                )
            }

        assertTrue(decision.allowed)
        assertEquals(1, dao.records.size)
        assertTrue(dao.records[0].allowed)
        assertEquals(
            CloudBudgetGate.ESTIMATED_COST_PER_REQUEST_RUPEES,
            dao.records[0].estimatedCostRupees,
            0.001
        )
        assertEquals(
            CloudBudgetGate.ESTIMATED_COST_PER_REQUEST_RUPEES,
            dao.state!!.monthlyEstimatedRupees,
            0.001
        )
        assertEquals(1, dao.state!!.requestCount)
    }

    @Test
    fun duplicateFileIsBlockedWithoutCost() {

        runBlocking {
            repository.authorizeAndRecord(
                sessionId = "s", frameId = "f", feedItemId = null,
                filePath = "/tmp/1.png", requestType = "classification",
                modelVersion = null
            )
        }

        val decision =
            runBlocking {
                repository.authorizeAndRecord(
                    sessionId = "s", frameId = "f", feedItemId = null,
                    filePath = "/tmp/1.png", requestType = "classification",
                    modelVersion = null
                )
            }

        assertFalse(decision.allowed)
        assertEquals(
            CloudBudgetGate.REASON_DUPLICATE,
            decision.reason
        )
        assertEquals(2, dao.records.size)
        assertFalse(dao.records[1].allowed)
        assertEquals(0.0, dao.records[1].estimatedCostRupees, 0.001)
        assertEquals(1, dao.state!!.requestCount)
    }

    @Test
    fun blockedAttemptIsStillRecorded() {

        val blocked =
            CloudUsageState(
                id = CloudUsageState.STATE_ID,
                monthKey = todayMonth(),
                dayKey = todayDay(),
                monthlyEstimatedRupees = 60.0
            )

        dao.state = blocked

        val decision =
            runBlocking {
                repository.authorizeAndRecord(
                    sessionId = null, frameId = null, feedItemId = null,
                    filePath = "/tmp/2.png", requestType = "classification",
                    modelVersion = null
                )
            }

        assertFalse(decision.allowed)
        assertEquals(1, dao.records.size)
        assertEquals(
            CloudBudgetGate.REASON_CEILING,
            dao.records[0].reason
        )
        assertEquals(60.0, dao.state!!.monthlyEstimatedRupees, 0.001)
    }

    @Test
    fun staleMonthKeyRollsTheCountersOver() {

        val stale =
            CloudUsageState(
                id = CloudUsageState.STATE_ID,
                monthKey = "1999-01",
                dayKey = "1999-01-01",
                monthlyEstimatedRupees = 29.5,
                dailyEstimatedRupees = 1.5,
                requestCount = 5
            )

        dao.state = stale

        val decision =
            runBlocking {
                repository.authorizeAndRecord(
                    sessionId = null, frameId = null, feedItemId = null,
                    filePath = "/tmp/3.png", requestType = "classification",
                    modelVersion = null
                )
            }

        assertTrue(decision.allowed)
        assertEquals(todayMonth(), dao.state!!.monthKey)
        assertEquals(
            CloudBudgetGate.ESTIMATED_COST_PER_REQUEST_RUPEES,
            dao.state!!.monthlyEstimatedRupees,
            0.001
        )
        assertEquals(1, dao.state!!.requestCount)
    }

    @Test
    fun noStateInitializesFreshCounters() {

        assertTrue(dao.state == null)

        val decision =
            runBlocking {
                repository.authorizeAndRecord(
                    sessionId = null, frameId = null, feedItemId = null,
                    filePath = "/tmp/4.png", requestType = "classification",
                    modelVersion = null
                )
            }

        assertTrue(decision.allowed)
        assertEquals(todayMonth(), dao.state!!.monthKey)
        assertEquals(todayDay(), dao.state!!.dayKey)
        assertEquals(1, dao.state!!.requestCount)
    }

    private fun todayMonth(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

    private fun todayDay(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}
