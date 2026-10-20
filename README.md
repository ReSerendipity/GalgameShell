# GalgameShell — GalGame Android Shell

基于官方 [`brunodev85/winlator-app`](https://github.com/brunodev85/winlator-app)（LGPL-2.1）的 galgame 专用外壳：以 Winlator 为模拟层基座，自动识别游戏引擎，在「内嵌原生引擎 / 原生播放器 (B) / 模拟 (A)」之间智能路由，让 galgame 在 Android 上导入即玩。

> **GalgameShell** is a galgame-dedicated Android shell built on the official [Winlator](https://github.com/brunodev85/winlator-app) (LGPL-2.1). It detects each game's engine automatically and routes between the embedded native engines (Ren'Py / Kirikiri2), an optional external native player, and the Winlator emulation layer — import a game, then play.

## 功能亮点

- **导入即玩**：选定游戏目录后，自动创建专属容器、复制游戏文件、注入运行环境，导入完成即可从库中启动，无需手工配置 Wine 容器。
- **内嵌原生引擎**：APK 内嵌 Ren'Py 引擎运行时（`librenpython.so`，Python 3.12.8 + Ren'Py 8.5.3，真机已跑通真实商业游戏《候鸟》全链路）与 Kirikiri2 引擎运行时（内嵌 Kirikiroid2，`libgame.so`，真机验证通过），识别到对应引擎的游戏由内嵌引擎进程内直启渲染；Winlator 容器引擎承载其余模拟游戏。
- **引擎识别与 A/B 双路由**：通过引擎指纹判断游戏引擎并自动选择运行方式——适合原生运行的游戏优先走内嵌原生引擎（B 路由默认渲染路线）；内嵌引擎不可用时可唤起外部原生播放器作为可选回退（闭源原生播放器默认关闭，需在设置中显式开启），其余游戏走 Winlator 模拟层；检测到加密仅作提示，不破解。
- **一键日文化**：自动注入日文区域环境（`LC_ALL` / `LANG` 环境变量与区域注册表）并配置日文字体，解决日文游戏乱码问题（字体文件不随应用捆绑）。
- **游戏内视频正常播放**：自动启用 DirectShow / Windows Media 解码，老游戏的开场动画与过场视频可正常播放。
- **存档重定向**：存档统一重定向到 S: 盘与 Shell Folder，便携存档自动「先搬后链」，并支持存档导出与恢复。
- **诊断向导与日志**：GPU、日文显示、视频解码、位宽、音频、加密逐项检查，配合日志查看（`logs.txt` / `stderr` / DXVK，自动提取要害行），启动失败时按向导逐项排查。
- **统一的游戏库界面**：导入、启动、诊断、日志、存档、原生路由开关集中在一处。

## 获取运行时资源

本仓库**按设计不附带大二进制运行时资源**：Wine / Box64 运行时（`app/src/main/assets/**/*.tzst`）与各架构原生库（`app/src/main/jniLibs/**/*.so`）不随源码仓分发（为控制仓库体积，运行时资源由使用者自行获取）。

因此 clone 之后**不能直接 `./gradlew assembleDebug` 出完整可用的 APK**，需先从上游补齐这部分资源：

```bash
# 从上游取回被排除的运行时资源与 native 库（只落工作区，不写索引）
git remote add upstream https://github.com/brunodev85/winlator-app   # 若尚未添加
git fetch upstream
git restore --source=upstream/main --worktree -- app/src/main/assets app/src/main/jniLibs
```

> ⚠️ 不要用 `git checkout upstream/main -- <路径>` 取这些文件：带 tree-ish 的 checkout 会**同时写索引**，
> 而 `.gitignore` 对已入索引的路径不再生效，实测会把 2 个 `*.tzst` 变成 `A`（已暂存）状态——
> 随后一次 `git commit` / `git commit -a` 就把本仓按设计排除的大二进制提交进来。
> 用上面的 `git restore --worktree` 形式，工作区拿到文件、`git status` 保持干净。

## 系统要求与免责声明

### 系统要求

- Android 11（API 30）或更高版本
- ARM64 设备，需支持 Vulkan 1.1+
- 存储空间预留 8–16 GB；建议 12 GB 及以上 RAM
- 不支持无 Vulkan 能力的设备（如 Kirin 平台）；Mali GPU 可走 Vortek / VirGL 兜底

### 免责声明

- 本应用仅为 galgame 运行外壳，**不含任何游戏本体，不提供破解**；请自备合法获得的游戏副本。
- 检测到加密的游戏仅作提示，不做破解。
- 内嵌原生引擎（Ren'Py / Kirikiroid2）按其各自开源许可随 APK 分发，许可信息见 [`NOTICE`](NOTICE)；第三方原生播放器为可选回退唤起功能，使用风险自负。
- 开源许可与源码获取方式见应用内「关于」及 [`NOTICE`](NOTICE)。

## 无需补齐二进制的最小验证路线

完整 APK 构建（下一节「构建」中的 `./gradlew assembleDebug`）有前置条件：必须**先完成上文的「获取运行时资源」**（`git restore --source=upstream/main --worktree -- app/src/main/assets app/src/main/jniLibs` 取回 `.tzst` 与 `.so`），否则无法产出完整可用的 APK。

若只想在 clone 后立刻验证框架侧代码，可不补齐任何大二进制，直接执行与 [`.github/workflows/ci.yml`](.github/workflows/ci.yml) 中 `galgame-compile` job 完全一致的三步检查（仅需 JDK 17，无需 Android SDK；覆盖范围为 `com.winlator.galgame` 全树 + 热点入口清单，边界与补桩方法见 [`ci/README.md`](ci/README.md)）：

```bash
# 1) 类型门禁：com.winlator.galgame 全树 + 热点入口清单（对 ci/stubs，命令与 ci.yml "类型门禁" step 一致）
rm -rf /tmp/stubs-out /tmp/check /tmp/hot
mkdir -p /tmp/stubs-out /tmp/check /tmp/hot
javac -d /tmp/stubs-out $(find ci/stubs -name '*.java')
javac -Xlint:all -cp /tmp/stubs-out -d /tmp/check $(find app/src/main/java/com/winlator/galgame -name '*.java')
javac -Xlint:all -cp /tmp/stubs-out:/tmp/check -d /tmp/hot \
  $(grep -vE '^[[:space:]]*(#|$)' ci/hotspot-entries.txt)

# 2) EngineDetector 回归测试（命令与 ci.yml "EngineDetector 回归测试" step 一致）
mkdir -p /tmp/detect-out
javac -cp /tmp/stubs-out:/tmp/check -d /tmp/detect-out tools/detect-test/DetectTest.java
java -cp /tmp/stubs-out:/tmp/check:/tmp/detect-out DetectTest
```

## 本地门禁：推送前类型检查（pre-push）

CI 的 `galgame-compile` 只在代码推上去之后才跑，而它的历史红灯全来自同一类问题：新代码用了 `ci/stubs` 里没有的 API。[`ci/pre-push`](ci/pre-push) 把同一套三步 `javac`（galgame 全树 + 热点入口清单）做成 git 钩子，让这类问题在本地 `git push` 前几秒暴露。

钩子位于 `.git/hooks/`，**不随 `git clone` 传播**，每个 clone 需各自启用一次（一次性，在仓库根目录执行，按宿主 shell 二选一）：

```bash
# POSIX shell / Windows Git-Bash
cp ci/pre-push .git/hooks/pre-push && chmod +x .git/hooks/pre-push
```

```powershell
# Windows PowerShell 7（无需 chmod：Git for Windows 用自带的 sh.exe 执行这个 #!/bin/sh 钩子，
# 不需要另装 bash，也不需要 WSL）
Copy-Item ci/pre-push .git/hooks/pre-push
```

不想动 `.git/hooks`，也可以直接手动跑门禁（仅需 JDK，产物落系统临时目录，不入库）。下面等价于钩子的核心三步（编译 `ci/stubs` + 校验 `com.winlator.galgame` 全树 + 热点入口清单）：

```powershell
$out = Join-Path $env:TEMP 'galgame-prepush'          # 钩子核心三步的 PowerShell 等价形式
Remove-Item -Recurse -Force $out -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$out\stubs", "$out\check", "$out\hot" | Out-Null
javac -d "$out\stubs" (Get-ChildItem -Recurse -Filter *.java ci\stubs).FullName
javac -Xlint:all -cp "$out\stubs" -d "$out\check" (Get-ChildItem -Recurse -Filter *.java app\src\main\java\com\winlator\galgame).FullName
# 热点入口清单唯一来源：ci\hotspot-entries.txt（跳过注释与空行，勿在此抄文件列表）
$hot = Get-Content ci\hotspot-entries.txt | Where-Object { $_ -and $_ -notmatch '^\s*#' }
javac -Xlint:all -cp "$out\stubs;$out\check" -d "$out\hot" $hot
if ($LASTEXITCODE -eq 0) { '[gate] PASS' } else { '[gate] FAIL：新代码用到了 ci/stubs 里没有的 API，按 ci/README.md 补桩' }
```

Git-Bash / WSL 下则直接执行钩子本体（`sh ci/pre-push`），它与已安装的钩子永远同口径；钩子如再扩展额外清单（见 [`ci/pre-push`](ci/pre-push) 末尾），以该文件为准。

> **未安装钩子时的兜底**：由 CI 承担 —— [`.github/workflows/ci.yml`](.github/workflows/ci.yml) 在 `push`（`galgame` / `main`）与 `pull_request` 上跑同一套类型门禁，红灯照样出现，只是发现时间从本地几秒推迟到 CI 数分钟。同理，钩子在 `PATH` 与 `JAVA_HOME` 里都找不到 `javac` 时**打印警告后放行**（不阻断推送，以免把本地门禁变成环境依赖门禁），这一路同样由 CI 兜底。补桩方法与钩子行为细节见 [`ci/README.md`](ci/README.md)。

## 构建

1. 配置 Android SDK：在项目根目录 `local.properties` 中设置 `sdk.dir`。
2. 编译调试包（需先按上文「获取运行时资源」补齐运行时资源）：

   ```bash
   ./gradlew assembleDebug
   ```

3. （可选，分发 APK 前）生成运行时资源 SHA-256 校验清单并随包分发，供接收方核验
   `.tzst` / `.so` 完整性：

   ```bash
   bash tools/make_runtime_checksums.sh   # 输出 RELEASE-RUNTIME-CHECKSUMS.txt（勿提交，随包分发）
   ```

## 参与方式

- 欢迎提交 Issue 与 Pull Request。本仓采用 DCO：所有提交须带 `Signed-off-by` 签署（`git commit -s` 自动追加），CI 会在 PR 上逐提交校验；本地门禁、提交约定与许可证要求见 [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md)。
- 改动上游核心文件前，先核对入库的例外登记 [`ci/zero-mod-exceptions.md`](ci/zero-mod-exceptions.md)。
- 项目文档（架构评审、决策记录、技术债登记等）随仓库分发在 [`docs/`](docs/) 下，clone 后可见。

## 许可证

LGPL-2.1（继承自 winlator-app）。本增量层同样以 LGPL-2.1 发布。
第三方组件与字体政策详见 [`NOTICE`](NOTICE)。
