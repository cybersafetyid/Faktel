package io.github.cybersafetyid.faktel.core.geometry

import kotlin.math.abs

/** A 3x3 projective transform in row-major order, mapping source to destination coordinates. */
public class Homography internal constructor(private val m: DoubleArray) {
    init {
        require(m.size == 9)
    }

    public fun map(x: Double, y: Double): Point {
        val w = m[6] * x + m[7] * y + m[8]
        return Point((m[0] * x + m[1] * y + m[2]) / w, (m[3] * x + m[4] * y + m[5]) / w)
    }

    public companion object {
        /**
         * Solves for the homography mapping each of the four [src] points onto the matching [dst] point.
         * @throws IllegalArgumentException if the points are degenerate (e.g. collinear).
         */
        public fun fromCorrespondences(src: List<Point>, dst: List<Point>): Homography {
            require(src.size == 4 && dst.size == 4) { "Exactly 4 point pairs are required" }
            val a = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val (x, y) = src[i]
                val (u, v) = dst[i]
                a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
            }
            val h = solveLinear(a)
            return Homography(doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0))
        }
    }
}

/** A 2D similarity transform (uniform scale + rotation + translation): `dst = [[a,-b],[b,a]] * src + t`. */
public class SimilarityTransform internal constructor(
    public val a: Double,
    public val b: Double,
    public val tx: Double,
    public val ty: Double,
) {
    public fun map(x: Double, y: Double): Point = Point(a * x - b * y + tx, b * x + a * y + ty)

    /** The transform that undoes this one. */
    public fun inverse(): SimilarityTransform {
        val d = a * a + b * b
        val ia = a / d
        val ib = -b / d
        return SimilarityTransform(ia, ib, -(ia * tx - ib * ty), -(ib * tx + ia * ty))
    }

    public companion object {
        /**
         * Least-squares similarity transform (Umeyama, closed form) that maps [src] onto [dst].
         */
        public fun estimate(src: List<Point>, dst: List<Point>): SimilarityTransform {
            require(src.size == dst.size && src.size >= 2) { "Need >= 2 matching point pairs" }
            val n = src.size.toDouble()
            val sx = src.sumOf { it.x } / n
            val sy = src.sumOf { it.y } / n
            val dx = dst.sumOf { it.x } / n
            val dy = dst.sumOf { it.y } / n
            var num1 = 0.0
            var num2 = 0.0
            var den = 0.0
            for (i in src.indices) {
                val px = src[i].x - sx
                val py = src[i].y - sy
                val qx = dst[i].x - dx
                val qy = dst[i].y - dy
                num1 += px * qx + py * qy
                num2 += px * qy - py * qx
                den += px * px + py * py
            }
            require(den > 1e-9) { "Degenerate source points" }
            val a = num1 / den
            val b = num2 / den
            return SimilarityTransform(a, b, dx - (a * sx - b * sy), dy - (b * sx + a * sy))
        }
    }
}

/** Gauss-Jordan elimination with partial pivoting on an augmented `n x (n+1)` matrix. */
internal fun solveLinear(aug: Array<DoubleArray>): DoubleArray {
    val n = aug.size
    for (col in 0 until n) {
        var pivot = col
        for (r in col + 1 until n) if (abs(aug[r][col]) > abs(aug[pivot][col])) pivot = r
        require(abs(aug[pivot][col]) > 1e-12) { "Singular system (degenerate geometry)" }
        val tmp = aug[col]
        aug[col] = aug[pivot]
        aug[pivot] = tmp
        val pv = aug[col][col]
        for (c in col..n) aug[col][c] /= pv
        for (r in 0 until n) {
            if (r == col) continue
            val f = aug[r][col]
            if (f == 0.0) continue
            for (c in col..n) aug[r][c] -= f * aug[col][c]
        }
    }
    return DoubleArray(n) { aug[it][n] }
}
