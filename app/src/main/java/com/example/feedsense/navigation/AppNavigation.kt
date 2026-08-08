package com.example.feedsense.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.feedsense.ui.home.HomeScreen
import com.example.feedsense.ui.theme.project.CreateProjectScreen
import com.example.feedsense.ui.theme.projects.ProjectsScreen
import com.example.feedsense.ui.theme.sessions.SessionHistoryScreen
import com.example.feedsense.viewmodel.ProjectViewModel
import com.example.feedsense.viewmodel.SessionViewModel

sealed class Screen(val route: String) {

    object Home : Screen("home")

    object CreateProject : Screen("create_project")

    object Projects : Screen("projects")

    object Sessions : Screen("sessions")
}

@Composable
fun AppNavigation(
    projectViewModel: ProjectViewModel,
    sessionViewModel: SessionViewModel
) {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {

        // HOME
        composable(Screen.Home.route) {

            HomeScreen(
                projectViewModel = projectViewModel,

                onCreateProjectClick = {
                    navController.navigate(
                        Screen.CreateProject.route
                    )
                },

                onOpenProjectsClick = {
                    navController.navigate(
                        Screen.Projects.route
                    )
                },

                onContinueSessionClick = {
                    navController.navigate(
                        Screen.Sessions.route
                    )
                }
            )
        }

        // CREATE PROJECT
        composable(Screen.CreateProject.route) {

            CreateProjectScreen(
                projectViewModel = projectViewModel,

                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        // ALL PROJECTS
        composable(Screen.Projects.route) {

            ProjectsScreen(
                projectViewModel = projectViewModel,

                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // SESSION HISTORY
        composable(Screen.Sessions.route) {

            val currentProject =
                projectViewModel.currentProject

            if (currentProject != null) {

                SessionHistoryScreen(
                    projectId = currentProject.id,
                    projectTitle = currentProject.title,
                    sessionViewModel = sessionViewModel,

                    onBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}