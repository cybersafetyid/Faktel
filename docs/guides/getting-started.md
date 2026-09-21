# Getting started

## 1. Add the dependency

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.cybersafetyid.faktel:faktel:<version>")
        }
    }
}
```

Requirements: Kotlin 2.3+, Android `minSdk 24`, iOS 15+, JDK 17+ for desktop.
Prefer fine-grained artifacts if you only need part of it (`faktel-face`, `faktel-ktp`, `faktel-ort`).

## 2. Ship the models

Copy `face_detection_yunet_2023mar.onnx` and `minifasnet_v2.onnx` from [`models/`](../../models/MODELS.md) into your
app: `src/androidMain/assets/` on Android, the app bundle ("Copy Bundle Resources") on iOS, or
`src/main/resources/` on desktop.

## 3. Provide an inference engine (per platform, once)

| Platform | What to do |
|---|---|
| Android | `val engine = OrtInferenceEngine()` - [guide](android.md) |
| Desktop JVM | `val engine = OrtInferenceEngine()` - [guide](jvm.md) |
| iOS | Add the Swift package and pass `OrtInferenceEngine()` in - [guide](ios.md) |

Everything else is shared Kotlin that receives an `InferenceEngine`.

## 4. Run a pipeline (shared code)

```kotlin
class FaceService(engine: InferenceEngine, yunet: ByteArray, fas: ByteArray) : AutoCloseable {
    private val analyzer = FaceAnalyzer(
        detector = YuNetFaceDetector(engine, yunet),
        liveness = MiniFasNetLivenessDetector(engine, fas),
    )

    fun analyze(frame: RgbImage): FaceAnalysis = analyzer.analyze(frame)
    override fun close() = analyzer.close()
}
```

Interpret the result in your UI:

```kotlin
val r = service.analyze(frame)
when {
    FaceIssue.NO_FACE in r.issues        -> hint("Position your face in the frame")
    FaceIssue.TOO_DARK in r.issues       -> hint("Find better light")
    FaceIssue.FACE_ANGLE in r.issues     -> hint("Look straight at the camera")
    r.liveness?.isLive == false          -> reject()
    r.isAcceptable                       -> capture(r.face!!)
}
```

## 5. Mind the basics

- **Create detectors once**, reuse across frames, `close()` on teardown.
- **Never run on the UI thread.** One session per thread.
- **Apply camera rotation and mirroring before analysis**: `frame.rotate(rotationDegrees)`, and for a mirrored
  front-camera preview *do not* mirror the analysis image unless your model pipeline expects it.
- **Do not persist raw face or KTP images.** See [privacy](privacy-and-security.md).
