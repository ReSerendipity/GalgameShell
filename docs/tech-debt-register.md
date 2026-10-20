# GalgameShell 技术债登记表

> 轻量登记（评估整改 P2-7，2026-10-11）。目的：为散文式「已知取舍」补上编号 / 登记日 / 偿还计划 / 状态，可被追债。
> 状态取值：已登记 / 已计划 / 待偿还 / 已偿还。
> 来源：`engine-equivalence-ui-landed.md` §9、`local/README.internal.md` §2/§7、`box64-wine-launch-chain.md`、本评估报告。

| 编号 | 登记日 | 债项 | 影响 | 偿还计划 / 状态 |
|---|---|---|---|---|
| TD-01 | 2026-10-05 | 双 UI 平行实现：`GalgameLibraryActivity` 与 `GalgameHomeFragment`（历史遗留） | 行为漂移、两处接线成本翻倍 | 已计划：路线图 P8（local/README.internal.md §7，2026-10-11 登记） |
| TD-02 | 2026-10-05 | P6 真机回归 R1–R17 未完成（Adreno + Mali 各一） | 发布门禁未过 | 待偿还：需真机设备 |
| TD-03 | 2026-10-05 | 索引与容器双真值：删除容器不会自动清索引条目 | 数据残留（containerized 条目不重复展示，影响可控） | 已登记，待设计 |
| TD-04 | 2026-10-05 | 外部播放器不纳入 `GameEngine` 抽象（`NativeRouteLauncher` 照旧） | 三条启动路线仅两条抽象，语义不统一 | 已决策保留（`engine-equivalence-ui-landed.md` §9）；若统一需先解决「第三方无运行时可用性」语义 |
| TD-05 | 2026-10-11 | box64 基线「上游 main 自建」不可精确复现（0.4.5 无上游 commit hash） | 重建不可复现、安全/兼容追溯难 | 已计划：chain 文档 §2 登记 hash 约定（向前执行）；历史 hash 需人工提供 |
| TD-06 | 2026-10-11 | 主计划 / 决策全文在仓外（`Desktop/galgame-plan-complete.md` 当前不存在） | 决策引用（RK-06、Plan §）不可仓内解析 | 已登记：`decision-registry.md` 索引兜底；主计划恢复后迁全文 |
| TD-07 | 2026-10-11 | `LocaleHelper.supportedLocales` 增 `zh_CN`（铁律文件历史越界） | 铁律文件存在未登记偏差 | 已登记为 A7 例外（local/README.internal.md §6）；上游原生支持 zh_CN 后回退 |
| TD-08 | 2026-10-11 | 上游改动面未集中登记（21 个 fork 主动改动 + 资源层） | 冲突面 / 审计不可预期 | 已登记：local/README.internal.md §5.1 清单 |
