package com.example.feedsense.analysis.ml.repro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/*
 * Milestone 8B-15-2.
 *
 * RUNTIME identity: same metadata -> equal; changed runtime
 * version/backend -> different identity. The runtime version is
 * independent of the host developer machine.
 */
class ReproRuntimeIdentityTest {

    @Test
    fun `same runtime metadata produces equal identity`() {
        assertEquals(ReproFix.runtime, ReproFix.runtime.copy())
    }

    @Test
    fun `changed runtime version produces different identity`() {
        assertNotEquals(ReproFix.runtime, ReproFix.runtime.copy(runtimeVersion = "0.0.2"))
    }

    @Test
    fun `changed backend produces different identity`() {
        assertNotEquals(
            ReproFix.runtime,
            ReproFix.runtime.copy(executionBackend = ExecutionBackend.GPU_DELEGATE)
        )
    }

    @Test
    fun `changed runtime name produces different identity`() {
        assertNotEquals(
            ReproFix.runtime,
            ReproFix.runtime.copy(runtimeName = ReproRuntimeName.EXECUTORCH)
        )
    }

    @Test
    fun `same runtime produces identical canonical serialization`() {
        assertEquals(
            ReproCanonicalSerializer.serializeRuntime(ReproFix.runtime),
            ReproCanonicalSerializer.serializeRuntime(ReproFix.runtime.copy())
        )
    }

    @Test
    fun `blank runtime version is rejected`() {
        try {
            ReproFix.runtime.copy(runtimeVersion = "")
            throw AssertionError("blank runtime version must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `runtime is independent of an unspecified version slot`() {
        val unspec = ReproFix.runtime.copy(runtimeVersion = REPRO_UNSPECIFIED)
        assertEquals(REPRO_UNSPECIFIED, unspec.runtimeVersion)
    }
}