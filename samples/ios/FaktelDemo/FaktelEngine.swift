import Faktel
import FaktelOnnxRuntime
import UIKit

/// The whole Faktel integration: one ONNX engine, two models, two pipelines.
/// Not thread-safe: always call from the same (background) queue.
final class FaktelEngine {
    private let selfie: FaceAnalyzer
    private let ktp: KtpScanner

    init() throws {
        let engine = OrtInferenceEngine()
        let yunet = Self.model("face_detection_yunet_2023mar")
        let fas = Self.model("minifasnet_v2")
        let opts = SessionOptions(numThreads: 2)
        let cfg = YuNetFaceDetector.Config(scoreThreshold: 0.6, nmsThreshold: 0.3, maxFaces: 10)

        selfie = FaceAnalyzer(
            detector: try YuNetFaceDetector(engine: engine, model: yunet, config: cfg, options: opts),
            quality: FaceQualityAssessor(config: FaceQualityConfig(
                minFaceWidthRatio: 0.2, minBlurScore: 30, minBrightness: 60, maxBrightness: 200,
                maxYaw: 25, maxPitch: 25, maxRoll: 25)),
            liveness: try MiniFasNetLivenessDetector(engine: engine, model: fas, cropScale: 2.7, threshold: 0.5, options: opts)
        )
        ktp = KtpScanner(
            detector: ClassicalKtpDetector(config: ClassicalKtpDetector.Config(
                analysisSize: 320, minAreaRatio: 0.08, maxAreaRatio: 0.97, minContrast: 30, minRectangularity: 0.85)),
            faceDetector: try YuNetFaceDetector(engine: engine, model: yunet, config: cfg, options: opts),
            config: KtpScanConfig(
                aspectTolerance: 0.15, minCardWidthPx: 500, minBlurScore: 40, maxGlareRatio: 0.03,
                portraitRegion: Rect(left: 0.6, top: 0.2, right: 1.0, bottom: 0.95))
        )
    }

    private static func model(_ name: String) -> KotlinByteArray {
        let url = Bundle.main.url(forResource: name, withExtension: "onnx")!
        return IosBridgingKt.toByteArray(try! Data(contentsOf: url))
    }

    func analyzeSelfie(_ image: RgbImage) throws -> (FaceAnalysis, Int) {
        let t = Date()
        let r = try selfie.analyze(image: image)
        return (r, Int(Date().timeIntervalSince(t) * 1000))
    }

    func scanKtp(_ image: RgbImage) throws -> (KtpScanResult, Int) {
        let t = Date()
        let r = try ktp.scan(image: image)
        return (r, Int(Date().timeIntervalSince(t) * 1000))
    }
}

// MARK: - UIImage <-> RgbImage

extension UIImage {
    /// Draws the image upright (EXIF applied) into RGBA and hands it to Faktel. Large photos are downscaled.
    func toRgbImage(maxSide: CGFloat = 1800) -> RgbImage {
        let k = min(1, maxSide / max(size.width, size.height))
        let w = Int((size.width * k).rounded()), h = Int((size.height * k).rounded())
        var px = [UInt8](repeating: 0, count: w * h * 4)
        let ctx = CGContext(
            data: &px, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        ctx.translateBy(x: 0, y: CGFloat(h)); ctx.scaleBy(x: 1, y: -1)   // CGContext is bottom-up, UIKit is top-down
        UIGraphicsPushContext(ctx)
        draw(in: CGRect(x: 0, y: 0, width: w, height: h))   // UIKit applies orientation here
        UIGraphicsPopContext()
        let bytes = IosBridgingKt.toByteArray(Data(px))
        return RgbImage.Companion.shared.fromRgba(width: Int32(w), height: Int32(h), rgba: bytes, rowStride: Int32(w * 4))
    }
}

extension RgbImage {
    func toUIImage() -> UIImage? {
        let w = Int(width), h = Int(height)
        // Element-wise copy: fine for a one-off 1011x638 card; use a bridging helper if you do this per frame.
        var rgba = [UInt8](repeating: 255, count: w * h * 4)
        for i in 0..<(w * h) {
            rgba[i * 4] = UInt8(bitPattern: data.get(index: Int32(i * 3)))
            rgba[i * 4 + 1] = UInt8(bitPattern: data.get(index: Int32(i * 3 + 1)))
            rgba[i * 4 + 2] = UInt8(bitPattern: data.get(index: Int32(i * 3 + 2)))
        }
        guard let ctx = CGContext(
            data: &rgba, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue),
            let cg = ctx.makeImage() else { return nil }
        return UIImage(cgImage: cg)
    }
}
