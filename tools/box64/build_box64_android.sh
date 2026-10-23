#!/usr/bin/env bash
# 构建 GalgameShell 自带的 box64（glibc/arm64，供 Winlator 式 rootfs 使用）。
#
# 为什么要自建：官方 winlator-app 只带 box64-0.4.4.tzst；本机启动链依赖
# 上游 main 的 custommem 修复 + tools/box64/box64-galgameshell.patch 的三处私有改动
# （39-bit VA 死循环、dl* 自插环）。配方与背景见 docs/box64-wine-launch-chain.md。
#
# 用法：
#   BOX64_SRC=... NDK=... GLSYSROOT=... ./build_box64_android.sh <upstream-ref> <version-label>
# 例：
#   ... ./build_box64_android.sh abfb8c3b 0.4.6
#
# 产物：app/src/main/assets/box64/box64-<version-label>.tzst（内部 ./usr/local/bin/box64）
# 不覆盖既有 tzst，便于在应用里按容器回退版本。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
: "${BOX64_SRC:?需要 BOX64_SRC=box64 源码 checkout 路径}"
: "${NDK:?需要 NDK=Android NDK 根目录（配方要求 27.0.12077973）}"
: "${GLSYSROOT:?需要 GLSYSROOT=Debian arm64 glibc sysroot 路径}"
REF="${1:?需要上游 ref（commit/branch）作为构建基线}"
VER="${2:?需要版本号标签，如 0.4.6}"

CLANG="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
LLVM_STRIP="$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe"
[ -x "$CLANG" ] || { echo "找不到 clang：$CLANG" >&2; exit 1; }
[ -d "$GLSYSROOT/lib" ] || { echo "sysroot 缺 lib/：$GLSYSROOT" >&2; exit 1; }

cd "$BOX64_SRC"
# 本脚本会 checkout/apply，绝不在带未提交改动的树上跑——那会吃掉别人的现场（补丁已另存于
# tools/box64/，但工作树里的其它改动没有）。要构建就指向一棵干净 checkout。
if [ -n "$(git status --porcelain)" ]; then
  echo "$BOX64_SRC 有未提交改动，已停止。请清理/换一棵干净 checkout 再构建。" >&2
  git status --short >&2
  exit 1
fi
git fetch --quiet origin
git checkout --quiet --detach "$REF"

# 私有改动必须先落到干净树上；冲突就停，不要静默丢补丁
if git apply --check "$ROOT/tools/box64/box64-galgameshell.patch" 2>/dev/null; then
  git apply "$ROOT/tools/box64/box64-galgameshell.patch"
  echo "已重放 box64-galgameshell.patch"
else
  echo "补丁无法干净重放到 $REF —— 需要人工合并，已停止（不自动三方合并）" >&2
  exit 1
fi

# 工具链文件按环境生成，绝不把机器绝对路径写进仓库
TOOLCHAIN="$BOX64_SRC/.galgameshell-toolchain.cmake"
cat > "$TOOLCHAIN" <<EOF
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_C_COMPILER "$CLANG")
set(CMAKE_C_COMPILER_TARGET aarch64-linux-gnu)
set(CMAKE_ASM_COMPILER "$CLANG")
set(CMAKE_ASM_COMPILER_TARGET aarch64-linux-gnu)
set(CMAKE_SYSROOT "$GLSYSROOT")
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
EOF

BUILD="$BOX64_SRC/build-$VER"
cmake -G Ninja -B "$BUILD" -S "$BOX64_SRC" \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DARM_DYNAREC=ON -DBOX32=ON -DWINLATOR_GLIBC=ON

# 链接期已知坑：sysroot 里没有 gcc 的 libgcc/crt 配套，ninja 默认链接命令会失败。
# 先正常试链接；失败就把命令交回人工，不猜参数。
if ! cmake --build "$BUILD" --target box64; then
  cat >&2 <<'EOF'
box64 目标链接失败。历史构建同样在此卡住（见 docs/box64-wine-launch-chain.md §2）。
请从上面的日志里取出 link 命令，补上 sysroot 的 crt/libgcc 后手工完成，
再把产物路径回填到本脚本的 STRIP 步骤。不要改用 NDK bionic 目标硬绕——
rootfs 是 glibc 的，bionic 产物跑不起来。
EOF
  exit 2
fi

BIN="$BUILD/box64"
"$LLVM_STRIP" --strip-all "$BIN"

OUT="$ROOT/app/src/main/assets/box64/box64-$VER.tzst"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$STAGE/usr/local/bin"
cp "$BIN" "$STAGE/usr/local/bin/box64"
# 结构对齐官方：./usr/local/bin/box64 + zstd
tar -C "$STAGE" -c --zstd -f "$OUT" ./usr/local/bin/box64

echo "== 完成 =="
echo "基线: $(git rev-parse HEAD)  标签: $VER"
echo "产物: $OUT ($(stat -c %s "$OUT") bytes)"
echo "记得把基线 hash 记进 docs/box64-wine-launch-chain.md §2（P2-8 可复现约定）"
