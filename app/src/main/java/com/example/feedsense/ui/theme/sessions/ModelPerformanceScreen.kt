package com.example.feedsense.ui.theme.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.feedsense.model.ModelPerformanceStats
import com.example.feedsense.ui.components.ConfidenceBadge
import com.example.feedsense.ui.components.FeedSenseCard
import com.example.feedsense.ui.components.MetricCard
import com.example.feedsense.ui.components.SectionHeader
import com.example.feedsense.ui.theme.ConfidenceHigh
import com.example.feedsense.ui.theme.ConfidenceLow
import com.example.feedsense.ui.theme.ConfidenceMedium
import com.example.feedsense.ui.theme.Primary
import com.example.feedsense.ui.theme.StatusActive
import com.example.feedsense.ui.theme.StatusInfo
import com.example.feedsense.viewmodel.ModelPerformanceViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ModelPerformanceScreen(
    viewModel: ModelPerformanceViewModel,
    onBack: () -> Unit
) {

    var stats by remember { mutableStateOf<ModelPerformanceStats?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        viewModel.stats.collectLatest { stats = it }
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
                    text = "Model Intelligence",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "How the local AI is performing, evaluated against human-reviewed labels.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val current = stats

            if (current == null) {
                item {
                    Text(
                        text = "Computing...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {

                // --------------------------------
                // TOP METRICS
                // --------------------------------

                item {
                    SectionHeader(title = "Classification Summary")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "Total",
                            value = "${current.totalClassifications}",
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "High Conf",
                            value = "${current.highConfidence}",
                            modifier = Modifier.weight(1f),
                            valueColor = ConfidenceHigh
                        )
                        MetricCard(
                            label = "Accuracy",
                            value = "%.1f%%".format(current.accuracy),
                            modifier = Modifier.weight(1f),
                            valueColor = if (current.accuracy >= 80) ConfidenceHigh else ConfidenceMedium
                        )
                    }
                }

                // --------------------------------
                // REVIEW STATS
                // --------------------------------

                item {
                    FeedSenseCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SectionHeader(title = "Review Pipeline")

                            StatRow(label = "Human reviewed", value = "${current.reviewed}")
                            StatRow(label = "AI agreed with human", value = "${current.correct}")
                            StatRow(label = "AI corrected by human", value = "${current.corrected}")
                            StatRow(label = "Rejected", value = "${current.rejected}")
                            StatRow(label = "Uncertain (in review)", value = "${current.uncertain}")
                        }
                    }
                }

                // --------------------------------
                // OBSERVED VALIDATION
                // --------------------------------

                item {
                    FeedSenseCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = StatusActive
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Observed Validation Accuracy",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "How often humans agreed with the AI on reviewed samples (calibration signal).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                            StatRow(label = "Topic accuracy", value = "%.1f%%".format(current.topicAccuracy))
                            StatRow(label = "Tone accuracy", value = "%.1f%%".format(current.toneAccuracy))
                            StatRow(label = "Uncertainty rate", value = "%.1f%%".format(current.uncertaintyRate))
                            StatRow(label = "Learning examples", value = "${current.feedbackCount}")
                        }
                    }
                }

                // --------------------------------
                // PERSONALIZATION
                // --------------------------------

                item {
                    FeedSenseCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Personalization",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "Cross-session user knowledge from validated references. History is never rewritten.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                            StatRow(
                                label = "Validated examples",
                                value = "${current.personalization.totalValidatedExamples}"
                            )
                            StatRow(
                                label = "Top categories",
                                value = current.personalization.topCategories.joinToString(", ") { "${it.key} (${it.count})" }
                            )
                            StatRow(
                                label = "Top platforms",
                                value = current.personalization.topPlatforms.joinToString(", ") { "${it.key} (${it.count})" }
                            )
                            StatRow(
                                label = "Top topics",
                                value = current.personalization.topTopics.joinToString(", ") { "${it.key} (${it.count})" }
                            )
                            StatRow(
                                label = "User corrections",
                                value = "${current.personalization.userCorrections}"
                            )
                            StatRow(
                                label = "User confirmations",
                                value = "${current.personalization.userConfirmations}"
                            )
                            StatRow(
                                label = "Correction rate",
                                value = "%.1f%%".format(current.personalization.correctionRate)
                            )
                        }
                    }
                }

                // --------------------------------
                // CLOUD COST
                // --------------------------------

                item {
                    FeedSenseCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = StatusInfo
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Cloud / Cost Control",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "The cloud is the teacher, never the default. Every request is gated.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                            StatRow(label = "Cloud fallback count", value = "${current.cloudFallbackCount}")
                            StatRow(label = "Cloud fallback rate", value = "%.1f%%".format(current.cloudFallbackRate))
                            StatRow(label = "Estimated cost (INR)", value = "%.2f".format(current.estimatedCloudCostRupees))
                        }
                    }
                }

                // --------------------------------
                // PIPELINE HONESTY
                // --------------------------------

                item {
                    FeedSenseCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Psychology,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Pipeline Honesty",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                            StatRow(label = "Local acceptance rate", value = "%.1f%%".format(current.localAcceptanceRate))
                            StatRow(label = "Review rate", value = "%.1f%%".format(current.reviewRate))
                            StatRow(label = "Correction rate", value = "%.1f%%".format(current.correctionRate))
                            StatRow(
                                label = "Interaction accuracy",
                                value = current.interactionAccuracy?.let { "%.1f%%".format(it) } ?: "Not yet measured"
                            )
                        }
                    }
                }

                // --------------------------------
                // PER MODEL VERSION
                // --------------------------------

                item {
                    FeedSenseCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Per Model Version",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (current.byModelVersion.isEmpty()) {
                                Text(
                                    text = "No reviewed references yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                current.byModelVersion.forEach { version ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = version.modelVersion,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${version.correct}/${version.reviewed} \u00B7 ${"%.1f%%".format(version.accuracy)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --------------------------------
                // ACCURACY BY CATEGORY
                // --------------------------------

                item {
                    FeedSenseCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Accuracy by Category",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (current.byCategory.isEmpty()) {
                                Text(
                                    text = "No reviewed references yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                current.byCategory.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = row.category,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${row.correct}/${row.reviewed} \u00B7 ${"%.1f%%".format(row.accuracy)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --------------------------------
                // ACCURACY BY PLATFORM
                // --------------------------------

                item {
                    FeedSenseCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Accuracy by Platform",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (current.byPlatform.isEmpty()) {
                                Text(
                                    text = "No reviewed references yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                current.byPlatform.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = row.platform,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${row.correct}/${row.reviewed} \u00B7 ${"%.1f%%".format(row.accuracy)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --------------------------------
                // CONFUSION
                // --------------------------------

                item {
                    FeedSenseCard {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = com.example.feedsense.ui.theme.StatusWarning
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Most Confused Categories",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (current.confusion.isEmpty()) {
                                Text(
                                    text = "No corrections recorded yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                current.confusion.take(5).forEach { pair ->
                                    Text(
                                        text = "'${pair.predicted}' \u2192 '${pair.actual}' \u00B7 ${pair.count}x",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                // --------------------------------
                // REFRESH
                // --------------------------------

                item {
                    OutlinedButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refresh Data")
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatRow(
    label: String,
    value: Int
) {
    StatRow(label = label, value = value.toString())
}
