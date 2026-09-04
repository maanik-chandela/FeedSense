package com.example.feedsense.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.feedsense.dao.CaptureDao
import com.example.feedsense.dao.CloudUsageDao
import com.example.feedsense.dao.EvaluationDao
import com.example.feedsense.dao.EvaluationRunDao
import com.example.feedsense.dao.FeedItemDao
import com.example.feedsense.dao.ModelFeedbackDao
import com.example.feedsense.dao.ObservationDao
import com.example.feedsense.dao.ProjectDao
import com.example.feedsense.dao.ReferenceDao
import com.example.feedsense.dao.SessionDao
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.CapturedFrame
import com.example.feedsense.model.CloudUsageRecord
import com.example.feedsense.model.CloudUsageState
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.EvaluationRun
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.GroundTruth
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelFeedback
import com.example.feedsense.model.ResearchObservation
import com.example.feedsense.model.ResearchProject
import com.example.feedsense.model.ResearchSession

/*
 * Milestone 8A-1. The database now also hosts the
 * dedicated research evaluation layer:
 *
 *   evaluation_items    - unit of evaluation, links to a
 *                         FeedItem + model/dataset version
 *   ai_predictions      - immutable AI prediction snapshot
 *   ground_truths       - human ground truth (multi-annotator)
 *   evaluation_results  - prediction-vs-truth comparison
 *   evaluation_runs     - frozen 8A-3 report snapshots
 *
 * These tables only reference FeedItems; they never mutate
 * them, so the existing AI pipeline and research data are
 * untouched.
 */
@Database(
    entities = [
        ResearchProject::class,
        ResearchSession::class,
        ResearchObservation::class,
        CapturedFrame::class,
        LabeledReference::class,
        FeedItem::class,
        ModelFeedback::class,
        CloudUsageRecord::class,
        CloudUsageState::class,
        EvaluationItem::class,
        AiPredictionRecord::class,
        GroundTruth::class,
        EvaluationRecord::class,
        EvaluationRun::class
    ],
    version = 26,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class FeedSenseDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao

    abstract fun sessionDao(): SessionDao

    abstract fun observationDao(): ObservationDao

    abstract fun captureDao(): CaptureDao

    abstract fun referenceDao(): ReferenceDao

    abstract fun feedItemDao(): FeedItemDao

    abstract fun feedbackDao(): ModelFeedbackDao

    abstract fun cloudUsageDao(): CloudUsageDao

    abstract fun evaluationDao(): EvaluationDao

    abstract fun evaluationRunDao(): EvaluationRunDao
}