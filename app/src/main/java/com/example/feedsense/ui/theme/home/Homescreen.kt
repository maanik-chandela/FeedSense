package com.example.feedsense.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.feedsense.model.ResearchSession
import com.example.feedsense.ui.components.AIStatusIndicator
import com.example.feedsense.ui.components.FeedSenseCard
import com.example.feedsense.ui.components.MetricCard
import com.example.feedsense.ui.components.SectionHeader
import com.example.feedsense.ui.components.StatusChip
import com.example.feedsense.ui.theme.Background
import com.example.feedsense.ui.theme.Primary
import com.example.feedsense.ui.theme.PrimaryDark
import com.example.feedsense.ui.theme.StatusActive
import com.example.feedsense.ui.theme.StatusInactive
import com.example.feedsense.ui.theme.SurfaceVariant
import com.example.feedsense.viewmodel.ProjectViewModel
import com.example.feedsense.viewmodel.SessionViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeScreen(
    projectViewModel: ProjectViewModel,
    sessionViewModel: SessionViewModel,
    onCreateProjectClick: () -> Unit,
    onOpenProjectsClick: () -> Unit,
    onContinueSessionClick: () -> Unit,
    onOpenModelPerformanceClick: () -> Unit,
    onOpenSettingsClick: () -> Unit = {},
    onOpenAnalyticsClick: () -> Unit = {},
    onOpenSessionHistoryClick: (String, String) -> Unit = { _, _ -> },
    onOpenAnnotationQueueClick: () -> Unit = {}
) {

    val currentProject = projectViewModel.currentProject

    var sessions by remember {
        mutableStateOf<List<ResearchSession>>(emptyList())
    }

    LaunchedEffect(currentProject?.id) {
        if (currentProject != null) {
            sessionViewModel
                .getSessionsForProject(currentProject.id)
                .collectLatest { sessions = it }
        } else {
            sessions = emptyList()
        }
    }

    val activeSession = sessions.firstOrNull { it.active }
    val latestSession = sessions.firstOrNull()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // --------------------------------
            // HEADER
            // --------------------------------

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "FeedSense Logo",
                        modifier = Modifier.size(40.dp),
                        tint = Primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "FeedSense",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Personal Behavioral Research Laboratory",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AIStatusIndicator(
                    isActive = activeSession != null,
                    modelVersion = "local-v6.0"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --------------------------------
            // CURRENT PROJECT
            // --------------------------------

            if (currentProject != null) {
                FeedSenseCard(elevated = true) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentProject.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (currentProject.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = currentProject.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            StatusChip(
                                label = if (activeSession != null) "ACTIVE" else "IDLE",
                                active = activeSession != null
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetricCard(
                                label = "Sessions",
                                value = "${sessions.size}",
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                label = "Feed Items",
                                value = "${currentProject.feedItemCount}",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            } else {
                // No project selected
                FeedSenseCard {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Research Project Selected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Create or open a project to begin research.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --------------------------------
            // ACTIVE SESSION CARD
            // --------------------------------

            if (activeSession != null) {
                FeedSenseCard(elevated = true) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(StatusActive)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SESSION ACTIVE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StatusActive,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = activeSession.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "FeedSense is observing screen content",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onContinueSessionClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Active Session")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --------------------------------
            // RESEARCH SNAPSHOT (when project exists)
            // --------------------------------

            if (currentProject != null && sessions.isNotEmpty()) {
                SectionHeader(
                    title = "Research Snapshot",
                    subtitle = "Overview of current research activity"
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        label = "Observed Time",
                        value = "${sessions.sumOf { it.observationCount }} obs",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        label = "Content Items",
                        value = "${currentProject.feedItemCount}",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // --------------------------------
            // QUICK ACTIONS
            // --------------------------------

            SectionHeader(title = "Quick Actions")

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.Add,
                    label = "New Project",
                    onClick = onCreateProjectClick,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    icon = Icons.Default.FolderOpen,
                    label = "Projects",
                    onClick = onOpenProjectsClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.Psychology,
                    label = "AI Review",
                    onClick = onOpenModelPerformanceClick,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    icon = Icons.Default.Analytics,
                    label = "Analytics",
                    onClick = onOpenAnalyticsClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.History,
                    label = "History",
                    onClick = {
                        if (currentProject != null) {
                            onOpenSessionHistoryClick(
                                currentProject.id,
                                currentProject.title
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    onClick = onOpenSettingsClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            QuickActionCard(
                icon = Icons.Default.CheckCircle,
                label = "Annotation Queue",
                onClick = onOpenAnnotationQueueClick,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // --------------------------------
            // RECENT ACTIVITY
            // --------------------------------

            if (latestSession != null && sessions.isNotEmpty()) {
                SectionHeader(
                    title = "Recent Sessions",
                    subtitle = "Latest research activity"
                )

                Spacer(modifier = Modifier.height(12.dp))

                sessions.take(3).forEach { session ->
                    FeedSenseCard(
                        modifier = Modifier.padding(bottom = 8.dp),
                        onClick = {
                            onOpenSessionHistoryClick(
                                session.projectId,
                                currentProject?.title ?: "Research"
                            )
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = session.startedAt.toString().take(10),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            StatusChip(
                                label = if (session.active) "ACTIVE" else "ENDED",
                                active = session.active
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --------------------------------
            // FEEDSENSE IDENTITY
            // --------------------------------

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "FEEDSENSE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
                )
                Text(
                    text = "Observe. Understand. Learn.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// --------------------------------
// QUICK ACTION CARD
// --------------------------------

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
