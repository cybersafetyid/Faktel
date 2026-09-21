package io.github.cybersafetyid.faktel.ktp

import io.github.cybersafetyid.faktel.core.geometry.Point
import io.github.cybersafetyid.faktel.core.geometry.Quad
import io.github.cybersafetyid.faktel.core.image.RgbImage
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Model-free card locator (pure Kotlin, no native code), meant as a fast fallback and for low-end devices.
 *
 * Method: estimate the background colour from the image border, segment everything that differs from it (Otsu),
 * keep the largest connected blob, take its convex hull and fit four corners.
 *
 * Works best with a card on a plain, contrasting surface that leaves a visible margin on all sides. It will fail on
 * cluttered backgrounds, cards that fill the whole frame, and cards whose colour matches the surface - a learned
 * detector is the right tool there, which is why [KtpDetector] is an interface.
 */
public class ClassicalKtpDetector(private val config: Config = Config()) : KtpDetector {

    /**
     * @property analysisSize longest side (px) of the working copy; larger is slower, not necessarily better
     * @property minAreaRatio smallest accepted card area as a fraction of the image
     * @property maxAreaRatio largest accepted card area as a fraction of the image
     * @property minContrast minimum Otsu-separated colour distance (0..441) between card and background
     * @property minRectangularity blob area / fitted-quad area required (1.0 = perfect rectangle)
     */
    public data class Config(
        val analysisSize: Int = 320,
        val minAreaRatio: Double = 0.08,
        val maxAreaRatio: Double = 0.97,
        val minContrast: Int = 30,
        val minRectangularity: Double = 0.85,
    )

    override fun detect(image: RgbImage): KtpDetection? {
        val scale = minOf(1.0, config.analysisSize.toDouble() / maxOf(image.width, image.height))
        val small = if (scale < 1.0) {
            image.resize(maxOf(8, (image.width * scale).toInt()), maxOf(8, (image.height * scale).toInt()))
        } else {
            image
        }
        val w = small.width
        val h = small.height

        val dist = colourDistanceFromBackground(small)
        val threshold = otsu(dist) ?: return null
        val mask = BooleanArray(w * h) { dist[it] > threshold }
        val blob = largestComponent(mask, w, h) ?: return null

        val hull = convexHull(boundaryPixels(blob, w, h))
        if (hull.size < 4) return null
        val quad = fitQuad(hull) ?: return null

        val areaRatio = quad.area / (w.toDouble() * h)
        if (areaRatio < config.minAreaRatio || areaRatio > config.maxAreaRatio) return null
        val rectangularity = (polygonArea(hull) / quad.area).coerceAtMost(1.0)
        if (rectangularity < config.minRectangularity) return null

        val inv = 1.0 / (if (scale < 1.0) small.width.toDouble() / image.width else 1.0)
        // Pixel centres -> corner coordinates in the source image.
        fun back(p: Point) = Point((p.x + 0.5) * inv, (p.y + 0.5) * inv)
        val full = Quad(back(quad.topLeft), back(quad.topRight), back(quad.bottomRight), back(quad.bottomLeft))
        return KtpDetection(full, rectangularity)
    }

