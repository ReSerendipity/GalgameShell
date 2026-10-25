# 真机验证报告（GalgameShell）

> 覆盖 2026-10-22 ～ 2026-10-24 三轮真机验证。**只写有证据的结论**；测不到、没测的格子单列。
> 证据文件在仓外（各会话的本地工作区），本文件记录结论、判据与复现方式。

---

## 1. 设备与构建基线

| 项 | 值 |
|---|---|
| 设备 | Realme RMX5010 |
| GPU | Adreno 830（Qualcomm），Vulkan |
| 系统 | Android 16 / arm64-v8a |
| 屏幕 | 1264 × 2780，560 dpi |
| 容器根 | `files/rootfs/home/xuser-N`（应用私有目录） |
| 图形栈 | turnip 26.2.0 + gladio 1.1；dxwrapper = dxvk 2.4.1 / vkd3d 2.14.1 |
| Wine | 10.10（Winlator 11.2 rootfs，上游预编译） |
| box64 | 0.4.5（自建，含设备私有补丁）；`0.4.5-gstfix` 已入库但**不作构建默认** |
| 被测包 | v11.3 release（`versionCode` 34）；取证期用同签名 debug 包（可 `run-as`） |

> debug 与 release 用同一 debug keystore 签名，互相覆盖安装不丢容器数据——取证期用它
> 拿到 `run-as` 与导出的 `XServerDisplayActivity`。

## 2. 结论速览

> **本节是 2026-10-22～24 的首轮结论，只到"渲染级/启动级"。**
> 2026-10-24 追加的**逐游戏实际游玩验证**（17 个样本、每款约 60 秒输入）见 **§7**——
> 那里有 8 个可玩、4 类失败归因，口径比本节更严格。

验证严格区分两个层级：**渲染级**（真的看到画面）与**启动级**（launcher 已派发到
`XServerDisplayActivity`，渲染结果未评估）。批次脚本只记录了后者，不能混为一谈。

### 2.1 渲染级：通过

| 游戏 | 容器 | 引擎 | 结果 | 证据 |
|---|---|---|---|---|
| 《花瓣！》`HANA9cn.EXE` | xuser-1 | 未识别（32 位 exe） | ✅ 标题画面完整渲染 | 2026-10-24 release 11.3 经 UI 启动截图；同日 debug 包直启截图 |

这是六层启动链的回归锚点（见 `docs/box64-wine-launch-chain.md` §1），三轮验证都过。

### 2.2 渲染级：失败（已定位根因）

| 游戏 | 容器 | 引擎 | 现象 | 根因 |
|---|---|---|---|---|
| `yuris_lufia` | xuser-10 | YU-RIS | 图建成、零报错，画面全黑 | Wine 内建 quartz 的 `put_WindowStyle` → `SetWindowPos` 跨线程 `SendMessage` 永不返回，`IMediaControl::Run` 从未调用（§7.2，TD-09） |
| `sysnnn_sainogami` | xuser-15 | SystemNNN | 全黑 | 同上（A/B 两种 `quartz` 覆盖均黑） |

判据是 `WINEDEBUG=quartz,msg` 下最后一条跨线程消息无 `retrieve_reply`，且 7 份日志里
`Run` 命中数为 0（`MediaControl_Stop` 命中 4，排除"没开通道"）。

### 2.3 启动级：已派发，渲染未评估

| 游戏 | 容器 | 引擎 | launcher 结果 |
|---|---|---|---|
| `yuris_karakoi` | xuser-11 | YU-RIS | `ok=true`，前台进入 `XServerDisplayActivity` |
| `bgi_ryo` | xuser-12 | BGI | 同上 |
| `siglus_sayaga` | xuser-13 | SIGLUS | 同上 |

> 这三条**只证明启动路由正确**，没有做渲染级判据，不能算"可玩"。

### 2.4 启动级：未派发（停在库页）

| 游戏 | 容器 | 引擎 | 记录 |
|---|---|---|---|
| `unity_nekoya` | xuser-8 | UNITY | 前台仍是 `GalgameMainActivity` |
| `yuris_nyonin` | xuser-9 | YU-RIS | 同上 |
| `advhd_toran` | xuser-14 | AdvHD | 同上；另有 `icu.dll` 前置阻塞 |

