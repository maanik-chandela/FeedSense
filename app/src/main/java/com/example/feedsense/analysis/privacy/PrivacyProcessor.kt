package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * Deterministic on-device privacy-processing pipeline.
 *
 *   DETECT  -> regions are supplied by the detection layer
 *              (8B-10 SystemUIRegionDetector / SamsungPattern
 *              / PrivacyOcrDetection); nothing here runs OCR or
 *              downloads a vision model (spec §3).
 *   DECIDE  -> risk + confidence computed; policy filters;
 *              rule table resolves the transformation; a
 *              DROP_FRAME decision may fire (spec §13-§14).
 *   TRANSFORM -> deterministic pixel transforms, applied
 *              weakest-first so the stronger transformation
 *              wins on overlap (spec §11).
 *   VALIDATE -> per-region checksums prove the transform
 *              actually changed the pixels; a no-op BLUR/PIXELATE
 *              escalates to MASK; unresolved regions surface
 *              PARTIALLY_SANITIZED (spec §15).
 *
 * The raw buffer is mutated IN PLACE: no second full-frame copy
 * on the common path, so the raw-frame lifetime ends with this
 * call (spec §18, §33). CROP produces a new smaller frame.
 *
 * Determinism: only frame pixels, regions, policy, rules, OCR
 * availability, and the injected clock are read; no randomness,
 * no wall-clock in the decision path.
 */
