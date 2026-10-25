# ci/ — CI 专用辅助文件（**不属于 Android 构建**）

## `stubs/`
给 CI 用的「离线类型门禁」桩代码：`com.winlator.galgame` 包与热点入口清单（见下文）
引用了 Android SDK / androidx / Winlator 组件的 `com.winlator.*` 部分 API，而本仓库按设计
**不含大二进制**、CI 也不装 Android SDK。因此用一组最小签名桩顶替这些外部符号，让
`javac -Xlint:all` 能在秒级内抓出真正的编译错误（签名不符、不可达 catch、缺 import 等）。

> 这些桩**只在 CI 里编译**，位于 `app/src/main/java` 之外，Gradle 不会把它们打进 APK，
> 也不会与真实类冲突。

本地等价命令：

```bash
mkdir -p /tmp/stubs-out /tmp/check /tmp/hot
javac -d /tmp/stubs-out $(find ci/stubs -name '*.java')
javac -Xlint:all -cp /tmp/stubs-out -d /tmp/check $(find app/src/main/java/com/winlator/galgame -name '*.java')
javac -Xlint:all -cp /tmp/stubs-out:/tmp/check -d /tmp/hot \
  $(grep -vE '^[[:space:]]*(#|$)' ci/hotspot-entries.txt)
```

## 热点入口清单（子树外的显式文件列表）

`com.winlator` 顶层最热改动的入口类不在 galgame 子树内，靠**显式清单**纳入门禁。
清单的唯一来源是 [`ci/hotspot-entries.txt`](hotspot-entries.txt)，消费者
（`.github/workflows/ci.yml`、`ci/pre-push`、`scripts/gen_galgame_stubs.py` 的 R 桩扫描、
`AGENTS.md` / `README.md` / 本文件的本地等价命令）一律从该文件读取，**不得再抄一份文件名列表**；
是否有第二份拷贝由 `bash ci/check-hotspot-sync.sh` 巡检。覆盖边界的完整声明见
`AGENTS.md`「类型门禁的覆盖边界」一节。

> ⚠️ 不要把清单条目硬编码进示例命令：那会让本地只编译清单的一个子集却报「通过」，
> 与 CI 结果不一致（假通过）。上面的示例用 `grep -vE '^[[:space:]]*(#|$)'` 从清单读取。

改桩时的两条红线：

- 桩签名以**真实源码为准**（直接读 `app/src/main/java` 下的对应类），不要为了「 compile 能过」而把桩写宽；
  例：`ContainerManager.duplicateContainerAsync(Container, Runnable)` 的真实签名必须一致。
- 新增热点文件 → 补对应桩后须重跑 `python scripts/gen_galgame_stubs.py` 重生 `R` 桩
  （`com.winlator.R` / `android.R` 由它自动生成，勿手改）；否则新文件的 `R.*` 引用会在
  热点步骤报缺符号。

Windows PowerShell（pwsh 7）等价形式，不依赖 `find` / `/tmp`：

```powershell
$out = Join-Path $env:TEMP 'galgame-prepush'
New-Item -ItemType Directory -Force "$out\stubs", "$out\check", "$out\hot" | Out-Null
javac -d "$out\stubs" (Get-ChildItem -Recurse -Filter *.java ci\stubs).FullName
javac -Xlint:all -cp "$out\stubs" -d "$out\check" (Get-ChildItem -Recurse -Filter *.java app\src\main\java\com\winlator\galgame).FullName
# 热点入口清单唯一来源：ci\hotspot-entries.txt（勿在此抄文件名列表）
$hot = Get-Content ci\hotspot-entries.txt | Where-Object { $_ -and $_ -notmatch '^\s*#' }
javac -Xlint:all -cp "$out\stubs;$out\check" -d "$out\hot" $hot
```

## 本地门禁：`ci/pre-push`（推送前类型门禁）

把上面那几条命令做成 git pre-push 钩子，**推送前几秒内**跑一遍与 CI 同口径的类型门禁，
避免「推上去才发现桩缺 API」——历史上 `Log.w` 三参重载、`Context.getFilesDir`、
`ValueAnimator.cancel` 三次 CI 失败都属于这一类，本地无门禁时只能等 CI 才发现。

启用（一次性，在仓库根目录执行；钩子不随 `git clone` 传播，每个 clone 各自启用）：

```bash
cp ci/pre-push .git/hooks/pre-push && chmod +x .git/hooks/pre-push
```

```powershell
# Windows PowerShell：无需 chmod，Git for Windows 用自带的 sh.exe 执行 #!/bin/sh 钩子
Copy-Item ci/pre-push .git/hooks/pre-push
```

不想安装钩子时，按上一节的 `javac` 命令手动跑门禁（Git-Bash / WSL 可直接 `sh ci/pre-push`，
它永远与钩子本体同口径；钩子如再扩展额外清单，以 `ci/pre-push` 为准）。跨 shell 的启用与调用说明同时写在根
`README.md`「本地门禁：推送前类型检查（pre-push）」一节。

`ci/pre-push` 是版本库内的唯一来源，改它之后需重新执行一次上面的安装命令。行为：

- 用 `javac`（PATH 或 `JAVA_HOME/bin`）编译 `ci/stubs`，再对 `com.winlator.galgame` 全树
  与热点入口清单跑 `-Xlint:all`；
- 任一环节失败即 **exit 1 阻断推送**，并提示按本文件补桩；
- 找不到 `javac` 时**优雅跳过并警告**（不阻断），避免把钩子变成环境依赖门禁；
- 临时产物写在 `.git/pre-push-out.<pid>`，退出时自动清理，不入库。

> **兜底口径**：上述「未装钩子」与「缺 javac 而放行」两种情况下，本地不会阻断推送，同口径检查由
> CI 兜底 —— `.github/workflows/ci.yml` 在 `push`（`galgame` / `main`）与 `pull_request` 上跑
> `galgame-compile` 类型门禁，差别只在发现时间（本地几秒 vs CI 数分钟）。

## 为什么不做完整 APK 构建
本 fork 是「框架-only」：`app/src/main/assets/**/*.tzst`（wine/box64 运行时）与
`app/src/main/jniLibs/**/*.so` 未随仓分发（体积/代理限制，且计划 §VIII 的口径是
「发框架、用户自取资源」）。要出完整 APK，需先按根目录 README 的说明补齐这些二进制，
再 `./gradlew assembleDebug`。CI 的 `galgame-compile` 保证**我们新增的代码**（galgame 子树
+ 热点入口清单）始终可编译；清单之外的官方/上游文件不在门禁内，这一覆盖边界在
`AGENTS.md`「类型门禁的覆盖边界」一节如实声明。
