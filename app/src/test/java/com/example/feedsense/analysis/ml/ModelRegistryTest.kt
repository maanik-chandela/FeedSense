package com.example.feedsense.analysis.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-14.
 *
 * Registry of packaged on-device models. No network
 * acquisition, no downloading (sections 36-39).
 */
class ModelRegistryTest {

    private fun fakeModel(
        modelId: String,
        version: String = "ml-v1"
    ): FakeOnDeviceModel {
        return FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(modelId = modelId, modelVersion = version)
        )
    }

    @Test
    fun `register get and availability for a known id`() {
        val registry = ModelRegistry()
        val model = fakeModel("model-a")
        registry.register(model)
        assertSame(model, registry.get("model-a"))
        assertEquals(ModelState.AVAILABLE, registry.availability("model-a"))
        assertEquals(setOf("model-a"), registry.registeredModelIds)
    }

    @Test
    fun `unknown ids return null`() {
        val registry = ModelRegistry()
        assertNull(registry.get("missing"))
        assertNull(registry.availability("missing"))
        assertTrue(registry.all().isEmpty())
    }

    @Test
    fun `unregister removes the model without owning its lifecycle`() {
        val registry = ModelRegistry()
        val model = fakeModel("model-a")
        registry.register(model)
        val returned = registry.unregister("model-a")
        assertSame(model, returned)
        assertTrue(registry.all().isEmpty())
        // registry does NOT close the model - caller owns lifecycle
        assertEquals(ModelState.AVAILABLE, model.state)
    }

    @Test
    fun `ready models reflect loaded state`() {
        val registry = ModelRegistry()
        val ready = fakeModel("ready-model")
        ready.load()
        val notReady = fakeModel("lazy-model")
        registry.register(ready)
        registry.register(notReady)

        val readyModels = registry.readyModels()
        assertEquals(listOf(ready), readyModels)
        assertEquals(ModelState.READY, registry.availability("ready-model"))
        assertEquals(ModelState.AVAILABLE, registry.availability("lazy-model"))
    }

    @Test
    fun `registration order is preserved`() {
        val registry = ModelRegistry()
        val a = fakeModel("model-a")
        val b = fakeModel("model-b")
        val c = fakeModel("model-c")
        registry.register(a)
        registry.register(b)
        registry.register(c)
        assertEquals(listOf("model-a", "model-b", "model-c"),
            registry.all().map { it.metadata.modelId })
    }

    @Test
    fun `re-registering replaces the previous instance`() {
        val registry = ModelRegistry()
        val first = fakeModel("model-a")
        val second = fakeModel("model-a", version = "ml-v2")
        registry.register(first)
        registry.register(second)
        assertSame(second, registry.get("model-a"))
        assertEquals("ml-v2", registry.get("model-a")!!.metadata.modelVersion)
    }
}