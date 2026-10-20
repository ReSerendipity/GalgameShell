# GalgameShell — 前置简报：Winlator 容器「引擎化」+ 前端 UI 个性化

> **用途**：本文件是交给新对话的「前置信息」。你（新会话的 agent）接手 GalgameShell 的这两项功能开发：
> ① 把 Winlator 容器/Wine 路线提升为与内置原生引擎（Krkr / RenPy）**等价的一等引擎**；
> ② 对自有前端 UI 做四项个性化。
> 下面是所有已核实事实、硬性约束、已批准计划与已拍板决策。**开始写代码前，请先 `git status` 确认工作树状态，并 Read 下列关键文件以核对当前代码（本简报基于 2026-10-05 的代码快照，可能已有变动）。**

---

## 0. 一句话任务

让 `XServerDisplayActivity`（容器/Wine 路线，即「Winlator 本体」）成为与 `KrkrEngine` / `RenPyEngine` 等价的一等 `GameEngine`；并把前端 UI（图标/色板/库布局/上游屏显隐）个性化。范围与决策见 §5。

---

## 1. 项目身份与核心铁律（务必遵守）

- **项目**：Winlator **v11.2** 的 fork（安卓端 Galgame 运行器）。上游 `brunodev85/winlator-app`，本仓 `ReSerendipity/GalgameShell`，包名 `com.winlator`，`versionName "11.2"`。
- **【零改铁律】** 不得修改上游 Winlator 核心文件，CI 有 rebase-guard 校验：
  - ❌ **绝不碰** `Container.java` / `LocaleHelper.java` / `values/strings.xml`（默认 `app/src/main/res/values/strings.xml`）。
  - ✅ `app/src/main/res/values/styles.xml` **可改**（加主题/属性，已有 `GalgameMainTheme`）。
  - ✅ 新 UI 字符串放 **`values-zh-rCN/strings.xml`**（已是本项目新增目录）或**新增** `galgame_strings.xml`，**不要去改默认 `values/strings.xml`**。
  - ✅ 扩展一律走**新增类 / 新增资源**，不要改既有上游类。
- **【双分支红线】**：分批提交、**默认不 push**（除非用户当次显式授权）；同一仓库同时只跑一个 fixer；严禁触碰未提交 WIP。
- **自有屏优先**：R 系列新 UI 在 `com.winlator.galgame.ui.*`；新屏一律用 `?attr` 主题属性，**禁止硬编码颜色**。
- **构建环境**（本机）：JDK 21（Adoptium）、Gradle 离线用 `C:/Users/Doro/.gradle/manual-dist/gradle-8.14.3`（wrapper 声明 8.14.5）、`ANDROID_HOME=C:/Users/Doro/AppData/Local/Android/Sdk`、NDK 24.0.8215888、AGP **8.4.2**（注意：**不是** DraftPeek 的 8.10.1）、compileSdk 35 / minSdk 30 / targetSdk 30。
  - 勘误（2026-10-11，第二轮移动端评估 T9 文档同步）：原记 `minSdk 26` 与代码不符，实际 **minSdk 30**（Android 11+，与 README「API 30」声明及 MANAGE_EXTERNAL_STORAGE 语义一致）。
  - 默认 APK 仅 `arm64-v8a`；可 `./gradlew assembleDebug -PgalgameUiAbi=x86_64` 出无原生库 UI 调试 APK（模拟器用）。
  - **勿用 gradlew**（离线 PKIX 失败）；本地用 `gradle-8.14.3/bin/gradle assembleDebug --offline`。
- **真机**：`dc57ebe3`（RMX5010，Android 15，1264×2780）。Git Bash 下 adb 须 `export MSYS_NO_PATHCONV=1`；`adb pull` 目标用 `C:/...` 形式。

---

## 2. 已核实架构事实（2026-10-05 快照，写代码前请复核）

