#!/usr/bin/env bash
# 生成 nativeLibraryDir 用的 box64（targetSdk>=29 的 W^X 禁止从 app 数据目录 exec）。
#
# 用法：
#   bash tools/prepare_box64_lib.sh                 # 用 DefaultVersion.BOX64 声明的那份
#   bash tools/prepare_box64_lib.sh 0.4.5-gstfix    # 指定 assets/box64/box64-<ver>.tzst
#   bash tools/prepare_box64_lib.sh --list          # 只列可用版本
#
# 版本号不再写死：缺省值从 app/src/main/java/com/winlator/core/DefaultVersion.java 读，
# 与 APK 里 GuestProgramLauncherComponent 用的 DefaultVersion.BOX64 同一个真源，
# 避免"构建默认"与"打包输入"两处各写一遍、悄悄漂移。
#
# 输出：app/src/main/jniLibs/arm64-v8a/libbox64.so（.gitignore 忽略）
#   注意：这个 .so 决定实际跑的 box64，而不是 tzst 本身。换 tzst 不重跑本脚本 = 没换。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets/box64"
DST="$ROOT/app/src/main/jniLibs/arm64-v8a/libbox64.so"
DECL="$ROOT/app/src/main/java/com/winlator/core/DefaultVersion.java"

list_versions() {
  local f
  for f in "$ASSETS"/box64-*.tzst; do
    [ -e "$f" ] || continue
    printf '  %s\n' "$(basename "$f" | sed 's/^box64-//; s/\.tzst$//')"
  done
}

default_version() {
  # 只认 DefaultVersion.java 里那一行，读不到就硬失败，不猜
  sed -n 's/.*String BOX64 = "\([^"]*\)".*/\1/p' "$DECL" | head -1
}

if [ "${1:-}" = "--list" ]; then
  echo "可用 box64 版本（$ASSETS）："; list_versions
  echo "构建默认（DefaultVersion.BOX64）：$(default_version)"
  exit 0
fi

VER="${1:-$(default_version)}"
[ -n "$VER" ] || { echo "读不到 DefaultVersion.BOX64，且未给版本号参数" >&2; exit 1; }
SRC="$ASSETS/box64-$VER.tzst"

if [ ! -f "$SRC" ]; then
  echo "缺少 $SRC —— 可用版本：" >&2
  list_versions >&2
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# 解包器优先级：GNU tar --zstd → zstd CLI → Windows bsdtar。
# 本机 GNU tar 1.35 没有 zstd 可执行文件，实际走的是 bsdtar 这一路。
# 两个坑：① 探测必须带 --zstd，bsdtar 对 .tzst 不自动开 zstd 过滤器，裸 -tf 会假失败；
#        ② bsdtar 是 Windows 程序，MSYS 形如 /c/... 的路径它读不了，必须 cygpath -w。
BSDTAR="/c/Windows/System32/tar.exe"
if tar --zstd -tf "$SRC" >/dev/null 2>&1; then
  tar --zstd -xf "$SRC" -C "$tmp"
elif command -v zstd >/dev/null 2>&1; then
  zstd -dc "$SRC" | tar -xf - -C "$tmp"
elif [ -x "$BSDTAR" ] && command -v cygpath >/dev/null 2>&1 \
     && "$BSDTAR" --zstd -tf "$(cygpath -w "$SRC")" >/dev/null 2>&1; then
  "$BSDTAR" -C "$(cygpath -w "$tmp")" -x --zstd -f "$(cygpath -w "$SRC")"
else
  echo "需要支持 --zstd 的 tar、zstd CLI，或带 cygpath 的 Windows bsdtar" >&2
  exit 1
fi

bin="$(find "$tmp" -type f -name box64 | head -n1)"
[ -n "$bin" ] || { echo "tzst 内未找到 box64" >&2; exit 1; }

mkdir -p "$(dirname "$DST")"
cp "$bin" "$DST"
chmod +x "$DST"
echo "OK: $DST ($(wc -c < "$DST") bytes)  来源: box64-$VER.tzst"
