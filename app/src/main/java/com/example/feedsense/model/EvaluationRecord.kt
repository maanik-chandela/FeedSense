package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.feedsense.analysis.CategoryCatalog
import java.time.LocalDateTime
import java.util.UUID

// --------------------------------
// EVALUATION RECORD (Milestone 8A-1)
// --------------------------------
//
// The RESULT of comparing an AI prediction against a human
// ground truth - the answer to "was the AI correct?".
//
// Kept separate from both the prediction (AiPredictionRecord)
// and the truth (GroundTruth) so that:
//
//   - the prediction stays immutable evidence
//   - the truth stays independent evidence
//   - the verdict is derivable / recomputable and carries
//     its own provenance (which model, which dataset, which
//     truth it was computed against)
//
// For multi-label content the primary category comparison
// is reported alongside a secondaryMatch flag, so a mixed
// item whose primary label was "wrong" but whose secondary
// label was correct is recorded, not lost.
//
// Duration inaccuracy, segmentation boundaries and
// interaction-signal errors are captured as explicit
// verdict/error fields so the core research questions can
// be answered without re-parsing frames.

@Entity(
    tableName = "evaluation_results",
    indices = [
        Index(
            name = "idx_evaluation_results_evaluation_item",
            value = ["evaluationItemId"]
        ),
        Index(
            name = "idx_evaluation_results_ground_truth",
            value = ["groundTruthId"]
        ),
        Index(
            name = "idx_evaluation_results_verdict",
            value = ["verdict"]
        ),
        Index(
            name = "idx_evaluation_results_model_dataset",
            value = ["modelVersion", "datasetVersion"]
        )
    ]
)
data class EvaluationRecord(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val evaluationItemId: String,

    val groundTruthId: String,

    val aiPredictionId: String,

    val modelVersion: String? = null,

    val datasetVersion: String? = null,

    /*
     * Evaluation methodology version. Bump when the
     * comparison rules change so old verdicts are never
     * silently recomputed with new semantics.
     */
    val evaluationMethodVersion: String = "1.0.0",

    // --------------------------------
    // SEGMENTATION / COMPARABILITY GUARD
    // --------------------------------
    //
    // True only if the AI prediction timing and the human
    // truth refer to the same content piece. If a human
    // segmented an item differently than the AI, direct
    // per-field comparison is not meaningful.

    val comparable: Boolean = true,

    val segmentationError: String? = null,

    // --------------------------------
    // OVERALL / PER-FIELD VERDICTS
    // --------------------------------

    /*
     * CORRECT / INCORRECT / PARTIAL / UNKNOWN / UNCOMPARABLE
     * for the primary category comparison.
     */
    val verdict: String = VERDICT_UNKNOWN,

    val categoryCorrect: Boolean? = null,

    val secondaryMatch: Boolean? = null,

    val topicAgreement: Boolean? = null,

    val toneAgreement: Boolean? = null,

    val platformAgreement: Boolean? = null,

    val contentTypeAgreement: Boolean? = null,

    /*
     * True when the human-recorded duration differs from
     * the AI-estimated duration by more than the frozen
     * tolerance.
     */
    val durationInaccurate: Boolean? = null,

    val durationErrorSeconds: Int? = null,

    val skippedAgreement: Boolean? = null,

    val interactionSignalsDisagreement: Int? = null,

    val recordedAt: LocalDateTime = LocalDateTime.now()
) {

    companion object {

        const val VERDICT_CORRECT =
            "CORRECT"

        const val VERDICT_INCORRECT =
            "INCORRECT"

        const val VERDICT_PARTIAL =
            "PARTIAL"

        const val VERDICT_UNKNOWN =
            "UNKNOWN"

        const val VERDICT_UNCOMPARABLE =
            "UNCOMPARABLE"

        /*
         * Frozen duration tolerance (seconds) used to decide
         * whether a duration discrepancy is material.
         */
        const val DURATION_TOLERANCE_SECONDS = 5

        /*
         * Pure, deterministic comparison of an immutable AI
         * prediction snapshot against human ground truth.
         * Kept as a companion builder so the verdict logic is
         * unit-testable without Room and reproducibly
         * recomputable from the stored inputs.
         */
        fun fromComponents(
            prediction: AiPredictionRecord,
            truth: GroundTruth
        ): EvaluationRecord {

            val predCat = CategoryCatalog.normalize(
                prediction.category
            )
            val truthCat = CategoryCatalog.normalize(
                truth.category
            )

            val categoryMatch =
                predCat != null && predCat == truthCat

            val secondaryMatch =
                matchSecondary(
                    predicted =
                        prediction
                            .secondaryCategories,
                    truthPrimary = truthCat,
                    truthSecondary =
                        truth
                            .secondaryCategories,
                    predictedPrimary = predCat
                )

            val topicAgreement =
                optionalTextAgreement(
                    prediction.topic,
                    truth.topic
                )

            val toneAgreement =
                optionalTextAgreement(
                    prediction.tone,
                    truth.tone
                )

            val platformAgreement =
                optionalTextAgreement(
                    prediction.platform,
                    truth.platform
                )

            val contentTypeAgreement =
                optionalTextAgreement(
                    prediction.contentType,
                    truth.contentType
                )

            val truthDuration =
                truth.durationSeconds

            val durationErrorSeconds =
                if (truthDuration != null) {
                    prediction.durationSeconds -
                        truthDuration
                } else {
                    null
                }

            val durationInaccurate =
                durationErrorSeconds?.let {
                    kotlin.math.abs(it) >
                        DURATION_TOLERANCE_SECONDS
                }

            val skippedAgreement =
                truth.skipped?.let {
                    it == prediction.skipped
                }

            val interactionDisagreement =
                truth
                    .interactionSignals
                    .toSet()
                    .size -
                    truth
                        .interactionSignals
                        .filter { signal ->
                            signal in
                                prediction
                                    .interactionSignals
                        }
                        .toSet()
                        .size

            val verdict = when {
                truth.ambiguity ==
                    GroundTruth.AMBIGUITY_UNKNOWN ->
                    VERDICT_UNKNOWN

                !categoryMatch &&
                    truth.ambiguity ==
                    GroundTruth.AMBIGUITY_MIXED &&
                    secondaryMatch -> VERDICT_PARTIAL

                categoryMatch -> VERDICT_CORRECT

                else -> VERDICT_INCORRECT
            }

            return EvaluationRecord(
                evaluationItemId =
                    prediction.evaluationItemId,
                groundTruthId = truth.id,
                aiPredictionId = prediction.id,
                modelVersion = prediction.modelVersion,
                verdict = verdict,
                categoryCorrect = categoryMatch,
                secondaryMatch = secondaryMatch,
                topicAgreement = topicAgreement,
                toneAgreement = toneAgreement,
                platformAgreement = platformAgreement,
                contentTypeAgreement = contentTypeAgreement,
                durationInaccurate = durationInaccurate,
                durationErrorSeconds = durationErrorSeconds,
                skippedAgreement = skippedAgreement,
                interactionSignalsDisagreement =
                    interactionDisagreement
            )
        }

        /*
         * True when any predicted label (primary or
         * secondary) appears among the ground-truth labels
         * (primary or secondary), after normalization.
         * Used so mixed content where the AI's primary was
         * wrong but a secondary was right is credited.
         */
        private fun matchSecondary(
            predicted: List<String>,
            truthPrimary: String?,
            truthSecondary: List<String>,
            predictedPrimary: String?
        ): Boolean {

            /*
             * The set of labels the human considered correct:
             * the truth primary label plus all truth secondary
             * labels. A predicted secondary that matches any of
             * these is credited, even if the AI primary was
             * wrong.
             */
            val truthSet = buildSet {
                CategoryCatalog
                    .normalize(truthPrimary)
                    ?.let { add(it) }
                truthSecondary.forEach {
                    CategoryCatalog
                        .normalize(it)
                        ?.let { label -> add(label) }
                }
            }

            if (predictedPrimary != null &&
                predictedPrimary in truthSet) {
                return true
            }

            return predicted
                .map { CategoryCatalog.normalize(it) }
                .any { it != null && it in truthSet }
        }

        private fun optionalTextAgreement(
            predicted: String?,
            truth: String?
        ): Boolean? {

            val predClean =
                predicted?.trim()?.takeIf { it.isNotEmpty() }
            val truthClean =
                truth?.trim()?.takeIf { it.isNotEmpty() }

            return when {
                truthClean == null -> null
                predClean == null -> false
                else -> predClean.equals(
                    truthClean,
                    ignoreCase = true
                )
            }
        }
    }
}
