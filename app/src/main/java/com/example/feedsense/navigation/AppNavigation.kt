package com.example.feedsense.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.feedsense.ui.home.HomeScreen
import com.example.feedsense.ui.theme.project.CreateProjectScreen
import com.example.feedsense.ui.theme.projects.ProjectsScreen
import com.example.feedsense.ui.theme.sessions.AnnotationQueueScreen
import com.example.feedsense.ui.theme.sessions.AnnotationScreen
import com.example.feedsense.ui.theme.sessions.ModelPerformanceScreen
import com.example.feedsense.ui.theme.sessions.ObservationScreen
import com.example.feedsense.ui.theme.sessions.ReviewScreen
import com.example.feedsense.ui.theme.sessions.SessionHistoryScreen
import com.example.feedsense.ui.theme.sessions.SessionScreen
import com.example.feedsense.ui.theme.sessions.SessionAnalyticsScreen
import com.example.feedsense.ui.theme.sessions.SettingsScreen
import com.example.feedsense.viewmodel.AnnotationViewModel
import com.example.feedsense.viewmodel.ModelPerformanceViewModel
import com.example.feedsense.viewmodel.ProjectViewModel
import com.example.feedsense.viewmodel.ReviewViewModel
import android.widget.Toast
import com.example.feedsense.worker.ExportScheduler
import com.example.feedsense.viewmodel.SessionViewModel

private object Routes {

    const val HOME = "home"

    const val PROJECTS = "projects"

    const val CREATE_PROJECT = "create_project"

    const val SESSION_HISTORY =
        "session_history/{projectId}/{projectTitle}"

    const val SESSION = "session/{sessionId}/{projectId}"

    const val OBSERVATION = "observation/{sessionId}"

    const val REVIEW = "review"

    const val MODEL_PERFORMANCE = "model_performance"

    const val SETTINGS = "settings"

    const val ANALYTICS = "analytics"

    const val SESSION_ANALYTICS = "session_analytics/{sessionId}"

    const val ANNOTATION_QUEUE = "annotation_queue"

    const val ANNOTATION = "annotation/{evaluationItemId}"
}

