# GalgameShell 技术债登记表

> 轻量登记（评估整改 P2-7，2026-10-11）。目的：为散文式「已知取舍」补上编号 / 登记日 / 偿还计划 / 状态，可被追债。
> 状态取值：已登记 / 已计划 / 待偿还 / 已偿还 / **永久跳过**。
> 来源：`engine-equivalence-ui-landed.md` §9、`local/README.internal.md` §2/§7、`box64-wine-launch-chain.md`、本评估报告。

| 编号 | 登记日 | 债项 | 影响 | 偿还计划 / 状态 |
|---|---|---|---|---|
| TD-01 | 2026-10-05 | 双 UI 平行实现：`GalgameLibraryActivity` 与 `GalgameHomeFragment`（历史遗留） | 行为漂移、两处接线成本翻倍 | 已计划：路线图 P8（local/README.internal.md §7，2026-10-11 登记） |
| TD-02 | 2026-10-05 | P6 真机回归 R1–R17 未完成（Adreno + Mali 各一） | 原为发布门禁，**现已解除** | **永久跳过（2026-10-24 决定）**：本机只有 RMX5010（Adreno 830），没有任何 Mali 设备，R1–R17 的 Mali 档永远不可能执行。因此该条**不再作为发布门禁**——发布验收只按 Adreno 覆盖，Mali 兼容性作为"未验证"如实声明，不再以"待偿还"挂账。此后不再在任何状态汇报、评审或任务清单中提及 |
| TD-03 | 2026-10-05 | 索引与容器双真值：删除容器不会自动清索引条目 | 数据残留（containerized 条目不重复展示，影响可控） | 已登记，待设计 |
| TD-04 | 2026-10-05 | 外部播放器不纳入 `GameEngine` 抽象（`NativeRouteLauncher` 照旧） | 三条启动路线仅两条抽象，语义不统一 | 已决策保留（`engine-equivalence-ui-landed.md` §9）；若统一需先解决「第三方无运行时可用性」语义 |
| TD-05 | 2026-10-11 | box64 基线「上游 main 自建」不可精确复现（0.4.5 无上游 commit hash） | 重建不可复现、安全/兼容追溯难 | **部分偿还（2026-10-23）**：0.4.5 基线 hash 已从残留源码树恢复并登记为 `2a062311`（chain §2）；三处私有改动导出为 `tools/box64/box64-galgameshell.patch`（双向校验）；打包器 `tools/prepare_box64_lib.sh` 入库且版本不再写死；gstfix 产物 force-track。**未偿**：`build_box64_android.sh` 端到端一次未跑通，链接三步仍属"手工步骤照抄"状态 |
| TD-06 | 2026-10-11 | 主计划 / 决策全文在仓外（`Desktop/galgame-plan-complete.md` 当前不存在） | 决策引用（RK-06、Plan §）不可仓内解析 | 已登记：`decision-registry.md` 索引兜底；主计划恢复后迁全文 |
| TD-07 | 2026-10-11 | `LocaleHelper.supportedLocales` 增 `zh_CN`（铁律文件历史越界） | 铁律文件存在未登记偏差 | 已登记为 A7 例外（local/README.internal.md §6）；上游原生支持 zh_CN 后回退 |
| TD-08 | 2026-10-11 | 上游改动面未集中登记（21 个 fork 主动改动 + 资源层） | 冲突面 / 审计不可预期 | 已登记：local/README.internal.md §5.1 清单 |
| TD-09 | 2026-10-23 | galgame 过场动画在 Wine 下不出帧（客户区全黑）：SystemNNN / YU-RIS 等走 DirectShow 的引擎 | 这批引擎不可玩；用户可见收益为零 | **不计划偿还**：2026-10-24 已把卡点坐实到消息级别（`box64-wine-launch-chain.md` §7.2）——Wine 内建 quartz 的 `IVideoWindow::put_WindowStyle` → `SetWindowPos` 跨线程 `SendMessage(WM_WINE_SETWINDOWPOS)` 发到视频窗口后**永不返回**，DirectShow 线程永久阻塞，`IMediaControl::Run` 从未被调用（7 份日志 Run 命中 0；`diagA` 里 `MediaControl_Stop` 命中 4，排除"没开通道"）。已排除：解码链（transform 已按 MPEG-1→NV12 建好）、MPEG-2（全库 0 个）、渲染遮挡、容器尺寸、应用侧 X server 卡写。§7/§7.1 对解码链与 MPEG-2 的因果归属均已作废。锁级定位因设备无 root 已决定跳过。偿还条件：上游 Wine 修 quartz 窗口路径，或换可改的 rootfs；应用层不可改 |
| TD-10 | 2026-10-24 | X server 事件发送失败只 `printStackTrace`、不终止连接（`XClient.sendEvent` 与 `EventListener.sendEvent` 各自 catch 后吞掉 IOExcepion） | 写入超时或对端已死后，服务端仍在半坏连接上继续发事件并反复刷栈；X11 协议流已错位却没人踢连接 | **已登记，待设计**：`XConnectorEpoll` 的请求处理路径已有 `catch (IOException) → killConnection(client)`，但事件发送路径绕过了它。修法是从 `XClient` 拿到 connector 引用、发送失败时 kill；需要把 `XServer` 持有的 connector 暴露给 `XClient`，属跨类改动，故未与 `xoutput_stream.c` 的写入修复同批落地 |
| TD-11 | 2026-10-24 | 版本号在 UI 里硬编码三处（`galgame_strings.xml` 的 `galgame_brand_meta` / `galgame_about_version`、`activity_galgame_settings.xml` 的 `TVVersion`），不随 `versionName` 自动更新 | 发 11.3 时界面仍显示 11.2，用户看到的版本与 `app/build.gradle` 不一致；每次发版都可能漏改 | **已登记，待设计**：`BuildConfig` 在本仓未启用（AGP 8 默认关闭、`buildFeatures` 未声明），故不能直接引用 `BuildConfig.VERSION_NAME`。两条候选：① 在 `app/build.gradle` 开 `buildConfig true` 后改为 `${BuildConfig.VERSION_NAME}`；② 运行时用 `PackageManager.getPackageInfo().versionName`，不碰构建配置。本次发版先手工把三处更新到 11.3 止血，结构性修法待选 |
| TD-12 | 2026-10-24 | 虚拟屏 1920x1080 时游戏菜单**点击不激活**（悬停正常）；同一游戏 960x540 在 1280x720 容器下可玩 | 用户无法靠"调大运行环境分辨率"规避问题；默认保持 1280x720 | **调查中，已排除三项**：① 指针坐标——START 会浮出「Main Story」悬停高亮，落点正确；② 消息投递——`+msg` 确认 `WM_MOUSEMOVE`/`WM_LBUTTONDOWN`/`WM_LBUTTONUP` 都进窗口；③ 窗口几何——`+win` 显示主窗口恒为 `(0,0)`＋**满虚拟屏**（1920x1080），不是被裁的小窗。**剩余疑点**：KiriKiri 在满屏窗口里 letterbox 自己的 960x540 画布，命中测试可能按窗口坐标而非画布坐标；下一步得在窗口过程层取按钮消息的 `lParam` 与游戏自己的坐标换算 |

> **积压取舍（2026-10-24）**：TD-01 / TD-03 / TD-04 / TD-05 / TD-06 / TD-10 判定为**可事后解决**，
> 保留为待办积压，不阻塞发布；TD-02 因无对应设备**永久跳过**（见上，不再提及）。
