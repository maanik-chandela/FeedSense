package com.example.feedsense.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.feedsense.FeedSenseApplication
import com.example.feedsense.R
import com.example.feedsense.analysis.dedup.AdaptiveFrameSampler
import com.example.feedsense.analysis.dedup.DeduplicationConfig
import com.example.feedsense.model.CapturedFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ScreenCaptureService : Service() {

    companion object {

        const val ACTION_START =
            "com.example.feedsense.START_CAPTURE"

        const val ACTION_STOP =
            "com.example.feedsense.STOP_CAPTURE"

        const val EXTRA_RESULT_CODE =
            "result_code"

        const val EXTRA_RESULT_DATA =
            "result_data"

        const val EXTRA_SESSION_ID =
            "session_id"

        private const val CHANNEL_ID =
            "feedsense_screen_capture"

        private const val NOTIFICATION_ID =
            1001

        /*
         * Capture resolution.
         */
        private const val CAPTURE_WIDTH =
            720

        private const val CAPTURE_HEIGHT =
            1280

        /*
         * JPEG quality.
         */
        private const val JPEG_QUALITY =
            65

        /*
         * Maximum saved frames in one session.
         */
        private const val MAX_STORED_FRAMES =
            300

        /*
         * Minimum time between frames that are
         * actually evaluated/saved.
         *
         * 1000 ms = approximately 1 evaluation/sec.
         */
        private const val FRAME_INTERVAL_MS =
            1000L

        /*
         * Pixel sampling interval.
         *
         * We don't compare every pixel.
         * Every 20 pixels is enough to detect
         * meaningful screen changes while keeping
         * CPU usage low.
         */
        private const val SAMPLE_STEP =
            20

        /*
         * Percentage of sampled pixels that must
         * differ before we consider the screen
         * meaningfully changed.
         *
         * 0.05 = 5%
         */
        private const val CHANGE_THRESHOLD =
            0.05f

        /*
         * Individual RGB difference required for
         * a sampled pixel to count as different.
         */
        private const val PIXEL_DIFFERENCE_THRESHOLD =
            25
    }

    private var mediaProjection:
            MediaProjection? = null

    private var virtualDisplay:
            VirtualDisplay? = null

    private var imageReader:
            ImageReader? = null

    private var currentSessionId:
            String? = null

    private var frameCounter =
        0

    /*
     * Last bitmap that was actually saved.
     *
     * We compare new frames against this bitmap.
     */
    private var previousSavedBitmap:
            Bitmap? = null

    /*
     * Timestamp of the last frame that was
     * evaluated.
     */
    private var lastFrameEvaluationTime =
        0L

    /*
     * Milestone 8B-3. Bounded adaptive frame sampler.
     * Replaces the fixed FRAME_INTERVAL_MS with an
     * adaptive interval based on visual change.
     */
    private val adaptiveSampler =
        AdaptiveFrameSampler()

    /*
     * Pixels of the last saved bitmap, used for
     * computing change magnitude for the adaptive
     * sampler.
     */
    private var previousSavedPixels:
            IntArray? = null

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                    Dispatchers.IO
        )

    private val sessionRepository
        get() =
            (application as FeedSenseApplication)
                .sessionRepository

    // ========================================
    // SERVICE LIFECYCLE
    // ========================================

    override fun onCreate() {

        super.onCreate()

        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        /*
         * Foreground service must be started
         * immediately.
         */
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

        } else {

            @Suppress("DEPRECATION")
            startForeground(
                NOTIFICATION_ID,
                createNotification()
            )
        }

        when (intent?.action) {

            ACTION_START -> {

                startCapture(
                    intent
                )
            }

            ACTION_STOP -> {

                stopCaptureService()
            }
        }

        return START_NOT_STICKY
    }

    // ========================================
    // START CAPTURE
    // ========================================

    private fun startCapture(
        intent: Intent
    ) {

        /*
         * Prevent multiple capture pipelines.
         */
        if (mediaProjection != null) {
            return
        }

        val resultCode =
            intent.getIntExtra(
                EXTRA_RESULT_CODE,
                -1
            )

        if (
            resultCode !=
            Activity.RESULT_OK
        ) {

            stopCaptureService()
            return
        }

        val resultData:
                Intent? =

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU
            ) {

                intent.getParcelableExtra(
                    EXTRA_RESULT_DATA,
                    Intent::class.java
                )

            } else {

                @Suppress("DEPRECATION")
                intent.getParcelableExtra(
                    EXTRA_RESULT_DATA
                )
            }

        if (resultData == null) {

            stopCaptureService()
            return
        }

        currentSessionId =
            intent.getStringExtra(
                EXTRA_SESSION_ID
            )

        if (
            currentSessionId == null
        ) {

            stopCaptureService()
            return
        }

        /*
         * Reset state for a new capture session.
         */
        frameCounter = 0

        lastFrameEvaluationTime = 0L

        adaptiveSampler.reset()

        previousSavedBitmap?.let {

            if (!it.isRecycled) {
                it.recycle()
            }
        }

        previousSavedBitmap = null

        previousSavedPixels = null

        try {

            val projectionManager =
                getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            mediaProjection =
                projectionManager
                    .getMediaProjection(
                        resultCode,
                        resultData
                    )

            if (
                mediaProjection == null
            ) {

                stopCaptureService()
                return
            }

            setupImageReader()

            setupVirtualDisplay()

        } catch (
            exception: SecurityException
        ) {

            exception.printStackTrace()

            stopCaptureService()
        }
    }

    // ========================================
    // IMAGE READER
    // ========================================

    private fun setupImageReader() {

        imageReader =
            ImageReader.newInstance(
                CAPTURE_WIDTH,
                CAPTURE_HEIGHT,
                PixelFormat.RGBA_8888,
                2
            )

        imageReader?.setOnImageAvailableListener(
            { reader ->

                processLatestImage(
                    reader
                )

            },
            handler
        )
    }

    // ========================================
    // VIRTUAL DISPLAY
    // ========================================

    private fun setupVirtualDisplay() {

        val projection =
            mediaProjection
                ?: return

        val surface =
            imageReader?.surface
                ?: return

        val density =
            resources
                .displayMetrics
                .densityDpi

        virtualDisplay =
            projection.createVirtualDisplay(
                "FeedSenseCapture",

                CAPTURE_WIDTH,

                CAPTURE_HEIGHT,

                density,

                DisplayManager
                    .VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,

                surface,

                null,

                handler
            )
    }

    // ========================================
    // PROCESS IMAGE
    // ========================================

    private fun processLatestImage(
        reader: ImageReader
    ) {

        var image:
                Image? = null

        try {

            /*
             * Always acquire only the newest image.
             */
            image =
                reader.acquireLatestImage()
                    ?: return

            /*
             * Throttle frame processing.
             *
             * Android may deliver many frames per
             * second. We only evaluate one every
             * FRAME_INTERVAL_MS.
             */
            val now =
                System.currentTimeMillis()

            if (
                now -
                lastFrameEvaluationTime <
                FRAME_INTERVAL_MS
            ) {

                return
            }

            lastFrameEvaluationTime =
                now

            /*
             * Convert Image -> Bitmap.
             */
            val bitmap =
                imageToBitmap(
                    image
                )
                    ?: return

            try {

                /*
                 * Milestone 8B-3. Adaptive sampling:
                 * compute visual change magnitude and
                 * let the sampler decide whether to
                 * evaluate this frame.
                 */
                val now =
                    System.currentTimeMillis()

                val changeMagnitude =
                    if (
                        previousSavedPixels !=
                        null &&
                        previousSavedBitmap !=
                        null &&
                        !previousSavedBitmap!!
                            .isRecycled
                    ) {

                    adaptiveSampler
                        .computeChangeMagnitude(
                            previousPixels =
                                previousSavedPixels!!,
                            currentPixels =
                                bitmapToPixels(bitmap),
                            width = bitmap.width,
                            height = bitmap.height
                        )

                } else {
                    1f
                }

                if (
                    !adaptiveSampler.shouldSample(
                        currentTimestampMs = now,
                        visualChangeMagnitude =
                            changeMagnitude
                    )
                ) {

                    return
                }

                /*
                 * First frame is always saved.
                 *
                 * Every following frame is compared
                 * against the previous saved frame.
                 */
                val shouldSave =
                    if (
                        previousSavedBitmap == null
                    ) {

                        true

                    } else {

                        hasMeaningfulChange(
                            previousSavedBitmap!!,
                            bitmap
                        )
                    }

                if (shouldSave) {

                    saveBitmap(
                        bitmap
                    )

                    /*
                     * Keep a copy of the saved bitmap
                     * for the next comparison.
                     *
                     * We copy because the original bitmap
                     * is recycled after this method.
                     */
                    previousSavedBitmap?.let {

                        if (!it.isRecycled) {
                            it.recycle()
                        }
                    }

                    previousSavedBitmap =
                        bitmap.copy(
                            Bitmap.Config.ARGB_8888,
                            false
                        )

                    /*
                     * Milestone 8B-3. Store pixel data
                     * for adaptive change magnitude
                     * computation.
                     */
                    previousSavedPixels?.let {
                        // old pixels are discarded
                    }

                    previousSavedPixels =
                        bitmapToPixels(bitmap)
                }

            } finally {

                if (
                    !bitmap.isRecycled
                ) {

                    bitmap.recycle()
                }
            }

        } catch (
            exception: Exception
        ) {

            exception.printStackTrace()

        } finally {

            /*
             * ImageReader images MUST be closed.
             */
            image?.close()
        }
    }

    // ========================================
    // IMAGE -> BITMAP
    // ========================================

    private fun imageToBitmap(
        image: Image
    ): Bitmap? {

        val plane =
            image.planes
                .firstOrNull()
                ?: return null

        val buffer =
            plane.buffer

        val pixelStride =
            plane.pixelStride

        val rowStride =
            plane.rowStride

        val rowPadding =
            rowStride -
                    pixelStride *
                    CAPTURE_WIDTH

        val bitmapWidth =
            CAPTURE_WIDTH +
                    rowPadding /
                    pixelStride

        val bitmap =
            Bitmap.createBitmap(
                bitmapWidth,
                CAPTURE_HEIGHT,
                Bitmap.Config.ARGB_8888
            )

        buffer.rewind()

        bitmap.copyPixelsFromBuffer(
            buffer
        )

        /*
         * Remove row padding.
         */
        if (
            bitmapWidth !=
            CAPTURE_WIDTH
        ) {

            val cropped =
                Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    CAPTURE_WIDTH,
                    CAPTURE_HEIGHT
                )

            bitmap.recycle()

            return cropped
        }

        return bitmap
    }

    // ========================================
    // DUPLICATE FRAME DETECTION
    // ========================================

    /*
     * Milestone 8B-3. Extract pixel data from a bitmap
     * for adaptive change magnitude computation.
     */
    private fun bitmapToPixels(
        bitmap: Bitmap
    ): IntArray {

        val width = bitmap.width
        val height = bitmap.height
        val pixels =
            IntArray(width * height)

        bitmap.getPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        return pixels
    }

    private fun hasMeaningfulChange(
        previous: Bitmap,
        current: Bitmap
    ): Boolean {

        /*
         * Dimensions should normally always match.
         */
        if (
            previous.width !=
            current.width ||
            previous.height !=
            current.height
        ) {

            return true
        }

        var sampledPixels =
            0

        var differentPixels =
            0

        /*
         * Sample the image instead of comparing
         * every pixel.
         */
        var y =
            0

        while (
            y < current.height
        ) {

            var x =
                0

            while (
                x < current.width
            ) {

                val previousPixel =
                    previous.getPixel(
                        x,
                        y
                    )

                val currentPixel =
                    current.getPixel(
                        x,
                        y
                    )

                val previousRed =
                    (previousPixel shr 16) and 0xFF

                val previousGreen =
                    (previousPixel shr 8) and 0xFF

                val previousBlue =
                    previousPixel and 0xFF

                val currentRed =
                    (currentPixel shr 16) and 0xFF

                val currentGreen =
                    (currentPixel shr 8) and 0xFF

                val currentBlue =
                    currentPixel and 0xFF

                val redDifference =
                    abs(
                        currentRed -
                                previousRed
                    )

                val greenDifference =
                    abs(
                        currentGreen -
                                previousGreen
                    )

                val blueDifference =
                    abs(
                        currentBlue -
                                previousBlue
                    )

                sampledPixels++

                if (
                    redDifference >
                    PIXEL_DIFFERENCE_THRESHOLD ||

                    greenDifference >
                    PIXEL_DIFFERENCE_THRESHOLD ||

                    blueDifference >
                    PIXEL_DIFFERENCE_THRESHOLD
                ) {

                    differentPixels++
                }

                x += SAMPLE_STEP
            }

            y += SAMPLE_STEP
        }

        if (
            sampledPixels == 0
        ) {

            return false
        }

        val changeRatio =
            differentPixels.toFloat() /
                    sampledPixels.toFloat()

        /*
         * Save only when enough of the screen
         * has changed.
         */
        return changeRatio >=
                CHANGE_THRESHOLD
    }

    // ========================================
    // SAVE BITMAP
    // ========================================

    private fun saveBitmap(
        bitmap: Bitmap
    ) {

        val sessionId =
            currentSessionId
                ?: return

        /*
         * Safety limit.
         */
        if (
            frameCounter >=
            MAX_STORED_FRAMES
        ) {

            return
        }

        val capturesDirectory =
            File(
                filesDir,
                "captures/$sessionId"
            )

        if (
            !capturesDirectory.exists()
        ) {

            capturesDirectory.mkdirs()
        }

        /*
         * Cleanup old files before creating
         * another one.
         */
        cleanupOldFrames(
            capturesDirectory
        )

        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss_SSS",
                Locale.US
            ).format(
                Date()
            )

        frameCounter++

        val file =
            File(
                capturesDirectory,
                "frame_${timestamp}_$frameCounter.jpg"
            )

        try {

            FileOutputStream(
                file
            ).use { output ->

                val compressed =
                    bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        JPEG_QUALITY,
                        output
                    )

                if (!compressed) {

                    file.delete()

                    frameCounter--

                    return
                }
            }

            /*
             * Store metadata in Room.
             *
             * Milestone 7S: the session-active guard is
             * the safety net for the stop command sent by
             * SessionRepository.endSession. If the user
             * ended the session while this frame was in
             * flight, the frame is discarded, the file
             * removed and capture stops - no data from an
             * ended session is persisted.
             */
            serviceScope.launch {

                try {

                    val sessionActive =
                        sessionRepository
                            .isSessionActive(
                                sessionId
                            )

                    if (!sessionActive) {

                        file.delete()

                        frameCounter--

                        stopCaptureService()

                        return@launch
                    }

                    sessionRepository
                        .insertCapturedFrame(
                            CapturedFrame(
                                sessionId =
                                    sessionId,

                                filePath =
                                    file.absolutePath
                            )
                        )

                } catch (
                    exception: Exception
                ) {

                    exception.printStackTrace()

                    file.delete()
                }
            }

        } catch (
            exception: Exception
        ) {

            exception.printStackTrace()

            file.delete()

            frameCounter--
        }
    }

    // ========================================
    // CLEANUP OLD FRAMES
    // ========================================

    private fun cleanupOldFrames(
        directory: File
    ) {

        val files =
            directory
                .listFiles { file ->

                    file.isFile &&
                            file.extension
                                .equals(
                                    "jpg",
                                    ignoreCase = true
                                )
                }
                ?.sortedBy {
                    it.lastModified()
                }
                ?: return

        val excess =
            files.size -
                    MAX_STORED_FRAMES

        if (
            excess <= 0
        ) {

            return
        }

        files
            .take(excess)
            .forEach { file ->

                file.delete()
            }
    }

    // ========================================
    // NOTIFICATION
    // ========================================

    private fun createNotification():
            Notification {

        return NotificationCompat
            .Builder(
                this,
                CHANNEL_ID
            )
            .setContentTitle(
                "FeedSense"
            )
            .setContentText(
                "Screen capture is active"
            )
            .setSmallIcon(
                R.mipmap.ic_launcher
            )
            .setOngoing(true)
            .setCategory(
                NotificationCompat.CATEGORY_SERVICE
            )
            .build()
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "FeedSense Screen Capture",
                    NotificationManager
                        .IMPORTANCE_LOW
                )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    // ========================================
    // STOP CAPTURE
    // ========================================

    private fun stopCaptureService() {

        /*
         * Release VirtualDisplay first.
         */
        virtualDisplay?.release()
        virtualDisplay = null

        /*
         * Stop receiving images.
         */
        imageReader?.setOnImageAvailableListener(
            null,
            null
        )

        imageReader?.close()
        imageReader = null

        /*
         * Stop MediaProjection.
         */
        mediaProjection?.stop()
        mediaProjection = null

        /*
         * Release comparison bitmap.
         */
        previousSavedBitmap?.let {

            if (!it.isRecycled) {
                it.recycle()
            }
        }

        previousSavedBitmap = null

        previousSavedPixels = null

        /*
         * Reset state.
         */
        currentSessionId = null

        frameCounter = 0

        lastFrameEvaluationTime = 0L

        /*
         * Remove foreground notification.
         */
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.N
        ) {

            stopForeground(
                STOP_FOREGROUND_REMOVE
            )

        } else {

            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    // ========================================
    // DESTROY
    // ========================================

    override fun onDestroy() {

        /*
         * Full defensive cleanup.
         */
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.setOnImageAvailableListener(
            null,
            null
        )

        imageReader?.close()
        imageReader = null

        mediaProjection?.stop()
        mediaProjection = null

        previousSavedBitmap?.let {

            if (!it.isRecycled) {
                it.recycle()
            }
        }

        previousSavedBitmap = null

        previousSavedPixels = null

        currentSessionId = null

        frameCounter = 0

        lastFrameEvaluationTime = 0L

        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }
}
