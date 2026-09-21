package io.github.cybersafetyid.faktel.core.inference

/**
 * A dense float32 tensor in row-major order.
 * Shapes follow the model's native layout (Faktel models here use NCHW).
 */
public class Tensor(public val shape: IntArray, public val data: FloatArray) {
    init {
        require(shape.fold(1L) { acc, d -> acc * d } == data.size.toLong()) {
            "Shape ${shape.toList()} does not match data size ${data.size}"
        }
    }

    override fun toString(): String = "Tensor(shape=${shape.toList()})"
}

/** A loaded model, ready to run. Instances are **not** required to be thread-safe; use one per thread. */
public interface InferenceSession : AutoCloseable {
    public val inputNames: List<String>
    public val outputNames: List<String>

    /**
     * Runs the model.
     * @param inputs tensors keyed by model input name
     * @return every model output keyed by output name
     * @throws io.github.cybersafetyid.faktel.core.FaktelException.Inference on backend failure
     */
    @Throws(Exception::class)
    public fun run(inputs: Map<String, Tensor>): Map<String, Tensor>
}

/** Tuning knobs shared by all backends; a backend may ignore what it cannot honour. */
public data class SessionOptions(val numThreads: Int = 2) {
    init {
        require(numThreads > 0) { "numThreads must be > 0" }
    }
}

/**
 * The single seam between Faktel's model-agnostic pipelines and a native runtime (ONNX Runtime, LiteRT, NCNN...).
 * Implement this to plug in a different backend; see the `faktel-ort` module for the reference implementation.
 */
public interface InferenceEngine {
    /**
     * @param model the raw bytes of the model file (`.onnx` for the ORT backend)
     * @throws io.github.cybersafetyid.faktel.core.FaktelException.ModelLoad if the model cannot be loaded
     */
    @Throws(Exception::class)
    public fun createSession(model: ByteArray, options: SessionOptions = SessionOptions()): InferenceSession
}
