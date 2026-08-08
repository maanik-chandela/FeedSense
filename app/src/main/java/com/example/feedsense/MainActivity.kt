package com.example.feedsense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feedsense.di.ProjectViewModelFactory
import com.example.feedsense.di.SessionViewModelFactory
import com.example.feedsense.navigation.AppNavigation
import com.example.feedsense.ui.theme.FeedSenseTheme
import com.example.feedsense.viewmodel.ProjectViewModel
import com.example.feedsense.viewmodel.SessionViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            FeedSenseTheme {

                val app = application as FeedSenseApplication

                val projectViewModel: ProjectViewModel = viewModel(
                    factory = ProjectViewModelFactory(
                        app.repository
                    )
                )

                val sessionViewModel: SessionViewModel = viewModel(
                    factory = SessionViewModelFactory(
                        app.sessionRepository
                    )
                )

                AppNavigation(
                    projectViewModel = projectViewModel,
                    sessionViewModel = sessionViewModel
                )
            }
        }
    }
}