package io.github.cybersafetyid.faktel.core.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.cybersafetyid.faktel.core.FaktelException

public actual object ImageDecoder {
    public actual fun decode(bytes: ByteArray): RgbImage {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw FaktelException.InvalidImage("Unsupported or corrupt image data")
        try {
            return bmp.toRgbImage()
        } finally {
            bmp.recycle()
        }
    }
}

/** Copies this bitmap's pixels into an [RgbImage]. The bitmap is not recycled. */
public fun Bitmap.toRgbImage(): RgbImage {
    val px = IntArray(width * height)
    getPixels(px, 0, width, 0, 0, width, height)
    val out = ByteArray(width * height * 3)
    for (i in px.indices) {
        val p = px[i]
        out[i * 3] = (p shr 16).toByte()
        out[i * 3 + 1] = (p shr 8).toByte()
        out[i * 3 + 2] = p.toByte()
    }
    return RgbImage(width, height, out)
}
