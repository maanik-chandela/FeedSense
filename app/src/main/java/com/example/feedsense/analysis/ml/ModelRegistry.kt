package com.example.feedsense.analysis.ml

// --------------------------------
// MODEL REGISTRY (8B-14)
// --------------------------------
//
// A small registry of packaged on-device models:
//
//     ModelRegistry
//       ├── ml-v1
//       └── future models (baseline stays out of this - the
//           baseline heuristic is NOT an ML runtime model)
//
// The registry NEVER downloads models and performs NO
// network acquisition (8B-14 sections 36, 38, 39). Models
// are packaged with the application / registered in code.
//
// Availability is exposed through the shared ModelState
// vocabulary so callers never inspect runtime exceptions to
// learn whether a model can serve a frame (section 37).

class ModelRegistry {

    private val models = LinkedHashMap<String, OnDeviceModel>()

    /*
     * Registers an already-constructed model instance.
     */
    fun register(model: OnDeviceModel) {
        models[model.metadata.modelId] = model
    }

    /*
     * Removes and returns the model, or null when absent.
     * Does not call close(): the caller owns the lifecycle.
     */
    fun unregister(modelId: String): OnDeviceModel? =
        models.remove(modelId)

    /*
     * Looks up a model by id.
     */
    fun get(modelId: String): OnDeviceModel? =
        models[modelId]

    /*
     * Availability of a registered model (null when the id
     * is unknown).
     */
    fun availability(modelId: String): ModelState? =
        models[modelId]?.state

    /*
     * ids of all registered models, in registration order.
     */
    val registeredModelIds: Set<String>
        get() = models.keys.toSet()

    /*
     * All registered models, in registration order.
     */
    fun all(): List<OnDeviceModel> =
        models.values.toList()

    /*
     * Models currently ready to serve inference.
     */
    fun readyModels(): List<OnDeviceModel> =
        models.values.filter { it.isReady() }
}