package com.example.feedsense.ui.theme.sessions

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.feedsense.analysis.TextHeuristicClassifier
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.viewmodel.ReviewViewModel
import java.io.File
import kotlinx.coroutines.flow.collectLatest

// --------------------------------
// REVIEW SCREEN
// --------------------------------
//
// Milestone 7B.
//
// Human review of frames the local pipeline could
// not confidently classify.
//
// Each card shows the captured frame and the local
// AI prediction. The reviewer picks the best
// category. That answer becomes validated
// reference/training data.
//

@Composable
fun ReviewScreen(
    reviewViewModel: ReviewViewModel,
    onBack: () -> Unit
) {

    var pendingReviews by remember {
        mutableStateOf<List<LabeledReference>>(
            emptyList()
        )
    }

    var validatedCount by remember {
        mutableIntStateOf(0)
    }

    var agreementCount by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(Unit) {

        reviewViewModel
            .getPendingReviews()
            .collectLatest {
                pendingReviews = it
            }
    }

    LaunchedEffect(Unit) {

        reviewViewModel.getValidatedCount {
            validatedCount = it
        }

        reviewViewModel.getValidatedAgreementCount {
            agreementCount = it
        }
    }

    val agreementPercent =
        if (validatedCount > 0) {
            agreementCount * 100 / validatedCount
        } else {
            0
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),

        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        // --------------------------------
        // HEADER
        // --------------------------------

        item {

            Text(
                text = "← Review",
                style =
                    MaterialTheme.typography
                        .titleMedium
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text = "Unclassified Frames",
                style =
                    MaterialTheme.typography
                        .headlineMedium
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            Text(
                text =
                    "Frames the local AI could not " +
                            "confidently classify.",
                style =
                    MaterialTheme.typography
                        .bodyMedium
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Text(
                text =
                    "Validated: $validatedCount",
                style =
                    MaterialTheme.typography
                        .bodyMedium
            )

            Text(
                text =
                    "Agreed with AI: $agreementCount " +
                            "($agreementPercent%)",
                style =
                    MaterialTheme.typography
                        .bodyMedium
            )

            Spacer(
                modifier =
                    Modifier.height(8.dp)
            )

            HorizontalDivider()
        }

        if (pendingReviews.isEmpty()) {

            item {

                Text(
                    text =
                        "No frames waiting for review.",
                    style =
                        MaterialTheme.typography
                            .bodyMedium
                )

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                OutlinedButton(
                    onClick = onBack,

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text = "Back"
                    )
                }
            }

        } else {

            items(
                items = pendingReviews,
                key = {
                    "review_${it.id}"
                }
            ) { reference ->

                ReviewCard(
                    reference = reference,
                    onLabel = { label ->

                        reviewViewModel.submitLabel(
                            referenceId = reference.id,
                            label = label
                        )
                    },
                    onReject = {

                        reviewViewModel.reject(
                            reference.id
                        )
                    }
                )
            }
        }
    }
}

// ========================================
// REVIEW CARD
// ========================================

@Composable
private fun ReviewCard(
    reference: LabeledReference,
    onLabel: (String) -> Unit,
    onReject: () -> Unit
) {

    val file =
        File(
            reference.filePath
        )

    val categoryCandidates =
        remember(reference) {

            buildList {

                reference.candidateCategories.forEach {
                    if (!contains(it)) {
                        add(it)
                    }
                }

                // Fall back to the full category list
                // when the AI produced no prediction.
                if (isEmpty()) {

                    TextHeuristicClassifier
                        .CATEGORY_KEYWORDS
                        .keys
                        .forEach {
                            add(it)
                        }
                }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
    ) {

        if (file.exists()) {

            val bitmap =
                remember(
                    reference.filePath
                ) {

                    BitmapFactory.decodeFile(
                        reference.filePath
                    )
                }

            if (bitmap != null) {

                Image(
                    bitmap =
                        bitmap.asImageBitmap(),

                    contentDescription =
                        "Frame awaiting review",

                    modifier =
                        Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                "AI Prediction: " +
                        (reference.aiCategory
                            ?: "none"),

            style =
                MaterialTheme.typography
                    .titleSmall
        )

        reference.aiConfidence?.let { confidence ->

            Spacer(
                modifier =
                    Modifier.height(2.dp)
            )

            Text(
                text =
                    "Confidence: " +
                            "%.0f%%".format(
                                confidence * 100.0
                            ),

                style =
                    MaterialTheme.typography
                        .bodySmall
            )
        }

        reference.modelVersion?.let { modelVersion ->

            Spacer(
                modifier =
                    Modifier.height(2.dp)
            )

            Text(
                text =
                    "Model: $modelVersion",

                style =
                    MaterialTheme.typography
                        .bodySmall
            )
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                "What category best describes this content?",

            style =
                MaterialTheme.typography
                    .bodyMedium
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        categoryCandidates.forEach { category ->

            Button(
                onClick = {
                    onLabel(category)
                },

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text = category
                )
            }

            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )
        }

        OutlinedButton(
            onClick = onReject,

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(
                text = "Reject (not classifiable)"
            )
        }

        HorizontalDivider(
            modifier =
                Modifier.padding(
                    top = 12.dp
                )
        )
    }
}
