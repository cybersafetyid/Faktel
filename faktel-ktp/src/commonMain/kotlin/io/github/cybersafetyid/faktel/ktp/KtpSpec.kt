package io.github.cybersafetyid.faktel.ktp

import io.github.cybersafetyid.faktel.core.geometry.Point
import io.github.cybersafetyid.faktel.core.geometry.Quad

/**
 * Physical facts about the Indonesian e-KTP, which follows ISO/IEC 7810 ID-1 (85.60 x 53.98 mm, Permendagri
 * No. 38/2009) - the same size as a bank card.
 */
public object KtpSpec {
    public const val WIDTH_MM: Double = 85.60
    public const val HEIGHT_MM: Double = 53.98

    /** Landscape width / height, about 1.586. */
    public const val ASPECT_RATIO: Double = WIDTH_MM / HEIGHT_MM

    /** Output size of a rectified card: 300 DPI equivalent (1011 x 638 px). */
    public const val RECTIFIED_WIDTH: Int = 1011
    public const val RECTIFIED_HEIGHT: Int = 638
}

/** A card found in a photo/frame. [confidence] is 0..1 and only comparable within one [KtpDetector]. */
public data class KtpDetection(val quad: Quad, val confidence: Double)

/** Locates a card-shaped quadrilateral. Implementations must not decide *whether it is a KTP* - see [KtpScanner]. */
public interface KtpDetector {
    /** @return the most card-like quadrilateral in [image], or null if there is none. */
    public fun detect(image: io.github.cybersafetyid.faktel.core.image.RgbImage): KtpDetection?
}

internal fun landscapeQuad(quad: Quad): Quad = if (quad.aspectRatio < 1.0) quad.rotated(1) else quad

internal val RECTIFIED_CORNERS: List<Point> = listOf(
    Point(0.0, 0.0),
    Point(KtpSpec.RECTIFIED_WIDTH.toDouble(), 0.0),
    Point(KtpSpec.RECTIFIED_WIDTH.toDouble(), KtpSpec.RECTIFIED_HEIGHT.toDouble()),
    Point(0.0, KtpSpec.RECTIFIED_HEIGHT.toDouble()),
)
