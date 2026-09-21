package io.github.cybersafetyid.faktel.ktp

import io.github.cybersafetyid.faktel.core.geometry.Point
import io.github.cybersafetyid.faktel.core.geometry.Quad
import io.github.cybersafetyid.faktel.core.image.RgbImage

/** Renders a flat-coloured card quad over a flat background; optionally a bright patch marks the "portrait" area. */
fun scene(
    width: Int,
    height: Int,
    quad: Quad,
    background: Int = 0xC8C8C8,
    card: Int = 0x2A5FA8,
): RgbImage {
    val d = ByteArray(width * height * 3)
    val c = quad.corners
    for (y in 0 until height) for (x in 0 until width) {
        val inside = inside(c, x + 0.5, y + 0.5)
        val col = if (inside) card else background
        val o = (y * width + x) * 3
        d[o] = (col shr 16).toByte()
        d[o + 1] = (col shr 8).toByte()
        d[o + 2] = col.toByte()
    }
    return RgbImage(width, height, d)
}

private fun inside(poly: List<Point>, x: Double, y: Double): Boolean {
    var sign = 0
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        val cross = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
        val s = if (cross > 0) 1 else -1
        if (sign == 0) sign = s else if (s != sign) return false
    }
    return true
}

fun cardQuad(cx: Double, cy: Double, w: Double, h: Double, degrees: Double = 0.0): Quad {
    val r = degrees * kotlin.math.PI / 180
    fun p(dx: Double, dy: Double) = Point(
        cx + dx * kotlin.math.cos(r) - dy * kotlin.math.sin(r),
        cy + dx * kotlin.math.sin(r) + dy * kotlin.math.cos(r),
    )
    return Quad(p(-w / 2, -h / 2), p(w / 2, -h / 2), p(w / 2, h / 2), p(-w / 2, h / 2))
}
