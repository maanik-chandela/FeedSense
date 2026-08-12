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
import com.example.feedsense.ui.theme.sessions.ObservationScreen
import com.example.feedsense.ui.theme.sessions.ReviewScreen
import com.example.feedsense.ui.theme.sessions.SessionHistoryScreen
import com.example.feedsense.ui.theme.sessions.SessionScreen
import com.example.feedsense.viewmodel.ProjectViewModel
import com.example.feedsense.viewmodel.ReviewViewModel
import com.example.feedsense.viewmodel.SessionViewModel

private object Routes {

    const val HOME = "home"

    const val PROJECTS =
        "projects"

    const val CREATE_PROJECT =
        "create_project"

    const val SESSION_HISTORY =
        "session_history/{projectId}/{projectTitle}"

    const val SESSION =
        "session/{sessionId}/{projectId}"

    const val OBSERVATION =
        "observation/{sessionId}"

    const val REVIEW =
        "review"
}

@Composable
fun AppNavigation(
    projectViewModel: ProjectViewModel,
    sessionViewModel: SessionViewModel,
    reviewViewModel: ReviewViewModel,
    onStartScreenCapture: (String) -> Unit,
    onStopScreenCapture: () -> Unit
) {

    val navController =
        rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {

        // --------------------------------
        // HOME
        // --------------------------------

        composable(
            route = Routes.HOME
        ) {

            HomeScreen(
                projectViewModel = projectViewModel,
                sessionViewModel = sessionViewModel,

                onCreateProjectClick = {

                    navController.navigate(
                        Routes.CREATE_PROJECT
                    )
                },

                onOpenProjectsClick = {

                    navController.navigate(
                        Routes.PROJECTS
                    )
                },

                onContinueSessionClick = {

                    val project =
                        projectViewModel.currentProject

                    if (project != null) {

                        navController.navigate(
                            "session_history/" +
                                    "${project.id}/" +
                                    project.title
                        )
                    }
                }
            )
        }

        // --------------------------------
        // PROJECTS
        // --------------------------------

        composable(
            route = Routes.PROJECTS
        ) {

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

        composable(
            route = Routes.CREATE_PROJECT
        ) {

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
                backStackEntry
                    .arguments
                    ?.getString("projectId")
                    ?: return@composable

            val projectTitle =
                backStackEntry
                    .arguments
                    ?.getString("projectTitle")
                    ?: "Research Project"

            SessionHistoryScreen(

                projectId =
                    projectId,

                projectTitle =
                    projectTitle,

                sessionViewModel =
                    sessionViewModel,

                onBack = {

                    navController.popBackStack()
                },

                onSessionClick = { session ->

                    /*
                     * IMPORTANT:
                     *
                     * Never pass the ResearchSession
                     * object through Navigation.
                     *
                     * Pass only its IDs.
                     */

                    navController.navigate(
                        "session/" +
                                "${session.id}/" +
                                "${session.projectId}"
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
                backStackEntry
                    .arguments
                    ?.getString("sessionId")
                    ?: return@composable

            val projectId =
                backStackEntry
                    .arguments
                    ?.getString("projectId")
                    ?: return@composable

            SessionScreen(

                sessionId =
                    sessionId,

                projectId =
                    projectId,

                sessionViewModel =
                    sessionViewModel,

                onStartScreenCapture = {

                    onStartScreenCapture(
                        sessionId
                    )
                },

                onStopScreenCapture = {

                    onStopScreenCapture()
                },

                onRecordObservation = {

                    navController.navigate(
                        "observation/$sessionId"
                    )
                },

                onOpenReview = {

                    navController.navigate(
                        Routes.REVIEW
                    )
                },

                onBack = {

                    navController.popBackStack()
                }
            )
        }

        // --------------------------------
        // REVIEW
        // --------------------------------
        //
        // Milestone 7B.
        //
        // Human review of frames the local pipeline
        // could not confidently classify.

        composable(
            route = Routes.REVIEW
        ) {

            ReviewScreen(
                reviewViewModel = reviewViewModel,

                onBack = {

                    navController.popBackStack()
                }
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
                backStackEntry
                    .arguments
                    ?.getString("sessionId")
                    ?: return@composable

            ObservationScreen(

                sessionId =
                    sessionId,

                sessionViewModel =
                    sessionViewModel,

                onBack = {

                    navController.popBackStack()
                }
            )
        }
    }
}
