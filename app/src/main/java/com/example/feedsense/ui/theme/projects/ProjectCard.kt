package com.example.feedsense.ui.theme.projects
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.feedsense.model.ResearchProject

@Composable
fun ProjectCard(
    project: ResearchProject,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },
        elevation = CardDefaults.cardElevation(4.dp)
    ) {

        Column(
            modifier = Modifier.padding(18.dp)
        ) {

            Text(
                project.title,
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                project.platform
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                "Sessions : ${project.sessionCount}"
            )

            Text(
                "Feed Items : ${project.feedItemCount}"
            )

        }

    }

}