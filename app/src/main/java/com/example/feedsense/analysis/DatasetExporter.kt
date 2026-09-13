package com.example.feedsense.analysis

import com.example.feedsense.analysis.privacy.PrivacyExportPolicy
import com.example.feedsense.analysis.privacy.PrivacyTextRedactor
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelFeedback
import com.example.feedsense.model.ResearchObservation
import com.example.feedsense.model.ResearchSession
import java.time.LocalDateTime
import org.json.JSONArray
import org.json.JSONObject

/*
 * Milestone 7T.
 *
 * Pure builder for the JSON dataset export. Every input
 * is a plain model so the output structure is
 * unit-testable without Room or Android.
 *
 * The export contains the RESEARCH record only:
 *
 *   sessions            - research session metadata
 *   feedItems           - content pieces and how long the
 *                         user watched each
 *   observations        - manual + AI observations
 *   references          - validated labels (the training /
 *                         evaluation dataset; the review
 *                         queue itself is excluded so the
 *                         export is stable ground truth)
 *   feedback            - every confirmed correction with
 *                         original prediction side by side
 *
 * No raw screenshots and no PENDING review rows - the
 * export is the labeled, shareable research dataset.
 */
class DatasetExporter {

    companion object {
        const val FORMAT_NAME = "feedsense-dataset"
        const val SCHEMA_VERSION = 1
    }

    fun buildJson(
        sessions: List<ResearchSession>,
        feedItems: List<FeedItem>,
        references: List<LabeledReference>,
        feedback: List<ModelFeedback>,
        observations: List<ResearchObservation>,
        exportedAt: LocalDateTime = LocalDateTime.now()
    ): JSONObject {

        return JSONObject().apply {

            // -------------------------------
            // MANIFEST
            // -------------------------------

            put(
                "manifest",
                JSONObject().apply {
                    put("format", FORMAT_NAME)
                    put("schemaVersion", SCHEMA_VERSION)
                    put("exportedAt", exportedAt.toString())
                    put(
                        "counts",
                        JSONObject().apply {
                            put("sessions", sessions.size)
                            put("feedItems", feedItems.size)
                            put("references", references.size)
                            put("feedback", feedback.size)
                            put("observations", observations.size)
                        }
                    )
                }
            )

            // -------------------------------
            // DATA
            // -------------------------------

            put("sessions", sessionArray(sessions))
            put("feedItems", feedItemArray(feedItems))
            put(
                "references",
                referenceArray(
                    references,
                    visibleTextProvider = { it },
                    includeFilePath = true
                )
            )
            put(
                "feedback",
                feedbackArray(
                    feedback,
                    visibleTextProvider = { it }
                )
            )
            put("observations", observationArray(observations))
        }
    }

    /*
     * Milestone 8B-10.
     *
     * Privacy-policy-aware export builder.
     *
     * Unlike buildJson (which always emits raw visibleText),
     * this overload applies an ExportPrivacyPolicy:
     *
     *   SANITIZED_METADATA_ONLY         visibleText omitted,
     *                                   raw frame paths omitted
     *   METADATA_AND_REDACTED_TEXT      visibleText replaced by
     *                                   [REDACTED]-style redaction
     *   DEBUG_RAW_TEXT                  raw OCR text and raw
     *                                   frame paths (DEBUG only)
     *
     * The manifest records the exact policy so consumers can
     * audit how the export was produced.
     */
    fun buildJsonByPrivacyPolicy(
        sessions: List<ResearchSession>,
        feedItems: List<FeedItem>,
        references: List<LabeledReference>,
        feedback: List<ModelFeedback>,
        observations: List<ResearchObservation>,
        policy: PrivacyExportPolicy =
            PrivacyExportPolicy.SAFE_DEFAULT,
        exportedAt: LocalDateTime = LocalDateTime.now()
    ): JSONObject {

        val redactor = PrivacyTextRedactor()

        val visibleTextProvider: (String?) -> String? = {
            raw -> when {
                policy.exposesRawOcrText -> raw
                policy.exposesRedactedOcrText -> {
                    raw?.let { redactor.redact(it).redacted.trim() }
                        ?.takeIf { it.isNotBlank() }
                }
                else -> null
            }
        }

        val includeFilePath = policy.exposesRawOcrText

        return JSONObject().apply {

            put(
                "manifest",
                JSONObject().apply {
                    put("format", FORMAT_NAME)
                    put("schemaVersion", SCHEMA_VERSION)
                    put("exportedAt", exportedAt.toString())
                    put(
                        "privacyExportMode",
                        policy.mode.label
                    )
                    put(
                        "privacyExportPolicyVersion",
                        policy.policyVersion
                    )
                    put(
                        "counts",
                        JSONObject().apply {
                            put("sessions", sessions.size)
                            put("feedItems", feedItems.size)
                            put("references", references.size)
                            put("feedback", feedback.size)
                            put("observations", observations.size)
                        }
                    )
                }
            )

            put("sessions", sessionArray(sessions))
            put("feedItems", feedItemArray(feedItems))
            put(
                "references",
                referenceArray(
                    references,
                    visibleTextProvider = visibleTextProvider,
                    includeFilePath = includeFilePath
                )
            )
            put(
                "feedback",
                feedbackArray(
                    feedback,
                    visibleTextProvider = visibleTextProvider
                )
            )
            put("observations", observationArray(observations))
        }
    }

