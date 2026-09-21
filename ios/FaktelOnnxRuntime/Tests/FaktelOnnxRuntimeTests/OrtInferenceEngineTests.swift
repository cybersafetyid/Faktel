import Faktel
import Foundation
import XCTest

@testable import FaktelOnnxRuntime

/// Runs the real YuNet model through ONNX Runtime on iOS via the Kotlin pipeline.
///
/// Set `TEST_RUNNER_FAKTEL_TEST_IMAGE=/path/to/face.jpg` when invoking `xcodebuild test` to also assert that a
/// face is detected (photos are deliberately not committed).
final class OrtInferenceEngineTests: XCTestCase {
    private var modelData: Data {
        let repoRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        return try! Data(contentsOf: repoRoot.appendingPathComponent("models/face_detection_yunet_2023mar.onnx"))
    }

    func testEngineLoadsYuNetAndRunsPipeline() throws {
        let engine = OrtInferenceEngine()
        let detector = YuNetFaceDetector(
            engine: engine,
            model: IosBridgingKt.toByteArray(modelData),
            config: YuNetFaceDetector.Config(scoreThreshold: 0.6, nmsThreshold: 0.3, maxFaces: 10),
            options: SessionOptions(numThreads: 2)
        )
        defer { detector.close() }

        // A flat gray image contains no face: proves session creation + inference + decoding all work.
        let blank = RgbImage(width: 320, height: 240, data: KotlinByteArray(size: 320 * 240 * 3))
        XCTAssertEqual(try detector.detect(image: blank).count, 0)

        guard let path = ProcessInfo.processInfo.environment["FAKTEL_TEST_IMAGE"] else { return }
        let photo = try ImageDecoder.shared.decode(bytes: IosBridgingKt.toByteArray(try Data(contentsOf: URL(fileURLWithPath: path))))
        let faces = try detector.detect(image: photo)
        XCTAssertGreaterThanOrEqual(faces.count, 1, "expected a face in \(path)")
        print("FAKTEL-IOS faces=\(faces.count) box=\(faces[0].box) score=\(faces[0].score)")
    }
}
