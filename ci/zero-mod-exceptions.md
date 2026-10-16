# 零改铁律例外登记（入库版）

本文件是**入库、可移植**的例外登记，供任何克隆环境在改动下列官方核心文件之前核对。
此前该登记只存在于被 `.gitignore` 排除的 `docs/`（克隆后为空），改前核对无法执行。

- 校验实现：`ci/check-zero-mod.sh`（CI `rebase-guard` 与本地共用同一份）
- 触发口径：校验比较的是**推送/PR 范围内的改动文件**，因此"历史既有差异"不会被本次范围重新判定
- 基线事实由 git 派生，不靠记忆抄写；重新派生命令见文末

## 受守卫的官方路径

| 路径 | 相对 upstream 的当前状态 | 登记结论 |
|---|---|---|
| `app/src/main/java/com/winlator/container/Container.java` | 与 upstream 一致 | 无例外，任何改动都应拦下 |
| `app/src/main/java/com/winlator/core/LocaleHelper.java` | `supportedLocales` 追加 `"zh_CN"`（+1 −1） | **已登记例外**：简体中文界面所需，`AGENTS.md` 称 A7，源自 b2b9e3e（2026-09-24） |
| `app/src/main/res/values/strings.xml` | ① `app_name` Winlator → GalgameShell 并加一行注释；② 删除条目 `native_resolution` | ① 品牌化所需，源自 1bf0011（2026-09-27），**此前未入库登记**；② **未见登记依据，待维护者确认** |

## 改动规则

1. 触碰上表任一路径前，先在本文件核对是否已有登记；没有登记即视为不接受。
2. 新增例外须在此写明：改动内容、依据的 fork 提交、为什么不能放到 `com.winlator.galgame` 新包或新资源文件里。
3. 已登记的例外在 rebase 时会重新出现于差异中，这是预期结果；若校验把它当新违规拦下，比对口径见上"触发口径"。

## 重新派生上述事实

```bash
git fetch upstream                       # 本机当前基线：3981d86（2026-09-25 fetch，可能已陈旧）
for f in app/src/main/java/com/winlator/container/Container.java \
         app/src/main/java/com/winlator/core/LocaleHelper.java \
         app/src/main/res/values/strings.xml; do
  printf '%-64s ' "$f"
  git diff --quiet upstream/main HEAD -- "$f" && echo SAME || git diff --numstat upstream/main HEAD -- "$f"
done
git log --format='%h %ci %s' upstream/main..HEAD -- app/src/main/res/values/strings.xml
```

> 表中数字是相对**本地 fetch 的 upstream/main**，不是相对上游最新提交。引用本表前先跑上面的 `git fetch`。
