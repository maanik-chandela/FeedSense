package com.example.feedsense.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.feedsense.viewmodel.ProjectViewModel

@Composable
fun HomeScreen(
    projectViewModel: ProjectViewModel,
    onCreateProjectClick: () -> Unit,
    onOpenProjectsClick: () -> Unit,
    onContinueSessionClick: () -> Unit
) {

    val currentProject = projectViewModel.currentProject

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

            // APP NAME
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

            // WELCOME
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

            // CURRENT PROJECT
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
                    text = "Sessions: ${currentProject.sessionCount}"
                )

                Spacer(
                    modifier = Modifier.height(4.dp)
                )

                Text(
                    text = "Observations: ${currentProject.feedItemCount}"
                )
            }

            Spacer(
                modifier = Modifier.height(28.dp)
            )

            // CONTINUE SESSION
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

            // NEW PROJECT
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

            // EXISTING PROJECTS
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

            // SETTINGS
            OutlinedButton(
                onClick = {
                    // Settings will be implemented later
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