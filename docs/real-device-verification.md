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


---

## 9. 「画面只占一小块」全屏修复——修复前/后对照（2026-10-25）

问题：Wine 路线部分游戏无法全屏 / 画面只占屏幕一小块。根因与修复方案见
`CHANGELOG` [Unreleased] 与 §9.1；本节只记录真机数据。

### 9.1 方法与判据

- 与 §7 同机同包基线（RMX5010，v11.3 debug 覆盖安装）。
- 直启 `am start XServerDisplayActivity(container_id, exec_path)`，28s/50s 各截一帧；
  内容块按列/行亮度密度（>0.5% 采样点亮）判定，剔除边缘杂散像素。
- 修复前/后各跑同一批用例，KR2 / Ren'Py 原生路线作回归对照。

### 9.2 结果

| 用例 | 路线 | 修复前占屏 | 修复后占屏 | 判定 |
|---|---|---|---|---|
| `krkr_caste`（KIRIKIRI 4:3，1920 容器） | Wine | 31.8% | **78.8%** | ✅ |
| `siglus_test`（SIGLUS 960x540 窗口） | Wine | 45.2% | **90.3%** | ✅ 16:9 letterbox 满幅 |
| `hana9test`（SIGLUS 800x600 窗口） | Wine | 42.1% | **80.3%** | ✅ |
| `krkr_tobenai`（启动分辨率对话框） | Wine | 11.8% | **88.9%** | ✅ 对话框等比放大可读 |
| `test_unity`（自设全屏，回归） | Wine | 78.3%（密度口径） | 90.3%（bbox 口径） | ✅ 无回归 |
| `krkr_munse`（回归） | KR2 | 99.9% | **99.9%** | ✅ |
| `renpy-testgame`（回归） | Ren'Py | 80.1% | **80.1%** | ✅ |

### 9.3 输入链验证（铺满变换开启后）

- `krkr_caste` 变换后点击屏幕中部：帧差在点击行出现 50px 高菜单高亮条（悬停响应）；
  `WINEDEBUG=+event` trace 里 `MotionNotify / ButtonPress / ButtonRelease` 完整到达
  游戏窗口（`hwnd 0x2007c`，两次点击 press→release 齐全）——坐标映射与事件投递正常。
  屏幕未推进属估点位置非可交互元素，与 §8.4 教训一致（先确认落点再怀疑输入链）。

### 9.4 关键机理（记档，防回归误判）

1. 上游 `forceWindowsFullscreen`（FullscreenTransformation）只接手**无装饰位**的窗口；
   本 app 的 X server 是不重定父的极简 WM，Wine 会对顶层窗口**自绘标题栏**（单窗口
   整体带 `_MOTIF_WM_HINTS` 装饰位，实测 `deco=58`）→ 不注入 `X11 Driver Decorated=N`
   时铺满变换静默不生效。此键已进语言/字体注入链（`GalgameLocaleInjector.apply`），
   旧容器走详情页「重新注入运行环境」补齐。
2. `GLRenderer.inBounds` 上界放宽为 `<=`：等屏窗口（KiriKiri (152,152) 偏移）也获得
   居中变换，不再右下裁切（TD-14 残留①）。
3. 虚拟屏尺寸与画面大小是**反向**杠杆：窗口小于虚拟屏时，虚拟屏越大画面越小
   （caste 1920 容器 31.8% vs siglus 720 容器 45.2% 即此效应）。「运行环境分辨率」
   只用于"窗口大于虚拟屏被裁"的场景。

### 9.5 引擎类型覆盖矩阵（补测，2026-10-25）