### 2.1 当前三种启动策略是**分叉代码路径**，并非统一抽象
| 策略 | 实现位置 | 覆盖引擎 |
| --- | --- | --- |
| ① 内置原生引擎 | `BuiltinEngine`（abstract）→ `KrkrEngine` / `RenPyEngine`（均在 `com.winlator.galgame.engine`） | KIRIKIRI / RENPY |
| ② 外部 APK 唤起 | `NativeRouteLauncher`（唤起第三方播放器） | TYRANO / ONSCRIPTER / RPGMAKER* / WOLFRPG / PSP / RENPY(→JoiPlay) |
| ③ 容器 / Wine 路线 | `XServerDisplayActivity`（即「Winlator 本体」路线） | SIGLUS / YURIS / ARTEMIS / CATSYSTEM2 / UNKNOWN |

### 2.2 检测层（`EngineDetector.java`）
- `enum Route { A, B }` 与 `enum Engine { KIRIKIRI(..B), RENPY(..B), TYRANO(..B), ONSCRIPTER(..B), RPGMAKER2K(..B), RPGMAKERRGSS(..B), RPGMAKERMV(..B), WOLFRPG(..B), PSP(..B), SIGLUS(..A,null), YURIS(..A,null), ARTEMIS(..A,null), CATSYSTEM2(..A,null), UNKNOWN(..A,null) }`。
- `detect(File)`：字节 magic 优先（.xp3 / .rpa / ARC-3.0），再文件名/特征文件；1b 段补了 `game/` 子目录 Ren'Py 标准布局（修复真实 Ren'Py 漏判，commit `57f2bca`）。
- `Route` 枚举**仅用于检测优先级注释**，本次改造后**不再作分发开关**。

### 2.3 抽象基类（`BuiltinEngine.java`）
- 字段：`id` / `displayName` / `upstreamUrl` / `license`。
- 方法：`abstract boolean isAvailable(Context)`、`abstract String unavailableReason(Context)`、`abstract boolean launch(Context, File gamePath)`。
- 实现：`KrkrEngine.INSTANCE`、`RenPyEngine.INSTANCE`。

### 2.4 注册表（`BuiltinEngineRegistry.java`）
- `resolve(Engine)`：KIRIKIRI→Krkr；RENPY→RenPy；**default→null**。

### 2.5 外部播放器（`NativeRouteLauncher.java`）
- `resolve(Engine)`：映射各引擎到 `Target`（包名数组、tier、scanDir）。对 SIGLUS/YURIS/CATSYSTEM2/UNKNOWN 返回 **null**（即只能走 A 路线）。
- `plan()` / `launch()` / `launchFile()` / `prepareScanDir()` 唤起外部 APK。

### 2.6 【关键缺口，本次要修】
- `GalgameLibraryActivity.launchGame(Container)` **唯一**的库内启动路径，它**无条件** `startActivity(new Intent(this, XServerDisplayActivity.class))`（带 `container_id` + `GalgameLaunchHelper.injectExecPath(container, intent)`）。**不按引擎分流** → 已导入的 Krkr/RenPy 从库里启动会走 wine，而非原生引擎。
- `KR2Activity` / `RenPyActivity` **仅在各自引擎类内部**启动；库启动路径无任何引擎感知。
- 内置引擎只在**导入时**经 `showNativeRouteDialog` 生效（`BuiltinEngineRegistry.resolve(engine)` 优先，`NativeRouteLauncher` 兜底）；一旦进入库，启动就回到容器路线。
- `ImportFlow.ImportResult` 含 `engine` / `route` 字段，容器数据 JSON 在导入时由引擎 preset 构造，**引擎信息在导入时已知且随容器数据落库**——库内分流改造可据此读出引擎。

### 2.7 入口与主题
- `GalgameMainActivity`（exported=true）启动即游戏库；R2 底部导航（游戏库 / 容器 / 工具 / 设置）。
- `GalgameMainTheme`（在 `styles.xml`，可改）已是自有主题；`app_name` 已品牌化为 `GalgameShell`（写在 `values/strings.xml` 与 `values-zh-rCN/strings.xml` —— 注意这已轻微触碰默认 `strings.xml`，**后续品牌文案请改用新增资源文件，勿再改 `values/strings.xml`**）。
- 上游屏：`ContainersFragment`、`ContainerFileManagerFragment`、以及 `XServerDisplayActivity` 等；UI 个性化 §C4 要决定它们的显隐/改名。

---

## 3. 已批准计划（分阶段，零改铁律为第一红线）

