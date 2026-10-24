# 零改铁律例外登记（入库版）

本文件是**入库、可移植**的例外登记，供任何克隆环境在改动下列官方核心文件之前核对。
此前该登记只存在于被 `.gitignore` 排除的 `docs/`（克隆后为空），改前核对无法执行。

- 校验实现：`ci/check-zero-mod.sh`（CI `rebase-guard` 与本地共用同一份）
- 触发口径：校验比较的是**推送/PR 范围内的改动文件**，因此"历史既有差异"不会被本次范围重新判定
- 基线事实由 git 派生，不靠记忆抄写；重新派生命令见文末

## 受守卫的官方路径

基线：`upstream/main = 8077f37`（2026-10-17 `git fetch upstream` 所得）；upstream 领先本 fork 5 笔，
本 fork 独有 144 笔。

| 路径 | 相对 upstream 的当前状态 | 登记结论 |
|---|---|---|
| `app/src/main/java/com/winlator/container/Container.java` | 与 upstream 一致 | 无例外，任何改动都应拦下 |
| `app/src/main/java/com/winlator/core/LocaleHelper.java` | `supportedLocales` 追加 `"zh_CN"`（+1 −1） | **已登记例外**：简体中文界面所需，`AGENTS.md` 称 A7，源自 b2b9e3e（2026-09-24）；rebase 时该差异会重新出现在推送范围里，属预期 |
| `app/src/main/res/values/strings.xml` | ① `app_name` Winlator → GalgameShell 并加一行注释；② 删除条目 `native_resolution` | ① **已登记例外**：品牌化所需，源自 1bf0011（2026-09-27），仅动 `app_name` 一行；② **rebase 阻断项，见下节** |
| `app/src/main/java/com/winlator/core/DefaultVersion.java` | `BOX64` 常量 `"0.4.4"` → `"0.4.5"`（+1 −1） | **已登记例外**：本 fork 自建 box64 的版本号，源自 ca5d411（0.4.5 build + mpshim heal shim）。上游文件，rebase 必冲突，属预期。它同时是 `tools/prepare_box64_lib.sh` 缺省打包哪份 tzst 的**唯一真源**——改这个值等于改构建默认，需与 `assets/box64/` 下实际存在的 tzst 对齐 |

## ② `native_resolution` 的删除：现在安全，rebase 后会破

事实（逐条实测，非推断）：

- 本 fork 的 `app/src/main/res/values/arrays.xml` 第 5 行是 `<item>640x360(16:9)</item>`，
  全仓 `app/src` 对 `native_resolution` 的引用数为 **0** ⇒ **今天**删掉它不影响本 fork 构建。
- 上游 `app/src/main/res/values/arrays.xml` 第 5 行是 `<item>@string/native_resolution</item>`，
  由 34df94f（Łukasz Ornatek，2026-09-14，"Native" screen size option，经 PR #67 合入）引入；
  该笔已包含在本次 fetch 到的 `upstream/main = 8077f37` 里。
- 因此 **本 fork 一旦按既定策略 rebase 到该上游**，上游的 `arrays.xml` 会引用这个字符串，
  而本 fork 的删除仍留在 diff 里 ⇒ 资源缺失。现有门禁看不见这条：类型门禁只对 `ci/stubs` 做
  `javac`，不校验资源；CI 仅校验 `galgame_strings.xml` 是合法 XML，不校验 `strings.xml` 条目完整性。

处置（本条即改前登记）：下次 rebase 时**撤销这条删除**，恢复
`<string name="native_resolution">Native</string>`。这是对官方文件的改动，会被零改守卫拦下，
所以在此预先登记为依据；恢复后用下面第三行的引用检查复验。在此之前**不动** `strings.xml`——
本 fork 现在没有引用，改动只会无谓触碰受守卫文件。

## 受守卫路径之外的 upstream 文件改动（登记备查）

零改守卫只拦上表三条路径，但 fork 对 upstream 的改动远不止这些（见 TD-08）。本节登记
**不属于守卫范围**、但确实改过的 upstream 文件，便于 rebase 时评估冲突面。

| 路径 | 改动内容 | 依据 | 为什么不放进 `com.winlator.galgame` |
|---|---|---|---|
| `app/src/main/cpp/winlator/src/xoutput_stream.c` | `XOutputStream_send` 由裸 `write()` 改为 `send(MSG_NOSIGNAL)` 循环写满；EINTR 重试；EAGAIN 时 `poll(POLLOUT)` 上限 2 秒；整体发送失败（含附带 fd 的短写）返回 false | 2026-10-24 `docs/box64-wine-launch-chain.md` §7.2 | X 协议写入是 JNI 原生实现，没有 Java 侧等价物，只能就地修 |

> 这是本 fork **第一处**对 upstream 原生 C 的改动——此前对 upstream 的改动全部落在
> Java / 资源层。rebase 时该文件若被上游改动，冲突需人工合并。

## 改动规则

1. 触碰上表任一路径前，先在本文件核对是否已有登记；没有登记即视为不接受。
2. 新增例外须在此写明：改动内容、依据的 fork 提交、为什么不能放到 `com.winlator.galgame` 新包或新资源文件里。
3. 已登记的例外在 rebase 时会重新出现于差异中，这是预期结果；若校验把它当新违规拦下，比对口径见上"触发口径"。

## 重新派生上述事实

```bash
git fetch upstream                       # 本表基线：8077f37（2026-10-17 fetch）
for f in app/src/main/java/com/winlator/container/Container.java \
         app/src/main/java/com/winlator/core/LocaleHelper.java \
         app/src/main/res/values/strings.xml; do
  printf '%-64s ' "$f"
  git diff --quiet upstream/main HEAD -- "$f" && echo SAME || git diff --numstat upstream/main HEAD -- "$f"
done
# native_resolution 引用复验（rebase 后两边都该 >0，且本 fork 的 strings.xml 必须重新定义它）
git grep -c native_resolution upstream/main -- app/src/main/res/values/arrays.xml
grep -c 'name="native_resolution"' app/src/main/res/values/strings.xml
```

> 表中数字是相对**本次 fetch 到的 upstream/main**，不是相对上游最新提交。引用本表前先跑上面的 `git fetch`。
