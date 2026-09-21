# ADR 0001: A single `InferenceEngine` seam

- Status: accepted
- Date: 2026-09-21

## Context

The models we use (YuNet, MiniFASNet, ArcFace-style embedders) are small and mature. The hard part on KMP is running
them: each platform has a different native runtime, and the research behind this project found the KMP-to-native
boundary (especially iOS linking) to be the main engineering risk. Runtimes will also change (ONNX Runtime, LiteRT,
NCNN/MNN, CoreML).

## Decision

Define a minimal interface in `commonMain` - `InferenceEngine` -> `InferenceSession` -> `Tensor` (float32) - and write
every pipeline (detector, liveness, embedding) against it. Pre/post-processing (letterbox, normalisation, decoding,
NMS, alignment) is shared Kotlin.

## Consequences

- Pipelines are testable with a fake engine, without a model or device.
- Backends are independent modules; adding LiteRT or NCNN does not touch pipelines.
- Only float32 tensors are supported. Quantised int8 models must be dequantised at the boundary or be handled by a
  backend that hides it. Acceptable for the current models; revisit if a needed model requires other dtypes.
- Per-call tensor copies cross the boundary. Cost is small next to inference for these model sizes.

## Alternatives considered

- **Call the runtime directly from each pipeline via `expect/actual`.** Rejected: duplicates runtime code per model
  and makes pipelines untestable.
- **Pure-Kotlin inference (KInference).** Rejected: not viable for real-time camera CNNs.
- **Depend on KTensorFlow (LiteRT wrapper).** Viable, and a backend could be built on it, but it requires CocoaPods
  linking on iOS and locks the model format to `.tflite`.
