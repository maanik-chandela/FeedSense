package com.example.feedsense.analysis.evidence.decision

/*
 * Milestone 8B-7.
 *
 * Decision observation text.
 *
 * Produces a concise, research-readable observation
 * sentence from an evidence-aware decision. This is a
 * standalone, side-effect-free formatter so the decision
 * layer can feed the observation subsystem without
 * coupling to SessionRepository.
 *
 * Output style mirrors the existing auto-observation
 * vocabulary ("AI: ...") while surfacing the decision
 * state, uncertainty, and evidence awareness explicitly.
 *
 * Important:
 *   - Honest: an abstention is stated, not hidden.
 *   - No fabricated certainty: supportScore is described
 *     as evidence, not probability.
 */
object DecisionObservationText {

    /**
     * Builds a human-readable observation for an
     * evidence-aware decision.
     */
    fun format(
        decision: ItemPredictionResult
    ): String {

        val category =
            decision.primaryCategory ?: "unclassified"

        val stateTag =
            "[${decision.decisionState.label}," +
            "${decision.uncertainty.label}]"

        val evidencePart =
            if (decision.primaryCategory == null) {
                " (no decided category)"
            } else {
                " (evidence support %.3f, margin %.3f)"
                    .format(
                        decision.supportScore,
                        decision.decisionMargin
                    )
            }

        val coveragePart =
            " coverage=%.2f".format(
                decision.coverageRatio
            )

        val reasonTail =
            if (decision.uncertaintyReasons.isEmpty()) {
                ""
            } else {
                " reasons: " +
                    decision.uncertaintyReasons
                        .joinToString(", ")
            }

        return when (decision.decisionState) {
            DecisionState.DECIDED ->
                "AI: Decided $category " +
                    "$stateTag$evidencePart" +
                    "$coveragePart$reasonTail"

            DecisionState.AMBIGUOUS ->
                "AI: Ambiguous between " +
                    categoryLabel(decision) +
                    " $stateTag$evidencePart$coveragePart"

            DecisionState.INSUFFICIENT_EVIDENCE ->
                "AI: Insufficient evidence to classify" +
                    " $stateTag$coveragePart$reasonTail"

            DecisionState.UNKNOWN ->
                "AI: Evidence present but unmappable" +
                    " to a category $stateTag$coveragePart"
        }
    }

    private fun categoryLabel(
        decision: ItemPredictionResult
    ): String {
        val names = decision.candidateCategories
            .take(3)
            .map { it.category }
        return if (names.isEmpty()) {
            "unknown categories"
        } else {
            names.joinToString("/")
        }
    }
}
