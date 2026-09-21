# Testing

## Layers

| Layer | Where | Runs on | Needs models? |
|---|---|---|---|
| Unit tests (geometry, image ops, decoding, validation, pipelines with a fake engine) | `*/src/commonTest` | JVM, iOS simulator, Android host | no |
| Real-model integration (YuNet + MiniFASNet + KTP scan through ONNX Runtime) | `faktel-ort/src/jvmTest` | desktop JVM | bundled models |
| Real-model integration on iOS (Swift ORT + Kotlin pipeline) | `ios/FaktelOnnxRuntime/Tests` | iOS simulator | bundled models |

`commonTest` is the workhorse: pipelines take an `InferenceEngine`, so tests substitute a fake and assert on
decoding, coordinate mapping, gating and error paths with no model or device.

## Real-model tests need a photo

Test photos are **not committed** (licensing and privacy). The integration tests are skipped unless you provide one:

```bash
FAKTEL_TEST_IMAGE=/path/to/face.jpg \
FAKTEL_TEST_KTP_IMAGE=/path/to/synthetic-or-consented-card-photo.jpg \
FAKTEL_EMBEDDER_MODEL=/path/to/arcface.onnx \
./gradlew :faktel-ort:jvmTest --rerun

# iOS: environment variables are forwarded with the TEST_RUNNER_ prefix
TEST_RUNNER_FAKTEL_TEST_IMAGE=/path/to/face.jpg \
xcodebuild test -scheme FaktelOnnxRuntime -destination 'platform=iOS Simulator,name=iPhone 16'
```

**Never use real KTP photos of other people.** Use a synthetic card or your own, and keep it out of git.

## What is and is not covered automatically

Covered by CI: unit tests on JVM and the iOS simulator; Android compilation and host tests; public-API compatibility;
XCFramework build.

Verified manually during development (not yet automated in CI): the real models through ONNX Runtime on desktop JVM
and on an iPhone simulator, with matching results.

**Not covered:** running inference on an Android device/emulator (ONNX Runtime's Android natives cannot load in a
host JVM test). The Android backend shares its implementation with the desktop backend (same Java API) and is
compiled in CI, but on-device behaviour and performance should be checked in your app. An emulator CI job is on the
[roadmap](../roadmap.md); contributions welcome.

## Writing tests

- Prefer `commonTest` with `FakeEngine`-style fakes (see `faktel-face/src/commonTest/.../Fakes.kt`).
- Compute expected values independently of the code under test (e.g. from a reference implementation), and cite it.
- Every bug fix gets a regression test.