| 引擎 | 代表用例 | 路线 | 结果 |
|---|---|---|---|
| KIRIKIRI（4:3 窗口） | `krkr_caste` | Wine | ✅ 31.8% → 78.8% |
| KIRIKIRI（启动对话框） | `krkr_tobenai` | Wine | ✅ 11.8% → 88.9% |
| SIGLUS（800x600 窗口） | `hana9test` | Wine | ✅ 42.1% → 80.3% |
| SIGLUS（960x540 窗口） | `test_siglus` | Wine | ✅ 45.2% → 90.3%（16:9 letterbox 满幅） |
| UNITY（自设全屏，窗口==虚拟屏，覆盖 P2 `<=` 路径） | `test_unity` | Wine | ✅ 无回归 |
| UNITY（§7 未进入样本） | `unity_nekoya` | Wine | 与 §7 一致：未进入游戏（启动级，非显示级） |
| YU-RIS（SoftDenchi DRM 对话框） | `yuris_karakoi` | Wine | 对话框为 DIALOG 类窗口，不参与铺满变换，原生尺寸居中可读；无回归 |
| YU-RIS（TD-09） | `yuris_lufia` | Wine | 与 §2.2 一致：黑屏 + 左侧竖线（上游 quartz 死锁，非本修复范围） |
| SystemNNN（TD-09） | `sysnnn_sainogami` | Wine | 同上 |
| AdvHD | `test_advhd` | Wine | 与 §7 一致：启动即退回库页（启动级；`wine_exec.log` 仅 `esync: up and running.` 一行，无错误输出，原因未取到） |
| BGI（非 ASCII exe 名） | `bgi_ryo` | Wine | **§7.4 的"结论未定"更新为：确认启动失败**——本次已正确传 `exec_path_wine`（`C:\galgame\bgi_ryo\両腕…exe`），仍落回库页；属启动级问题，非 §7.4 猜测的转义缺陷，与显示修复无关 |
| KIRIKIRI 原生 ×2 | `krkr_munse` / `krkr_tokekaze` | KR2 | ✅ 99.9% / 99.9% |
| Ren'Py 原生 | `renpy-testgame` | Ren'Py | ✅ 80.1% |

**覆盖结论**：所有能渲染出画面的引擎类型（KIRIKIRI / SIGLUS / UNITY / KR2 / Ren'Py）均验证
铺满生效或无回归；渲染不出来的是另行登记的启动级 / 上游问题（TD-09、AdvHD/BGI 启动链），
行为与修复前文档记录一致，无一例由本修复引入。

---

## 10. 启动链排查与修复——AdvHD / BGI / 自动应答（2026-10-25 第三轮）

### 10.1 取证方法

`XServerDisplayActivity.onMapWindow` 临时窗口普查日志（GalgameWin，已随提交移除）+
`WINEDEBUG=err,warn,fixme,loaddll,process` / `module,file,nls` 双组通道 + PE 导入表
解析（本地 Python struct 解析，含加壳 exe 的导入目录）。全部结论有日志/截图证据。

### 10.2 根因与修复对照

| 游戏 | 修复前 | 根因（证据） | 修复 | 修复后 |
|---|---|---|---|---|
| `test_advhd`（リビドー・アバンちゅ～る） | 启动即退回库页 | exe 静态导入 icu.dll（仅用 uloc_getDefault），加载器 c0000135；补桩后可见 **Enigma Protector+PlayDRM** 双重商业保护，解壳后自主退出 | icu 桩注入（加载通过） | **仍不可玩**：DRM 行为，按 A5 不破解，登记为不可玩 |
| `advhd_toran` | 启动即退回库页 | 同上 icu.dll 缺失；过加载关后再崩于 d2d1/DirectWrite（TD-17，上游 Wine 能力缺口） | 同上 | 引擎可加载、1280x720 窗口可建，渲染链崩（TD-17） |
| `bgi_ryo`（両腕が折れたので…） | 启动即退回库页（§7.4「未定」） | 非 ASCII 主程序路径在 wine 启动器**早期**（prefix 加载前、ACP 未初始化）被按 ANSI 解析为乱码宽字符 → 文件打不开（`+file,+nls` 取证：get_load_order 收到乱码名）。**双层真相**：当年验收脚本的转义确实也坏，两层都真 | `gs_launch.exe` ASCII 硬链接别名 + 启动改走别名 | **完整可玩**：进入游戏、满幅渲染 90.3%（含窗口铺满变换） |
| `krkr_tobenai` | 流程挂起 | 480x280 主窗口映射后画面静止、点击全无响应；Decorated A/B 排除上轮修复副作用 | — | 登记 TD-16 独立排查（当年 §8.4 在 1920 容器通过） |

