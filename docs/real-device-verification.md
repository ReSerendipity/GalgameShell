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