class PrivacyProcessor(
    val policy: PrivacyPolicy = PrivacyPolicy.RESEARCH,
    val rules: PrivacyRuleTable = PrivacyRuleTable.DEFAULT,
    private val nowMs: () -> Long = { 0L }
) {

    /*
     * Processes ONE frame. The buffer passed in becomes the safe
     * frame; use result.safeFrame afterwards. May return
     * safeFrame == null when the frame was dropped.
     */
    fun process(
        frame: PrivacyFrame,
        regions: List<PrivacyRegion>,
        ocrAvailability: OcrAvailability
    ): AnonymizationResult {
        val timestampMs = nowMs()

        val ordered = regions.sortedWith(
            compareBy<PrivacyRegion>(
                { it.type.label },
                { it.bounds.x },
                { it.bounds.y },
                { it.bounds.width },
                { it.bounds.height },
                { it.confidence }
            )
        )

        val risk = PrivacyRiskAggregator.compute(ordered, ocrAvailability)
        val confidence = PrivacyConfidenceAggregator.aggregate(ordered, ocrAvailability)
        val unknownRisk = ocrAvailability == OcrAvailability.OCR_UNAVAILABLE &&
            ordered.any { it.type == PrivacyRegionType.UNKNOWN_SENSITIVE_REGION }

        val enabled = ordered.filter { policy.isSanitizationEnabled(it.type) }

        if (enabled.isEmpty()) {
            val status = if (ordered.isEmpty()) {
                PrivacySanitizationStatus.NOT_REQUIRED
            } else {
                PrivacySanitizationStatus.SANITIZATION_UNAVAILABLE
            }
            return buildResult(
                frameId = null,
                timestampMs = timestampMs,
                ocrAvailability = ocrAvailability,
                ordered = ordered,
                enabledCount = 0,
                risk = risk,
                confidence = confidence,
                status = status,
                safeFrame = frame,
                transformations = emptyList(),
                dropped = false,
                evidenceLoss = PrivacyEvidenceLoss.NONE
            )
        }

        // --- DECIDE: DROP_FRAME ---------------------------------
        val researchCoverage = occupiedFraction(frame, enabled)
        val researchValueFraction = (1.0 - researchCoverage).coerceIn(0.0, 1.0)
        val drop = policy.policyMode.allowsFrameDrop &&
            risk.isHighRisk &&
            researchValueFraction < DROP_VALUE_THRESHOLD

        if (drop) {
            return buildResult(
                frameId = null,
                timestampMs = timestampMs,
                ocrAvailability = ocrAvailability,
                ordered = ordered,
                enabledCount = enabled.size,
                risk = risk,
                confidence = confidence,
                status = PrivacySanitizationStatus.SANITIZED,
                safeFrame = null,
                transformations = emptyList(),
                dropped = true,
                evidenceLoss = PrivacyEvidenceLoss
                    .EVIDENCE_LOST_DUE_TO_SANITIZATION
            )
        }

        // --- DECIDE: transformation plan ------------------------
        var workingFrame = frame
        val plan = enabled.map { region ->
            val transform = rules.transformationFor(region.type, unknownRisk)
            val fallbackTransform = if (transform == PrivacyTransformation.CROP) {
                PrivacyTransformation.MASK
            } else {
                transform
            }
            Plan(
                region = region,
                proposed = transform,
                effective = fallbackTransform
            )
        }

        // --- TRANSFORM: crops first (reshape the frame) ---------
        val croppedPlans = mutableListOf<Plan>()
        val cropRecords = mutableListOf<AppliedTransformation>()

        for (crop in plan.sortedBy { it.region.bounds.y }) {
            if (crop.proposed != PrivacyTransformation.CROP) continue
            val bounds = workingFrame.pixelBounds(crop.region.bounds)
            if (bounds.width == 0 || bounds.height == 0) continue
            val isFullWidthBand =
                bounds.left == 0 && bounds.right == workingFrame.width
            val isFullHeightBand =
                bounds.top == 0 && bounds.bottom == workingFrame.height
            if (!isFullWidthBand && !isFullHeightBand) continue
            val cropped = PrivacyTransformers.cropBand(workingFrame, bounds)
            if (cropped !== workingFrame) {
                workingFrame = cropped
                croppedPlans += crop
                cropRecords += remember(crop, PrivacyTransformation.CROP)
            }
        }

        // --- TRANSFORM: point transforms, severity ascending -----
        val points = plan
            .filter { candidate -> croppedPlans.none { it === candidate } }
            .sortedBy { it.effective.severity }

        val beforeChecksums = points.associate { point ->
            val bounds = workingFrame.pixelBounds(point.region.bounds)
            point to (bounds to workingFrame.regionChecksum(bounds))
        }

        for (point in points) {
            val transform = point.effective
            if (transform == PrivacyTransformation.NONE) continue
            val bounds = workingFrame.pixelBounds(point.region.bounds)
            if (!bounds.isValid) continue
            when (transform) {
                PrivacyTransformation.BLUR ->
                    PrivacyTransformers.blur(workingFrame, bounds)
                PrivacyTransformation.PIXELATE ->
                    PrivacyTransformers.pixelate(workingFrame, bounds)
                PrivacyTransformation.MASK ->
                    PrivacyTransformers.mask(workingFrame, bounds)
                else -> { /* NONE handled above */ }
            }
        }

        // --- VALIDATE: checksums, escalate no-ops ----------------
        val handled = mutableListOf<AppliedTransformation>()
        val unhandled = mutableListOf<AppliedTransformation>()

        for (point in points) {
            val bounds = workingFrame.pixelBounds(point.region.bounds)
            val (originalBounds, originalChecksum) =
                beforeChecksums.getValue(point)
            val effectiveTransform = point.effective

            val afterChecksum = workingFrame.regionChecksum(originalBounds)
            val changed = afterChecksum != originalChecksum

            if (!changed &&
                (effectiveTransform == PrivacyTransformation.BLUR ||
                    effectiveTransform == PrivacyTransformation.PIXELATE)
            ) {
                // No-op on the content (e.g. uniform region):
                // escalate to the deterministic marking pattern.
                PrivacyTransformers.mask(workingFrame, bounds)
                val escalatedChecksum =
                    workingFrame.regionChecksum(originalBounds)
                if (escalatedChecksum != originalChecksum) {
                    handled += remember(point, PrivacyTransformation.MASK)
                } else {
                    unhandled += remember(point, effectiveTransform)
                }
            } else if (changed) {
                handled += remember(point, effectiveTransform)
            } else {
                unhandled += remember(point, effectiveTransform)
            }
        }

        val status = when {
            unhandled.isEmpty() ->
                PrivacySanitizationStatus.SANITIZED
            handled.isEmpty() ->
                PrivacySanitizationStatus.SANITIZATION_FAILED
            else ->
                PrivacySanitizationStatus.PARTIALLY_SANITIZED
        }

        val evidenceLoss = if (handled.isNotEmpty() || cropRecords.isNotEmpty()) {
            PrivacyEvidenceLoss.EVIDENCE_LOST_DUE_TO_SANITIZATION
        } else {
            PrivacyEvidenceLoss.NONE
        }

        val transformations = cropRecords + handled + unhandled

        return buildResult(
            frameId = null,
            timestampMs = timestampMs,
            ocrAvailability = ocrAvailability,
            ordered = ordered,
            enabledCount = enabled.size,
            risk = risk,
            confidence = confidence,
            status = status,
            safeFrame = workingFrame,
            transformations = transformations,
            dropped = false,
            evidenceLoss = evidenceLoss
        )
    }

    private fun remember(
        point: Plan,
        transform: PrivacyTransformation
    ): AppliedTransformation {
        val signal = point.region.signals.firstOrNull()
            ?: PrivacyDetectionSignal.LOW_CONFIDENCE_FALLBACK
        return AppliedTransformation(
            regionType = point.region.type,
            transformation = transform,
            signal = signal,
            confidence = PrivacyConfidence.forSignal(signal)
        )
    }

    /*
     * Approximate fraction of the frame covered by the ENABLED
     * region set (sum of band areas, clamped to 1.0). Overlaps
     * inflate slightly; documented approximation. Used only for
     * the drop heuristic, never reported as a metric.
     */
    private fun occupiedFraction(
        frame: PrivacyFrame,
        regions: List<PrivacyRegion>
    ): Double {
        var covered = 0.0
        for (region in regions) {
            val bounds = frame.pixelBounds(region.bounds)
            covered += (bounds.width.toLong() * bounds.height.toLong())
        }
        val total = frame.area.toLong()
        if (total == 0L) return 0.0
        return (covered.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun buildResult(
        frameId: String?,
        timestampMs: Long,
        ocrAvailability: OcrAvailability,
        ordered: List<PrivacyRegion>,
        enabledCount: Int,
        risk: PrivacyRisk,
        confidence: PrivacyConfidence,
        status: PrivacySanitizationStatus,
        safeFrame: PrivacyFrame?,
        transformations: List<AppliedTransformation>,
        dropped: Boolean,
        evidenceLoss: PrivacyEvidenceLoss
    ): AnonymizationResult {
        val fId = frameId ?: "frame-${timestampMs}"
        val decision = PrivacyDecision(
            frameId = fId,
            timestampMs = timestampMs,
            policyVersion = policy.policyVersion,
            processingVersion = PrivacySanitizationVersion.PROCESSING,
            rulesVersion = PrivacySanitizationVersion.RULES,
            ocrAvailability = ocrAvailability,
            regionsDetected = ordered.size,
            regionsEnabled = enabledCount,
            risk = risk,
            confidence = confidence,
            status = status,
            transformations = transformations,
            dropped = dropped,
            evidenceLoss = evidenceLoss
        )
        return AnonymizationResult(decision, safeFrame)
    }

    private data class Plan(
        val region: PrivacyRegion,
        val proposed: PrivacyTransformation,
        val effective: PrivacyTransformation
    )

    companion object {
        /*
         * Below this remaining research-value fraction a frame may
         * be dropped (spec §13): when sensitive content covers
         * ~90%+ of the screen there is little research value left.
         */
        const val DROP_VALUE_THRESHOLD = 0.1
    }
}