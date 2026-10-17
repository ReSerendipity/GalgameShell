#!/usr/bin/env bash
# ci/check-hotspot-sync.sh — 热点入口清单唯一性巡检（返回 clean / gap）
#
# 为什么存在：清单此前同时硬编码在 AGENTS.md、README.md、.github/workflows/ci.yml 与
# ci/pre-push 四处，而 AGENTS.md 只声明「两处必须同步修改」——按它改完仍会留下陈旧副本，
# 本地照文档跑绿不再等价于 CI 跑绿。现在清单只有一个来源，本脚本证明「没有第二份拷贝」。
#
# 用法（仓库根）： bash ci/check-hotspot-sync.sh
set -uo pipefail

cd "$(git rev-parse --show-toplevel)" || exit 1

LIST="ci/hotspot-entries.txt"
LIST_CONSUMERS=".github/workflows/ci.yml ci/pre-push scripts/gen_galgame_stubs.py AGENTS.md README.md"
# 不得再出现具体热点文件名的地方（清单文件本身除外）
HARD_CODE_TARGETS=".github/workflows/ci.yml ci/pre-push AGENTS.md README.md"

GAPS=0
say_gap() { echo "GAP: $*"; GAPS=$((GAPS + 1)); }

if [ ! -f "$LIST" ]; then
  echo "GAP: 清单文件不存在：$LIST"
  exit 1
fi

# 1) 清单本身：有有效条目、无重复、每条都指向真实文件
ENTRIES="$(grep -vE '^[[:space:]]*(#|$)' "$LIST" || true)"
if [ -z "$ENTRIES" ]; then
  say_gap "$LIST 没有有效条目（全是注释或空行），类型门禁将编不出热点类却看起来「在跑」"
fi

DUPES="$(printf '%s\n' "$ENTRIES" | sort | uniq -d || true)"
[ -n "$DUPES" ] && say_gap "清单存在重复条目：$(printf '%s ' $DUPES)"

COUNT=0
for entry in $ENTRIES; do
  COUNT=$((COUNT + 1))
  [ -f "$entry" ] || say_gap "清单条目指向不存在的文件：$entry"
done

# 2) 消费者必须引用清单，而不是自带副本
#    只强制**入库文件**：AGENTS.md 按 2026-10-13 的决定不入库（.gitignore 排除），
#    若把它当必需消费者，陌生克隆上会因为「文件不存在」而报 gap——那是假红，不是漂移。
SKIPPED=""
for f in $LIST_CONSUMERS; do
  if ! git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
    SKIPPED="$SKIPPED $f"
    echo "SKIP（未入库，不作强制）: $f"
    continue
  fi
  refs="$(grep -c "$LIST" "$f" || true)"
  [ "${refs:-0}" -gt 0 ] || say_gap "$f 未引用唯一来源 $LIST（可能仍在硬编码或已脱钩）"
done

# 3) 除清单文件外，入库消费者不得再硬编码具体热点文件名
for entry in $ENTRIES; do
  base="$(basename "$entry")"
  for f in $HARD_CODE_TARGETS; do
    git ls-files --error-unmatch "$f" >/dev/null 2>&1 || continue
    hits="$(grep -c -- "$base" "$f" || true)"
    [ "${hits:-0}" -eq 0 ] || say_gap "$f 硬编码了热点文件名 $base（$hits 处）；清单只应出现在 $LIST"
  done
done

# 4) 生成脚本的扫描排除项必须是清单成员（防止排除名单随着清单演化而失效）
EXCLUDED="$(grep -oE '"[A-Za-z0-9_]+\.java"' scripts/gen_galgame_stubs.py || true)"
for q in $EXCLUDED; do
  name="$(tr -d '"' <<< "$q")"
  [ "$name" = "R.java" ] && continue
  if [ "$COUNT" -gt 0 ]; then
    inlist="$(grep -c -- "/$name\$" "$LIST" || true)"
    [ "${inlist:-0}" -gt 0 ] || say_gap "gen_galgame_stubs.py 排除了 $name，但它不在 $LIST 中——排除名单已失效，请更新"
  fi
done

if [ "$GAPS" -gt 0 ]; then
  echo "[hotspot-sync] ✗ 发现 $GAPS 个 gap"
  exit 1
fi
if [ -n "$SKIPPED" ]; then
  echo "[hotspot-sync] ✓ clean：清单 $COUNT 条、$LIST 为唯一来源、无硬编码副本、排除名单仍有效（未入库而跳过:$SKIPPED）"
else
  echo "[hotspot-sync] ✓ clean：清单 $COUNT 条、$LIST 为唯一来源、无硬编码副本、排除名单仍有效"
fi
