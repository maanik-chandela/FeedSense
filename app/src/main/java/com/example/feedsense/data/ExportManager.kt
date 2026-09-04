package com.example.feedsense.data

import android.content.Context
import com.example.feedsense.analysis.DatasetExporter
import com.example.feedsense.repository.ModelFeedbackRepository
import com.example.feedsense.repository.ReferenceRepository
import com.example.feedsense.repository.SessionRepository
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/*
 * Milestone 7T.
 *
 * Android-side export manager. Reads the full research
 * dataset through the repositories, builds the JSON with
 * the pure DatasetExporter and writes it to app-private
 * storage (filesDir/exports) - no storage permission
 * needed. Returns the written file (or null on failure).
 */
class ExportManager(
    context: Context
) {
    private val appContext =
        context.applicationContext

    private val exportsDirectory =
        File(
            appContext.filesDir,
            "exports"
        )

    fun exportsDirectoryFile(): File {
        return exportsDirectory
    }

    suspend fun exportDataset(
        sessionRepository: SessionRepository,
        referenceRepository: ReferenceRepository,
        feedbackRepository: ModelFeedbackRepository
    ): File? {

        return try {

            if (!exportsDirectory.exists()) {
                exportsDirectory.mkdirs()
            }

            val exporter =
                DatasetExporter()

            val json =
                exporter.buildJson(
                    sessions =
                        sessionRepository.getAllSessions(),
                    feedItems =
                        sessionRepository.getAllItems(),
                    references =
                        referenceRepository.getAllValidated(),
                    feedback =
                        feedbackRepository.getAll(),
                    observations =
                        sessionRepository.getAllObservations()
                )

            val fileName =
                "feedsense_export_" +
                        DateTimeFormatter
                            .ofPattern(
                                "yyyyMMdd_HHmmss"
                            )
                            .format(
                                LocalDateTime.now()
                            ) +
                        ".json"

            val file =
                File(
                    exportsDirectory,
                    fileName
                )

            file.writeText(
                json.toString(2),
                Charsets.UTF_8
            )

            file

        } catch (exception: Exception) {

            exception.printStackTrace()

            null
        }
    }
}
