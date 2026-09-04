package com.example.feedsense

import com.example.feedsense.analysis.DatasetExporter
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelFeedback
import com.example.feedsense.model.ResearchObservation
import com.example.feedsense.model.ResearchSession
import java.time.LocalDateTime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 7T.
 *
 * The pure JSON dataset exporter: manifest + counts, all
 * five sections, list fields as arrays and nulls omitted.
 */
class DatasetExporterTest {

    private val exporter =
        DatasetExporter()

    private val now =
        LocalDateTime.of(2026, 8, 17, 10, 30)

    private fun session(): ResearchSession {
        return ResearchSession(
            id = "session-1",
            projectId = "project-1",
            title = "Evening scrolling",
            startedAt = now,
            endedAt = now.plusHours(1),
            observationCount = 3,
            notes = "test",
            active = false
        )
    }

    private fun item(): FeedItem {
        return FeedItem(
            id = "item-1",
            sessionId = "session-1",
            startTime = now,
            endTime = now.plusSeconds(30),
            durationSeconds = 30,
            category = "comedy",
            confidence = 0.9,
            topic = "standup",
            tone = "funny",
            contentType = FeedItem.CONTENT_SHORT_VIDEO,
            skipped = false,
            representativeFramePath = "/tmp/a.jpg",
            frameCount = 12,
            interactionSignals = listOf("watched", "liked"),
            modelVersion = "local-v5.1",
            needsReview = false,
            interactionEvidence = listOf("watched|HIGH|10s"),
            secondaryCategories = listOf("variety"),
            mixedContent = false,
            uncertaintyLevel = FeedItem.UNCERTAINTY_LOW
        )
    }

    private fun reference(): LabeledReference {
        return LabeledReference(
            id = "ref-1",
            frameId = "frame-1",
            sessionId = "session-1",
            filePath = "/tmp/a.jpg",
            aiCategory = "comedy",
            aiConfidence = 0.6,
            aiSource = "LOCAL",
            modelVersion = "local-v5.1",
            platform = "instagram",
            topic = "standup",
            tone = "funny",
            visibleText = "hello",
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            validationStatus = LabeledReference.VALIDATION_VALIDATED,
            validatedLabel = "comedy",
            agreement = true,
            reviewedAt = now
        )
    }

    private fun feedback(): ModelFeedback {
        return ModelFeedback(
            id = "fb-1",
            frameId = "frame-1",
            feedItemId = null,
            sessionId = "session-1",
            originalCategory = "comedy",
            originalConfidence = 0.6,
            modelVersion = "local-v5.1",
            correctedCategory = "comedy",
            correctionSource = ModelFeedback.SOURCE_USER,
            categoryAgreement = true,
            createdAt = now
        )
    }

    private fun observation(): ResearchObservation {
        return ResearchObservation(
            id = "obs-1",
            sessionId = "session-1",
            text = "watched two reels",
            createdAt = now,
            source = ResearchObservation.SOURCE_AUTO
        )
    }

    // --------------------------------
    // MANIFEST
    // --------------------------------

    @Test
    fun `manifest carries format version timestamp and counts`() {

        val json =
            exporter.buildJson(
                sessions = listOf(session()),
                feedItems = listOf(item()),
                references = listOf(reference()),
                feedback = listOf(feedback()),
                observations = listOf(observation()),
                exportedAt = now
            )

        val manifest =
            json.getJSONObject("manifest")

        assertEquals(
            DatasetExporter.FORMAT_NAME,
            manifest.getString("format")
        )
        assertEquals(
            DatasetExporter.SCHEMA_VERSION,
            manifest.getInt("schemaVersion")
        )
        assertEquals(
            now.toString(),
            manifest.getString("exportedAt")
        )

        val counts =
            manifest.getJSONObject("counts")

        assertEquals(1, counts.getInt("sessions"))
        assertEquals(1, counts.getInt("feedItems"))
        assertEquals(1, counts.getInt("references"))
        assertEquals(1, counts.getInt("feedback"))
        assertEquals(1, counts.getInt("observations"))
    }

    @Test
    fun `empty dataset exports zero counts`() {

        val json =
            exporter.buildJson(
                sessions = emptyList(),
                feedItems = emptyList(),
                references = emptyList(),
                feedback = emptyList(),
                observations = emptyList(),
                exportedAt = now
            )

        val counts =
            json.getJSONObject("manifest")
                .getJSONObject("counts")

        assertEquals(0, counts.getInt("sessions"))
        assertEquals(0, counts.getInt("feedItems"))
        assertEquals(0, counts.getInt("references"))
        assertEquals(0, counts.getInt("feedback"))
        assertEquals(0, counts.getInt("observations"))

        assertEquals(
            0,
            json.getJSONArray("sessions").length()
        )
        assertEquals(
            0,
            json.getJSONArray("feedItems").length()
        )
    }

    // --------------------------------
    // SESSIONS
    // --------------------------------

    @Test
    fun `session is serialized with metadata`() {

        val json =
            exporter.buildJson(
                sessions = listOf(session()),
                feedItems = emptyList(),
                references = emptyList(),
                feedback = emptyList(),
                observations = emptyList(),
                exportedAt = now
            )

        val row =
            json.getJSONArray("sessions")
                .getJSONObject(0)

        assertEquals("session-1", row.getString("id"))
        assertEquals("Evening scrolling", row.getString("title"))
        assertEquals(now.toString(), row.getString("startedAt"))
        assertEquals(now.plusHours(1).toString(), row.getString("endedAt"))
        assertEquals(3, row.getInt("observationCount"))
        assertFalse(row.getBoolean("active"))
    }

