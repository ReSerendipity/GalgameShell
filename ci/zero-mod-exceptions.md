# 零改铁律例外登记（入库版）

> 定位（决策 A8）：本项目是独立项目，内嵌 winlator-app 为源码组件。本表的两重身份：
> ① LGPL-2.1 §2 对组件修改的**修改标注**；② 按需移植官方修复时的**冲突面清单**。
> 与「fork 靠 rebase 同步上游」的工作流已于 2026-10-25 终结：上游更新改为按需移植。

本文件是**入库、可移植**的例外登记，供任何克隆环境在改动下列官方核心文件之前核对。
此前该登记只存在于被 `.gitignore` 排除的 `docs/`（克隆后为空），改前核对无法执行。

- 校验实现：`ci/check-zero-mod.sh`（CI `rebase-guard` 与本地共用同一份）
- 触发口径：校验比较的是**推送/PR 范围内的改动文件**，因此"历史既有差异"不会被本次范围重新判定
- 基线事实由 git 派生，不靠记忆抄写；重新派生命令见文末

## 受守卫的官方路径

基线：`upstream/main = 8077f37`（2026-10-17 `git fetch upstream` 所得）；upstream 领先 5 笔，
本仓库独有 144 笔（fork 时代的历史计数；2026-10-25 起为独立项目，见决策 A8）。

| 路径 | 相对 upstream 的当前状态 | 登记结论 |
|---|---|---|
| `app/src/main/java/com/winlator/container/Container.java` | 与 upstream 一致 | 无例外，任何改动都应拦下 |
| `app/src/main/java/com/winlator/core/LocaleHelper.java` | `supportedLocales` 追加 `"zh_CN"`（+1 −1） | **已登记例外**：简体中文界面所需，`AGENTS.md` 称 A7，源自 b2b9e3e（2026-09-24）；rebase 时该差异会重新出现在推送范围里，属预期 |
| `app/src/main/res/values/strings.xml` | ① `app_name` Winlator → GalgameShell 并加一行注释；② 删除条目 `native_resolution` | ① **已登记例外**：品牌化所需，源自 1bf0011（2026-09-27），仅动 `app_name` 一行；② **rebase 阻断项，见下节** |
| `app/src/main/java/com/winlator/core/DefaultVersion.java` | `BOX64` 常量 `"0.4.4"` → `"0.4.5"`（+1 −1） | **已登记例外**：本仓库自建 box64 的版本号，源自 ca5d411（0.4.5 build + mpshim heal shim）。上游文件，日后移植官方改动时必冲突，属预期。它同时是 `tools/prepare_box64_lib.sh` 缺省打包哪份 tzst 的**唯一真源**——改这个值等于改构建默认，需与 `assets/box64/` 下实际存在的 tzst 对齐 |

## ② `native_resolution` 的删除：现在安全，rebase 后会破

事实（逐条实测，非推断）：

- 本仓库的 `app/src/main/res/values/arrays.xml` 第 5 行是 `<item>640x360(16:9)</item>`，
  全仓 `app/src` 对 `native_resolution` 的引用数为 **0** ⇒ **今天**删掉它不影响本仓库构建。
- 上游 `app/src/main/res/values/arrays.xml` 第 5 行是 `<item>@string/native_resolution</item>`，
  由 34df94f（Łukasz Ornatek，2026-09-14，"Native" screen size option，经 PR #67 合入）引入；
  该笔已包含在本次 fetch 到的 `upstream/main = 8077f37` 里。
- 因此 **若日后移植上游 34df94f（native_resolution）**，上游的 `arrays.xml` 会引用这个字符串，
  而本仓库的删除仍留在 diff 里 ⇒ 资源缺失。现有门禁看不见这条：类型门禁只对 `ci/stubs` 做
  `javac`，不校验资源；CI 仅校验 `galgame_strings.xml` 是合法 XML，不校验 `strings.xml` 条目完整性。

处置（本条即改前登记）：若移植该上游提交，须**撤销这条删除**，恢复
`<string name="native_resolution">Native</string>`。这是对官方文件的改动，会被零改守卫拦下，
所以在此预先登记为依据；恢复后用下面第三行的引用检查复验。在此之前**不动** `strings.xml`——
本仓库现在没有引用，改动只会无谓触碰受守卫文件。

## 受守卫路径之外的 upstream 文件改动（登记备查）

零改守卫只拦上表三条路径，但本仓库对 Winlator 组件的改动远不止这些（见 TD-08）。本节登记
**不属于守卫范围**、但确实改过的 upstream 文件，便于 rebase 时评估冲突面。

