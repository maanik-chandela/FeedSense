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
import androidx.compose.material3.OutlinedButton
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
    onBack: () -> Unit,
    onSessionClick: (ResearchSession) -> Unit
) {
    var sessions by remember {
        mutableStateOf<List<ResearchSession>>(emptyList())
    }

    LaunchedEffect(projectId) {

        sessionViewModel
            .getSessionsForProject(projectId)
            .collectLatest { result ->
                sessions = result
            }
    }

    val activeSession =
        sessions.firstOrNull { it.active }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        // --------------------------------
        // BACK
        // --------------------------------

        TextButton(
            onClick = onBack
        ) {
            Text("← Back")
        }

        // --------------------------------
        // PROJECT TITLE
        // --------------------------------

        Text(
            text = projectTitle,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = "Sessions",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        // --------------------------------
        // CURRENT SESSION
        // --------------------------------

        Text(
            text = "Current Session",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        if (activeSession != null) {

            Text(
                text = "🟢 ${activeSession.title}",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "Started: ${activeSession.startedAt}"
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Button(
                onClick = {
                    onSessionClick(activeSession)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Current Session")
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            /*
             * For now this only ends the database session.
             *
             * Screen-capture service lifecycle will be handled
             * separately so ending a session does not introduce
             * navigation/capture coupling here.
             */
            OutlinedButton(
                onClick = {
                    sessionViewModel.endSession(
                        activeSession.id
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("⏹ End Session")
            }

        } else {

            Text(
                text = "⚪ No Session Currently Running"
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Button(
                onClick = {

                    sessionViewModel.startSession(
                        projectId = projectId,
                        title = "Session ${sessions.size + 1}"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("＋ Start New Session")
            }
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        HorizontalDivider()

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        // --------------------------------
        // HISTORY
        // --------------------------------

        Text(
            text = "Session History",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        if (sessions.isEmpty()) {

            Text(
                text = "No sessions yet."
            )

        } else {

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                items(
                    items = sessions,
                    key = { it.id }
                ) { session ->

                    SessionCard(
                        session = session,
                        onOpenSession = {
                            onSessionClick(session)
                        },
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
    onOpenSession: () -> Unit,
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

        if (session.endedAt != null) {

            Text(
                text = "Ended: ${session.endedAt}"
            )

        } else {

            Text(
                text = "Status: 🟢 Active"
            )
        }

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = "Observations: ${session.observationCount}"
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        OutlinedButton(
            onClick = onOpenSession
        ) {
            Text("Open Session")
        }

        if (session.active) {

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Button(
                onClick = onEndSession
            ) {
                Text("End Session")
            }
        }
    }

}
