package com.example.feedsense

import android.app.Application
import androidx.room.Room
import com.example.feedsense.analysis.FrameAnalysisPipeline
import com.example.feedsense.analysis.LocalFrameAnalyzer
import com.example.feedsense.database.FeedSenseDatabase
import com.example.feedsense.repository.AnnotationRepository
import com.example.feedsense.repository.CloudUsageRepository
import com.example.feedsense.repository.EvaluationRepository
import com.example.feedsense.repository.EvaluationRunRepository
import com.example.feedsense.repository.ModelFeedbackRepository
import com.example.feedsense.repository.ModelPerformanceRepository
import com.example.feedsense.repository.ProjectRepository
import com.example.feedsense.repository.ReferenceRepository
import com.example.feedsense.repository.SessionRepository
import com.example.feedsense.repository.UserKnowledgeRepository
import com.example.feedsense.worker.FeedItemScheduler
import com.example.feedsense.worker.FrameAnalysisScheduler
import com.example.feedsense.worker.RetentionScheduler
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class FeedSenseApplication : Application() {

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            FeedSenseDatabase::class.java,
            "feedsense_database"
        )
        .addMigrations(
            MIGRATION_10_11,
            MIGRATION_11_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
            MIGRATION_18_19,
            MIGRATION_19_20,
            MIGRATION_20_21,
            MIGRATION_21_22,
            MIGRATION_22_23,
            MIGRATION_23_24,
            MIGRATION_24_25,
            MIGRATION_25_26
        )
        .fallbackToDestructiveMigration()
            .build()
    }

    /*
     * Milestone 7D:
     *
     * - feed_items: needsReview (uncertain items are
     *   kept but flagged for review), candidateCategories
     *   and classificationReason.
     * - labeled_references: feedItemId links an
     *   item-level review reference to its FeedItem so
     *   a human correction can update the item.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            database.execSQL(
                """
            ALTER TABLE feed_items
            ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE feed_items
            ADD COLUMN candidateCategories TEXT NOT NULL DEFAULT '[]'
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE feed_items
            ADD COLUMN classificationReason TEXT
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE labeled_references
            ADD COLUMN feedItemId TEXT
            """.trimIndent()
            )
        }
    }

    /*
     * Milestone 7F:
     *
     * - feed_items: contentTransitions and
     *   interactionEvidence (pipe-joined string lists,
     *   NOT NULL so Room's identity-hash check passes),
     *   secondaryCategory, paused/active watch duration,
     *   and the centralized uncertaintyLevel.
     * - labeled_references: platform/topic/tone/
     *   visibleText/aiReason/interactionSignals so the
     *   local dataset is searchable after the fact.
     */
    val MIGRATION_11_13 = object : Migration(11, 13) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            database.execSQL(
                """
            ALTER TABLE feed_items
            ADD COLUMN contentTransitions TEXT NOT NULL DEFAULT ''
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE feed_items
            ADD COLUMN interactionEvidence TEXT NOT NULL DEFAULT ''
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE feed_items
            ADD COLUMN secondaryCategory TEXT
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE feed_items
            ADD COLUMN pausedDurationSeconds INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE feed_items
            ADD COLUMN activeWatchDurationSeconds INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE feed_items
            ADD COLUMN uncertaintyLevel TEXT NOT NULL DEFAULT 'LOW'
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE labeled_references
            ADD COLUMN platform TEXT
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE labeled_references
            ADD COLUMN topic TEXT
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE labeled_references
            ADD COLUMN tone TEXT
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE labeled_references
            ADD COLUMN visibleText TEXT
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE labeled_references
            ADD COLUMN aiReason TEXT
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE labeled_references
            ADD COLUMN interactionSignals TEXT NOT NULL DEFAULT ''
            """.trimIndent()
            )
        }
    }
    /*
     * Milestone 7G:
     *
     * - labeled_references: frameFingerprint stores the
     *   perceptual hash of the frame (or representative
     *   frame) behind each reference so the local
     *   reference memory can find visually similar
     *   previous examples.
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            database.execSQL(
                """
            ALTER TABLE labeled_references
            ADD COLUMN frameFingerprint TEXT
            """.trimIndent()
            )
        }
    }

    /*
     * Milestone 7K (Part 1): the local learning / model
     * feedback dataset. One row per human correction or
     * system/cloud confirmation, preserving the original
     * AI prediction side by side with the corrected
     * truth.
     */
    val MIGRATION_14_15 = object : Migration(14, 15) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            database.execSQL(
                """
            CREATE TABLE IF NOT EXISTS model_feedback (
                id TEXT NOT NULL PRIMARY KEY,
                frameId TEXT,
                feedItemId TEXT,
                sessionId TEXT NOT NULL,
                platform TEXT,
                visibleText TEXT,
                interactionSignals TEXT NOT NULL DEFAULT '',
                originalCategory TEXT,
                originalConfidence REAL,
                originalTopic TEXT,
                originalTone TEXT,
                modelVersion TEXT,
                correctedCategory TEXT,
                correctedTopic TEXT,
                correctedTone TEXT,
                correctionSource TEXT NOT NULL,
                confidenceAfterCorrection REAL,
                categoryAgreement INTEGER NOT NULL DEFAULT 0,
                topicAgreement INTEGER,
                toneAgreement INTEGER,
                createdAt TEXT NOT NULL
            )
            """.trimIndent()
            )
        }
    }

    /*
     * Milestone 7P. Adds mixed-content intelligence
     * columns to feed_items:
     *
     *   secondaryCategories - ranked secondary category
     *                         list (TEXT, JSON/pipe list;
     *                         non-null for all rows).
     *   mixedContent        - INTEGER NOT NULL DEFAULT 0
     *                         (Boolean; 0 = false).
     *
     * Non-destructive: existing rows keep their data and
     * simply report "no mixed content" until rebuilt.
     */
    val MIGRATION_15_16 = object : Migration(15, 16) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            database.execSQL(
                """
            ALTER TABLE feed_items
            ADD COLUMN secondaryCategories TEXT NOT NULL DEFAULT ''
            """.trimIndent()
            )

            database.execSQL(
                """
            ALTER TABLE feed_items
            ADD COLUMN mixedContent INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
            )
        }
    }

    /*
     * Milestone 7Q. Adds the cloud-usage audit tables:
     *
     *   cloud_usage_records - one row per request attempt
     *                         (allowed or blocked) with
     *                         its estimated cost.
     *   cloud_usage_state   - single-row running budget
     *                         counters keyed by month/day.
     *
     * Metadata and costs only - no image contents, no
     * API keys. Non-destructive CREATE TABLEs.
     */
    val MIGRATION_16_17 = object : Migration(16, 17) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            database.execSQL(
                """
            CREATE TABLE IF NOT EXISTS cloud_usage_records (
                id TEXT NOT NULL PRIMARY KEY,
                sessionId TEXT,
                frameId TEXT,
                feedItemId TEXT,
                filePath TEXT,
                requestedAt TEXT NOT NULL,
                requestType TEXT NOT NULL,
                estimatedCostRupees REAL NOT NULL DEFAULT 0,
                allowed INTEGER NOT NULL DEFAULT 0,
                reason TEXT NOT NULL DEFAULT '',
                modelVersion TEXT
            )
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE TABLE IF NOT EXISTS cloud_usage_state (
                id TEXT NOT NULL PRIMARY KEY,
                monthKey TEXT NOT NULL,
                dayKey TEXT NOT NULL,
                monthlyEstimatedRupees REAL NOT NULL DEFAULT 0,
                dailyEstimatedRupees REAL NOT NULL DEFAULT 0,
                requestCount INTEGER NOT NULL DEFAULT 0,
                lastUpdated TEXT NOT NULL
            )
            """.trimIndent()
            )
        }
    }

    /*
     * Milestone 7S. Adds the perceptual fingerprint as a
     * real column on captured_frames so the worker can
     * detect identical content in the same session with a
     * single indexed-ish lookup instead of parsing every
     * analysisResult JSON. Nullable, so existing rows
     * (and frames captured before this migration) remain
     * valid and are simply not eligible for dedup until
     * they are analyzed again.
     */
    val MIGRATION_17_18 = object : Migration(17, 18) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            database.execSQL(
                """
            ALTER TABLE captured_frames
            ADD COLUMN frameFingerprint TEXT
            """.trimIndent()
            )
        }
    }

    /*
     * Milestone 7T. Adds the query-path indexes.
     *
     *   captured_frames
     *     (sessionId, analysisStatus)    - item building
     *     (analysisStatus)               - pending loading
     *     (sessionId, frameFingerprint)  - 7S dedup
     *   feed_items
     *     (sessionId)                    - per-session items
     *     (sessionId, updatedAt)         - 7O immutability check
     *   labeled_references
     *     (validationStatus)             - review queue
     *     (frameId)                      - pending-per-frame
     *     (sessionId, feedItemId)        - item-level assignment
     *
     * CREATE INDEX IF NOT EXISTS so the migration is safe
     * on every path (fresh, partial, or re-run).
     */
    val MIGRATION_18_19 = object : Migration(18, 19) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_captured_frames_session_status
            ON captured_frames (sessionId, analysisStatus)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_captured_frames_status
            ON captured_frames (analysisStatus)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_captured_frames_session_fingerprint
            ON captured_frames (sessionId, frameFingerprint)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_feed_items_session
            ON feed_items (sessionId)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_feed_items_session_updated
            ON feed_items (sessionId, updatedAt)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_labeled_references_status
            ON labeled_references (validationStatus)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_labeled_references_frame
            ON labeled_references (frameId)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_labeled_references_session_item
            ON labeled_references (sessionId, feedItemId)
            """.trimIndent()
            )
        }
    }

    /*
     * Milestone 7V:
     *
     * feed_items.categoryDomain stores the top-level
     * domain of the predicted category ("sports",
     * "entertainment", ...) while category keeps the
     * refined subcategory leaf ("cricket"). NULL for items
     * captured before the hierarchy refinement existed.
     */
    val MIGRATION_19_20 = object : Migration(19, 20) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            database.execSQL(
                """
            ALTER TABLE feed_items
            ADD COLUMN categoryDomain TEXT
            """.trimIndent()
            )
        }
    }

    /*
     * Milestone 7W:
     *
     * feed_items.categoryScores persists the scored
     * multi-label confidence (JSON "category" -> score)
     * aggregated from the item's frames. Legacy items
     * default to an empty object.
     */
    val MIGRATION_20_21 = object : Migration(20, 21) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            database.execSQL(
                """
            ALTER TABLE feed_items
            ADD COLUMN categoryScores TEXT NOT NULL DEFAULT '{}'
            """.trimIndent()
            )
        }
    }

    /*
     * Milestone 8. Manual observation support + platform.
     *
     * - feed_items: source, platform, researcherCategory,
     *   researcherTopic, researcherNotes, and 8 interaction
     *   flags for researcher-observed data.
     */
    val MIGRATION_21_22 = object : Migration(21, 22) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            val columns = listOf(
                "source TEXT NOT NULL DEFAULT 'AI'",
                "platform TEXT",
                "researcherCategory TEXT",
                "researcherTopic TEXT",
                "researcherNotes TEXT",
                "researcherLiked INTEGER NOT NULL DEFAULT 0",
                "researcherSkipped INTEGER NOT NULL DEFAULT 0",
                "researcherCommented INTEGER NOT NULL DEFAULT 0",
                "researcherShared INTEGER NOT NULL DEFAULT 0",
                "researcherSaved INTEGER NOT NULL DEFAULT 0",
                "researcherFollowed INTEGER NOT NULL DEFAULT 0",
                "researcherPaused INTEGER NOT NULL DEFAULT 0",
                "researcherReplayed INTEGER NOT NULL DEFAULT 0"
            )
            for (col in columns) {
                database.execSQL(
                    "ALTER TABLE feed_items ADD COLUMN $col"
                )
            }
        }
    }

    val MIGRATION_22_23 = object : Migration(22, 23) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            val columns = listOf(
                "appContext TEXT",
                "chromeOnly INTEGER NOT NULL DEFAULT 0"
            )
            for (col in columns) {
                database.execSQL(
                    "ALTER TABLE feed_items ADD COLUMN $col"
                )
            }
        }
    }

    /*
     * Milestone 8A-1. Adds the dedicated research
     * evaluation layer as four NEW, purely additive tables.
     *
     *   evaluation_items    - unit of evaluation, links to
     *                         FeedItem + model/dataset version
     *   ai_predictions      - immutable AI prediction snapshot
     *   ground_truths       - human ground truth
     *   evaluation_results  - prediction-vs-truth comparison
     *
     * Non-destructive: only CREATE TABLE + CREATE INDEX
     * statements. No existing table/column is touched, so
     * all existing research data survives.
     *
     * The fallbackToDestructiveMigration() fallback is never
     * reached for 23 -> 24 because this migration is
     * provided and succeeds.
     */
    val MIGRATION_23_24 = object : Migration(23, 24) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            database.execSQL(
                """
            CREATE TABLE IF NOT EXISTS evaluation_items (
                id TEXT NOT NULL PRIMARY KEY,
                feedItemId TEXT NOT NULL,
                sessionId TEXT NOT NULL,
                projectId TEXT,
                modelVersion TEXT,
                datasetVersion TEXT,
                evaluationStatus TEXT NOT NULL,
                createdAt TEXT NOT NULL,
                enqueuedAt TEXT NOT NULL,
                completedAt TEXT
            )
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE TABLE IF NOT EXISTS ai_predictions (
                id TEXT NOT NULL PRIMARY KEY,
                evaluationItemId TEXT NOT NULL,
                predictedAt TEXT NOT NULL,
                source TEXT,
                modelVersion TEXT,
                category TEXT,
                categoryDomain TEXT,
                confidence REAL,
                secondaryCategories TEXT NOT NULL DEFAULT '',
                categoryScores TEXT NOT NULL DEFAULT '{}',
                platform TEXT,
                contentType TEXT,
                durationSeconds INTEGER NOT NULL DEFAULT 0,
                skipped INTEGER NOT NULL DEFAULT 0,
                interactionSignals TEXT NOT NULL DEFAULT '',
                topic TEXT,
                tone TEXT,
                uncertaintyLevel TEXT NOT NULL DEFAULT 'LOW',
                needsReview INTEGER NOT NULL DEFAULT 0,
                feedItemId TEXT
            )
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE TABLE IF NOT EXISTS ground_truths (
                id TEXT NOT NULL PRIMARY KEY,
                evaluationItemId TEXT NOT NULL,
                annotatorId TEXT,
                recordedAt TEXT NOT NULL,
                category TEXT,
                categoryDomain TEXT,
                secondaryCategories TEXT NOT NULL DEFAULT '',
                ambiguity TEXT NOT NULL DEFAULT 'CLEAR',
                platform TEXT,
                contentType TEXT,
                durationSeconds INTEGER,
                skipped INTEGER,
                interactionSignals TEXT NOT NULL DEFAULT '',
                topic TEXT,
                tone TEXT,
                notes TEXT
            )
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE TABLE IF NOT EXISTS evaluation_results (
                id TEXT NOT NULL PRIMARY KEY,
                evaluationItemId TEXT NOT NULL,
                groundTruthId TEXT NOT NULL,
                aiPredictionId TEXT NOT NULL,
                modelVersion TEXT,
                datasetVersion TEXT,
                evaluationMethodVersion TEXT NOT NULL,
                comparable INTEGER NOT NULL DEFAULT 1,
                segmentationError TEXT,
                verdict TEXT NOT NULL,
                categoryCorrect INTEGER,
                secondaryMatch INTEGER,
                topicAgreement INTEGER,
                toneAgreement INTEGER,
                platformAgreement INTEGER,
                contentTypeAgreement INTEGER,
                durationInaccurate INTEGER,
                durationErrorSeconds INTEGER,
                skippedAgreement INTEGER,
                interactionSignalsDisagreement INTEGER,
                recordedAt TEXT NOT NULL
            )
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_evaluation_items_feed_item
            ON evaluation_items (feedItemId)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_evaluation_items_session
            ON evaluation_items (sessionId)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_evaluation_items_dataset
            ON evaluation_items (datasetVersion)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_evaluation_items_status
            ON evaluation_items (evaluationStatus)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_ai_predictions_evaluation_item
            ON ai_predictions (evaluationItemId)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_ai_predictions_model_version
            ON ai_predictions (modelVersion)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_ground_truths_evaluation_item
            ON ground_truths (evaluationItemId)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_ground_truths_annotator
            ON ground_truths (annotatorId)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_evaluation_results_evaluation_item
            ON evaluation_results (evaluationItemId)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_evaluation_results_ground_truth
            ON evaluation_results (groundTruthId)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_evaluation_results_verdict
            ON evaluation_results (verdict)
            """.trimIndent()
            )

            database.execSQL(
                """
            CREATE INDEX IF NOT EXISTS idx_evaluation_results_model_dataset
            ON evaluation_results (modelVersion, datasetVersion)
            """.trimIndent()
            )
        }
    }

    /*
     * Milestone 8A-2. Adds per-signal interaction tri-state
     * to ground_truths.
     *
     *   liked / commented / shared / saved / followed /
     *   paused / playing  INTEGER (nullable)
     *
     * Value semantics:
     *   NULL  = UNKNOWN (no evidence either way)
     *   0     = certain it did NOT occur
     *   1     = evidence it occurred
     *
     * Non-destructive: purely additive ALTER TABLE ... ADD
     * COLUMN on ground_truths. Every existing ground-truth
     * row keeps its data and simply reports UNKNOWN for the
     * new interaction signals until re-annotated. The
     * already-evaluated rows are not destroyed.
     */
    val MIGRATION_24_25 = object : Migration(24, 25) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            val columns = listOf(
                "liked INTEGER",
                "commented INTEGER",
                "shared INTEGER",
                "saved INTEGER",
                "followed INTEGER",
                "paused INTEGER",
                "playing INTEGER"
            )
            for (col in columns) {
                database.execSQL(
                    "ALTER TABLE ground_truths ADD COLUMN $col"
                )
            }
        }
    }

    /*
     * Milestone 8A-3. Adds the read-only evaluation-run
     * snapshot table so a fully-computed report can be frozen
     * and never recomputed against a moving dataset. Purely
     * additive; existing tables are untouched.
     */
    val MIGRATION_25_26 = object : Migration(25, 26) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS evaluation_runs (
                    id TEXT NOT NULL PRIMARY KEY,
                    runId TEXT NOT NULL,
                    datasetVersion TEXT,
                    modelVersion TEXT,
                    evaluationMethodVersion TEXT NOT NULL,
                    createdAt TEXT NOT NULL,
                    configJson TEXT NOT NULL,
                    reportJson TEXT NOT NULL,
                    totalItems INTEGER NOT NULL,
                    eligibleItems INTEGER NOT NULL,
                    excludedItems INTEGER NOT NULL,
                    description TEXT
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_evaluation_runs_dataset
                ON evaluation_runs (datasetVersion)
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_evaluation_runs_model
                ON evaluation_runs (modelVersion)
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS idx_evaluation_runs_created
                ON evaluation_runs (createdAt)
                """.trimIndent()
            )
        }
    }

    val repository by lazy {
        ProjectRepository(
            database.projectDao()
        )
    }

    val sessionRepository by lazy {
        SessionRepository(
            sessionDao = database.sessionDao(),
            observationDao = database.observationDao(),
            captureDao = database.captureDao(),
            feedItemDao = database.feedItemDao(),
            referenceRepository = referenceRepository,
            applicationContext = applicationContext
        )
    }

    /*
     * Labeled reference / training dataset.
     */
    val referenceRepository by lazy {
        ReferenceRepository(
            database.referenceDao()
        )
    }

    /*
     * Milestone 7K. The local learning / model feedback
     * dataset (confirmed corrections), used by the review
     * flow and by the observed-validation statistics.
     */
    val modelFeedbackRepository by lazy {
        ModelFeedbackRepository(
            database.feedbackDao()
        )
    }

    /*
     * Milestone 7O. Cross-session user knowledge:
     * what the user watches and corrects, aggregated
     * from validated references and feedback. Feeds the
     * personalization section of the evaluation screen.
     */
    val userKnowledgeRepository by lazy {
        UserKnowledgeRepository(
            referenceDao = database.referenceDao(),
            feedbackDao = database.feedbackDao()
        )
    }

    /*
     * Milestone 7G. Model performance evaluation,
     * computed on demand from the existing tables.
     */
    val modelPerformanceRepository by lazy {
        ModelPerformanceRepository(
            referenceDao = database.referenceDao(),
            feedItemDao = database.feedItemDao(),
            captureDao = database.captureDao(),
            feedbackDao = database.feedbackDao(),
            cloudUsageDao = database.cloudUsageDao(),
            userKnowledgeRepository =
                userKnowledgeRepository
        )
    }

    /*
     * Milestone 7Q. Cloud usage manager: gates and
     * records every cloud AI request against the daily/
     * monthly budget. Local-only stays the default; the
     * manager only matters when a cloud analyzer is
     * configured AND a request is about to be made.
     */
    val cloudUsageRepository by lazy {
        CloudUsageRepository(
            database.cloudUsageDao()
        )
    }

    /*
     * Milestone 8A-1. Dedicated research evaluation layer:
     * AI prediction snapshot / human ground truth /
     * evaluation result, plus dataset versioning.
     */
    val evaluationRepository by lazy {
        EvaluationRepository(
            database.evaluationDao()
        )
    }

    /*
     * DAO accessors exposed for the 8A-2 annotation
     * workflow. The annotation repository needs the
     * underlying evaluation, feed-item and capture DAOs to
     * assemble evidence (AI snapshot + representative frame
     * + session frames within the item's time window).
     */
    val evaluationDao by lazy {
        database.evaluationDao()
    }

    val feedItemDao by lazy {
        database.feedItemDao()
    }

    val captureDao by lazy {
        database.captureDao()
    }

    val annotationRepository by lazy {
        AnnotationRepository(
            evaluationRepository = evaluationRepository,
            evaluationDao = evaluationDao,
            feedItemDao = feedItemDao,
            captureDao = captureDao
        )
    }

    val evaluationRunDao by lazy {
        database.evaluationRunDao()
    }

    /*
     * Milestone 8A-3. Read-only evaluation engine over 8A-1/8A-2
     * data: computable run reports frozen into evaluation_runs.
     */
    val evaluationRunRepository by lazy {
        EvaluationRunRepository(
            evaluationDao = evaluationDao,
            evaluationRunDao = evaluationRunDao
        )
    }

    /*
     * The hybrid analysis pipeline.
     *
     * Local analysis first. Cloud fallback only when
     * the local result is not confident or is ambiguous,
     * and only while the cloud budget allows it.
     */
    val frameAnalyzer by lazy {
        FrameAnalysisPipeline(
            localAnalyzer =
                LocalFrameAnalyzer(
                    applicationContext
                ),
            cloudUsageManager =
                cloudUsageRepository
        )
    }

    override fun onCreate() {
        super.onCreate()

        FrameAnalysisScheduler.schedule(this)

        /*
         * Rebuild feed items from any analyzed frames
         * that were never built (e.g. the app was killed
         * before the incremental builder could run).
         */
        FeedItemScheduler.schedule(this)

        /*
         * Milestone 7T. Daily retention: purge old
         * sessions and trim over-cap sessions so the raw
         * screenshot store stays bounded.
         */
        RetentionScheduler.schedule(this)
    }
}