批次脚本没记录各自的失败原因，只有"没进游戏"这个事实。

## 3. 界面与工程项验证

| 项 | 结果 | 判据 |
|---|---|---|
| 库页 | ✅ | 19 条记录正常渲染（release 11.3） |
| 详情页 | ✅ | `hana9test` 详情页正常，「启动」按钮可用 |
| 明暗双档 | ✅ | 亮色纸面 RGB(255,255,255)、深色纸面 RGB(23,22,31)；深/亮各做 force-stop + 冷启动均保持所选档 |
| 深色下各内页 | ✅ | 设置 / 详情 / 诊断 / 导入 四屏均正常 |
| 版本号显示 | ✅ | 标题栏显示 `GALGAMESHELL / 11.3`（修硬编码后复验） |
| 子目录 `.xp3` 重检测升级 | ✅ | `登记=UNKNOWN 实测=KIRIKIRI`，成功转入 KR2；导入日志标签同时验证 |
| 运行环境重新注入语言字体 | ✅ | 容器 `windows/Fonts` 出现 `NotoSansCJK-Regular.ttc`，overlay 写回 `font_face` |
| X 写入修复（v11.3） | ✅ | 锚点正常渲染、应用无崩溃、`wine_exec.log` 里 X 写入错误 0 条 |

## 4. 未覆盖的格子（如实声明）

- **渲染级只覆盖 1 个游戏通过 + 2 个失败**。其余容器没有做过渲染判据。
- `yuris_karakoi` / `bgi_ryo` / `siglus_sayaga` 只到启动级。
- `unity_*` 系列（`test_unity` / `unity_shina` / `unity_swan` / `unity_nekoya`）在不同
  DLL 覆盖下表现不一致（曾观察到 `err:seh:NtRaiseException c0000005`），未形成结论。
- `advhd_toran` 有 `icu.dll` 前置阻塞，到不了视频/渲染环节。
- 没有做长时间稳定性测试（连续游玩、发热降频、内存增长）。
- 没有做存档读写的完整回归（只在诊断页看到 S: 盘重定向生效）。

## 5. 复现方式

```bash
# 设备与包
adb devices -l
adb shell dumpsys package com.winlator | grep -E 'versionName|versionCode'

# 容器 → 游戏映射（release 包需 debug 包才能 run-as）
adb shell "run-as com.winlator sh -c \
  'for d in files/rootfs/home/xuser-*; do echo \$(basename \$d): \$(ls \$d/.wine/drive_c/galgame 2>/dev/null | tr \"\n\" \",\"); done'"

# 直启某容器（仅 debug 包：release 不导出 XServerDisplayActivity）
adb shell am start -n com.winlator/.XServerDisplayActivity \
  --ei container_id 1 \
  --es exec_path "/data/data/com.winlator/files/rootfs/home/xuser-1/.wine/drive_c/galgame/hana9test/HANA9cn.EXE"

# 取 Wine 日志（debug 包）
adb shell "run-as com.winlator cat files/wine_exec.log" > wine_exec.log

# 打开某组调试通道
#   设置里「Wine 调试」开，通道填 quartz,ole,msg（对应 prefs 的 enable_wine_debug / wine_debug_channels）

# release 包走正常 UI：库 → 点卡片 → 详情页「启动」
```

## 6. 证据索引（仓外，本机）

| 内容 | 路径 |
|---|---|
| 主题亮暗验收汇总 | 会话 `aacde7da` 的 `galgame-verify-20261022/final/165-theme-verification.txt` |
| 子目录 xp3 重检测 | 同上 `final/142-xp3-subdir-redetect.{txt,png}` |
| 导入日志标签 | 同上 `final/143-import-log.txt` |
| Swiss UI 亮/暗截图 | 同上，会话 `3e69ebfb` 的 `apk_shots/` |
| 视频链决定性 trace | 同上，会话 `3e69ebfb` 的 `logs/lufia_msg_probe.log` |
| 视频链完整函数表 | 同上 `logs/lufia_render_probe1.log` |
| 尺寸对照实验 | 同上 `logs/lufia_1920x1080.log` |
| quartz A/B 差分 | 同上 `logs/diagA_builtin.log`、`diagB_native.log` |
| 批次启动结果 | 同上 `batch_results.json` |
| box64 重建/链接日志 | 同上 `logs/box64_build.log`、`box64_link2.log` |

