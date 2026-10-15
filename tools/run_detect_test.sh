#!/usr/bin/env bash
# EngineDetector.detect() 的纯 JVM 回归验证（无需 Android SDK / 设备 / 模拟器）
#
# 为什么存在：detect() 是纯 java.io.File 逻辑（无 Android 依赖），
# 因此可以直接用 JDK 编译运行。2026-10-05 修复「Ren'Py 标准布局漏判」时建立，
# 用于防止该修复回归，并守住 KiriKiri > Ren'Py 的优先级裁决。
#
# 用法： bash tools/run_detect_test.sh     （退出码非 0 表示有用例失败）
set -euo pipefail

# 根路径由 git 推导，不写死用户主目录，换机器/换克隆位置依旧可跑
R="$(git rev-parse --show-toplevel)"
SRC="$R/app/src/main/java/com/winlator/galgame"
T="$R/tools/detect-test"

# javac/java 查找口径与 ci/pre-push:17-21 一致：先查 PATH，再回退 $JAVA_HOME/bin
JAVAC=""
JAVA=""
if command -v javac >/dev/null 2>&1 && command -v java >/dev/null 2>&1; then
    JAVAC="javac"
    JAVA="java"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
fi

if [ -z "$JAVAC" ]; then
    echo "[run_detect_test] 未找到 javac/java（PATH 与 JAVA_HOME 都没有），无法运行测试" >&2
    exit 1
fi

rm -rf "$T/src" "$T/classes"
mkdir -p "$T/src/com/winlator/galgame" "$T/classes"

# 始终从真实源码复制，保证测的是当前代码（不是快照）
cp "$SRC/EngineDetector.java" "$T/src/com/winlator/galgame/"

"$JAVAC" -encoding UTF-8 -nowarn \
  -d "$T/classes" \
  "$T/src/com/winlator/galgame/EngineDetector.java" \
  "$T/DetectTest.java"

"$JAVA" -Dfile.encoding=UTF-8 -cp "$T/classes" DetectTest
