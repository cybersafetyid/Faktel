# Architecture

## Goals

1. **One Kotlin API** for Android, iOS and desktop JVM; nothing in the public API is platform-specific.
2. **On-device only.** No network calls, no cloud CV, no vendor CV SDK.
3. **Swappable inference runtime.** Models are mature; the native-runtime boundary is the hard part on KMP, so it
   is isolated behind one interface.
4. **Small, testable units.** All geometry, image processing, decoding and validation is plain Kotlin in `commonMain`
   and is unit-tested without a device or a model.

## Module graph

```
                      faktel   (umbrella + iOS XCFramework)
             ┌───────────┼──────────────┬─────────────┐
             ▼           ▼              ▼             ▼
        faktel-ktp ──▶ faktel-face ──▶ faktel-core ◀── faktel-ort
        (KTP scan)     (faces)         (types, image,   (ONNX Runtime backend:
                                        InferenceEngine) Android + JVM)
```

- **`faktel-core`** - `RgbImage`, `Point/Rect/Quad`, `Homography`, `SimilarityTransform`, `warpPerspective`,
  `letterbox`, `toTensor`, quality metrics, `ImageDecoder` (`expect/actual`), and the `InferenceEngine` /
  `InferenceSession` / `Tensor` seam. **No ML dependency.**
- **`faktel-face`** - `FaceDetector` (`YuNetFaceDetector`), `FaceQualityAssessor`, `LivenessDetector`
  (`MiniFasNetLivenessDetector`), `FaceEmbedder` (`ArcFaceEmbedder`), `FaceMatcher`, `FaceAnalyzer`.
- **`faktel-ktp`** - `KtpDetector` (`ClassicalKtpDetector`), `KtpScanner`, `KtpSpec`. Depends on `faktel-face`
  because portrait placement is part of validating a KTP.
- **`faktel-ort`** - `OrtInferenceEngine` for Android and JVM. The iOS counterpart is Swift
  ([`ios/FaktelOnnxRuntime`](../ios/FaktelOnnxRuntime)).
- **`faktel`** - depends on all of the above; builds `Faktel.xcframework`; exposes `Faktel.VERSION`.

Dependencies only point **downwards**. `faktel-face` and `faktel-ktp` know nothing about ONNX Runtime; that is what
lets you replace the backend, and what makes their tests run with a fake engine.

## Data flow

```
camera frame / photo bytes
      │  RgbImage.fromYuv420 / fromBgra / ImageDecoder.decode      (platform edge, once)
      ▼
   RgbImage  ── rotate()/flipHorizontal() ──▶ upright, unmirrored
      │
      ├── selfie ─▶ YuNet ─▶ Face ─▶ quality gate ─▶ MiniFASNet ─▶ (optional) ArcFace embedding
      │             letterbox 640      blur/exposure/    crop x2.7      align 5 pts → 112x112
      │             decode+NMS         pose/size         softmax
      │
      └── card ───▶ KtpDetector ─▶ Quad ─▶ warpPerspective 1011x638 ─▶ validate
                    (segment+hull)          (homography)                 aspect/size/blur/glare
                                                                         portrait on right? (YuNet)
```

Rules that keep it fast on phones:

- **Convert once.** Platform frame formats become `RgbImage` at the edge; nothing downstream knows about them.
- **Run models selectively.** Detection is cheap; liveness and embedding only run on frames that pass the quality
  gate (`FaceAnalyzer` does this for you). Do not run the face and KTP pipelines at the same time - they are
  different capture modes.
- **Reuse sessions.** Creating a session is expensive (model parse); creating one per frame will destroy your frame
  rate. Create once, `close()` on teardown.

## The inference seam

```kotlin
interface InferenceEngine  { fun createSession(model: ByteArray, options: SessionOptions): InferenceSession }
interface InferenceSession : AutoCloseable { val inputNames; val outputNames; fun run(inputs: Map<String, Tensor>): Map<String, Tensor> }
class Tensor(val shape: IntArray, val data: FloatArray)      // float32, row-major
```

Why this shape: it is the smallest surface every runtime can implement (ONNX Runtime, LiteRT, NCNN, MNN, CoreML),
and float32 tensors cover all the models used. Rationale and rejected alternatives:
[ADR 0001](adr/0001-inference-seam.md), [ADR 0002](adr/0002-onnx-runtime-on-ios-via-swift.md).

Threading: a session is **not** guaranteed thread-safe. Use one session per thread, and never call detectors on the
UI thread.

## Error model

- Programming/infrastructure failures throw `FaktelException` (`ModelLoad`, `Inference`, `InvalidImage`).
- Business outcomes are **values**, never exceptions: "no face" is `FaceIssue.NO_FACE`, "not a KTP" is a list of
  `KtpIssue`. Your UI should react to those lists.

## Coordinates and conventions

- Pixel origin top-left, y down. `Rect`/`Point`/`Quad` use `Double`.
- Landmarks are anatomical: `rightEye` is the *subject's* right eye, which is on the **left** of a non-mirrored image.
- A `Quad` is ordered clockwise from the top-left corner.
- Face coordinates are in the image passed to the detector. `KtpScanResult.portrait` is in **rectified card**
  coordinates, and `KtpScanResult.card` is the image those coordinates refer to.

## Public API stability

Public API is guarded by [binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator)
dumps in each module's `api/` directory; CI fails if a public signature changes without an updated dump, which
forces the change to be deliberate and visible in review. See [versioning](development/versioning.md).
