# Faktel

**On-device face and Indonesian e-KTP detection for Kotlin Multiplatform.**

Faktel is an open-source Kotlin Multiplatform (KMP) library that runs computer-vision pipelines on the device -
no cloud round trip, no vendor CV SDK. One shared Kotlin API for Android, iOS and desktop JVM.

**[Bahasa Indonesia](README.id.md)**

[![CI](https://github.com/cybersafetyid/Faktel/actions/workflows/ci.yml/badge.svg)](https://github.com/cybersafetyid/Faktel/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
![Status](https://img.shields.io/badge/status-pre--1.0-orange.svg)

> **Status: pre-1.0 (`0.x`).** The API can still change between minor versions; every change is recorded in the
> [CHANGELOG](CHANGELOG.md). See [Limitations](#limitations-read-this-first) before using it for anything
> security-sensitive.

## What it does

| Capability | API | Backed by | Needs a model? |
|---|---|---|---|
| Face detection + 5 landmarks | `YuNetFaceDetector` | YuNet (MIT) | yes - **bundled in repo** |
| Face quality gate (size, blur, exposure, pose, cut-off) | `FaceQualityAssessor` | pure Kotlin | no |
| Passive liveness (print/replay attacks) | `MiniFasNetLivenessDetector` | MiniFASNetV2 (Apache-2.0) | yes - **bundled in repo** |
| Face embedding + selfie-to-KTP matching | `ArcFaceEmbedder`, `FaceMatcher` | any ArcFace-style ONNX | yes - **bring your own** ([why](models/MODELS.md)) |
| KTP card localisation | `ClassicalKtpDetector` (or your own `KtpDetector`) | pure Kotlin | no |
| KTP rectification + validation (aspect ratio 1.586, size, blur, glare, portrait placement) | `KtpScanner` | pure Kotlin + face detector | optional |
| Camera-frame conversion (NV21 / YUV_420_888 / BGRA / RGBA, rotate, flip) | `RgbImage.from*` | pure Kotlin | no |

All heavy math runs through one small seam, `InferenceEngine`, so the ML runtime is swappable.

## Modules

| Artifact | Contents | Targets |
|---|---|---|
| `faktel` | Umbrella: depends on everything below; also the iOS `Faktel.xcframework` | Android, iOS, JVM |
| `faktel-core` | Image type, geometry, preprocessing, quality metrics, `InferenceEngine` interface | Android, iOS, JVM |
| `faktel-face` | Detector, quality, liveness, embedding, matching | Android, iOS, JVM |
| `faktel-ktp` | KTP detection, rectification, validation | Android, iOS, JVM |
| `faktel-ort` | ONNX Runtime backend (Android + JVM; iOS via Swift package) | Android, JVM (+ Swift) |

Group ID: `io.github.cybersafetyid.faktel`. Details: [architecture](docs/architecture.md).

## Install

```kotlin
// build.gradle.kts of your shared module
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.cybersafetyid.faktel:faktel:<version>")
        }
    }
}
```

Then wire the platform pieces (one-time): [Android](docs/guides/android.md) - [iOS](docs/guides/ios.md) -
[Desktop JVM](docs/guides/jvm.md).

## Quick start

Shared code depends only on the `InferenceEngine` interface:

```kotlin
class SelfieChecker(engine: InferenceEngine, yunet: ByteArray, fas: ByteArray) {
    private val analyzer = FaceAnalyzer(
        detector = YuNetFaceDetector(engine, yunet),
        liveness = MiniFasNetLivenessDetector(engine, fas),
    )

    fun check(frame: RgbImage): FaceAnalysis = analyzer.analyze(frame)   // detect -> quality -> liveness
}
```

```kotlin
val scanner = KtpScanner(faceDetector = YuNetFaceDetector(engine, yunet))
val scan = scanner.scan(photo)
if (scan.isAcceptable) {
    val card = scan.card!!            // upright 1011x638 image
    val portrait = scan.portrait!!    // face box in card coordinates
} else {
    showHint(scan.issues)             // e.g. [BLURRY, GLARE] -> "hold steady, avoid reflections"
}
```

Selfie-to-KTP match: [docs/guides/selfie-ktp-matching.md](docs/guides/selfie-ktp-matching.md).

## Documentation

Start at **[docs/README.md](docs/README.md)**. Highlights:
[Getting started](docs/guides/getting-started.md) - [Architecture](docs/architecture.md) -
[Face guide](docs/guides/face.md) - [KTP guide](docs/guides/ktp.md) - [Models & licences](models/MODELS.md) -
[Tuning thresholds](docs/guides/tuning.md) - [Privacy & security](docs/guides/privacy-and-security.md) -
[Troubleshooting](docs/guides/troubleshooting.md) - [API reference](https://cybersafetyid.github.io/Faktel/).

## Limitations (read this first)

- **Liveness is passive and single-frame.** It stops casual printed-photo and screen-replay attacks. It is **not**
  certified anti-spoofing (e.g. iBeta PAD) and must not be your only control in a high-assurance flow.
- **The KTP module checks that an object *looks like* a KTP** (shape, size, focus, glare, portrait placement). It does
  not read text (no OCR), verify authenticity, or detect forgeries.
- **The bundled classical card detector needs a plain, contrasting background** and a visible margin around the card.
  A learned detector is on the [roadmap](docs/roadmap.md); the `KtpDetector` interface is where it plugs in.
- **Default thresholds are starting points.** Calibrate on your devices and users
  ([guide](docs/guides/tuning.md)).
- **KTP images are personal data** under Indonesian data-protection law. Process on-device, do not persist raw images
  ([guide](docs/guides/privacy-and-security.md)).

## Verified

Unit tests run on JVM and the iOS simulator in CI. The real models (YuNet, MiniFASNetV2, an ArcFace MobileFaceNet)
were exercised end-to-end through ONNX Runtime on desktop JVM **and** on an iPhone simulator with matching results.
On-device Android *runtime* (as opposed to compilation) is exercised by your app; see
[docs/development/testing.md](docs/development/testing.md) for what is and is not covered automatically.

## Contributing

Contributions are welcome - bug reports, docs, thresholds from real devices, new backends, a learned KTP detector.
Read [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md). Versioning follows
[SemVer](docs/development/versioning.md). Security issues: [SECURITY.md](SECURITY.md).

## License

[Apache License 2.0](LICENSE). Bundled model weights keep their own licences - see [models/MODELS.md](models/MODELS.md)
and [NOTICE](NOTICE).
