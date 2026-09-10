package com.example.feedsense.analysis.privacy

import com.example.feedsense.analysis.DatasetExporter
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelFeedback
import com.example.feedsense.model.ResearchObservation
import com.example.feedsense.model.ResearchSession
import java.time.LocalDateTime
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-10.
 *
 * Export privacy policy + DatasetExporter policy path.
 *
 * The production default is SANITIZED_METADATA_ONLY: raw OCR
 * text and raw frame paths are never written to export files.
 */
class PrivacyExportPolicyTest {

    private val exporter = DatasetExporter()
    private val now = LocalDateTime.of(
        2024, 5, 1, 12, 0, 0
    )

    private fun reference(
        visibleText: String? = "Contact john@example.com",
        filePath: String = "/data/raw/frames/frame-1.jpg"
    ): LabeledReference {
        return LabeledReference(
            frameId = "frame-1",
            sessionId = "s1",
            filePath = filePath,
            aiCategory = "sports",
            aiConfidence = 0.8,
            aiSource = "LOCAL",
            modelVersion = "v1",
            visibleText = visibleText,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            validationStatus =
                LabeledReference.VALIDATION_VALIDATED,
            validatedLabel = "sports",
            createdAt = now
        )
    }

    private fun feedback(
        visibleText: String? = "private@example.com"
    ): ModelFeedback {
        return ModelFeedback(
            frameId = "frame-1",
            feedItemId = null,
            sessionId = "s1",
            visibleText = visibleText,
            originalCategory = "sports",
            correctedCategory = "comedy",
            correctionSource = "USER",
            categoryAgreement = false,
            createdAt = now
        )
    }

    private fun emptySession(): ResearchSession {
        return ResearchSession(
            id = "s1",
            projectId = "p1",
            title = "t",
            startedAt = now,
            active = false
        )
    }

    private fun referenceArray(
        json: org.json.JSONObject
    ): JSONArray {
        return json.getJSONArray("references")
    }

    // --------------------------------------------------
    // SAFE DEFAULT
    // --------------------------------------------------

    @Test
    fun `safe default omits raw OCR text and raw frame paths`() {
        val json = exporter.buildJsonByPrivacyPolicy(
            sessions = listOf(emptySession()),
            feedItems = emptyList(),
            references = listOf(reference()),
            feedback = listOf(feedback()),
            observations = emptyList(),
            exportedAt = now
        )

        assertEquals(
            "SANITIZED_METADATA_ONLY",
            json.getJSONObject("manifest")
                .getString("privacyExportMode")
        )
        assertEquals(
            "privacy-v1",
            json.getJSONObject("manifest")
                .getString("privacyExportPolicyVersion")
        )

        val row = referenceArray(json).getJSONObject(0)
        assertFalse("raw visibleText leaked", row.has("visibleText"))
        assertFalse("raw frame path leaked", row.has("filePath"))

        val fbRow = json.getJSONArray("feedback").getJSONObject(0)
        assertFalse("raw feedback text leaked", fbRow.has("visibleText"))
    }

    @Test
    fun `safe default keeps structured model metadata`() {
        val json = exporter.buildJsonByPrivacyPolicy(
            sessions = listOf(emptySession()),
            feedItems = emptyList(),
            references = listOf(reference()),
            feedback = emptyList(),
            observations = emptyList(),
            exportedAt = now
        )

        val row = referenceArray(json).getJSONObject(0)
        assertEquals("sports", row.getString("aiCategory"))
        assertEquals("sports", row.getString("validatedLabel"))
        assertEquals(
            LabeledReference.VALIDATION_VALIDATED,
            row.getString("validationStatus")
        )
    }

    // --------------------------------------------------
    // REDACTED TEXT MODE
    // --------------------------------------------------

    @Test
    fun `redacted mode exports redacted text with no raw identifiers`() {
        val json = exporter.buildJsonByPrivacyPolicy(
            sessions = listOf(emptySession()),
            feedItems = emptyList(),
            references = listOf(reference(
                visibleText = "Hi john@example.com!"
            )),
            feedback = emptyList(),
            observations = emptyList(),
            policy = PrivacyExportPolicy(
                mode = ExportPrivacyMode.METADATA_AND_REDACTED_TEXT
            ),
            exportedAt = now
        )

        val row = referenceArray(json).getJSONObject(0)
        val text = row.getString("visibleText")
        assertFalse(text.contains("john@example.com"))
        assertTrue(text.contains(REDACTION_TOKEN))
        assertTrue(text.startsWith("Hi "))
        // Raw frame paths still excluded in redacted mode.
        assertFalse(row.has("filePath"))
    }

    // --------------------------------------------------
    // DEBUG RAW TEXT MODE
    // --------------------------------------------------

    @Test
    fun `debug mode exports raw text and paths`() {
        val json = exporter.buildJsonByPrivacyPolicy(
            sessions = listOf(emptySession()),
            feedItems = emptyList(),
            references = listOf(reference()),
            feedback = emptyList(),
            observations = emptyList(),
            policy = PrivacyExportPolicy(
                mode = ExportPrivacyMode.DEBUG_RAW_TEXT
            ),
            exportedAt = now
        )

        val row = referenceArray(json).getJSONObject(0)
        assertEquals(
            "Contact john@example.com",
            row.getString("visibleText")
        )
        assertEquals(
            "/data/raw/frames/frame-1.jpg",
            row.getString("filePath")
        )
    }

    // --------------------------------------------------
    // LEGACY BUILD JSON IS UNCHANGED
    // --------------------------------------------------

    @Test
    fun `legacy buildJson still exports raw text`() {
        val json = exporter.buildJson(
            sessions = listOf(emptySession()),
            feedItems = emptyList(),
            references = listOf(reference()),
            feedback = listOf(feedback()),
            observations = emptyList(),
            exportedAt = now
        )
        assertEquals(
            "Contact john@example.com",
            referenceArray(json).getJSONObject(0)
                .getString("visibleText")
        )
        // Legacy export does not advertise a privacy mode.
        assertFalse(
            json.getJSONObject("manifest")
                .has("privacyExportMode")
        )
    }

    // --------------------------------------------------
    // RESEARCH OBSERVATIONS NEVER EXPORT RAW SCREEN DATA
    // --------------------------------------------------

    @Test
    fun `observations carry only recorded text`() {
        val observation = ResearchObservation(
            id = "obs-1",
            sessionId = "s1",
            text = "IPL 2026 classifications consistent",
            createdAt = now,
            source = "AI"
        )
        val json = exporter.buildJsonByPrivacyPolicy(
            sessions = listOf(emptySession()),
            feedItems = emptyList(),
            references = emptyList(),
            feedback = listOf(feedback()),
            observations = listOf(observation),
            exportedAt = now
        )
        // Research-observation text is researcher-authored
        // metadata and remains available.
        assertEquals(
            "IPL 2026 classifications consistent",
            json.getJSONArray("observations")
                .getJSONObject(0)
                .getString("text")
        )
    }
}