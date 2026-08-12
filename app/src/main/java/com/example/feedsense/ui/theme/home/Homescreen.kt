package com.example.feedsense.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.example.feedsense.model.ResearchSession
import com.example.feedsense.viewmodel.ProjectViewModel
import com.example.feedsense.viewmodel.SessionViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeScreen(
    projectViewModel: ProjectViewModel,
    sessionViewModel: SessionViewModel,
    onCreateProjectClick: () -> Unit,
    onOpenProjectsClick: () -> Unit,
    onContinueSessionClick: () -> Unit
) {

    val currentProject =
        projectViewModel.currentProject

    var sessions by remember {
        mutableStateOf<List<ResearchSession>>(emptyList())
    }

    LaunchedEffect(currentProject?.id) {

        if (currentProject != null) {

            sessionViewModel
                .getSessionsForProject(currentProject.id)
                .collectLatest {
                    sessions = it
                }

        } else {

            sessions = emptyList()
        }
    }

    val activeSession =
        sessions.firstOrNull { it.active }

    val latestSession =
        sessions.firstOrNull()

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Text(
                text = "FeedSense",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = "AI-powered Research Platform",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Text(
                text = "Welcome Back!",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "Your research workspace",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            HorizontalDivider()

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Text(
                text = "Current Research",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            if (currentProject == null) {

                Text(
                    text = "No Research Project Selected",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "Create or open a project to begin.",
                    style = MaterialTheme.typography.bodyMedium
                )

            } else {

                Text(
                    text = currentProject.title,
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = "Platform: ${currentProject.platform}"
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "Sessions: ${sessions.size}"
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "Observations: ${currentProject.feedItemCount}"
                )

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                if (activeSession != null) {

                    Text(
                        text = "🟢 ${activeSession.title} is running",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Started: ${activeSession.startedAt}"
                    )

                } else if (latestSession != null) {

                    Text(
                        text = "⚪ No session currently running",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Latest: ${latestSession.title}"
                    )

                } else {

                    Text(
                        text = "No sessions yet."
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(28.dp)
            )

            Button(
                onClick = onContinueSessionClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = currentProject != null
            ) {

                Text(
                    text = "▶ Continue Session"
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Button(
                onClick = onCreateProjectClick,
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text = "➕ New Research Project"
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            OutlinedButton(
                onClick = onOpenProjectsClick,
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text = "📂 Open Existing Projects"
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            OutlinedButton(
                onClick = {
                    // Settings later
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text = "⚙ Settings"
                )
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "Version 0.1.0",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )
        }
    }
}