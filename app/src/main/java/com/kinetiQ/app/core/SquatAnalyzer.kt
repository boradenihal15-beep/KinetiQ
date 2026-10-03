package com.kinetiQ.app.core

import kotlin.math.abs

enum class Phase { STANDING, DESCENDING, BOTTOM, ASCENDING }
enum class Status { GOOD, WARNING, INCORRECT, UNKNOWN }

enum class Issue(val label: String, val tip: String) {
    SHALLOW_DEPTH("Insufficient squat depth", "Go a bit deeper"),
    FORWARD_LEAN("Excessive forward lean", "Keep your chest up"),
    KNEE_VALGUS("Knees caving inward", "Push your knees out")
}

/** All thresholds live here. They are starting guesses to be tuned on real recordings. */
data class SquatConfig(
    val standingAngle: Float = 160f,   // knee angle above this = standing
    val descendAngle: Float = 150f,    // below this a squat cycle starts
    val bottomAngle: Float = 115f,     // below this = bottom phase
    val goodDepthAngle: Float = 100f,  // at or below this = good depth
    val minRepAngle: Float = 125f,     // must get below this to count as a rep
    val reversalDelta: Float = 12f,    // rise from minimum that means "ascending"
    val minRepMs: Long = 700,
    val leanWarn: Float = 45f,
    val leanBad: Float = 60f,
    val minVisibility: Float = 0.5f,
    val lostTimeoutMs: Long = 1500
)

data class RepResult(
    val number: Int,
    val score: Int,
    val good: Boolean,
    val minKneeAngle: Float,
    val maxLean: Float,
    val issues: List<Issue>,
    val durationMs: Long
)

data class LiveFrame(
    val status: Status,
    val phase: Phase,
    val message: String,
    val kneeAngle: Float?,
    val redPairs: Set<Pair<Int, Int>>,
    val amberPairs: Set<Pair<Int, Int>>,
    val reps: Int,
    val goodReps: Int,
    val lastScore: Int?
)

data class SessionSummary(
    val totalReps: Int,
    val goodReps: Int,
    val needsImprovement: Int,
    val avgScore: Int,
    val topIssue: String?,
    val durationSec: Long,
    val reps: List<RepResult>
)

class SquatAnalyzer(private val cfg: SquatConfig = SquatConfig()) {
    private val repList = mutableListOf<RepResult>()
    private var phase = Phase.STANDING
    private var smoothKnee: Float? = null
    private var minKnee = 180f
    private var maxLean = 0f
    private var cycleFrames = 0
    private var valgusFrames = 0
    private var frontFrames = 0
    private var cycleStart = 0L
    private var lastSeen = 0L
    private var holdMsg: String? = null
    private var holdStatus = Status.GOOD
    private var holdUntil = 0L
    private var lastScore: Int? = null

    @Synchronized
    fun unavailable(msg: String, now: Long): LiveFrame {
        if (lastSeen != 0L && now - lastSeen > cfg.lostTimeoutMs && phase != Phase.STANDING) {
            phase = Phase.STANDING   // discard the unfinished cycle
            smoothKnee = null
        }
        return frame(Status.UNKNOWN, msg, null, emptySet(), emptySet())
    }

    /** lm = 33 landmarks in PIXEL coordinates (so angles are not distorted). */
    @Synchronized
    fun analyze(lm: List<Pt>?, now: Long): LiveFrame {
        if (lm == null || lm.size < 33) return unavailable("No person detected", now)

        val leftIdx = intArrayOf(11, 23, 25, 27)
        val rightIdx = intArrayOf(12, 24, 26, 28)
        val leftVis = leftIdx.sumOf { lm[it].vis.toDouble() }
        val rightVis = rightIdx.sumOf { lm[it].vis.toDouble() }
        val useLeft = leftVis >= rightVis
        val idx = if (useLeft) leftIdx else rightIdx
        val sh = lm[idx[0]]
        val hip = lm[idx[1]]
        val knee = lm[idx[2]]
        val ank = lm[idx[3]]

        if (minOf(sh.vis, hip.vis, knee.vis, ank.vis) < cfg.minVisibility) {
            return unavailable("Step back so your full body is visible", now)
        }
        lastSeen = now

        // ---- features ----
        val rawKnee = Geometry.angle(hip, knee, ank)
        val k = smoothKnee?.let { it + 0.5f * (rawKnee - it) } ?: rawKnee
        smoothKnee = k
        val lean = Geometry.leanFromVertical(hip, sh)

        val bothSides = listOf(11, 12, 23, 24, 25, 26, 27, 28).all { lm[it].vis >= cfg.minVisibility }
        val torsoLen = Geometry.dist(hip, sh).coerceAtLeast(1f)
        val frontView = bothSides && abs(lm[11].x - lm[12].x) / torsoLen > 0.7f

        var valgusL = false
        var valgusR = false
        if (frontView && phase != Phase.STANDING && k < 140f) {
            val midX = (lm[23].x + lm[24].x) / 2f
            valgusL = kneeCollapsed(lm[23], lm[25], lm[27], midX)
            valgusR = kneeCollapsed(lm[24], lm[26], lm[28], midX)
        }
        val valgus = valgusL || valgusR

        // ---- state machine ----
        when (phase) {
            Phase.STANDING -> if (k < cfg.descendAngle) {
                phase = Phase.DESCENDING
                cycleStart = now
                minKnee = k
                maxLean = lean
                cycleFrames = 0
                valgusFrames = 0
                frontFrames = 0
                holdMsg = null
            }
            Phase.DESCENDING -> {
                if (k < cfg.bottomAngle) phase = Phase.BOTTOM
                else if (k > minKnee + cfg.reversalDelta) phase = Phase.ASCENDING
            }
            Phase.BOTTOM -> if (k > minKnee + cfg.reversalDelta) phase = Phase.ASCENDING
            Phase.ASCENDING -> {
                if (k > cfg.standingAngle) finishRep(now)
                else if (k < cfg.bottomAngle) phase = Phase.BOTTOM
            }
        }
        if (phase != Phase.STANDING) {
            minKnee = minOf(minKnee, k)
            maxLean = maxOf(maxLean, lean)
            cycleFrames++
            if (valgus) valgusFrames++
            if (frontView) frontFrames++
        }

        // ---- live feedback ----
        val inCycle = phase != Phase.STANDING
        val leanBad = lean > cfg.leanBad
        val leanWarn = lean > cfg.leanWarn
        val red = mutableSetOf<Pair<Int, Int>>()
        val amber = mutableSetOf<Pair<Int, Int>>()
        if (inCycle) {
            val torso = if (useLeft) 11 to 23 else 12 to 24
            if (leanBad) red.add(torso) else if (leanWarn) amber.add(torso)
            if (valgusL) { red.add(23 to 25); red.add(25 to 27) }
            if (valgusR) { red.add(24 to 26); red.add(26 to 28) }
        }
        val status = when {
            inCycle && (leanBad || valgus) -> Status.INCORRECT
            inCycle && leanWarn -> Status.WARNING
            else -> Status.GOOD
        }
        val hm = holdMsg
        val holding = hm != null && now < holdUntil
        val message = when {
            holding -> hm!!
            !inCycle -> "Ready - start your squat"
            valgus -> Issue.KNEE_VALGUS.tip
            leanWarn -> Issue.FORWARD_LEAN.tip
            phase == Phase.DESCENDING -> "Lower slowly"
            phase == Phase.BOTTOM ->
                if (minKnee <= cfg.goodDepthAngle) "Good depth - drive up" else "Go a bit deeper"
            else -> "Push through your feet"
        }
        return frame(if (holding) holdStatus else status, message, k, red, amber)
    }

