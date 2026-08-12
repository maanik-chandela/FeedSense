package com.example.feedsense.ui.theme.sessions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.feedsense.viewmodel.SessionViewModel

@Composable
fun ObservationScreen(
    sessionId: String,
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit
) {

    var observationText by remember {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        Text(
            text = "Record Observation",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Capture what you observed during this research session."
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        OutlinedTextField(
            value = observationText,
            onValueChange = {
                observationText = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Observation")
            },
            placeholder = {
                Text("Describe what you observed...")
            },
            minLines = 5
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Button(
            onClick = {

                sessionViewModel.addObservation(
                    sessionId = sessionId,
                    text = observationText
                )

                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = observationText.isNotBlank()
        ) {
            Text("Save Observation")
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}