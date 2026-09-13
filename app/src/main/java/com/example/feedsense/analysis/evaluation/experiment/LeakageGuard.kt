package com.example.feedsense.analysis.evaluation.experiment

import com.example.feedsense.model.EvaluationItem

/**
 * Milestone 8B-9.
 *
 * Leakage protection.
 *
 * A controlled experiment must never evaluate on observations that
 * were also used to train or validate the systems under test,
 * otherwise reported performance is contaminated (train-on-test
 * leakage) and any conclusion is invalid.
 *
 * The guard compares the experiment's selected population against a
 * nominated training / validation dataset cohort
 * ([ExperimentDefinition.trainingDatasetVersion]) and reports every
 * overlapping item id. It is a pure, read-only check; it does not
 * itself modify any dataset.
 */
object LeakageGuard {

    /** Result of the leakage check. */
    data class LeakageReport(
        val checked: Boolean,
        val checkedAgainstVersion: String?,
        val overlapItemIds: Set<String>,
        val leaked: Boolean,
        val note: String
    ) {
        /** Overlap ratio over the experiment population. */
        fun overlapCount(
            experimentItemIds: Set<String>
        ): Int = overlapItemIds.count { it in experimentItemIds }
    }

    /**
     * Checks for overlap between the experiment population and a
     * nominated training/validation dataset.
     *
     * @param experimentItems the items in the experiment population
     * @param trainingVersion the frozen training/validation dataset
     *                        version, or null to skip the check
     * @param trainingItems   the items belonging to the training
     *                        dataset (fetched by the caller), or null
     *                        when not provided
     */
    fun check(
        experimentItems: List<EvaluationItem>,
        trainingVersion: String?,
        trainingItems: List<EvaluationItem>? = null
    ): LeakageReport {
        if (trainingVersion == null) {
            return LeakageReport(
                checked = false,
                checkedAgainstVersion = null,
                overlapItemIds = emptySet(),
                leaked = false,
                note = "No training/validation dataset nominated; leakage check skipped."
            )
        }
        val expIds = experimentItems.map { it.id }.toSet()
        val trainIds = trainingItems.orEmpty()
            .filter { it.datasetVersion == trainingVersion }
            .map { it.id }
            .toSet()
        val overlap = expIds.intersect(trainIds)

        return LeakageReport(
            checked = true,
            checkedAgainstVersion = trainingVersion,
            overlapItemIds = overlap,
            leaked = overlap.isNotEmpty(),
            note = if (overlap.isEmpty()) {
                "No overlap detected between the experiment population " +
                    "and training/validation dataset '$trainingVersion'."
            } else {
                "LEAKAGE DETECTED: ${overlap.size} observation(s) in the " +
                    "experiment population also belong to " +
                    "training/validation dataset '$trainingVersion'. " +
                    "The experiment must not be run as-is."
            }
        )
    }
}
