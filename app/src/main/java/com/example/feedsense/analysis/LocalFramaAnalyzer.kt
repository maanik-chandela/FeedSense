package com.example.feedsense.analysis
import android.content.Context
import java.io.File

// --------------------------------
// LOCAL FRAME ANALYZER
// --------------------------------
//
// Milestone 7A local analysis chain.
//
// Composes:
//
//   1. OCR (ML Kit)   -> visible text
//   2. Heuristic      -> content category, confidence,
//                        ambiguity
//
// Runs fully on-device, offline, and free.
//
// The FrameAnalyzer interface is the swappable
// boundary: a real vision model can replace this
// implementation later without touching the pipeline
// or the worker.
//

class LocalFrameAnalyzer(
    context: Context
) : FrameAnalyzer {

    private val ocrAnalyzer =
        OcrFrameAnalyzer(
            context
        )

    private val classifier =
        TextHeuristicClassifier()

    /*
     * Milestone 7M. The stronger local classifier: runs
     * the text heuristic first, then refines it with
     * platform priors and confidence calibration. Falls
     * back safely to the heuristic result.
     */
    private val multiSignalClassifier =
        MultiSignalClassifier()

    private val interactionDetector =
        InteractionDetector()

    private val platformDetector =
        PlatformDetector()

    /*
     * App Chrome Detection. Identifies which app is on
     * screen and whether the frame is UI chrome or
     * actual content.
     */
    private val appChromeDetector =
        AppChromeDetector()

    /*
     * Milestone 7U. Multi-signal candidate generation:
     * ranks every canonical category from text, hashtag,
     * title, platform and memory signals. It never
     * overrides the classifier - it enriches the candidate
     * list and the audit evidence.
     */
    private val categoryScorer =
        CategoryScorer()

    /*
     * Milestone 7V. Hierarchical refinement: when the
     * multi-signal prediction lands on a DOMAIN, a clear
     * subcategory winner (cricket under sports, school
     * under education, ...) refines the label. The
     * top-level domain is reported separately on the
     * result (categoryDomain).
     */
    private val hierarchyClassifier =
        HierarchyClassifier()

    /*
     * Milestone 7W. Turns the scorer's per-category
     * scores into an explicit, scored multi-label set and
     * flags when a secondary label is dangerously close
     * to the primary (genuinely mixed content).
     */
    private val multiLabelExtractor =
        MultiLabelExtractor()

    override suspend fun analyze(
        file: File
    ): FrameAnalysisResult {

        // --------------------------------
        // STEP 1: OCR
        // --------------------------------

        val ocrResult =
            ocrAnalyzer.analyze(
                file
            )

        // --------------------------------
        // STEP 2: HEURISTIC CLASSIFICATION
        // --------------------------------

        val heuristicClassification =
            classifier.classify(
                ocrResult.visibleText
            )

        // --------------------------------
        // STEP 3b: PLATFORM (7E)
        // --------------------------------
        //
        // Milestone 7E. Which platform was on screen?
        // Detected from the same OCR evidence so an
        // observation can record the platform.

        val platform =
            platformDetector.detect(
                ocrResult.visibleText
            )

        // --------------------------------
        // STEP 2b: MULTI-SIGNAL REFINEMENT (7M)
        // --------------------------------
        //
        // The text heuristic is the base layer. The
        // multi-signal layer refines it with platform
        // priors and calibrated confidence. When it does
        // not change anything it is a no-op, so the
        // heuristic result (and its modelVersion) is
        // preserved.

        val multiSignal =
            multiSignalClassifier.classify(
                MultiSignalClassifier.SignalSet(
                    text = ocrResult.visibleText,
                    platform = platform,
                    heuristic =
                        heuristicClassification
                )
            )

        // --------------------------------
        // STEP 2c: APP CHROME DETECTION
        // --------------------------------
        //
        // Identifies the app on screen and whether the
        // frame is UI chrome (status bar, nav bar, app
        // header) vs actual content. Chrome frames get
        // flagged so feed item building can skip them.

        val chromeDetection =
            appChromeDetector.detect(
                ocrResult.visibleText
            )

        /*
         * Milestone 7V. Refine a domain-level prediction
         * into its subcategory when the evidence is a
         * clear winner. The parent domain is preserved on
         * the result.
         */
        val hierarchical =
            hierarchyClassifier.refine(
                result = multiSignal,
                text = ocrResult.visibleText
            )

        val classification =
            hierarchical

        val categoryDomain =
            CategoryCatalog.domainOf(
                classification.primaryCategory
            )

        /*
         * Milestone 7U. The scorer produces ranked
         * multi-signal candidates (text + hashtag + title
         * + platform + memory). Extra candidates extend
         * the secondary list so mixed content is
         * represented; hashtag/title/memory signals are
         * appended to the audit evidence. Memory hooks are
         * populated by the 7Y retrieval wiring.
         */
        val scorer =
            categoryScorer.score(
                text = ocrResult.visibleText,
                platform = platform,
                memoryCandidates = emptySet(),
                memorySupport = 0.0
            )

        val enrichedSecondary =
            buildList {
                addAll(
                    classification.secondaryCategories
                )
                scorer.ranked.forEach {
                    if (
                        it.category !=
                        classification.primaryCategory &&
                        !contains(it.category)
                    ) {
                        add(it.category)
                    }
                }
            }
                .distinct()
                .take(MAX_SECONDARY_CATEGORIES)

        val scorerEvidence =
            scorer.evidence
                .filter {
                    it.startsWith("hashtag:") ||
                        it.startsWith("title:") ||
                        it.startsWith("memory:")
                }

        /*
         * Milestone 7W. The scored multi-label set
         * (secondary labels with confidence + an
         * uncertainty decision) derived from the same
         * scores that rank the candidates. Its reason is
         * appended to the audit evidence so the labels are
         * transparent.
         */
        val multiLabel =
            multiLabelExtractor.extract(
                primary =
                    classification.primaryCategory,
                scores =
                    scorer.scores
            )

        val enrichedReason =
            buildString {
                classification.reason?.let {
                    append(it)
                }
                if (
                    scorerEvidence.isNotEmpty() &&
                    classification.reason != null
                ) {
                    append(" ")
                }
                append(
                    scorerEvidence.joinToString(",")
                )
                if (
                    multiLabel.reason != null
                ) {
                    if (
                        scorerEvidence.isNotEmpty() ||
                        classification.reason != null
                    ) {
                        append(" ")
                    }
                    append(multiLabel.reason)
                }
            }
            .takeIf {
                it.isNotBlank()
            }
            ?: classification.reason

        /*
         * Milestone 7V. A hierarchy refinement changes the
         * primary label; that counts as local refinement
         * for the modelVersion report.
         */
        val hierarchyRefined =
            classification.primaryCategory !=
                multiSignal.primaryCategory

        val refined =
            multiSignal.confidence !=
                heuristicClassification.confidence ||
                multiSignal.primaryCategory !=
                heuristicClassification.primaryCategory ||
                enrichedReason !=
                heuristicClassification.reason ||
                hierarchyRefined

        // --------------------------------
        // STEP 3: INTERACTION EVIDENCE
        // --------------------------------
        //
        // Milestone 7D-A. Only UI evidence found in
        // the OCR text becomes a signal.
        //
        // Milestone 7F (Part 3): each signal now keeps
        // its confidence level and the matched text, so
        // the evidence is auditable, not just a label.
        // `detect` still returns plain ids for
        // compatibility.

        val interactionSignals =
            interactionDetector.detect(
                ocrResult.visibleText
            )

        val interactionEvidence =
            interactionDetector
                .detectWithEvidence(
                    ocrResult.visibleText
                )
                .map {
                    it.toEvidenceString()
                }

        // --------------------------------
        // STEP 4: MERGE INTO RESULT
        // --------------------------------

        return FrameAnalysisResult(
            status =
                "LOCAL_ANALYSIS_COMPLETE",

            fileName =
                ocrResult.fileName,

            width =
                ocrResult.width,

            height =
                ocrResult.height,

            fileSizeBytes =
                ocrResult.fileSizeBytes,

            message =
                if (classification.primaryCategory != null) {
                    "Local analysis complete. Category: " +
                            classification.primaryCategory
                } else {
                    "Local analysis complete. No category " +
                            "confidently detected."
                },

            screenType =
                ocrResult.screenType,

            application =
                platform
                    ?: ocrResult.application,

            activity =
                ocrResult.activity,

            visibleText =
                ocrResult.visibleText,

            confidence =
                classification.confidence,

            classificationReason =
                enrichedReason,

            contentCategory =
                classification.primaryCategory,

            /*
             * Milestone 7V. The top-level domain of the
             * predicted category. When refinement fires the
             * item is labeled with the subcategory while
             * this keeps the parent domain for roll-ups.
             */
            categoryDomain =
                categoryDomain,

            secondaryCategories =
                enrichedSecondary,

            /*
             * Milestone 7W. Scored multi-label confidence
             * for every category the scorer considered, so
             * the label set (and its scores) survives into
             * the feed item and dataset.
             */
            categoryScores =
                scorer.scores,

            topic =
                classification.topic,

            tone =
                classification.tone,

            ambiguityScore =
                classification.ambiguityScore,

            interactionSignals =
                interactionSignals,

            interactionEvidence =
                interactionEvidence,

            modelVersion =
                if (refined) {
                    MultiSignalClassifier.MODEL_VERSION
                } else {
                    MODEL_VERSION
                },

            isChromeFrame =
                chromeDetection.isChromeFrame,

            appContext =
                chromeDetection.appContext
        )
    }

    companion object {

        /*
         * Milestone 7U. Cap on the enriched multi-signal
         * secondary category list (mirrors the heuristic
         * classifier's cap so mixed content stays focused).
         */
        private const val MAX_SECONDARY_CATEGORIES = 3

        /*
         * Version of the local heuristic model.
         *
         * Bumped whenever the keyword sets or scoring
         * logic change, so results are comparable.
         *
         * v3 (7D): category keywords moved to the
         * data-driven CategoryCatalog, lifestyle and
         * finance categories added, classification
         * reason/evidence added.
         *
         * v4 (7E): platform detection added (which
         * platform is on screen).
         *
         * v5 (7F): interaction signals carry confidence
         * and evidence; same-category content splits.
         *
         * v6 (7U): category taxonomy expanded to the full
         * 62-key catalog; the multi-signal CategoryScorer
         * enriches secondary categories and audit evidence.
         *
         * When the multi-signal layer (7M) refines a
         * result, the emitted modelVersion is
         * MultiSignalClassifier.MODEL_VERSION
         * ("local-v6.0") instead, so the evaluation
         * screens can tell which logic produced the
         * result.
         */
        const val MODEL_VERSION =
            "heuristic-v6"
    }
}