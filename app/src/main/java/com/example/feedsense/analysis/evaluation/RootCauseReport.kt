package com.example.feedsense.analysis.evaluation

import org.json.JSONArray
import org.json.JSONObject

// --------------------------------
// ROOT-CAUSE REPORT (Milestone 8A-6)
// --------------------------------
//
// Aggregates the per-error RootCauseAssessment records into a
// research-facing report. Responsibilities:
//
//   - association table assessmentType -> attributionClass, so
//     patterns across errors are traceable
//   - data-vs-model vs pipeline vs taxonomy vs annotation
//     classification with counts
//   - deterministic PRIORITY score for the hard-case queue
//   - a hard-case review queue (never expands a CONFIRMED claim)
//   - TRAINING-CANDIDATE marking WITHOUT training anything
//   - CSV / JSON export referencing only existing object ids
//
// Privacy: no raw screenshots or OCR payloads are serialized;
// exports reference frame/prediction/truth ids.

class RootCauseReport private constructor(
    val assessments: List<RootCauseAssessment>,
    val aggregated: Aggregated
) {

    data class Association(
        val errorType: String,
        val attributionClass: String,
        val count: Int
    )

    data class ClassCount(
        val attributionClass: String,
        val count: Int
    )

    data class DataVsModelSummary(
        val modelRelated: Int,
        val dataRelated: Int,
        val pipelineRelated: Int,
        val taxonomyRelated: Int,
        val annotationRelated: Int,
        val unknownRelated: Int,
        val insufficientRealData: Boolean
    )

    data class Aggregated(
        val totalAssessed: Int,
        val confirmed: Int,
        val possible: Int,
        val supported: Int,
        val rejected: Int,
        val inconclusive: Int,
        val notApplicable: Int,
        val associations: List<Association>,
        val classCounts: List<ClassCount>,
        val dataVsModel: DataVsModelSummary,
        val reviewQueue: List<RootCauseAssessment>
    ) {
        val overallAttributionConfidence: String
            get() = when {
                totalAssessed == 0 ->
                    AnnotationAgreement.INSUFFICIENT_DATA_FOR_INTER_ANNOTATOR_STATISTICS
                confirmed >= totalAssessed / 2 -> RootCauseTypes.CONFIDENCE_HIGH
                possible + supported >= totalAssessed / 2 ->
                    RootCauseTypes.CONFIDENCE_MEDIUM
                else -> RootCauseTypes.CONFIDENCE_LOW
            }
    }

    // --------------------------------
    // BUILDERS
    // --------------------------------

    companion object {

        /**
         * Builds the report from per-error assessments.
         * Deterministic: results are ordered by priority (see
         * priorityScore) then by analysis id.
         */
        fun build(
            assessments: List<RootCauseAssessment>
        ): RootCauseReport {
            val conf = assessments.count { it.anyConfirmed() }
            val poss = assessments.count {
                it.attributionStatus == RootCauseTypes.STATUS_POSSIBLE
            }
            val supp = assessments.count {
                it.attributionStatus == RootCauseTypes.STATUS_SUPPORTED
            }
            val rej = assessments.count {
                it.attributionStatus == RootCauseTypes.STATUS_REJECTED
            }
            val incon = assessments.count {
                it.attributionStatus == RootCauseTypes.STATUS_INCONCLUSIVE
            }
            val na = assessments.count {
                it.attributionStatus == RootCauseTypes.STATUS_NOT_APPLICABLE
            }

            val associations = associations(assessments)
            val classCounts = classCounts(assessments)
            val dvm = dataVsModel(assessments)
            val queue = hardCaseQueue(assessments)

            val aggregated = Aggregated(
                totalAssessed = assessments.size,
                confirmed = conf,
                possible = poss,
                supported = supp,
                rejected = rej,
                inconclusive = incon,
                notApplicable = na,
                associations = associations,
                classCounts = classCounts,
                dataVsModel = dvm,
                reviewQueue = queue
            )
            return RootCauseReport(assessments, aggregated)
        }

        // --------------------------------
        // AGGREGATION HELPERS
        // --------------------------------

        fun associations(
            assessments: List<RootCauseAssessment>
        ): List<Association> {
            return assessments
                .flatMap { a ->
                    a.causes.map { c ->
                        a.errorType to c.attributionClass
                    }
                }
                .groupingBy { it }
                .eachCount()
                .map { (key, count) ->
                    Association(key.first, key.second, count)
                }
                .sortedWith(
                    compareByDescending<Association> { it.count }
                        .thenBy { it.errorType }
                        .thenBy { it.attributionClass }
                )
        }

        fun classCounts(
            assessments: List<RootCauseAssessment>
        ): List<ClassCount> {
            return RootCauseTypes.ALL_ATTRIBUTION_CLASSES.map { clazz ->
                ClassCount(
                    attributionClass = clazz,
                    count = assessments.count {
                        it.attributionClass == clazz
                    }
                )
            }
        }

        fun dataVsModel(
            assessments: List<RootCauseAssessment>
        ): DataVsModelSummary {
            return DataVsModelSummary(
                modelRelated = assessments.count {
                    it.attributionClass == RootCauseTypes.CLASS_MODEL_RELATED
                },
                dataRelated = assessments.count {
                    it.attributionClass == RootCauseTypes.CLASS_DATA_RELATED
                },
                pipelineRelated = assessments.count {
                    it.attributionClass == RootCauseTypes.CLASS_PIPELINE_RELATED
                },
                taxonomyRelated = assessments.count {
                    it.attributionClass == RootCauseTypes.CLASS_TAXONOMY_RELATED
                },
                annotationRelated = assessments.count {
                    it.attributionClass == RootCauseTypes.CLASS_ANNOTATION_RELATED
                },
                unknownRelated = assessments.count {
                    it.attributionClass == RootCauseTypes.CLASS_UNKNOWN
                },
                insufficientRealData = assessments.isEmpty()
            )
        }

        /**
         * Deterministic hard-case priority score (higher = more
         * urgent for human review). Only POSSIBLE / INCONCLUSIVE /
         * UNKNOWN assessments enter the queue; CONFIRMED or
         * REJECTED do not. Ties break on analysis id.
         */
        fun priorityScore(a: RootCauseAssessment): Int {
            if (a.anyConfirmed() ||
                a.attributionStatus == RootCauseTypes.STATUS_REJECTED
            ) {
                return Int.MIN_VALUE
            }
            var score = 0
            when (a.attributionStatus) {
                RootCauseTypes.STATUS_INCONCLUSIVE -> score += 40
                RootCauseTypes.STATUS_POSSIBLE -> score += 30
                RootCauseTypes.STATUS_UNASSESSED -> score += 20
                else -> score += 10
            }
            when (a.attributionClass) {
                RootCauseTypes.CLASS_UNKNOWN -> score += 25
                RootCauseTypes.CLASS_MODEL_RELATED -> score += 15
                RootCauseTypes.CLASS_TAXONOMY_RELATED -> score += 10
                else -> score += 5
            }
            return score
        }

        fun hardCaseQueue(
            assessments: List<RootCauseAssessment>
        ): List<RootCauseAssessment> {
            return assessments
                .filter {
                    !it.anyConfirmed() &&
                        it.attributionStatus !=
                        RootCauseTypes.STATUS_REJECTED
                }
                .sortedWith(
                    compareByDescending<RootCauseAssessment> {
                        priorityScore(it)
                    }.thenBy { it.analysisId }
                )
        }

        // --------------------------------
        // TRAINING-CANDIDATE MARKING (no training)
        // --------------------------------
        //
        // Flags assessments that could be re-examined (NOT
        // retrained on) later. The flag is a research marker with
        // provenance; it never triggers or performs any training
        // in this milestone.
        fun trainingCandidates(
            assessments: List<RootCauseAssessment>
        ): List<RootCauseAssessment> {
            return hardCaseQueue(assessments)
                .filter {
                    it.attributionStatus == RootCauseTypes.STATUS_POSSIBLE ||
                        it.attributionStatus == RootCauseTypes.STATUS_INCONCLUSIVE
                }
                .sortedBy { it.analysisId }
        }

        // --------------------------------
        // CSV EXPORT (references only)
        // --------------------------------

        fun toCsv(
            assessments: List<RootCauseAssessment>
        ): String {
            val sb = StringBuilder()
            sb.append(
                "analysisId,evaluationItemId,evaluationRecordId," +
                    "datasetVersion,modelVersion,diagnosticVersion," +
                    "capability,errorType,cause,role,attributionClass," +
                    "attributionStatus,evidenceStrength," +
                    "attributionConfidence,humanReviewStatus,reviewerId," +
                    "evidenceCount\n"
            )
            assessments.sortedBy { it.analysisId }.forEach { a ->
                val ec = a.evidenceReferences.size
                if (a.causes.isEmpty()) {
                    sb.append(
                        csvLine(
                            a, "", "", a.attributionClass,
                            a.attributionStatus, a.evidenceStrength,
                            a.attributionConfidence, ec
                        )
                    )
                } else {
                    a.causes.forEach { c ->
                        sb.append(
                            csvLine(
                                a, c.cause, c.role, c.attributionClass,
                                c.attributionStatus, c.evidenceStrength,
                                c.attributionConfidence, ec
                            )
                        )
                    }
                }
            }
            return sb.toString()
        }

        private fun csvLine(
            a: RootCauseAssessment,
            cause: String,
            role: String,
            clazz: String,
            status: String,
            strength: String,
            confidence: String?,
            evidenceCount: Int
        ): String {
            val esc = { s: String -> "\"" + s.replace("\"", "\"\"") + "\"" }
            return listOf(
                a.analysisId,
                a.evaluationItemId,
                a.evaluationRecordId ?: "",
                a.datasetVersion ?: "",
                a.modelVersion ?: "",
                a.diagnosticVersion,
                a.capability,
                a.errorType,
                cause,
                role,
                clazz,
                status,
                strength,
                confidence ?: "",
                a.humanReviewStatus,
                a.reviewerId ?: "",
                evidenceCount.toString()
            ).joinToString(",") { esc(it) } + "\n"
        }
    }

    // --------------------------------
    // JSON EXPORT (references only)
    // --------------------------------

    fun toJson(): String = toJsonObject().toString(2)

    fun toJsonObject(): JSONObject {
        val root = JSONObject()
        root.put("totalAssessed", aggregated.totalAssessed)
        root.put("confirmed", aggregated.confirmed)
        root.put("possible", aggregated.possible)
        root.put("supported", aggregated.supported)
        root.put("rejected", aggregated.rejected)
        root.put("inconclusive", aggregated.inconclusive)
        root.put("notApplicable", aggregated.notApplicable)
        root.put("overallAttributionConfidence",
            aggregated.overallAttributionConfidence)

        val classes = JSONArray()
        aggregated.classCounts.forEach { c ->
            val o = JSONObject()
            o.put("attributionClass", c.attributionClass)
            o.put("count", c.count)
            classes.put(o)
        }
        root.put("attributionClasses", classes)

        val associations = JSONArray()
        aggregated.associations.forEach { a ->
            val o = JSONObject()
            o.put("errorType", a.errorType)
            o.put("attributionClass", a.attributionClass)
            o.put("count", a.count)
            associations.put(o)
        }
        root.put("associations", associations)

        val dvm = JSONObject()
        dvm.put("modelRelated", aggregated.dataVsModel.modelRelated)
        dvm.put("dataRelated", aggregated.dataVsModel.dataRelated)
        dvm.put("pipelineRelated",
            aggregated.dataVsModel.pipelineRelated)
        dvm.put("taxonomyRelated",
            aggregated.dataVsModel.taxonomyRelated)
        dvm.put("annotationRelated",
            aggregated.dataVsModel.annotationRelated)
        dvm.put("unknownRelated",
            aggregated.dataVsModel.unknownRelated)
        dvm.put("insufficientRealData",
            aggregated.dataVsModel.insufficientRealData)
        root.put("dataVsModel", dvm)

        val queue = JSONArray()
        aggregated.reviewQueue.forEach { a ->
            queue.put(a.analysisId)
        }
        root.put("reviewQueue", queue)

        val candidates = JSONArray()
        trainingCandidates(assessments).forEach { a ->
            candidates.put(a.analysisId)
        }
        root.put("trainingCandidates", candidates)

        val details = JSONArray()
        assessments.sortedBy { it.analysisId }.forEach { a ->
            details.put(a.toJsonObject())
        }
        root.put("assessments", details)

        return root
    }
}
