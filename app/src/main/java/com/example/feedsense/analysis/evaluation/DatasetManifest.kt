package com.example.feedsense.analysis.evaluation

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

// --------------------------------
// DATASET MANIFEST (Milestone 8A-4)
// --------------------------------
//
// A machine-readable, deterministic record of exactly what
// an evaluation dataset contains, how it was constructed,
// and what its limitations are.
//
// The manifest is the primary tool for reproducibility:
// given the same raw data and policy, the same manifest
// should be reproducible.
//
// Stored as JSON in the EvaluationRun.description field
// or exported independently. No new database tables needed.

data class DatasetManifest(

    // --------------------------------
    // IDENTITY
    // --------------------------------

    val datasetVersion: String,

    val createdAt: LocalDateTime,

    val policyDescription: String,

    // --------------------------------
    // SOURCE DATA RANGE
    // --------------------------------

    val sourceSessionIds: List<String>,

    val sourceProjectIds: List<String>,

    val modelVersionsRepresented: List<String>,

    // --------------------------------
    // CANDIDATE POPULATION
    // --------------------------------

    val totalCandidateItems: Int,

    val includedItemIds: List<String>,

    val excludedItems: List<ExcludedItemRecord>,

    // --------------------------------
    // DISTRIBUTIONS
    // --------------------------------

    val categoryDistribution: List<CountEntry>,

    val platformDistribution: List<CountEntry>,

    val contentTypeDistribution: List<CountEntry>,

    val durationDistribution: List<CountEntry>,

    val skipDistribution: List<CountEntry>,

    val interactionDistribution: Map<String, List<CountEntry>>,

    val confidenceDistribution: List<CountEntry>,

    val modelVersionDistribution: List<CountEntry>,

    // --------------------------------
    // COVERAGE
    // --------------------------------

    val totalCategoriesAvailable: Int,

    val categoriesRepresented: Int,

    val categoriesWithMeaningfulSupport: Int,

    val totalPlatformsSupported: Int,

    val platformsRepresented: Int,

    val platformsWithMeaningfulSupport: Int,

    // --------------------------------
    // ANNOTATION COMPLETENESS
    // --------------------------------

    val annotationCompleteness: Map<String, Double>,

    // --------------------------------
    // QUALITY FLAGS
    // --------------------------------

    val qualityFlags: List<QualityFlag>,

    // --------------------------------
    // AMBIGUITY / DISPUTED
    // --------------------------------

    val ambiguousItemCount: Int,

    val mixedItemCount: Int,

    val disputedItemCount: Int,

    val unknownCategoryCount: Int,

    // --------------------------------
    // DUPLICATE RISK
    // --------------------------------

    val duplicateCandidateCount: Int,

    val duplicateExcludedCount: Int,

    // --------------------------------
    // PROVENANCE
    // --------------------------------

    val temporalEarliestItem: String?,

    val temporalLatestItem: String?
) {

    data class CountEntry(
        val key: String,
        val count: Int
    ) {
        val fraction: Double = 0.0
    }

    data class ExcludedItemRecord(
        val evaluationItemId: String,
        val reason: String
    )

    data class QualityFlag(
        val flag: String,
        val severity: Severity,
        val message: String,
        val details: Map<String, String> = emptyMap()
    ) {
        enum class Severity {
            INFO,
            WARNING,
            CRITICAL
        }
    }

    // --------------------------------
    // JSON SERIALIZATION
    // --------------------------------

    fun toJson(): String {
        return toJsonObject().toString(2)
    }

    fun toJsonObject(): JSONObject {
        val root = JSONObject()

        root.put("datasetVersion", datasetVersion)
        root.put("createdAt", createdAt.toString())
        root.put("policyDescription", policyDescription)

        root.put("sourceSessionIds", JSONArray(sourceSessionIds))
        root.put("sourceProjectIds", JSONArray(sourceProjectIds))
        root.put("modelVersionsRepresented", JSONArray(modelVersionsRepresented))

        root.put("totalCandidateItems", totalCandidateItems)
        root.put("includedItemIds", JSONArray(includedItemIds))

        val excluded = JSONArray()
        excludedItems.forEach { e ->
            val o = JSONObject()
            o.put("evaluationItemId", e.evaluationItemId)
            o.put("reason", e.reason)
            excluded.put(o)
        }
        root.put("excludedItems", excluded)

        root.put("categoryDistribution", countEntries(categoryDistribution))
        root.put("platformDistribution", countEntries(platformDistribution))
        root.put("contentTypeDistribution", countEntries(contentTypeDistribution))
        root.put("durationDistribution", countEntries(durationDistribution))
        root.put("skipDistribution", countEntries(skipDistribution))

        val interactions = JSONObject()
        interactionDistribution.forEach { (signal, entries) ->
            interactions.put(signal, countEntries(entries))
        }
        root.put("interactionDistribution", interactions)

        root.put("confidenceDistribution", countEntries(confidenceDistribution))
        root.put("modelVersionDistribution", countEntries(modelVersionDistribution))

        root.put("totalCategoriesAvailable", totalCategoriesAvailable)
        root.put("categoriesRepresented", categoriesRepresented)
        root.put("categoriesWithMeaningfulSupport", categoriesWithMeaningfulSupport)
        root.put("totalPlatformsSupported", totalPlatformsSupported)
        root.put("platformsRepresented", platformsRepresented)
        root.put("platformsWithMeaningfulSupport", platformsWithMeaningfulSupport)

        val completeness = JSONObject()
        annotationCompleteness.forEach { (field, fraction) ->
            completeness.put(field, Math.round(fraction * 10000.0) / 10000.0)
        }
        root.put("annotationCompleteness", completeness)

        val flags = JSONArray()
        qualityFlags.forEach { f ->
            val o = JSONObject()
            o.put("flag", f.flag)
            o.put("severity", f.severity.name)
            o.put("message", f.message)
            if (f.details.isNotEmpty()) {
                val d = JSONObject()
                f.details.forEach { (k, v) -> d.put(k, v) }
                o.put("details", d)
            }
            flags.put(o)
        }
        root.put("qualityFlags", flags)

        root.put("ambiguousItemCount", ambiguousItemCount)
        root.put("mixedItemCount", mixedItemCount)
        root.put("disputedItemCount", disputedItemCount)
        root.put("unknownCategoryCount", unknownCategoryCount)

        root.put("duplicateCandidateCount", duplicateCandidateCount)
        root.put("duplicateExcludedCount", duplicateExcludedCount)

        root.put("temporalEarliestItem", temporalEarliestItem ?: JSONObject.NULL)
        root.put("temporalLatestItem", temporalLatestItem ?: JSONObject.NULL)

        return root
    }

    private fun countEntries(entries: List<CountEntry>): JSONArray {
        val arr = JSONArray()
        entries.forEach { e ->
            val o = JSONObject()
            o.put("key", e.key)
            o.put("count", e.count)
            arr.put(o)
        }
        return arr
    }

    companion object {

        fun fromJson(json: String): DatasetManifest {
            return fromJsonObject(JSONObject(json))
        }

        fun fromJsonObject(root: JSONObject): DatasetManifest {
            val excluded = mutableListOf<ExcludedItemRecord>()
            val excludedArr = root.getJSONArray("excludedItems")
            for (i in 0 until excludedArr.length()) {
                val o = excludedArr.getJSONObject(i)
                excluded.add(
                    ExcludedItemRecord(
                        evaluationItemId = o.getString("evaluationItemId"),
                        reason = o.getString("reason")
                    )
                )
            }

            val interactionDist = mutableMapOf<String, List<CountEntry>>()
            val interactionsObj = root.getJSONObject("interactionDistribution")
            for (key in interactionsObj.keys()) {
                interactionDist[key] = parseCountEntries(interactionsObj.getJSONArray(key))
            }

            val completeness = mutableMapOf<String, Double>()
            val completenessObj = root.getJSONObject("annotationCompleteness")
            for (key in completenessObj.keys()) {
                completeness[key] = completenessObj.getDouble(key)
            }

            val flags = mutableListOf<QualityFlag>()
            val flagsArr = root.getJSONArray("qualityFlags")
            for (i in 0 until flagsArr.length()) {
                val o = flagsArr.getJSONObject(i)
                val details = mutableMapOf<String, String>()
                if (o.has("details")) {
                    val d = o.getJSONObject("details")
                    for (k in d.keys()) {
                        details[k] = d.getString(k)
                    }
                }
                flags.add(
                    QualityFlag(
                        flag = o.getString("flag"),
                        severity = QualityFlag.Severity.valueOf(o.getString("severity")),
                        message = o.getString("message"),
                        details = details
                    )
                )
            }

            val temporalEarliest = root.optString("temporalEarliestItem", null)
            val temporalLatest = root.optString("temporalLatestItem", null)

            return DatasetManifest(
                datasetVersion = root.getString("datasetVersion"),
                createdAt = LocalDateTime.parse(root.getString("createdAt")),
                policyDescription = root.getString("policyDescription"),
                sourceSessionIds = root.getJSONArray("sourceSessionIds").let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                },
                sourceProjectIds = root.getJSONArray("sourceProjectIds").let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                },
                modelVersionsRepresented = root.getJSONArray("modelVersionsRepresented").let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                },
                totalCandidateItems = root.getInt("totalCandidateItems"),
                includedItemIds = root.getJSONArray("includedItemIds").let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                },
                excludedItems = excluded,
                categoryDistribution = parseCountEntries(root.getJSONArray("categoryDistribution")),
                platformDistribution = parseCountEntries(root.getJSONArray("platformDistribution")),
                contentTypeDistribution = parseCountEntries(root.getJSONArray("contentTypeDistribution")),
                durationDistribution = parseCountEntries(root.getJSONArray("durationDistribution")),
                skipDistribution = parseCountEntries(root.getJSONArray("skipDistribution")),
                interactionDistribution = interactionDist,
                confidenceDistribution = parseCountEntries(root.getJSONArray("confidenceDistribution")),
                modelVersionDistribution = parseCountEntries(root.getJSONArray("modelVersionDistribution")),
                totalCategoriesAvailable = root.getInt("totalCategoriesAvailable"),
                categoriesRepresented = root.getInt("categoriesRepresented"),
                categoriesWithMeaningfulSupport = root.getInt("categoriesWithMeaningfulSupport"),
                totalPlatformsSupported = root.getInt("totalPlatformsSupported"),
                platformsRepresented = root.getInt("platformsRepresented"),
                platformsWithMeaningfulSupport = root.getInt("platformsWithMeaningfulSupport"),
                annotationCompleteness = completeness,
                qualityFlags = flags,
                ambiguousItemCount = root.getInt("ambiguousItemCount"),
                mixedItemCount = root.getInt("mixedItemCount"),
                disputedItemCount = root.getInt("disputedItemCount"),
                unknownCategoryCount = root.getInt("unknownCategoryCount"),
                duplicateCandidateCount = root.getInt("duplicateCandidateCount"),
                duplicateExcludedCount = root.getInt("duplicateExcludedCount"),
                temporalEarliestItem = temporalEarliest,
                temporalLatestItem = temporalLatest
            )
        }

        private fun parseCountEntries(arr: JSONArray): List<CountEntry> {
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CountEntry(
                    key = o.getString("key"),
                    count = o.getInt("count")
                )
            }
        }
    }
}
