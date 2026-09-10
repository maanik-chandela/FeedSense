package com.example.feedsense.analysis.ml.preprocess.golden

import com.example.feedsense.analysis.ml.ModelInput
import com.example.feedsense.analysis.ml.preprocess.PreprocessResult
import com.example.feedsense.analysis.ml.preprocess.PreprocessedInput
import kotlin.math.abs
import kotlin.math.max

// --------------------------------
// GOLDEN COMPARATOR (8B-15-4 §6, §11)
// --------------------------------
//
// Compares an actual preprocessing result against a golden
// expectation, producing a structured comparison result with
// detailed diagnostics on mismatch.
//
// Two comparison modes:
//   EXACT: byte-level comparison of the tensor hash (preferred)
//   FLOAT_TOLERANT: per-element comparison with a documented
//     tolerance for floating-point arithmetic differences
//
// The comparator NEVER auto-updates golden values. A mismatch is
// always reported as a failure that a human must inspect.

/**
 * Structured result of comparing actual output against golden
 * expectations.
 */
sealed class GoldenComparisonResult {
    data class Match(
        val fixtureId: String,
        val tensorHashMatch: Boolean,
        val fieldMatches: List<String>
    ) : GoldenComparisonResult()

    data class Mismatch(
        val fixtureId: String,
        val diagnostics: GoldenDiagnostics
    ) : GoldenComparisonResult()
}

/**
 * Detailed diagnostics for a golden test failure.
 *
 * Designed to be human-readable and machine-parseable. Contains
 * enough information to identify the root cause without dumping
 * enormous tensors into test output.
 */
data class GoldenDiagnostics(
    val fixtureId: String,
    val expectedWidth: Int?,
    val actualWidth: Int?,
    val expectedHeight: Int?,
    val actualHeight: Int?,
    val expectedChannels: Int?,
    val actualChannels: Int?,
    val expectedTensorType: String?,
    val actualTensorType: String?,
    val expectedHash: String?,
    val actualHash: String?,
    val expectedFingerprint: String?,
    val actualFingerprint: String?,
    val expectedFailureCode: com.example.feedsense.analysis.ml.preprocess.PreprocessFailureCode?,
    val actualFailureCode: com.example.feedsense.analysis.ml.preprocess.PreprocessFailureCode?,
    val expectedSanitizationStatus: String?,
    val actualSanitizationStatus: String?,
    val firstDifferingIndex: Int?,
    val totalDifferingElements: Int?,
    val maxAbsoluteDifference: Float?,
    val expectedValue: Float?,
    val actualValue: Float?,
    val mismatches: List<String> = emptyList()
) {
    fun summary(): String {
        val sb = StringBuilder()
        sb.appendLine("=== GOLDEN FIXTURE MISMATCH: $fixtureId ===")
        if (expectedWidth != actualWidth) sb.appendLine("  width: expected=$expectedWidth actual=$actualWidth")
        if (expectedHeight != actualHeight) sb.appendLine("  height: expected=$expectedHeight actual=$actualHeight")
        if (expectedChannels != actualChannels) sb.appendLine("  channels: expected=$expectedChannels actual=$actualChannels")
        if (expectedTensorType != actualTensorType) sb.appendLine("  tensorType: expected=$expectedTensorType actual=$actualTensorType")
        if (expectedHash != null && actualHash != null && expectedHash != actualHash) {
            sb.appendLine("  tensorHash: expected=$expectedHash")
            sb.appendLine("  tensorHash: actual  =$actualHash")
        }
        if (expectedFingerprint != null && actualFingerprint != null && expectedFingerprint != actualFingerprint) {
            sb.appendLine("  fingerprint: expected=$expectedFingerprint")
            sb.appendLine("  fingerprint: actual  =$actualFingerprint")
        }
        if (expectedFailureCode != actualFailureCode) {
            sb.appendLine("  failureCode: expected=$expectedFailureCode actual=$actualFailureCode")
        }
        if (expectedSanitizationStatus != null && expectedSanitizationStatus != actualSanitizationStatus) {
            sb.appendLine("  sanitizationStatus: expected=$expectedSanitizationStatus actual=$actualSanitizationStatus")
        }
        if (firstDifferingIndex != null) {
            sb.appendLine("  firstDifferingIndex=$firstDifferingIndex")
            sb.appendLine("  totalDifferingElements=$totalDifferingElements")
            sb.appendLine("  maxAbsoluteDifference=$maxAbsoluteDifference")
            if (expectedValue != null && actualValue != null) {
                sb.appendLine("  expectedValue[$firstDifferingIndex]=$expectedValue")
                sb.appendLine("  actualValue[$firstDifferingIndex]=$actualValue")
            }
        }
        for (mismatch in mismatches) {
            sb.appendLine("  $mismatch")
        }
        return sb.toString()
    }
}

/**
 * Compares a preprocessing result against a golden expectation.
 */
object GoldenComparator {