    @Synchronized
    fun summary(durationMs: Long): SessionSummary {
        val good = repList.count { it.good }
        val avg = if (repList.isEmpty()) 0 else repList.map { it.score }.average().toInt()
        val top = repList.flatMap { it.issues }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key?.label
        return SessionSummary(repList.size, good, repList.size - good, avg, top, durationMs / 1000, repList.toList())
    }

    private fun finishRep(now: Long) {
        val dur = now - cycleStart
        phase = Phase.STANDING
        if (minKnee >= cfg.minRepAngle || dur < cfg.minRepMs) {
            holdMsg = if (minKnee >= cfg.minRepAngle) "Partial squat - go deeper" else "Too fast - slow down"
            holdStatus = Status.WARNING
            holdUntil = now + 2000
            return
        }
        val issues = mutableListOf<Issue>()
        if (minKnee > cfg.goodDepthAngle) issues.add(Issue.SHALLOW_DEPTH)
        if (maxLean > cfg.leanWarn) issues.add(Issue.FORWARD_LEAN)
        val valgusFrac = if (cycleFrames > 0) valgusFrames.toFloat() / cycleFrames else 0f
        val sawFront = frontFrames > cycleFrames / 2
        if (sawFront && valgusFrac > 0.25f) issues.add(Issue.KNEE_VALGUS)

        // Weights: depth and torso are the most measurable from one camera.
        val depthScore = 100f - ((minKnee - cfg.goodDepthAngle) /
                (cfg.minRepAngle - cfg.goodDepthAngle)).coerceIn(0f, 1f) * 60f
        val torsoScore = 100f - ((maxLean - cfg.leanWarn) /
                (cfg.leanBad - cfg.leanWarn)).coerceIn(0f, 1f) * 60f
        val kneeScore = (100f - valgusFrac * 200f).coerceAtLeast(40f)
        val score = if (sawFront) 0.40f * depthScore + 0.35f * torsoScore + 0.25f * kneeScore
        else 0.55f * depthScore + 0.45f * torsoScore

        val good = issues.isEmpty()
        repList.add(RepResult(repList.size + 1, score.toInt(), good, minKnee, maxLean, issues, dur))
        lastScore = score.toInt()
        holdMsg = if (good) "Good squat!" else issues.first().tip
        holdStatus = if (good) Status.GOOD else Status.WARNING
        holdUntil = now + 2000
    }

    /** Knee is "collapsed" if it sits closer to the body midline than the hip-ankle line. */
    private fun kneeCollapsed(hip: Pt, knee: Pt, ank: Pt, midX: Float): Boolean {
        val dy = ank.y - hip.y
        val t = if (abs(dy) < 1f) 0.5f else (knee.y - hip.y) / dy
        val lineX = hip.x + t * (ank.x - hip.x)
        val legLen = Geometry.dist(hip, ank).coerceAtLeast(1f)
        return abs(knee.x - midX) < abs(lineX - midX) - 0.08f * legLen
    }

    private fun frame(
        status: Status, msg: String, knee: Float?,
        red: Set<Pair<Int, Int>>, amber: Set<Pair<Int, Int>>
    ) = LiveFrame(status, phase, msg, knee, red, amber, repList.size, repList.count { it.good }, lastScore)
}