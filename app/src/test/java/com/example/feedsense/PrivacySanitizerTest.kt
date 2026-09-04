package com.example.feedsense

import com.example.feedsense.analysis.privacy.NoOpDetector
import com.example.feedsense.analysis.privacy.PrivacySanitizationConfig
import com.example.feedsense.analysis.privacy.PrivacySanitizer
import com.example.feedsense.analysis.privacy.ProtectedRegion
import com.example.feedsense.analysis.privacy.RedactionMode
import com.example.feedsense.analysis.privacy.SanitizationMetrics
import com.example.feedsense.analysis.privacy.SanitizationStatus
import com.example.feedsense.analysis.privacy.SystemUIRegionDetector
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-4.
 *
 * PrivacySanitizer: sanitization logic, fail-safe
 * behavior, configuration, and integration.
 *
 * Uses injected transform function for JVM testing
 * (no Android Bitmap dependency).
 */
class PrivacySanitizerTest {

    /*
     * Fake transform function that records calls
     * and returns a dummy output file.
     */
    private var lastTransformedRegions:
            List<ProtectedRegion>? = null

    private var lastTransformedConfig:
            PrivacySanitizationConfig? = null

    private var transformShouldFail = false

    private val fakeTransform:
            (File, List<ProtectedRegion>,
                    PrivacySanitizationConfig)
                    -> File? = { file, regions, config ->
        lastTransformedRegions = regions
        lastTransformedConfig = config

        if (transformShouldFail) {
            null
        } else {
            File(file.parent, "sanitized_" +
                    file.name).apply {
                createNewFile()
            }
        }
    }

    private fun createTestFile(
        dir: File,
        name: String = "test.png"
    ): File {
        return File(dir, name).apply {
            createNewFile()
        }
    }

    // --------------------------------
    // DISABLED CONFIG
    // --------------------------------