### 10.3 启动对话框自动应答（教学-回放）

- 机制：`GalgameDialogAutoClick`——启动 45s 内的顶层小窗口（320–720 × 160–480、居中、
  类名非空，KiriKiri 尺寸确认实测 480x280 class=game.exe）为候选；首次手动点击记录
  相对坐标（容器 extra `galgame_dialog_click`="w,h,rx,ry"，按尺寸区分）；之后同尺寸
  （±24px）窗口映射 600ms 后自动重放（`xServer.injectPointer*`，按下保持 60ms）。
  **无教学点绝不点击**（零盲点设计）。
- 真机验证：教学记录 ✓（tobenai 点击被记录 480x280 rel=(403,262)，logcat
  `taught dialog click`）；回放触发 ✓（二次启动 `replay dialog click at virtual
  (803,491)`）。端到端「自动点掉对话框进主画面」因 TD-16（tobenai 挂起）未闭环，
  待首个可复现对话框样本。
- 附带修复：S: 重定向目标目录自愈（appdata/roaming、local），icu 桩资产声明入 NOTICE。

### 10.4 本轮回归

`krkr_caste` 窗口铺满 78.8% 无变化；`bgi_ryo` 满幅 90.3%；别名/自愈幂等（二次启动无重复动作）。

## 11. 返回键 UX 与性能显示开关（2026-10-25 第四轮，设备 dc57ebe3）

### 11.1 现象取证（修复前，构建 6bd95bf）

用户反馈「手机返回键还是会弹出一个 Winlator 的页面」。真机复现（`hana9test` Wine 路线，
`input keyevent 4` 后截图 `back_after_key.png`）：上游 `XServerDisplayActivity.onBackPressed`
的行为是**返回键=打开左侧抽屉**——抽屉头部大字标语为
「GalGame Android Shell (**Winlator-based**)」，内容是 Winlator 功能集
（触控模式/键盘/输入控制/切换全屏/任务管理器/活动窗口/放大镜/屏幕效果/画中画/退出），
即用户感知的「Winlator 页面」。

### 11.2 修复内容

1. **返回键 = ESC**：`onBackPressed` 改为「抽屉开着→关抽屉；否则→`injectKeyPress(KEY_ESC,
   0xff1b)` + 40ms 后 release」。ESC 是 galgame 通用取消/呼出菜单键（与 PC 一致）。
2. **抽屉入口改浮钮**：右上角 38dp 半透明 ☰（`ic_galgame_menu`，放右上避让左上角 FrameRating
   HUD；PiP 时自动隐藏）；触摸板模式原有的四指点按呼出保留。
3. **抽屉头部标语**换为独立的 `galgame_drawer_header`（「游戏菜单」），游戏中不再出现
   "(Winlator-based)" 字样；About 页归属声明不变。
4. **性能显示开关**：抽屉新增「性能显示」（关闭/简单(FPS)/完整(FPS·CPU·内存·GPU)），
   立即生效并写回容器 `hudMode`。上游 FrameRating 本有此能力但只能在上游容器编辑页里开，
   galgame 容器进不去该页。为此 `frameRating` 改为**无条件创建**（上游仅
   HUDMode≠DISABLED 才创建，中途开启会因 `frameRatingWindowId` 未解析而接不上帧计数）。

### 11.3 复测证据（构建 11.3 / versionCode 34 + 本轮工作区）

