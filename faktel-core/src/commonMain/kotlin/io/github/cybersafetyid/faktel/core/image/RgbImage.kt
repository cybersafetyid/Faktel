package io.github.cybersafetyid.faktel.core.image

import io.github.cybersafetyid.faktel.core.geometry.Rect
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * An immutable-by-convention, packed 8-bit RGB image (`R,G,B,R,G,B,...`, row-major, no row padding).
 *
 * Every Faktel algorithm consumes this single type; platform frame formats (Android `Bitmap`/`ImageProxy`,
 * iOS `CVPixelBuffer`/`UIImage`) are converted to it once at the edge of the pipeline.
 */
public class RgbImage(public val width: Int, public val height: Int, public val data: ByteArray) {
    init {
        require(width > 0 && height > 0) { "Image size must be positive: ${width}x$height" }
        require(data.size == width * height * 3) {
            "Expected ${width * height * 3} bytes for ${width}x$height RGB, got ${data.size}"
        }
    }

    /** Returns the red/green/blue value (0..255) at ([x], [y]). */
    public fun r(x: Int, y: Int): Int = data[(y * width + x) * 3].toInt() and 0xFF
    public fun g(x: Int, y: Int): Int = data[(y * width + x) * 3 + 1].toInt() and 0xFF
    public fun b(x: Int, y: Int): Int = data[(y * width + x) * 3 + 2].toInt() and 0xFF

    /** Crops to [region] (clamped to the image bounds, coordinates truncated to whole pixels). */
    public fun crop(region: Rect): RgbImage {
        val x0 = floor(region.left).toInt().coerceIn(0, width - 1)
        val y0 = floor(region.top).toInt().coerceIn(0, height - 1)
        val x1 = floor(region.right).toInt().coerceIn(x0 + 1, width)
        val y1 = floor(region.bottom).toInt().coerceIn(y0 + 1, height)
        val w = x1 - x0
        val h = y1 - y0
        val out = ByteArray(w * h * 3)
        for (y in 0 until h) {
            data.copyInto(out, y * w * 3, ((y0 + y) * width + x0) * 3, ((y0 + y) * width + x0 + w) * 3)
        }
        return RgbImage(w, h, out)
    }

    /** Bilinear resize using half-pixel centres (the same convention as OpenCV `INTER_LINEAR`, no antialiasing). */
    public fun resize(newWidth: Int, newHeight: Int): RgbImage {
        if (newWidth == width && newHeight == height) return this
        val out = ByteArray(newWidth * newHeight * 3)
        val sx = width.toDouble() / newWidth
        val sy = height.toDouble() / newHeight
        for (y in 0 until newHeight) {
            val fy = (y + 0.5) * sy - 0.5
            val y0 = floor(fy).toInt()
            val wy = fy - y0
            val ya = y0.coerceIn(0, height - 1)
            val yb = (y0 + 1).coerceIn(0, height - 1)
            for (x in 0 until newWidth) {
                val fx = (x + 0.5) * sx - 0.5
                val x0 = floor(fx).toInt()
                val wx = fx - x0
                val xa = x0.coerceIn(0, width - 1)
                val xb = (x0 + 1).coerceIn(0, width - 1)
                val o = (y * newWidth + x) * 3
                for (c in 0 until 3) {
                    val top = lerp(px(xa, ya, c), px(xb, ya, c), wx)
                    val bot = lerp(px(xa, yb, c), px(xb, yb, c), wx)
                    out[o + c] = lerp(top, bot, wy).roundToInt().toByte()
                }
            }
        }
        return RgbImage(newWidth, newHeight, out)
    }

