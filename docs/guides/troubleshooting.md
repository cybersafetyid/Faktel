# Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `FaktelException.ModelLoad: Not a YuNet model: missing outputs [...]` | Wrong `.onnx` (or a different YuNet export) | Use `face_detection_yunet_2023mar.onnx` from `models/`; the decoder relies on its output names. |
| `FaktelException.ModelLoad: ONNX Runtime could not load the model` | Truncated file, or asset compressed/corrupted by the build | Verify SHA-256 ([models](../../models/MODELS.md)); on Android do not let AAPT compress `.onnx` if you memory-map it. |
| Faces detected in the wrong place / boxes offset | Camera rotation not applied, or mirrored image | Call `rotate(rotationDegrees)` before analysis; map results back with the same transform for overlays. |
| Skewed, striped or green frames on Android | YUV plane strides ignored | Pass `yRowStride`, `uvRowStride`, `uvPixelStride` exactly as reported by CameraX ([guide](android.md)). |
| Colours look swapped on iOS | BGRA treated as RGBA | Use `RgbImage.fromBgra` for `kCVPixelFormatType_32BGRA`. |
| Photo from gallery is sideways | EXIF orientation not applied | `ImageDecoder` ignores EXIF; read the tag and call `rotate()`. |
| No face found in a large photo with a small face | Model input is 640 px; face is tiny after downscale | Crop or ask for a closer selfie; use `minFaceWidthRatio`. |
| Liveness always "spoof" | Wrong `cropScale` for the model, or crop channels/order changed | `2.7` for MiniFASNetV2, `4.0` for V1SE; do not pre-normalise pixels (the model expects raw 0..255 BGR). |
| Liveness never runs (`liveness == null`) | Quality gate failed, or no `LivenessDetector` configured | Check `FaceAnalysis.issues`; it is skipped on unusable frames on purpose. |
| KTP `CARD_NOT_FOUND` | Cluttered/similar-coloured background, card fills the frame | Use a plain contrasting surface with margin, or implement a learned `KtpDetector` ([KTP guide](ktp.md)). |
| KTP `BAD_ASPECT_RATIO` while card looks fine | Strong perspective, or the card is cropped/occluded | Ask for a flatter, complete shot; loosen `aspectTolerance` cautiously. |
| KTP `BLURRY` on every frame | `minBlurScore` not calibrated for your camera | Log `blurScore` on good captures and set the threshold below them ([tuning](tuning.md)). |
| iOS: `Undefined symbols`/duplicate Kotlin classes | Both `Faktel.xcframework` and your own KMP framework include Faktel | Use exactly one; see [iOS guide B](ios.md#b-kotlin-multiplatform-app-shared-module--ios-app). |
| iOS: Swift `OrtInferenceEngine` does not conform to `InferenceEngine` | Adapter imports a different framework than the one exporting the Kotlin protocol | Change the adapter's `import` to the framework that exports `faktel-core`. |
| Gradle: strange `Source file ... not found` in `build-logic` | Stale Gradle caches on some external volumes | See [building](../development/building.md#known-environment-issues). |

Still stuck? Open an issue with: platform/OS/device, Faktel version, minimal snippet, and the exception text. Do
**not** attach real personal images.
