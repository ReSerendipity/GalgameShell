# GalgameShell AGENTS.md — AI 辅助开发指南

本文件是**被跟踪、可移植**的代理指令：陌生 agent 克隆本仓库后，照此即可行动。
纯本地备忘一律留在被忽略的 `docs/`（见下文分区约定）。

## 📂 对内/对外文档分区（最高优先级约定）

- **根目录 `README.md` = 对外展示**：面向 GitHub 访客与用户，只写「这是什么、怎么用、怎么参与」。
- **`docs/` = 对内项目说明的唯一位置**：内部开发进度、决策记录、发布备忘、维护者备注等一律写入 `docs/`。
  该目录与 `docs/README.internal.md` 由 `.gitignore` 排除、**仅本地保留**：克隆后看不到、也不要创建可入库的同名副本。
- 维护者备忘不得写回根 README；AI 更新文档时先判断「陌生用户读了能否行动」，不能行动的进 `docs/`。

## 文档优先级

1. 代码与配置本身
2. [AGENTS.md](AGENTS.md)
3. [README.md](README.md) / 对内说明入口 [docs/README.internal.md](docs/README.internal.md)

## 🚫 零改铁律边界（CI `rebase-guard` 强制）

本 fork 基于官方 winlator-app，靠 rebase 同步上游。**不得修改上游核心文件**——
CI 用 `git diff` 名单比对这些**官方真实路径**（`ci/stubs/` 下的同名桩不受该判定影响）：

- `app/src/main/java/com/winlator/container/Container.java`
- `app/src/main/java/com/winlator/core/LocaleHelper.java`
- `app/src/main/res/values/strings.xml`（现有条目）

命中即 CI 失败。galgame 价值一律放新包 `com.winlator.galgame` + 新资源文件
（`res/values/galgame_strings.xml` 等）。登记例外（如 A7 `LocaleHelper.supportedLocales`
增加 `zh_CN`）记在被忽略的 `docs/`，改动这些文件前须先核对既有登记。

## ✅ 被 CI 强制的门禁清单（`.github/workflows/ci.yml`）

CI 在**无 Android SDK、无大二进制**的条件下可跑；`./gradlew assembleDebug` 不是 CI 门禁
（本仓按设计不含 `*.tzst` / `jniLibs/*.so`，clone 后无法直接出完整 APK，补齐口径见 `README.md`）。

| job | 门禁内容 |
|---|---|
| `rebase-guard` | 零改铁律校验；`engine_presets.json` / `galgame_fonts.json` 可解析；`galgame_strings.xml` 为合法 XML |
| `galgame-compile`（依赖 `rebase-guard`） | `com.winlator.galgame` 全树 + 热点入口清单 `javac -Xlint:all` 类型门禁（对 `ci/stubs`）；EngineDetector 回归测试 `DetectTest` |

### 类型门禁的覆盖边界（如实声明）

- 编译范围 = `com.winlator.galgame` **整子树** + **显式热点清单**（当前为
  `ContainersFragment.java`、`GalgameHost.java`、`GalgameHomeFragment.java`，与
  `com.winlator` 顶层同名文件同目录）。清单以文件列表形式硬编码在
  `.github/workflows/ci.yml` 与 `ci/pre-push` 中，两处必须同步修改；
  `R` 桩扫描范围由 `scripts/gen_galgame_stubs.py` 的 `SRC` + `HOT` 维护，改热点清单后须重跑。
- **清单之外的其余 `com.winlator.*` 官方/上游文件不在任何门禁内**；热点类依赖的宽
  依赖官方类（如 `ContainerDetailFragment`）在 `ci/stubs` 中只有同名类型桩，桩不校验
  真实实现，改动这类官方文件时门禁不会报警。
- 门禁通过 ≠ APK 可构建：`ci/stubs` 是签名最小桩而非 Android SDK/Gradle 环境，
  资源管道、依赖项、native 构建均不在门禁范围内（无 SDK/Gradle 是设计约束，见 `ci/README.md`）。

### 类型门禁：本地等价命令（与 CI 同口径）

在仓库根目录执行（需 `javac` 于 `PATH` 或 `$JAVA_HOME/bin`）：

```bash
rm -rf /tmp/stubs-out /tmp/check /tmp/hot
mkdir -p /tmp/stubs-out /tmp/check /tmp/hot
javac -d /tmp/stubs-out $(find ci/stubs -name '*.java')
javac -Xlint:all -cp /tmp/stubs-out -d /tmp/check $(find app/src/main/java/com/winlator/galgame -name '*.java')
# 热点入口清单（与 ci.yml / ci/pre-push 同列表；GalgameHost.java 为真实源码随编译）
javac -Xlint:all -cp /tmp/stubs-out:/tmp/check -d /tmp/hot \
  app/src/main/java/com/winlator/ContainersFragment.java \
  app/src/main/java/com/winlator/GalgameHost.java \
  app/src/main/java/com/winlator/GalgameHomeFragment.java
```

失败含义：新代码用到了 `ci/stubs` 里没有的 API → 按 `ci/README.md` 补桩后重试。
同口径的 pre-push 本地钩子（一次性启用，此后每次 `git push` 前自动跑）：

```bash
cp ci/pre-push .git/hooks/pre-push && chmod +x .git/hooks/pre-push
```

### DetectTest 回归：CI 中的原样命令

须在上面两步之后执行（复用 `/tmp/check` 产物）：

```bash
mkdir -p /tmp/detect-out
javac -cp /tmp/stubs-out:/tmp/check -d /tmp/detect-out tools/detect-test/DetectTest.java
java -cp /tmp/stubs-out:/tmp/check:/tmp/detect-out DetectTest
```

本地一键版：`bash tools/run_detect_test.sh`（退出码非 0 即有用例失败）。
