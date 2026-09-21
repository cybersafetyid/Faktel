# Desktop JVM

Useful for server-side pre-checks, CLI tools, and fast local experiments.

```kotlin
dependencies { implementation("io.github.cybersafetyid.faktel:faktel-ort:<version>") }  // brings faktel-core

val engine = OrtInferenceEngine()                           // ONNX Runtime CPU, natives bundled for desktop OSes
val yunet = readModelFile("models/face_detection_yunet_2023mar.onnx")   // or readModelResource("...") from classpath
val detector = YuNetFaceDetector(engine, yunet)

val image = ImageDecoder.decode(File("photo.jpg").readBytes())
val faces = detector.detect(image)
```

`readModelResource(path)` reads from the classpath; `readModelFile(path)` from disk.

JVM target is JDK 17 bytecode. The integration tests in `faktel-ort/src/jvmTest` are a working, runnable example.
