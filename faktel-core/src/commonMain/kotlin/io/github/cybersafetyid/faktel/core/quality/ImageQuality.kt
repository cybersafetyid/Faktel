package io.github.cybersafetyid.faktel.core.quality

import io.github.cybersafetyid.faktel.core.image.GrayImage
import io.github.cybersafetyid.faktel.core.image.RgbImage

/**
 * Focus measure: variance of the 4-neighbour Laplacian. Higher is sharper.
 *
 * The value depends on resolution and content, so compare crops of a *normalised* size (Faktel resizes before
 * scoring) and calibrate the threshold on your own devices.
 */
public fun GrayImage.laplacianVariance(): Double {
    if (width < 3 || height < 3) return 0.0
    var sum = 0.0
    var sumSq = 0.0
    var n = 0
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val l = 4 * this[x, y] - this[x - 1, y] - this[x + 1, y] - this[x, y - 1] - this[x, y + 1]
            sum += l
            sumSq += l.toDouble() * l
            n++
        }
    }
    val mean = sum / n
    return sumSq / n - mean * mean
}

/** Mean luma in `0.0..255.0`. */
public fun GrayImage.meanBrightness(): Double {
    var s = 0L
    for (b in data) s += b.toInt() and 0xFF
    return s.toDouble() / data.size
}

/**
 * Fraction (`0.0..1.0`) of pixels where *all* channels are at least [threshold] - i.e. blown-out highlights such as
 * glare on a laminated card.
 */
public fun RgbImage.saturatedRatio(threshold: Int = 250): Double {
    var count = 0
    var i = 0
    while (i < data.size) {
        if ((data[i].toInt() and 0xFF) >= threshold &&
            (data[i + 1].toInt() and 0xFF) >= threshold &&
            (data[i + 2].toInt() and 0xFF) >= threshold
        ) {
            count++
        }
        i += 3
    }
    return count.toDouble() / (data.size / 3)
}

/**
 * Focus score on a normalised copy whose longest side is [normalizedSize], so results are comparable
 * between a 4K photo and a 720p camera frame.
 */
public fun RgbImage.blurScore(normalizedSize: Int = 256): Double {
    val s = normalizedSize.toDouble() / maxOf(width, height)
    val n = if (s < 1.0) resize(maxOf(1, (width * s).toInt()), maxOf(1, (height * s).toInt())) else this
    return n.toGray().laplacianVariance()
}
