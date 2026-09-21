package io.github.cybersafetyid.faktel.face

import io.github.cybersafetyid.faktel.core.geometry.Point
import io.github.cybersafetyid.faktel.core.geometry.Rect
import io.github.cybersafetyid.faktel.core.image.RgbImage
import io.github.cybersafetyid.faktel.core.inference.InferenceEngine
import io.github.cybersafetyid.faktel.core.inference.InferenceSession
import io.github.cybersafetyid.faktel.core.inference.SessionOptions
import io.github.cybersafetyid.faktel.core.inference.Tensor

/** An engine whose sessions call [handler]; records the tensors it was fed. */
class FakeEngine(
    private val inputs: List<String>,
    private val outputs: List<String>,
    val handler: (Map<String, Tensor>) -> Map<String, Tensor>,
) : InferenceEngine {
    val fed = ArrayList<Map<String, Tensor>>()
    var closed = false

    override fun createSession(model: ByteArray, options: SessionOptions): InferenceSession = object : InferenceSession {
        override val inputNames = inputs
        override val outputNames = outputs
        override fun run(inputs: Map<String, Tensor>): Map<String, Tensor> {
            fed += inputs
            return handler(inputs)
        }
        override fun close() { closed = true }
    }
}

fun solid(w: Int, h: Int, v: Int = 128) = RgbImage(w, h, ByteArray(w * h * 3) { v.toByte() })

fun landmarksAround(cx: Double, cy: Double, s: Double = 20.0) = FaceLandmarks(
    rightEye = Point(cx - s, cy - s * 0.6), leftEye = Point(cx + s, cy - s * 0.6),
    nose = Point(cx, cy + s * 0.2), rightMouth = Point(cx - s * 0.7, cy + s), leftMouth = Point(cx + s * 0.7, cy + s),
)

fun fakeFace(cx: Double, cy: Double, half: Double = 40.0, score: Double = 0.9) =
    Face(Rect(cx - half, cy - half, cx + half, cy + half), landmarksAround(cx, cy), score)
