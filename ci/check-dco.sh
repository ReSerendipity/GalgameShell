#!/usr/bin/env bash
# ci/check-dco.sh — 逐提交 DCO 签署校验（本地落地路径 + CI pull_request 共用口径）
#
# 为什么存在：docs/CONTRIBUTING.md 声明「所有提交必须带 Signed-off-by」，但唯一的执行体挂在
# pull_request 事件上，而本仓提交实际经直推 main 落地（主机 ci.yml 运行全部为 push 事件），
# 于是这道闸没有任何流量经过。本脚本把同一检查放到提交真正走的那条路上。
#
# 用法（仓库根）：
#     bash ci/check-dco.sh                       # 自动确定范围
#     DCO_BASE=<sha> bash ci/check-dco.sh        # 显式范围 <sha>..HEAD
#     REMOTE_SHA=<oid> LOCAL_SHA=<oid> bash ci/check-dco.sh   # 钩子调用形式
# 无法确定范围时以非零码退出，**不静默放行**。
set -uo pipefail

cd "$(git rev-parse --show-toplevel)" || exit 1

ZERO="0000000000000000000000000000000000000000"
RANGE=""
WHY=""

if [ -n "${DCO_BASE:-}" ]; then
  RANGE="${DCO_BASE}..${DCO_HEAD:-HEAD}"; WHY="DCO_BASE"
elif [ -n "${LOCAL_SHA:-}" ] && [ "${REMOTE_SHA:-}" != "$ZERO" ] && [ -n "${REMOTE_SHA:-}" ]; then
  RANGE="${REMOTE_SHA}..${LOCAL_SHA}"; WHY="pre-push 钩子参数"
elif [ -n "${LOCAL_SHA:-}" ]; then
  echo "::error::新分支推送（远端 OID 为全 0），无法确定逐提交范围。请传 DCO_BASE=<分叉点 sha> 后重试。" >&2
  exit 2
elif UP="$(git rev-parse --abbrev-ref '@{upstream}' 2>/dev/null)"; then
  RANGE="${UP}..HEAD"; WHY="上游跟踪分支 $UP"
fi

if [ -z "$RANGE" ]; then
  echo "::error::DCO 检查无法确定提交范围（无 DCO_BASE、无钩子参数、无上游跟踪分支）。" >&2
  echo "::error::本脚本不静默放行：请显式传入 DCO_BASE=<sha>。" >&2
  exit 2
fi

if ! git rev-parse --verify "${RANGE}" >/dev/null 2>&1 && ! git rev-list -n 1 "$RANGE" >/dev/null 2>&1; then
  echo "::error::范围 ${RANGE} 不可解析（历史被改写或浅克隆）。" >&2
  exit 2
fi

TOTAL=0
UNSIGNED=""
MERGES=0
# --no-merges：合并提交（尤其 GitHub 为 pull_request 合成的 refs/pull/N/merge）永远不带
# trailer，把它算进来看守结果会让每个 PR 无故变红。内容提交仍逐笔校验，且被跳过的合并数
# 显式打印，不做静默豁免。
for sha in $(git rev-list --no-merges "$RANGE"); do
  TOTAL=$((TOTAL + 1))
  if ! git log -1 --format=%B "$sha" | grep -E -c '^[[:space:]]*Signed-off-by:' >/dev/null 2>&1; then
    UNSIGNED="${UNSIGNED}$(git log -1 --format='  %h %s' "$sha")
"
  fi
done
MERGES="$(git rev-list --merges --count "$RANGE")"

if [ "$TOTAL" -eq 0 ]; then
  echo "[dco] 范围 ${RANGE} 内 0 笔提交（无事可查，非「已校验通过」）"
  exit 0
fi

if [ -n "$UNSIGNED" ]; then
  echo "::error::DCO：${TOTAL} 笔内容提交（另跳过 ${MERGES} 笔合并提交）中有缺签署者（范围来自 ${WHY}）" >&2
  printf '%s' "$UNSIGNED" >&2
  echo "::error::补签署：git commit -s（新提交）；已有提交用 rebase 补签，或与维护者确认豁免口径。" >&2
  exit 1
fi

echo "[dco] ✓ ${TOTAL} 笔内容提交全部带 Signed-off-by（另跳过 ${MERGES} 笔合并提交；范围 ${RANGE}，来自 ${WHY}）"