| 用例 | 操作 | 结果 |
|---|---|---|
| 返回键不再弹抽屉 | 游戏内 `keyevent 4` | ✅ 截图与基线逐字节同尺寸（无抽屉），对比修复前 `back_after_key.png` |
| ESC 真实送达 | 标题画面 `keyevent 4` 一次 | ✅ 花弔！标题收到 ESC 触发自身 exit 流程，游戏结束后 `topResumedActivity=GalgameMainActivity`（落回游戏库，非 Winlator 页面/桌面） |
| 浮钮开抽屉 | tap (2678,101)（横屏 2780x1264，density 560，uiautomator 实测 bounds [2612,35]-[2745,168]） | ✅ 抽屉打开，头部「GALGAMESHELL / 游戏菜单」，无 "(Winlator-based)"；菜单含新项「性能显示」 |
| 返回键关抽屉 | 抽屉开着 `keyevent 4` | ✅ 截图回到纯游戏画面 |
| 性能显示中途生效 | 抽屉→性能显示→完整 | ✅ 四行浮窗立即出现：**FPS 59.8 / GPU Turnip Adreno 830 / RAM 8.7/15.5GB / CPU 3.19GHz \| Box64 v0.4.5**（`fix_hud_full2.png` + 左上角放大 `fix_hud_crop.png`） |
| 退出落点回归 | 游戏内 ESC 触发游戏退出 | ✅ 落 `GalgameMainActivity`（§7 的 PR#37 语义未破坏） |

注记：ESC 语义=PC 上的 ESC——标题画面按 ESC 可能直接触发游戏自身的退出确认/流程
（花弔！即如此），属游戏自身行为；对白/一般场景通常呼出游戏菜单或无操作。

### 11.4 性能监测能力盘点（本轮调研，方案备查）

Winlator 自带（FrameRating 三档）：FPS；FULL 档=GPU 名称（`_NET_WM_GPU_INFO`，guest
显卡驱动上报，如 Turnip Adreno 830）、系统内存 已用/总量、CPU 最高核主频 + Box64 版本。
`core/CPUStatus` 另有 `getTemperature()`（thermal zone）但 FrameRating 未展示。
**不自带**：CPU 使用率（/proc/stat 差分可加）、负载（/proc/loadavg 可加）、功耗
（BatteryManager 电流×电压可得整机瞬时功耗，无按应用功耗的公开 API）、GPU 使用率
（Android 无通用免 root 接口，高通 kgsl / Mali 节点因机型而异多数不可读）、按进程
CPU/RSS（box64/wine 进程 /proc/<pid> 自家可读）。以上均可在我们自己的代码里扩展，
不触碰上游文件，与组件升级解耦。

## 12. 第五轮：随机启动闪退修复 / 加载 UI 品牌化 / 性能 HUD 多项化（2026-10-25，设备 dc57ebe3）

### 12.1 闪退根因与修复（P0）

用户反馈「随机启动游戏都有闪退」。真机取证（`logcat -b crash`）：
`test_siglus`（SIGLUS→Wine 路线）启动 10s 后 `FATAL EXCEPTION: main
ArrayIndexOutOfBoundsException: length=4096; index=4096
at ArrayUtils.indexOf ← Box64Utils.extractBinVersion ← FrameRating.run`。

链条：第四轮「FrameRating 无条件创建」+ 容器 HUDMode=FULL → 进入 `run()` 的 FULL 分支 →
`extractBinVersion` 4096 分块读 box64 二进制找版本串 → 上游 `indexOf` 内层匹配循环**缺
`j < length` 边界**，"Box64 arm64 v" 前缀恰落块尾时 `array[4096]` 越界 → 主线程 FATAL →
**整个 App 闪退**（进程内运行，殃及一切）。上游潜伏 bug，被第四轮改动暴露。

修复（两层）：
1. `core/ArrayUtils.java` `indexOf` 补 `j < length`（根治，GamepadHandler 同受益）；
2. `widget/FrameRating.java` `run()` 的 `extractBinVersion` 包 try-catch，异常降级
   "Box64 v?" 并缓存（防御：主线程 IO 不再每帧重试）。

复测：`test_siglus` 两轮冷启动（22:0x / 22:1x）`crash buffer` 均空、
`XServerDisplayActivity` 稳定前台。

### 12.2 运行时目录自污染（健壮性）