| 路径 | 改动内容 | 依据 | 为什么不放进 `com.winlator.galgame` |
|---|---|---|---|
| `app/src/main/java/com/winlator/services/ForegroundService.java`、`services/NotificationUtils.java` | 前台服务通知与默认通知的点击落地页由上游 `MainActivity` 改为 `GalgameMainActivity`（游戏库壳），并移除随之不再引用的 import | 2026-10-25 「退出后仍显示 WINLATOR 页」修复 | 通知构建在 services 层，galgame 包无等价注入点 |
| `app/src/main/java/com/winlator/XServerDisplayActivity.java`、`res/menu/xserver_menu.xml`、`res/layout/main_menu_header.xml` | ① `onBackPressed` 由上游「返回键开抽屉」改为「关抽屉→否则向游戏注入 ESC」（galgame 通用取消键；用户反馈返回键像「弹出 Winlator 页面」），抽屉入口改右上角 `ic_galgame_menu` 浮钮（PiP 时隐藏）；② `frameRating` 改为无条件创建（上游仅 HUDMode≠DISABLED 才建），否则游戏中途开 HUD 接不上已映射窗口的帧计数；③ 抽屉「性能显示」= **多项选择**（FPS/CPU 使用率/CPU 频率/系统内存/进程内存/GPU/负载/功耗，容器 extra `galgame_perf_items`，`GalgamePerfHud` 渲染；帧/GPU 信息由本 Activity 帧链转发）；④ 抽屉头部标语由共享的 `galgame_tagline` 改为独立的 `galgame_drawer_header`（「游戏菜单」），游戏中不再出现 "(Winlator-based)" 字样（About 页归属不变）；⑤ `onDestroy` 销毁 PerfHud | 2026-10-25 返回键 UX 修复 + HUD 开关；2026-10-25 第五轮 HUD 多项化 | 返回键/浮钮/HUD 开关都是 Activity 私有逻辑，galgame 包无等价注入点 |
| `app/src/main/java/com/winlator/core/ArrayUtils.java` | `indexOf(byte[],…)` 内层匹配循环补 `j < length` 边界：上游原实现只判 `j < end`，模式串前缀命中缓冲区尾部、部分匹配延伸出块时 `array[j]` 越界（AIOOBE length=4096 index=4096）。真机触发链：`FrameRating.run`→`Box64Utils.extractBinVersion`（4096 分块读 box64 二进制找版本串）→本方法；第四轮「无条件创建 FrameRating」把上游这个潜伏 bug 暴露成随机启动 wine 路线游戏必崩 | 2026-10-25 随机启动闪退修复（真机 FATAL 取证：test_siglus 启动即崩） | 数组工具无子类化注入点；越界修复必须落在原方法 |
| `app/src/main/java/com/winlator/widget/FrameRating.java` | `run()` 的 `Box64Utils.extractBinVersion`（主线程读数 MB 二进制）包 try-catch：任何异常降级 "Box64 v?" 并缓存，不再每帧重试 IO | 2026-10-25 随机启动闪退修复（防御层） | 上游类内部逻辑，无等价注入点 |
| `app/src/main/java/com/winlator/XServerDisplayActivity.java`（第六轮追加） | ① galgame 直启（exec_path）不再创建上游 FrameRating（用户反馈关不掉+双浮窗：上游 update() 每帧拉回 VISIBLE + 容器残留 FULL 档），帧/GPU 读取与实例解耦（PerfHud 独立接帧）；② 游戏内菜单高级项按设置页开关显隐（setMenuVisible，默认隐藏） | 2026-10-25 第六轮 HUD 重复修复 + 菜单开关 | Activity 私有逻辑，galgame 包无等价注入点 |
| `app/src/main/res/layout/preloader_dialog.xml` | 启动加载浮层品牌化：全屏 90% 黑遮罩 + GALGAMESHELL 字距标 + 主文案 + 2dp 细进度条（Swiss 纸面语言），不再引用上游 `preloaderBackground` / `progressBarIndeterminateImage` attr；`@+id/TextView` 保留供 `PreloaderDialog.setText` | 2026-10-25 用户反馈「启动加载还是原版 UI」 | 布局被上游 `PreloaderDialog.setContentView` 固定引用，替换视图需改上游类，收益不成比例 |
| `app/src/main/java/com/winlator/renderer/GLRenderer.java` | `collectRenderableWindows` 的 `inBounds` 上界由 `width < screen` 放宽为 `<=`：窗口恰好等于虚拟屏尺寸时也获得居中的 FullscreenTransformation。KiriKiri 把主窗口固定在 (152,152)，等尺寸窗口右/下边缘被裁（TD-14 残留①） | 2026-10-25 全屏修复（窗口铺满 P2） | 窗口入格判定是 renderer 私有循环条件，galgame 包无等价注入点 |
| `app/src/main/cpp/winlator/src/xoutput_stream.c` | `XOutputStream_send` 由裸 `write()` 改为 `send(MSG_NOSIGNAL)` 循环写满；EINTR 重试；EAGAIN 时 `poll(POLLOUT)` 上限 2 秒；整体发送失败（含附带 fd 的短写）返回 false | 2026-10-24 `docs/box64-wine-launch-chain.md` §7.2 | X 协议写入是 JNI 原生实现，没有 Java 侧等价物，只能就地修 |

> 这是本仓库**第一处**对 Winlator 组件原生 C 的改动——此前对组件的改动全部落在
> Java / 资源层。rebase 时该文件若被上游改动，冲突需人工合并。

## 改动规则

1. 触碰上表任一路径前，先在本文件核对是否已有登记；没有登记即视为不接受。
2. 新增例外须在此写明：改动内容、依据的本仓提交、为什么不能放到 `com.winlator.galgame` 新包或新资源文件里。
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
# native_resolution 引用复验（移植上游 34df94f 后两边都该 >0，且本仓库的 strings.xml 必须重新定义它）
git grep -c native_resolution upstream/main -- app/src/main/res/values/arrays.xml
grep -c 'name="native_resolution"' app/src/main/res/values/strings.xml
```

> 表中数字是相对**本次 fetch 到的 upstream/main**，不是相对上游最新提交。引用本表前先跑上面的 `git fetch`。
