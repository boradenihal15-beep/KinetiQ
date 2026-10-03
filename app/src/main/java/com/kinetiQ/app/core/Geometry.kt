package com.kinetiQ.app.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/** A body landmark. x/y in pixels, vis = visibility 0..1 */
data class Pt(val x: Float, val y: Float, val vis: Float)

object Geometry {
    /** Angle ABC in degrees, vertex at B. */
    fun angle(a: Pt, b: Pt, c: Pt): Float {
        val v1x = a.x - b.x
        val v1y = a.y - b.y
        val v2x = c.x - b.x
        val v2y = c.y - b.y
        val m = sqrt(v1x * v1x + v1y * v1y) * sqrt(v2x * v2x + v2y * v2y)
        if (m < 1e-6f) return 180f
        val cos = ((v1x * v2x + v1y * v2y) / m).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble()).toFloat()
    }

    /** Angle of the bottom->top segment from vertical, degrees (0 = upright). */
    fun leanFromVertical(bottom: Pt, top: Pt): Float {
        val dx = abs(top.x - bottom.x).toDouble()
        val dy = abs(top.y - bottom.y).toDouble()
        return Math.toDegrees(atan2(dx, dy)).toFloat()
    }

    fun dist(a: Pt, b: Pt): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}

object Skeleton {
    val CONNECTIONS = listOf(
        11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
        11 to 23, 12 to 24, 23 to 24,
        23 to 25, 25 to 27, 24 to 26, 26 to 28,
        27 to 29, 29 to 31, 27 to 31, 28 to 30, 30 to 32, 28 to 32
    )
    val JOINTS = listOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32)
}