库里「files」「.preference」两条目 = KR2 的 `getWritablePath()` 在**游戏源目录**创建的
运行时目录（内含 XP3 魔数存档封包）被再次导入时误判 KIRIKIRI 入库，启动即黑屏挂起。
修复：`EngineDetector.isRuntimeDirName`（.preference/files/savedata）三处拦截——
`detect()` 入口、深层下钻、`GameLauncher.launch()` 兜底（旧库残留条目启动时明确报错
提示移除重导）。

### 12.3 启动加载 UI 品牌化

`preloader_dialog.xml` 重写：全屏 90% 黑遮罩 + GALGAMESHELL 字距标 + 主文案 + 2dp 细
进度条（Swiss 纸面语言），不再引用 Winlator attr。真机视觉实证（`r5_preloader.png`）：
GALGAMESHELL 小字 + 主文案 + 细进度条，无 Winlator 圆形转圈。

### 12.4 性能 HUD 多项化（13 项可选）

`GalgamePerfHud`（新，零上游依赖）：FPS（帧回调）/ CPU 使用率（/proc/stat 差分）/
CPU 频率（cpufreq sysfs avg·max）/ CPU 各核占用 / 系统内存 / 游戏进程内存（含
box64/wine RSS）/ 游戏进程 CPU / GPU 型号（_NET_WM_GPU_INFO）/ 负载（loadavg）/
整机功耗（电流×电压）/ 温度（CPU·电池 thermal zone）/ 电池（电量·状态）/ 网络上下行
（/proc/net/dev 差分）。抽屉「性能显示」改**多选对话框**，容器 extra `galgame_perf_items`
持久化，与上游三档 FrameRating 互斥显示。

复测（hana9test）：多选对话框打开（视觉实证 13 项列表）→ 勾选 → 应用 → 左上浮窗渲染
**FPS 59.8 / CPU 45.2%**（`r5_hud_zoom.png`，行距 49px 等宽白字特征）——全链打通。
自动化勾选仅可靠勾中 2-3 项（滚动列表 + 动画 idle 问题），13 项全开状态待人工确认，
各行渲染由同一循环保证。

### 12.5 其他回归

返回键（抽屉开→BACK→关）像素实证 61.9%→9.0% 白面板消失；`GalgamePerfHud` 帧回调与
`FrameRating` 共存不冲突。

## 13. 第六轮：HUD 重复修复 / 长按管理 / 桌面快捷方式 / 导入进度（2026-10-25，设备 dc57ebe3）

### 13.1 性能显示重复与关不掉（P0）

用户反馈：左上角一直有一个性能显示（关闭设置后仍在）；再开启会多出一个（两个浮窗，
外观不同：旧的是彩色行、新的是单色行）。根因两层：
① 上游 `FrameRating`（彩色）与 `GalgamePerfHud`（单色）并存——第五轮「无条件创建
FrameRating」+ 容器残留 hudMode=FULL → 上游三档浮窗按容器设置自行显示；
② 上游 `FrameRating.update()` 每帧把自身拉回 VISIBLE，即便我们 GONE 它也会复活。

修复：**galgame 直启路线（exec_path）不再创建上游 FrameRating**（上游路线保留三档
语义）；`changeFrameRatingVisibility`/帧回调/GPU 读取全部与实例解耦（PerfHud 独立
接帧）。复测（uiautomator 层级证据）：未开启时层级无 TextView 节点（残留容器 FULL
档不再生效）；勾 FPS→应用→出现 `text="FPS     0.0"` 单行；取消勾选→应用→TextView
归零。开/关/单浮窗闭环全绿。

### 13.2 其余五项

- **游戏内菜单开关**：设置页新增「游戏内菜单」分区 8 个开关（输入控制/切换全屏/任务
  管理器/活动窗口/放大镜/屏幕效果/画中画/触摸板帮助），默认隐藏；永显项=触控模式/
  性能显示/键盘/退出。
- **长按菜单**：库卡片长按 → 启动/打开详情/添加到桌面快捷方式/彻底删除（含容器）；
  「彻底删除」=容器（游戏文件）+ S: 独立存档 + 库记录一并清理，另有「仅移除记录」
  逃生门。
- **桌面快捷方式**：长按菜单→添加到桌面（系统 pin shortcut，封面作图标）→
  `GalgameShortcutActivity`（透明 exported 转发）→ 详情页 auto_launch 直启。
