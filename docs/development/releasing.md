# Releasing

Maintainers only. Releases are tag-driven; [`release.yml`](../../.github/workflows/release.yml) does the publishing.

## One-time setup

1. **Maven Central**: register the namespace `io.github.cybersafetyid` on the
   [Central Publisher Portal](https://central.sonatype.com) (verified via the GitHub account) and create a user token.
2. **GPG**: create a signing key; publish the public key to a keyserver.
3. **GitHub repository secrets**:
   | Secret | Value |
   |---|---|
   | `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` | Portal user-token pair |
   | `SIGNING_IN_MEMORY_KEY` | ASCII-armoured private key (`gpg --armor --export-secret-keys ID`) |
   | `SIGNING_IN_MEMORY_KEY_ID` | last 8 hex chars of the key ID |
   | `SIGNING_IN_MEMORY_KEY_PASSWORD` | key passphrase |
4. **GitHub Pages**: Settings -> Pages -> Source: *GitHub Actions* (API reference is deployed by `docs.yml`).

## Cutting a release

```bash
scripts/release.sh 0.1.0        # sets VERSION_NAME, dates the CHANGELOG, commits, creates tag v0.1.0 (local)
git push origin main v0.1.0     # the tag triggers the release workflow
```

The workflow: verifies the tag equals `VERSION_NAME`; runs all checks; publishes every module to Maven Central;
builds `Faktel.xcframework`, zips it and computes its SwiftPM checksum; creates the GitHub Release with the
CHANGELOG section as notes and the XCFramework zip attached.

Afterwards, on `main`: bump `VERSION_NAME` to the next `-SNAPSHOT` (`scripts/release.sh --next 0.2.0`).

## Checklist

- [ ] CI green on `main`
- [ ] `CHANGELOG.md` `[Unreleased]` is accurate
- [ ] `./gradlew apiCheck` clean; intentional API changes reflect the version bump
- [ ] Model contracts unchanged, or MAJOR/MINOR bump chosen accordingly
- [ ] Docs updated for new behaviour
