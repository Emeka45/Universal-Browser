#!/bin/bash

set -e

source common.sh
set_keys

export VERSION=$(grep -m1 -o '[0-9]\+\(\.[0-9]\+\)\{3\}' vanadium/args.gn)
export CHROMIUM_SOURCE=https://chromium.googlesource.com/chromium/src.git
export DEBIAN_FRONTEND=noninteractive

echo "========================================"
echo "Universal Browser ARM64 Build"
echo "Version: $VERSION"
echo "========================================"

sudo apt-get update

sudo apt-get install -y \
  sudo \
  lsb-release \
  file \
  nano \
  git \
  curl \
  python3 \
  python3-pillow \
  imagemagick \
  librsvg2-bin

if [ ! -d "depot_tools" ]; then
  git clone --depth 1 \
    https://chromium.googlesource.com/chromium/tools/depot_tools.git
fi

export PATH="$PWD/depot_tools:$PATH"

mkdir -p chromium/src/out/Default
cd chromium/src

git init

if ! git remote get-url origin >/dev/null 2>&1; then
  git remote add origin "$CHROMIUM_SOURCE"
fi

git fetch \
  --depth 1 \
  "$CHROMIUM_SOURCE" \
  +refs/tags/$VERSION:chromium_$VERSION

git checkout "$VERSION"

cp "$SCRIPT_DIR/.gclient" ../.gclient

rm -rf "$SCRIPT_DIR/vanadium/patches/"*trichrome-{apk-build-targets,browser-apk-targets}.patch
rm -rf "$SCRIPT_DIR/vanadium/patches/"*{detailed,supported}-language*.patch
rm -rf "$SCRIPT_DIR/vanadium/patches/"*javascript-optimizer-{site-setting,settings-UI}.patch
rm -rf "$SCRIPT_DIR/vanadium/patches/"*component-updates.patch
rm -rf "$SCRIPT_DIR/vanadium/patches/"*{pdf,PDF,for-content-public,toolbar-button,configs-from-config-app,new-tab-card,predictive-back*}*.patch

replace "$SCRIPT_DIR/vanadium/patches" "VANADIUM" "TITANIUM"
replace "$SCRIPT_DIR/vanadium/patches" "Vanadium" "Titanium"
replace "$SCRIPT_DIR/vanadium/patches" "vanadium" "titanium"

git am --whitespace=nowarn --keep-non-patch \
  "$SCRIPT_DIR/vanadium/patches/"*.patch

gclient sync -D --no-history --nohooks
gclient runhooks

./build/install-build-deps.sh --no-prompt

source "$SCRIPT_DIR/patch.sh"

cp "$SCRIPT_DIR/args.gn" out/Default/args.gn

cat >> out/Default/args.gn <<'EOF'

# Universal Browser ARM64-only build
target_os = "android"
target_cpu = "arm64"

# Build optimizations
is_component_build = false
symbol_level = 0
android_static_analysis = "off"

EOF

gn gen out/Default

mkdir -p out/release

echo "========================================"
echo "Building ARM64 APK..."
echo "========================================"

autoninja -C out/Default chrome_public_apk

APK=$(find out/Default/apks -type f -name 'Chrome*.apk' | head -n 1)

if [ -z "$APK" ]; then
  echo "ERROR: ARM64 APK was not produced."
  exit 1
fi

export PATH="$PWD/third_party/jdk/current/bin/:$PATH"
export ANDROID_HOME="$PWD/third_party/android_sdk/public"

echo "========================================"
echo "Signing APK..."
echo "========================================"

sign_apk \
  "$APK" \
  "out/release/$VERSION-arm64-v8a.apk"

if [ ! -f "out/release/$VERSION-arm64-v8a.apk" ]; then
  echo "ERROR: Signed APK was not created."
  exit 1
fi

echo "========================================"
echo "BUILD SUCCESSFUL"
echo "========================================"

ls -lh "out/release/$VERSION-arm64-v8a.apk"

rm -rf "$SCRIPT_DIR/keys"