### 阶段 A — 统一引擎抽象（新增，不动既有逻辑）
1. 新增接口 `GameEngine`（`com.winlator.galgame.engine`）：契约 `id` / `displayName` / `upstreamUrl` / `license` / `boolean isAvailable(Context)` / `boolean launch(Context, GameLaunchRequest)`。
2. 新增值对象 `GameLaunchRequest`：携带 `File gamePath` + `Container container`（或 `long containerId`）+ `EngineDetector.Engine engine`。让容器引擎能复用既有容器接线，内置引擎只取 `gamePath`。
   - 兼容做法：`BuiltinEngine` 保留现有 `launch(Context, File)` 作为内部实现，`GameEngine.launch(req)` 委托给 `gamePath`；或直接让 `BuiltinEngine` 实现新签名。
3. `BuiltinEngine` 改为 `implements GameEngine`（逻辑零改，仅加声明 / 适配签名）。
4. **新增 `WinlatorContainerEngine implements GameEngine`**：
   - `launch(req)` 取出 `containerId`，构造与 `launchGame()` 完全相同的 `Intent(XServerDisplayActivity.class)` + `putExtra("container_id", id)` + `GalgameLaunchHelper.injectExecPath(container, intent)`，再 `startActivity`。**绝不改 `Container.java`**。
   - `isAvailable(Context)` 默认 `true`（box64/wine 随 APK 打包）；可加 libs 存在性校验（可选）。
5. **新增 `GameEngineRegistry.resolve(EngineDetector.Engine)`**：KIRIKIRI→Krkr；RENPY→RenPy；**SIGLUS / YURIS / ARTEMIS / CATSYSTEM2 / UNKNOWN→WinlatorContainerEngine**；其余（TYRANO/ONSCRIPTER/RPGMAKER*/WOLFRPG/PSP）→ 返回 null（仍由 `NativeRouteLauncher` 处理，见阶段 B）。

### 阶段 B — 分发统一 + 修复库内不分流（正确性修复）
1. `GalgameLibraryActivity.launchGame(Container)`：从容器数据读出 `engine` → `GameEngineRegistry.resolve(engine)` → `engine.launch(req)`。**已导入 Krkr/RenPy 从库启动改走原生引擎**（修掉误走 wine 的缺口）；容器引擎则复用既有 XServerDisplayActivity 接线。
   - 需先确认 `Container` / 容器 DB 如何存 `engine`（`ImportFlow` 已写入容器数据 JSON），实现时 `Read` 核对字段名。
2. `showNativeRouteDialog`（导入时）：用 `GameEngine e = GameEngineRegistry.resolve(engine); if (e!=null && e.isAvailable) e.launch(req); else NativeRouteLauncher.plan(...)` 替掉 builtin→external 硬分支；容器引擎作兜底。
3. `GalgameHomeFragment` 导入后分发同步走统一入口。
4. `EngineDetector.Route` 枚举保留作检测优先级注释，**不再作分发开关**。

### 阶段 C — 前端 UI 个性化（四项全做）
> 合规：新素材 / 新布局走新增资源与 `galgame.ui` 包；`styles.xml` 可加主题；**绝不改 `values/strings.xml`**（新字符串进 `values-zh-rCN/` 或新增 `galgame_strings.xml`）。
- **C1 图标 / 闪屏品牌**：替换 launcher 图标（含 adaptive icon 前景/背景）、通知图标，新增 splash drawable + 在 manifest / 主题引用，去掉残留 Winlator 图标辨识。
- **C2 品牌色板**：在 `GalgameMainTheme` 用 `?attr` 定义品牌色（如 `?attr/galgamePrimary` 等），统一游戏库卡片 / 顶栏 / 按钮；**消除 `galgame.ui` 布局里的硬编码颜色**。
- **C3 游戏库 / 首页布局**：封面卡片网格、空状态、`FAB`、顶栏标题「GalgameShell」视觉打磨（`GalgameLibraryActivity` / `GalgameLibraryAdapter` / `GalgameHomeFragment`）。
- **C4 上游屏幕显隐**：游戏库为主入口；上游 Winlator 屏（`ContainersFragment` / `ContainerFileManagerFragment`）**改名或隐藏**（从导航与入口移除/重命名，**不删类**），让自有 UI 成为绝对主入口。