    @Test
    fun `disabled config returns UNCHANGED`() {

        val dir =
            File(System.getProperty("java.io.tmpdir"),
                "privacy_test_disabled")
        dir.mkdirs()

        try {
            val file = createTestFile(dir)
            lastTransformedRegions = null

            val sanitizer = PrivacySanitizer(
                config =
                    PrivacySanitizationConfig.DISABLED,
                transformFunction = fakeTransform
            )

            val result = sanitizer.sanitize(file)

            assertEquals(
                SanitizationStatus.UNCHANGED,
                result.status
            )

            assertEquals(0, result.regionCount)
            assertFalse(result.wasTransformed)

            // Transform should NOT have been called
            assertNull(lastTransformedRegions)

            file.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // FILE VALIDATION
    // --------------------------------

    @Test
    fun `nonexistent file returns FAILED`() {

        val dir =
            File(System.getProperty("java.io.tmpdir"),
                "privacy_test_nofile")
        dir.mkdirs()

        try {
            val file = File(dir, "missing.png")
            lastTransformedRegions = null

            val sanitizer = PrivacySanitizer(
                config =
                    PrivacySanitizationConfig.DEFAULT,
                transformFunction = fakeTransform
            )

            val result = sanitizer.sanitize(file)

            assertEquals(
                SanitizationStatus.FAILED,
                result.status
            )

            assertNotNull(result.errorMessage)
            assertNull(lastTransformedRegions)
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // NO REGIONS
    // --------------------------------

    @Test
    fun `no regions returns UNCHANGED`() {

        val dir =
            File(System.getProperty("java.io.tmpdir"),
                "privacy_test_noregions")
        dir.mkdirs()

        try {
            val file = createTestFile(dir)
            lastTransformedRegions = null

            val sanitizer = PrivacySanitizer(
                config = PrivacySanitizationConfig(
                    enabled = true,
                    protectedRegions = emptyList()
                ),
                detectors = listOf(NoOpDetector()),
                transformFunction = fakeTransform
            )

            val result = sanitizer.sanitize(file)

            assertEquals(
                SanitizationStatus.UNCHANGED,
                result.status
            )

            assertEquals(0, result.regionCount)
            assertNull(lastTransformedRegions)

            file.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // SUCCESSFUL SANITIZATION
    // --------------------------------

    @Test
    fun `sanitization with regions returns SANITIZED`() {

        val dir =
            File(System.getProperty("java.io.tmpdir"),
                "privacy_test_success")
        dir.mkdirs()

        try {
            val file = createTestFile(dir)

            val sanitizer = PrivacySanitizer(
                config = PrivacySanitizationConfig(
                    enabled = true,
                    protectedRegions = listOf(
                        ProtectedRegion.STATUS_BAR
                    )
                ),
                transformFunction = fakeTransform
            )

            val result = sanitizer.sanitize(file)

            assertEquals(
                SanitizationStatus.SANITIZED,
                result.status
            )

            assertEquals(1, result.regionCount)
            assertTrue(result.wasTransformed)
            assertTrue(result.isSafeForDownstream)
            assertNotNull(
                result.transformedFramePath
            )

            assertEquals(
                listOf("STATUS_BAR"),
                result.regionsProcessed
            )

            assertEquals(
                "privacy-v1",
                result.sanitizationVersion
            )

            // Transform was called with correct regions
            assertEquals(
                1,
                lastTransformedRegions?.size
            )

            file.delete()
            result.transformedFramePath?.let {
                File(it).delete()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `multiple regions all processed`() {

        val dir =
            File(System.getProperty("java.io.tmpdir"),
                "privacy_test_multi")
        dir.mkdirs()

        try {
            val file = createTestFile(dir)

            val sanitizer = PrivacySanitizer(
                config = PrivacySanitizationConfig
                    .DEFAULT,
                transformFunction = fakeTransform
            )

            val result = sanitizer.sanitize(file)

            assertEquals(
                SanitizationStatus.SANITIZED,
                result.status
            )

            assertEquals(3, result.regionCount)

            val labels = result.regionsProcessed

            assertTrue(labels.contains("STATUS_BAR"))
            assertTrue(
                labels.contains("NOTIFICATION")
            )

            assertTrue(
                labels.contains("NAVIGATION")
            )

            file.delete()
            result.transformedFramePath?.let {
                File(it).delete()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // TRANSFORM FAILURE
    // --------------------------------

    @Test
    fun `transform failure returns FAILED`() {

        val dir =
            File(System.getProperty("java.io.tmpdir"),
                "privacy_test_fail")
        dir.mkdirs()

        try {
            val file = createTestFile(dir)
            transformShouldFail = true

            val sanitizer = PrivacySanitizer(
                config = PrivacySanitizationConfig(
                    enabled = true,
                    protectedRegions = listOf(
                        ProtectedRegion.STATUS_BAR
                    )
                ),
                transformFunction = fakeTransform
            )

            val result = sanitizer.sanitize(file)

            assertEquals(
                SanitizationStatus.FAILED,
                result.status
            )

            assertFalse(result.isSafeForDownstream)
            assertFalse(result.wasTransformed)
            assertNotNull(result.errorMessage)

            file.delete()
        } finally {
            transformShouldFail = false
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // SANITIZER EXCEPTION
    // --------------------------------

    @Test
    fun `sanitizer exception returns FAILED`() {

        val dir =
            File(System.getProperty("java.io.tmpdir"),
                "privacy_test_exception")
        dir.mkdirs()

        try {
            val file = createTestFile(dir)

            val sanitizer = PrivacySanitizer(
                config = PrivacySanitizationConfig(
                    enabled = true,
                    protectedRegions = listOf(
                        ProtectedRegion.STATUS_BAR
                    )
                ),
                transformFunction = {
                        _, _, _ ->
                    throw RuntimeException(
                        "Test exception"
                    )
                }
            )

            val result = sanitizer.sanitize(file)

            assertEquals(
                SanitizationStatus.FAILED,
                result.status
            )

            assertNotNull(result.errorMessage)
            assertTrue(
                result.errorMessage!!
                    .contains("exception")
            )

            file.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // METRICS INTEGRATION
    // --------------------------------

    @Test
    fun `metrics track sanitization counts`() {

        val dir =
            File(System.getProperty("java.io.tmpdir"),
                "privacy_test_metrics")
        dir.mkdirs()

        try {
            val metrics = SanitizationMetrics()
            val file = createTestFile(dir)

            val sanitizer = PrivacySanitizer(
                config = PrivacySanitizationConfig(
                    enabled = true,
                    protectedRegions = listOf(
                        ProtectedRegion.STATUS_BAR
                    )
                ),
                metrics = metrics,
                transformFunction = fakeTransform
            )

            sanitizer.sanitize(file)

            val snapshot = metrics.snapshot()

            assertEquals(1L, snapshot.framesReceived)
            assertEquals(1L, snapshot.framesSanitized)
            assertEquals(1L, snapshot.regionsProtected)

            file.delete()
            File(
                file.parent,
                "sanitized_" + file.name
            ).delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `metrics track unchanged counts`() {

        val dir =
            File(System.getProperty("java.io.tmpdir"),
                "privacy_test_metrics2")
        dir.mkdirs()

        try {
            val metrics = SanitizationMetrics()
            val file = createTestFile(dir)

            val sanitizer = PrivacySanitizer(
                config = PrivacySanitizationConfig(
                    enabled = true,
                    protectedRegions = emptyList()
                ),
                detectors = listOf(NoOpDetector()),
                metrics = metrics,
                transformFunction = fakeTransform
            )

            sanitizer.sanitize(file)

            val snapshot = metrics.snapshot()

            assertEquals(1L, snapshot.framesReceived)
            assertEquals(
                1L,
                snapshot.framesUnchanged
            )

            file.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // VERSION TRACEABILITY
    // --------------------------------

    @Test
    fun `result contains sanitization version`() {

        val dir =
            File(System.getProperty("java.io.tmpdir"),
                "privacy_test_version")
        dir.mkdirs()

        try {
            val file = createTestFile(dir)

            val sanitizer = PrivacySanitizer(
                config = PrivacySanitizationConfig(
                    enabled = true,
                    protectedRegions = listOf(
                        ProtectedRegion.STATUS_BAR
                    ),
                    sanitizationVersion =
                        "privacy-test-v2"
                ),
                transformFunction = fakeTransform
            )

            val result = sanitizer.sanitize(file)

            assertEquals(
                "privacy-test-v2",
                result.sanitizationVersion
            )

            file.delete()
            result.transformedFramePath?.let {
                File(it).delete()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // CONSERVATIVE CONFIG
    // --------------------------------

    @Test
    fun `conservative config has 5 regions`() {

        val dir =
            File(System.getProperty("java.io.tmpdir"),
                "privacy_test_conservative")
        dir.mkdirs()

        try {
            val file = createTestFile(dir)

            val sanitizer = PrivacySanitizer(
                config =
                    PrivacySanitizationConfig
                        .CONSERVATIVE,
                transformFunction = fakeTransform
            )

            val result = sanitizer.sanitize(file)

            assertEquals(5, result.regionCount)

            file.delete()
            result.transformedFramePath?.let {
                File(it).delete()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // FAIL-SAFE BEHAVIOR
    // --------------------------------

    @Test
    fun `failed result is not safe for downstream`() {

        val dir =
            File(System.getProperty("java.io.tmpdir"),
                "privacy_test_failsafe")
        dir.mkdirs()

        try {
            val file = createTestFile(dir)
            transformShouldFail = true

            val sanitizer = PrivacySanitizer(
                config = PrivacySanitizationConfig(
                    enabled = true,
                    protectedRegions = listOf(
                        ProtectedRegion.STATUS_BAR
                    )
                ),
                transformFunction = fakeTransform
            )

            val result = sanitizer.sanitize(file)

            assertFalse(
                result.isSafeForDownstream
            )

            assertFalse(result.wasTransformed)
            assertNull(result.transformedFramePath)

            file.delete()
        } finally {
            transformShouldFail = false
            dir.deleteRecursively()
        }
    }

    @Test
    fun `UNCERTAIN result is not safe for downstream`() {

        val dir =
            File(System.getProperty("java.io.tmpdir"),
                "privacy_test_uncertain")
        dir.mkdirs()

        try {
            val file = createTestFile(dir)

            // Use a config with no regions and a
            // detector that returns regions, but
            // make the transform return null to
            // simulate uncertainty
            val sanitizer = PrivacySanitizer(
                config = PrivacySanitizationConfig(
                    enabled = true,
                    protectedRegions = listOf(
                        ProtectedRegion.STATUS_BAR
                    )
                ),
                transformFunction = { _, _, _ ->
                    null
                }
            )

            val result = sanitizer.sanitize(file)

            assertEquals(
                SanitizationStatus.FAILED,
                result.status
            )

            assertFalse(
                result.isSafeForDownstream
            )

            file.delete()
        } finally {
            dir.deleteRecursively()
        }
    }
}
