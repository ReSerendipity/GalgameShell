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