### 阶段 D — 验证 & 提交策略
- 真机 `dc57ebe3` 回归：内置引擎游戏库启动→原生（`KR2Activity` / `RenPyActivity`）；容器游戏（Siglus 样本）库启动→`XServerDisplayActivity`；导入对话框行为不变；UI 改动截图核对。
- 按依赖**分批提交**（抽象 → 引擎类 → 注册表 → 分发 → UI 四项），**默认不 push**（除非用户当次授权）；遵守双分支红线。

---

## 4. 验证手法（模型读不了图，用客观像素分析替代主观描述）
- 真机取证：`adb -s dc57ebe3 shell am start ...` / `screencap -p` / `adb pull` 到 `C:/Users/Doro/Desktop/GalgameShell验证截图/`。
- 像素分析脚本：`.workbuddy/verify/analyze_png.py`（仓根相对路径；`.workbuddy/` 为本地不入库目录）（`python analyze_png.py <png>` 统计黑占比 / 占位灰(170,170,170) / 彩色占比 / 颜色数）。
- UI 改动用截图核对布局/色板（可 `show_widget` 内联 `<img src="C:/绝对路径">` 兜底预览）。

---

## 5. 已拍板决策（不要再向用户追问，直接按此执行）
- **统一范围 = 最小**：只把容器/Wine 路线提为 `GameEngine` 与 Krkr/RenPy 等价；**外部播放器维持 `NativeRouteLauncher` 现状**，不纳入 `GameEngine` 抽象。
- **UI 个性化 = 四项全做**：C1 图标/闪屏品牌 + C2 品牌色板 + C3 库/首页布局 + C4 上游屏幕显隐。

---

## 6. 关键文件索引（写代码前请 Read 核对当前状态）
- 抽象与注册表：`app/src/main/java/com/winlator/galgame/engine/BuiltinEngine.java`、`BuiltinEngineRegistry.java`、`KrkrEngine.java`、`RenPyEngine.java`
- 检测：`app/src/main/java/com/winlator/galgame/EngineDetector.java`
- 外部播放器：`app/src/main/java/com/winlator/galgame/NativeRouteLauncher.java`
- 分发入口：`app/src/main/java/com/winlator/galgame/ui/GalgameLibraryActivity.java`（`launchGame` ~L172、`showNativeRouteDialog` ~L484）、`app/src/main/java/com/winlator/GalgameHomeFragment.java`（导入分发 ~L22/L345）
- 容器接线：`app/src/main/java/com/winlator/galgame/GalgameLaunchHelper.java`（`injectExecPath`）、`app/src/main/java/com/winlator/XServerDisplayActivity.java`、`Container.java`（只读，勿改）
- 导入：`app/src/main/java/com/winlator/galgame/ImportFlow.java`（`ImportResult.engine/route`、容器数据构造）
- UI：`app/src/main/java/com/winlator/galgame/ui/GalgameLibraryActivity.java`、`GalgameLibraryAdapter.java`、`GalgameSettings.java`；资源 `app/src/main/res/values/styles.xml`（`GalgameMainTheme`）、`values-zh-rCN/strings.xml`、各 `layout-*`
- 集成事实基线：`docs/native-engine-integration.md`（Ren'Py/Krkr 集成已验证，含引擎等价化相关背景）
- 像素验证：`.workbuddy/verify/analyze_png.py`（仓根相对路径；`.workbuddy/` 为本地不入库目录）

---

## 7. 当前状态
> **本简报已被取代**：本功能已落地，实际结果见 `docs/engine-equivalence-ui-landed.md`（含勘误与真机复测手册）。
> 以下为 2026-10-05 简报时点的原文状态，保留供追溯，不代表当前状态。

- 【已过时（2026-10-05 快照时的状态）】当时本功能**尚未实现**，工作树干净（上一轮已提交并推送的是 Ren'Py 真机 E2E 验证工作，与本功能无关）。
- 本简报基于 2026-10-05 代码快照；新会话开工前务必 `git status` + `git log --oneline -5` 确认无未提交改动、无新增相关提交后再动手。
