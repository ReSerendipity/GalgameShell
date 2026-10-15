#!/usr/bin/env bash
# 运行时资源 SHA-256 清单生成（v2.9 评估整改，对应《软件供应链安全_Flash报告》P1-3）
#
# 背景：本仓按设计不附带 .tzst / .so（见 README「获取运行时资源」），因此运行时
# 资源的完整性此前无任何核验手段（TD-05：box64 构建不可精确复现）。本脚本在
# 补齐运行时资源、出 APK 之前运行，生成与 APK 同发的 SHA-256 清单；接收方用
# sha256sum -c 核对，堵住"运行时资源无完整性校验"的缺口。
#
# 用法： bash tools/make_runtime_checksums.sh [输出文件] [app/src/main 根目录]
#   缺省输出 = 仓库根 RELEASE-RUNTIME-CHECKSUMS.txt（勿提交，发布时随包分发）
#   缺省扫描根 = 仓库根/app/src/main（assets/*.tzst|*.tar 与 jniLibs/**/*.so）
# 退出码：0=清单已生成；1=无任何运行时资源（先按 README 补齐）或环境缺 sha256sum。
set -euo pipefail

R="$(git rev-parse --show-toplevel)"
OUT="${1:-$R/RELEASE-RUNTIME-CHECKSUMS.txt}"
SRC="${2:-$R/app/src/main}"

command -v sha256sum >/dev/null 2>&1 || { echo "[checksums] FAIL: sha256sum 不在 PATH" >&2; exit 1; }
[ -d "$SRC" ] || { echo "[checksums] FAIL: 扫描根不存在：$SRC" >&2; exit 1; }

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT
find "$SRC/assets" -maxdepth 1 -type f \( -name '*.tzst' -o -name '*.tar' \) -print0 2>/dev/null | sort -z > "$TMP"
find "$SRC/jniLibs" -type f -name '*.so' -print0 2>/dev/null | sort -z >> "$TMP"

TOTAL=$(tr -cd '\0' < "$TMP" | wc -c | tr -d ' ')
if [ "${TOTAL:-0}" -eq 0 ]; then
  echo "[checksums] FAIL: 未找到任何 .tzst/.tar/.so 运行时资源——请先按 README「获取运行时资源」补齐" >&2
  exit 1
fi

{
  echo "# GalgameShell 运行时资源 SHA-256 清单"
  echo "# 生成时间: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo "# 覆盖: assets/*.tzst|*.tar 与 jniLibs/**/*.so（路径相对 app/src/main）"
  echo "# 核验: 解包后 cd app/src/main && sha256sum -c <清单文件>"
  while IFS= read -r -d '' f; do
    h=$(sha256sum -b "$f" | awk '{print $1}')
    echo "$h  ${f#"$SRC"/}"
  done < "$TMP"
} > "$OUT"

echo "[checksums] 已生成 $OUT（$TOTAL 个文件）"
