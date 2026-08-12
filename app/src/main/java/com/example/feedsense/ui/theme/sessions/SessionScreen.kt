package com.example.feedsense.ui.theme.sessions

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.feedsense.model.CapturedFrame
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.ResearchObservation
import com.example.feedsense.model.ResearchSession
import com.example.feedsense.viewmodel.SessionViewModel
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
import java.io.File

@Composable
fun SessionScreen(
    sessionId: String,
    projectId: String,
    sessionViewModel: SessionViewModel,
    onStartScreenCapture: () -> Unit,
    onStopScreenCapture: () -> Unit,
    onRecordObservation: () -> Unit,
    onOpenReview: () -> Unit,
    onBack: () -> Unit
) {

    var session by remember {
        mutableStateOf<ResearchSession?>(null)
    }

    var observations by remember {
        mutableStateOf<List<ResearchObservation>>(
            emptyList()
        )
    }

    var capturedFrames by remember {
        mutableStateOf<List<CapturedFrame>>(
            emptyList()
        )
    }

    var feedItems by remember {
        mutableStateOf<List<FeedItem>>(
            emptyList()
        )
    }

    // --------------------------------
    // LOAD SESSION
    // --------------------------------

    LaunchedEffect(
        sessionId,
        projectId
    ) {

        sessionViewModel
            .getSessionsForProject(
                projectId
            )
            .collectLatest { sessions ->

                session =
                    sessions.firstOrNull {
                        it.id == sessionId
                    }
            }
    }

    // --------------------------------
    // LOAD OBSERVATIONS
    // --------------------------------

    LaunchedEffect(
        sessionId
    ) {

        sessionViewModel
            .getObservationsForSession(
                sessionId
            )
            .collectLatest {

                observations = it
            }
    }

    // --------------------------------
    // LOAD CAPTURED FRAMES
    // --------------------------------

    LaunchedEffect(
        sessionId
    ) {

        sessionViewModel
            .getFramesForSession(
                sessionId
            )
            .collectLatest {

                capturedFrames = it
            }
    }

    // --------------------------------
    // LOAD FEED ITEMS
    // --------------------------------

    LaunchedEffect(
        sessionId
    ) {

        sessionViewModel
            .getFeedItemsForSession(
                sessionId
            )
            .collectLatest {

                feedItems = it
            }
    }

    val currentSession =
        session

    // --------------------------------
    // LOADING
    // --------------------------------

    if (currentSession == null) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
        ) {

            Text(
                text = "Loading Session...",
                style =
                    MaterialTheme.typography
                        .headlineSmall
            )

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )

            OutlinedButton(
                onClick = onBack
            ) {

                Text(
                    text = "Back"
                )
            }
        }

        return
    }

    // --------------------------------
    // FRAME ANALYSIS COUNTS
    // --------------------------------

    val totalFrames =
        capturedFrames.size

    val pendingFrames =
        capturedFrames.count {
            it.analysisStatus == "PENDING"
        }

    val processingFrames =
        capturedFrames.count {
            it.analysisStatus == "PROCESSING"
        }

    val analyzedFrames =
        capturedFrames.count {
            it.analysisStatus == "ANALYZED"
        }

    val failedFrames =
        capturedFrames.count {
            it.analysisStatus == "FAILED"
        }

    val needsReviewFrames =
        capturedFrames.count {
            it.analysisStatus == "NEEDS_REVIEW"
        }

    val reviewedFrames =
        capturedFrames.count {
            it.analysisStatus == "REVIEWED"
        }

    val latestAnalyzedFrame =
        capturedFrames
            .firstOrNull {
                it.analysisStatus == "ANALYZED" &&
                        !it.analysisResult.isNullOrBlank()
            }

    // --------------------------------
    // SESSION CONTENT
    // --------------------------------

    val listState =
        rememberLazyListState()

    LazyColumn(
        state = listState,

        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),

        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        // --------------------------------
        // HEADER
        // --------------------------------

        item {

            Text(
                text = "← Current Session",
                style =
                    MaterialTheme.typography
                        .titleMedium
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text = currentSession.title,
                style =
                    MaterialTheme.typography
                        .headlineMedium
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text =
                    if (currentSession.active) {
                        "🟢 Session Running"
                    } else {
                        "⚪ Session Completed"
                    },

                style =
                    MaterialTheme.typography
                        .titleLarge
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text = "Started",
                style =
                    MaterialTheme.typography
                        .titleMedium
            )

            Text(
                text =
                    currentSession
                        .startedAt
                        .toString()
            )

            if (
                currentSession.endedAt != null
            ) {

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                Text(
                    text = "Ended",
                    style =
                        MaterialTheme.typography
                            .titleMedium
                )

                Text(
                    text =
                        currentSession
                            .endedAt
                            .toString()
                )
            }

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            HorizontalDivider()
        }

        // --------------------------------
        // OBSERVATIONS HEADER
        // --------------------------------

        item {

            Text(
                text =
                    "Observations (${observations.size})",

                style =
                    MaterialTheme.typography
                        .titleLarge
            )
        }

        // --------------------------------
        // OBSERVATIONS
        // --------------------------------

        if (observations.isEmpty()) {

            item {

                Text(
                    text =
                        "No observations recorded yet.",

                    style =
                        MaterialTheme.typography
                            .bodyMedium
                )
            }

        } else {

            items(
                items = observations,
                key = {
                    "observation_${it.id}"
                }
            ) { observation ->

                ObservationCard(
                    observation = observation
                )
            }
        }

        // --------------------------------
        // CAPTURED FRAMES HEADER
        // --------------------------------

        item {

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            HorizontalDivider()

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text =
                    "Captured Frames ($totalFrames)",

                style =
                    MaterialTheme.typography
                        .titleLarge
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    if (capturedFrames.isEmpty()) {
                        "No screen frames captured yet."
                    } else {
                        "Frames captured during this session."
                    },

                style =
                    MaterialTheme.typography
                        .bodyMedium
            )
        }

        // --------------------------------
        // ANALYSIS STATUS
        // --------------------------------

        item {

            if (capturedFrames.isNotEmpty()) {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = 8.dp
                            )
                ) {

                    Text(
                        text = "Frame Analysis",
                        style =
                            MaterialTheme.typography
                                .titleMedium
                    )

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "Pending: $pendingFrames"
                    )

                    Text(
                        text =
                            "Processing: $processingFrames"
                    )

                    Text(
                        text =
                            "Analyzed: $analyzedFrames"
                    )

                    Text(
                        text =
                            "Failed: $failedFrames"
                    )

                    Text(
                        text =
                            "Needs Review: $needsReviewFrames"
                    )

                    Text(
                        text =
                            "Reviewed: $reviewedFrames"
                    )

                    if (
                        latestAnalyzedFrame != null
                    ) {

                        Spacer(
                            modifier =
                                Modifier.height(8.dp)
                        )

                        Text(
                            text =
                                "Latest Analysis",
                            style =
                                MaterialTheme.typography
                                    .titleSmall
                        )

                        Spacer(
                            modifier =
                                Modifier.height(4.dp)
                        )

                        val latestText =
                            extractVisibleText(
                                latestAnalyzedFrame
                                    .analysisResult
                            )

                        if (
                            !latestText.isNullOrBlank()
                        ) {

                            Text(
                                text =
                                    "Detected Text",
                                style =
                                    MaterialTheme.typography
                                        .titleSmall
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(4.dp)
                            )

                            Text(
                                text = latestText,
                                style =
                                    MaterialTheme.typography
                                        .bodyMedium
                            )

                        } else {

                            Text(
                                text =
                                    latestAnalyzedFrame
                                        .analysisResult
                                        ?: "No result"
                            )
                        }
                    }
                }
            }
        }

        // --------------------------------
        // CAPTURED FRAMES
        // --------------------------------

        if (capturedFrames.isEmpty()) {

            item {

                Text(
                    text =
                        "Start screen capture to begin collecting frames.",

                    style =
                        MaterialTheme.typography
                            .bodyMedium
                )
            }

        } else {

            items(
                items = capturedFrames,
                key = {
                    "frame_${it.id}"
                }
            ) { frame ->

                CapturedFrameCard(
                    frame = frame
                )
            }
        }

        // --------------------------------
        // CONTENT ITEMS
        // --------------------------------

        item {

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            HorizontalDivider()

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text =
                    "Content Items (${feedItems.size})",

                style =
                    MaterialTheme.typography
                        .titleLarge
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    if (feedItems.isEmpty()) {
                        "Grouped content pieces will appear " +
                                "after frames are analyzed."
                    } else {
                        "Grouped content pieces detected " +
                                "from captured frames."
                    },

                style =
                    MaterialTheme.typography
                        .bodyMedium
            )
        }

        if (feedItems.isEmpty()) {

            item {

                Text(
                    text =
                        "No content items yet.",
                    style =
                        MaterialTheme.typography
                            .bodyMedium
                )
            }

        } else {

            items(
                items = feedItems,
                key = {
                    "item_${it.id}"
                }
            ) { item ->

                FeedItemCard(
                    item = item
                )
            }
        }

        // --------------------------------
        // SCREEN CAPTURE BUTTON
        // --------------------------------

        item {

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            if (currentSession.active) {

                Button(
                    onClick =
                        onStartScreenCapture,

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            "▶ Start Screen Capture"
                    )
                }
            }
        }

        // --------------------------------
        // OBSERVATION BUTTON
        // --------------------------------

        item {

            Button(
                onClick =
                    onRecordObservation,

                modifier =
                    Modifier.fillMaxWidth(),

                enabled =
                    currentSession.active
            ) {

                Text(
                    text =
                        "＋ Record Observation"
                )
            }
        }

        // --------------------------------
        // END / REOPEN
        // --------------------------------

        item {

            if (currentSession.active) {

                OutlinedButton(
                    onClick = {

                        sessionViewModel
                            .endSession(
                                currentSession.id
                            )
                    },

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            "⏹ End Session"
                    )
                }

            } else {

                OutlinedButton(
                    onClick = {

                        sessionViewModel
                            .reopenSession(
                                currentSession.id
                            )
                    },

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            "▶ Run This Session Again"
                    )
                }
            }
        }

        // --------------------------------
        // REVIEW UNCLASSIFIED FRAMES
        // --------------------------------

        item {

            if (needsReviewFrames > 0) {

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Button(
                    onClick = onOpenReview,

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            "Review Unclassified Frames " +
                                    "($needsReviewFrames)"
                    )
                }
            }
        }

        // --------------------------------
        // BACK
        // --------------------------------

        item {

            OutlinedButton(
                onClick = onBack,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Back to Sessions"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(24.dp)
            )
        }
    }
}

