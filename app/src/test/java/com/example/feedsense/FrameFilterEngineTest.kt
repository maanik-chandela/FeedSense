package com.example.feedsense

import com.example.feedsense.analysis.dedup.DeduplicationConfig
import com.example.feedsense.analysis.dedup.FrameFilterEngine
import com.example.feedsense.analysis.dedup.PerceptualHashEngine
import com.example.feedsense.analysis.dedup.RetentionDecision
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-3.
 *
 * FrameFilterEngine: filtering, force-keep, fail-open,
 * and session lifecycle.
 *
 * Uses injected hash functions for JVM testing (no
 * Android BitmapFactory dependency).
 */
class FrameFilterEngineTest {

    /*
     * Well-separated hash values for different file
     * names. Each name maps to a unique 16-char hex
     * string with large hamming distances between them.
     */
    private val hashValues = mapOf(
        "aaa" to "a0a0a0a0a0a0a0a0",
        "bbb" to "0b0b0b0b0b0b0b0b",
        "ccc" to "c0c0c0c0c0c0c0c0",
        "light" to "eeeeeeeeeeeeeeee",
        "dark" to "1111111111111111",
        "same" to "5555555555555555",
        "frame1" to "3333333333333333",
        "good" to "aaaaaaaaaaaaaaaa",
        "another" to "7777777777777777"
    )

    private fun fakeHashFunction(
        file: File
    ): String? {

        if (!file.exists()) return null

        val name =
            file.nameWithoutExtension

        return hashValues[name]
            ?: "0000000000000000"
    }

    /*
     * Hash function that fails for files whose name
     * starts with "bad", succeeds otherwise.
     */
    private val selectiveFailHash:
            (File) -> String? = { file ->
        if (
            file.nameWithoutExtension
                .startsWith("bad")
        ) {
            null
        } else {
            fakeHashFunction(file)
        }
    }

    // --------------------------------
    // FIRST FRAME
    // --------------------------------

