# Roadmap

Status of ideas, not promises. Open an issue to discuss or to pick one up.

## Next (0.2 - 0.x)

- **Learned KTP detector** (`KtpDetector` implementation): small YOLO / segmentation model trained on synthetic KTP
  data (template + generated fields/faces, composited on varied backgrounds with perspective/glare augmentation) and
  validated on a small consented real holdout. The classical detector stays as a fallback. This removes the plain-background
  limitation.
- **Device benchmarks**: published latency/fps numbers on representative mid-range Android and iOS devices; the
  research this project builds on notes that mobile-CPU numbers for these exact models under ONNX Runtime are
  largely unpublished.
- **Calibrated default thresholds** from real-device data (blur, glare, liveness, match).
- **Android runtime CI** using an emulator/device-farm job (compile + host tests run today).
- **Sample apps** (Compose Multiplatform) with camera integration.

## Later

- Active liveness (blink / head-turn) from landmark tracking.
- Additional backends behind `InferenceEngine`: LiteRT, NCNN/MNN, CoreML/Metal delegate on iOS, NNAPI/GPU on Android.
- Optional camera-integration module (CameraK / Peekaboo adapters).
- Card-region crops for downstream OCR of NIK and name fields (Faktel itself will not ship OCR).
- Publish `Package.swift` for the XCFramework on each release.

## Explicit non-goals

- Certified anti-spoofing / iBeta-grade PAD.
- KTP authenticity or forgery detection.
- Cloud APIs or anything that uploads images.
