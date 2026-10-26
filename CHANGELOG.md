# Changelog

本文件记录 GalgameShell 的所有重要变更。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本 SemVer](https://semver.org/lang/zh-CN/)。
分类口径：`feat → Added`、`fix → Fixed`、`refactor → Changed`（见
[REPOSITORY_GUIDELINES.md §10.2](https://github.com/ReSerendipity/.github/blob/main/REPOSITORY_GUIDELINES.md)）。

> 说明：本仓发布说明（Release notes）当前由 `gh release create --generate-notes` 自动生成。
> 下条目仅回填可从 git 历史、tag / Release 与版本位溯源的客观事实，不做推测性归因。

## [Unreleased]

## [11.5] - 2026-10-26

### Added

- **JVM 回归扩到分类逻辑**：`tools/run_detect_test.sh` 新增 `FilterTest`（33 例，含 `IconCodec` 6 例）——gameId
  派生与避碰（ASCII / CJK 哈希 / 空名 / -2/-3）、标签规范化（去空白 / 截断 12 字 / 换行）、
  三维过滤判定（含 null 与组合失配）。纯逻辑抽到 `GalgameFilter`，UI 与测试共用同一实现。
- **自定义封面 + exe 图标提取修复**：详情页「维护 → 封面」可**从相册选图**或**恢复自动**
  （索引新增 `cover` 字段，优先级 自定义 > 目录图 > exe 图标；选图先落 `.tmp` 并实际解码一次，
  解不开的不落盘）。图标提取补上 DIB→BMP 兜底（补 `BITMAPFILEHEADER`、`biHeight` 减半去掉
  AND 掩码层、8bpp 调色板偏移），组内候选按字节数降序逐个重试——真机上该 Unity 游戏的
  exe 图标由「一直占位图」变为正常显示；`IconCodec` 抽成纯逻辑并加 6 例 JVM 回归。
- **批量/扫描导入**：导入向导第一步新增「批量扫描导入（多选）」——选一个目录（如 Download）
  用 SAF 扫出候选（顶层有 `.exe` / `.xp3` / `.rpa` 等，或 Ren'Py 的 `game/`；套娃目录下探一层），
  勾选后串行导入并逐条汇报状态。复用同一套 `ImportFlow` / `SafStager` / `GalgameNativeStore`：
  A 路由失败回滚容器、B 路由收纳后登记；批量**不**自动拉起内置引擎，导入完统一回库自己点。
  真机验证：隔离目录里 Unity / Ren'Py / 套娃三个候选全部导入、非游戏目录不入候选；再用
  750MB 真实游戏走单候选路径。

### Fixed

- **换完封面回列表仍显示占位图**：解码完成前同一位置可能已被重绑到另一个 View（两次 bind
  都早于回调，tag 都等于同一条目），旧逻辑只贴给回调持有者，屏上那个永远停在占位图；
  解码完成后让列表按缓存位图重画一次。
- **「继续游玩」按最后启动时间排序**：此前是「列表前 3 个」，名不副实；现在只收真正启动过的
  （`lastPlayedAt > 0`）并按时间倒序，没玩过任何游戏时整段隐藏。分类过滤升级为**三维组合**
  （引擎 + 品类 + 标签 AND，同一维度再点一次取消），筛空时空态显示「没有匹配的游戏」。
- **多选条与删除确认**：多选条加「品类/标签」后六个动作把「已选 N 项」挤成 0 宽、「完成」被截断
  （真机实测）——标签改「删除/移除」、边距收紧、计数给最小宽度，全部回到一行内；
  「彻底删除 / 仅移除」确认弹窗改为**列出游戏名**（此前只报数量，真机复验时因此误删过一批），
  并写明原生游戏的收纳副本也会被删。

## [11.4] - 2026-10-26

### Added

- **按引擎指定 Wine 版本（任务一机制层）**：`engine_presets.json` 新增 `wine_version`
  （Wine identifier）。导入时先核对设备上该版本真装好（`opt/installed-wine/<id>` 目录 +
  `container-pattern-<版本>.tzst` 齐备）才写入容器 data；未装/装坏则回落主 Wine 并写带
  可操作下一步的 logcat 告警，不静默降级。高级设置「环境详情」新增容器实际 Wine 版本行。
- **Wine 安装入口放行**：设置→更多设置里的「Wine 版本 / 安装 / 移除」行原被硬编码的
  `MainActivity.DEBUG_MODE=false` 挡死（逻辑早已写好但用户永远看不到），现同时认本 fork
  的「开发者模式」开关（`galgame_dev_mode`）。
- **导入改用系统文件夹选择器（SAF）**：`ACTION_OPEN_DOCUMENT_TREE` + persistable 授权，
  授权跟所选目录走、不随卸载重装重置（「所有文件访问」会）；所选树先复制进应用私有暂存
  （`files/import-stage`，带进度、失败清半成品、可用空间 <128MB 硬失败、6h 残骸清扫）再走原
  导入管线。手输路径框与快速路径按钮一并移除——输错/输不全是历史失败诱因。
  评估与取证见 [`docs/robustness-assessment-2026-10.md`](docs/robustness-assessment-2026-10.md)。
- **游戏库分类（引擎 / 品类 / 标签）**：主库页顶部 chips 单选过滤——引擎从卡片自动派生
  （只在多于一种引擎时出现）；预置内容品类「视觉小说 / 文字冒险 / 其他」（id 落盘、显示名
  走资源，改名不动数据）；自定义标签（多选、去重、≤12 字）。长按进入多选后新增「品类 / 标签」
  两个动作，可一次给多张卡归类，标签可内联新建；旧容器库页的长按菜单同样接入。索引 JSON
  新增 `genre` / `tags`，旧索引缺字段即「未设置」，向后兼容；筛空时空态文案改为「没有匹配的游戏」。

### Changed

- **导入入口单一化**：游戏库的 FAB / 空态按钮改为打开导入向导，删除库内 254 行「手输路径 +
  自跑一遍 ImportFlow」的重复实现（两套入口会让同一批修复只落到一套）与 8 条随之死掉的字符串。
- **gameId 与容器名一致性**：容器名恒等于 `galgame-<gameId>`（库/详情/存档都靠去前缀反推 id），
  重名避让上移到 id 派生——占用集同时收自有索引与现存容器名，孤儿容器不再能遮住新导入。
- **引擎预设 dxwrapper 分化（任务二）**：`advhd`/`yrg`/`systemnnn`/`artemis`/`cat_system2`/`cmvs`
  改 `wined3d`（其中 `yrg`/`systemnnn`/`artemis` 追加 `ddrawWrapper=cnc-ddraw`）；
  `kirikiri`/`siglus`/`unity`/`bgi`/`unknown` 保持 `dxvk`——前四者真机已验证可玩，改了即回归。
  分化面只覆盖基线里不通的引擎，故无回归面。真机取证：AdvHD 由 TD-17 的静默崩溃变为渲染出
  引擎自身警告框（仍未可玩）；YU-RIS 机制全链路生效但 TD-09 黑屏未变。
  背景、逐引擎依据与未覆盖格子见 [`docs/wine-dxwrapper-tuning.md`](docs/wine-dxwrapper-tuning.md)。

### Fixed

- **「更多设置」底部大黑框**：真机取证定位为 SwissConfirmBar「完成」确认条（原通栏
  纯黑 56dp，与页面对比亮度 18 vs 253）。改纸面版：白底 + 顶部 2dp 墨线 + 黑字；
  修复后同区域亮度 253.9（§16.4）。全站 UI 一致性扫描（库/设置/详情/高级设置/导入）
  无其他黑框残留。
- **导入假成功链（真机事故 D1–D5）**：重装后「所有文件访问」被重置时 `listFiles()` 为 null，
  旧行为检测判 UNKNOWN、复制 0 文件、到启动才报「启动文件缺失」——现 step0 即 fail-fast 并带用户
  去授权页；解压套娃目录在检测与复制两端归一（`EngineDetector.resolveGameRoot`，回归 E15–E21）；
  库条目回填 title/engine；CJK 目录名塌缩改附 4 位哈希后缀；导入失败回滚已建容器（不再留孤儿）；
  复制完成但没定位到启动 exe 时当场告警，不再等启动才报错。
- **SAF 树 API 误用（真机实测）**：树 URI（`/tree/…`）取 documentId 必须用 `getTreeDocumentId`、
  取自身显示名必须先 `buildDocumentUriUsingTree`；误用 `getDocumentId` 会抛
  IllegalArgumentException，表现为所选目录标签退回整串 content:// URI、复制在第一层即失败。
- **真机复验补的三处**：卡片/详情标题优先取索引里的显示名（此前容器卡只显示 ASCII gameId，
  即一串下划线）；确认页文件数改按归一后的游戏根统计（套娃目录此前恒显示 1）；取消/退出导入
  现在会清掉整份暂存副本（此前实测残留 750MB 级），并作废已删的 `source` 以免误报权限问题。
- **数据安全三件（TD-20/21/24）**：①「彻底删除」与详情「从库中移除」现在会删
  `files/galgame-native/<id>` 收纳副本（此前数 GB 静默泄漏），批删校验容器/存档/收纳三项结果并
  汇总未删净数量；②索引改为 `.tmp` + `fsync` + rename 原子写，解析失败先把旧文件隔离成
  `.corrupt-<ts>` 再重建（此前直接覆写，写一半即全库索引损坏、下次 upsert 覆盖丢条目）；
  ③`GalgameSaveManager` 拒绝空 gameId（空串会让存档目录退化成 S: 盘根、跨游戏串档），详情页缺
  gameId 直接退回；便携存档「先搬后链」改为合并不完整就不删源、链接失败把档拷回、同名冲突留 `.bak`。


### Changed

- **游戏库多选操作栏重设计**：第八轮的顶部黑条（临时代码）改为底部 Swiss 纸面操作栏
  ——白底 + 2dp 墨色分隔线 + 无框大写字距文字按钮（彻底删除/仅移除用 Swiss 红），
  与全站纸面语言一致。

### Fixed

- **性能监控 0/N/A（三项根因）**：① FPS 恒 0——帧率窗口仅在窗口映射时解析，HUD 中途
  开启无帧源（galgame 路线现无条件解析+GPU 缓存回填）；② FREQ N/A——sysfs 文件名错误
  （`scaling_cur_frequency`→`scaling_cur_freq`）；③ TEMP/PWR/BAT N/A——电池温度改
  sticky 广播、CPU 温度加上游 CPUStatus 回退、电流属性无效时回退广播 current_now。
  真机 13 项全开逐行识读：13/13 真实数据（FPS 59.8 / FREQ 2.55·3.19GHz / PWR 6.11W /
  TEMP 42.5·28.1°C / BAT 65% 等，§15.1）。

### Added

- **游戏库多选模式**：长按卡片进入（✓ 标记+未选压暗），顶部选择条：计数 / 全选（再点
  清空）/ 批量彻底删除（容器+S:存档+记录，异步逐个排队）/ 批量仅移除记录 / 退出（返回
  键亦可）。桌面快捷方式入口迁至详情页「添加到桌面」。
- 详情页环境标签三态：容器 / 已收纳·应用内部 / 内嵌引擎·引用原目录（提示可迁移）。

### Added

- **导入后可选删除源目录**：导入完成页「返回游戏库」时询问「删除源目录？」（保留 /
  删除）——两条路由（Wine 容器复制、原生收纳）统一生效；应用内部路径不询问。删除失败
  不回滚（副本已完整），仅提示手动清理。
- **原生引擎游戏收入应用内部（健壮性）**：Kirikiroid2 / Ren'Py 路线导入时不再引用用户
  源目录，改为完整复制到应用私有空间 `files/galgame-native/<gameId>`（新
  `GalgameNativeStore`，幂等、带进度回调）——库外的改名/移动/误删不再损坏游戏，KR2
  运行时产物（.preference/files）也留在私有空间。存量引用型游戏保持可玩，详情页新增
  「收入应用内部」手动迁移入口。真机闭环：导入→删源→从库启动 logcat 证实
  `gamePath=…/galgame-native/bosei2 ok=true`（real-device-verification.md §14）。

### Fixed

- **性能显示重复与关不掉（P0）**：用户反馈左上角一直有性能显示、关闭设置后仍在、再开
  会叠出两个（外观不同）。根因：上游 FrameRating（彩色）与 GalgamePerfHud（单色）并存
  ——容器残留 hudMode=FULL 让上游三档浮窗自行显示，且其 update() 每帧把自身拉回
  VISIBLE。修复：galgame 直启路线不再创建上游 FrameRating（上游 shortcut 路线三档语义
  保留），帧计数/GPU 读取与实例解耦。真机层级证据：未开启=无浮窗节点；勾选=单行
  `FPS` 节点；取消=归零（real-device-verification.md §13.1）。
- **设置页「外接控制器」闪退**：无参跳入上游 ExternalControllerBindingsActivity 导致
  loadProfile(0)=null NPE；修复为取首个输入控制配置+default controller 进入，无配置时
  Toast 引导先创建。

### Added

- **游戏内菜单显隐开关**：设置页新增「游戏内菜单」分区，8 个高级项（输入控制/切换全屏/
  任务管理器/活动窗口/放大镜/屏幕效果/画中画/触摸板帮助）默认隐藏、按需开启；永显项=
  触控模式/性能显示/键盘/退出。
- **游戏库长按菜单**：长按卡片 → 启动 / 打开详情 / 添加到桌面快捷方式 / 彻底删除；
  「彻底删除」一键清理游戏文件+S: 独立存档+整个运行环境容器（含「仅移除记录」逃生门）。
- **桌面快捷方式直启**：pin shortcut（封面图标）→ 透明转发 Activity → 详情页自动启动，
  点桌面图标即进游戏，不再走三级菜单。
- **导入进度条**：复制进容器阶段显示红条 + `COPY xx% · 已拷/总量(MB/GB)`，字节级统计、
  200ms 节流。

### Fixed

- **随机启动游戏闪退（P0 回归修复）**：第四轮「FrameRating 无条件创建」把上游潜伏 bug
  暴露成 wine 路线游戏启动必崩——`Box64Utils.extractBinVersion` 4096 分块读 box64 版本串
  时，上游 `ArrayUtils.indexOf` 内层匹配循环缺 `j < length` 边界，"Box64 arm64 v" 前缀
  恰落块尾即 `array[4096]` 越界，主线程 FATAL、整个 App 闪退（真机 test_siglus 取证
  FATAL 堆栈）。修复两层：`indexOf` 补边界（根治，GamepadHandler 同受益）；
  `FrameRating.run` 的版本串读取包 try-catch 降级 "Box64 v?" 并缓存。复测两轮冷启动
  crash buffer 均空。
- **运行时目录自污染拦截**：KR2 的 `getWritablePath()` 会在游戏源目录创建
  `.preference/`、`files/`（内含 XP3 魔数存档封包），再次导入时被误判 KIRIKIRI 入库、
  启动即黑屏挂起（真机库里的「files」「.preference」两条目即此来源）。新增
  `EngineDetector.isRuntimeDirName` 三处拦截：detect 入口、深层下钻、`GameLauncher.launch`
  兜底（旧库残留条目启动时明确提示移除重导）。

### Added

- **启动加载 UI 品牌化**：`preloader_dialog` 重写为 Swiss 纸面语言——全屏 90% 黑遮罩 +
  GALGAMESHELL 大写字距标 + 主文案 + 2dp 细进度条，不再引用 Winlator 的
  preloaderBackground/progressBarIndeterminateImage attr（真机视觉实证，
  real-device-verification.md §12.3）。
- **性能显示多项化（13 项可选）**：新 `GalgamePerfHud`（零上游依赖）——FPS / CPU
  使用率 / CPU 频率（avg·max）/ **CPU 各核占用** / 系统内存 / 游戏进程内存（含
  box64/wine RSS）/ **游戏进程 CPU** / GPU 型号 / 负载（loadavg）/ **整机功耗**（电流×
  电压）/ **温度**（CPU·电池 thermal zone）/ **电池**（电量·状态）/ **网络上下行**。
  抽屉「性能显示」由三档改**多选对话框**，容器 extra `galgame_perf_items` 持久化、
  中途开关立即生效；有选择项时接管左上角、上游三档 FrameRating 自动隐藏。
  真机实证多选→应用→浮窗渲染（FPS 59.8 / CPU 45.2%，§12.4）。
- 游戏内**返回键改为发送 ESC**（galgame 通用取消/呼出菜单键，与 PC 一致），不再像上游那样
  打开左侧功能抽屉（用户反馈「返回键弹出 Winlator 页面」）。功能菜单入口改为**右上角
  半透明 ☰ 浮钮**（画中画时自动隐藏；触摸板模式的四指点按呼出保留）；抽屉头部标语改为
  「游戏菜单」，游戏中不再出现 "(Winlator-based)" 字样（关于页归属声明不变）。真机验证：
  ESC 实际送达游戏（花弔！标题触发自身退出并落回游戏库，而非 Winlator 页面）、
  浮钮/关抽屉/退出落点全部通过（docs/real-device-verification.md §11）。
- 游戏内抽屉新增「**性能显示**」三档开关（关闭 / 简单(FPS) / 完整(FPS·CPU·内存·GPU)），
  中途可切换、立即生效并写回容器。上游 FrameRating 本有此能力但只能在上游容器编辑页里开，
  galgame 容器进不去该页；为此 `frameRating` 改为无条件创建（上游仅 HUDMode≠DISABLED 才
  创建，中途开启会接不上已映射窗口的帧计数）。真机实测完整档：FPS 59.8 /
  GPU Turnip Adreno 830 / RAM 8.7/15.5GB / CPU 3.19GHz | Box64 v0.4.5。
- 新增 `docs/component-versions.md` **组件版本登记表**：Ren'Py / Kirikiroid2 / Winlator
  三组件的内嵌版本、上游最新稳定版、核查日期（2026-10-25）与升级路径的单一事实源；
  发版前核查、结果记入 CHANGELOG（当前结论：Ren'Py 8.5.3＝最新稳定版无需动作，
  KR2 上游 2024-06 起停更无同步对象，Winlator 落后 5 笔按需移植）。
- 启动前运行环境自愈 `GalgameLaunchHelper.ensureEngineRuntime`（引擎启动链自动执行，幂等）：
  ① AdvHD 引擎游戏目录注入 3KB 自制 x64 `icu.dll` 最小桩（assets 内置，仅导出引擎实际
  静态导入的 `uloc_getDefault`）——修复 AdvHD 系游戏「启动即退：加载器缺 icu.dll
  c0000135」（真机 toran 由秒退变为引擎可加载、窗口可建）；② 主程序文件名非 ASCII 时
  自动建同目录硬链接 `gs_launch.exe` 并改走别名——修复 wine 启动器早期按 ANSI 码页
  解析主程序路径导致 BGI（両腕…exe）打不开（真机由秒退变为完整进入游戏、满幅渲染
  90.3%）；③ 存档重定向 S: 盘目标 appdata/roaming、appdata/local 目录自愈（PlayDRM
  等写 S: 的组件不再 PATH_NOT_FOUND）。
- 启动对话框自动应答（教学-回放）`GalgameDialogAutoClick`：首次启动手动点击启动期
  小窗口（分辨率确认/免责声明等）时记录相对坐标（容器 extra，按窗口尺寸区分），之后
  每次启动检测到同尺寸窗口自动重放点击。零盲点：没有教学点绝不自动点。教学记录/回放
  触发/坐标注入三环节真机验证通过；端到端（自动点掉对话框进主画面）待首个可复现样本
  闭环（krkr_tobenai 该容器启动流程挂起，已另登记排查）。
- 源码排查澄清：BGI「§7.4 结论未定」确认为**双层真相**——当年验收脚本转义确实坏，
  且应用层非 ASCII 主程序路径在 wine 启动器早期确实打不开（本轮以 `+file,+nls` 通道
  取证到乱码宽字符名）；test_advhd 为 Enigma Protector+PlayDRM 双重商业保护，解壳后
  自主退出，按 A5 决策不破解、登记为不可玩。

- 项目身份重构（决策 A8）：GalgameShell 定位为**独立项目**，内嵌 winlator-app
  （LGPL-2.1）为**源码组件**——声明方式同内嵌的 Kirikiroid2 / Ren'Py（`NOTICE` §1）。
  GitHub 侧本就不是 fork（独立建仓）；「rebase 同步上游」工作流终结，上游更新改为
  按需移植；零改例外登记表的双重身份明确为 LGPL-2.1 §2 修改标注 + 移植冲突面清单；
  整仓保持单许可证 LGPL-2.1（组件部分继承不可更授，增量层自主选择保持一致）。
  README / NOTICE / ci 文档同步改写。

- 修复「退出后仍显示 WINLATOR 页面」：前台服务通知非游戏会话时的点击落地页由上游
  `MainActivity` 改为游戏库壳 `GalgameMainActivity`（通知是应用里最后一个能落到
  WINLATOR 界面的入口；抽屉「退出」实测本就正常回库）。
- 新增 [docs/fullscreen-fix-retrospective.md](docs/fullscreen-fix-retrospective.md)：
  全屏修复的经验与教训（反向杠杆、装饰位挡门、测试方法论坑与上游影响面纪律）。

- galgame 容器直启默认启用「窗口画面铺满」（上游 forceWindowsFullscreen）：游戏窗口等比
  放大居中到虚拟屏、输入坐标自动换算，无拉伸变形。修复 Wine 路线 galgame 几乎都以小于
  虚拟屏的窗口运行、画面只占物理屏一小块的问题（真机 7 用例复测：Wine 路线 5 用例
  12%–45% → 79%–90%，Unity/KR2/Ren'Py 原生路线零回归；X11 `ButtonPress` 级确认点击
  投递正常）。配套两处：① 语言/字体注入链新增 Wine 注册表 `X11 Driver → Decorated=N`，
  让顶层窗口不带 Wine 自绘标题栏（本 app 的 X server 是不重定父的极简 WM，带装饰位的
  窗口进不了铺满变换；去掉标题栏后变换生效且不会把标题栏一起放大）——旧容器用详情页
  「重新注入运行环境」即可补上；② 放宽 `GLRenderer.inBounds` 上界（`<` → `<=`），使
  「窗口==虚拟屏」的 KiriKiri（(152,152) 固定偏移）不再右下裁切（TD-14 残留①）。
  每游戏可在详情页「高级设置 → 窗口画面铺满」关闭；shortcut 与普通 Winlator 容器不受影响。

- 详情页「高级设置 → 运行环境分辨率」：可把容器的虚拟屏在 1280x720 / 1600x900 / 1920x1080
  之间调整，改完下次启动生效、无需重建容器。用于游戏自己选的窗口尺寸撞上容器分辨率时
  画布被裁切、靠右/靠下的 UI 点不到的情况（成因见 TD-14：KiriKiri 把主窗口固定在 (152,152)）。

- 导入时按引擎自动放大虚拟屏：`engine_presets.json` 新增 `screen_size` 字段，KIRIKIRI 预设
  默认 **1920x1080**，新建容器（含详情页「改用 Wine 容器运行」）不再落到 1280x720。
  真机验证两款：`krkr_tobenai` 在 1920 容器 + 游戏窗口 960x540 下画布完整可见，一次点中
  START 进正片并连续推进对白约 60 秒；`krkr_caste` 经「改用 Wine 容器运行」新建的容器
  `screenSize` 即为 1920x1080，从警告页 → 标题 → 「从头开始」→ 正文对白与背景正常推进。

- 「改用 Wine 容器运行」：原生 Kirikiri / Ren'Py 游戏在详情页「高级设置」里可一键转成
  独立容器，改由游戏自带的 Windows 原版引擎跑。用于内置 Kirikiroid2 报脚本错误
  （`Member … does not exist`）的游戏——真机验证同一游戏在 KR2 下报错、在 Wine 容器下
  正常进标题页。

## [11.3] - 2026-10-24

### Added

- Swiss 单首页 IA 与全站视觉重构：内页与游戏内 UI 收进同一套视觉语言；「设置 → 输入控制」
  新增宿主 `GalgameInputControlsActivity`，从死路接成真入口（`7a1312f`、`be13b21`、`a9302cf`）。
- 明暗双档走同一套主题 token，`values-night` 反相，状态栏图标随档位切换（`e01cb50`、`4739d2f`）。
- 运行环境可管理：详情、重新注入语言字体、删除（`e01cb50`）。
- 引擎指纹扩展到 19 个：Siglus / YU-RIS / Artemis / CatSystem2 / AdvHD / SystemNNN / BGI /
  CMVS / Unity 等；新增 `detectDeep()` 有限下钻（深度 ≤3、预算 4000 项），修子目录 `.xp3`
  被判 UNKNOWN 后无法升级路由（`c46485e`、`a678ad6`）。
- box64 自建变成可复现资产：私有补丁导出 + 双向校验、构建脚本、打包器入库且版本不再
  写死（缺省从 `DefaultVersion.BOX64` 读）（`1ecda1e`、`cd1798d`、`cdea13d`、`e33fae0`）。
- `ci/hotspot-entries.txt` 纳入 `GalgameSettingsActivity` / `GalgameImportActivity` /
  `GalgameDetailActivity`（3 → 6 条）；`com.winlator.R` 桩 100 → 183 符号（`c880186`）。

### Changed

- 依赖升级（Dependabot）：`androidx.appcompat:appcompat` 1.4.0 → 1.8.0（#2）；`com.google.android.material:material`（#3）；`org.apache.commons:commons-compress`（#6）；`org.tukaani:xz` 1.7 → 1.12（#9）；`com.github.luben:zstd-jni` 1.5.2-3 → 1.5.7-23（#8）。
- CI 依赖升级（Dependabot）：`actions/setup-java` 4 → 6（#4）；`actions/checkout` 4 → 7（#5）；`actions/upload-artifact` 4 → 7（#7）。
- 新增 `.mailmap`，统一贡献者邮箱显示（`5adcca4`）。
- 新增 `docs/release-governance.md`（§10 发布治理本地化），并在 Release 附 `SHA256SUMS.txt`（#10）。
- 导入/启动链路：选中真正的主程序、修复非 ASCII exe 启动（`f338e06`）；完成态标记与容器
  列表缓存（`b0e3a52`）。
- 高级设置结构重排；导入时外部播放器回退；库内移除（`ebc9239`）。
- 运行时资源 SHA-256 清单不再漏掉 `assets` 子目录（`d69eb3f`）。
- README 补 Gradle wrapper 拉不下来时的离线构建路线（PKIX 报错 / 半截缓存）（`661b91e`）。

### Fixed

- X server 输出流：`XOutputStream_send` 由裸阻塞 `write()` 改为 `send(MSG_NOSIGNAL)` 循环
  写满，EINTR 重试、EAGAIN 时 `poll` 上限 2 秒——消除协议流静默截断与 SIGPIPE（`7c08fc2`）。
- 日志滚动条在深色主题下不可见（`72e5d23`）。

## [11.2] - 2026-10-17

- 发布 tag `v11.2`（提交 `1e47bd0`）；对应版本位 `app/build.gradle`：`versionName = "11.2"`、`versionCode = 33`。
- 本版 Release 说明由 GitHub `--generate-notes` 自动生成，未包含人工维护条目；完整提交对比见
  [v11.2 对比 main](https://github.com/ReSerendipity/GalgameShell/compare/v11.2...main)。

[Unreleased]: https://github.com/ReSerendipity/GalgameShell/compare/v11.5...HEAD
[11.5]: https://github.com/ReSerendipity/GalgameShell/releases/tag/v11.5
[11.4]: https://github.com/ReSerendipity/GalgameShell/releases/tag/v11.4
[11.3]: https://github.com/ReSerendipity/GalgameShell/releases/tag/v11.3
[11.2]: https://github.com/ReSerendipity/GalgameShell/releases/tag/v11.2
