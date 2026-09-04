package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

// --------------------------------
// CLOUD USAGE RECORD
// --------------------------------
//
// Milestone 7Q.
//
// One row per cloud request ATTEMPT - allowed or
// blocked. This is the audit trail behind the budget
// gate: every request is logged with its estimated
// cost and the gate's decision, so nobody can silently
// spend money and every rupee is traceable.
//
// Nothing in this table ever contains image content or
// API keys - only metadata and costs.
//

@Entity(tableName = "cloud_usage_records")
data class CloudUsageRecord(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val sessionId: String? = null,

    val frameId: String? = null,

    val feedItemId: String? = null,

    val filePath: String? = null,

    val requestedAt: LocalDateTime = LocalDateTime.now(),

    val requestType: String = REQUEST_TYPE_CLASSIFICATION,

    val estimatedCostRupees: Double = 0.0,

    val allowed: Boolean = false,

    val reason: String = "",

    val modelVersion: String? = null
) {

    companion object {

        const val REQUEST_TYPE_CLASSIFICATION =
            "classification"

        const val REQUEST_TYPE_EVALUATION =
            "evaluation"
    }
}
