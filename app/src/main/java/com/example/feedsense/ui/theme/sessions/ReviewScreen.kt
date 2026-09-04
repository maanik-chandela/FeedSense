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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.ui.components.AIStatusIndicator
import com.example.feedsense.ui.components.ConfidenceBadge
import com.example.feedsense.ui.components.EmptyState
import com.example.feedsense.ui.components.FeedSenseCard
import com.example.feedsense.ui.components.InteractionTag
import com.example.feedsense.ui.components.MetricCard
import com.example.feedsense.ui.components.PlatformChip
import com.example.feedsense.ui.components.SectionHeader
import com.example.feedsense.viewmodel.ReviewViewModel
import java.io.File
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    reviewViewModel: ReviewViewModel,
    onBack: () -> Unit
) {

    var pendingReviews by remember {
        mutableStateOf<List<LabeledReference>>(emptyList())
    }
    var validatedCount by remember { mutableIntStateOf(0) }
    var agreementCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        reviewViewModel.getPendingReviews()
            .collectLatest { pendingReviews = it }
    }

    LaunchedEffect(Unit) {
        reviewViewModel.getValidatedCount { validatedCount = it }
        reviewViewModel.getValidatedAgreementCount { agreementCount = it }
    }

    val agreementPercent = if (validatedCount > 0) {
        agreementCount * 100 / validatedCount
    } else {
        0
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // --------------------------------
            // HEADER
            // --------------------------------

            item {
                Spacer(modifier = Modifier.height(48.dp))

                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Review Center",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Content the local AI was not sure about. Your corrections teach the local model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        label = "Pending",
                        value = "${pendingReviews.size}",
                        modifier = Modifier.weight(1f),
                        valueColor = MaterialTheme.colorScheme.secondary
                    )
                    MetricCard(
                        label = "Validated",
                        value = "$validatedCount",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "AI Agreement",
                        value = "$agreementPercent%",
                        modifier = Modifier.weight(1f),
                        valueColor = com.example.feedsense.ui.theme.ConfidenceHigh
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            if (pendingReviews.isEmpty()) {
                item {
                    FeedSenseCard {
                        EmptyState(
                            icon = Icons.Default.CheckCircle,
                            title = "All caught up!",
                            subtitle = "No content waiting for review."
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Back")
                    }
                }
            } else {

                items(pendingReviews, key = { "review_${it.id}" }) { reference ->
                    ReviewCardWithContext(
                        reference = reference,
                        reviewViewModel = reviewViewModel
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// --------------------------------
// REVIEW CARD (WITH FEED ITEM CONTEXT)
// --------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCardWithContext(
    reference: LabeledReference,
    reviewViewModel: ReviewViewModel
) {

    var item by remember { mutableStateOf<FeedItem?>(null) }

    LaunchedEffect(reference.id) {
        reference.feedItemId?.let { feedItemId ->
            reviewViewModel.getItemById(feedItemId = feedItemId) { item = it }
        }
    }

    val file = File(reference.filePath)

    val categoryCandidates = remember(reference) {
        buildList {
            reference.candidateCategories.forEach {
                if (!contains(it)) add(it)
            }
            if (isEmpty()) {
                CategoryCatalog.keys.forEach { add(it) }
            }
        }
    }

    var topic by remember(reference.id) { mutableStateOf(reference.topic ?: "") }
    var tone by remember(reference.id) { mutableStateOf(reference.tone ?: "") }

    FeedSenseCard(elevated = true) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Frame image
            if (file.exists()) {
                val bitmap = remember(reference.filePath) {
                    BitmapFactory.decodeFile(reference.filePath)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Frame awaiting review",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }

            // AI prediction header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = reference.aiCategory ?: "Unclassified",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                reference.aiConfidence?.let { ConfidenceBadge(confidence = it) }
            }

            // Metadata chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                reference.modelVersion?.let {
                    PlatformChip(platform = "Model: $it")
                }
                reference.platform?.takeIf { it.isNotBlank() }?.let {
                    PlatformChip(platform = it)
                }
                item?.let { feedItem ->
                    PlatformChip(platform = "${feedItem.durationSeconds}s")
                    feedItem.interactionSignals.takeIf { it.isNotEmpty() }?.let { signals ->
                        signals.forEach { InteractionTag(signal = it) }
                    }
                }
            }

            // AI reasoning
            reference.aiReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Why the AI thinks this",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Topic / tone corrections
            Text(
                text = "Topic (optional correction)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. cooking tutorial") },
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Tone (optional correction)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = tone,
                onValueChange = { tone = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. humorous") },
                shape = RoundedCornerShape(10.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // Category selection
            Text(
                text = "What category best describes this?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categoryCandidates.forEach { category ->
                    Button(
                        onClick = {
                            reviewViewModel.submitLabel(
                                referenceId = reference.id,
                                label = category,
                                correctedTopic = topic,
                                correctedTone = tone
                            )
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = category, style = MaterialTheme.typography.labelLarge)
                    }
                }

                Button(
                    onClick = {
                        reviewViewModel.submitLabel(
                            referenceId = reference.id,
                            label = CATEGORY_MIXED,
                            correctedTopic = topic,
                            correctedTone = tone
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Mixed", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Action buttons
            OutlinedButton(
                onClick = { reviewViewModel.confirmAi(reference.id) },
                enabled = reference.aiCategory != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("AI was correct")
            }

            OutlinedButton(
                onClick = { reviewViewModel.reject(reference.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Reject (not classifiable)")
            }

            OutlinedButton(
                onClick = { reviewViewModel.skip(reference.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Skip review")
            }
        }
    }
}

private const val CATEGORY_MIXED = "mixed"
