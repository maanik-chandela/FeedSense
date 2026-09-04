package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

// --------------------------------
// CLOUD USAGE STATE
// --------------------------------
//
// Milestone 7Q.
//
// Single-row running total behind the budget gate.
// Keys are calendar keys (e.g. "2026-08" for a month,
// "2026-08-16" for a day) so the counters reset
// automatically when the period rolls over.
//
// Every column is a number - no content, no keys.
//

@Entity(tableName = "cloud_usage_state")
data class CloudUsageState(

    @PrimaryKey
    val id: String = STATE_ID,

    val monthKey: String,

    val dayKey: String,

    val monthlyEstimatedRupees: Double = 0.0,

    val dailyEstimatedRupees: Double = 0.0,

    val requestCount: Int = 0,

    val lastUpdated: LocalDateTime = LocalDateTime.now()
) {

    companion object {

        const val STATE_ID = "state"
    }
}
