#!/usr/bin/env bash
# Build the next Play release bundle.
#
#   ./scripts/release.sh            bump versionCode, build, archive the old bundle, sign-check
#   ./scripts/release.sh --no-bump  same, but keep the current versionCode (rebuild only)
#
# Result: releases/plainpdf-play.aab is always the bundle to upload, and the previous one
# is kept in releases/archive/ as plainpdf-<versionName>-<versionCode>-<date>.aab.
# The version bump is left uncommitted on purpose; commit it with the release.
set -euo pipefail
cd "$(dirname "$0")/.."

GRADLE_FILE="app/build.gradle.kts"
CURRENT="releases/plainpdf-play.aab"
SIDECAR="releases/plainpdf-play.version"
ARCHIVE="releases/archive"
mkdir -p "$ARCHIVE"

bump=1
[ "${1:-}" = "--no-bump" ] && bump=0

if [ "$bump" = 1 ]; then
  old=$(grep -oE 'versionCode = [0-9]+' "$GRADLE_FILE" | grep -oE '[0-9]+')
  new=$((old + 1))
  sed -i -E "s/versionCode = $old\b/versionCode = $new/" "$GRADLE_FILE"
  echo "versionCode $old -> $new"
fi

name=$(grep -oE 'versionName = "[^"]+"' "$GRADLE_FILE" | sed -E 's/.*"([^"]+)"/\1/')
code=$(grep -oE 'versionCode = [0-9]+' "$GRADLE_FILE" | grep -oE '[0-9]+')

echo "Building $name ($code)..."
./gradlew :app:bundlePlayRelease -q 2>&1 | grep -v -E '^w:|^\s*$' || true
built="app/build/outputs/bundle/playRelease/app-play-release.aab"
[ -f "$built" ] || { echo "Build failed: no bundle produced"; exit 1; }

# Archive whatever is current, named by the version it carries.
if [ -f "$CURRENT" ]; then
  if [ -f "$SIDECAR" ]; then
    read -r oname ocode odate < "$SIDECAR"
  else
    oname="unknown"; ocode="0"; odate=$(date -r "$CURRENT" +%Y-%m-%d)
  fi
  target="$ARCHIVE/plainpdf-$oname-$ocode-$odate.aab"
  n=1; while [ -f "$target" ]; do target="$ARCHIVE/plainpdf-$oname-$ocode-$odate-$n.aab"; n=$((n + 1)); done
  mv "$CURRENT" "$target"
  echo "Archived previous bundle as $target"
fi

cp "$built" "$CURRENT"
printf '%s %s %s\n' "$name" "$code" "$(date +%Y-%m-%d)" > "$SIDECAR"

if command -v jarsigner >/dev/null 2>&1; then
  jarsigner -verify "$CURRENT" | grep -qi "jar verified" && echo "Signature verified" || { echo "Signature check FAILED"; exit 1; }
fi
size=$(du -m "$CURRENT" | cut -f1)
echo "Ready: $CURRENT ($name, versionCode $code, ${size} MB)"
echo "Remember: commit the versionCode change together with this release."
./gradlew --stop >/dev/null 2>&1 || true
