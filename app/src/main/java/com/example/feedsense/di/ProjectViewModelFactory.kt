package com.example.feedsense.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.feedsense.repository.AnnotationRepository
import com.example.feedsense.repository.ModelFeedbackRepository
import com.example.feedsense.repository.ModelPerformanceRepository
import com.example.feedsense.repository.ProjectRepository
import com.example.feedsense.repository.ReferenceRepository
import com.example.feedsense.repository.SessionRepository
import com.example.feedsense.viewmodel.AnnotationViewModel
import com.example.feedsense.viewmodel.ModelPerformanceViewModel
import com.example.feedsense.viewmodel.ProjectViewModel
import com.example.feedsense.viewmodel.ReviewViewModel
import com.example.feedsense.viewmodel.SessionViewModel

class ProjectViewModelFactory(
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(ProjectViewModel::class.java)) {

            return ProjectViewModel(
                projectRepository,
                sessionRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}

class SessionViewModelFactory(
    private val sessionRepository: SessionRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {

            return SessionViewModel(
                sessionRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}

class ReviewViewModelFactory(
    private val referenceRepository: ReferenceRepository,
    private val sessionRepository: SessionRepository,
    private val feedbackRepository: ModelFeedbackRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(ReviewViewModel::class.java)) {

            return ReviewViewModel(
                referenceRepository,
                sessionRepository,
                feedbackRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}

class ModelPerformanceViewModelFactory(
    private val modelPerformanceRepository: ModelPerformanceRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(
                ModelPerformanceViewModel::class.java
            )
        ) {

            return ModelPerformanceViewModel(
                modelPerformanceRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}

class AnnotationViewModelFactory(
    private val annotationRepository: AnnotationRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(
                AnnotationViewModel::class.java
            )
        ) {

            return AnnotationViewModel(
                annotationRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}