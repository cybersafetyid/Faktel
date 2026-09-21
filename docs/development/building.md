# Building

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | **21** | Runs Gradle. Library bytecode targets Java 17. |
| Android SDK | platform 36 | Point Gradle at it with `local.properties`: `sdk.dir=/path/to/sdk` (not committed). |
| macOS + Xcode | 15+ | Needed for iOS targets, tests and the XCFramework. Linux/Windows can build Android + JVM only. |

The Gradle wrapper is committed; use `./gradlew`.

## Project layout

```
faktel-core/  faktel-face/  faktel-ktp/  faktel-ort/  faktel/     library modules (see docs/architecture.md)
build-logic/                                                       convention plugins (kmp-library, publish, docs)
ios/FaktelOnnxRuntime/                                             Swift ONNX Runtime backend + XCTest
models/                                                            bundled ONNX models + MODELS.md
docs/                                                              documentation
gradle/libs.versions.toml                                          all dependency versions
gradle.properties                                                  GROUP and VERSION_NAME (single source of truth)
```

## Common tasks

```bash
./gradlew jvmTest                               # unit tests, all modules, desktop JVM
./gradlew iosSimulatorArm64Test                 # same tests on the iOS simulator (macOS)
./gradlew testAndroidHostTest                   # Android host tests
./gradlew apiCheck                              # public API unchanged? (fails on unreviewed changes)
./gradlew apiDump                               # accept intentional public API changes
./gradlew :faktel:assembleFaktelXCFramework     # iOS XCFramework -> faktel/build/XCFrameworks/
./gradlew dokkaGenerate                         # API reference -> build/dokka/html
./gradlew publishToMavenLocal                   # install artifacts into ~/.m2 for a local consumer
```

Conventions enforced by the build: Kotlin **explicit API mode** (every public declaration is deliberate) and
**warnings are errors**.

## Known environment issues

- **Configuration cache and build cache are disabled** in `gradle.properties`. On some setups (notably projects on
  external volumes) the precompiled convention plugins in `build-logic` failed with "Source file or directory not
  found" when either was enabled. Re-enable them if your environment is fine; measure and open a PR.
- **Kotlin compiler runs in-process** (`kotlin.compiler.execution.strategy=in-process`) for the same reason.
- **Mixed JDKs**: if you see "compiled by a more recent version of the Java Runtime", your shell and Gradle daemons
  use different JDKs. Set `JAVA_HOME` to JDK 21 and run `./gradlew --stop`.
- `build-logic/build` can hold stale generated sources after plugin edits: `./gradlew --stop && rm -rf build-logic/build`.

## Swift package

```bash
./gradlew :faktel:assembleFaktelXCFramework
cd ios/FaktelOnnxRuntime
xcodebuild test -scheme FaktelOnnxRuntime -destination 'platform=iOS Simulator,name=iPhone 16'
```
