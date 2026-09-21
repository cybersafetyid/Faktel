package io.github.cybersafetyid.faktel.face

import io.github.cybersafetyid.faktel.core.FaktelException
import io.github.cybersafetyid.faktel.core.geometry.Point
import io.github.cybersafetyid.faktel.core.geometry.SimilarityTransform
import io.github.cybersafetyid.faktel.core.image.RgbImage
import io.github.cybersafetyid.faktel.core.image.warpSimilarity
import io.github.cybersafetyid.faktel.core.inference.ChannelOrder
import io.github.cybersafetyid.faktel.core.inference.InferenceEngine
import io.github.cybersafetyid.faktel.core.inference.SessionOptions
import io.github.cybersafetyid.faktel.core.inference.toTensor
import kotlin.math.sqrt

/** An L2-normalised face descriptor. Compare with [FaceMatcher]; never persist raw images just to re-embed. */
public class FaceEmbedding(values: FloatArray) {
    public val values: FloatArray

    init {
        require(values.isNotEmpty()) { "Empty embedding" }
        var norm = 0.0
        for (v in values) norm += v.toDouble() * v
        norm = sqrt(norm)
        require(norm > 1e-9) { "Zero-length embedding" }
        this.values = FloatArray(values.size) { (values[it] / norm).toFloat() }
    }

    /** Cosine similarity in `-1.0..1.0` (dot product, since both sides are unit length). */
    public fun cosineSimilarity(other: FaceEmbedding): Double {
        require(values.size == other.values.size) { "Embedding sizes differ: ${values.size} vs ${other.values.size}" }
        var dot = 0.0
        for (i in values.indices) dot += values[i].toDouble() * other.values[i]
        return dot
    }
}

/** Turns an aligned face into a [FaceEmbedding]. */
public interface FaceEmbedder : AutoCloseable {
    public fun embed(image: RgbImage, face: Face): FaceEmbedding
}

/**
 * ArcFace-family embedder (e.g. MobileFaceNet trained with ArcFace loss). Model I/O contract: `[1,3,112,112]` RGB
 * normalised to `(v - 127.5) / 127.5`, output a single embedding vector (typically 512-d).
 *
 * Faces are aligned with a similarity transform fitted to the five landmarks and the standard 112x112 template.
 *
 * **Licensing:** Faktel bundles no embedding weights. The popular InsightFace `buffalo` weights are restricted to
 * non-commercial research; verify the licence of the exact weights you ship (see `models/MODELS.md`).
 */
public class ArcFaceEmbedder(
    engine: InferenceEngine,
    model: ByteArray,
    options: SessionOptions = SessionOptions(),
) : FaceEmbedder {
    private val session = engine.createSession(model, options)
    private val inputName = session.inputNames.singleOrNull()
        ?: throw FaktelException.ModelLoad("Embedder expects exactly one input, got ${session.inputNames}")

    override fun embed(image: RgbImage, face: Face): FaceEmbedding {
        val aligned = align(image, face)
        val tensor = aligned.toTensor(ChannelOrder.RGB, mean = 127.5f, scale = 1f / 127.5f)
        val out = session.run(mapOf(inputName to tensor)).values.first().data
        return FaceEmbedding(out)
    }

    override fun close(): Unit = session.close()

    internal companion object {
        const val SIZE = 112

        /** Standard ArcFace 112x112 landmark template: right eye, left eye, nose, right mouth, left mouth. */
        val TEMPLATE = listOf(
            Point(38.2946, 51.6963),
            Point(73.5318, 51.5014),
            Point(56.0252, 71.7366),
            Point(41.5493, 92.3655),
            Point(70.7299, 92.2041),
        )

        fun align(image: RgbImage, face: Face): RgbImage {
            val t = SimilarityTransform.estimate(face.landmarks.asList(), TEMPLATE)
            return image.warpSimilarity(t, SIZE, SIZE)
        }
    }
}

/**
 * @property similarity cosine similarity of the two faces
 * @property isMatch `similarity >= threshold`
 */
public data class FaceMatchResult(val similarity: Double, val isMatch: Boolean)

/**
 * Decides whether two embeddings show the same person.
 *
 * @property threshold cosine-similarity cut-off. There is no universal value: it depends on the model and on how
 * different the sources are (a selfie vs. a small, laminated KTP portrait scores lower than two selfies).
 * Calibrate against your own genuine/impostor pairs to reach your target false-accept rate.
 */
public class FaceMatcher(public val threshold: Double = 0.3) {
    public fun compare(a: FaceEmbedding, b: FaceEmbedding): FaceMatchResult {
        val s = a.cosineSimilarity(b)
        return FaceMatchResult(s, s >= threshold)
    }
}
