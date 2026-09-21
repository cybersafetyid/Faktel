import Faktel
import Foundation
import OnnxRuntimeBindings

/// ONNX Runtime backend for Faktel on iOS.
///
/// Kotlin/Native cannot link the ONNX Runtime static libraries reliably, so the runtime lives on the Swift side
/// and is handed to Faktel through its `InferenceEngine` seam:
///
///     let engine = try OrtInferenceEngine()
///     let detector = YuNetFaceDetector(engine: engine, model: modelBytes, config: .init(), options: .init(numThreads: 2))
public final class OrtInferenceEngine: NSObject, InferenceEngine {
    private let env: ORTEnv

    public override init() {
        // ORTEnv only fails on an unusable runtime install; surface that as a programmer error.
        self.env = try! ORTEnv(loggingLevel: .warning)
        super.init()
    }

    public func createSession(model: KotlinByteArray, options: SessionOptions) throws -> InferenceSession {
        // The Objective-C API loads from a path only, so stage the bytes in a temporary file.
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("faktel-\(UUID().uuidString).onnx")
        try (model.toNSData() as Data).write(to: url, options: .atomic)
        defer { try? FileManager.default.removeItem(at: url) }

        let sessionOptions = try ORTSessionOptions()
        try sessionOptions.setIntraOpNumThreads(options.numThreads)
        try sessionOptions.setGraphOptimizationLevel(.all)
        let session = try ORTSession(env: env, modelPath: url.path, sessionOptions: sessionOptions)
        return try OrtSessionAdapter(session: session)
    }
}

private final class OrtSessionAdapter: NSObject, InferenceSession {
    private let session: ORTSession
    let inputNames: [String]
    let outputNames: [String]

    init(session: ORTSession) throws {
        self.session = session
        self.inputNames = try session.inputNames()
        self.outputNames = try session.outputNames()
        super.init()
    }

    func run(inputs: [String: Tensor]) throws -> [String: Tensor] {
        var feed: [String: ORTValue] = [:]
        for (name, tensor) in inputs {
            let data = NSMutableData(data: tensor.toNSData() as Data)
            let shape = (0..<Int(tensor.shape.size)).map { NSNumber(value: tensor.shape.get(index: Int32($0))) }
            feed[name] = try ORTValue(tensorData: data, elementType: .float, shape: shape)
        }
        let outputs = try session.run(withInputs: feed, outputNames: Set(outputNames), runOptions: nil)
        var result: [String: Tensor] = [:]
        for (name, value) in outputs {
            let shape = try value.tensorTypeAndShapeInfo().shape.map { KotlinInt(value: $0.int32Value) }
            result[name] = IosBridgingKt.tensorFromNSData(shape: shape, data: try value.tensorData() as Data)
        }
        return result
    }

    func close() {
        // ORTSession releases its native resources on deinit.
    }
}
