package com.kinetiQ.app.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

data class PoseFrame(
    val landmarks: List<NormalizedLandmark>?,
    val people: Int,
    val w: Int,
    val h: Int
)

class PoseDetector(
    context: Context,
    private val onResult: (PoseFrame) -> Unit
) {
    @Volatile private var landmarker: PoseLandmarker? = null
    @Volatile private var imgW = 0
    @Volatile private var imgH = 0
    var usingGpu = false
        private set

    val isReady: Boolean get() = landmarker != null

    init {
        landmarker = try {
            build(context, Delegate.CPU)
        } catch (t: Throwable) {
            android.util.Log.e("KinetiQ", "Pose model failed to load", t)
            null
        }
    }

    private fun build(context: Context, delegate: Delegate): PoseLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .setDelegate(delegate)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(2)   // 2 so we can warn when more than one person is visible
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, _ -> handle(result) }
            .setErrorListener { _ -> onResult(PoseFrame(null, 0, imgW, imgH)) }
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }

    private fun handle(result: PoseLandmarkerResult) {
        val all = result.landmarks()
        onResult(PoseFrame(all.firstOrNull(), all.size, imgW, imgH))
    }

    /** Called for every camera frame. mirror = true for the front camera. */
    fun detect(image: ImageProxy, mirror: Boolean) {
        val lm = landmarker
        try {
            if (lm == null) return
            val w = image.width
            val h = image.height
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(image.planes[0].buffer)
            val m = Matrix().apply {
                postRotate(image.imageInfo.rotationDegrees.toFloat())
                if (mirror) postScale(-1f, 1f)
            }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, w, h, m, true)
            imgW = rotated.width
            imgH = rotated.height
            lm.detectAsync(BitmapImageBuilder(rotated).build(), SystemClock.uptimeMillis())
        } catch (e: Exception) {
            // drop this frame
        } finally {
            image.close()
        }
    }

    fun close() {
        val l = landmarker
        landmarker = null
        try { l?.close() } catch (e: Exception) { }
    }
}