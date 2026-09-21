package io.github.cybersafetyid.faktel.core.image

import io.github.cybersafetyid.faktel.core.FaktelException
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

public actual object ImageDecoder {
    public actual fun decode(bytes: ByteArray): RgbImage {
        val img = try {
            ImageIO.read(ByteArrayInputStream(bytes))
        } catch (e: java.io.IOException) {
            throw FaktelException.InvalidImage("Unsupported or corrupt image data", e)
        } ?: throw FaktelException.InvalidImage("Unsupported or corrupt image data")
        val w = img.width
        val h = img.height
        val px = img.getRGB(0, 0, w, h, null, 0, w)
        val out = ByteArray(w * h * 3)
        for (i in px.indices) {
            val p = px[i]
            out[i * 3] = (p shr 16).toByte()
            out[i * 3 + 1] = (p shr 8).toByte()
            out[i * 3 + 2] = p.toByte()
        }
        return RgbImage(w, h, out)
    }
}
