package com.example.feedsense.ui.theme.project
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.feedsense.viewmodel.ProjectViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun CreateProjectScreen(
    onBackClick: () -> Unit,
    projectViewModel: ProjectViewModel,
) {

    var projectName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf("Instagram") }
    var researchQuestion by remember { mutableStateOf("") }
    var hypothesis by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        TextButton(
            onClick = onBackClick
        ) {
            Text("← Back")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Create Research Project",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = projectName,
            onValueChange = {
                projectName = it
            },
            label = {
                Text("Project Title")
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = description,
            onValueChange = {
                description = it
            },
            label = {
                Text("Description")
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = researchQuestion,
            onValueChange = {
                researchQuestion = it
            },
            label = {
                Text("Research Question")
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = hypothesis,
            onValueChange = {
                hypothesis = it
            },
            label = {
                Text("Hypothesis (Optional)")
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = owner,
            onValueChange = {
                owner = it
            },
            label = {
                Text("Researcher Name")
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Platform",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        PlatformOption(
            name = "Instagram",
            selectedPlatform = selectedPlatform
        ) {
            selectedPlatform = "Instagram"
        }

        PlatformOption(
            name = "YouTube",
            selectedPlatform = selectedPlatform
        ) {
            selectedPlatform = "YouTube"
        }

        PlatformOption(
            name = "Reddit",
            selectedPlatform = selectedPlatform
        ) {
            selectedPlatform = "Reddit"
        }

        PlatformOption(
            name = "X (Twitter)",
            selectedPlatform = selectedPlatform
        ) {
            selectedPlatform = "X (Twitter)"
        }

        PlatformOption(
            name = "Other",
            selectedPlatform = selectedPlatform
        ) {
            selectedPlatform = "Other"
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {

                projectViewModel.createProject(
                    title = projectName,
                    description = description,
                    researchQuestion = researchQuestion,
                    hypothesis = hypothesis,
                    platform = selectedPlatform,
                    owner = owner
                )

                onBackClick()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Project")
        }

    }

}

@Composable
fun PlatformOption(
    name: String,
    selectedPlatform: String,
    onSelected: () -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {

        RadioButton(
            selected = selectedPlatform == name,
            onClick = onSelected
        )

        Text(
            text = name,
            modifier = Modifier.padding(top = 12.dp)
        )

    }

}