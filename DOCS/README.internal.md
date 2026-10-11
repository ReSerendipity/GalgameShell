# GalgameShell 对内开发说明

> 本文件收容自根 `README.md` 拆分移出的全部内部内容（计划指引、进度、模块编号对照、资源与 CI 约定、rebase 运维、决策记录、路线图、发布备忘），按主题分节，除必要串接语外保持原文措辞。
> 按 `AGENTS.md` 的对内/对外分区约定，本文件即对内说明入口，落地仓库时应位于 `DOCS/README.internal.md`。

## 1. 仓库与计划指引

- 完整计划见 `Desktop/galgame-plan-complete.md`（Plan Part I–VIII）。
- 本仓库 fork：https://github.com/ReSerendipity/GalgameShell （工作分支 `galgame`）
- 上游基座：官方 [`brunodev85/winlator-app`](https://github.com/brunodev85/winlator-app)（`main`，LGPL-2.1）。
- 路线 1：以 Winlator 为模拟层基座，做「引擎识别 + 原生(B) / 模拟(A) 智能路由」的装修层。

## 2. 当前状态

| 阶段 | 内容 | 状态 |
|------|------|------|
| P0 | 脚手架：fork + 新包 + `galgame_strings.xml` + CI | ✅ |
| P1 | 导入即玩：`EngineDetector` / `ImportFlow` / `EnginePreset` / A2 每游戏容器 / A3 复制 / B4 S: 盘 | ✅ |
| P2 | 日文化：`GalgameLocaleInjector`（env + 区域注册表 + 日文字体）/ `GalgameFonts` | ✅ |
| P3 | 视频解码：`GalgameVideoSupport`（强制 `directshow=1`/`wmdecoder=1`） | ✅ |
| P4 | 存档：S: 盘 + Shell Folder 重定向 + 便携存档「先搬后链」+ 导出/恢复 | ✅ |
| P5 | B 路由：`NativeRouteLauncher`（Tier-1 开源默认 / Tier-2 闭源需显式开启+免责） | ✅ |
| — | 诊断向导 `GalgameDiagnostics` + 日志查看 `GalgameLogs` | ✅ |
| — | 库 UI `GalgameLibraryActivity`（导入/启动/诊断/日志/存档/B 路由对话框） | ✅ |
| P6 | 真机回归 R1–R17（Adreno + Mali 各一） | ⏳ 需真机 |
| P7 | 许可合规（LICENSE/NOTICE/源码获取）+ rebase 硬化 | ✅（本文件 + `NOTICE`） |

## 3. 模块一览（内部编号对照）

```
app/src/main/java/com/winlator/galgame/
  EngineDetector.java        B1 引擎指纹（magic bytes）+ A/B 路由 + A5 加密检测
  EnginePreset.java          engine_presets.json 单条目解析
  ImportFlow.java            P1 导入即玩编排（构造 Container data → stage 复制 + S: + P2 注入）
  GalgameSaveManager.java    B4/P4 存档（S: 盘 + Shell Folder 重定向 + 先搬后链 + 导出/恢复）
  GalgameLocaleInjector.java P2 日文化（env LC_ALL/LANG + 区域注册表 + 字体）
  GalgameFonts.java          P2 日文字体定位（框架不分发字体）
  GalgameVideoSupport.java   P3 视频解码（强制 directshow/wmdecoder）
  NativeRouteLauncher.java   P5 B 路由唤起（Tier 分层 + 免责 + 扫描目录预置）
  GalgameDiagnostics.java    诊断向导（GPU/日文/视频/位宽/音频/加密 检查表）
  GalgameLogs.java           日志查看（logs.txt / stderr / dxvk + 要害行提取）
  ui/
    GalgameLibraryActivity.java  库 UI（导入/启动/诊断/日志/存档/B 路由）
    GalgameSettings.java         Tier-2 开关（SharedPreferences）
```

新增资源：`assets/engine_presets.json`、`assets/galgame_fonts.json`、
`res/values/galgame_strings.xml`、`res/layout/activity_galgame_library.xml`。

## 4. 资源口径与 CI 内部约定

> ⚠️ **本仓库按设计不含大二进制**：`app/src/main/assets/**/*.tzst`（wine/box64 运行时）与
> `app/src/main/jniLibs/**/*.so` 未随仓分发（体积 + 计划口径「发框架、用户自取资源」）。
> 因此**不能直接 `assembleDebug` 出完整 APK**，需先补齐二进制。

CI（`.github/workflows/ci.yml`）在无 SDK / 无二进制的条件下，用 `ci/stubs` 对
`com.winlator.galgame` 与 `.ui` 两个包做 `javac -Xlint:all` 类型门禁 + 零改铁律校验 +
资产合法性校验（详见 `ci/README.md`）。

## 5. rebase 运维流（C3，Plan §VIII.4）与零改铁律

```bash
git fetch upstream
git rebase upstream/main          # 冲突应仅 3–4 文件小 diff
git push --force-with-lease origin galgame
```

**零改铁律**（CI 强制校验）——以下文件绝不直接改：

- `app/src/main/java/com/winlator/container/Container.java`
- `app/src/main/java/com/winlator/core/LocaleHelper.java`
- `app/src/main/res/values/strings.xml` 现有条目

> **登记例外（A7，2026-10-11）**：`LocaleHelper.supportedLocales` 增加 `"zh_CN"` 是本仓简体中文界面的
> 必要触碰（提交 `bb810ca`），登记为既有例外；若上游未来原生支持 zh_CN，应回退本文件改动。详见 §6 A7。

所有 galgame 价值放新包 `com.winlator.galgame` + 新文件 `galgame_strings.xml`。
已接受的**加法型**冲突面：`AndroidManifest.xml`（注册新 Activity + `<queries>`）、
`res/layout/activity_galgame_library.xml`（全新建）。

### 5.1 已触碰上游文件清单（截至 2026-10-11，评估整改 P1-3）

> 口径：`git diff upstream/main HEAD -- app/src/main/java` 中 **fork 主动改动**（有 fork 提交归因）；
> 落后上游所致、fork 未主动改的差异单列于表末。冲突风险：低=新增/UI 局部；中=与待同步上游提交重叠。

| 文件 | 改动要点 | fork 提交（示例） | 冲突风险 |
|---|---|---|---|
| `BaseFileManagerFragment.java` | P0 设计系统：列表卡片化 + 空态组件化 | 18949f3 | 低 |
| `ContainerDetailFragment.java` | 容器详情走 GalgameHost 底部导航壳 | 84e873f | 低 |
| `ContainerFileManagerFragment.java` | 整行进入目录；卡片图标品牌圆底盘 | fc395b4 / 7fd0929 | 低 |
| `ContainersFragment.java` | 退出落回 R 新壳；启动前 exe 缺失拦截 | eeafc3a / 84e873f | 低 |
| `InputControlsFragment.java` | GalgameHost 宿主契约，解耦上游强转 | 70f0a12 | 低 |
| `MainActivity.java` | 抽屉品牌化与开合遮罩；GalgameHost 契约 | 444d4d4 / 70f0a12 | 低 |
| `SettingsFragment.java` | GalgameHost 宿主契约 | 70f0a12 | 低 |
| `ShortcutsFragment.java` | 整行进入目录；卡片图标品牌圆底盘 | fc395b4 / 7fd0929 | 低 |
| `XServerDisplayActivity.java` | 触控模式运行时切换；galgame 容器退出落回库；nogui 直跑 + wine 输出落盘 | b7243c8 / 6fceb55 | **中**（待同步上游 a544910 亦改此文件） |
| `contentdialog/AboutDialog.java` | 关于页品牌化 | 662399f | 低 |
| `contentdialog/ContentDialog.java` | P1–P3 对话框/表单品牌化 | d3726a3 | 低 |
| `core/AppUtils.java` | 外壳品牌化：紫色亮/深双主题 | efe256c | 低 |
| `core/DefaultVersion.java` | BOX64 0.4.4 → 0.4.5 | e80db63 | 低 |
| `core/FileUtils.java` | 缺 `.update-timestamp` NPE 修复 | 1e97eef | 低 |
| `core/GeneralComponents.java` | GalgameHost 宿主契约 | 70f0a12 | 低 |
| `core/LocaleHelper.java` | `supportedLocales` 增 `zh_CN`（**A7 登记例外**） | bb810ca | 中（铁律文件，CI 门禁锁） |
| `core/ProcessHelper.java` | wine 输出落盘 `wine_exec.log`（原 /dev/null） | b7243c8 | 低 |
| `core/WineUtils.java` | 缺 `.update-timestamp` NPE 修复 | 1e97eef | 低 |
| `widget/ImagePickerView.java` | GalgameHost 宿主契约 | 70f0a12 | 低 |
| `xenvironment/RootFSInstaller.java` | GalgameHost 宿主契约 | 70f0a12 | 低 |
| `xenvironment/components/GuestProgramLauncherComponent.java` | libmpshim 条件 LD_PRELOAD；移除临时 WINEDEBUG | b7243c8 / 45b76b6 | 低 |

资源/构建层（非 java，另行登记）：

| 文件 | 改动要点 | 备注 |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | MANAGE_EXTERNAL_STORAGE / `<queries>` / 入口接管 | 文档已声明为加法型冲突面 |
| `app/build.gradle` | 品牌化、minSdk 对齐 API 30、abiFilters 调试开关 | 待同步上游亦改此文件（**中**） |
| `app/src/main/res/values/strings.xml` | `app_name` 品牌化为 GalgameShell（4729601，既有登记） | 待同步上游 34df94f 新增 `native_resolution`（**中**） |
| `app/src/main/res/values/arrays.xml`、`widget/TouchpadView.java` | **待同步差异**：落后上游 4 commits（34df94f / a544910）所致，fork 无主动改动 | 随 T5 同步后消失 |

## 6. 决策锁定（A1–A7）

- **A1 字体**：仅开源 CJK（IPAex / Noto，OFL）；**禁捆绑商业字体**；框架不分发字体。
- **A2 容器**：每游戏独立容器（隔离 + 导入即玩）。
- **A3 导入**：默认**复制（可写）**，非引用挂载（规避符号链接死结 RK-06）。
- **A4 B 路由**：默认唤起不内嵌；Tier-1 开源默认 / Tier-2 闭源显式开启 + 免责。
- **A5 加密**：仅检测 + 提示，不破解。
- **A6 许可**：LGPL-2.1，fork 公开 + `LICENSE`/`NOTICE` + 应用内源码获取。
- **A7 LocaleHelper 例外（2026-10-11 登记，评估整改 P1-1）**：`LocaleHelper.supportedLocales` 的 `"zh_CN"`
  为简体中文界面必要触碰（提交 `bb810ca`）；上游原生支持 zh_CN 后回退本文件改动。

## 7. 阶段路线图（P0–P8）

`P0 → P1 → (P2, P3 并行) → P4 → P5 → P6 → P7`

P6（真机回归 R1–R17）为发布门禁，需 Adreno + Mali 各一台真机。

| 阶段 | 内容 | 状态 |
|------|------|------|
| P8 | 合并双 UI：`GalgameLibraryActivity` 与 `GalgameHomeFragment` 并为单一 Fragment（历史遗留，见 `engine-equivalence-ui-landed.md` §9 / tech-debt-register.md TD-01；2026-10-11 评估整改 P2-6 登记） | ⬜ 待排期（验收：两条启动路径行为一致、库 UI 仅单一实现） |

## 8. 商店说明要点（发布用）

- 最低要求：Android 11+ (API 30) / ARM64 / Vulkan 1.1+；存储预留 8–16GB；建议 12GB+ RAM。
- 规避无 Vulkan 设备（如 Kirin）；Mali 走 Vortek/VirGL 兜底。
- 本应用不含游戏本体、不提供破解；用户需自备合法游戏副本。
- 第三方原生播放器为可选唤起，使用风险自负。
- 开源许可与源码获取方式见应用内「关于」及 `NOTICE`。
