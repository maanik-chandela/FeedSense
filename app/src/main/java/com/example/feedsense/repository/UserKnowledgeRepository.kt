package com.example.feedsense.repository

import com.example.feedsense.analysis.PersonalizationAggregator
import com.example.feedsense.dao.ModelFeedbackDao
import com.example.feedsense.dao.ReferenceDao
import com.example.feedsense.model.PersonalizationStats

// --------------------------------
// USER KNOWLEDGE REPOSITORY
// --------------------------------
//
// Milestone 7O.
//
// Cross-session personalization. Aggregates what the
// user actually watches and corrects from data the app
// already owns:
//
//   validated references -> trusted memory (watched
//                           and human-confirmed content).
//   model_feedback       -> confirmations/corrections.
//
// The stats are computed ON DEMAND (only while the
// evaluation screen is open), never written back into
// historical sessions, and never sent to the cloud.
// Personalization influences future predictions only,
// through the reference-memory retrieval path.
//

class UserKnowledgeRepository(
    private val referenceDao: ReferenceDao,
    private val feedbackDao: ModelFeedbackDao
) {

    suspend fun computePersonalizationStats():
            PersonalizationStats {

        return PersonalizationAggregator.aggregate(
            validatedReferences =
                referenceDao.getAllValidated(),
            feedback =
                feedbackDao.getAll()
        )
    }
}
