# Versioning

Faktel follows [Semantic Versioning 2.0.0](https://semver.org): `MAJOR.MINOR.PATCH`.

| Change | Bump |
|---|---|
| Bug fix, internal change, docs, model-contract-compatible fix | PATCH |
| Backwards-compatible feature or new module/class | MINOR |
| Breaking change to public API or to an ONNX model contract (input layout, normalisation, output names) | MAJOR |

**Pre-1.0 (`0.x`)**: the API is not yet frozen. Breaking changes may land in MINOR releases, always listed under
"Changed/Removed" in the [CHANGELOG](../../CHANGELOG.md). `1.0.0` is declared once the API has settled and
device benchmarks and calibrated thresholds exist.

## Single source of truth

`VERSION_NAME` in [`gradle.properties`](../../gradle.properties). All modules publish the **same version** (they are
released together). Development builds are `X.Y.Z-SNAPSHOT`; releases drop the suffix. The version is also exposed at
runtime as `Faktel.VERSION` (umbrella module).

## What counts as public API

Everything public in a module's Kotlin API (guarded by `apiCheck`, see each module's `api/` directory), the Swift-visible
API of `Faktel.xcframework`, and the model I/O contracts in [models/MODELS.md](../../models/MODELS.md).
Default values of `*Config` classes are behaviour: changing a default threshold is documented in the changelog, and is a
MINOR change pre-1.0 and a MAJOR change afterwards if it can flip results for existing integrations.

## Deprecation

Deprecate with `@Deprecated(message, ReplaceWith(...))` for at least one MINOR release before removal (post-1.0).

## Changelog

[Keep a Changelog](https://keepachangelog.com) format. Add entries under `## [Unreleased]` in every PR that changes
behaviour.
