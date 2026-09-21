package io.github.cybersafetyid.faktel.core

import io.github.cybersafetyid.faktel.core.image.RgbImage

/** Builds an image by evaluating [f] (returns 0xRRGGBB) at every pixel. */
fun image(width: Int, height: Int, f: (x: Int, y: Int) -> Int): RgbImage {
    val d = ByteArray(width * height * 3)
    for (y in 0 until height) for (x in 0 until width) {
        val c = f(x, y)
        val o = (y * width + x) * 3
        d[o] = (c shr 16).toByte()
        d[o + 1] = (c shr 8).toByte()
        d[o + 2] = c.toByte()
    }
    return RgbImage(width, height, d)
}

fun gray(v: Int): Int = (v shl 16) or (v shl 8) or v
