package io.github.cybersafetyid.faktel.core.image

import io.github.cybersafetyid.faktel.core.geometry.Homography
import io.github.cybersafetyid.faktel.core.geometry.Point
import io.github.cybersafetyid.faktel.core.geometry.Quad
import io.github.cybersafetyid.faktel.core.geometry.SimilarityTransform

/**
 * Perspective-corrects the region bounded by [quad] into an upright `outWidth x outHeight` image
 * (the top-left corner of [quad] becomes the output's top-left, and so on clockwise).
 */
public fun RgbImage.warpPerspective(quad: Quad, outWidth: Int, outHeight: Int): RgbImage {
    val dst = listOf(
        Point(0.0, 0.0),
        Point(outWidth.toDouble(), 0.0),
        Point(outWidth.toDouble(), outHeight.toDouble()),
        Point(0.0, outHeight.toDouble()),
    )
    // Pixel centres: output pixel (x, y) is the centre of a unit square, so shift by 0.5 into corner space.
    val toSource = Homography.fromCorrespondences(dst, quad.corners)
    val out = ByteArray(outWidth * outHeight * 3)
    for (y in 0 until outHeight) {
        for (x in 0 until outWidth) {
            val p = toSource.map(x + 0.5, y + 0.5)
            sampleInto(p.x - 0.5, p.y - 0.5, out, (y * outWidth + x) * 3)
        }
    }
    return RgbImage(outWidth, outHeight, out)
}

/** Applies [transform] (source -> output coordinates) and renders an `outWidth x outHeight` image. */
public fun RgbImage.warpSimilarity(transform: SimilarityTransform, outWidth: Int, outHeight: Int): RgbImage {
    val inv = transform.inverse()
    val out = ByteArray(outWidth * outHeight * 3)
    for (y in 0 until outHeight) {
        for (x in 0 until outWidth) {
            val p = inv.map(x.toDouble(), y.toDouble())
            sampleInto(p.x, p.y, out, (y * outWidth + x) * 3)
        }
    }
    return RgbImage(outWidth, outHeight, out)
}