- **导入进度条**：`ImportFlow.stageGameFiles` 进度重载（字节统计、200ms 节流），
  导入页底部红条 + `COPY xx% · MB/GB` 文案。
- **外接控制器闪退**（问题6）：设置页无参跳入上游 `ExternalControllerBindingsActivity`
  → loadProfile(0) null → NPE。修复：取首个输入控制配置 + default controller 进入；
  无配置时 Toast 引导。

### 13.3 导入语义（用户问询的回答，代码依据）

A 路由（Wine 容器）：`ImportFlow.stageGameFiles` **复制**源目录到容器
`drive_c/galgame/<gameId>`（源保留原位，双份占用空间，删除游戏不影响源）。
B 路由（原生 KR2/Ren'Py）：**引用**源目录（不复制，库记录 sourcePath）。

## 14. 第七轮：源目录清理选项 + 原生引擎收入应用内部（2026-10-26，设备 dc57ebe3）

### 14.1 原生游戏「收纳」（B 路由语义升级）

背景（用户健壮性诉求）：B 路由此前引用用户源目录，库外的改名/移动/误删都会损坏游戏。
现新增 `GalgameNativeStore`（`files/galgame-native/<gameId>` 应用私有空间）：导入时完整
复制（进度条复用第六轮 UI），索引与启动全部指向收纳目录；adapter 取目录时收纳优先、
sourcePath 兜底（存量引用型游戏保持可玩，详情页可手动「收入应用内部」迁移）。

真机证据（bosei2，KR2）：
- 导入 → 自动从收纳目录启动（KR2Activity 前台）✓
- 「返回游戏库」点击时弹「删除源目录？」对话框（保留/删除源目录）✓
  （曾两轮不出框：① 询问在 done() 里被引擎 Activity 切换吞掉——挪到返回按钮点击；
  ② builtin.launch 期间本 Activity 被回收重建、source 字段丢失——onSaveInstanceState
  增存 source 绝对路径并在 onCreate 恢复）
- 点「删除源目录」→ `/sdcard/Games/bosei2` 实际消失、回库页条目仍在 ✓
- 删源后从库再启动 → logcat：`gamePath=/data/user/0/com.winlator/files/galgame-native/
  bosei2`、`launch via krkr ok=true`——游戏完全独立于源目录 ✓

### 14.2 Wine 路线（A 路由）

同一条「删除源目录？」询问对 A 路由同样生效（stageGameFiles 复制完成后 COMPLETED 页
返回库时询问）；应用内部路径（重复导入已收纳游戏）不询问。

### 14.3 已知残留

详情页「不可用 · 原生目录」标签对已收纳游戏显示旧索引文案（启动行为正确，仅展示
层未区分「已收纳」），下一轮随详情页状态条目化一并清理。

## 15. 第八轮：性能监控 0/N/A 修复 / 多选模式 / 巡检（2026-10-26，设备 dc57ebe3）

### 15.1 性能监控 0/N/A（三项根因 + 修复）

用户反馈部分监控项显示 0 或 N/A。逐项根因：
1. **FPS 恒 0**：帧率窗口（frameRatingWindowId）只在窗口映射事件里解析，而 galgame
   路线 HUD 关闭时启动 → `changeFrameRatingVisibility` 早退 → 中途开 HUD 无帧源。
   修复：galgame 路线（exec_path）无条件解析帧率窗口 + GPU 型号缓存（lastGpuInfo），
   HUD 中途开启时回填。
2. **FREQ N/A**：读的是 `scaling_cur_frequency`——文件名错误，正确为
   `scaling_cur_freq`（上游 CPUStatus 同路径，本机实证可读）。
3. **TEMP/PWR/BAT N/A**：thermal zone 缺电池节点、BatteryManager 电流属性恒 0。
   修复：电池温度走 sticky 广播 EXTRA_TEMPERATURE（0.1°C）、CPU 温度加上游
   CPUStatus.getTemperature 回退、电流属性无效时回退广播 "current_now"、
   EXTRA_VOLTAGE 电压（EXTRA_CURRENT_NOW 无公开常量，用字面量）。

