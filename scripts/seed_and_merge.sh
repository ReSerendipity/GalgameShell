#!/usr/bin/env bash
# seed_and_merge.sh — 把 GalgameShell 增量层并入 winlator-app 上游克隆。
# 用法（在本机直连网络下，上游克隆成功后执行）：
#   bash scripts/seed_and_merge.sh <上游克隆目录>
#
# 设计铁律（Plan §VIII.4）：本脚本只复制 galgame 专属新增文件，
# 绝不触碰 Container.java / LocaleHelper.supportedLocales / 官方 strings.xml 现有条目。
set -euo pipefail

UP="${1:-}"
SELF="$(cd "$(dirname "$0")/.." && pwd)"   # GalgameShell 仓库根

if [ -z "$UP" ] || [ ! -d "$UP/app/src/main" ]; then
  echo "❌ 用法: bash scripts/seed_and_merge.sh <上游克隆目录>" >&2
  echo "   上游目录需含 app/src/main（即 winlator-app 克隆根）" >&2
  exit 1
fi

echo "→ 上游: $UP"
echo "→ 本层: $SELF"

# 1) 复制唯一路径的 galgame 增量文件
mkdir -p "$UP/app/src/main/assets" \
         "$UP/app/src/main/res/values" \
         "$UP/app/src/main/java/com/winlator/galgame" \
         "$UP/.github/workflows"

cp "$SELF/app/src/main/assets/engine_presets.json"        "$UP/app/src/main/assets/"
cp "$SELF/app/src/main/res/values/galgame_strings.xml"    "$UP/app/src/main/res/values/"
cp -r "$SELF/app/src/main/java/com/winlator/galgame/."    "$UP/app/src/main/java/com/winlator/galgame/"
cp "$SELF/.github/workflows/ci.yml"                       "$UP/.github/workflows/"

# 2) README / .gitignore：保留上游原版，不覆盖（避免丢失上游信息）
echo "→ 保留上游 README/.gitignore（如需用本层版请手动 cp）"

# 3) 提交增量层
cd "$UP"
git add -A
if git diff --cached --quiet; then
  echo "ℹ️ 无新增改动，跳过提交"
else
  git -c user.email="doro@local" -c user.name="Doro" \
      commit -q -m "GalgameShell: 并入增量层（engine_presets / galgame_strings / com.winlator.galgame 包 / CI）"
  echo "✅ 已提交 galgame 增量层"
fi

echo "下一步: git remote add origin <你的 GitHub fork URL>; git push -u origin main"
echo "之后持续接收官方更新: git fetch upstream && git rebase upstream/main"