    /** Rotates clockwise by [degrees], which must be a multiple of 90. Use it to apply camera sensor rotation. */
    public fun rotate(degrees: Int): RgbImage {
        val d = ((degrees % 360) + 360) % 360
        require(d % 90 == 0) { "Rotation must be a multiple of 90, got $degrees" }
        if (d == 0) return this
        val (nw, nh) = if (d == 180) width to height else height to width
        val out = ByteArray(nw * nh * 3)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val (nx, ny) = when (d) {
                    90 -> (height - 1 - y) to x
                    180 -> (width - 1 - x) to (height - 1 - y)
                    else -> y to (width - 1 - x)
                }
                data.copyInto(out, (ny * nw + nx) * 3, (y * width + x) * 3, (y * width + x) * 3 + 3)
            }
        }
        return RgbImage(nw, nh, out)
    }

    /** Mirrors the image left-to-right (typical for front-camera selfies). */
    public fun flipHorizontal(): RgbImage {
        val out = ByteArray(data.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                data.copyInto(out, (y * width + (width - 1 - x)) * 3, (y * width + x) * 3, (y * width + x) * 3 + 3)
            }
        }
        return RgbImage(width, height, out)
    }

    /** Luma (ITU-R BT.601) grayscale copy. */
    public fun toGray(): GrayImage {
        val out = ByteArray(width * height)
        for (i in out.indices) {
            val o = i * 3
            val r = data[o].toInt() and 0xFF
            val g = data[o + 1].toInt() and 0xFF
            val b = data[o + 2].toInt() and 0xFF
            out[i] = ((299 * r + 587 * g + 114 * b) / 1000).toByte()
        }
        return GrayImage(width, height, out)
    }

    /**
     * Bilinear sample at continuous coordinates, writing 3 bytes into [out] at [outOffset].
     * Positions outside the image produce black.
     */
    internal fun sampleInto(fx: Double, fy: Double, out: ByteArray, outOffset: Int) {
        if (fx < -0.5 || fy < -0.5 || fx > width - 0.5 || fy > height - 0.5) {
            out[outOffset] = 0
            out[outOffset + 1] = 0
            out[outOffset + 2] = 0
            return
        }
        val x0 = floor(fx).toInt()
        val y0 = floor(fy).toInt()
        val wx = fx - x0
        val wy = fy - y0
        val xa = x0.coerceIn(0, width - 1)
        val xb = (x0 + 1).coerceIn(0, width - 1)
        val ya = y0.coerceIn(0, height - 1)
        val yb = (y0 + 1).coerceIn(0, height - 1)
        for (c in 0 until 3) {
            val top = lerp(px(xa, ya, c), px(xb, ya, c), wx)
            val bot = lerp(px(xa, yb, c), px(xb, yb, c), wx)
            out[outOffset + c] = lerp(top, bot, wy).roundToInt().toByte()
        }
    }

    private fun px(x: Int, y: Int, c: Int): Double = (data[(y * width + x) * 3 + c].toInt() and 0xFF).toDouble()

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    public companion object {
        /** From tightly- or loosely-packed `R,G,B,A` bytes (e.g. Android `Bitmap.Config.ARGB_8888` copied to a buffer). */
        public fun fromRgba(width: Int, height: Int, rgba: ByteArray, rowStride: Int = width * 4): RgbImage =
            fromFourChannel(width, height, rgba, rowStride, 0, 1, 2)

        /** From `B,G,R,A` bytes (e.g. iOS `kCVPixelFormatType_32BGRA` camera frames). */
        public fun fromBgra(width: Int, height: Int, bgra: ByteArray, rowStride: Int = width * 4): RgbImage =
            fromFourChannel(width, height, bgra, rowStride, 2, 1, 0)

        /** From an Android NV21 buffer (full Y plane followed by interleaved V,U). */
        public fun fromNv21(width: Int, height: Int, nv21: ByteArray): RgbImage {
            val ySize = width * height
            require(nv21.size >= ySize + (width / 2) * (height / 2) * 2) { "NV21 buffer too small" }
            return yuvToRgb(width, height, nv21, width, nv21, ySize + 1, nv21, ySize, width, 2)
        }

        /**
         * From separate YUV 4:2:0 planes, e.g. Android `ImageProxy` `YUV_420_888` (`planes[0..2]`) or an iOS
         * bi-planar NV12 buffer (pass the interleaved UV plane as both [uPlane] and [vPlane] with [vOffset] = 1).
         *
         * @param uvRowStride bytes between consecutive chroma rows
         * @param uvPixelStride bytes between consecutive chroma samples in a row (1 planar, 2 semi-planar)
         */
        public fun fromYuv420(
            width: Int,
            height: Int,
            yPlane: ByteArray,
            yRowStride: Int,
            uPlane: ByteArray,
            vPlane: ByteArray,
            uvRowStride: Int,
            uvPixelStride: Int,
            uOffset: Int = 0,
            vOffset: Int = 0,
        ): RgbImage = yuvToRgb(width, height, yPlane, yRowStride, uPlane, uOffset, vPlane, vOffset, uvRowStride, uvPixelStride)

        private fun fromFourChannel(w: Int, h: Int, src: ByteArray, stride: Int, ri: Int, gi: Int, bi: Int): RgbImage {
            require(stride >= w * 4 && src.size >= stride * (h - 1) + w * 4) { "Pixel buffer too small" }
            val out = ByteArray(w * h * 3)
            for (y in 0 until h) {
                var s = y * stride
                var d = y * w * 3
                for (x in 0 until w) {
                    out[d] = src[s + ri]
                    out[d + 1] = src[s + gi]
                    out[d + 2] = src[s + bi]
                    s += 4
                    d += 3
                }
            }
            return RgbImage(w, h, out)
        }

        private fun yuvToRgb(
            w: Int, h: Int,
            yP: ByteArray, yStride: Int,
            uP: ByteArray, uOff: Int,
            vP: ByteArray, vOff: Int,
            uvStride: Int, uvPixel: Int,
        ): RgbImage {
            val out = ByteArray(w * h * 3)
            for (y in 0 until h) {
                val cRow = (y shr 1) * uvStride
                for (x in 0 until w) {
                    val yy = yP[y * yStride + x].toInt() and 0xFF
                    val ci = cRow + (x shr 1) * uvPixel
                    val u = (uP[uOff + ci].toInt() and 0xFF) - 128
                    val v = (vP[vOff + ci].toInt() and 0xFF) - 128
                    val o = (y * w + x) * 3
                    out[o] = clamp8(yy + 1.402 * v)
                    out[o + 1] = clamp8(yy - 0.344136 * u - 0.714136 * v)
                    out[o + 2] = clamp8(yy + 1.772 * u)
                }
            }
            return RgbImage(w, h, out)
        }

        private fun clamp8(v: Double): Byte = v.roundToInt().coerceIn(0, 255).toByte()
    }
}

/** A packed 8-bit single-channel image. */
public class GrayImage(public val width: Int, public val height: Int, public val data: ByteArray) {
    init {
        require(width > 0 && height > 0) { "Image size must be positive: ${width}x$height" }
        require(data.size == width * height) { "Expected ${width * height} bytes, got ${data.size}" }
    }

    /** Pixel value 0..255 at ([x], [y]). */
    public operator fun get(x: Int, y: Int): Int = data[y * width + x].toInt() and 0xFF
}
