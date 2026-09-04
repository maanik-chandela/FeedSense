package com.example.feedsense.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedsense.model.ModelPerformanceStats
import com.example.feedsense.repository.ModelPerformanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// --------------------------------
// MODEL PERFORMANCE VIEW MODEL
// --------------------------------
//
// Milestone 7G.
//
// Loads the on-demand performance statistics for the
// evaluation screen.
//

class ModelPerformanceViewModel(
    private val repository: ModelPerformanceRepository
) : ViewModel() {

    private val _stats =
        MutableStateFlow<ModelPerformanceStats?>(null)

    val stats: StateFlow<ModelPerformanceStats?> =
        _stats.asStateFlow()

    private val _loading =
        MutableStateFlow(false)

    val loading: StateFlow<Boolean> =
        _loading.asStateFlow()

    fun refresh() {

        viewModelScope.launch {

            _loading.value = true

            try {

                _stats.value =
                    repository.computeStats()

            } finally {

                _loading.value = false
            }
        }
    }
}
