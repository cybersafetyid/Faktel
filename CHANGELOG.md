# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). See [versioning](docs/development/versioning.md).

## [Unreleased]

## [0.1.0] - 2026-09-21

### Added
- `faktel-core`: `RgbImage` (RGBA/BGRA/NV21/YUV_420_888 conversion, crop, resize, rotate, flip), geometry
  (`Point`, `Rect`, `Quad`, `Homography`, `SimilarityTransform`), perspective and similarity warps, letterbox and
  tensor preprocessing, image-quality metrics, platform `ImageDecoder`, and the `InferenceEngine` seam.
- `faktel-face`: `YuNetFaceDetector`, head-pose estimate, `FaceQualityAssessor`, `MiniFasNetLivenessDetector`,
  `ArcFaceEmbedder`, `FaceMatcher`, `FaceAnalyzer`.
- `faktel-ktp`: `ClassicalKtpDetector`, `KtpScanner` (rectification, aspect-ratio/size/blur/glare/portrait
  validation, 180-degree recovery), `KtpSpec`.
- `faktel-ort`: `OrtInferenceEngine` for Android and desktop JVM.
- `faktel`: umbrella module, `Faktel.VERSION`, iOS `Faktel.xcframework`.
- `ios/FaktelOnnxRuntime`: Swift ONNX Runtime backend for iOS with an XCTest that runs the real model.
- Bundled models: YuNet (MIT) and MiniFASNetV2 (Apache-2.0).
- Documentation, ADRs, CI, release automation.