    /**
     * Compares the actual preprocessing result against the
     * expected golden values.
     */
    fun compare(
        fixture: GoldenFixture,
        result: PreprocessResult,
        actualFingerprint: String? = null
    ): GoldenComparisonResult {
        val expectation = fixture.expectation

        // Handle expected failure case
        if (expectation.expectedFailureCode != null) {
            return compareExpectedFailure(fixture, result)
        }

        // Handle expected success case
        if (result is PreprocessResult.Failure) {
            return GoldenComparisonResult.Mismatch(
                fixtureId = fixture.fixtureId,
                diagnostics = GoldenDiagnostics(
                    fixtureId = fixture.fixtureId,
                    expectedWidth = expectation.expectedWidth,
                    actualWidth = null,
                    expectedHeight = expectation.expectedHeight,
                    actualHeight = null,
                    expectedChannels = expectation.expectedChannels,
                    actualChannels = null,
                    expectedTensorType = expectation.expectedTensorType,
                    actualTensorType = null,
                    expectedHash = expectation.expectedTensorHash,
                    actualHash = null,
                    expectedFingerprint = expectation.expectedFingerprint,
                    actualFingerprint = null,
                    expectedFailureCode = null,
                    actualFailureCode = result.code,
                    expectedSanitizationStatus = expectation.expectedSanitizationStatus,
                    actualSanitizationStatus = null,
                    firstDifferingIndex = null,
                    totalDifferingElements = null,
                    maxAbsoluteDifference = null,
                    expectedValue = null,
                    actualValue = null,
                    mismatches = listOf("expected success but got failure: ${result.code} - ${result.message}")
                )
            )
        }

        val output = (result as PreprocessResult.Success).output
        return compareOutput(fixture, output, actualFingerprint)
    }

    private fun compareExpectedFailure(
        fixture: GoldenFixture,
        result: PreprocessResult
    ): GoldenComparisonResult {
        val expectation = fixture.expectation
        if (result is PreprocessResult.Failure) {
            val mismatches = mutableListOf<String>()
            if (result.code != expectation.expectedFailureCode) {
                mismatches.add("failureCode: expected=${expectation.expectedFailureCode} actual=${result.code}")
            }
            if (expectation.expectedFailureMessageContains != null &&
                !result.message.contains(expectation.expectedFailureMessageContains)
            ) {
                mismatches.add(
                    "failureMessage does not contain '${expectation.expectedFailureMessageContains}': " +
                        "actual='${result.message}'"
                )
            }
            return if (mismatches.isEmpty()) {
                GoldenComparisonResult.Match(
                    fixtureId = fixture.fixtureId,
                    tensorHashMatch = true,
                    fieldMatches = listOf("failureCode=${result.code}", "failureMessage=${result.message}")
                )
            } else {
                GoldenComparisonResult.Mismatch(
                    fixtureId = fixture.fixtureId,
                    diagnostics = GoldenDiagnostics(
                        fixtureId = fixture.fixtureId,
                        expectedWidth = null,
                        actualWidth = null,
                        expectedHeight = null,
                        actualHeight = null,
                        expectedChannels = null,
                        actualChannels = null,
                        expectedTensorType = null,
                        actualTensorType = null,
                        expectedHash = null,
                        actualHash = null,
                        expectedFingerprint = null,
                        actualFingerprint = null,
                        expectedFailureCode = expectation.expectedFailureCode,
                        actualFailureCode = result.code,
                        expectedSanitizationStatus = null,
                        actualSanitizationStatus = null,
                        firstDifferingIndex = null,
                        totalDifferingElements = null,
                        maxAbsoluteDifference = null,
                        expectedValue = null,
                        actualValue = null,
                        mismatches = mismatches
                    )
                )
            }
        } else {
            return GoldenComparisonResult.Mismatch(
                fixtureId = fixture.fixtureId,
                diagnostics = GoldenDiagnostics(
                    fixtureId = fixture.fixtureId,
                    expectedWidth = null,
                    actualWidth = null,
                    expectedHeight = null,
                    actualHeight = null,
                    expectedChannels = null,
                    actualChannels = null,
                    expectedTensorType = null,
                    actualTensorType = null,
                    expectedHash = null,
                    actualHash = null,
                    expectedFingerprint = null,
                    actualFingerprint = null,
                    expectedFailureCode = expectation.expectedFailureCode,
                    actualFailureCode = null,
                    expectedSanitizationStatus = null,
                    actualSanitizationStatus = null,
                    firstDifferingIndex = null,
                    totalDifferingElements = null,
                    maxAbsoluteDifference = null,
                    expectedValue = null,
                    actualValue = null,
                    mismatches = listOf("expected failure (${expectation.expectedFailureCode}) but preprocessing succeeded")
                )
            )
        }
    }

