package io.github.cybersafetyid.faktel.core

import io.github.cybersafetyid.faktel.core.geometry.Homography
import io.github.cybersafetyid.faktel.core.geometry.Point
import io.github.cybersafetyid.faktel.core.geometry.Quad
import io.github.cybersafetyid.faktel.core.geometry.Rect
import io.github.cybersafetyid.faktel.core.geometry.SimilarityTransform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryTest {
    private fun near(a: Double, b: Double, eps: Double = 1e-6) = assertTrue(abs(a - b) < eps, "$a != $b")

    @Test
    fun quadFromUnorderedSortsClockwiseFromTopLeft() {
        val q = Quad.fromUnordered(listOf(Point(100.0, 80.0), Point(0.0, 0.0), Point(0.0, 80.0), Point(100.0, 0.0)))
        assertEquals(Point(0.0, 0.0), q.topLeft)
        assertEquals(Point(100.0, 0.0), q.topRight)
        assertEquals(Point(100.0, 80.0), q.bottomRight)
        assertEquals(Point(0.0, 80.0), q.bottomLeft)
        near(1.25, q.aspectRatio)
        near(8000.0, q.area)
    }

    @Test
    fun quadRotationShiftsCorners() {
        val q = Quad(Point(0.0, 0.0), Point(2.0, 0.0), Point(2.0, 1.0), Point(0.0, 1.0))
        assertEquals(Point(2.0, 0.0), q.rotated(1).topLeft)
        assertEquals(Point(0.0, 1.0), q.rotated(-1).topLeft)
        near(0.5, q.rotated(1).aspectRatio)
    }

    @Test
    fun rectIou() {
        val a = Rect(0.0, 0.0, 10.0, 10.0)
        near(1.0, a.iou(a))
        near(0.0, a.iou(Rect(20.0, 20.0, 30.0, 30.0)))
        near(25.0 / 175.0, a.iou(Rect(5.0, 5.0, 15.0, 15.0)))
    }

    @Test
    fun homographyMapsCorrespondences() {
        val src = listOf(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0))
        val dst = listOf(Point(2.0, 1.0), Point(12.0, 3.0), Point(9.0, 14.0), Point(-1.0, 9.0))
        val h = Homography.fromCorrespondences(src, dst)
        for (i in 0 until 4) {
            val p = h.map(src[i].x, src[i].y)
            near(dst[i].x, p.x)
            near(dst[i].y, p.y)
        }
    }

    @Test
    fun similarityRecoversKnownTransform() {
        val src = listOf(Point(0.0, 0.0), Point(1.0, 0.0), Point(0.0, 1.0), Point(2.0, 3.0))
        // scale 2, 90 degree rotation (a=0,b=2), translation (5, -1)
        val dst = src.map { Point(-2.0 * it.y + 5.0, 2.0 * it.x - 1.0) }
        val t = SimilarityTransform.estimate(src, dst)
        near(0.0, t.a)
        near(2.0, t.b)
        val p = t.inverse().map(dst[3].x, dst[3].y)
        near(2.0, p.x)
        near(3.0, p.y)
    }
}