复测（hana9test，13 项全开，截图 r8_hud_top/bottom.png 逐行视觉识读）：
FPS 59.8 · CPU 12.5% · FREQ avg 2.55/max 3.19GHz · RAM 8.7/15.5GB · PROC 1.2GB ·
PCPU 98% · GPU Turnip Adreno 830 · LOAD 4.32/3.86 · PWR 6.11W · TEMP cpu 42.5°C/
bat 28.1°C · BAT 65% 放电 · NET ↓38.2KB/s ↑2.1KB/s——**13/13 全部真实数据**。

### 15.2 多选模式

长按网格卡片进入多选（✓ 前缀 + 未选卡片压暗 0.35 alpha），顶部选择条：
已选计数 / 全选（再点清空）/ 彻底删除（容器+S:存档+记录，容器异步逐个排队避免并发）
/ 仅移除记录 / 退出（BACK 亦可，OnBackPressedCallback）。onDestroyView 摘除选择条
防泄漏。复测：长按进入 ✓、点第二卡勾选 ✓（✓ test_advhd + ✓ unity_shina）、BACK 退出
勾选清零 ✓。桌面快捷方式入口从长按菜单迁至详情页「添加到桌面」行（swiss_detail_rows）。

### 15.3 巡检修复与备注

- 详情页环境标签三态化：容器名 / 「已收纳 · 应用内部」/ 「内嵌引擎 · 引用原目录」
  （§14.3 残留清理，引用态提示可迁移）。
- 设备端 NotificationShade 在巡检中被系统 heads-up 通知卡住（USB 调试通知），
  常规手势/service call 均无法收起——系统层现象与本 App 无关，记录备忘。

## 16. 第九轮（进行中）：UI 纸面统一——多选操作栏重设计（2026-10-26）

- **多选选择条黑框（用户反馈①）**：第八轮的临时实现是顶部黑条（0xEE101010 背景 +
  默认 Material 按钮），不符合 Swiss 纸面语言。重设计为**底部白色操作栏**：纸面白底 +
  顶部 2dp 墨色分隔线 + 无框大写字距文字按钮（删除动作 Swiss 红）+ 左侧加粗计数。
  （设备临时锁屏，视觉复验待解锁后补；gradle 真机构建通过。）
- **高级设置「更多设置」黑框（用户反馈②）**：代码排查——该页嵌入的 legacy
  GalgameSettingsFragment 布局已是 Swiss 白底、宿主窗口背景 swiss_paper、
  advancedToolbar 为 1dp 隐形、panelBackground 继承 light——**未找到代码级黑源**，
  需真机解锁后截图定位（疑为特定子控件/对话框），下一轮补。
- 桌面快捷方式入口（用户问询③）：详情页「维护」区「添加到桌面」行（第八轮从长按
  菜单迁入），已答复位置。

### 16.4 「更多设置」黑框定位与修复（2026-10-26 解锁后取证）

- 定位（像素带分析 + uiautomator 节点）：底部 y2584-2776 纯黑带 = legacy 设置页的
  **SwissConfirmBar「完成」确认条**（原样式通栏 swiss_ink 黑底白字 56dp）。
  页面级对比：设置页/库页/高级设置主页底部均亮 253（白），唯此页 18（黑）——
  非系统导航栏，是该控件自身设计。用户感知为「突兀大黑框」。
- 修复：SwissConfirmBar 改纸面版——白底 + 顶部 2dp 墨线（swiss_confirm_bar_bg
  layer-list）+ swiss_ink 黑字；settings_fragment（唯一使用处）同步生效。
- 复测：修复后同区域均亮 **253.9**（黑→白实证）。
- 全 UI 一致性扫描（底部/顶部色带）：库 253 / 设置 253 / 详情 253 / 高级设置主页
  253 / 导入页 158（黑底实心按钮区，属设计元素）/ legacy 修复后 253.9——
  全站纸面统一，无其他黑框残留。
