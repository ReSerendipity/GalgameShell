# Changelog

本文件记录 GalgameShell 的所有重要变更。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本 SemVer](https://semver.org/lang/zh-CN/)。
分类口径：`feat → Added`、`fix → Fixed`、`refactor → Changed`（见
[REPOSITORY_GUIDELINES.md §10.2](https://github.com/ReSerendipity/.github/blob/main/REPOSITORY_GUIDELINES.md)）。

> 说明：本仓发布说明（Release notes）当前由 `gh release create --generate-notes` 自动生成。
> 下条目仅回填可从 git 历史、tag / Release 与版本位溯源的客观事实，不做推测性归因。

## [Unreleased]

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

[Unreleased]: https://github.com/ReSerendipity/GalgameShell/compare/v11.3...HEAD
[11.3]: https://github.com/ReSerendipity/GalgameShell/releases/tag/v11.3
[11.2]: https://github.com/ReSerendipity/GalgameShell/releases/tag/v11.2
