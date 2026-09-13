package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * Privacy COVERAGE evaluation (spec §37-§38).
 *
 * Evaluates the privacy layer against MANUALLY annotated ground
 * truth. Ground truth is a human-drawn set of sensitive regions
 * for a synthetic frame; it is NEVER AI predictions (spec §37).
 *
 * Metrics (deterministic, measured only - never fabricated):
 *
 *   coverage        : fraction of ground-truth area that the
 *                     privacy process actually protected.
 *   falsePositives  : automatic regions (of a type that WAS
 *                     transformed) with no GT overlap - the layer
 *                     protected something nobody asked it to.
 *   falseNegatives  : GT regions with no overlap on any protected
 *                     automatic region - a leak.
 *
 * All geometry is normalized at GT/region level and resolved
 * against the evaluation frame at measurement time.
 */
data class PrivacyCoverageReport(
    val evaluatorVersion: String,
    val groundTruthRegions: Int,
    val coverage: Double,
    val falsePositives: Int,
    val falseNegatives: Int,
    val totalRegions: Int,
    val coveredRegions: Int,
    val protectionEvidence: List<ProtectionEvidence>
)

/*
 * One observed protection event: which type was transformed, and
 * how much of it overlapped ground truth. No raw content, only
 * masked metadata (spec §21).
 */
data class ProtectionEvidence(
    val regionType: PrivacyRegionType,
    val transformation: PrivacyTransformation,
    val overlapWithGroundTruth: Double
)

/*
 * Milestone 8B-13.
 *
 * Offline coverage evaluator (spec §38). Deterministic for fixed
 * inputs.
 */
class PrivacyCoverageEvaluator {

    fun evaluate(
        frame: PrivacyFrame,
        groundTruth: List<ProtectedRegion>,
        automaticRegions: List<PrivacyRegion>,
        driver: CoverageDriver = PrivacyProcessorDriver(PrivacyPolicy.RESEARCH)
    ): PrivacyCoverageReport {

        val result = driver.process(frame, automaticRegions.toList())

        val transformedTypes = result.decision.transformations
            .filter { it.transformation != PrivacyTransformation.NONE }
            .map { it.regionType }
            .toSet()

        // Kind of a protected region set: an automatic region is
        // "protected" when its type was actually transformed.
        val protectedAuto = automaticRegions.filter {
            it.type in transformedTypes
        }

        // --- coverage & false negatives -------------------------
        var gtArea = 0L
        var protectedGtArea = 0L
        var coveredRegions = 0
        var falseNegatives = 0

        for (gt in groundTruth) {
            val bounds = gt.toPixelBounds(frame.width, frame.height)
            val area = bounds.width.toLong() * bounds.height.toLong()
            gtArea += area
            val isProtected = protectedAuto.any { auto ->
                overlap(
                    auto.bounds.toPixelBounds(frame.width, frame.height),
                    bounds
                )
            }
            if (isProtected) {
                protectedGtArea += area
                coveredRegions++
            } else {
                falseNegatives++
            }
        }

        val coverage = if (gtArea == 0L) 0.0 else {
            (protectedGtArea.toDouble() / gtArea.toDouble())
                .coerceIn(0.0, 1.0)
        }

        // --- false positives ------------------------------------
        val falsePositives = protectedAuto.count { auto ->
            val bounds = auto.bounds.toPixelBounds(frame.width, frame.height)
            groundTruth.none {
                overlap(bounds, it.toPixelBounds(frame.width, frame.height))
            }
        }

        val evidence = protectedAuto.map { auto ->
            val bounds = auto.bounds.toPixelBounds(frame.width, frame.height)
            val overlap = groundTruth.sumOf { gt ->
                overlapFraction(
                    bounds,
                    gt.toPixelBounds(frame.width, frame.height)
                )
            }.coerceIn(0.0, 1.0)
            ProtectionEvidence(
                regionType = auto.type,
                transformation = result.decision.transformations
                    .filter { it.regionType == auto.type }
                    .maxByOrNull { it.transformation.severity }
                    ?.transformation
                    ?: PrivacyTransformation.NONE,
                overlapWithGroundTruth = overlap
            )
        }.sortedBy { it.regionType.label }

        return PrivacyCoverageReport(
            evaluatorVersion = PrivacySanitizationVersion.COVERAGE,
            groundTruthRegions = groundTruth.size,
            coverage = coverage,
            falsePositives = falsePositives,
            falseNegatives = falseNegatives,
            totalRegions = automaticRegions.size,
            coveredRegions = coveredRegions,
            protectionEvidence = evidence
        )
    }

    private fun overlap(
        a: PixelBounds,
        b: PixelBounds
    ): Boolean = a.overlaps(b)

    private fun overlapFraction(
        a: PixelBounds,
        b: PixelBounds
    ): Double {
        val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (w <= 0 || h <= 0) return 0.0
        val aArea = a.width.toLong() * a.height.toLong()
        if (aArea <= 0L) return 0.0
        return (w.toDouble() * h.toDouble() / aArea).coerceIn(0.0, 1.0)
    }
}

/*
 * Milestone 8B-13.
 *
 * Abstraction over a privacy driver so the coverage evaluator can
 * consume either the in-memory processor or (with a thin adapter)
 * the file-based 8B-10 sanitizer.
 */
interface CoverageDriver {
    fun process(
        frame: PrivacyFrame,
        regions: List<PrivacyRegion>
    ): AnonymizationResult
}

/*
 * Default driver: the deterministic in-memory processor.
 */
class PrivacyProcessorDriver(
    val policy: PrivacyPolicy = PrivacyPolicy.RESEARCH
) : CoverageDriver {

    private val processor = PrivacyProcessor(policy)

    override fun process(
        frame: PrivacyFrame,
        regions: List<PrivacyRegion>
    ): AnonymizationResult {
        return processor.process(frame, regions, OcrAvailability.OCR_AVAILABLE)
    }
}