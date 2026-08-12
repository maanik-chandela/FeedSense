package com.example.feedsense

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.feedsense.capture.ScreenCaptureService
import com.example.feedsense.di.ProjectViewModelFactory
import com.example.feedsense.di.ReviewViewModelFactory
import com.example.feedsense.di.SessionViewModelFactory
import com.example.feedsense.navigation.AppNavigation
import com.example.feedsense.ui.theme.FeedSenseTheme
import com.example.feedsense.viewmodel.ProjectViewModel
import com.example.feedsense.viewmodel.ReviewViewModel
import com.example.feedsense.viewmodel.SessionViewModel

class MainActivity : ComponentActivity() {

    /*
     * The session that requested screen capture.
     *
     * Android shows the MediaProjection permission screen
     * asynchronously, so we must remember which session
     * requested the capture.
     */
    private var pendingCaptureSessionId: String? = null

    /*
     * Android MediaProjection permission launcher.
     */
    private val screenCaptureLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            val resultData = result.data

            /*
             * Permission was granted.
             */
            if (
                result.resultCode == RESULT_OK &&
                resultData != null
            ) {

                val sessionId =
                    pendingCaptureSessionId

                /*
                 * We cannot start a capture without
                 * knowing which session owns the frames.
                 */
                if (sessionId == null) {
                    return@registerForActivityResult
                }

                startScreenCaptureService(
                    resultCode = result.resultCode,
                    resultData = resultData,
                    sessionId = sessionId
                )

            } else {

                /*
                 * User denied screen capture permission.
                 */
                pendingCaptureSessionId = null
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {

            FeedSenseTheme {

                val app =
                    application as FeedSenseApplication

                val projectViewModel: ProjectViewModel =
                    viewModel(
                        factory =
                            ProjectViewModelFactory(
                                app.repository,
                                app.sessionRepository
                            )
                    )

                val sessionViewModel: SessionViewModel =
                    viewModel(
                        factory =
                            SessionViewModelFactory(
                                app.sessionRepository
                            )
                    )

                val reviewViewModel: ReviewViewModel =
                    viewModel(
                        factory =
                            ReviewViewModelFactory(
                                app.referenceRepository,
                                app.sessionRepository
                            )
                    )

                AppNavigation(
                    projectViewModel =
                        projectViewModel,

                    sessionViewModel =
                        sessionViewModel,

                    reviewViewModel =
                        reviewViewModel,

                    onStartScreenCapture = { sessionId ->

                        requestScreenCapture(
                            sessionId
                        )
                    },

                    onStopScreenCapture = {

                        stopScreenCaptureService()
                    }
                )
            }
        }
    }

    /*
     * Request MediaProjection permission.
     */
    private fun requestScreenCapture(
        sessionId: String
    ) {

        /*
         * Remember the session before launching
         * the Android permission dialog.
         */
        pendingCaptureSessionId =
            sessionId

        val projectionManager =
            getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        val captureIntent =
            projectionManager
                .createScreenCaptureIntent()

        screenCaptureLauncher.launch(
            captureIntent
        )
    }

    /*
     * Start the foreground screen-capture service.
     */
    private fun startScreenCaptureService(
        resultCode: Int,
        resultData: Intent,
        sessionId: String
    ) {

        val serviceIntent =
            Intent(
                this,
                ScreenCaptureService::class.java
            ).apply {

                action =
                    ScreenCaptureService.ACTION_START

                putExtra(
                    ScreenCaptureService.EXTRA_RESULT_CODE,
                    resultCode
                )

                putExtra(
                    ScreenCaptureService.EXTRA_RESULT_DATA,
                    resultData
                )

                /*
                 * THIS WAS THE IMPORTANT MISSING VALUE.
                 */
                putExtra(
                    ScreenCaptureService.EXTRA_SESSION_ID,
                    sessionId
                )
            }

        ContextCompat.startForegroundService(
            this,
            serviceIntent
        )

        /*
         * Permission result has now been consumed.
         */
        pendingCaptureSessionId = null
    }

    /*
     * Stop the capture service explicitly.
     *
     * This is called by the Stop Capture button,
     * not by Activity destruction.
     */
    private fun stopScreenCaptureService() {

        val serviceIntent =
            Intent(
                this,
                ScreenCaptureService::class.java
            ).apply {

                action =
                    ScreenCaptureService.ACTION_STOP
            }

        startService(
            serviceIntent
        )
    }

    override fun onDestroy() {

        /*
         * DO NOT stop screen capture here.
         *
         * The service owns the MediaProjection lifecycle.
         */
        super.onDestroy()
    }
}