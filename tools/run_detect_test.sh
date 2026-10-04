#!/usr/bin/env bash
# EngineDetector.detect() 的纯 JVM 回归验证（无需 Android SDK / 设备 / 模拟器）
#
# 为什么存在：detect() 是纯 java.io.File 逻辑（无 Android 依赖），
# 因此可以直接用 JDK 编译运行。2026-10-05 修复「Ren'Py 标准布局漏判」时建立，
# 用于防止该修复回归，并守住 KiriKiri > Ren'Py 的优先级裁决。
#
# 用法： bash tools/run_detect_test.sh     （退出码非 0 表示有用例失败）
set -euo pipefail

JDK="C:/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"
SRC="C:/Users/Doro/GalgameShell/app/src/main/java/com/winlator/galgame"
T="C:/Users/Doro/GalgameShell/tools/detect-test"

rm -rf "$T/src" "$T/classes"
mkdir -p "$T/src/com/winlator/galgame" "$T/classes"

# 始终从真实源码复制，保证测的是当前代码（不是快照）
cp "$SRC/EngineDetector.java" "$T/src/com/winlator/galgame/"

"$JDK/bin/javac" -encoding UTF-8 -nowarn \
  -d "$T/classes" \
  "$T/src/com/winlator/galgame/EngineDetector.java" \
  "$T/DetectTest.java"

"$JDK/bin/java" -Dfile.encoding=UTF-8 -cp "$T/classes" DetectTest
