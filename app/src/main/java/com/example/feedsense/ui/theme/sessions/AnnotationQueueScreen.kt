package com.example.feedsense.ui.theme.sessions

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.ui.components.FeedSenseCard
import com.example.feedsense.ui.components.EmptyState
import com.example.feedsense.viewmodel.AnnotationViewModel
import java.io.File

/*
 * Milestone 8A-2. The annotation QUEUE: a browsable list of
 * evaluation items grouped by annotation status
 * (UNREVIEWED / IN_PROGRESS / REVIEWED / DISPUTED). Each row
 * shows the representative frame and the AI category so the
 * evaluator can pick an item to open for annotation.
 */
@Composable
fun AnnotationQueueScreen(
    annotationViewModel: AnnotationViewModel,
    onOpenItem: (evaluationItemId: String, sessionId: String) -> Unit,
    onBack: () -> Unit
) {

    val queue by annotationViewModel.queue.collectAsState()
    val status by annotationViewModel.queueStatus.collectAsState()
    val loading by annotationViewModel.loading.collectAsState()

    LaunchedEffect(status) {
        annotationViewModel.loadQueue(status)
    }

    val statuses = listOf(
        EvaluationItem.STATUS_NOT_EVALUATED,
        EvaluationItem.STATUS_PARTIALLY_EVALUATED,
        EvaluationItem.STATUS_EVALUATED,
        EvaluationItem.STATUS_DISPUTED
    )

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
                    text = "Annotation Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Status filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statuses.forEach { s ->
                    OutlinedButton(
                        onClick = { annotationViewModel.loadQueue(s) },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            annotationViewModel.annotationStatusLabel(s),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            if (queue.isEmpty() && !loading) {
                FeedSenseCard(
                    modifier = Modifier.padding(16.dp)
                ) {
                    EmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = "No ${annotationViewModel.annotationStatusLabel(status)} items",
                        subtitle = "Items appear here once they are enqueued from a session."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(queue, key = { it.item.id }) { row ->
                        QueueRowCard(
                            evaluationItem = row.item,
                            category = row.feedItem?.category,
                            framePath = row.feedItem?.representativeFramePath,
                            statusLabel =
                                annotationViewModel.annotationStatusLabel(
                                    row.item.evaluationStatus
                                ),
                            onClick = {
                                onOpenItem(
                                    row.item.id,
                                    row.item.sessionId
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRowCard(
    evaluationItem: EvaluationItem,
    category: String?,
    framePath: String?,
    statusLabel: String,
    onClick: () -> Unit
) {
    FeedSenseCard(
        onClick = onClick,
        elevated = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (framePath != null) {
                val file = File(framePath)
                if (file.exists()) {
                    val bitmap = remember(framePath) {
                        BitmapFactory.decodeFile(framePath)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Thumbnail",
                            modifier = Modifier
                                .width(64.dp)
                                .height(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = CategoryCatalog.displayName(category),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "model: ${evaluationItem.modelVersion ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "status: $statusLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}