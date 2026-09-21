package io.github.cybersafetyid.faktel.ktp

import io.github.cybersafetyid.faktel.core.geometry.Rect
import io.github.cybersafetyid.faktel.core.image.RgbImage
import io.github.cybersafetyid.faktel.core.image.warpPerspective
import io.github.cybersafetyid.faktel.core.quality.blurScore
import io.github.cybersafetyid.faktel.core.quality.saturatedRatio
import io.github.cybersafetyid.faktel.face.Face
import io.github.cybersafetyid.faktel.face.FaceDetector
import kotlin.math.abs

public enum class KtpIssue {
    /** No card-shaped object was found. */
    CARD_NOT_FOUND,

    /** The object's shape is not close to a 85.60 x 53.98 mm ID-1 card. */
    BAD_ASPECT_RATIO,

    /** The card covers too few pixels to be read reliably. */
    TOO_SMALL,
    BLURRY,

    /** Too many blown-out pixels (reflection on the laminate). */
    GLARE,

    /** No portrait found on the card (requires a face detector). */
    PORTRAIT_NOT_FOUND,

    /** A face was found but not where a KTP portrait sits (right side) - wrong document or wrong orientation. */
    PORTRAIT_MISPLACED,
}

/**
 * Thresholds for [KtpScanner]. Defaults are starting points to calibrate on your own devices and backgrounds.
 *
 * @property aspectTolerance allowed relative deviation of the card's long/short side ratio from [KtpSpec.ASPECT_RATIO];
 * generous by default because perspective foreshortens edges before rectification
 * @property minCardWidthPx minimum length of the card's long side in the source image
 * @property minBlurScore minimum Laplacian variance of the rectified card (normalised to 512 px)
 * @property maxGlareRatio maximum fraction of saturated pixels on the rectified card
 * @property portraitRegion where the portrait's centre must fall, as fractions of the rectified card
 */
public data class KtpScanConfig(
    val aspectTolerance: Double = 0.15,
    val minCardWidthPx: Int = 500,
    val minBlurScore: Double = 40.0,
    val maxGlareRatio: Double = 0.03,
    val portraitRegion: Rect = Rect(0.6, 0.2, 1.0, 0.95),
)

/**
 * @property detection where the card was found in the source image, or null
 * @property card the perspective-corrected, upright card ([KtpSpec.RECTIFIED_WIDTH] x [KtpSpec.RECTIFIED_HEIGHT])
 * @property portrait the portrait face **in [card] coordinates**, or null (needs a face detector)
 * @property blurScore Laplacian variance of [card]
 * @property glareRatio fraction of saturated pixels on [card]
 */
public data class KtpScanResult(
    val detection: KtpDetection?,
    val card: RgbImage?,
    val portrait: Face?,
    val blurScore: Double?,
    val glareRatio: Double?,
    val issues: List<KtpIssue>,
) {
    public val isAcceptable: Boolean get() = issues.isEmpty()
}

/**
 * KTP capture pipeline: locate card -> rectify -> validate (shape, size, focus, glare, portrait placement).
 *
 * Supplying a [faceDetector] enables portrait validation, 180-degree orientation recovery, and gives you the
 * portrait for selfie-to-KTP matching (`embedder.embed(result.card, result.portrait)`).
 *
 * **This validates that the object *looks like* a KTP; it does not read text, verify authenticity, or detect
 * forgeries.** KTP images are personal data - keep processing on-device and do not persist raw images.
 */
public class KtpScanner(
    private val detector: KtpDetector = ClassicalKtpDetector(),
    private val faceDetector: FaceDetector? = null,
    private val config: KtpScanConfig = KtpScanConfig(),
) {
    public fun scan(image: RgbImage): KtpScanResult {
        val detection = detector.detect(image)
            ?: return KtpScanResult(null, null, null, null, null, listOf(KtpIssue.CARD_NOT_FOUND))

        val quad = landscapeQuad(detection.quad)
        val issues = mutableListOf<KtpIssue>()

        val ratio = quad.aspectRatio
        if (abs(ratio / KtpSpec.ASPECT_RATIO - 1.0) > config.aspectTolerance) issues += KtpIssue.BAD_ASPECT_RATIO
        if (maxOf(quad.topEdge, quad.bottomEdge) < config.minCardWidthPx) issues += KtpIssue.TOO_SMALL

        var card = image.warpPerspective(quad, KtpSpec.RECTIFIED_WIDTH, KtpSpec.RECTIFIED_HEIGHT)
        var portrait: Face? = null
        val fd = faceDetector
        if (fd != null) {
            var search = findPortrait(fd, card)
            if (search.placed == null) {
                // A landscape quad can still be upside down; try the 180-degree flip before giving up.
                val flippedCard = image.warpPerspective(quad.rotated(2), KtpSpec.RECTIFIED_WIDTH, KtpSpec.RECTIFIED_HEIGHT)
                val flipped = findPortrait(fd, flippedCard)
                if (flipped.placed != null || (search.any == null && flipped.any != null)) {
                    card = flippedCard
                    search = flipped
                }
            }
            portrait = search.placed ?: search.any
            when {
                search.placed != null -> Unit
                search.any != null -> issues += KtpIssue.PORTRAIT_MISPLACED
                else -> issues += KtpIssue.PORTRAIT_NOT_FOUND
            }
        }

        val blur = card.blurScore(normalizedSize = 512)
        val glare = card.saturatedRatio()
        if (blur < config.minBlurScore) issues += KtpIssue.BLURRY
        if (glare > config.maxGlareRatio) issues += KtpIssue.GLARE

        return KtpScanResult(detection, card, portrait, blur, glare, issues.distinct())
    }

    private class PortraitSearch(val placed: Face?, val any: Face?)

    private fun findPortrait(fd: FaceDetector, card: RgbImage): PortraitSearch {
        val faces = fd.detect(card)
        val r = config.portraitRegion
        val placed = faces.firstOrNull {
            val cx = it.box.centerX / card.width
            val cy = it.box.centerY / card.height
            cx in r.left..r.right && cy in r.top..r.bottom
        }
        return PortraitSearch(placed, faces.firstOrNull())
    }
}
