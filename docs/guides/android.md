# Android

## Setup

`faktel` brings `faktel-ort`, which depends on `com.microsoft.onnxruntime:onnxruntime-android` (CPU). No manual
native-library setup is needed. `minSdk 24`.

Put the models in `src/androidMain/assets/` (or `app/src/main/assets/`).

```kotlin
val engine = OrtInferenceEngine()                                  // create once (e.g. in your DI graph)
val yunet = context.readModelAsset("face_detection_yunet_2023mar.onnx")
val detector = YuNetFaceDetector(engine, yunet, options = SessionOptions(numThreads = 2))
```

`readModelAsset` is an extension on `Context` from `faktel-core`.

## CameraX frames (YUV_420_888)

```kotlin
imageAnalysis.setAnalyzer(analysisExecutor) { proxy ->
    val y = proxy.planes[0]; val u = proxy.planes[1]; val v = proxy.planes[2]
    fun ByteBuffer.bytes() = ByteArray(remaining()).also { get(it) }
    val frame = RgbImage.fromYuv420(
        width = proxy.width, height = proxy.height,
        yPlane = y.buffer.bytes(), yRowStride = y.rowStride,
        uPlane = u.buffer.bytes(), vPlane = v.buffer.bytes(),
        uvRowStride = u.rowStride, uvPixelStride = u.pixelStride,
    ).rotate(proxy.imageInfo.rotationDegrees)          // sensor -> upright

    val result = analyzer.analyze(frame)
    proxy.close()
    publish(result)
}
```

Notes:

- `rowStride` and `pixelStride` matter: **do not assume tightly packed planes.** Ignoring strides is the classic
  source of skewed/garbled frames on some devices.
- Use `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` so a slow frame never queues up.
- Downscale large frames before analysis if you need more speed: `frame.resize(w, h)`.
- Analysis runs on `analysisExecutor`, not the main thread.

## Gallery / photo bytes

```kotlin
val image = ImageDecoder.decode(bytes)        // JPEG/PNG -> RgbImage
val fromBitmap = bitmap.toRgbImage()          // android.graphics.Bitmap -> RgbImage
```

EXIF orientation is **not** applied by `ImageDecoder`. If the photo comes from a camera, read the orientation
(`androidx.exifinterface`) and call `rotate()` yourself.

## Shrinking / R8

ONNX Runtime ships its own consumer ProGuard rules. Faktel itself needs none.
APK size impact is dominated by ONNX Runtime's native libraries (per ABI); use ABI splits/App Bundles.

## Testing

Host (JVM) unit tests cover all pure-Kotlin logic. Running the ONNX models on a device or emulator is done from your
app; see [testing](../development/testing.md).
