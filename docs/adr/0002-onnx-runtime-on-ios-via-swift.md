# ADR 0002: ONNX Runtime on iOS lives in Swift

- Status: accepted
- Date: 2026-09-21

## Context

ONNX Runtime ships iOS builds as XCFrameworks of **static libraries**. Consuming them from Kotlin/Native via
cinterop runs into documented friction: simulator/device slice selection, header paths, and linker options that
cinterop does not accept through Gradle. The only public KMP + ONNX iOS sample wraps the runtime in Swift rather than
calling the C API through cinterop.

## Decision

Implement the iOS `InferenceEngine` in Swift (`ios/FaktelOnnxRuntime`) using the official
`onnxruntime-swift-package-manager` package, and pass it into shared Kotlin through the exported Kotlin protocol.
Kotlin provides `memcpy`-based helpers (`toNSData`, `toByteArray`, `tensorFromNSData`) so buffers cross the boundary
without per-element copies.

## Consequences

- No cinterop to ONNX Runtime, no static-library linking in Gradle. The Swift compiler and SwiftPM resolve the runtime.
- iOS consumers must wire one Swift file/package. We accept that cost and document both integration styles.
- The Swift adapter must conform to the Kotlin protocol *of the framework the app links* - see the iOS guide (KMP apps
  copy the file and change one import).
- The model is written to a temporary file because the Objective-C API loads from a path.
- Verified end to end: the package's XCTest runs the real model on an iOS simulator with results matching desktop JVM.

## Alternatives considered

- **cinterop to the ORT C API.** Rejected for now (see Context); can be revisited to remove the Swift step.
- **CoreML backend.** Requires model conversion and would still need a Swift/ObjC shim; a candidate for a later backend.
