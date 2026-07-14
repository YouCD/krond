#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
MODULE_DIR="$ROOT/krond_injector"
APK_SRC="$ROOT/krond_app/app/build/outputs/apk/debug/app-debug.apk"
APK_DST="$MODULE_DIR/KrondInjector.apk"
MODULE_PROP="$MODULE_DIR/module.prop"
UPDATE_JSON="$ROOT/update.json"
ZIP_OUT="$ROOT/krond_injector.zip"

cd "$ROOT"

# ── 1. 编译 krond ──
echo "==> 编译 krond..."
cd "$ROOT/krond"
GIT_TAG=$(git describe --tags --always 2>/dev/null || echo "v0.0.0-dev")
VERSION="${GIT_TAG#v}"
LDFLAGS="-X main.Version=$VERSION"
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags "$LDFLAGS" -o "$ROOT/krond_injector/krond" .
echo "==> krond 编译完成 (version=$GIT_TAG)"

# ── 2. 编译 APK ──
echo "==> 编译 APK..."
cd "$ROOT/krond_app"
./gradlew :app:assembleDebug --no-daemon >/dev/null 2>&1
cd "$ROOT"

if [ ! -f "$APK_SRC" ]; then
  echo "!! APK 编译失败: $APK_SRC 不存在" >&2
  exit 1
fi
echo "==> APK 编译完成: $APK_SRC"

# ── 3. 拷贝 APK 到模块目录 ──
cp "$APK_SRC" "$APK_DST"
echo "==> APK 已拷贝到模块目录"

# ── 4. 注入版本号 ──
GIT_TAG=$(git describe --tags --always 2>/dev/null || echo "v0.0.0")
VERSION="${GIT_TAG#v}"
VERSION_CODE=$(git rev-list --count HEAD 2>/dev/null || echo 0)

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
