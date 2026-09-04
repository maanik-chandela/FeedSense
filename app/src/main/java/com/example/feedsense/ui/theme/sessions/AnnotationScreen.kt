package com.example.feedsense.ui.theme.sessions

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.feedsense.analysis.AnnotationTaxonomy
import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.model.CapturedFrame
import com.example.feedsense.model.GroundTruth
import com.example.feedsense.ui.components.FieldGroupSpacer
import com.example.feedsense.ui.components.FeedSenseCard
import com.example.feedsense.ui.components.LabeledDropdown
import com.example.feedsense.ui.components.SectionHeader
import com.example.feedsense.ui.components.TriStateToggle
import com.example.feedsense.viewmodel.AnnotationViewModel
import java.io.File

/*
 * Milestone 8A-2. The annotation FORM.
 *
 * Left/top: the EVIDENCE - the representative frame, the AI
 * prediction snapshot (category, confidence, model version,
 * platform, content type, duration, topic, tone, uncertainty,
 * skipped, interaction signals) and the session frames
 * captured within the item's [startTime, endTime] window.
 *
 * Right/bottom: the human GROUND-TRUTH form. Every field
 * reuses the frozen taxonomy via AnnotationTaxonomy. The
 * human's answers are saved as an INDEPENDENT record and
 * never overwrite the AI snapshot.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnnotationScreen(
    evaluationItemId: String,
    annotationViewModel: AnnotationViewModel,
    onBack: () -> Unit
) {

    val evidence by annotationViewModel.evidence.collectAsState()
    val draft by annotationViewModel.draft.collectAsState()
    val saveState by annotationViewModel.saveState.collectAsState()
    val error by annotationViewModel.error.collectAsState()
    val loading by annotationViewModel.loading.collectAsState()

    LaunchedEffect(evaluationItemId) {
        annotationViewModel.clearFeedback()
        annotationViewModel.loadItem(evaluationItemId)
    }

    val annotator = annotationViewModel.annotatorId

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Text(
                    text = "Annotation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    ) { padding ->

        if (loading && evidence == null) {
            FeedSenseCard(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Loading evidence...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val current = evidence
        if (current == null) {
            FeedSenseCard(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Text(
                    text = "No evidence could be loaded for this item.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            return@Scaffold
        }

        // Frames within the item window, collected at the
        // @Composable scope (LazyColumn's content scope is
        // not composable).
        val windowFrames by remember(current.feedItem) {
            current.framesInWindow
        }.collectAsState(initial = emptyList())

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // --------------------------------
            item {
                FieldGroupSpacer("EVIDENCE")
            }

            // Representative frame
            current.feedItem
                ?.takeIf { it.representativeFramePath.isNotBlank() }
                ?.let { feed ->
                    item {
                        val file = File(feed.representativeFramePath)
                        if (file.exists()) {
                            val bitmap = remember(feed.representativeFramePath) {
                                BitmapFactory.decodeFile(feed.representativeFramePath)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Representative frame",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                }

            // AI prediction snapshot
            current.prediction?.let { pred ->
                item {
                    FeedSenseCard(elevated = true) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "AI PREDICTION (frozen snapshot)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            EvidenceRow("Category", CategoryCatalog.displayName(pred.category))
                            pred.confidence?.let {
                                EvidenceRow("Confidence", "%.2f".format(it))
                            }
                            EvidenceRow("Model", pred.modelVersion ?: "unknown")
                            pred.platform?.let { EvidenceRow("Platform", it) }
                            pred.contentType?.let { EvidenceRow("Content type", it) }
                            pred.durationSeconds?.let {
                                EvidenceRow("Duration", "${it}s")
                            }
                            pred.topic?.let { EvidenceRow("Topic", it) }
                            pred.tone?.let { EvidenceRow("Tone", it) }
                            EvidenceRow("Uncertainty", pred.uncertaintyLevel)
                            EvidenceRow("Skipped", pred.skipped?.toString() ?: "unknown")
                            if (pred.interactionSignals.isNotEmpty()) {
                                EvidenceRow(
                                    "AI interactions",
                                    pred.interactionSignals.joinToString(", ")
                                )
                            }
                        }
                    }
                }
            }

            // Session frames in the item window
            if (windowFrames.isNotEmpty()) {
                item {
                    FeedSenseCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Frames in item window",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Best-effort: frames captured within this item's " +
                                    "[startTime, endTime]. The capture pipeline is not altered.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(windowFrames, key = { it.id }) { frame ->
                    FrameThumbnail(frame)
                }
            }

            item {
                HorizontalDivider()
            }

            // --------------------------------
            item {
                FieldGroupSpacer("HUMAN GROUND TRUTH")
            }

            // Primary category
            item {
                LabeledDropdown(
                    label = "Primary category",
                    value = draft.category,
                    options = AnnotationTaxonomy.primaryCategories(),
                    display = {
                        if (it == GroundTruth.AMBIGUITY_UNKNOWN) "UNKNOWN (no category)" else CategoryCatalog.displayName(it)
                    },
                    onSelect = { annotationViewModel.setCategory(it) }
                )
            }

            // Secondary categories
            item {
                SectionHeader(
                    title = "Secondary categories",
                    subtitle = "0..n. The primary is never repeated."
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnnotationTaxonomy.secondaryCategories()
                        .filter { it != draft.category }
                        .forEach { option ->
                            FilterChip(
                                selected = option in draft.secondaryCategories,
                                onClick = {
                                    annotationViewModel.toggleSecondaryCategory(option)
                                },
                                label = {
                                    Text(CategoryCatalog.displayName(option))
                                }
                            )
                        }
                }
            }

            // Ambiguity
            item {
                LabeledDropdown(
                    label = "Ambiguity",
                    value = draft.ambiguity,
                    options = AnnotationTaxonomy.ambiguities(),
                    onSelect = { annotationViewModel.setAmbiguity(it) }
                )
            }

            // Platform
            item {
                LabeledDropdown(
                    label = "Platform",
                    value = draft.platform,
                    options = AnnotationTaxonomy.platforms(),
                    placeholder = "Select platform",
                    onSelect = { annotationViewModel.setPlatform(it) }
                )
            }

            // Content type
            item {
                LabeledDropdown(
                    label = "Content type",
                    value = draft.contentType,
                    options = AnnotationTaxonomy.contentTypes(),
                    onSelect = { annotationViewModel.setContentType(it) }
                )
            }

            // Skipped
            item {
                TriStateToggle(
                    label = "Was this skipped?",
                    value = draft.skipped,
                    onCycle = { annotationViewModel.setSkipped(cycle(draft.skipped)) }
                )
            }

            // Interaction tri-states
            item {
                FieldGroupSpacer("INTERACTIONS (UNKNOWN unless evidence)")
            }
            AnnotationTaxonomy.interactions().forEach { signal ->
                item {
                    TriStateToggle(
                        label = signal.replaceFirstChar { it.uppercase() },
                        value = draftInteraction(draft, signal),
                        onCycle = {
                            annotationViewModel.cycleInteraction(signal)
                        }
                    )
                }
            }

            // Tone
            item {
                LabeledDropdown(
                    label = "Tone",
                    value = draft.tone,
                    options = AnnotationTaxonomy.tones(),
                    placeholder = "Select tone",
                    onSelect = { annotationViewModel.setTone(it) }
                )
            }

            // Topic
            item {
                OutlinedTextField(
                    value = draft.topic ?: "",
                    onValueChange = { annotationViewModel.setTopic(it) },
                    label = { Text("Topic (free-form)") },
                    placeholder = { Text("AI suggestion: ${current.prediction?.topic ?: "none"}") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Notes
            item {
                OutlinedTextField(
                    value = draft.notes ?: "",
                    onValueChange = { annotationViewModel.setNotes(it) },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )
            }

            if (error != null) {
                item {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            item {
                Text(
                    text = "Annotator: $annotator (pseudonymous)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (saveState.isNotEmpty()) {
                item {
                    Text(
                        text = saveState,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                Button(
                    onClick = { annotationViewModel.save() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save ground truth")
                }
            }

            item {
                OutlinedButton(
                    onClick = { annotationViewModel.markDisputed() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mark DISPUTED for review")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun EvidenceRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun FrameThumbnail(frame: CapturedFrame) {
    val file = File(frame.filePath)
    if (file.exists()) {
        val bitmap = remember(frame.filePath) {
            BitmapFactory.decodeFile(frame.filePath)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Frame",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        }
    }
}

private fun cycle(value: Boolean?): Boolean? {
    return when (value) {
        null -> true
        true -> false
        false -> null
    }
}

private fun draftInteraction(
    draft: GroundTruth,
    signal: String
): Boolean? {
    return when (signal) {
        GroundTruth.INTERACTION_LIKED -> draft.liked
        GroundTruth.INTERACTION_COMMENTED -> draft.commented
        GroundTruth.INTERACTION_SHARED -> draft.shared
        GroundTruth.INTERACTION_SAVED -> draft.saved
        GroundTruth.INTERACTION_FOLLOWED -> draft.followed
        GroundTruth.INTERACTION_PAUSED -> draft.paused
        GroundTruth.INTERACTION_PLAYING -> draft.playing
        else -> null
    }
}