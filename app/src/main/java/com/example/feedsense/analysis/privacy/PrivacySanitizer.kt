package com.example.feedsense.analysis.privacy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream

/*
 * Milestone 8B-4.
 *
 * Privacy sanitizer.
 *
 * Main entry point for the privacy sanitization layer.
 * Transforms captured frames to protect sensitive
 * regions before downstream AI/OCR processing.
 *
 * Pipeline position:
 *   Capture -> 8B-3 Sampling/Dedup -> 8B-4 Sanitize -> AI
 *
 * The sanitizer:
 *   1. Reads the captured frame
 *   2. Detects/identifies sensitive regions
 *   3. Applies configured redaction/blur
 *   4. Writes sanitized frame to working directory
 *   5. Returns result with metadata
 *
 * Design principles:
 *   - Fail-safe: errors produce FAILED status, not
 *     silently sanitized frames
 *   - Deterministic: same input + same config = same output
 *   - Auditable: metadata traces which rules were applied
 *   - Testable: injectable transform function for JVM tests
 *
 * Limitations:
 *   - Redaction uses solid overlay (not encryption)
 *   - Blur strength is visual, not cryptographic
 *   - Does not detect content-based sensitive regions
 *   - Does not bypass FLAG_SECURE
 */
class PrivacySanitizer(
    private val context: Context? = null,
    private val config: PrivacySanitizationConfig =
        PrivacySanitizationConfig.DEFAULT,
    private val detectors:
        List<PrivacyRegionDetector> = emptyList(),
    private val metrics: SanitizationMetrics =
        SanitizationMetrics(),
    /*
     * Injectable transform function for JVM testing.
     * When null, uses Android Bitmap API.
     * Signature: (inputFile, regions, config) -> outputFile?
     */
    private val transformFunction:
        ((File, List<ProtectedRegion>,
                PrivacySanitizationConfig)
                -> File?)? = null
) {

    /*
     * Sanitize a captured frame.
     *
     * @param frameFile The captured frame image.
     * @param outputDir Directory for sanitized output.
     *   If null, uses a temp directory.
     * @return SanitizationResult with status and metadata.
     */
    fun sanitize(
        frameFile: File,
        outputDir: File? = null
    ): SanitizationResult {

        metrics.recordReceived()

        // --------------------------------
        // DISABLED CHECK
        // --------------------------------

        if (!config.enabled) {
            metrics.recordUnchanged()

            return SanitizationResult(
                status =
                    SanitizationStatus.UNCHANGED,
                regionsProcessed = emptyList(),
                regionCount = 0,
                sanitizationVersion =
                    config.sanitizationVersion,
                transformedFramePath = null
            )
        }

        // --------------------------------
        // FILE VALIDATION
        // --------------------------------

        if (!frameFile.exists() ||
            !frameFile.isFile
        ) {
            metrics.recordFailed()

            return SanitizationResult(
                status =
                    SanitizationStatus.FAILED,
                regionsProcessed = emptyList(),
                regionCount = 0,
                sanitizationVersion =
                    config.sanitizationVersion,
                transformedFramePath = null,
                errorMessage =
                    "Frame file does not exist " +
                            "or is not a file"
            )
        }

        // --------------------------------
        // COLLECT REGIONS
        // --------------------------------
        //
        // Combine configured static regions with
        // any detected regions from detectors.

        val allRegions =
            mutableListOf<ProtectedRegion>()

        allRegions.addAll(
            config.protectedRegions
        )

        for (detector in detectors) {
            try {
                val detected =
                    detector.detect(
                        frameFile,
                        0,
                        0
                    )
                allRegions.addAll(detected)
            } catch (_: Exception) {
                // Detector failure: skip, continue
            }
        }

        if (allRegions.isEmpty()) {
            metrics.recordUnchanged()

            return SanitizationResult(
                status =
                    SanitizationStatus.UNCHANGED,
                regionsProcessed = emptyList(),
                regionCount = 0,
                sanitizationVersion =
                    config.sanitizationVersion,
                transformedFramePath = null
            )
        }

        // --------------------------------
        // APPLY TRANSFORMATION
        // --------------------------------

        val startTimeNs =
            System.nanoTime()

        return try {
            val result =
                if (transformFunction != null) {
                    applyTransformViaFunction(
                        frameFile,
                        allRegions,
                        outputDir
                    )
                } else {
                    applyTransformNative(
                        frameFile,
                        allRegions,
                        outputDir
                    )
                }

            val elapsedNs =
                System.nanoTime() - startTimeNs

            metrics.recordTiming(elapsedNs)

            if (result.status ==
                SanitizationStatus.SANITIZED
            ) {
                metrics.recordSanitized(
                    result.regionCount
                )
            } else if (
                result.status ==
                SanitizationStatus.UNCHANGED
            ) {
                metrics.recordUnchanged()
            } else if (
                result.status ==
                SanitizationStatus.FAILED
            ) {
                metrics.recordFailed()
            } else {
                metrics.recordUncertain()
            }

            result

        } catch (exception: Exception) {

            val elapsedNs =
                System.nanoTime() - startTimeNs

            metrics.recordTiming(elapsedNs)
            metrics.recordFailed()

            SanitizationResult(
                status =
                    SanitizationStatus.FAILED,
                regionsProcessed = emptyList(),
                regionCount = 0,
                sanitizationVersion =
                    config.sanitizationVersion,
                transformedFramePath = null,
                errorMessage =
                    "Sanitization exception: " +
                        exception.message
            )
        }
    }

    // --------------------------------
    // INJECTABLE TRANSFORM
    // --------------------------------

    private fun applyTransformViaFunction(
        frameFile: File,
        regions: List<ProtectedRegion>,
        outputDir: File?
    ): SanitizationResult {

        val transformed =
            transformFunction?.invoke(
                frameFile, regions, config
            )

        return if (transformed != null &&
            transformed.exists()
        ) {
            SanitizationResult(
                status =
                    SanitizationStatus.SANITIZED,
                regionsProcessed =
                    regions.map { it.label },
                regionCount = regions.size,
                sanitizationVersion =
                    config.sanitizationVersion,
                transformedFramePath =
                    transformed.absolutePath
            )
        } else {
            SanitizationResult(
                status =
                    SanitizationStatus.FAILED,
                regionsProcessed = emptyList(),
                regionCount = 0,
                sanitizationVersion =
                    config.sanitizationVersion,
                transformedFramePath = null,
                errorMessage =
                    "Transform function returned null"
            )
        }
    }

    // --------------------------------
    // NATIVE ANDROID TRANSFORM
    // --------------------------------

    private fun applyTransformNative(
        frameFile: File,
        regions: List<ProtectedRegion>,
        outputDir: File?
    ): SanitizationResult {

        if (context == null) {
            return SanitizationResult(
                status =
                    SanitizationStatus.FAILED,
                regionsProcessed = emptyList(),
                regionCount = 0,
                sanitizationVersion =
                    config.sanitizationVersion,
                transformedFramePath = null,
                errorMessage =
                    "No context available for " +
                            "native transform"
            )
        }

        val bitmap =
            BitmapFactory.decodeFile(
                frameFile.absolutePath
            )

        if (bitmap == null) {
            return SanitizationResult(
                status =
                    SanitizationStatus.FAILED,
                regionsProcessed = emptyList(),
                regionCount = 0,
                sanitizationVersion =
                    config.sanitizationVersion,
                transformedFramePath = null,
                errorMessage =
                    "Could not decode frame image"
            )
        }

        val workingBitmap =
            bitmap.copy(
                Bitmap.Config.ARGB_8888, true
            )

        bitmap.recycle()

        val canvas = Canvas(workingBitmap)
        val paint = Paint().apply {
            isAntiAlias = true
        }

        val processedLabels =
            mutableListOf<String>()

        for (region in regions) {

            val bounds =
                region.toPixelBounds(
                    workingBitmap.width,
                    workingBitmap.height
                )

            if (!bounds.isValid) continue

            when (config.redactionMode) {
                RedactionMode.REDACT -> {
                    paint.color = Color.BLACK
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(
                        bounds.left.toFloat(),
                        bounds.top.toFloat(),
                        bounds.right.toFloat(),
                        bounds.bottom.toFloat(),
                        paint
                    )
                }

                RedactionMode.BLUR -> {
                    // Pixelation-based blur that works
                    // on all API levels. Scales the
                    // region down and back up to create
                    // a strong pixelation effect.
                    val regionWidth =
                        bounds.right - bounds.left
                    val regionHeight =
                        bounds.bottom - bounds.top

                    if (regionWidth > 0 &&
                        regionHeight > 0
                    ) {
                        val pixelSize =
                            config.blurStrength

                        val scaledWidth =
                            (regionWidth /
                                    pixelSize)
                                .coerceAtLeast(1)

                        val scaledHeight =
                            (regionHeight /
                                    pixelSize)
                                .coerceAtLeast(1)

                        val regionBitmap =
                            Bitmap.createBitmap(
                                workingBitmap,
                                bounds.left,
                                bounds.top,
                                regionWidth,
                                regionHeight
                            )

                        val scaled =
                            Bitmap.createScaledBitmap(
                                regionBitmap,
                                scaledWidth,
                                scaledHeight,
                                true
                            )

                        val pixelated =
                            Bitmap.createScaledBitmap(
                                scaled,
                                regionWidth,
                                regionHeight,
                                false
                            )

                        canvas.drawBitmap(
                            pixelated,
                            bounds.left.toFloat(),
                            bounds.top.toFloat(),
                            null
                        )

                        regionBitmap.recycle()
                        scaled.recycle()
                        pixelated.recycle()
                    }
                }

                RedactionMode.NONE -> {
                    // Mark only, no transform
                }
            }

            processedLabels.add(bounds.label)
        }

        // --------------------------------
        // WRITE OUTPUT
        // --------------------------------

        val outDir =
            outputDir ?: context.cacheDir

        val outputFile =
            File(
                outDir,
                "sanitized_" +
                        frameFile.name
            )

        return try {
            FileOutputStream(outputFile)
                .use { fos ->
                    workingBitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        fos
                    )
                }

            workingBitmap.recycle()

            SanitizationResult(
                status =
                    SanitizationStatus.SANITIZED,
                regionsProcessed = processedLabels,
                regionCount = processedLabels.size,
                sanitizationVersion =
                    config.sanitizationVersion,
                transformedFramePath =
                    outputFile.absolutePath
            )
        } catch (exception: Exception) {

            workingBitmap.recycle()

            SanitizationResult(
                status =
                    SanitizationStatus.FAILED,
                regionsProcessed = processedLabels,
                regionCount = processedLabels.size,
                sanitizationVersion =
                    config.sanitizationVersion,
                transformedFramePath = null,
                errorMessage =
                    "Output write failed: " +
                        exception.message
            )
        }
    }
}
