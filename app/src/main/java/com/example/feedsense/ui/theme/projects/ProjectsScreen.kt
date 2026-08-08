package com.example.feedsense.ui.theme.projects
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.feedsense.model.ResearchProject
import com.example.feedsense.viewmodel.ProjectViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ProjectsScreen(
    projectViewModel: ProjectViewModel,
    onBack: () -> Unit
) {

    var projects by remember {
        mutableStateOf<List<ResearchProject>>(emptyList())
    }

    LaunchedEffect(Unit) {

        projectViewModel.allProjects.collectLatest {
            projects = it
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
            text = "Research Projects",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(16.dp))

        if (projects.isEmpty()) {

            Text(
                text = "No Projects Yet"
            )

        } else {

            LazyColumn {

                items(projects) { project ->

                    ProjectCard(
                        project = project,
                        onClick = {
                            projectViewModel.selectProject(project)
                            onBack()
                        }
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )
                }

            }

        }

    }

}