    @Test
    fun `first frame is always retained`() {

        val dir =
            File(
                System.getProperty(
                    "java.io.tmpdir"
                ),
                "dedup_test_1"
            )

        dir.mkdirs()

        try {
            val file =
                File(dir, "frame1.png")
                    .apply { createNewFile() }

            val engine =
                FrameFilterEngine(
                    hashFunction =
                        ::fakeHashFunction
                )

            val result =
                engine.evaluateFrame(
                    file,
                    System.currentTimeMillis()
                )

            assertTrue(result.shouldRetain)
            assertEquals(
                "FIRST_FRAME",
                result.reason
            )
            assertEquals(
                RetentionDecision.DIFFERENT,
                result.decision
            )
            assertNotNull(result.currentHash)

            file.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // IDENTICAL FRAMES
    // --------------------------------

    @Test
    fun `identical frames are deduplicated`() {

        val dir =
            File(
                System.getProperty(
                    "java.io.tmpdir"
                ),
                "dedup_test_2"
            )

        dir.mkdirs()

        try {
            val file1 =
                File(dir, "same.png")
                    .apply { createNewFile() }

            val file2 =
                File(dir, "same.png")
                    .apply { createNewFile() }

            val engine =
                FrameFilterEngine(
                    hashFunction =
                        ::fakeHashFunction
                )

            val t =
                System.currentTimeMillis()

            val first =
                engine.evaluateFrame(file1, t)

            assertTrue(first.shouldRetain)

            val second =
                engine.evaluateFrame(
                    file2, t + 1000
                )

            assertFalse(second.shouldRetain)
            assertEquals(
                RetentionDecision.DEFINITELY_SAME,
                second.decision
            )

            file1.delete()
            file2.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // DIFFERENT FRAMES
    // --------------------------------

    @Test
    fun `different frames are retained`() {

        val dir =
            File(
                System.getProperty(
                    "java.io.tmpdir"
                ),
                "dedup_test_3"
            )

        dir.mkdirs()

        try {
            val file1 =
                File(dir, "light.png")
                    .apply { createNewFile() }

            val file2 =
                File(dir, "dark.png")
                    .apply { createNewFile() }

            val engine =
                FrameFilterEngine(
                    hashFunction =
                        ::fakeHashFunction
                )

            val t =
                System.currentTimeMillis()

            val first =
                engine.evaluateFrame(file1, t)

            assertTrue(first.shouldRetain)

            val second =
                engine.evaluateFrame(
                    file2, t + 1000
                )

            assertTrue(second.shouldRetain)
            assertEquals(
                RetentionDecision.DIFFERENT,
                second.decision
            )

            file1.delete()
            file2.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // FORCE-KEEP
    // --------------------------------

    @Test
    fun `force-keep triggers after interval`() {

        val dir =
            File(
                System.getProperty(
                    "java.io.tmpdir"
                ),
                "dedup_test_4"
            )

        dir.mkdirs()

        try {
            val file1 =
                File(dir, "same.png")
                    .apply { createNewFile() }

            val file2 =
                File(dir, "same.png")
                    .apply { createNewFile() }

            val config =
                DeduplicationConfig(
                    forceKeepIntervalMs = 5000L
                )

            val engine =
                FrameFilterEngine(
                    hashFunction =
                        ::fakeHashFunction,
                    config = config
                )

            val t =
                System.currentTimeMillis()

            engine.evaluateFrame(file1, t)

            // After force-keep interval, even identical
            // frame is retained
            val result =
                engine.evaluateFrame(
                    file2, t + 6000
                )

            assertTrue(result.shouldRetain)
            assertTrue(result.forceKept)
            assertEquals(
                "FORCE_KEEP_INTERVAL",
                result.reason
            )

            file1.delete()
            file2.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // DISABLED CONFIG
    // --------------------------------

    @Test
    fun `disabled config retains all frames`() {

        val dir =
            File(
                System.getProperty(
                    "java.io.tmpdir"
                ),
                "dedup_test_5"
            )

        dir.mkdirs()

        try {
            val file1 =
                File(dir, "same.png")
                    .apply { createNewFile() }

            val file2 =
                File(dir, "same.png")
                    .apply { createNewFile() }

            val engine =
                FrameFilterEngine(
                    hashFunction =
                        ::fakeHashFunction,
                    config =
                        DeduplicationConfig
                            .DISABLED
                )

            val t =
                System.currentTimeMillis()

            engine.evaluateFrame(file1, t)

            val result =
                engine.evaluateFrame(
                    file2, t + 1000
                )

            assertTrue(result.shouldRetain)
            assertEquals(
                "DEDUP_DISABLED",
                result.reason
            )

            file1.delete()
            file2.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // NONEXISTENT FILE (FAIL-OPEN)
    // --------------------------------

    @Test
    fun `hash failure on second frame retains`() {

        val dir =
            File(
                System.getProperty(
                    "java.io.tmpdir"
                ),
                "dedup_test_fail"
            )

        dir.mkdirs()

        try {
            val file1 =
                File(dir, "good.png")
                    .apply { createNewFile() }

            // First: retain a frame with normal hash
            val engine =
                FrameFilterEngine(
                    hashFunction =
                        selectiveFailHash
                )

            val t =
                System.currentTimeMillis()

            engine.evaluateFrame(file1, t)

            // Second frame: name starts with "bad",
            // hash fails
            val file2 =
                File(dir, "bad_frame.png")
                    .apply { createNewFile() }

            val result =
                engine.evaluateFrame(
                    file2, t + 1000
                )

            assertTrue(result.shouldRetain)
            assertEquals(
                "HASH_FAILURE",
                result.reason
            )

            file1.delete()
            file2.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // RESET
    // --------------------------------

    @Test
    fun `reset clears state`() {

        val dir =
            File(
                System.getProperty(
                    "java.io.tmpdir"
                ),
                "dedup_test_6"
            )

        dir.mkdirs()

        try {
            val file1 =
                File(dir, "same.png")
                    .apply { createNewFile() }

            val file2 =
                File(dir, "same.png")
                    .apply { createNewFile() }

            val engine =
                FrameFilterEngine(
                    hashFunction =
                        ::fakeHashFunction
                )

            val t =
                System.currentTimeMillis()

            engine.evaluateFrame(file1, t)

            // Same frame is deduplicated
            val deduped =
                engine.evaluateFrame(
                    file2, t + 1000
                )

            assertFalse(deduped.shouldRetain)

            // After reset, same frame is retained
            engine.reset()

            val retained =
                engine.evaluateFrame(
                    file2, t + 2000
                )

            assertTrue(retained.shouldRetain)

            file1.delete()
            file2.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // HASH PRESENT IN RESULT
    // --------------------------------

    @Test
    fun `result contains current hash`() {

        val dir =
            File(
                System.getProperty(
                    "java.io.tmpdir"
                ),
                "dedup_test_7"
            )

        dir.mkdirs()

        try {
            val file =
                File(dir, "frame1.png")
                    .apply { createNewFile() }

            val engine =
                FrameFilterEngine(
                    hashFunction =
                        ::fakeHashFunction
                )

            val result =
                engine.evaluateFrame(
                    file,
                    System.currentTimeMillis()
                )

            assertNotNull(result.currentHash)
            assertTrue(
                result.currentHash!!.isNotEmpty()
            )

            file.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // HAMMING DISTANCE PRESENT
    // --------------------------------

    @Test
    fun `second frame has hamming distance`() {

        val dir =
            File(
                System.getProperty(
                    "java.io.tmpdir"
                ),
                "dedup_test_8"
            )

        dir.mkdirs()

        try {
            val file1 =
                File(dir, "light.png")
                    .apply { createNewFile() }

            val file2 =
                File(dir, "dark.png")
                    .apply { createNewFile() }

            val engine =
                FrameFilterEngine(
                    hashFunction =
                        ::fakeHashFunction
                )

            val t =
                System.currentTimeMillis()

            engine.evaluateFrame(file1, t)

            val result =
                engine.evaluateFrame(
                    file2, t + 1000
                )

            assertNotNull(result.hammingDistance)
            assertTrue(
                result.hammingDistance!! > 0
            )

            file1.delete()
            file2.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // DETERMINISM
    // --------------------------------

    @Test
    fun `same inputs produce same decision`() {

        val dir =
            File(
                System.getProperty(
                    "java.io.tmpdir"
                ),
                "dedup_test_9"
            )

        dir.mkdirs()

        try {
            val file1 =
                File(dir, "same.png")
                    .apply { createNewFile() }

            val file2 =
                File(dir, "same.png")
                    .apply { createNewFile() }

            // Run 1
            val engine1 =
                FrameFilterEngine(
                    hashFunction =
                        ::fakeHashFunction
                )

            val t = 1000000L

            engine1.evaluateFrame(file1, t)
            val r1 =
                engine1.evaluateFrame(
                    file2, t + 1000
                )

            // Run 2 (fresh engine, same file names)
            val engine2 =
                FrameFilterEngine(
                    hashFunction =
                        ::fakeHashFunction
                )

            engine2.evaluateFrame(file1, t)
            val r2 =
                engine2.evaluateFrame(
                    file2, t + 1000
                )

            assertEquals(
                r1.decision,
                r2.decision
            )

            assertEquals(
                r1.shouldRetain,
                r2.shouldRetain
            )

            assertEquals(
                r1.currentHash,
                r2.currentHash
            )

            file1.delete()
            file2.delete()
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------
    // PREVIOUS HASH TRACKING
    // --------------------------------

    @Test
    fun `previousHash tracks last retained frame`() {

        val dir =
            File(
                System.getProperty(
                    "java.io.tmpdir"
                ),
                "dedup_test_prev"
            )

        dir.mkdirs()

        try {
            val file1 =
                File(dir, "aaa.png")
                    .apply { createNewFile() }

            val file2 =
                File(dir, "bbb.png")
                    .apply { createNewFile() }

            val file3 =
                File(dir, "ccc.png")
                    .apply { createNewFile() }

            val engine =
                FrameFilterEngine(
                    hashFunction =
                        ::fakeHashFunction
                )

            val t =
                System.currentTimeMillis()

            // First frame: no previous hash
            val first =
                engine.evaluateFrame(file1, t)

            assertNull(first.previousHash)

            // Second frame: previous = first
            val second =
                engine.evaluateFrame(
                    file2, t + 1000
                )

            assertEquals(
                first.currentHash,
                second.previousHash
            )

            // Third frame: previous = second
            val third =
                engine.evaluateFrame(
                    file3, t + 2000
                )

            assertEquals(
                second.currentHash,
                third.previousHash
            )

            file1.delete()
            file2.delete()
            file3.delete()
        } finally {
            dir.deleteRecursively()
        }
    }
}
