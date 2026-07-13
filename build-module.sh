#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
MODULE_DIR="$ROOT/crond_injector"
APK_SRC="$ROOT/cron_app/app/build/outputs/apk/debug/app-debug.apk"
APK_DST="$MODULE_DIR/CrondInjector.apk"
MODULE_PROP="$MODULE_DIR/module.prop"
UPDATE_JSON="$ROOT/update.json"
ZIP_OUT="$ROOT/crond_injector.zip"

cd "$ROOT"

# ── 1. 编译 APK ──
echo "==> 编译 APK..."
cd "$ROOT/cron_app"
./gradlew :app:assembleDebug >/dev/null 2>&1
cd "$ROOT"

if [ ! -f "$APK_SRC" ]; then
  echo "!! APK 编译失败: $APK_SRC 不存在" >&2
  exit 1
fi
echo "==> APK 编译完成: $APK_SRC"

# ── 2. 拷贝 APK 到模块目录 ──
cp "$APK_SRC" "$APK_DST"
echo "==> APK 已拷贝到模块目录"

# ── 3. 注入版本号 ──
GIT_TAG=$(git describe --tags --always 2>/dev/null || echo "v0.0.0")
VERSION="${GIT_TAG#v}"
VERSION_CODE=$(git rev-list --count HEAD 2>/dev/null || echo 0)

echo "==> 注入版本: version=$VERSION, versionCode=$VERSION_CODE"

sed -i \
  -e "s/^version=.*/version=$VERSION/" \
  -e "s/^versionCode=.*/versionCode=$VERSION_CODE/" \
  "$MODULE_PROP"

# ── 4. 生成 update.json（仅本地打包用，CI 会覆盖） ──
ZIP_URL="https://github.com/YouCD/cron_manager/releases/download/$GIT_TAG/crond_injector.zip"
CHANGELOG="https://github.com/YouCD/cron_manager/releases/tag/$GIT_TAG"

cat > "$UPDATE_JSON" <<EOF
{
  "version": "$VERSION",
  "versionCode": $VERSION_CODE,
  "zipUrl": "$ZIP_URL",
  "changelog": "$CHANGELOG"
}
EOF

echo "==> update.json 已更新"

# ── 5. 打包模块 ──
rm -f "$ZIP_OUT"
cd "$MODULE_DIR"
zip -r "$ZIP_OUT" . -x "crond/crond.log" >/dev/null
cd "$ROOT"

echo "==> 打包完成: $ZIP_OUT ($(du -h "$ZIP_OUT" | cut -f1))"