> 这些证据不入库（体积与机器路径），需要归档时应在仓内另建一份脱敏摘要。

---

## 7. 逐游戏「实际游玩」验证（2026-10-24）

前几节只到"渲染"与"启动"两级。本节按**能否真的玩起来**做验证：启动后给予约 60 秒
连续输入，再判定画面是否进入可交互状态。

### 7.1 方法

- **输入**：右侧菜单区垂直扫点（命中 Start/开始，不依赖具体菜单坐标）→ 中心点击 +
  `KEYCODE_ENTER` + `KEYCODE_SPACE` 轮换，共约 60 秒
- **取证**：启动后 12s / 中段 / 结束各截一帧
- **判据**：截图亮度均值、非黑像素占比、帧间哈希差；再对每类抽查目视确认

### 7.2 结果

样本全部取自 `D:\need load`（79 个归档）中体积最小的代表，按引擎分组。

| 类型 | 游戏 | 结果 | 依据 |
|---|---|---|---|
| KIRIKIRI | `krkr_caste`（カースト上位の牝たち…） | ✅ **可玩** | IPPA 警告页 → 标题页，菜单可点 |
| KIRIKIRI | `krkr_munsei`（母性女友2） | ✅ **可玩** | 厂牌 splash → 游戏内「退出确认」对话框 |
| KIRIKIRI | `krkr_tokekaze` / `krkr_tobenai` | ⚠️ **KiriKiri 脚本错误** | `Member 'win'/'wf' does not exist`（见 §7.4 归因存疑） |
| SIGLUS | `hana9test`（花瓣！） | ✅ **可玩** | 标题页 → 菜单/Extra 可导航 |
| SIGLUS | `test_siglus`（モザイクの天使） | ✅ **可玩** | 标题页 START/CONTINUE/… 菜单 |
| SIGLUS | `siglus_sayaga`（サヤガタリ） | ✅ **可玩** | 标题页 はじめから/つづきから… |
| UNITY | `test_unity`（KamikuraHina） | ✅ **可玩** | 进入游戏内 GALLERY 界面 |
| UNITY | `unity_shiina`（椎名真穂） | ✅ **可玩** | 标题页 GAME START/… 菜单 |
| UNITY | `unity_swan`（The Weeping Swan） | ✅ **可玩** | 标题页 スタート/ロード/… |
| UNITY | `unity_nekoya` | ❌ 未进入游戏 | 启动后回到库页，原因未定 |
| YU-RIS | `yuris_lufia`（REIJUNOACOLYTE） | ❌ 全黑 | 状态栏以下 0 个亮像素 |
| YU-RIS | `yuris_nyonin`（にょにんじま2） | ❌ 全黑 | 仅左侧 6px 竖线，其余全黑 |
| YU-RIS | `yuris_karakoi` | ❌ **DRM 拦截** | 弹出 **SdWrap / ソフト電池（SoftDenchi）** 对话框，要求安装软电池运行时 |
| ADVHD | `test_advhd` / `advhd_toran` | ❌ 未进入游戏 | 启动后回到库页 |
| BGI | `bgi_ryo` | ⚠️ 方法受限 | 日文 exe 名需 `exec_path_wine`，本轮参数转义失败（见 §7.4） |
| SystemNNN | `sysnnn_sainogami` | ❌ 全黑 | 状态栏以下仅 4px 竖线 |

**通过率**：17 个样本中 **8 个可玩**，全部集中在 KIRIKIRI / SIGLUS / UNITY 三类；
YU-RIS / SystemNNN / ADVHD / BGI 无一通过。

### 7.3 失败归类

| 类别 | 游戏 | 性质 |
|---|---|---|
| **DRM 拦截** | `yuris_karakoi` | 非缺陷：SdWrap/SoftDenchi 商业加密，按 A5 决策只提示不破解 |
| **黑屏（TD-09）** | `yuris_lufia` / `yuris_nyonin` / `sysnnn_sainogami` | Wine 内建 quartz 的 `put_WindowStyle` 跨线程死锁 |
| **KiriKiri 脚本错误** | `krkr_tobenai`（及疑似 `krkr_tokekaze`） | `Member 'win'/'wf' does not exist` —— 游戏脚本用到 Kirikiroid2 当前版本没有的引擎成员 |
| **未进入游戏** | `unity_nekoya` / `test_advhd` / `advhd_toran` | 启动后即回到库页，本轮未取到原因 |

