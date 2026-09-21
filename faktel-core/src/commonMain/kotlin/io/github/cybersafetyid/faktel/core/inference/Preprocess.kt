package io.github.cybersafetyid.faktel.core.inference

import io.github.cybersafetyid.faktel.core.image.RgbImage
import kotlin.math.min
import kotlin.math.roundToInt

public enum class ChannelOrder { RGB, BGR }

/**
 * Packs the image into a `[1, 3, height, width]` float tensor: `(value - mean) * scale` per channel value.
 *
 * Common recipes: raw 0..255 (`mean = 0, scale = 1`), or `[-1, 1]` (`mean = 127.5, scale = 1/127.5`).
 */
public fun RgbImage.toTensor(order: ChannelOrder, mean: Float = 0f, scale: Float = 1f): Tensor {
    val plane = width * height
    val out = FloatArray(3 * plane)
    val (c0, c2) = if (order == ChannelOrder.RGB) 0 to 2 else 2 to 0
    for (i in 0 until plane) {
        val o = i * 3
        out[i] = ((data[o + c0].toInt() and 0xFF) - mean) * scale
        out[plane + i] = ((data[o + 1].toInt() and 0xFF) - mean) * scale
        out[2 * plane + i] = ((data[o + c2].toInt() and 0xFF) - mean) * scale
    }
    return Tensor(intArrayOf(1, 3, height, width), out)
}

/** The result of [letterbox]: the padded square image and how to map its coordinates back to the source. */
public class Letterbox(
    public val image: RgbImage,
    public val scale: Double,
    public val padX: Int,
    public val padY: Int,
) {
    /** Maps an x coordinate in the letterboxed image to the source image. */
    public fun toSourceX(x: Double): Double = (x - padX) / scale

    /** Maps a y coordinate in the letterboxed image to the source image. */
    public fun toSourceY(y: Double): Double = (y - padY) / scale
}

/**
 * Resizes preserving aspect ratio to fit inside `size x size`, centring it on a canvas filled with [padValue].
 */
public fun RgbImage.letterbox(size: Int, padValue: Int = 0): Letterbox {
    val scale = min(size.toDouble() / width, size.toDouble() / height)
    val nw = (width * scale).roundToInt().coerceIn(1, size)
    val nh = (height * scale).roundToInt().coerceIn(1, size)
    val resized = resize(nw, nh)
    val padX = (size - nw) / 2
    val padY = (size - nh) / 2
    val out = ByteArray(size * size * 3)
    if (padValue != 0) out.fill(padValue.toByte())
    for (y in 0 until nh) {
        resized.data.copyInto(out, ((y + padY) * size + padX) * 3, y * nw * 3, (y + 1) * nw * 3)
    }
    return Letterbox(RgbImage(size, size, out), scale, padX, padY)
}
