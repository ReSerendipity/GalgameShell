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
| TD-05 | 2026-10-11 | box64 基线「上游 main 自建」不可精确复现（0.4.5 无上游 commit hash） | 重建不可复现、安全/兼容追溯难 | **部分偿还（2026-10-23）**：0.4.5 基线 hash 已从残留源码树恢复并登记为 `2a062311`（chain §2）；三处私有改动导出为 `tools/box64/box64-galgameshell.patch`（双向校验）；打包器 `tools/prepare_box64_lib.sh` 入库且版本不再写死；gstfix 产物 force-track。**未偿**：`build_box64_android.sh` 端到端一次未跑通，链接三步仍属"手工步骤照抄"状态 |
| TD-06 | 2026-10-11 | 主计划 / 决策全文在仓外（`Desktop/galgame-plan-complete.md` 当前不存在） | 决策引用（RK-06、Plan §）不可仓内解析 | 已登记：`decision-registry.md` 索引兜底；主计划恢复后迁全文 |
| TD-07 | 2026-10-11 | `LocaleHelper.supportedLocales` 增 `zh_CN`（铁律文件历史越界） | 铁律文件存在未登记偏差 | 已登记为 A7 例外（local/README.internal.md §6）；上游原生支持 zh_CN 后回退 |
| TD-08 | 2026-10-11 | 上游改动面未集中登记（21 个 fork 主动改动 + 资源层） | 冲突面 / 审计不可预期 | 已登记：local/README.internal.md §5.1 清单 |
| TD-09 | 2026-10-23 | galgame 过场动画在 Wine 下不出帧（客户区全黑）：SystemNNN / YU-RIS 等走 DirectShow 的引擎 | 这批引擎不可玩；用户可见收益为零 | **不计划偿还**：已证到 `dlls/quartz/mpeg_splitter.c` 的实现边界（`box64-wine-launch-chain.md` §7）。box64 侧 GStreamer 桥已由 #4528 修通并真机验证（`[BOX64]` 报错归零、`wg_parser_create` 正常），但 2026-10-23 普查 86 个媒体文件发现全库 **0 个 MPEG-2**，且一局 MPEG-1 片头在**全链路零报错**下仍全黑 → 真因不在视频解码链，§7 的因果归属已就地更正。仅当上游 Wine 补 MPEG-2 splitter、或换可改的 rootfs、或另立"引擎渲染面"调查线时才可偿 |
