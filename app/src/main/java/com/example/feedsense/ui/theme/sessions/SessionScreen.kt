package com.example.feedsense.ui.theme.sessions

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.ResearchSession
import com.example.feedsense.ui.components.AIStatusIndicator
import com.example.feedsense.ui.components.CategoryChip
import com.example.feedsense.ui.components.ConfidenceBadge
import com.example.feedsense.ui.components.EmptyState
import com.example.feedsense.ui.components.FeedSenseCard
import com.example.feedsense.ui.components.InteractionTag
import com.example.feedsense.ui.components.MetricCard
import com.example.feedsense.ui.components.PlatformChip
import com.example.feedsense.ui.components.StatusChip
import com.example.feedsense.ui.theme.ConfidenceHigh
import com.example.feedsense.ui.theme.ConfidenceLow
import com.example.feedsense.ui.theme.ConfidenceMedium
import com.example.feedsense.ui.theme.StatusActive
import com.example.feedsense.ui.theme.StatusInactive
import com.example.feedsense.viewmodel.SessionViewModel
import kotlinx.coroutines.flow.collectLatest
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
    onExport: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onBack: () -> Unit
) {
    var session by remember { mutableStateOf<ResearchSession?>(null) }
    var feedItems by remember { mutableStateOf<List<FeedItem>>(emptyList()) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showScreenshotFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionId, projectId) {
        sessionViewModel.getSessionsForProject(projectId)
            .collectLatest { sessions ->
                session = sessions.firstOrNull { it.id == sessionId }
            }
    }

    LaunchedEffect(sessionId) {
        sessionViewModel.getResearchLogItems(sessionId)
            .collectLatest { feedItems = it }
    }

    val currentSession = session ?: run {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Loading session...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val needsReview = feedItems.count { it.needsReview }
    val aiItems = feedItems.count { it.source == FeedItem.SOURCE_AI }
    val manualItems = feedItems.count { it.source == FeedItem.SOURCE_MANUAL }
    val totalWatch = feedItems.sumOf { it.durationSeconds }
    val likedCount = feedItems.count { it.researcherLiked }

    Scaffold(
        topBar = {
            SessionTopBar(
                session = currentSession,
                onBack = onBack,
                onToggleCapture = {
                    if (currentSession.active) {
                        onStopScreenCapture()
                    } else {
                        onStartScreenCapture()
                    }
                },
                onStartCapture = onStartScreenCapture,
                onStopCapture = onStopScreenCapture,
                isCapturing = currentSession.active
            )
        },
        floatingActionButton = {
            if (currentSession.active) {
                FloatingActionButton(
                    onClick = { showManualDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Observation")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                SessionMetricsRow(
                    itemCount = feedItems.size,
                    aiCount = aiItems,
                    manualCount = manualItems,
                    totalWatch = totalWatch,
                    needsReview = needsReview
                )
            }

            if (currentSession.active) {
                item {
                    CaptureControls(
                        isCapturing = currentSession.active,
                        onStart = onStartScreenCapture,
                        onStop = onStopScreenCapture,
                        onEndSession = {
                            onStopScreenCapture()
                            sessionViewModel.endSession(currentSession.id)
                        },
                        onExport = onExport,
                        onAnalytics = onOpenAnalytics
                    )
                }
            }

            item {
                ResearchLogHeader(
                    count = feedItems.size,
                    needsReview = needsReview,
                    onOpenReview = onOpenReview
                )
            }

            if (feedItems.isEmpty()) {
                item {
                    FeedSenseCard {
                        EmptyState(
                            icon = Icons.Default.CameraAlt,
                            title = "No research log entries yet",
                            subtitle = "Start screen capture to begin logging content."
                        )
                    }
                }
            } else {
                itemsIndexed(feedItems, key = { _, item -> item.id }) { index, item ->
                    androidx.compose.animation.AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = tween(
                                durationMillis = 300,
                                delayMillis = index * 50,
                                easing = LinearEasing
                            )
                        ) + fadeIn(
                            animationSpec = tween(
                                durationMillis = 400,
                                delayMillis = index * 50
                            )
                        )
                    ) {
                        ResearchLogRow(
                            sequenceNumber = index + 1,
                            item = item,
                            onClick = { showScreenshotFor = item.id },
                            onToggleLike = {
                                sessionViewModel.toggleResearcherFlag(
                                    item.id,
                                    "researcherLiked",
                                    !item.researcherLiked
                                )
                            }
                        )
                    }
                }
            }

            if (!currentSession.active) {
                item {
                    OutlinedButton(
                        onClick = { sessionViewModel.reopenSession(currentSession.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run This Session Again")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showManualDialog) {
        ManualObservationDialog(
            sessionId = sessionId,
            sessionViewModel = sessionViewModel,
            onDismiss = { showManualDialog = false }
        )
    }

    showScreenshotFor?.let { itemId ->
        feedItems.firstOrNull { it.id == itemId }?.let { item ->
            ReviewItemDialog(
                item = item,
                sessionViewModel = sessionViewModel,
                onDismiss = { showScreenshotFor = null }
            )
        }
    }
}

@Composable
private fun SessionTopBar(
    session: ResearchSession,
    onBack: () -> Unit,
    onToggleCapture: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    isCapturing: Boolean
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(200.dp)
                    )
                    StatusChip(
                        label = if (session.active) "ACTIVE" else "ENDED",
                        active = session.active
                    )
                }

                IconButton(onClick = { onStartCapture() }) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Start Capture",
                        tint = if (session.active) StatusActive else StatusInactive
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SessionMetricsRow(
    itemCount: Int,
    aiCount: Int,
    manualCount: Int,
    totalWatch: Int,
    needsReview: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard(
            label = "Items",
            value = "$itemCount",
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "AI",
            value = "$aiCount",
            modifier = Modifier.weight(1f),
            valueColor = ConfidenceHigh
        )
        MetricCard(
            label = "Manual",
            value = "$manualCount",
            modifier = Modifier.weight(1f),
            valueColor = ConfidenceMedium
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard(
            label = "Watch Time",
            value = formatDuration(totalWatch),
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "Review",
            value = "$needsReview",
            modifier = Modifier.weight(1f),
            valueColor = if (needsReview > 0) ConfidenceLow else ConfidenceHigh
        )
    }
}

@Composable
private fun CaptureControls(
    isCapturing: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEndSession: () -> Unit,
    onExport: () -> Unit,
    onAnalytics: () -> Unit
) {
    FeedSenseCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Session Controls",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Capture")
                }

                OutlinedButton(
                    onClick = onStop,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Capture")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEndSession,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("End Session")
                }

                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Export Data")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onAnalytics,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Session Analysis")
            }
        }
    }
}

