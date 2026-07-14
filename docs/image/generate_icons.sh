#!/usr/bin/env bash
#
# 生成 App 启动图标前景层 (ic_launcher_foreground.png)
#
# 流程：
#   1. 用 rsvg-convert 把 icon_circle.svg 渲染为 432x432 PNG（含绿色圆环 + 深色时钟表盘）
#   2. 用 ImageMagick 将图标整体缩进到自适应图标「安全区」内（绿圆半径 ≈35.5dp < 36dp），
#      避免被系统图标遮罩裁掉绿环；背景层仍由 ic_launcher_background.xml 的绿色兜底。
#
# 用法：
#   cd docs/image && ./generate_icons.sh
#
# 依赖：rsvg-convert (librsvg)、magick (ImageMagick 7)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SRC="$SCRIPT_DIR/icon_circle.svg"
TMP="$(mktemp --suffix=.png)"
OUT="$REPO_ROOT/krond_app/app/src/main/res/drawable/ic_launcher_foreground.png"

if ! command -v rsvg-convert >/dev/null; then
  echo "缺少 rsvg-convert (librsvg)，请先安装" >&2
  exit 1
fi
if ! command -v magick >/dev/null; then
  echo "缺少 magick (ImageMagick 7)，请先安装" >&2
  exit 1
fi

echo "渲染 SVG -> $TMP"
rsvg-convert -w 432 -h 432 "$SRC" -o "$TMP"

echo "缩进安全区 -> $OUT"
magick "$TMP" -resize 285x285 -background none -gravity center -extent 432x432 "$OUT"

rm -f "$TMP"
echo "完成。背景层(绿色)为 krond_app/app/src/main/res/drawable/ic_launcher_background.xml"
