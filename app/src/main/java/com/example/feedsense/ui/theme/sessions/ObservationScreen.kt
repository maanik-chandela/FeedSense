package com.example.feedsense.ui.theme.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.feedsense.ui.theme.ChipSports
import com.example.feedsense.ui.theme.ChipComedy
import com.example.feedsense.ui.theme.ChipMotivation
import com.example.feedsense.ui.theme.ChipMusic
import com.example.feedsense.ui.theme.ChipEducation
import com.example.feedsense.ui.theme.ChipNews
import com.example.feedsense.ui.theme.ChipGaming
import com.example.feedsense.ui.theme.ChipTechnology
import com.example.feedsense.ui.theme.ChipFood
import com.example.feedsense.ui.theme.ChipDefault
import com.example.feedsense.viewmodel.SessionViewModel

private val OBSERVATION_CATEGORIES = listOf(
    "sports" to ChipSports,
    "comedy" to ChipComedy,
    "motivation" to ChipMotivation,
    "music" to ChipMusic,
    "educational" to ChipEducation,
    "news" to ChipNews,
    "gaming" to ChipGaming,
    "technology" to ChipTechnology,
    "food" to ChipFood,
    "other" to ChipDefault
)

private val INTERACTION_OPTIONS = listOf(
    "liked", "skipped", "commented",
    "shared", "saved", "followed",
    "paused", "replayed"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObservationScreen(
    sessionId: String,
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit
) {
    var observationText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var topic by remember { mutableStateOf("") }
    var interactions by remember { mutableStateOf(setOf<String>()) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Record Observation",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Capture what you observed during this research session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Category",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OBSERVATION_CATEGORIES.forEach { (category, color) ->
                    Surface(
                        modifier = Modifier.clickable {
                            selectedCategory = if (selectedCategory == category) null else category
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedCategory == category) {
                            color.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = category.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedCategory == category) {
                                color
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Topic (optional)") },
                placeholder = { Text("e.g. cricket match, workout routine") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = observationText,
                onValueChange = { observationText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Observation") },
                placeholder = { Text("Describe what you observed...") },
                minLines = 5,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Interactions",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                INTERACTION_OPTIONS.forEach { interaction ->
                    val selected = interaction in interactions
                    Surface(
                        modifier = Modifier.clickable {
                            interactions = if (selected) {
                                interactions - interaction
                            } else {
                                interactions + interaction
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = interaction.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    sessionViewModel.addManualObservation(
                        sessionId = sessionId,
                        category = selectedCategory,
                        topic = topic.ifBlank { null },
                        notes = observationText,
                        liked = "liked" in interactions,
                        commented = "commented" in interactions,
                        shared = "shared" in interactions,
                        saved = "saved" in interactions
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = observationText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Observation")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
