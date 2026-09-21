package io.github.cybersafetyid.faktel.face

import io.github.cybersafetyid.faktel.core.FaktelException
import io.github.cybersafetyid.faktel.core.geometry.Point
import io.github.cybersafetyid.faktel.core.geometry.Rect
import io.github.cybersafetyid.faktel.core.image.RgbImage
import io.github.cybersafetyid.faktel.core.inference.ChannelOrder
import io.github.cybersafetyid.faktel.core.inference.InferenceEngine
import io.github.cybersafetyid.faktel.core.inference.InferenceSession
import io.github.cybersafetyid.faktel.core.inference.SessionOptions
import io.github.cybersafetyid.faktel.core.inference.Tensor
import io.github.cybersafetyid.faktel.core.inference.letterbox
import io.github.cybersafetyid.faktel.core.inference.toTensor
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Face detector backed by [YuNet](https://github.com/opencv/opencv_zoo/tree/main/models/face_detection_yunet)
 * (`face_detection_yunet_2023mar.onnx`, MIT). Model I/O contract: `input` float32 `[1,3,640,640]` BGR 0..255;
 * outputs `cls|obj|bbox|kps` for strides 8, 16 and 32.
 */
public class YuNetFaceDetector(
    engine: InferenceEngine,
    model: ByteArray,
    private val config: Config = Config(),
    options: SessionOptions = SessionOptions(),
) : FaceDetector {

    /**
     * @property scoreThreshold minimum `sqrt(cls * obj)` confidence to keep a candidate
     * @property nmsThreshold IoU above which overlapping candidates are merged
     * @property maxFaces upper bound on returned faces
     */
    public data class Config(
        val scoreThreshold: Double = 0.6,
        val nmsThreshold: Double = 0.3,
        val maxFaces: Int = 10,
    )

    private val session: InferenceSession = engine.createSession(model, options)
    private val inputName: String = session.inputNames.singleOrNull()
        ?: throw FaktelException.ModelLoad("YuNet expects exactly one input, got ${session.inputNames}")

    init {
        val missing = REQUIRED_OUTPUTS.filterNot { it in session.outputNames }
        if (missing.isNotEmpty()) {
            session.close()
            throw FaktelException.ModelLoad("Not a YuNet model: missing outputs $missing")
        }
    }

    override fun detect(image: RgbImage): List<Face> {
        val lb = image.letterbox(INPUT_SIZE)
        val outputs = session.run(mapOf(inputName to lb.image.toTensor(ChannelOrder.BGR)))
        return decodeYuNet(outputs, config).map { c ->
            val box = Rect(
                lb.toSourceX(c.left), lb.toSourceY(c.top), lb.toSourceX(c.right), lb.toSourceY(c.bottom),
            ).clampTo(image.width, image.height)
            fun p(i: Int) = Point(lb.toSourceX(c.landmarks[2 * i]), lb.toSourceY(c.landmarks[2 * i + 1]))
            Face(box, FaceLandmarks(p(0), p(1), p(2), p(3), p(4)), c.score)
        }.filter { it.box.width > 1.0 && it.box.height > 1.0 }
    }

    override fun close(): Unit = session.close()

    internal companion object {
        const val INPUT_SIZE = 640
        private val STRIDES = intArrayOf(8, 16, 32)
        private val REQUIRED_OUTPUTS = STRIDES.flatMap { s -> listOf("cls_$s", "obj_$s", "bbox_$s", "kps_$s") }

        /** A decoded candidate in letterboxed (model input) coordinates. */
        class Candidate(
            val left: Double, val top: Double, val right: Double, val bottom: Double,
            val landmarks: DoubleArray, val score: Double,
        ) {
            fun box() = Rect(left, top, right, bottom)
        }

        fun decodeYuNet(outputs: Map<String, Tensor>, config: Config): List<Candidate> {
            val found = ArrayList<Candidate>()
            for (stride in STRIDES) {
                val cls = outputs.getValue("cls_$stride").data
                val obj = outputs.getValue("obj_$stride").data
                val bbox = outputs.getValue("bbox_$stride").data
                val kps = outputs.getValue("kps_$stride").data
                val cells = INPUT_SIZE / stride
                if (cls.size != cells * cells) {
                    throw FaktelException.Inference("Unexpected YuNet output size for stride $stride: ${cls.size}")
                }
                for (i in 0 until cells * cells) {
                    val score = sqrt(cls[i].coerceIn(0f, 1f).toDouble() * obj[i].coerceIn(0f, 1f).toDouble())
                    if (score < config.scoreThreshold) continue
                    val row = i / cells
                    val col = i % cells
                    val cx = (col + bbox[4 * i]) * stride
                    val cy = (row + bbox[4 * i + 1]) * stride
                    val w = exp(bbox[4 * i + 2].toDouble()) * stride
                    val h = exp(bbox[4 * i + 3].toDouble()) * stride
                    val lm = DoubleArray(10) { k ->
                        val base = if (k % 2 == 0) col else row
                        (kps[10 * i + k] + base).toDouble() * stride
                    }
                    found += Candidate(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, lm, score)
                }
            }
            return nonMaxSuppression(found, config.nmsThreshold).take(config.maxFaces)
        }

        private fun nonMaxSuppression(candidates: List<Candidate>, iouThreshold: Double): List<Candidate> {
            val kept = ArrayList<Candidate>()
            for (c in candidates.sortedByDescending { it.score }) {
                if (kept.none { it.box().iou(c.box()) > iouThreshold }) kept += c
            }
            return kept
        }
    }
}
