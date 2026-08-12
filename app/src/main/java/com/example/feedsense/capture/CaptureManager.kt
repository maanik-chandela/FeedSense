package com.example.feedsense.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import com.example.feedsense.viewmodel.SessionViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CaptureManager(
    private val context: Context,
    private val sessionViewModel: SessionViewModel
) {

    private var captureJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var sessionId: String? = null

    private val handler = Handler(Looper.getMainLooper())

    private var lastCaptureTime = 0L

    fun setMediaProjection(
        projection: MediaProjection,
        width: Int,
        height: Int,
        density: Int
    ) {

        stopProjection()

        mediaProjection = projection

        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = projection.createVirtualDisplay(
            "FeedSenseCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            handler
        )
    }

    fun start(
        scope: CoroutineScope,
        sessionId: String
    ) {

        if (captureJob?.isActive == true) {
            return
        }

        if (mediaProjection == null) {
            return
        }

        if (imageReader == null) {
            return
        }

        this.sessionId = sessionId

        captureJob = scope.launch {

            while (isActive) {

                captureFrame(
                    sessionId = sessionId
                )

                delay(5_000)
            }
        }
    }

    private suspend fun captureFrame(
        sessionId: String
    ) {

        val now = System.currentTimeMillis()

        if (now - lastCaptureTime < 4_000) {
            return
        }

        lastCaptureTime = now

        val reader = imageReader
            ?: return

        val image = reader.acquireLatestImage()
            ?: return

        try {

            val plane = image.planes.firstOrNull()
                ?: return

            val buffer = plane.buffer

            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride

            val rowPadding =
                rowStride - pixelStride * image.width

            val bitmapWidth =
                image.width + rowPadding / pixelStride

            val bitmap = Bitmap.createBitmap(
                bitmapWidth,
                image.height,
                Bitmap.Config.ARGB_8888
            )

            bitmap.copyPixelsFromBuffer(buffer)

            val croppedBitmap =
                if (bitmapWidth != image.width) {

                    Bitmap.createBitmap(
                        bitmap,
                        0,
                        0,
                        image.width,
                        image.height
                    )

                } else {
                    bitmap
                }

            val file = saveFrame(
                bitmap = croppedBitmap,
                sessionId = sessionId
            )

            if (file != null) {

                sessionViewModel.addCapturedFrame(
                    sessionId = sessionId,
                    filePath = file.absolutePath
                )
            }

            if (croppedBitmap !== bitmap) {
                croppedBitmap.recycle()
            }

            bitmap.recycle()

        } catch (_: Exception) {

            // Ignore an individual bad frame.
            // Capture continues on the next interval.

        } finally {

            image.close()
        }
    }

    private fun saveFrame(
        bitmap: Bitmap,
        sessionId: String
    ): File? {

        return try {

            val directory = File(
                context.filesDir,
                "captures/$sessionId"
            )

            if (!directory.exists()) {
                directory.mkdirs()
            }

            val timestamp =
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss_SSS",
                    Locale.US
                ).format(Date())

            val file = File(
                directory,
                "frame_$timestamp.jpg"
            )

            FileOutputStream(file).use { output ->

                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    70,
                    output
                )
            }

            file

        } catch (_: Exception) {

            null
        }
    }

    fun stop() {

        captureJob?.cancel()
        captureJob = null

        stopProjection()

        sessionId = null
        lastCaptureTime = 0L
    }

    private fun stopProjection() {

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection?.stop()
        mediaProjection = null
    }
}