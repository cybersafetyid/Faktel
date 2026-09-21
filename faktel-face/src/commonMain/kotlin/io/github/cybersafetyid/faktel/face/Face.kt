package io.github.cybersafetyid.faktel.face

import io.github.cybersafetyid.faktel.core.geometry.Point
import io.github.cybersafetyid.faktel.core.geometry.Rect
import io.github.cybersafetyid.faktel.core.image.RgbImage

/**
 * The five facial landmarks produced by the detector.
 *
 * "Right"/"left" are anatomical (the *subject's* right eye appears on the **left** side of a non-mirrored image).
 * [asList] returns them in the canonical order used by alignment templates:
 * right eye, left eye, nose, right mouth corner, left mouth corner.
 */
public data class FaceLandmarks(
    val rightEye: Point,
    val leftEye: Point,
    val nose: Point,
    val rightMouth: Point,
    val leftMouth: Point,
) {
    public fun asList(): List<Point> = listOf(rightEye, leftEye, nose, rightMouth, leftMouth)
}

/** A detected face. Coordinates are in the pixel space of the image passed to the detector. */
public data class Face(val box: Rect, val landmarks: FaceLandmarks, val score: Double)

/** Finds faces in an image. Implementations must return results sorted by descending [Face.score]. */
public interface FaceDetector : AutoCloseable {
    public fun detect(image: RgbImage): List<Face>
}
