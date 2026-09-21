package io.github.cybersafetyid.faktel.ort

/**
 * Describes where the ONNX Runtime backend is implemented.
 *
 * - Android and desktop JVM: `OrtInferenceEngine` (Kotlin, in this module).
 * - iOS: the Swift package `ios/FaktelOnnxRuntime`, which implements `InferenceEngine` on the Swift side because
 *   Kotlin/Native cannot reliably link ONNX Runtime's static libraries (see docs/adr/0002).
 *
 * This module still ships an iOS artifact so that `commonMain` dependencies on it resolve on every target.
 */
public object OrtBackend {
    /** Name of the runtime every Faktel ONNX backend wraps. */
    public const val RUNTIME: String = "ONNX Runtime"
}
