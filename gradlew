#!/usr/bin/env sh
# Small Gradle bootstrap used because the binary wrapper JAR is intentionally not vendored.
# The distribution is verified against Gradle's published SHA-256 before extraction.
set -eu
GRADLE_VERSION="8.13"
GRADLE_ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/nocturne-bootstrap"
GRADLE_HOME="$GRADLE_ROOT/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$GRADLE_ROOT"
  ARCHIVE="$GRADLE_ROOT/gradle-$GRADLE_VERSION-bin.zip"
  CHECKSUM_FILE="$ARCHIVE.sha256"
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  EXPECTED_SHA256="20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
  echo "Gradle $GRADLE_VERSION is not installed; downloading and verifying it."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "$URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "$URL"
  else
    echo "Install curl or wget to bootstrap Gradle." >&2
    exit 1
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL_SHA256="$(sha256sum "$ARCHIVE" | awk '{print $1}')"
  elif command -v shasum >/dev/null 2>&1; then
    ACTUAL_SHA256="$(shasum -a 256 "$ARCHIVE" | awk '{print $1}')"
  else
    echo "A SHA-256 utility (sha256sum or shasum) is required to verify Gradle." >&2
    exit 1
  fi
  if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
    echo "Gradle distribution SHA-256 mismatch. Refusing to extract it." >&2
    rm -f "$ARCHIVE"
    exit 1
  fi
  if ! command -v unzip >/dev/null 2>&1; then
    echo "Install unzip to unpack Gradle." >&2
    exit 1
  fi
  unzip -q -o "$ARCHIVE" -d "$GRADLE_ROOT"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"