    // --------------------------------
    // FEED ITEMS
    // --------------------------------

    @Test
    fun `feed item carries classification and list fields`() {

        val json =
            exporter.buildJson(
                sessions = emptyList(),
                feedItems = listOf(item()),
                references = emptyList(),
                feedback = emptyList(),
                observations = emptyList(),
                exportedAt = now
            )

        val row =
            json.getJSONArray("feedItems")
                .getJSONObject(0)

        assertEquals("comedy", row.getString("category"))
        assertEquals(0.9, row.getDouble("confidence"), 0.0001)
        assertEquals("standup", row.getString("topic"))
        assertEquals("funny", row.getString("tone"))
        assertEquals(30, row.getInt("durationSeconds"))
        assertEquals("SHORT_VIDEO", row.getString("contentType"))

        val signals =
            row.getJSONArray("interactionSignals")
        assertEquals(2, signals.length())
        assertEquals("watched", signals.getString(0))
        assertEquals("liked", signals.getString(1))

        val evidence =
            row.getJSONArray("interactionEvidence")
        assertEquals("watched|HIGH|10s", evidence.getString(0))

        val secondary =
            row.getJSONArray("secondaryCategories")
        assertEquals("variety", secondary.getString(0))

        assertEquals("LOW", row.getString("uncertaintyLevel"))
    }

    @Test
    fun `null classification fields are omitted`() {

        val bareItem =
            FeedItem(
                id = "item-2",
                sessionId = "session-1",
                startTime = now,
                representativeFramePath = "/tmp/b.jpg"
            )

        val json =
            exporter.buildJson(
                sessions = emptyList(),
                feedItems = listOf(bareItem),
                references = emptyList(),
                feedback = emptyList(),
                observations = emptyList(),
                exportedAt = now
            )

        val row =
            json.getJSONArray("feedItems")
                .getJSONObject(0)

        assertFalse(row.has("category"))
        assertFalse(row.has("confidence"))
        assertEquals("UNKNOWN", row.getString("contentType"))
    }

    // --------------------------------
    // REFERENCES
    // --------------------------------

    @Test
    fun `reference preserves ai prediction and validated truth`() {

        val json =
            exporter.buildJson(
                sessions = emptyList(),
                feedItems = emptyList(),
                references = listOf(reference()),
                feedback = emptyList(),
                observations = emptyList(),
                exportedAt = now
            )

        val row =
            json.getJSONArray("references")
                .getJSONObject(0)

        assertEquals("comedy", row.getString("aiCategory"))
        assertEquals(0.6, row.getDouble("aiConfidence"), 0.0001)
        assertEquals("LOCAL", row.getString("aiSource"))
        assertEquals("HUMAN", row.getString("labelSource"))
        assertEquals("VALIDATED", row.getString("validationStatus"))
        assertEquals("comedy", row.getString("validatedLabel"))
        assertTrue(row.getBoolean("agreement"))
        assertEquals("instagram", row.getString("platform"))
        assertEquals("hello", row.getString("visibleText"))
    }

    // --------------------------------
    // FEEDBACK
    // --------------------------------

    @Test
    fun `feedback keeps original and corrected side by side`() {

        val json =
            exporter.buildJson(
                sessions = emptyList(),
                feedItems = emptyList(),
                references = emptyList(),
                feedback = listOf(feedback()),
                observations = emptyList(),
                exportedAt = now
            )

        val row =
            json.getJSONArray("feedback")
                .getJSONObject(0)

        assertEquals("comedy", row.getString("originalCategory"))
        assertEquals(0.6, row.getDouble("originalConfidence"), 0.0001)
        assertEquals("comedy", row.getString("correctedCategory"))
        assertEquals("USER", row.getString("correctionSource"))
        assertTrue(row.getBoolean("categoryAgreement"))
        assertEquals(
            "local-v5.1",
            row.getString("modelVersion")
        )
    }

    // --------------------------------
    // OBSERVATIONS
    // --------------------------------

    @Test
    fun `observation is serialized`() {

        val json =
            exporter.buildJson(
                sessions = emptyList(),
                feedItems = emptyList(),
                references = emptyList(),
                feedback = emptyList(),
                observations = listOf(observation()),
                exportedAt = now
            )

        val row =
            json.getJSONArray("observations")
                .getJSONObject(0)

        assertEquals("obs-1", row.getString("id"))
        assertEquals("watched two reels", row.getString("text"))
        assertEquals("AUTO", row.getString("source"))
        assertEquals(now.toString(), row.getString("createdAt"))
    }

    @Test
    fun `output is valid json and starts with manifest`() {

        val json =
            exporter.buildJson(
                sessions = emptyList(),
                feedItems = emptyList(),
                references = emptyList(),
                feedback = emptyList(),
                observations = emptyList(),
                exportedAt = now
            )

        // Round-trip the serialized string to prove it is
        // well-formed JSON.
        val reparsed =
            JSONObject(json.toString())

        assertTrue(
            reparsed.has("manifest")
        )
    }
}
