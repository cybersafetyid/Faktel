# Contributing to Faktel

Thanks for helping! Bug reports, docs fixes, real-device measurements, new backends and a learned KTP detector are all
welcome. By participating you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to contribute

- **Report a bug** or **request a feature** using the issue templates. Include platform, device, Faktel version and a
  minimal snippet. **Never attach real personal images** (faces of others, real KTPs).
- **Share measurements**: blur/glare/liveness/match scores from real devices help calibrate defaults
  ([tuning](docs/guides/tuning.md)).
- **Improve docs**: typos, missing steps, clearer examples.
- **Write code**: pick an issue labelled `good first issue` or `help wanted`, or one from the [roadmap](docs/roadmap.md).
  For anything non-trivial, open an issue first so we can agree on the approach.

## Development setup

See [docs/development/building.md](docs/development/building.md). In short: JDK 21, Android SDK (`local.properties`),
macOS + Xcode for iOS.

```bash
./gradlew jvmTest apiCheck
```

## Workflow

1. Fork, then branch from `main`: `feat/short-name`, `fix/short-name`, `docs/short-name`.
2. Make focused changes. One logical change per PR.
3. Add or update tests. Bug fixes need a regression test.
4. Update docs and add an entry under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md).
5. Run `./gradlew jvmTest apiCheck` (and `iosSimulatorArm64Test` on macOS). If you changed the public API on purpose,
   run `./gradlew apiDump` and commit the updated `api/` files.
6. Open a PR using the template. CI must be green.

### Commit messages

[Conventional Commits](https://www.conventionalcommits.org): `feat(face): ...`, `fix(ktp): ...`, `docs: ...`,
`test: ...`, `chore: ...`. Explain *why* in the body when it is not obvious.

## Code standards

- **Kotlin official style**; explicit API mode is on - every public declaration must be intentional and documented
  with KDoc; prefer `internal`.
- **Warnings are errors.**
- **Shared code first.** Put logic in `commonMain`; add `expect/actual` only when a platform API is unavoidable.
- **Pure functions over state** for image/geometry code; keep them unit-testable without a model or a device.
- **No network, no telemetry, no image persistence** in library code. This is a hard rule.
- **Never commit real personal data.** No real KTPs, no real face photos, no embeddings of real people.
- Match the surrounding code's naming and comment density. Comments explain *why*, not *what*.

## Adding a model or backend

**Model** (detector, liveness, embedder, KTP detector):
1. Verify the **weights' licence** permits redistribution/commercial use. Code licence is not enough.
2. Implement the relevant interface (`FaceDetector`, `LivenessDetector`, `FaceEmbedder`, `KtpDetector`) in the right
   module. Keep pre/post-processing in Kotlin and the model call behind `InferenceEngine`.
3. Unit-test decoding/mapping with a fake engine (see `faktel-face/src/commonTest`); if you can, add a real-model
   integration test gated on an env var.
4. Document its I/O contract, source, size, licence and SHA-256 in [models/MODELS.md](models/MODELS.md).

**Inference backend** (LiteRT, NCNN, ...): implement `InferenceEngine`/`InferenceSession` in a new `faktel-<name>`
module; add it to the umbrella only if it is broadly useful. Explain the trade-off in an ADR under `docs/adr/`.

## Reviewing

Reviews focus on correctness, API stability, test quality and privacy implications. Small PRs are reviewed faster.

## Licensing of contributions

Unless you state otherwise, contributions are licensed under the project's [Apache-2.0 license](LICENSE)
(inbound = outbound). You confirm that you have the right to submit the work.