### 7.4 方法与局限（重要，避免误读）

1. **两条启动路径不同**。容器类游戏（SIGLUS/UNITY/YU-RIS/ADVHD/BGI/SystemNNN）
   走 `am start XServerDisplayActivity`；KIRIKIRI 走「详情页 → 启动」→ KR2。
   前者跳过了 `GameLauncher` 的路由决策，**只验证引擎运行，不验证路由**。
2. **非 ASCII exe 名必须同时传 `exec_path_wine`**（`C:\` 路径）。本轮 `bgi_ryo`
   两次尝试都因 shell 反斜杠转义失败而报
   `wine: failed to open "C:galgamebgi_ryo\..."`——**这是验收脚本的缺陷，不是应用缺陷**，
   该游戏结论**未定**。
3. **KR2 的「启动最近游戏」按的是引擎自己的持久列表索引**，不保证是刚写入的那一项。
   因此 `krkr_tokekaze` 的报错标题显示的是「飛べない蝶のバレンタイン」（另一样本），
   这两条的归属**存疑**；能确认的是 KR2 对它们报了 KiriKiri 脚本错误。
4. **「可玩」的判据是"进入可交互画面并响应输入"**，不是"通关"或"玩了很久"。
   多数样本停在标题页/画廊，未验证剧情推进、存档读写、长时间稳定性。

### 7.5 证据

截图：`_test/playtest/<label>_t0|t30|t60.png`（17 组，仓外）。
量化表：同目录 `results.txt` + 本地 `analyze_playtest.py` 输出。

---

## 8. 两项绕过实验（2026-10-24）

本节验证 §7 里两类失败是否有可行绕过。

### 8.1 YU-RIS 片头：禁用 quartz → 致命错误，无法跳过 ❌

思路：让 DirectShow 直接失败（`quartz=disabled`），很多引擎会静默跳过 OP 继续进正片。

实测（容器 xuser-10 / `yuris_lufia`，仅改注册表覆盖，其余不变）：

| 配置 | 结果 |
|---|---|
| `quartz=native,builtin`（原状） | 全黑挂死（TD-09） |
| `quartz=disabled` | 不再挂死，弹出 YU-RIS 错误框：`data\script\eris\es_movieplay.yst` 第 85 行「ムービー再生に失敗しました。」，按钮是 **終了(X)** |

**结论**：引擎把"影片播放失败"当**致命错误**并退出，不会跳过片头。所以
"关掉视频子系统绕过"这条路对 YU-RIS 不成立。

副作用值得记：`quartz=disabled` 把"永远黑屏"变成"一条明确报错"，**排障可读性更好**，
但不是可玩方案。

### 8.2 KiriKiri：改走 Wine 容器 → 成功 ✅

思路：§7 里 `krkr_tobenai` 在 KR2 下报 `Member 'wf' does not exist`。它自带了 Windows 版
`game.exe`，用原版引擎跑就没有"模拟器缺成员"的问题。

验证手法（不改代码）：把 `/sdcard/Games/krkr_tobenai` 下所有 `.xp3` 临时改名，使引擎检测
落到 UNKNOWN → 导入走容器路线 → 建出 `xuser-16` → 在容器内还原 `.xp3` 文件名 → 直启
`game.exe`。

| 路径 | 结果 |
|---|---|
| KR2（内置引擎） | ❌ `Member 'wf' does not exist @line(7202)` |
| Wine 容器（原版 `game.exe`） | ✅ 分辨率选择框（含标题美术）→ Clover splash → **标题页（CONTINUE / START / LOAD）** |

**结论**：KiriKiri 走容器是可行解。四个样本都带 Windows exe
（`joui2.exe` / `bosei2.exe` / `とける風花とシロうさぎ.exe` / `game.exe`），前提成立。

### 8.3 落地缺口

§8.2 证明路线可行，但**应用当前不会这么走**：`GameEngineRegistry.resolve()` 按引擎枚举
映射（`KIRIKIRI → KrkrEngine`），即使游戏已经有容器，仍然进内置 KR2。

要落地需要：库索引加"强制容器"标记 → `resolve()` 前置判断 → 详情页开关 + 按需建容器。
（`xuser-16` 是本轮为验证而建的容器，可删。）

### 8.4 完整流程验证：通过（含一条分辨率陷阱）

§8.3 的缺口已补（详见 `CHANGELOG` 与 PR #27 / #28）。随后用真实目标 `krkr_tobenai`
从应用界面走完了全流程：

| 步 | 动作 | 结果 |
|---|---|---|
| 1 | 库 → 搜索 → 卡片 → 详情 | ✅ `KIRIKIRI · 无容器` |
| 2 | 高级设置 → 改用 Wine 容器运行 → 确认 | ✅ |
| 3 | 建容器 + 复制 | ✅ `xuser-16`、1.1G、写入 `preferred_engine=winlator-container` |
| 4 | 详情页刷新 | ✅「运行环境已就绪」 |
| 5 | 启动 | ✅ `launch via winlator-container kind=UNKNOWN containerId=16` |
| 6 | 分辨率对话框 → 免责声明 → 标题页 | ✅ |
| 7 | 点 START 进入剧情并推进约 60 秒 | ✅ 立绘 + 对白逐句推进（三帧哈希各不相同） |

**过程中定位到一条真陷阱（非应用缺陷，但会让人误判"游戏点不动"）**：

- 该游戏的分辨率对话框提供 **1920x1080 / 1440x810 / 1280x720 / 960x540** 四档，
  而容器 `screenSize` 是 **1280x720**。
- 选 **1440x810 或更大**时，游戏窗口**比容器屏幕还大** → 窗口被裁切，
  屏幕坐标与游戏内坐标不再对应，点击全部落空（表现为"菜单点不动"）。
- 选 **960x540** 后一次就点中，直接进正片。
- `+msg` 追踪确认输入本身是通的：`WM_MOUSEMOVE` / `WM_LBUTTONDOWN` / `WM_LBUTTONUP`
  都正常投递到窗口（`lp 0` 是该 trace 的打印口径，不是坐标丢失——用已知可点的游戏对照验证过）。

> 结论：**容器分辨率 ≥ 游戏可选的最高分辨率**，否则会出现"能渲染但点不动"的假故障。
> 排查"点不动"时应先确认游戏实际跑的分辨率，再怀疑输入链路。

> **2026-10-24 晚 终局复核（本条上方原结论成立；中途两次"更正"作废）**：用 guest 侧 hook
> （MSVC x86 编的 `activator.exe` + `hook.dll`，挂 `WH_GETMESSAGE`/`WH_CALLWNDPROCRET`）取证后确认：
> 输入链路**从头到尾都是好的**——`WM_LBUTTONDOWN/UP` 正常投递到主窗口，`GetAsyncKeyState(VK_LBUTTON)=0x8000`，
> `GetActiveWindow/GetFocus/GetForegroundWindow` 全是游戏窗口。所谓"1920 点不动""主窗口未激活"
> 都是**验收脚本自己估点打偏**（拿截图按 1.25 倍折算坐标）造成的误判，我据此写下的两条更正是错的。
> 改用像素扫描定位菜单行后：容器 **1920x1080 + 游戏窗口 960x540** 一次点中 START，
> 引擎日志 `Scenario loaded : macro.ks / 01_02.ks`，随后连续点击推进对白约 60 秒，
> 立绘、表情、中文文本、Q-SAVE/LOAD/CONFIG/AUTO/SKIP/BACKLOG 全部正常 —— **达到游玩级**。
> 本条原结论（容器分辨率要 ≥ 游戏窗口）依然有效，其真实机理见 TD-14：KiriKiri 把窗口放在
> 虚拟屏 (152,152)，当窗口尺寸等于容器尺寸时画布右下被裁（约右 156px / 下 175px 在屏外）。
> 另记两条独立缺陷：键盘事件到不了 Wine 客户程序（TD-13）、启动尺寸对话框每次必弹（TD-15）。