    // --------------------------------
    // SESSIONS
    // --------------------------------

    private fun sessionArray(
        sessions: List<ResearchSession>
    ): JSONArray {

        return JSONArray().apply {

            for (session in sessions) {

                put(
                    JSONObject().apply {
                        put("id", session.id)
                        put("projectId", session.projectId)
                        put("title", session.title)
                        put("startedAt", session.startedAt.toString())
                        putOrNull("endedAt", session.endedAt)
                        put("observationCount", session.observationCount)
                        put("notes", session.notes)
                        put("active", session.active)
                    }
                )
            }
        }
    }

    // --------------------------------
    // FEED ITEMS
    // --------------------------------

    private fun feedItemArray(
        items: List<FeedItem>
    ): JSONArray {

        return JSONArray().apply {

            for (item in items) {

                put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("sessionId", item.sessionId)
                        put("startTime", item.startTime.toString())
                        putOrNull("endTime", item.endTime)
                        put("durationSeconds", item.durationSeconds)
                        putOrNull("category", item.category)
                        putOrNull("confidence", item.confidence)
                        putOrNull("topic", item.topic)
                        putOrNull("tone", item.tone)
                        put("contentType", item.contentType)
                        put("skipped", item.skipped)
                        put("frameCount", item.frameCount)
                        put("interactionSignals", stringArray(item.interactionSignals))
                        putOrNull("modelVersion", item.modelVersion)
                        putOrNull("frameFingerprint", item.frameFingerprint)
                        put("needsReview", item.needsReview)
                        put("candidateCategories", stringArray(item.candidateCategories))
                        putOrNull("classificationReason", item.classificationReason)
                        put("updatedAt", item.updatedAt.toString())
                        put("contentTransitions", stringArray(item.contentTransitions))
                        put("interactionEvidence", stringArray(item.interactionEvidence))
                        putOrNull("secondaryCategory", item.secondaryCategory)
                        put("secondaryCategories", stringArray(item.secondaryCategories))
                        put("mixedContent", item.mixedContent)
                        put("pausedDurationSeconds", item.pausedDurationSeconds)
                        put("activeWatchDurationSeconds", item.activeWatchDurationSeconds)
                        put("uncertaintyLevel", item.uncertaintyLevel)
                    }
                )
            }
        }
    }

    // --------------------------------
    // REFERENCES (VALIDATED GROUND TRUTH)
    // --------------------------------

    private fun referenceArray(
        references: List<LabeledReference>,
        visibleTextProvider: (String?) -> String?,
        includeFilePath: Boolean
    ): JSONArray {

        return JSONArray().apply {

            for (reference in references) {

                put(
                    JSONObject().apply {
                        put("id", reference.id)
                        put("frameId", reference.frameId)
                        put("sessionId", reference.sessionId)
                        putOrNull("filePath", reference.filePath, includeFilePath)
                        putOrNull("feedItemId", reference.feedItemId)
                        putOrNull("aiCategory", reference.aiCategory)
                        putOrNull("aiConfidence", reference.aiConfidence)
                        put("aiSource", reference.aiSource)
                        putOrNull("modelVersion", reference.modelVersion)
                        put("candidateCategories", stringArray(reference.candidateCategories))
                        putOrNull("platform", reference.platform)
                        putOrNull("topic", reference.topic)
                        putOrNull("tone", reference.tone)
                        putOrNull("visibleText", visibleTextProvider(reference.visibleText))
                        putOrNull("aiReason", reference.aiReason)
                        put("interactionSignals", stringArray(reference.interactionSignals))
                        putOrNull("frameFingerprint", reference.frameFingerprint)
                        put("labelSource", reference.labelSource)
                        put("validationStatus", reference.validationStatus)
                        putOrNull("validatedLabel", reference.validatedLabel)
                        putOrNull("agreement", reference.agreement)
                        put("createdAt", reference.createdAt.toString())
                        putOrNull("reviewedAt", reference.reviewedAt)
                    }
                )
            }
        }
    }

    // --------------------------------
    // FEEDBACK (CORRECTIONS)
    // --------------------------------

    private fun feedbackArray(
        feedback: List<ModelFeedback>,
        visibleTextProvider: (String?) -> String?
    ): JSONArray {

        return JSONArray().apply {

            for (item in feedback) {

                put(
                    JSONObject().apply {
                        put("id", item.id)
                        putOrNull("frameId", item.frameId)
                        putOrNull("feedItemId", item.feedItemId)
                        put("sessionId", item.sessionId)
                        putOrNull("platform", item.platform)
                        putOrNull("visibleText", visibleTextProvider(item.visibleText))
                        put("interactionSignals", stringArray(item.interactionSignals))
                        putOrNull("originalCategory", item.originalCategory)
                        putOrNull("originalConfidence", item.originalConfidence)
                        putOrNull("originalTopic", item.originalTopic)
                        putOrNull("originalTone", item.originalTone)
                        putOrNull("modelVersion", item.modelVersion)
                        putOrNull("correctedCategory", item.correctedCategory)
                        putOrNull("correctedTopic", item.correctedTopic)
                        putOrNull("correctedTone", item.correctedTone)
                        put("correctionSource", item.correctionSource)
                        putOrNull("confidenceAfterCorrection", item.confidenceAfterCorrection)
                        put("categoryAgreement", item.categoryAgreement)
                        putOrNull("topicAgreement", item.topicAgreement)
                        putOrNull("toneAgreement", item.toneAgreement)
                        put("createdAt", item.createdAt.toString())
                    }
                )
            }
        }
    }

    // --------------------------------
    // OBSERVATIONS
    // --------------------------------

    private fun observationArray(
        observations: List<ResearchObservation>
    ): JSONArray {

        return JSONArray().apply {

            for (observation in observations) {

                put(
                    JSONObject().apply {
                        put("id", observation.id)
                        put("sessionId", observation.sessionId)
                        put("text", observation.text)
                        put("createdAt", observation.createdAt.toString())
                        put("source", observation.source)
                    }
                )
            }
        }
    }

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun stringArray(
        values: List<String>
    ): JSONArray {

        return JSONArray().apply {
            values.forEach {
                put(it)
            }
        }
    }

    private fun JSONObject.putOrNull(
        key: String,
        value: String?
    ) {
        if (value != null) {
            put(key, value)
        }
    }

    private fun JSONObject.putOrNull(
        key: String,
        value: String?,
        include: Boolean
    ) {
        if (include && value != null) {
            put(key, value)
        }
    }

    private fun JSONObject.putOrNull(
        key: String,
        value: Double?
    ) {
        if (value != null) {
            put(key, value)
        }
    }

    private fun JSONObject.putOrNull(
        key: String,
        value: Boolean?
    ) {
        if (value != null) {
            put(key, value)
        }
    }

    private fun JSONObject.putOrNull(
        key: String,
        value: LocalDateTime?
    ) {
        if (value != null) {
            put(key, value.toString())
        }
    }
}
