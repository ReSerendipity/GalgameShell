# 上游回馈清单（UPSTREAM-PR-CHECKLIST）

> 对应 v2.9 评估《开源治理与合规_Flash报告》P1-1「只取不予」：把滞留 fork 的通用修复
> 整理成可向上游 `brunodev85/winlator-app` 提交的 PR 清单，逐项回馈、逐项销账。
> 证据口径：改动要点与 fork 提交号自 `local/README.internal.md` §5.1（该文件仅本地保留，
> 本表自带关键事实，不依赖其可读）。实际提交 PR 需 GitHub 账号与联网操作，由维护者执行；
> 提交后在本表登记 PR 链接与状态。

| # | 候选改动 | fork 提交 | 上游价值 | PR 适配前提 | 状态 |
|---|---|---|---|---|---|
| 1 | `core/FileUtils.java`：缺 `.update-timestamp` 时 NPE 修复 | 1e97eef | 通用健壮性（新装环境必触发） | 无依赖，单文件小 diff | 待提交 |
| 2 | `core/WineUtils.java`：同一 NPE 根因修复 | 1e97eef | 同上（与 #1 同根因，建议合并为一个 PR） | 同上 | 待提交 |
| 3 | `core/ProcessHelper.java`：wine 输出落盘 `wine_exec.log`（原 /dev/null） | b7243c8 | 可诊断性（排障必需） | 上游可能倾向开关化：PR 描述给出默认关闭选项 | 待提交 |
| 4 | `core/LocaleHelper.java`：`supportedLocales` 增 `zh_CN` | bb810ca | 简体中文界面 | 属上游文件（本仓 A7 登记例外）：先开 issue 讨论，上游接受后本仓改动随 rebase 回退 | 待提交（先 issue） |
| 5 | `xenvironment/components/GuestProgramLauncherComponent.java`：libmpshim 条件 LD_PRELOAD | b7243c8 / 45b76b6 | 特定设备（exec-mprotect 限制）启动链修复 | 需说明适用面（39-bit VA 设备），上游可能要求设备探测开关 | 评估中 |

**销账规则**：每项 PR 被上游合并（或明确拒绝并记录理由）后，把「状态」改为
`已合并 <PR 链接>` / `已拒绝：<理由>`；上游吸收后本仓对应改动随 rebase 自然消解。
