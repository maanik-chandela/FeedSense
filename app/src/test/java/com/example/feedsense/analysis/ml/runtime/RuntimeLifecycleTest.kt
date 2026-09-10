package com.example.feedsense.analysis.ml.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-5.
 *
 * Runtime lifecycle: state machine transitions, repeated
 * operations, inference-after-close.
 */
class RuntimeLifecycleTest {

    @Test
    fun `initial state is UNLOADED`() {
        val lifecycle = RuntimeLifecycleManager()
        assertEquals(RuntimeLifecycleState.UNLOADED, lifecycle.state)
        assertTrue(lifecycle.canInitialize)
        assertFalse(lifecycle.canInfer)
    }

    @Test
    fun `begin load transitions to LOADING`() {
        val lifecycle = RuntimeLifecycleManager()
        assertTrue(lifecycle.beginLoad())
        assertEquals(RuntimeLifecycleState.LOADING, lifecycle.state)
    }

    @Test
    fun `complete load transitions to READY`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        assertTrue(lifecycle.completeLoad())
        assertEquals(RuntimeLifecycleState.READY, lifecycle.state)
        assertTrue(lifecycle.canInfer)
    }

    @Test
    fun `fail load transitions to FAILED`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        assertTrue(lifecycle.failLoad())
        assertEquals(RuntimeLifecycleState.FAILED, lifecycle.state)
        assertFalse(lifecycle.canInfer)
    }

    @Test
    fun `can reinitialize from FAILED`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        lifecycle.failLoad()
        assertTrue(lifecycle.canInitialize)
        assertTrue(lifecycle.beginLoad())
    }

    @Test
    fun `begin inference transitions to INFERRING`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        lifecycle.completeLoad()
        assertTrue(lifecycle.beginInference())
        assertEquals(RuntimeLifecycleState.INFERRING, lifecycle.state)
        assertFalse(lifecycle.canInfer)
    }

    @Test
    fun `complete inference transitions to READY`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        lifecycle.completeLoad()
        lifecycle.beginInference()
        assertTrue(lifecycle.completeInference())
        assertEquals(RuntimeLifecycleState.READY, lifecycle.state)
    }

    @Test
    fun `release from READY transitions to UNLOADED`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        lifecycle.completeLoad()
        assertTrue(lifecycle.release())
        assertEquals(RuntimeLifecycleState.UNLOADED, lifecycle.state)
    }

    @Test
    fun `release from UNLOADED is no-op`() {
        val lifecycle = RuntimeLifecycleManager()
        assertTrue(lifecycle.release())
        assertEquals(RuntimeLifecycleState.UNLOADED, lifecycle.state)
    }

    @Test
    fun `release from FAILED transitions to UNLOADED`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        lifecycle.failLoad()
        assertTrue(lifecycle.release())
        assertEquals(RuntimeLifecycleState.UNLOADED, lifecycle.state)
    }

    @Test
    fun `inference not allowed from UNLOADED`() {
        val lifecycle = RuntimeLifecycleManager()
        assertFalse(lifecycle.beginInference())
    }

    @Test
    fun `inference not allowed from FAILED`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        lifecycle.failLoad()
        assertFalse(lifecycle.beginInference())
    }

    @Test
    fun `begin load not allowed from LOADING`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        assertFalse(lifecycle.beginLoad())
    }

    @Test
    fun `begin load not allowed from READY`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        lifecycle.completeLoad()
        assertFalse(lifecycle.beginLoad())
    }

    @Test
    fun `complete load not allowed from READY`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        lifecycle.completeLoad()
        assertFalse(lifecycle.completeLoad())
    }

    @Test
    fun `history tracks all transitions`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        lifecycle.completeLoad()
        lifecycle.beginInference()
        lifecycle.completeInference()
        lifecycle.release()
        val history = lifecycle.history()
        assertEquals(7, history.size)
        assertEquals(RuntimeLifecycleState.UNLOADED, history[0].first)
        assertEquals(RuntimeLifecycleState.LOADING, history[1].first)
        assertEquals(RuntimeLifecycleState.READY, history[2].first)
        assertEquals(RuntimeLifecycleState.INFERRING, history[3].first)
        assertEquals(RuntimeLifecycleState.READY, history[4].first)
        assertEquals(RuntimeLifecycleState.RELEASING, history[5].first)
        assertEquals(RuntimeLifecycleState.UNLOADED, history[6].first)
    }

    @Test
    fun `full lifecycle load infer release`() {
        val lifecycle = RuntimeLifecycleManager()
        assertTrue(lifecycle.beginLoad())
        assertTrue(lifecycle.completeLoad())
        assertTrue(lifecycle.beginInference())
        assertTrue(lifecycle.completeInference())
        assertTrue(lifecycle.release())
        assertEquals(RuntimeLifecycleState.UNLOADED, lifecycle.state)
    }

    @Test
    fun `full lifecycle load infer multiple release`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        lifecycle.completeLoad()
        for (i in 1..5) {
            assertTrue(lifecycle.beginInference())
            assertTrue(lifecycle.completeInference())
        }
        assertTrue(lifecycle.release())
        assertEquals(RuntimeLifecycleState.UNLOADED, lifecycle.state)
    }

    @Test
    fun `reset returns to initial state`() {
        val lifecycle = RuntimeLifecycleManager()
        lifecycle.beginLoad()
        lifecycle.completeLoad()
        lifecycle.reset()
        assertEquals(RuntimeLifecycleState.UNLOADED, lifecycle.state)
        assertEquals(1, lifecycle.history().size)
    }
}
