#!/usr/bin/env bash
# 三路径启动的 adb 脚本化冒烟（v2.9 评估整改，对应《测试_Flash报告》P1-2）
#
# 定位：把 engine-equivalence-ui-landed.md §8「真机复测手册」的可脚本化部分落成命令，
# 每次出包后一条命令完成 安装 -> 授权 -> 启动 -> 前台断言 的最低限验证；
# 三条启动路线的画面级验证（Kirikiri 原生 / Ren'Py 原生 / Siglus 容器）仍需
# 按手册人工执行（截图 + 像素统计），本脚本不替代 P6 真机门禁 R1-R17。
#
# 用法：
#   bash tools/smoke_e2e_adb.sh [apk 路径]
#   缺省 apk 路径 = app/build/outputs/apk/debug/app-debug.apk
# 前置：adb 在 PATH；恰好一台设备/模拟器在线；APK 需先按 README 补齐运行时资源构建。
# 退出码：0=冒烟通过；1=环境/APK/断言失败。
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="com.winlator"
MAIN="$PKG/.GalgameMainActivity"

die() { echo "[smoke-e2e] FAIL: $*" >&2; exit 1; }

command -v adb >/dev/null 2>&1 || die "adb 不在 PATH（Android platform-tools）"
[ -f "$APK" ] || die "APK 不存在：$APK（先按 README 补齐运行时资源后 ./gradlew assembleDebug）"

DEVICES=$(adb devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')
[ "$DEVICES" -eq 1 ] || die "需要恰好 1 台在线设备（当前 $DEVICES 台）；多台请用 ANDROID_SERIAL 指定"

echo "[smoke-e2e] 1/4 安装 $APK"
adb install -r "$APK" >/dev/null

echo "[smoke-e2e] 2/4 预授权 MANAGE_EXTERNAL_STORAGE（免首启弹窗）"
adb shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || echo "  (appops 不支持，跳过——首启手工授权)"

echo "[smoke-e2e] 3/4 冷启动 GalgameMainActivity"
adb shell am force-stop "$PKG"
adb shell am start -W -n "$MAIN" >/dev/null

echo "[smoke-e2e] 4/4 断言前台 Activity"
sleep 3
FOCUS=$(adb shell dumpsys window 2>/dev/null | grep -m1 mCurrentFocus || true)
echo "  focus: $FOCUS"
case "$FOCUS" in
  *GalgameMainActivity*) echo "[smoke-e2e] PASS：启动即游戏库，前台断言通过" ;;
  *) die "前台不是 GalgameMainActivity（见上方 focus 输出）" ;;
esac

cat <<'NEXT'
[smoke-e2e] 后续人工步骤（三路径画面级回归，见 docs/engine-equivalence-ui-landed.md §8）：
  a. Kirikiri 原生：导入含 data.xp3 的游戏 -> 应直启 KR2Activity
  b. Ren'Py 原生：tools/renpy-testgame（make_art.py 生成）-> 应直启 RenPyActivity
  c. Siglus 容器：导入 HANA9cn.EXE 类游戏 -> 应走 XServerDisplayActivity
NEXT
