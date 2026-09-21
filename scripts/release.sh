#!/usr/bin/env bash
# Usage:
#   scripts/release.sh 0.1.0          prepare release 0.1.0 (version, changelog, commit, local tag)
#   scripts/release.sh --next 0.2.0   set the development version to 0.2.0-SNAPSHOT and commit
set -euo pipefail
cd "$(dirname "$0")/.."

props=gradle.properties
today=$(date +%Y-%m-%d)

set_version() { sed -i.bak -E "s/^VERSION_NAME=.*/VERSION_NAME=$1/" "$props" && rm -f "$props.bak"; }

if [[ "${1:-}" == "--next" ]]; then
  [[ "${2:-}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "usage: $0 --next X.Y.Z" >&2; exit 2; }
  set_version "$2-SNAPSHOT"
  git add "$props"
  git commit -m "chore: start $2 development"
  exit 0
fi

version="${1:-}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "usage: $0 X.Y.Z" >&2; exit 2; }
[[ -z "$(git status --porcelain)" ]] || { echo "working tree not clean" >&2; exit 1; }
grep -q '^## \[Unreleased\]' CHANGELOG.md || { echo "CHANGELOG.md has no [Unreleased] section" >&2; exit 1; }

set_version "$version"
# Turn [Unreleased] into the release section and open a fresh [Unreleased].
sed -i.bak -E "s/^## \[Unreleased\]/## [Unreleased]\n\n## [$version] - $today/" CHANGELOG.md && rm -f CHANGELOG.md.bak
git add "$props" CHANGELOG.md
git commit -m "chore: release $version"
git tag -a "v$version" -m "Faktel $version"
echo "Created tag v$version. Review, then: git push origin main v$version"
