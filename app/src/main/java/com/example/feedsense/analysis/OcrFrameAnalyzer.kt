package com.example.feedsense.analysis

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class OcrFrameAnalyzer(
    private val context: Context
) : FrameAnalyzer {

    private val recognizer =
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

    override suspend fun analyze(
        file: File
    ): FrameAnalysisResult {

        if (
            !file.exists() ||
            !file.isFile
        ) {
            throw IllegalArgumentException(
                "Frame file does not exist: ${file.absolutePath}"
            )
        }

        val options =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        BitmapFactory.decodeFile(
            file.absolutePath,
            options
        )

        val width =
            options.outWidth

        val height =
            options.outHeight

        if (
            width <= 0 ||
            height <= 0
        ) {
            throw IllegalArgumentException(
                "Unable to decode frame dimensions: ${file.name}"
            )
        }

        val image =
            InputImage.fromFilePath(
                context,
                Uri.fromFile(file)
            )

        val text =
            suspendCancellableCoroutine<String> { continuation ->

                recognizer
                    .process(image)
                    .addOnSuccessListener { result ->

                        continuation.resume(
                            result.text
                        )
                    }
                    .addOnFailureListener { exception ->

                        continuation.resumeWithException(
                            exception
                        )
                    }
            }

        val cleanedText =
            text
                .trim()
                .takeIf {
                    it.isNotEmpty()
                }

        return FrameAnalysisResult(

            status =
                if (cleanedText != null) {
                    "OCR_COMPLETE"
                } else {
                    "OCR_COMPLETE_NO_TEXT"
                },

            fileName =
                file.name,

            width =
                width,

            height =
                height,

            fileSizeBytes =
                file.length(),

            message =
                if (cleanedText != null) {
                    "OCR completed successfully."
                } else {
                    "OCR completed but no text was detected."
                },

            screenType =
                null,

            application =
                null,

            activity =
                null,

            visibleText =
                cleanedText,

            confidence =
                null
        )
    }
}