    /** Distance of each pixel's colour from the median border colour, scaled to 0..255, lightly smoothed. */
    private fun colourDistanceFromBackground(img: RgbImage): IntArray {
        val w = img.width
        val h = img.height
        val band = maxOf(2, minOf(w, h) / 50)
        val hist = Array(3) { IntArray(256) }
        var n = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (x < band || y < band || x >= w - band || y >= h - band) {
                hist[0][img.r(x, y)]++
                hist[1][img.g(x, y)]++
                hist[2][img.b(x, y)]++
                n++
            }
        }
        val bg = IntArray(3) { c ->
            var acc = 0
            var v = 0
            while (acc < n / 2 && v < 255) acc += hist[c][v++]
            v
        }
        val raw = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val dr = img.r(x, y) - bg[0]
            val dg = img.g(x, y) - bg[1]
            val db = img.b(x, y) - bg[2]
            raw[y * w + x] = (sqrt((dr * dr + dg * dg + db * db).toDouble()) * 255 / 441.67).toInt()
        }
        return boxBlur(raw, w, h, radius = 1)
    }

    private fun boxBlur(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val tmp = IntArray(src.size)
        val out = IntArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            var c = 0
            for (k in -radius..radius) {
                val xx = x + k
                if (xx in 0 until w) {
                    s += src[y * w + xx]
                    c++
                }
            }
            tmp[y * w + x] = s / c
        }
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            var c = 0
            for (k in -radius..radius) {
                val yy = y + k
                if (yy in 0 until h) {
                    s += tmp[yy * w + x]
                    c++
                }
            }
            out[y * w + x] = s / c
        }
        return out
    }

    /** Otsu threshold on 0..255 values, or null when the two classes are too close to be a card on a background. */
    private fun otsu(values: IntArray): Int? {
        val hist = IntArray(256)
        for (v in values) hist[v.coerceIn(0, 255)]++
        val total = values.size.toDouble()
        var sumAll = 0.0
        for (i in 0 until 256) sumAll += i * hist[i].toDouble()
        var wB = 0.0
        var sumB = 0.0
        var best = -1.0
        var bestT = 0
        var meanLow = 0.0
        var meanHigh = 0.0
        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) {
                best = between
                bestT = t
                meanLow = mB
                meanHigh = mF
            }
        }
        val minSep = config.minContrast * 255.0 / 441.67
        return if (best < 0 || meanHigh - meanLow < minSep) null else bestT
    }

    /** Pixel indices of the largest 4-connected component, or null if the mask is empty. */
    private fun largestComponent(mask: BooleanArray, w: Int, h: Int): BooleanArray? {
        val label = IntArray(w * h)
        val stack = IntArray(w * h)
        var bestLabel = 0
        var bestSize = 0
        var next = 0
        for (start in mask.indices) {
            if (!mask[start] || label[start] != 0) continue
            next++
            var sp = 0
            var size = 0
            stack[sp++] = start
            label[start] = next
            while (sp > 0) {
                val p = stack[--sp]
                size++
                val x = p % w
                val y = p / w
                if (x > 0 && mask[p - 1] && label[p - 1] == 0) { label[p - 1] = next; stack[sp++] = p - 1 }
                if (x < w - 1 && mask[p + 1] && label[p + 1] == 0) { label[p + 1] = next; stack[sp++] = p + 1 }
                if (y > 0 && mask[p - w] && label[p - w] == 0) { label[p - w] = next; stack[sp++] = p - w }
                if (y < h - 1 && mask[p + w] && label[p + w] == 0) { label[p + w] = next; stack[sp++] = p + w }
            }
            if (size > bestSize) {
                bestSize = size
                bestLabel = next
            }
        }
        if (bestSize == 0) return null
        return BooleanArray(w * h) { label[it] == bestLabel }
    }

    private fun boundaryPixels(blob: BooleanArray, w: Int, h: Int): List<Point> {
        val pts = ArrayList<Point>()
        for (y in 0 until h) for (x in 0 until w) {
            if (!blob[y * w + x]) continue
            val edge = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
                !blob[y * w + x - 1] || !blob[y * w + x + 1] || !blob[(y - 1) * w + x] || !blob[(y + 1) * w + x]
            if (edge) pts += Point(x.toDouble(), y.toDouble())
        }
        return pts
    }

    /** Andrew's monotone chain. Returns hull vertices in order, without repeating the first point. */
    private fun convexHull(points: List<Point>): List<Point> {
        val p = points.sortedWith(compareBy({ it.x }, { it.y }))
        if (p.size < 3) return p
        fun cross(o: Point, a: Point, b: Point) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val hull = ArrayList<Point>()
        for (pt in p) {
            while (hull.size >= 2 && cross(hull[hull.size - 2], hull[hull.size - 1], pt) <= 0) hull.removeAt(hull.size - 1)
            hull += pt
        }
        val lower = hull.size + 1
        for (i in p.size - 2 downTo 0) {
            while (hull.size >= lower && cross(hull[hull.size - 2], hull[hull.size - 1], p[i]) <= 0) {
                hull.removeAt(hull.size - 1)
            }
            hull += p[i]
        }
        hull.removeAt(hull.size - 1)
        return hull
    }

    private fun polygonArea(poly: List<Point>): Double {
        var s = 0.0
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            s += a.x * b.y - b.x * a.y
        }
        return abs(s) / 2.0
    }

    /**
     * Four corners of a convex polygon: find the minimum-area bounding rectangle's orientation, then take the hull
     * point most extreme towards each of its corners. Unlike a plain axis-aligned extreme search this also handles
     * cards rotated in-plane by ~45 degrees, and keeps perspective skew.
     */
    private fun fitQuad(hull: List<Point>): Quad? {
        var bestArea = Double.MAX_VALUE
        var bu = Point(1.0, 0.0)
        for (i in hull.indices) {
            val a = hull[i]
            val b = hull[(i + 1) % hull.size]
            val len = a.distanceTo(b)
            if (len < 1e-9) continue
            val u = Point((b.x - a.x) / len, (b.y - a.y) / len)
            val v = Point(-u.y, u.x)
            var minA = Double.MAX_VALUE; var maxA = -Double.MAX_VALUE
            var minB = Double.MAX_VALUE; var maxB = -Double.MAX_VALUE
            for (p in hull) {
                val pa = p.x * u.x + p.y * u.y
                val pb = p.x * v.x + p.y * v.y
                if (pa < minA) minA = pa
                if (pa > maxA) maxA = pa
                if (pb < minB) minB = pb
                if (pb > maxB) maxB = pb
            }
            val area = (maxA - minA) * (maxB - minB)
            if (area < bestArea) {
                bestArea = area
                bu = u
            }
        }
        val bv = Point(-bu.y, bu.x)
        fun a(p: Point) = p.x * bu.x + p.y * bu.y
        fun b(p: Point) = p.x * bv.x + p.y * bv.y
        val corners = listOf(
            hull.minBy { a(it) + b(it) },
            hull.maxBy { a(it) - b(it) },
            hull.maxBy { a(it) + b(it) },
            hull.minBy { a(it) - b(it) },
        )
        if (corners.toSet().size < 4) return null
        return Quad.fromUnordered(corners)
    }
}