@Composable
private fun ResearchLogHeader(
    count: Int,
    needsReview: Int,
    onOpenReview: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Research Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$count content items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (needsReview > 0) {
            OutlinedButton(
                onClick = onOpenReview,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Review ($needsReview)")
            }
        }
    }
}

@Composable
private fun ResearchLogRow(
    sequenceNumber: Int,
    item: FeedItem,
    onClick: () -> Unit,
    onToggleLike: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    FeedSenseCard(onClick = onClick) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#$sequenceNumber",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp)
                    )

                    item.category?.let {
                        CategoryChip(category = it)
                    } ?: run {
                        Text(
                            text = "Unclassified",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (item.source == FeedItem.SOURCE_MANUAL) {
                        StatusChip(label = "MANUAL", active = false)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    item.confidence?.let {
                        ConfidenceBadge(confidence = it)
                    }

                    IconButton(
                        onClick = onToggleLike,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Like",
                            modifier = Modifier.size(16.dp),
                            tint = if (item.researcherLiked) ConfidenceLow else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item.platform?.let { PlatformChip(platform = it) }

                Text(
                    text = formatDuration(item.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "${item.frameCount} frames",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (item.needsReview) {
                    StatusChip(label = "REVIEW", active = true)
                }

                if (item.mixedContent) {
                    StatusChip(label = "MIXED", active = false)
                }
            }

            item.topic?.let { topic ->
                if (topic.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = topic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = tween(300, easing = LinearEasing)
                ) + fadeIn(
                    animationSpec = tween(300, easing = LinearEasing)
                ),
                exit = shrinkVertically(
                    animationSpec = tween(200, easing = LinearEasing)
                ) + fadeOut(
                    animationSpec = tween(200, easing = LinearEasing)
                )
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Spacer(modifier = Modifier.height(8.dp))

                    item.interactionSignals.forEach { signal ->
                        InteractionTag(signal = signal)
                    }

                    item.classificationReason?.let { reason ->
                        if (reason.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    item.researcherNotes?.let { notes ->
                        if (notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Notes: $notes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualObservationDialog(
    sessionId: String,
    sessionViewModel: SessionViewModel,
    onDismiss: () -> Unit
) {
    var category by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var liked by remember { mutableStateOf(false) }
    var commented by remember { mutableStateOf(false) }
    var shared by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Manual Observation",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    placeholder = { Text("e.g. sports, motivation, comedy") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic") },
                    placeholder = { Text("e.g. cricket match, workout routine") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    placeholder = { Text("Describe what you observed...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3
                )

                Text(
                    text = "Interactions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InteractionToggleButton(label = "Liked", checked = liked, onToggle = { liked = !liked })
                    InteractionToggleButton(label = "Commented", checked = commented, onToggle = { commented = !commented })
                    InteractionToggleButton(label = "Shared", checked = shared, onToggle = { shared = !shared })
                    InteractionToggleButton(label = "Saved", checked = saved, onToggle = { saved = !saved })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    sessionViewModel.addManualObservation(
                        sessionId = sessionId,
                        category = category.ifBlank { null },
                        topic = topic.ifBlank { null },
                        notes = notes,
                        liked = liked,
                        commented = commented,
                        shared = shared,
                        saved = saved
                    )
                    onDismiss()
                },
                enabled = notes.isNotBlank() || category.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun InteractionToggleButton(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onToggle),
        shape = RoundedCornerShape(8.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewItemDialog(
    item: FeedItem,
    sessionViewModel: SessionViewModel,
    onDismiss: () -> Unit
) {
    var category by remember { mutableStateOf(item.category ?: "") }
    var topic by remember { mutableStateOf(item.topic ?: "") }
    var tone by remember { mutableStateOf(item.tone ?: "") }
    var notes by remember { mutableStateOf(item.researcherNotes ?: "") }
    var liked by remember { mutableStateOf(item.researcherLiked) }
    var commented by remember { mutableStateOf(item.researcherCommented) }
    var shared by remember { mutableStateOf(item.researcherShared) }
    var saved by remember { mutableStateOf(item.researcherSaved) }
    var showCategoryPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (item.category == null) "Review & Classify" else "Correct Item",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Screenshot preview
                if (item.representativeFramePath.isNotBlank()) {
                    val file = File(item.representativeFramePath)
                    if (file.exists()) {
                        val bitmap = remember(item.representativeFramePath) {
                            BitmapFactory.decodeFile(item.representativeFramePath)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Screenshot",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }

                // Category selector
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    placeholder = { Text("e.g. sports, comedy, motivation") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showCategoryPicker = !showCategoryPicker }) {
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = "Browse categories",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )

                // Category picker grid
                AnimatedVisibility(
                    visible = showCategoryPicker,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CATEGORY_DISPLAY_NAMES.forEach { (key, name) ->
                                FilterChip(
                                    selected = category == key,
                                    onClick = {
                                        category = key
                                        showCategoryPicker = false
                                    },
                                    label = {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Topic
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic") },
                    placeholder = { Text("e.g. cricket match, workout routine") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Tone
                OutlinedTextField(
                    value = tone,
                    onValueChange = { tone = it },
                    label = { Text("Tone") },
                    placeholder = { Text("e.g. informative, entertaining") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    placeholder = { Text("Add researcher notes...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                // Existing signals
                if (item.interactionSignals.isNotEmpty()) {
                    Text(
                        text = "Detected Signals",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item.interactionSignals.forEach { InteractionTag(signal = it) }
                    }
                }

                // Interaction toggles
                Text(
                    text = "Your Interactions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    InteractionToggleButton(label = "Liked", checked = liked, onToggle = { liked = !liked })
                    InteractionToggleButton(label = "Commented", checked = commented, onToggle = { commented = !commented })
                    InteractionToggleButton(label = "Shared", checked = shared, onToggle = { shared = !shared })
                    InteractionToggleButton(label = "Saved", checked = saved, onToggle = { saved = !saved })
                }

                // Classification reason
                item.classificationReason?.let { reason ->
                    if (reason.isNotBlank()) {
                        DetailRow("AI Reason", reason)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    sessionViewModel.submitCorrection(
                        feedItemId = item.id,
                        category = category.ifBlank { "other" },
                        topic = topic.ifBlank { null },
                        tone = tone.ifBlank { null },
                        notes = notes.ifBlank { null },
                        liked = liked,
                        commented = commented,
                        shared = shared,
                        saved = saved
                    )
                    onDismiss()
                },
                enabled = true,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatDuration(seconds: Int): String {
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

/*
 * Subset of the full CategoryCatalog keys used in the
 * review dialog category picker. Grouped by domain for
 * quick scanning.
 */
private val CATEGORY_DISPLAY_NAMES = listOf(
    "sports" to "Sports",
    "comedy" to "Comedy",
    "motivation" to "Motivation",
    "education" to "Education",
    "news" to "News",
    "music" to "Music",
    "gaming" to "Gaming",
    "fitness" to "Fitness",
    "technology" to "Technology",
    "fashion" to "Fashion",
    "food" to "Food",
    "travel" to "Travel",
    "finance" to "Finance",
    "science" to "Science",
    "health" to "Health",
    "relationships" to "Relationships",
    "lifestyle" to "Lifestyle",
    "entertainment" to "Entertainment",
    "film" to "Film",
    "anime" to "Anime",
    "politics" to "Politics",
    "business" to "Business",
    "podcasts" to "Podcasts",
    "other" to "Other",
    "unknown" to "Unknown"
)
