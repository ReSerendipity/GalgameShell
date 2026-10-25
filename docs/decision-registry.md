# GalgameShell 决策与风险编号登记表

> 目的（评估整改 P2-4，2026-10-11）：让代码/文档中引用的决策编号可在仓内解析，实现仓内自洽。
> 仓外主计划 `Desktop/galgame-plan-complete.md` 当前在本机不存在（2026-10-11 探测），完整决策全文无法迁入；
> 本表作为「索引 + 仓内已有摘要」，全文缺失项如实标注，待主计划恢复后补齐。
> 位置口径：下表 `local/README.internal.md` 为对内说明唯一入口（`REPOSITORY_GUIDELINES.md` §8），
> `local/` 整体不入库（`.gitignore` `/local/`），故本表中对内版引用在 fresh clone 中不可解析，属预期。

| 编号 | 主题 | 仓内位置 | 状态 |
|---|---|---|---|
| A1 | 字体策略：仅开源 CJK（IPAex/Noto，OFL）、禁捆绑商业字体、框架不分发 | `local/README.internal.md` §6 / `NOTICE` §4 | 仓内完整 |
| A2 | 每游戏独立容器（隔离 + 导入即玩） | `local/README.internal.md` §6 | 仓内完整 |
| A3 | 导入默认复制（可写），非引用挂载（规避符号链接死结 RK-06） | `local/README.internal.md` §6 | 仓内完整 |
| A4 | B 路由默认唤起不内嵌；Tier-1 开源默认 / Tier-2 闭源显式开启 + 免责 | `local/README.internal.md` §6 / `NOTICE` §5 | 仓内完整 |
| A5 | 加密仅检测 + 提示，不破解 | `local/README.internal.md` §6 | 仓内完整 |
| A6 | LGPL-2.1 公开源码 + `LICENSE`/`NOTICE` + 应用内源码获取（原表述为「fork 公开」，身份定位已由 A8 更新） | `local/README.internal.md` §6 / `NOTICE` §1 | 仓内完整 |
| A7 | `LocaleHelper.supportedLocales` 增 `zh_CN` 为既有例外（提交 `bb810ca`）；上游原生支持后回退 | `local/README.internal.md` §6（2026-10-11 登记） | 仓内完整 |
| A8 | **项目身份：独立项目 + 内嵌 winlator-app 为源码组件**（声明方式同内嵌 Kirikiroid2 / Ren'Py，见 `NOTICE` §1）。GitHub 侧本就不是 fork（`is_fork=false`，独立建仓）；「rebase 同步上游」工作流终结，上游更新改为**按需移植**；整仓保持单许可证 LGPL-2.1（组件部分继承不可更授，增量层自主选择保持一致） | `NOTICE` §1 / `ci/zero-mod-exceptions.md` 头注 / `README` 许可证节（2026-10-25 登记） | 仓内完整 |
| AC-18 | LGPL §6 提供对应源码义务（应用内列出源码地址） | `NOTICE` §1 | 仓内完整 |
| AC-19 | galgame 文案一律走 `galgame_strings.xml` / 新增资源文件，不改默认 `strings.xml` | `app/.../galgame/package-info.java` | 仓内完整 |
| RK-01 | 框架不分发字体二进制（用户自备，仅本地注入不重分发） | `NOTICE` §4 | 仓内完整 |
| RK-06 | 符号链接死结（引用挂载风险，A3 因此选复制） | `local/README.internal.md` §6 A3（摘要）；**完整全文在仓外主计划** | 摘要仓内 / 全文缺失 |
| RK-11 | Siglus / YU-RIS / Artemis / CatSystem2 为「可执行名+扩展名」启发式，待真实样本校准 | `app/.../galgame/EngineDetector.java` 头注释 | 仓内完整 |
| Plan Part I §I.5 | 包边界（galgame 专属包 = 新增价值层） | `package-info.java` / `local/README.internal.md` §3 | 摘要仓内 / 全文仓外 |
| Plan Part III §III.2 | 引擎指纹 magic bytes 方案 | `EngineDetector.java` 头注释 | 摘要仓内 / 全文仓外 |
| Plan Part VIII §VIII.4 | rebase 硬化 / 零改铁律 / CI 门禁 | `local/README.internal.md` §5 / `.github/workflows/ci.yml` | 摘要仓内 / 全文仓外 |

> 处置结论：A/AC/RK 编号中仓内已完整的可直接解析；仅 **RK-06 与 Plan § 引用**的全文缺失——
> 主计划文件恢复后应迁入全文或改写为仓内自述（对应 tech-debt-register.md TD-06）。
