package com.example.feedsense.repository

import com.example.feedsense.dao.ModelFeedbackDao
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelFeedback

// --------------------------------
// MODEL FEEDBACK REPOSITORY
// --------------------------------
//
// Milestone 7K (Parts 1 and 4). Writes and reads the
// local learning dataset. The only path that creates a
// USER learning example is a human correction in the
// review flow (via submitCorrection).
//

class ModelFeedbackRepository(
    private val feedbackDao: ModelFeedbackDao
) {

    suspend fun submitCorrection(
        reference: LabeledReference,
        correctedCategory: String,
        correctedTopic: String?,
        correctedTone: String?,
        correctionSource: String =
            ModelFeedback.SOURCE_USER
    ) {
        feedbackDao.insert(
            ModelFeedback.fromCorrection(
                reference = reference,
                correctedCategory =
                    correctedCategory,
                correctedTopic =
                    correctedTopic,
                correctedTone =
                    correctedTone,
                correctionSource =
                    correctionSource
            )
        )
    }

    suspend fun getAll(): List<ModelFeedback> {
        return feedbackDao.getAll()
    }

    suspend fun getForSession(
        sessionId: String
    ): List<ModelFeedback> {
        return feedbackDao.getForSession(sessionId)
    }

    suspend fun getForFeedItem(
        feedItemId: String
    ): List<ModelFeedback> {
        return feedbackDao.getForFeedItem(feedItemId)
    }

    suspend fun getCount(): Int {
        return feedbackDao.getCount()
    }
}
