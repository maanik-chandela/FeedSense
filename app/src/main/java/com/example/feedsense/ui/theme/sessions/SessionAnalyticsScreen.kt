package com.example.feedsense.ui.theme.sessions

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.feedsense.ui.components.FeedSenseCard
import com.example.feedsense.ui.components.MetricCard
import com.example.feedsense.ui.components.SectionHeader
import com.example.feedsense.ui.components.platformColor
import com.example.feedsense.ui.theme.ConfidenceHigh
import com.example.feedsense.ui.theme.ConfidenceLow
import com.example.feedsense.ui.theme.ConfidenceMedium
import com.example.feedsense.ui.theme.Primary
import com.example.feedsense.ui.theme.PrimaryLight
import com.example.feedsense.ui.theme.SurfaceVariant
import com.example.feedsense.viewmodel.SessionAnalytics
import com.example.feedsense.viewmodel.SessionViewModel

@Composable
fun SessionAnalyticsScreen(
    sessionId: String,
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit
) {
    var analytics by remember { mutableStateOf<SessionAnalytics?>(null) }

    LaunchedEffect(sessionId) {
        sessionViewModel.getSessionAnalytics(sessionId) { analytics = it }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(48.dp)) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Session Analysis",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Research metrics for this session",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val data = analytics

            if (data == null) {
                item {
                    Text(
                        text = "Loading analytics...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "Total Items",
                            value = "${data.totalItems}",
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "AI",
                            value = "${data.aiItems}",
                            modifier = Modifier.weight(1f),
                            valueColor = ConfidenceHigh
                        )
                        MetricCard(
                            label = "Manual",
                            value = "${data.manualItems}",
                            modifier = Modifier.weight(1f),
                            valueColor = ConfidenceMedium
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "Watch Time",
                            value = formatTime(data.totalWatchSeconds),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Active Time",
                            value = formatTime(data.activeWatchSeconds),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (data.categoryBreakdown.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Category Breakdown")
                        Spacer(modifier = Modifier.height(8.dp))
                        FeedSenseCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                data.categoryBreakdown.entries.forEachIndexed { index, (category, count) ->
                                    val fraction = if (data.totalItems > 0) {
                                        count.toFloat() / data.totalItems.toFloat()
                                    } else 0f
                                    val animatedFraction by animateFloatAsState(
                                        targetValue = fraction,
                                        animationSpec = tween(
                                            durationMillis = 600 + index * 100,
                                            easing = LinearEasing
                                        ),
                                        label = "cat_bar"
                                    )

                                    Text(
                                        text = category.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { animatedFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Primary,
                                        trackColor = SurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$count items (${(fraction * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (index < data.categoryBreakdown.size - 1) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                if (data.platformBreakdown.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Platform Breakdown")
                        Spacer(modifier = Modifier.height(8.dp))
                        FeedSenseCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                data.platformBreakdown.entries.forEachIndexed { index, (platform, count) ->
                                    val fraction = if (data.totalItems > 0) {
                                        count.toFloat() / data.totalItems.toFloat()
                                    } else 0f
                                    val animatedFraction by animateFloatAsState(
                                        targetValue = fraction,
                                        animationSpec = tween(
                                            durationMillis = 600 + index * 100,
                                            easing = LinearEasing
                                        ),
                                        label = "plat_bar"
                                    )

                                    Text(
                                        text = platform,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { animatedFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = platformColor(platform),
                                        trackColor = SurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$count items (${(fraction * 100).toInt()}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (index < data.platformBreakdown.size - 1) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                if (data.contentTypeBreakdown.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Content Type")
                        Spacer(modifier = Modifier.height(8.dp))
                        FeedSenseCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                data.contentTypeBreakdown.entries.forEach { (type, count) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = type.replace("_", " "),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "$count",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                if (data.uncertaintyBreakdown.isNotEmpty()) {
                    item {
                        SectionHeader(title = "AI Confidence Distribution")
                        Spacer(modifier = Modifier.height(8.dp))
                        UncertaintyDonutChart(breakdown = data.uncertaintyBreakdown)
                    }
                }

                item {
                    SectionHeader(title = "Researcher Interactions")
                    Spacer(modifier = Modifier.height(8.dp))
                    FeedSenseCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            InteractionStatRow(
                                icon = Icons.Default.Favorite,
                                label = "Liked",
                                count = data.likedCount,
                                total = data.totalItems
                            )
                            InteractionStatRow(
                                icon = Icons.Default.ThumbUp,
                                label = "Commented",
                                count = data.commentedCount,
                                total = data.totalItems
                            )
                            InteractionStatRow(
                                icon = Icons.Default.Share,
                                label = "Shared",
                                count = data.sharedCount,
                                total = data.totalItems
                            )
                            InteractionStatRow(
                                icon = Icons.Default.ThumbUp,
                                label = "Saved",
                                count = data.savedCount,
                                total = data.totalItems
                            )
                            InteractionStatRow(
                                icon = Icons.Default.ThumbUp,
                                label = "Skipped",
                                count = data.skippedCount,
                                total = data.totalItems
                            )
                            InteractionStatRow(
                                icon = Icons.Default.Analytics,
                                label = "Review needed",
                                count = data.needsReview,
                                total = data.totalItems
                            )
                            InteractionStatRow(
                                icon = Icons.Default.Analytics,
                                label = "Manual corrections",
                                count = data.manualCorrections,
                                total = data.totalItems
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun InteractionStatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    total: Int
) {
    val fraction = if (total > 0) count.toFloat() / total.toFloat() else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(500, easing = LinearEasing),
        label = "interaction_bar"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { animatedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Primary,
                trackColor = SurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Primary
        )
    }
}

@Composable
private fun UncertaintyDonutChart(
    breakdown: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val total = breakdown.values.sum().toFloat()
    if (total == 0f) return

    val high = breakdown["HIGH"] ?: 0
    val medium = breakdown["MEDIUM"] ?: 0
    val low = breakdown["LOW"] ?: 0

    val highFraction by animateFloatAsState(
        targetValue = high / total,
        animationSpec = tween(800, easing = LinearEasing),
        label = "high_anim"
    )
    val mediumFraction by animateFloatAsState(
        targetValue = medium / total,
        animationSpec = tween(800, easing = LinearEasing),
        label = "med_anim"
    )

    FeedSenseCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(140.dp)) {
                    val strokeWidth = 20f
                    val diameter = size.minDimension - strokeWidth
                    val radius = diameter / 2
                    val topLeft = Offset(
                        (size.width - diameter) / 2,
                        (size.height - diameter) / 2
                    )
                    val arcSize = Size(diameter, diameter)

                    drawArc(
                        color = ConfidenceLow,
                        startAngle = -90f,
                        sweepAngle = highFraction * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ConfidenceMedium,
                        startAngle = -90f + highFraction * 360f,
                        sweepAngle = mediumFraction * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ConfidenceHigh,
                        startAngle = -90f + (highFraction + mediumFraction) * 360f,
                        sweepAngle = (1f - highFraction - mediumFraction) * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${total.toInt()}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "items",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ChartLegend(color = ConfidenceHigh, label = "Low", count = low)
                ChartLegend(color = ConfidenceMedium, label = "Medium", count = medium)
                ChartLegend(color = ConfidenceLow, label = "High", count = high)
            }
        }
    }
}

@Composable
private fun ChartLegend(
    color: Color,
    label: String,
    count: Int
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(10.dp),
            shape = CircleShape,
            color = color
        ) {}
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(seconds: Int): String {
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes < 60) "${minutes}m ${secs}s"
    else {
        val hours = minutes / 60
        val mins = minutes % 60
        "${hours}h ${mins}m"
    }
}
