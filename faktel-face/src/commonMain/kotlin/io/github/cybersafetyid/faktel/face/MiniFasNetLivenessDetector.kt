package io.github.cybersafetyid.faktel.face

import io.github.cybersafetyid.faktel.core.FaktelException
import io.github.cybersafetyid.faktel.core.geometry.Rect
import io.github.cybersafetyid.faktel.core.image.RgbImage
import io.github.cybersafetyid.faktel.core.inference.ChannelOrder
import io.github.cybersafetyid.faktel.core.inference.InferenceEngine
import io.github.cybersafetyid.faktel.core.inference.SessionOptions
import io.github.cybersafetyid.faktel.core.inference.toTensor
import kotlin.math.exp
import kotlin.math.min

/**
 * @property realScore probability (0..1) that the face belongs to a live person
 * @property isLive `realScore >= threshold`
 */
public data class LivenessResult(val realScore: Double, val isLive: Boolean)

/** Decides whether a detected face is a live person or a presentation attack (printed photo, screen replay). */
public interface LivenessDetector : AutoCloseable {
    public fun assess(image: RgbImage, face: Face): LivenessResult
}

/**
 * Passive single-frame liveness using [MiniFASNet](https://github.com/minivision-ai/Silent-Face-Anti-Spoofing)
 * (Apache-2.0). Model I/O contract: `[1,3,80,80]` BGR 0..255 in, 3 logits out where class 1 is "real".
 *
 * **Security note:** this stops casual print/replay attacks only. It is not certified anti-spoofing (e.g. iBeta PAD)
 * and must not be the sole control for high-assurance flows.
 *
 * @property cropScale how much larger than the detector box the crop is (2.7 for MiniFASNetV2, 4.0 for V1SE)
 */
public class MiniFasNetLivenessDetector(
    engine: InferenceEngine,
    model: ByteArray,
    private val cropScale: Double = 2.7,
    private val threshold: Double = 0.5,
    options: SessionOptions = SessionOptions(),
) : LivenessDetector {
    private val session = engine.createSession(model, options)
    private val inputName = session.inputNames.singleOrNull()
        ?: throw FaktelException.ModelLoad("MiniFASNet expects exactly one input, got ${session.inputNames}")

    override fun assess(image: RgbImage, face: Face): LivenessResult {
        val crop = cropForLiveness(image, face.box, cropScale).resize(INPUT_SIZE, INPUT_SIZE)
        val out = session.run(mapOf(inputName to crop.toTensor(ChannelOrder.BGR))).values.first().data
        if (out.size != 3) throw FaktelException.Inference("Expected 3 logits from MiniFASNet, got ${out.size}")
        val real = softmax(out)[REAL_CLASS]
        return LivenessResult(real, real >= threshold)
    }

    override fun close(): Unit = session.close()

    internal companion object {
        const val INPUT_SIZE = 80
        const val REAL_CLASS = 1

        /** Mirrors the reference implementation's crop so the model sees the context it was trained with. */
        fun cropForLiveness(image: RgbImage, box: Rect, scale: Double): RgbImage {
            val bx = box.left.toInt()
            val by = box.top.toInt()
            val bw = (box.right.toInt() - bx).coerceAtLeast(1)
            val bh = (box.bottom.toInt() - by).coerceAtLeast(1)
            val s = min(min((image.height - 1).toDouble() / bh, (image.width - 1).toDouble() / bw), scale)
            val nw = bw * s
            val nh = bh * s
            val cx = bx + bw / 2.0
            val cy = by + bh / 2.0
            val x1 = (cx - nw / 2).toInt().coerceAtLeast(0)
            val y1 = (cy - nh / 2).toInt().coerceAtLeast(0)
            val x2 = (cx + nw / 2).toInt().coerceAtMost(image.width - 1)
            val y2 = (cy + nh / 2).toInt().coerceAtMost(image.height - 1)
            return image.crop(Rect(x1.toDouble(), y1.toDouble(), (x2 + 1).toDouble(), (y2 + 1).toDouble()))
        }

        fun softmax(logits: FloatArray): DoubleArray {
            val max = logits.max().toDouble()
            val e = DoubleArray(logits.size) { exp(logits[it] - max) }
            val sum = e.sum()
            return DoubleArray(e.size) { e[it] / sum }
        }
    }
}
