package io.github.cybersafetyid.faktel.face

import io.github.cybersafetyid.faktel.core.image.RgbImage
import io.github.cybersafetyid.faktel.core.quality.blurScore
import io.github.cybersafetyid.faktel.core.quality.meanBrightness
import kotlin.math.abs

public enum class FaceIssue {
    NO_FACE,
    MULTIPLE_FACES,
    TOO_SMALL,
    BLURRY,
    TOO_DARK,
    TOO_BRIGHT,
    FACE_ANGLE,
    FACE_CUT_OFF,
}

/**
 * Thresholds for [FaceQualityAssessor]. Defaults are conservative starting points; **calibrate them on your target
 * cameras** before relying on them.
 *
 * @property minFaceWidthRatio minimum face width as a fraction of image width
 * @property minBlurScore minimum Laplacian variance of the face crop (normalised to 128 px)
 * @property minBrightness / [maxBrightness] acceptable mean luma range (0..255) of the face crop
 * @property maxYaw / [maxPitch] / [maxRoll] maximum absolute head angle in degrees
 */
public data class FaceQualityConfig(
    val minFaceWidthRatio: Double = 0.2,
    val minBlurScore: Double = 30.0,
    val minBrightness: Double = 60.0,
    val maxBrightness: Double = 200.0,
    val maxYaw: Double = 25.0,
    val maxPitch: Double = 25.0,
    val maxRoll: Double = 25.0,
)

public data class FaceQualityReport(
    val face: Face,
    val blurScore: Double,
    val brightness: Double,
    val pose: HeadPose,
    val issues: List<FaceIssue>,
) {
    public val isAcceptable: Boolean get() = issues.isEmpty()
}

/** Cheap, model-free checks that decide whether a face crop is good enough for liveness or matching. */
public class FaceQualityAssessor(private val config: FaceQualityConfig = FaceQualityConfig()) {
    public fun assess(image: RgbImage, face: Face): FaceQualityReport {
        val crop = image.crop(face.box)
        val blur = crop.blurScore(normalizedSize = 128)
        val brightness = crop.toGray().meanBrightness()
        val pose = face.headPose()
        val issues = buildList {
            if (face.box.width / image.width < config.minFaceWidthRatio) add(FaceIssue.TOO_SMALL)
            if (blur < config.minBlurScore) add(FaceIssue.BLURRY)
            if (brightness < config.minBrightness) add(FaceIssue.TOO_DARK)
            if (brightness > config.maxBrightness) add(FaceIssue.TOO_BRIGHT)
            if (abs(pose.yaw) > config.maxYaw || abs(pose.pitch) > config.maxPitch || abs(pose.roll) > config.maxRoll) {
                add(FaceIssue.FACE_ANGLE)
            }
            if (face.landmarks.asList().any { it.x < 0 || it.y < 0 || it.x > image.width || it.y > image.height }) {
                add(FaceIssue.FACE_CUT_OFF)
            }
        }
        return FaceQualityReport(face, blur, brightness, pose, issues)
    }
}
