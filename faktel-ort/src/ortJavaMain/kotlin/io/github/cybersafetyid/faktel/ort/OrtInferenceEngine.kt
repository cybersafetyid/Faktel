package io.github.cybersafetyid.faktel.ort

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import io.github.cybersafetyid.faktel.core.FaktelException
import io.github.cybersafetyid.faktel.core.inference.InferenceEngine
import io.github.cybersafetyid.faktel.core.inference.InferenceSession
import io.github.cybersafetyid.faktel.core.inference.SessionOptions
import io.github.cybersafetyid.faktel.core.inference.Tensor
import java.nio.FloatBuffer

/**
 * [InferenceEngine] backed by ONNX Runtime's Java API. Works on Android (`onnxruntime-android`, CPU) and desktop JVM.
 *
 * iOS has no Kotlin implementation of this class - see `ios/FaktelOnnxRuntime` for the Swift counterpart.
 */
public class OrtInferenceEngine : InferenceEngine {
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    override fun createSession(model: ByteArray, options: SessionOptions): InferenceSession = try {
        val so = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(options.numThreads)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        OrtInferenceSession(env, env.createSession(model, so), so)
    } catch (e: OrtException) {
        throw FaktelException.ModelLoad("ONNX Runtime could not load the model: ${e.message}", e)
    }
}

private class OrtInferenceSession(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val options: OrtSession.SessionOptions,
) : InferenceSession {
    override val inputNames: List<String> = session.inputNames.toList()
    override val outputNames: List<String> = session.outputNames.toList()

    override fun run(inputs: Map<String, Tensor>): Map<String, Tensor> {
        val created = ArrayList<OnnxTensor>(inputs.size)
        try {
            val feed = HashMap<String, OnnxTensor>(inputs.size)
            for ((name, t) in inputs) {
                val shape = LongArray(t.shape.size) { t.shape[it].toLong() }
                val ot = OnnxTensor.createTensor(env, FloatBuffer.wrap(t.data), shape)
                created += ot
                feed[name] = ot
            }
            session.run(feed).use { result ->
                val out = LinkedHashMap<String, Tensor>()
                for (entry in result) {
                    val ot = entry.value as? OnnxTensor
                        ?: throw FaktelException.Inference("Output '${entry.key}' is not a tensor")
                    val buf = ot.floatBuffer
                    val data = FloatArray(buf.remaining()).also { buf.get(it) }
                    out[entry.key] = Tensor(IntArray(ot.info.shape.size) { ot.info.shape[it].toInt() }, data)
                }
                return out
            }
        } catch (e: OrtException) {
            throw FaktelException.Inference("ONNX Runtime failed: ${e.message}", e)
        } finally {
            created.forEach { it.close() }
        }
    }

    override fun close() {
        session.close()
        options.close()
    }
}
