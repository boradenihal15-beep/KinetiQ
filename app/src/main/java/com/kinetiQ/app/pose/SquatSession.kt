package com.kinetiQ.app.pose

import android.content.Context
import android.os.SystemClock
import com.kinetiQ.app.core.LiveFrame
import com.kinetiQ.app.core.Pt
import com.kinetiQ.app.core.SessionSummary
import com.kinetiQ.app.core.SquatAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiState(
    val frame: LiveFrame? = null,
    val points: List<Pt>? = null,   // normalised 0..1, used for drawing
    val imgW: Int = 0,
    val imgH: Int = 0,
    val modelReady: Boolean = true
)

/** Glue: pose detector -> squat analyzer -> UI state. */
class SquatSession(context: Context) {
    private val analyzer = SquatAnalyzer()
    private val startMs = SystemClock.elapsedRealtime()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val detector = PoseDetector(context) { onPose(it) }

    init {
        _state.value = UiState(modelReady = detector.isReady)
    }

    private fun onPose(f: PoseFrame) {
        val now = SystemClock.elapsedRealtime()
        val lms = f.landmarks
        when {
            lms == null || f.people == 0 ->
                _state.value = UiState(analyzer.unavailable("No person detected", now), null, f.w, f.h)
            f.people > 1 ->
                _state.value = UiState(
                    analyzer.unavailable("Please ensure only one person is visible", now), null, f.w, f.h
                )
            else -> {
                val norm = lms.map { Pt(it.x(), it.y(), it.visibility().orElse(0f)) }
                val px = norm.map { Pt(it.x * f.w, it.y * f.h, it.vis) }
                _state.value = UiState(analyzer.analyze(px, now), norm, f.w, f.h)
            }
        }
    }

    fun finish(): SessionSummary = analyzer.summary(SystemClock.elapsedRealtime() - startMs)

    fun release() = detector.close()
}