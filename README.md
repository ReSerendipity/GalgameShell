# GalgameShell — GalGame Android Shell

基于官方 [`brunodev85/winlator-app`](https://github.com/brunodev85/winlator-app)（`main`，LGPL-2.1）的 galgame 专用外壳。
路线 1：以 Winlator 为模拟层基座，做「引擎识别 + 原生(B) / 模拟(A) 智能路由」的装修层。

> 完整计划见 `Desktop/galgame-plan-complete.md`（Plan Part I–VIII）。
> 本仓库 fork：https://github.com/ReSerendipity/GalgameShell （工作分支 `galgame`）

## 当前状态

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

> ⚠️ **本仓库按设计不含大二进制**：`app/src/main/assets/**/*.tzst`（wine/box64 运行时）与
> `app/src/main/jniLibs/**/*.so` 未随仓分发（体积 + 计划口径「发框架、用户自取资源」）。
> 因此**不能直接 `assembleDebug` 出完整 APK**，需先补齐二进制（见下）。

## 模块一览（新增价值层）

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

## 构建

1. 补齐上游大二进制（二选一）：
   ```bash
   # 从上游取回被排除的运行时资源与 native 库
   git remote add upstream https://github.com/brunodev85/winlator-app   # 若尚未添加
   git fetch upstream
   git checkout upstream/main -- app/src/main/assets app/src/main/jniLibs
   ```
2. 配置 Android SDK（`local.properties` 的 `sdk.dir`），然后：
   ```bash
   ./gradlew assembleDebug
   ```

CI（`.github/workflows/ci.yml`）在无 SDK / 无二进制的条件下，用 `ci/stubs` 对
`com.winlator.galgame` 与 `.ui` 两个包做 `javac -Xlint:all` 类型门禁 + 零改铁律校验 +
资产合法性校验（详见 `ci/README.md`）。

## rebase 运维流（C3，Plan §VIII.4）

```bash
git fetch upstream
git rebase upstream/main          # 冲突应仅 3–4 文件小 diff
git push --force-with-lease origin galgame
```

**零改铁律**（CI 强制校验）——以下文件绝不直接改：

- `app/src/main/java/com/winlator/container/Container.java`
- `app/src/main/java/com/winlator/core/LocaleHelper.java`
- `app/src/main/res/values/strings.xml` 现有条目

所有 galgame 价值放新包 `com.winlator.galgame` + 新文件 `galgame_strings.xml`。
已接受的**加法型**冲突面：`AndroidManifest.xml`（注册新 Activity + `<queries>`）、
`res/layout/activity_galgame_library.xml`（全新建）。

## 决策锁定（A1–A6）

- **A1 字体**：仅开源 CJK（IPAex / Noto，OFL）；**禁捆绑商业字体**；框架不分发字体。
- **A2 容器**：每游戏独立容器（隔离 + 导入即玩）。
- **A3 导入**：默认**复制（可写）**，非引用挂载（规避符号链接死结 RK-06）。
- **A4 B 路由**：默认唤起不内嵌；Tier-1 开源默认 / Tier-2 闭源显式开启 + 免责。
- **A5 加密**：仅检测 + 提示，不破解。
- **A6 许可**：LGPL-2.1，fork 公开 + `LICENSE`/`NOTICE` + 应用内源码获取。

## 阶段路线图（P0–P7）

`P0 → P1 → (P2, P3 并行) → P4 → P5 → P6 → P7`

P6（真机回归 R1–R17）为发布门禁，需 Adreno + Mali 各一台真机。

## 商店说明要点（发布用）

- 最低要求：Android 11+ (API 30) / ARM64 / Vulkan 1.1+；存储预留 8–16GB；建议 12GB+ RAM。
- 规避无 Vulkan 设备（如 Kirin）；Mali 走 Vortek/VirGL 兜底。
- 本应用不含游戏本体、不提供破解；用户需自备合法游戏副本。
- 第三方原生播放器为可选唤起，使用风险自负。
- 开源许可与源码获取方式见应用内「关于」及 `NOTICE`。

## 许可证

LGPL-2.1（继承自 winlator-app）。本增量层同样以 LGPL-2.1 发布。
第三方组件与字体政策详见 [`NOTICE`](NOTICE)。
