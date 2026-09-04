package com.example.feedsense.analysis

// --------------------------------
// CLOUD BUDGET GATE
// --------------------------------
//
// Milestone 7Q.
//
// The cloud AI is the TEACHER, not the default path:
// it exists to produce reference results that a human
// later validates. Every cloud call costs real money,
// so a pure, deterministic gate decides whether one
// more request is affordable BEFORE the request is
// ever made.
//
// Budget model (rupees, estimated):
//
//   daily budget     ₹2/day
//   monthly target   ₹30/month (normal operation)
//   ₹30..₹35         warning band  - still allowed
//   ₹35..₹40         aggressive    - allowed only
//                                    under the warning
//   ₹40..₹50         aggressive cutoff - blocked
//   ₹50+             safety ceiling - hard shutdown
//
// Hard rules:
//
//   - never above the ₹50 ceiling
//   - never above the ₹40 aggressive cutoff
//   - never more than the daily budget in one day
//   - never more than MAX_REQUESTS_PER_DAY requests
//   - never a duplicate request (same content again)
//
// The gate never calls the cloud itself and never needs
// a network - it is pure so it can be unit-tested.
//

class CloudBudgetGate {

    data class BudgetDecision(
        val allowed: Boolean,
        val reason: String
    )

    fun decide(
        monthlyEstimatedRupees: Double,
        dailyEstimatedRupees: Double,
        requestCountToday: Int,
        isDuplicate: Boolean
    ): BudgetDecision {

        /*
         * Priority: the safety ceiling is the absolute
         * top rule - no request ever happens above it.
         * The duplicate rule prevents the same content
         * from being sent twice within a run.
         */
        if (
            monthlyEstimatedRupees >=
            MONTHLY_CEILING_RUPEES
        ) {
            return BudgetDecision(
                allowed = false,
                reason = REASON_CEILING
            )
        }

        if (
            monthlyEstimatedRupees >=
            MONTHLY_AGGRESSIVE_CUTOFF_RUPEES
        ) {
            return BudgetDecision(
                allowed = false,
                reason = REASON_AGGRESSIVE_CUTOFF
            )
        }

        if (
            dailyEstimatedRupees >=
            DAILY_BUDGET_RUPEES
        ) {
            return BudgetDecision(
                allowed = false,
                reason = REASON_DAILY_BUDGET
            )
        }

        if (
            requestCountToday >=
            MAX_REQUESTS_PER_DAY
        ) {
            return BudgetDecision(
                allowed = false,
                reason = REASON_DAILY_REQUEST_LIMIT
            )
        }

        if (isDuplicate) {
            return BudgetDecision(
                allowed = false,
                reason = REASON_DUPLICATE
            )
        }

        /*
         * Warning band: ₹30 (target) .. ₹35 (warning).
         * The budget is healthy but getting close, so
         * the request is allowed and flagged.
         */
        if (
            monthlyEstimatedRupees >=
            MONTHLY_TARGET_RUPEES
        ) {
            return BudgetDecision(
                allowed = true,
                reason = REASON_WARNING
            )
        }

        return BudgetDecision(
            allowed = true,
            reason = REASON_OK
        )
    }

    companion object {

        /*
         * Estimated cost of one cloud classification, in
         * rupees. Deliberately conservative and recorded
         * with every request so the totals are auditable.
         */
        const val ESTIMATED_COST_PER_REQUEST_RUPEES =
            0.5

        const val DAILY_BUDGET_RUPEES = 2.0

        const val MONTHLY_TARGET_RUPEES = 30.0

        const val MONTHLY_WARNING_RUPEES = 35.0

        const val MONTHLY_AGGRESSIVE_CUTOFF_RUPEES =
            40.0

        const val MONTHLY_CEILING_RUPEES = 50.0

        const val MAX_REQUESTS_PER_DAY = 20

        const val REASON_OK =
            "within-budget"

        const val REASON_WARNING =
            "monthly-warning"

        const val REASON_DAILY_BUDGET =
            "daily-budget-exceeded"

        const val REASON_DAILY_REQUEST_LIMIT =
            "daily-request-limit"

        const val REASON_AGGRESSIVE_CUTOFF =
            "aggressive-cutoff"

        const val REASON_CEILING =
            "safety-ceiling"

        const val REASON_DUPLICATE =
            "duplicate-request"
    }
}
