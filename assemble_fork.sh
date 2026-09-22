#!/usr/bin/env bash
# assemble_fork.sh — 在 wl-upstream (blobless 上游克隆) 检索完成后执行：
#   1) 删除磁盘上的大二进制资产（.tzst/.tar/.obb/.so/.sf2），它们不会被纳入 fork
#   2) 清理 index 并提交「上游源码基线」(保留上游 git 历史，便于后续 rebase)
#   3) 运行 seed_and_merge.sh 并入 galgame 增量层
#   4) 用本层 README.md / .gitignore 覆盖上游版
#   5) 备份旧 GalgameShell，将完整 fork 移动到 C:/Users/Doro/GalgameShell
set -uo pipefail
UP="C:/Users/Doro/wl-upstream"
LAYER="C:/Users/Doro/GalgameShell"   # 旧增量层（含本层 README/.gitignore）
GIT="git -c http.schannelCheckRevoke=false"
cd "$UP" || exit 1

echo "== 1) 删除大二进制资产 =="
find . -path ./.git -prune -o -type f \( -name '*.tzst' -o -name '*.tar' -o -name '*.obb' -o -name '*.so' -o -name '*.sf2' \) -print -delete 2>/dev/null

echo "== 2) 清理 index 并提交上游源码基线 =="
rm -f .git/index.lock
$GIT rm --cached --ignore-unmatch . >/dev/null 2>&1 || true
$GIT add -A
$GIT -c user.email="doro@local" -c user.name="Doro" \
  commit -q -m "chore: import upstream winlator-app source (routing-agnostic baseline; binaries excluded)" \
  && echo "✅ baseline committed: $(git rev-parse --short HEAD)"
echo "   已纳入文件数: $($GIT ls-files | wc -l)"

echo "== 3) 并入 galgame 增量层 =="
bash "$LAYER/scripts/seed_and_merge.sh" "$UP"

echo "== 4) 用本层 README / .gitignore 覆盖 =="
cp "$LAYER/README.md"    "$UP/README.md"
cp "$LAYER/.gitignore"   "$UP/.gitignore"
$GIT add -A
$GIT -c user.email="doro@local" -c user.name="Doro" \
  commit -q -m "docs: use GalgameShell README and .gitignore" \
  && echo "✅ docs committed"

echo "== 5) 移动到 GalgameShell =="
if [ -d "$LAYER" ]; then mv "$LAYER" "C:/Users/Doro/GalgameShell.bak"; echo "→ 旧增量层备份至 GalgameShell.bak"; fi
mv "$UP" "C:/Users/Doro/GalgameShell"
echo "✅ 完整 fork 已就位: C:/Users/Doro/GalgameShell"
echo "最终纳入文件数: $(git -C 'C:/Users/Doro/GalgameShell' ls-files | wc -l)"
