# iOS

Kotlin/Native cannot link the ONNX Runtime static libraries reliably, so on iOS the runtime lives in **Swift** and is
handed to Faktel through the `InferenceEngine` interface (see [ADR 0002](../adr/0002-onnx-runtime-on-ios-via-swift.md)).
Everything else - all detection, quality, liveness, KTP logic - is the same shared Kotlin.

There are two integration styles. Pick the one that matches your app.

## A. Native Swift app using the `Faktel` XCFramework

1. Build (or download from the GitHub release) `Faktel.xcframework`:
   ```bash
   ./gradlew :faktel:assembleFaktelXCFramework      # -> faktel/build/XCFrameworks/release/Faktel.xcframework
   ```
2. Add the local Swift package [`ios/FaktelOnnxRuntime`](../../ios/FaktelOnnxRuntime) to your app (it depends on
   the XCFramework and on `onnxruntime-swift-package-manager`). Adjust the `binaryTarget` path or point it at the
   release asset.
3. Add the model files to the app target (Copy Bundle Resources).

```swift
import Faktel
import FaktelOnnxRuntime

let engine = OrtInferenceEngine()
let bytes = IosBridgingKt.toByteArray(try Data(contentsOf: Bundle.main.url(forResource: "face_detection_yunet_2023mar", withExtension: "onnx")!))
let detector = YuNetFaceDetector(
    engine: engine,
    model: bytes,
    config: YuNetFaceDetector.Config(scoreThreshold: 0.6, nmsThreshold: 0.3, maxFaces: 10),
    options: SessionOptions(numThreads: 2)
)

let image = try ImageDecoder.shared.decode(bytes: IosBridgingKt.toByteArray(jpegData))
let faces = try detector.detect(image: image)
```

Camera frames: convert a BGRA `CVPixelBuffer` to bytes (lock the base address, honour `bytesPerRow`), then
`RgbImage.Companion.shared.fromBgra(width:height:bgra:rowStride:)`. For NV12 use `fromYuv420` with the
interleaved UV plane passed as both `uPlane` and `vPlane` and `vOffset = 1`, `uvPixelStride = 2`.

## B. Kotlin Multiplatform app (shared module + iOS app)

Your shared module already produces its own framework (say `Shared`). Two rules:

1. **Export the Faktel types you touch from Swift** so protocol types like `InferenceEngine` live in *your* framework:
   ```kotlin
   iosTarget.binaries.framework {
       baseName = "Shared"
       export("io.github.cybersafetyid.faktel:faktel-core:<version>")
   }
   ```
   Do **not** also link `Faktel.xcframework` - the same Kotlin classes would then exist in two frameworks and
   Swift would treat them as different types.
2. **Copy** [`OrtInferenceEngine.swift`](../../ios/FaktelOnnxRuntime/Sources/FaktelOnnxRuntime/OrtInferenceEngine.swift)
   into your iOS app and change `import Faktel` to `import Shared`. Add the
   [`onnxruntime-swift-package-manager`](https://github.com/microsoft/onnxruntime-swift-package-manager) package
   (product `onnxruntime`) to the app target.

Then hand the engine to shared Kotlin at startup:

```swift
let engine = OrtInferenceEngine()
FaceFeature(engine: engine)          // your shared Kotlin class that takes an InferenceEngine
```

In shared Kotlin, read bundled models with `readBundleModel("face_detection_yunet_2023mar", "onnx")` (from
`faktel-core`'s `iosMain`).

## Notes

- Minimum iOS 15. Simulator (arm64) and device (arm64) slices are built; there is no x86_64 simulator slice.
- `OrtInferenceEngine.init()` traps if ONNX Runtime cannot initialise (an unusable install, not a runtime condition).
- ONNX Runtime's Objective-C API loads from a file path, so the engine stages model bytes in a temp file and deletes
  it right after the session is created.
- Kotlin objects are not thread-confined, but a session is not thread-safe: analyse on a background queue and reuse
  the objects from that queue only.
- Verified: the Swift package's XCTest runs the real YuNet model through ONNX Runtime on an iPhone simulator and
  detects a face with the same result as desktop JVM (`xcodebuild test`, see [testing](../development/testing.md)).
