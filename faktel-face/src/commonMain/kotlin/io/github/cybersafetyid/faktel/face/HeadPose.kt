package io.github.cybersafetyid.faktel.face

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2

/**
 * Approximate head orientation in degrees. Positive yaw = nose displaced towards the right of the image, positive pitch = looking
 * up, positive roll = head tilted clockwise on screen.
 *
 * Derived geometrically from five 2D landmarks, so it is a coarse gate for "roughly frontal", not a measurement.
 */
public data class HeadPose(val yaw: Double, val pitch: Double, val roll: Double)

/** Estimates [HeadPose] from the landmarks of this face. */
public fun Face.headPose(): HeadPose {
    val l = landmarks
    val eyeDx = l.leftEye.x - l.rightEye.x
    val eyeDy = l.leftEye.y - l.rightEye.y
    val eyeDist = kotlin.math.hypot(eyeDx, eyeDy).coerceAtLeast(1e-6)
    val eyeMidX = (l.rightEye.x + l.leftEye.x) / 2
    val eyeMidY = (l.rightEye.y + l.leftEye.y) / 2
    val mouthMidY = (l.rightMouth.y + l.leftMouth.y) / 2

    val roll = atan2(eyeDy, eyeDx) * 180 / PI

    // The nose tip sits roughly 0.6 * interocular distance in front of the eye line.
    val yawSin = ((l.nose.x - eyeMidX) / eyeDist / NOSE_DEPTH).coerceIn(-1.0, 1.0)
    val yaw = asin(yawSin) * 180 / PI

    // Frontal faces put the nose about 55% of the way from eye line to mouth line.
    val span = (mouthMidY - eyeMidY).coerceAtLeast(1e-6)
    val pitch = (NEUTRAL_NOSE_RATIO - (l.nose.y - eyeMidY) / span) * PITCH_GAIN

    return HeadPose(yaw, pitch, roll)
}

private const val NOSE_DEPTH = 0.6
private const val NEUTRAL_NOSE_RATIO = 0.55
private const val PITCH_GAIN = 100.0
