package io.github.cybersafetyid.faktel.core.geometry

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A 2D point in pixel coordinates (origin top-left, y grows downwards). */
public data class Point(val x: Double, val y: Double) {
    public fun distanceTo(other: Point): Double = hypot(x - other.x, y - other.y)
}

/** An axis-aligned rectangle in pixel coordinates. */
public data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    public val width: Double get() = right - left
    public val height: Double get() = bottom - top
    public val centerX: Double get() = (left + right) / 2.0
    public val centerY: Double get() = (top + bottom) / 2.0
    public val area: Double get() = max(0.0, width) * max(0.0, height)

    /** Intersection-over-union with [other], in `0.0..1.0`. */
    public fun iou(other: Rect): Double {
        val iw = min(right, other.right) - max(left, other.left)
        val ih = min(bottom, other.bottom) - max(top, other.top)
        if (iw <= 0.0 || ih <= 0.0) return 0.0
        val inter = iw * ih
        val union = area + other.area - inter
        return if (union <= 0.0) 0.0 else inter / union
    }

    /** Returns this rectangle clamped to `[0, width] x [0, height]`. */
    public fun clampTo(width: Int, height: Int): Rect = Rect(
        left.coerceIn(0.0, width.toDouble()),
        top.coerceIn(0.0, height.toDouble()),
        right.coerceIn(0.0, width.toDouble()),
        bottom.coerceIn(0.0, height.toDouble()),
    )
}

/**
 * A convex quadrilateral with corners in a fixed clockwise order starting at the top-left, as seen in the image.
 */
public data class Quad(
    val topLeft: Point,
    val topRight: Point,
    val bottomRight: Point,
    val bottomLeft: Point,
) {
    /** Corners in clockwise order starting at [topLeft]. */
    public val corners: List<Point> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    public val topEdge: Double get() = topLeft.distanceTo(topRight)
    public val bottomEdge: Double get() = bottomLeft.distanceTo(bottomRight)
    public val leftEdge: Double get() = topLeft.distanceTo(bottomLeft)
    public val rightEdge: Double get() = topRight.distanceTo(bottomRight)

    /** Mean of the horizontal edges divided by mean of the vertical edges. `>1` means landscape. */
    public val aspectRatio: Double
        get() = ((topEdge + bottomEdge) / 2.0) / ((leftEdge + rightEdge) / 2.0)

    /** Polygon area (shoelace formula). */
    public val area: Double
        get() {
            val c = corners
            var s = 0.0
            for (i in c.indices) {
                val a = c[i]
                val b = c[(i + 1) % c.size]
                s += a.x * b.y - b.x * a.y
            }
            return kotlin.math.abs(s) / 2.0
        }

    /** Returns the same quad with its corners cyclically shifted by [steps] positions (clockwise). */
    public fun rotated(steps: Int): Quad {
        val c = corners
        val n = ((steps % 4) + 4) % 4
        return Quad(c[n], c[(n + 1) % 4], c[(n + 2) % 4], c[(n + 3) % 4])
    }

    public companion object {
        /**
         * Builds a [Quad] from four points in arbitrary order by sorting them clockwise around their centroid,
         * starting from the corner closest to the image's top-left.
         */
        public fun fromUnordered(points: List<Point>): Quad {
            require(points.size == 4) { "A quad needs exactly 4 points, got ${points.size}" }
            val cx = points.sumOf { it.x } / 4.0
            val cy = points.sumOf { it.y } / 4.0
            // Ascending angle in image space (y down) == clockwise on screen.
            val sorted = points.sortedBy { atan2(it.y - cy, it.x - cx) }
            val start = sorted.indices.minBy { sorted[it].x + sorted[it].y }
            return Quad(
                sorted[start],
                sorted[(start + 1) % 4],
                sorted[(start + 2) % 4],
                sorted[(start + 3) % 4],
            )
        }
    }
}
