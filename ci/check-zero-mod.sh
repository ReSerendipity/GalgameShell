#!/usr/bin/env bash
# ci/check-zero-mod.sh — 零改铁律校验（CI `rebase-guard` 与本地共用这一份实现）
#
# 为什么存在：本 fork 基于官方 winlator-app 靠 rebase 同步上游，官方核心文件不得改动。
# 原实现把基线写死成「push 事件字段，取不到就回落 HEAD~1」，在 pull_request 上退化成
# 只比最后一笔，且把 git diff 的失败用 `|| true` 吞成空名单——空名单同样打印 OK。
# 现在按事件取权威基线，基线不可解析就显式失败，绝不回落。
#
# 例外登记不在本文件：见 ci/zero-mod-exceptions.md（改动下列文件前先在那裡核对）。
#
# 用法（本地，仓库根）：
#     ZERO_MOD_BASE=<base sha> bash ci/check-zero-mod.sh
# CI 由 .github/workflows/ci.yml 传入 GH_EVENT / EVENT_BEFORE / PR_BASE_SHA。
set -euo pipefail

cd "$(git rev-parse --show-toplevel)" || exit 1

# 只锁官方真实路径（避免误伤 ci/stubs 里的同名桩）
FORBIDDEN='^app/src/main/java/com/winlator/container/Container\.java$|^app/src/main/java/com/winlator/core/LocaleHelper\.java$|^app/src/main/res/values/strings\.xml$'

BASE="${ZERO_MOD_BASE:-}"
if [ -z "$BASE" ]; then
  case "${GH_EVENT:-}" in
    pull_request) BASE="${PR_BASE_SHA:-}" ;;
    push)         BASE="${EVENT_BEFORE:-}" ;;
    *)            BASE="" ;;
  esac
fi

# 新建分支/标签推送时 before 为全 0；与「空」同等处理
case "$BASE" in
  ""|0000000000000000000000000000000000000000) BASE="" ;;
esac

if [ -z "$BASE" ]; then
  echo "::error::零改守卫无法确定比较基线（事件=${GH_EVENT:-未设置}）。" >&2
  echo "::error::不回落 HEAD~1——那只会覆盖最后一笔。请显式传入 ZERO_MOD_BASE=<base sha>。" >&2
  exit 2
fi

if ! git cat-file -e "${BASE}^{commit}" 2>/dev/null; then
  echo "::error::基线 ${BASE} 在本次检出中不可解析（历史被改写或浅克隆）。" >&2
  echo "::error::请用 fetch-depth: 0 重新检出，或传入可解析的 ZERO_MOD_BASE。" >&2
  exit 2
fi

echo "[zero-mod] 比较范围: ${BASE}..HEAD"

# 不设 `|| true`：git 失败必须让校验失败，不能退化成空名单
CHANGED="$(git diff --name-only "$BASE" HEAD)"

if [ -z "$CHANGED" ]; then
  echo "[zero-mod] 比较范围内无文件变更（空名单，非「已校验通过」）"
  exit 0
fi

echo "$CHANGED"

# 用 here-string 而不是 `printf | grep -q`：grep -q 命中即退出，会让上游 printf 收到
# SIGPIPE，在 `set -o pipefail` 下把「命中」翻转成非零状态——违规反而判为通过。
HIT_COUNT="$(grep -Ec "$FORBIDDEN" <<< "$CHANGED" || true)"

if [ "${HIT_COUNT:-0}" -gt 0 ]; then
  echo "::error::FAIL 零改铁律：${HIT_COUNT} 个官方核心文件在本次范围被修改（violation of zero-modification rule）" >&2
  echo "::error::先核对既有登记：ci/zero-mod-exceptions.md；未登记的官方文件改动一律不接受。" >&2
  grep -En "$FORBIDDEN" <<< "$CHANGED" | sed 's/^/  命中: /' >&2
  exit 1
fi

echo "[zero-mod] OK 零改铁律通过（已比对 $(grep -c '' <<< "$CHANGED") 个变更文件）"