    private fun compareOutput(
        fixture: GoldenFixture,
        output: PreprocessedInput,
        actualFingerprint: String?
    ): GoldenComparisonResult {
        val expectation = fixture.expectation
        val mismatches = mutableListOf<String>()
        var firstDiffIdx: Int? = null
        var totalDiff = 0
        var maxDiff = 0.0f
        var diffExpected: Float? = null
        var diffActual: Float? = null

        // Shape checks
        if (output.input.width != expectation.expectedWidth) {
            mismatches.add("width: expected=${expectation.expectedWidth} actual=${output.input.width}")
        }
        if (output.input.height != expectation.expectedHeight) {
            mismatches.add("height: expected=${expectation.expectedHeight} actual=${output.input.height}")
        }
        if (output.input.channels != expectation.expectedChannels) {
            mismatches.add("channels: expected=${expectation.expectedChannels} actual=${output.input.channels}")
        }

        // Layout
        if (output.input.layout != expectation.expectedLayout) {
            mismatches.add("layout: expected=${expectation.expectedLayout} actual=${output.input.layout}")
        }

        // Sanitization status
        if (expectation.expectedSanitizationStatus != null &&
            output.sanitizationStatus != expectation.expectedSanitizationStatus
        ) {
            mismatches.add(
                "sanitizationStatus: expected=${expectation.expectedSanitizationStatus} " +
                    "actual=${output.sanitizationStatus}"
            )
        }

        // Fingerprint
        if (expectation.expectedFingerprint != null && actualFingerprint != null &&
            expectation.expectedFingerprint != actualFingerprint
        ) {
            mismatches.add(
                "fingerprint: expected=${expectation.expectedFingerprint} actual=$actualFingerprint"
            )
        }

        // Tensor hash comparison
        val actualHash = GoldenHasher.hashTensor(output.input)
        val hashMatch = expectation.expectedTensorHash == null || expectation.expectedTensorHash == actualHash
        if (expectation.expectedTensorHash != null && !hashMatch) {
            mismatches.add(
                "tensorHash: expected=${expectation.expectedTensorHash} actual=$actualHash"
            )
        }

        // Per-element comparison when hash mismatches (for diagnostics)
        if (!hashMatch && expectation.comparisonPolicy == ComparisonPolicy.FLOAT_TOLERANT) {
            // We cannot do per-element without expected values, so report hash mismatch
            mismatches.add("float-tolerant comparison: hash mismatch, per-element diff requires expected values")
        }

        // Check expected float array if provided
        if (expectation.expectedTensorHash == null && expectation.tensorSize == output.tensorSize) {
            // No hash provided; structural match is the primary check
        }

        return if (mismatches.isEmpty()) {
            GoldenComparisonResult.Match(
                fixtureId = fixture.fixtureId,
                tensorHashMatch = hashMatch,
                fieldMatches = listOf(
                    "width=${output.input.width}",
                    "height=${output.input.height}",
                    "channels=${output.input.channels}",
                    "tensorSize=${output.tensorSize}",
                    "layout=${output.input.layout}",
                    "hash=$actualHash"
                )
            )
        } else {
            GoldenComparisonResult.Mismatch(
                fixtureId = fixture.fixtureId,
                diagnostics = GoldenDiagnostics(
                    fixtureId = fixture.fixtureId,
                    expectedWidth = expectation.expectedWidth,
                    actualWidth = output.input.width,
                    expectedHeight = expectation.expectedHeight,
                    actualHeight = output.input.height,
                    expectedChannels = expectation.expectedChannels,
                    actualChannels = output.input.channels,
                    expectedTensorType = expectation.expectedTensorType,
                    actualTensorType = output.input.tensorType.label,
                    expectedHash = expectation.expectedTensorHash,
                    actualHash = actualHash,
                    expectedFingerprint = expectation.expectedFingerprint,
                    actualFingerprint = actualFingerprint,
                    expectedFailureCode = null,
                    actualFailureCode = null,
                    expectedSanitizationStatus = expectation.expectedSanitizationStatus,
                    actualSanitizationStatus = output.sanitizationStatus,
                    firstDifferingIndex = firstDiffIdx,
                    totalDifferingElements = totalDiff,
                    maxAbsoluteDifference = maxDiff,
                    expectedValue = diffExpected,
                    actualValue = diffActual,
                    mismatches = mismatches
                )
            )
        }
    }

    /**
     * Detailed per-element float comparison for diagnostic
     * purposes. Returns the first differing index, total
     * differences, and maximum absolute difference.
     */
    fun diffFloats(
        expected: FloatArray,
        actual: FloatArray,
        tolerance: Float = 1e-6f
    ): FloatDiffResult {
        if (expected.size != actual.size) {
            return FloatDiffResult(
                identical = false,
                firstDifferingIndex = 0,
                totalDifferingElements = minOf(expected.size, actual.size),
                maxAbsoluteDifference = Float.MAX_VALUE
            )
        }
        var firstDiff: Int? = null
        var totalDiff = 0
        var maxDiff = 0.0f
        for (i in expected.indices) {
            val diff = abs(expected[i] - actual[i])
            if (diff > tolerance) {
                if (firstDiff == null) firstDiff = i
                totalDiff++
                if (diff > maxDiff) maxDiff = diff
            }
        }
        return FloatDiffResult(
            identical = totalDiff == 0,
            firstDifferingIndex = firstDiff,
            totalDifferingElements = totalDiff,
            maxAbsoluteDifference = maxDiff
        )
    }
}

data class FloatDiffResult(
    val identical: Boolean,
    val firstDifferingIndex: Int?,
    val totalDifferingElements: Int,
    val maxAbsoluteDifference: Float
)