// ========================================
// OBSERVATION CARD
// ========================================

@Composable
private fun ObservationCard(
    observation: ResearchObservation
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
    ) {

        Text(
            text =
                observation.text,

            style =
                MaterialTheme.typography
                    .bodyLarge
        )

        Spacer(
            modifier =
                Modifier.height(6.dp)
        )

        Text(
            text =
                (
                        if (
                            observation.source ==
                            ResearchObservation.SOURCE_AUTO
                        ) {
                            "[AI] "
                        } else {
                            ""
                        }
                        ) + observation.createdAt
                            .toString(),

            style =
                MaterialTheme.typography
                    .bodySmall
        )
    }
}

// ========================================
// FEED ITEM CARD
// ========================================

@Composable
private fun FeedItemCard(
    item: FeedItem
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
    ) {

        Text(
            text =
                (item.category
                    ?: "Unclassified") +
                        " · " +
                        item.contentType,

            style =
                MaterialTheme.typography
                    .titleSmall
        )

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        item.topic?.let { topic ->

            if (topic.isNotEmpty()) {

                Text(
                    text =
                        "Topic: $topic",

                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }
        }

        item.tone?.let { tone ->

            if (tone.isNotEmpty()) {

                Text(
                    text =
                        "Tone: $tone",

                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }
        }

        Text(
            text =
                "Watched: ${item.durationSeconds}s" +
                        if (item.skipped) {
                            " · Skipped"
                        } else {
                            ""
                        },

            style =
                MaterialTheme.typography
                    .bodySmall
        )

        Text(
            text =
                "Frames: ${item.frameCount}",

            style =
                MaterialTheme.typography
                    .bodySmall
        )

        item.confidence?.let { confidence ->

            Text(
                text =
                    "Confidence: " +
                            "%.0f%%".format(
                                confidence * 100.0
                            ),

                style =
                    MaterialTheme.typography
                        .bodySmall
            )
        }

        if (
            item.interactionSignals.isNotEmpty()
        ) {

            Text(
                text =
                    "Signals: " +
                            item.interactionSignals
                                .joinToString(", "),

                style =
                    MaterialTheme.typography
                        .bodySmall
            )
        }

        HorizontalDivider(
            modifier =
                Modifier.padding(
                    top = 8.dp
                )
        )
    }
}

// ========================================
// CAPTURED FRAME CARD
// ========================================

@Composable
private fun CapturedFrameCard(
    frame: CapturedFrame
) {

    val file =
        File(
            frame.filePath
        )

    val visibleText =
        remember(
            frame.analysisResult
        ) {
            extractVisibleText(
                frame.analysisResult
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
    ) {

        if (file.exists()) {

            val bitmap =
                remember(
                    frame.filePath
                ) {

                    BitmapFactory.decodeFile(
                        frame.filePath
                    )
                }

            if (bitmap != null) {

                Image(
                    bitmap =
                        bitmap.asImageBitmap(),

                    contentDescription =
                        "Captured frame",

                    modifier =
                        Modifier.fillMaxWidth()
                )
            }

        } else {

            Text(
                text =
                    "Frame file not found",

                style =
                    MaterialTheme.typography
                        .bodyMedium
            )
        }

        Spacer(
            modifier =
                Modifier.height(6.dp)
        )

        Text(
            text =
                frame.capturedAt
                    .toString(),

            style =
                MaterialTheme.typography
                    .bodySmall
        )

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        Text(
            text =
                "Status: ${frame.analysisStatus}",

            style =
                MaterialTheme.typography
                    .bodySmall
        )

        val contentCategory =
            remember(
                frame.analysisResult
            ) {
                extractContentCategory(
                    frame.analysisResult
                )
            }

        val confidenceLabel =
            remember(
                frame.analysisResult
            ) {
                extractConfidenceLabel(
                    frame.analysisResult
                )
            }

        val sourceLabel =
            remember(
                frame.analysisResult
            ) {
                extractSourceLabel(
                    frame.analysisResult
                )
            }

        if (
            contentCategory != null ||
            confidenceLabel != null ||
            sourceLabel != null
        ) {

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            contentCategory?.let {

                Text(
                    text =
                        "Category: $it",

                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }

            confidenceLabel?.let {

                Text(
                    text =
                        "Confidence: $it",

                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }

            sourceLabel?.let {

                Text(
                    text =
                        "Source: $it",

                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }
        }

        if (
            !visibleText.isNullOrBlank()
        ) {

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text =
                    "Detected Text",

                style =
                    MaterialTheme.typography
                        .titleSmall
            )

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    visibleText,

                style =
                    MaterialTheme.typography
                        .bodyMedium
            )

        } else if (
            !frame.analysisResult.isNullOrBlank()
        ) {

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Text(
                text =
                    frame.analysisResult!!,

                style =
                    MaterialTheme.typography
                        .bodySmall
            )
        }

        HorizontalDivider(
            modifier =
                Modifier.padding(
                    top = 8.dp
                )
        )
    }
}

// ========================================
// OCR TEXT EXTRACTION
// ========================================

private fun extractVisibleText(
    analysisResult: String?
): String? {

    if (
        analysisResult.isNullOrBlank()
    ) {
        return null
    }

    return try {

        val json =
            JSONObject(
                analysisResult
            )

        if (
            !json.has("visibleText") ||
            json.isNull("visibleText")
        ) {

            null

        } else {

            json
                .optString(
                    "visibleText",
                    ""
                )
                .trim()
                .takeIf {
                    it.isNotEmpty()
                }
        }

    } catch (
        exception: Exception
    ) {

        null
    }
}

// ========================================
// CLASSIFICATION EXTRACTION
// ========================================

private fun extractContentCategory(
    analysisResult: String?
): String? {

    if (
        analysisResult.isNullOrBlank()
    ) {
        return null
    }

    return try {

        val json =
            JSONObject(
                analysisResult
            )

        if (
            !json.has("contentCategory") ||
            json.isNull("contentCategory")
        ) {

            null

        } else {

            json
                .optString(
                    "contentCategory",
                    ""
                )
                .trim()
                .takeIf {
                    it.isNotEmpty()
                }
        }

    } catch (
        exception: Exception
    ) {

        null
    }
}

private fun extractConfidenceLabel(
    analysisResult: String?
): String? {

    if (
        analysisResult.isNullOrBlank()
    ) {
        return null
    }

    return try {

        val json =
            JSONObject(
                analysisResult
            )

        if (
            !json.has("confidence") ||
            json.isNull("confidence")
        ) {

            null

        } else {

            val confidence =
                json.optDouble(
                    "confidence",
                    -1.0
                )

            if (confidence < 0.0) {
                null
            } else {
                "%.0f%%".format(
                    confidence * 100.0
                )
            }
        }

    } catch (
        exception: Exception
    ) {

        null
    }
}

private fun extractSourceLabel(
    analysisResult: String?
): String? {

    if (
        analysisResult.isNullOrBlank()
    ) {
        return null
    }

    return try {

        val json =
            JSONObject(
                analysisResult
            )

        if (
            !json.has("source") ||
            json.isNull("source")
        ) {

            null

        } else {

            json
                .optString(
                    "source",
                    ""
                )
                .trim()
                .takeIf {
                    it.isNotEmpty()
                }
        }

    } catch (
        exception: Exception
    ) {

        null
    }
}
