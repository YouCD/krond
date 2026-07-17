#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
MODULE_DIR="$ROOT/krond_injector"
APK_SRC="$ROOT/krond_app/app/build/outputs/apk/release/app-release.apk"
APK_DST="$MODULE_DIR/KrondInjector.apk"
MODULE_PROP="$MODULE_DIR/module.prop"
UPDATE_JSON="$ROOT/update.json"
ZIP_OUT="$ROOT/krond_injector.zip"

cd "$ROOT"

# ── 0. 提取版本号（供后续所有步骤使用） ──
GIT_TAG="${1:-}"
if [ -z "$GIT_TAG" ]; then
  GIT_TAG=$(git describe --tags --always 2>/dev/null || echo "v0.0.0-dev")
fi
VERSION="${GIT_TAG#v}"
VERSION_CODE=$(git rev-list --count HEAD 2>/dev/null || echo 0)
echo "==> 版本: $GIT_TAG ($VERSION), versionCode=$VERSION_CODE"

# ── 1. 编译 krond（注入 version 到 Go 二进制） ──
# 优先使用 Android NDK 交叉编译（自动适配 DNS/TLS），否则回退纯静态编译
NDK_HOME="${ANDROID_NDK_HOME:-}"
if [ -z "$NDK_HOME" ]; then
  for d in "$ANDROID_HOME/ndk"/* "$HOME/Android/Sdk/ndk"/*; do
    [ -d "$d" ] && NDK_HOME="$d" && break
  done
fi

CC=""
TARGET=""
if [ -n "$NDK_HOME" ]; then
  CC_FOUND=$(ls "$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang" 2>/dev/null | head -1)
  if [ -n "$CC_FOUND" ]; then
    CC="$CC_FOUND"
    TARGET="android (NDK $NDK_HOME)"
  fi
fi

echo "==> 编译 krond..."
cd "$ROOT/krond"

LDFLAGS="-X main.Version=$VERSION"
if [ -n "${GH_RELEASE:-}" ]; then
  LDFLAGS="$LDFLAGS -X main.GithubToken=$GH_RELEASE"
fi

if [ -n "$CC" ]; then
  CGO_ENABLED=1 GOOS=android GOARCH=arm64 CC="$CC" \
    go build -ldflags "$LDFLAGS" \
    -o "$ROOT/krond_injector/krond" .
  echo "==> krond 编译完成 (version=$VERSION, target=$TARGET)"
else
  echo "==> NDK 未找到，使用静态编译（纯 Go DNS/TLS）"
  CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags "$LDFLAGS" \
    -o "$ROOT/krond_injector/krond" .
  echo "==> krond 编译完成 (version=$VERSION, 静态)"
fi

# ── 2. 编译 APK（注入 versionName/versionCode） ──
echo "==> 编译 APK..."
cd "$ROOT/krond_app"
./gradlew :app:assembleRelease --no-daemon \
  -PversionName="$VERSION" -PversionCode="$VERSION_CODE" >/dev/null 2>&1
cd "$ROOT"

if [ ! -f "$APK_SRC" ]; then
  echo "!! APK 编译失败: $APK_SRC 不存在" >&2
  exit 1
fi
echo "==> APK 编译完成: $APK_SRC"

# ── 3. 拷贝 APK 到模块目录 ──
cp "$APK_SRC" "$APK_DST"
echo "==> APK 已拷贝到模块目录"

# ── 4. 注入版本号到 module.prop ──
echo "==> 注入版本: version=$VERSION, versionCode=$VERSION_CODE"

sed -i \
  -e "s/^version=.*/version=$VERSION/" \
  -e "s/^versionCode=.*/versionCode=$VERSION_CODE/" \
  "$MODULE_PROP"

# ── 5. 生成 update.json ──
ZIP_URL="https://github.com/YouCD/krond/releases/download/$GIT_TAG/krond_injector.zip"
CHANGELOG="https://github.com/YouCD/krond/releases/tag/$GIT_TAG"

cat > "$UPDATE_JSON" <<EOF
{
  "version": "$VERSION",
  "versionCode": $VERSION_CODE,
  "zipUrl": "$ZIP_URL",
  "changelog": "$CHANGELOG"
}
EOF

echo "==> update.json 已更新"

# ── 6. 打包模块 ──
rm -f "$ZIP_OUT"
cd "$MODULE_DIR"
zip -r "$ZIP_OUT" . -x "*.log" >/dev/null
cd "$ROOT"

echo "==> 打包完成: $ZIP_OUT ($(du -h "$ZIP_OUT" | cut -f1))"
