package com.example.feedsense.analysis.ml.runtime

// --------------------------------
// RUNTIME LIFECYCLE (8B-15-5)
// --------------------------------
//
// Resource lifecycle state machine for the runtime adapter.
//
// Valid transitions:
//
//   UNLOADED -> LOADING -> READY
//                  |
//                  v
//                FAILED -> UNLOADED (release)
//
//   READY -> RELEASING -> UNLOADED
//   READY -> INFERRING -> READY
//
// Lifecycle rules:
//   - inference is only allowed from READY
//   - release is allowed from any state
//   - load is only allowed from UNLOADED or FAILED
//   - repeated initialization from READY is a no-op
//   - inference after close must fail cleanly

/**
 * The lifecycle state of a runtime adapter instance.
 */
enum class RuntimeLifecycleState(val label: String) {
    /**
     * No model loaded, no resources held.
     */
    UNLOADED("UNLOADED"),

    /**
     * Model load in progress.
     */
    LOADING("LOADING"),

    /**
     * Model loaded and ready for inference.
     */
    READY("READY"),

    /**
     * Inference in progress.
     */
    INFERRING("INFERRING"),

    /**
     * Release in progress.
     */
    RELEASING("RELEASING"),

    /**
     * Load was attempted and failed. Resources may be partially
     * held.
     */
    FAILED("FAILED")
}

/**
 * Lifecycle manager for a runtime adapter. Enforces valid state
 * transitions and tracks state history.
 *
 * Thread-safety: the lifecycle is NOT thread-safe by itself.
 * Callers requiring thread-safety must synchronize externally.
 */
class RuntimeLifecycleManager {

    private var currentState: RuntimeLifecycleState = RuntimeLifecycleState.UNLOADED
    private var stateHistory: MutableList<Pair<RuntimeLifecycleState, Long>> =
        mutableListOf(RuntimeLifecycleState.UNLOADED to System.currentTimeMillis())

    /**
     * Current lifecycle state.
     */
    val state: RuntimeLifecycleState get() = currentState

    /**
     * Whether inference is allowed in the current state.
     */
    val canInfer: Boolean
        get() = currentState == RuntimeLifecycleState.READY

    /**
     * Whether initialization (load) is allowed.
     */
    val canInitialize: Boolean
        get() = currentState == RuntimeLifecycleState.UNLOADED ||
            currentState == RuntimeLifecycleState.FAILED

    /**
     * Whether the adapter is in a terminal error state.
     */
    val isFailed: Boolean
        get() = currentState == RuntimeLifecycleState.FAILED

    /**
     * Attempts to transition to LOADING state.
     *
     * @return true if the transition was successful
     */
    fun beginLoad(): Boolean {
        if (!canInitialize) return false
        transitionTo(RuntimeLifecycleState.LOADING)
        return true
    }

    /**
     * Transitions from LOADING to READY state.
     *
     * @return true if the transition was successful
     */
    fun completeLoad(): Boolean {
        if (currentState != RuntimeLifecycleState.LOADING) return false
        transitionTo(RuntimeLifecycleState.READY)
        return true
    }

    /**
     * Transitions from LOADING to FAILED state.
     *
     * @return true if the transition was successful
     */
    fun failLoad(): Boolean {
        if (currentState != RuntimeLifecycleState.LOADING) return false
        transitionTo(RuntimeLifecycleState.FAILED)
        return true
    }

    /**
     * Transitions from READY to INFERRING state.
     *
     * @return true if the transition was successful
     */
    fun beginInference(): Boolean {
        if (currentState != RuntimeLifecycleState.READY) return false
        transitionTo(RuntimeLifecycleState.INFERRING)
        return true
    }

    /**
     * Transitions from INFERRING to READY state.
     *
     * @return true if the transition was successful
     */
    fun completeInference(): Boolean {
        if (currentState != RuntimeLifecycleState.INFERRING) return false
        transitionTo(RuntimeLifecycleState.READY)
        return true
    }

    /**
     * Transitions to RELEASING and then to UNLOADED.
     *
     * @return true if the transition was successful
     */
    fun release(): Boolean {
        if (currentState == RuntimeLifecycleState.UNLOADED) return true
        if (currentState == RuntimeLifecycleState.RELEASING) return false
        transitionTo(RuntimeLifecycleState.RELEASING)
        transitionTo(RuntimeLifecycleState.UNLOADED)
        return true
    }

    /**
     * Returns the full state transition history as a list of
     * (state, timestamp) pairs.
     */
    fun history(): List<Pair<RuntimeLifecycleState, Long>> =
        stateHistory.toList()

    /**
     * Resets the lifecycle to UNLOADED state. Intended for
     * testing only.
     */
    fun reset() {
        currentState = RuntimeLifecycleState.UNLOADED
        stateHistory.clear()
        stateHistory.add(RuntimeLifecycleState.UNLOADED to System.currentTimeMillis())
    }

    private fun transitionTo(newState: RuntimeLifecycleState) {
        currentState = newState
        stateHistory.add(newState to System.currentTimeMillis())
    }
}