@Composable
fun AppNavigation(
    projectViewModel: ProjectViewModel,
    sessionViewModel: SessionViewModel,
    reviewViewModel: ReviewViewModel,
    modelPerformanceViewModel: ModelPerformanceViewModel,
    annotationViewModel: AnnotationViewModel,
    onStartScreenCapture: (String) -> Unit,
    onStopScreenCapture: () -> Unit
) {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {

        // --------------------------------
        // HOME
        // --------------------------------

        composable(route = Routes.HOME) {

            HomeScreen(
                projectViewModel = projectViewModel,
                sessionViewModel = sessionViewModel,

                onCreateProjectClick = {
                    navController.navigate(Routes.CREATE_PROJECT)
                },

                onOpenProjectsClick = {
                    navController.navigate(Routes.PROJECTS)
                },

                onContinueSessionClick = {

                    val project = projectViewModel.currentProject

                    if (project != null) {
                        navController.navigate(
                            "session_history/${project.id}/${project.title}"
                        )
                    }
                },

                onOpenModelPerformanceClick = {
                    navController.navigate(Routes.MODEL_PERFORMANCE)
                },

                onOpenSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                },

                onOpenAnalyticsClick = {
                    navController.navigate(Routes.ANALYTICS)
                },

                onOpenSessionHistoryClick = { projectId, projectTitle ->
                    navController.navigate(
                        "session_history/$projectId/$projectTitle"
                    )
                },

                onOpenAnnotationQueueClick = {
                    navController.navigate(
                        Routes.ANNOTATION_QUEUE
                    )
                }
            )
        }

        // --------------------------------
        // PROJECTS
        // --------------------------------

        composable(route = Routes.PROJECTS) {

            ProjectsScreen(
                projectViewModel = projectViewModel,

                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // --------------------------------
        // CREATE PROJECT
        // --------------------------------

        composable(route = Routes.CREATE_PROJECT) {

            CreateProjectScreen(
                projectViewModel = projectViewModel,

                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        // --------------------------------
        // SESSION HISTORY
        // --------------------------------

        composable(
            route = Routes.SESSION_HISTORY,

            arguments = listOf(
                navArgument("projectId") {
                    type = NavType.StringType
                },
                navArgument("projectTitle") {
                    type = NavType.StringType
                }
            )

        ) { backStackEntry ->

            val projectId =
                backStackEntry.arguments?.getString("projectId")
                    ?: return@composable

            val projectTitle =
                backStackEntry.arguments?.getString("projectTitle")
                    ?: "Research Project"

            SessionHistoryScreen(
                projectId = projectId,
                projectTitle = projectTitle,
                sessionViewModel = sessionViewModel,
                onStopScreenCapture = { onStopScreenCapture() },
                onBack = { navController.popBackStack() },
                onSessionClick = { session ->
                    navController.navigate(
                        "session/${session.id}/${session.projectId}"
                    )
                }
            )
        }

        // --------------------------------
        // SESSION
        // --------------------------------

        composable(
            route = Routes.SESSION,

            arguments = listOf(
                navArgument("sessionId") {
                    type = NavType.StringType
                },
                navArgument("projectId") {
                    type = NavType.StringType
                }
            )

        ) { backStackEntry ->

            val sessionId =
                backStackEntry.arguments?.getString("sessionId")
                    ?: return@composable

            val projectId =
                backStackEntry.arguments?.getString("projectId")
                    ?: return@composable

            SessionScreen(
                sessionId = sessionId,
                projectId = projectId,
                sessionViewModel = sessionViewModel,
                onStartScreenCapture = {
                    onStartScreenCapture(sessionId)
                },
                onStopScreenCapture = { onStopScreenCapture() },
                onRecordObservation = {
                    navController.navigate("observation/$sessionId")
                },
                onOpenReview = {
                    navController.navigate(Routes.REVIEW)
                },
                onExport = {
                    ExportScheduler.schedule(navController.context)
                    Toast.makeText(
                        navController.context,
                        "Export started. Check app storage/exports.",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onOpenAnalytics = {
                    navController.navigate("session_analytics/$sessionId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        // --------------------------------
        // REVIEW
        // --------------------------------

        composable(route = Routes.REVIEW) {

            ReviewScreen(
                reviewViewModel = reviewViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // --------------------------------
        // OBSERVATION
        // --------------------------------

        composable(
            route = Routes.OBSERVATION,

            arguments = listOf(
                navArgument("sessionId") {
                    type = NavType.StringType
                }
            )

        ) { backStackEntry ->

            val sessionId =
                backStackEntry.arguments?.getString("sessionId")
                    ?: return@composable

            ObservationScreen(
                sessionId = sessionId,
                sessionViewModel = sessionViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // --------------------------------
        // SESSION ANALYTICS
        // --------------------------------

        composable(
            route = Routes.SESSION_ANALYTICS,
            arguments = listOf(
                navArgument("sessionId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val sessionId =
                backStackEntry.arguments?.getString("sessionId")
                    ?: return@composable

            SessionAnalyticsScreen(
                sessionId = sessionId,
                sessionViewModel = sessionViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // --------------------------------
        // MODEL PERFORMANCE
        // --------------------------------

        composable(route = Routes.MODEL_PERFORMANCE) {

            ModelPerformanceScreen(
                viewModel = modelPerformanceViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // --------------------------------
        // SETTINGS
        // --------------------------------

        composable(route = Routes.SETTINGS) {

            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // --------------------------------
        // ANALYTICS (reuses ModelPerformance)
        // --------------------------------

        composable(route = Routes.ANALYTICS) {

            ModelPerformanceScreen(
                viewModel = modelPerformanceViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // --------------------------------
        // ANNOTATION QUEUE (8A-2)
        // --------------------------------

        composable(route = Routes.ANNOTATION_QUEUE) {

            AnnotationQueueScreen(
                annotationViewModel = annotationViewModel,

                onOpenItem = { evaluationItemId, _ ->
                    navController.navigate(
                        "annotation/$evaluationItemId"
                    )
                },

                onBack = { navController.popBackStack() }
            )
        }

        // --------------------------------
        // ANNOTATION (8A-2)
        // --------------------------------

        composable(
            route = Routes.ANNOTATION,

            arguments = listOf(
                navArgument("evaluationItemId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val evaluationItemId =
                backStackEntry.arguments
                    ?.getString("evaluationItemId")
                    ?: return@composable

            AnnotationScreen(
                evaluationItemId = evaluationItemId,
                annotationViewModel = annotationViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
