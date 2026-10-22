#!/usr/bin/env bash
# Downloads everything Patootie needs and builds it: a JDK 17 (only if you don't
# have Java 17+), the Android SDK pieces, and all Gradle dependencies.
# Nothing is installed system-wide; it all goes into .local/ next to the project.
#
#   ./scripts/setup-and-build.sh
#
# Works on Linux and macOS (Intel or Apple Silicon). On Windows, run it in WSL.
# Uses ANDROID_HOME instead of downloading an SDK if you already have one.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCAL="$ROOT/.local"
CMDLINE_TOOLS_BUILD=11076708
PLATFORM="platforms;android-35"
BUILD_TOOLS="build-tools;35.0.0"

say() { printf '\n\033[1;35m✿ %s\033[0m\n' "$*"; }
die() { printf '\n\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

for tool in curl unzip tar; do
  command -v "$tool" >/dev/null || die "Please install '$tool' first."
done

case "$(uname -s)" in
  Linux) OS=linux; SDK_OS=linux ;;
  Darwin) OS=mac; SDK_OS=mac ;;
  *) die "Unsupported OS $(uname -s). On Windows, run this inside WSL." ;;
esac
case "$(uname -m)" in
  x86_64 | amd64) ARCH=x64 ;;
  arm64 | aarch64) ARCH=aarch64 ;;
  *) die "Unsupported CPU $(uname -m)." ;;
esac
mkdir -p "$LOCAL"

# --- Java 17+ -------------------------------------------------------------
java_major() { "$1" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1; }
JAVA_BIN=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVA_BIN="$JAVA_HOME/bin/java";
elif command -v java >/dev/null; then JAVA_BIN="$(command -v java)"; fi

if [ -n "$JAVA_BIN" ] && [ "$(java_major "$JAVA_BIN")" -ge 17 ] 2>/dev/null; then
  say "Using your Java $(java_major "$JAVA_BIN")"
  [ -n "${JAVA_HOME:-}" ] || export JAVA_HOME="$(cd "$(dirname "$(readlink -f "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")")/.." && pwd)"
else
  JDK_DIR="$LOCAL/jdk-17"
  if [ ! -x "$JDK_DIR/bin/java" ]; then
    say "Downloading JDK 17 (Eclipse Temurin)…"
    rm -rf "$LOCAL/jdk-download" && mkdir -p "$LOCAL/jdk-download"
    curl -fL --retry 3 -o "$LOCAL/jdk.tar.gz" \
      "https://api.adoptium.net/v3/binary/latest/17/ga/$OS/$ARCH/jdk/hotspot/normal/eclipse"
    tar -xzf "$LOCAL/jdk.tar.gz" -C "$LOCAL/jdk-download"
    home="$(find "$LOCAL/jdk-download" -maxdepth 6 -type f -path '*/bin/java' | head -1)"
    [ -n "$home" ] || die "Couldn't find java in the JDK download."
    rm -rf "$JDK_DIR" && mv "$(dirname "$(dirname "$home")")" "$JDK_DIR"
    rm -rf "$LOCAL/jdk-download" "$LOCAL/jdk.tar.gz"
  fi
  export JAVA_HOME="$JDK_DIR"
  say "Using bundled JDK 17"
fi
export PATH="$JAVA_HOME/bin:$PATH"

# --- Android SDK ------------------------------------------------------------
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$LOCAL/android-sdk}}"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  say "Downloading Android command-line tools…"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  curl -fL --retry 3 -o "$LOCAL/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-$SDK_OS-${CMDLINE_TOOLS_BUILD}_latest.zip"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest" "$ANDROID_HOME/cmdline-tools/cmdline-tools"
  unzip -q "$LOCAL/cmdline-tools.zip" -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm "$LOCAL/cmdline-tools.zip"
fi

say "Accepting Android SDK licenses…"
yes 2>/dev/null | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true

say "Installing Android platform 35 and build tools (skips what's already there)…"
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" "$PLATFORM" "$BUILD_TOOLS" "platform-tools"

echo "sdk.dir=$ANDROID_HOME" > "$ROOT/local.properties"

# --- Build ----------------------------------------------------------------
cd "$ROOT"
say "Building (first run downloads Gradle and all libraries; give it a few minutes)…"
./gradlew --no-daemon :engine:test :app:assembleDebug :app:assembleRelease

say "Done! Your APKs:"
find "$ROOT/app/build/outputs/apk" -name '*.apk' -print | sed 's/^/   /'
if [ -z "${PATOOTIE_KEYSTORE_FILE:-}" ]; then
  echo "   (The release APK is signed with your local debug key. See README → Release builds for a real key.)"
fi
echo
echo "   Install on a connected phone with:"
echo "   $ANDROID_HOME/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk"
