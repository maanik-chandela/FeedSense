package com.example.feedsense.ui.theme.sessions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.feedsense.model.ResearchSession
import com.example.feedsense.viewmodel.SessionViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SessionHistoryScreen(
    projectId: String,
    projectTitle: String,
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit
) {

    var sessions by remember {
        mutableStateOf<List<ResearchSession>>(emptyList())
    }

    LaunchedEffect(projectId) {

        sessionViewModel
            .getSessionsForProject(projectId)
            .collectLatest {
                sessions = it
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        TextButton(
            onClick = onBack
        ) {
            Text("← Back")
        }

        Text(
            text = projectTitle,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Session History",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        HorizontalDivider()

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        if (sessions.isEmpty()) {

            Text(
                text = "No sessions yet."
            )

        } else {

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                items(sessions) { session ->

                    SessionCard(
                        session = session,
                        onEndSession = {
                            sessionViewModel.endSession(
                                session.id
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: ResearchSession,
    onEndSession: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {

        Text(
            text = session.title,
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = "Started: ${session.startedAt}"
        )

        Text(
            text = if (session.endedAt == null)
                "Status: Active"
            else
                "Ended: ${session.endedAt}"
        )

        Text(
            text = "Observations: ${session.observationCount}"
        )

        if (session.active) {

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Button(
                onClick = onEndSession
            ) {
                Text("End Session")
            }
        }
    }
}