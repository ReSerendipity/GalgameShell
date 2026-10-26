# Wine / dxwrapper 兼容性调优：任务背景、实施与真机取证

> 来源：Zcode 会话 `session_9ea123b5608edfbebad2803f7379ef9a`（标题「排查游戏窗口小于虚拟屏问题」）
> 的**最后一轮**。该轮用户提问（2026-10-26 08:34）：「关于项目依赖的一个引擎 WineBox64，
> 这个目前调整的是最优解吗？兼容性方面」。代理核对配置后给出分层评估，结尾停在
> 「我的建议是先做 2……1 作为下一轮独立任务评估体积预算后再定。要立项哪个说一声」。
> 本文记录其后立项的**任务一 / 任务二**的背景、当次核对的事实、实施细节与真机取证。
>
> 定位：可行动的技术说明（陌生克隆者读了能复跑），故入 `docs/`（见 `local/AGENTS.md` 分区约定）。

## 1. 任务定义

上一轮评估给出「往更优推」的三条路，其中前两条即本轮任务：

| 任务 | 上一轮原文 | 本轮口径 |
|---|---|---|
| 任务一 | 「预置第二个 Wine（老 9.x）+ 按游戏/按引擎切换（收益最大）……代价是 APK/rootfs 体积约 +200MB」 | 打通按引擎指定 Wine 版本的接线；让第二个 Wine **装得上、切得动**；不打包进 APK（用户本轮拍板） |
| 任务二 | 「引擎预设 dxwrapper 分化（小改动）：老 2D 引擎预设改 wined3d 或 cnc-ddraw，新 3D 引擎维持 DXVK……顺带攻 TD-17」 | 按**真机证据**逐引擎分化，已验证可玩的引擎一律不动 |

第三条「维持现状」未立项。

## 2. 当次核对的配置事实（2026-10-26，main @ c109f41）

| 层 | 事实 | 出处 |
|---|---|---|
| Wine | 单版本 **10.10**（rootfs 内置）；多版本机制**代码完整存在**：安装（Uri→`.tar.xz` 解包→跑 `wine --version` 探版本→跑 winecfg 生成 container-pattern）、列表、按容器选择、启动时 `setWinePath` | `core/WineInfo.java:16`、`core/WineInstaller.java:23/82/93/177`、`container/ContainerManager.java:265-268`、`XServerDisplayActivity.java:207` |
| box64 | 0.4.5 自建为默认；资产另有 0.4.4 与 0.4.5-gstfix | `core/DefaultVersion.java`、`assets/box64/` |
| dxwrapper | 顶层只认 `wined3d`/`dxvk` 两值，其余**静默回落 dxvk**；`cnc-ddraw`/`d7vk` 只能经 `dxwrapperConfig` 第 0 段的 `ddrawWrapper` 子选项进入 | `container/DXWrappers.java:23-26`、`XServerDisplayActivity.java:243`、`:981`、`:1028-1046` |
| dxwrapper 资产 | dxvk 1.10.3/2.4.1、d8vk 1.0、d7vk 1.11、vkd3d 2.14.1、cnc-ddraw 6.6（`Shaders/`+`ddraw.ini`+`ddraw.tzst` 齐备） | `assets/dxwrapper/` |
| GPU | turnip 26.2.0 / zink 22.2.5 / virgl 23.1.9 / vortek 2.1 / gladio 1.1 | `assets/graphics_driver/` |
| 已知失败项 | TD-09（SystemNNN/YU-RIS 过场黑屏，quartz 死锁）、TD-16（krkr_tobenai 挂起）、TD-17（AdvHD d2d1 崩） | `docs/tech-debt-register.md` |

### 2.1 对上一轮评估的三处更正（均为当次实测，非推断）

1. **BGI 不该进 wined3d 候选**。评估把 BGI 列为待改引擎，但 `bgi_ryo` 在第三轮已
   **完整可玩**（满幅 90.3%，`real-device-verification.md` §10.2/§10.4），用的就是 dxvk。
   改它会制造回归。本轮 BGI 保持 dxvk。
2. **「只是没打包第二个 Wine」不完整**。安装入口 `LLWineInstallation` 被
   `MainActivity.DEBUG_MODE`（硬编码 `false`，`MainActivity.java:36`）挡死，
   本 fork 里**用户永远看不到**「Wine 版本 / 安装 Wine」那一行（见 §4.2）。
3. **「上游支持 wine-<版本> 多目录」为真，但第三方现成 Wine 包在本 rootfs 上永远起不来**。
   socket 目录是**编译期写死**的，不是运行时可配的（见 §4.3 的字符串级证据）。

## 3. 任务二：引擎预设 dxwrapper 分化

### 3.1 实施

- `EnginePreset` 新增 `dxwrapperConfig` 字段（JSON 键 `dxwrapper_config`），`ImportFlow.buildContainerData`
  原样写入容器 data 的 `dxwrapperConfig`。格式遵循官方 `DXWrappers.parseConfigs`：按 `|` 切两段，
  段内 `k=v,k=v`；**尾部的 `|` 不能省**，否则 `ddrawWrapper` 会落进 vkd3d 段。
- `assets/engine_presets.json` 逐引擎分化：

| 预设 | dxwrapper | ddrawWrapper | 依据（真机） |
|---|---|---|---|
| kirikiri | dxvk（不变） | — | ✅ 可玩 78.8% / 88.9%（§9.5） |
| siglus | dxvk（不变） | — | ✅ 可玩 ×3（80.3% / 90.3%，§7.2/§9.5） |
| unity | dxvk（不变） | — | ✅ 可玩 ×3；现代 3D，DXVK 正确 |
| bgi | dxvk（不变） | — | ✅ 可玩 90.3%（§10.2）；见 §2.1 更正 1 |
| unknown | dxvk（不变） | — | 通用兜底，可能是现代 3D |
| **advhd** | **wined3d** | — | ❌ TD-17；登记时点名 wined3d 可试。它是 d3d11/d2d1 而非 ddraw，故不配 cnc-ddraw |
| **yrg**（YU-RIS） | **wined3d** | **cnc-ddraw** | ❌ TD-09 全黑；DDraw 时代引擎 |
| **systemnnn** | **wined3d** | **cnc-ddraw** | ❌ TD-09 全黑；DDraw 时代引擎 |
| **artemis** | **wined3d** | **cnc-ddraw** | ⬜ 无样本未测；预设本就 winxp + `MESA_EXTENSION_MAX_YEAR=2003` |
| **cat_system2** | **wined3d** | — | ⬜ 无样本未测；DX8 时代 |
| **cmvs** | **wined3d** | — | ⬜ 无样本未测；2008 年代 DX9 2D |

  分化面**只覆盖当前不通的引擎**：6 个被改引擎在基线里全部不可玩，因此该改动
  **不存在回归面**；5 个已验证可玩/兜底的保持 dxvk。

### 3.2 真机验证（设备 dc57ebe3，debug 包）

方法：把最小受影响样本复制到新目录名后用仓库自带的调试自动化导入
（`am start -n com.winlator/.GalgameImportActivity --es galgame.path <dir> --ez galgame.auto true`），
再经真实启动路径（`GalgameDetailActivity` + `galgame.auto_launch`，保证走 `GalgameLaunchHelper` 的
icu 桩注入，`GalgameLaunchHelper.java:174-175`）启动；截图按亮度均值/非黑占比量化
（口径同 `real-device-verification.md` §7.1）。

| 样本 | 预设生效 | 启动落地 | 画面 | 结论 |
|---|---|---|---|---|
| `yuris_wined3d`（YU-RIS） | 容器 data `dxwrapper=wined3d`、`dxwrapperConfig="ddrawWrapper=cnc-ddraw|"` | `ProgramData/cnc-ddraw/{ddraw.ini,Shaders}` 新建；`syswow64/ddraw.dll`=378880 B ≠ Wine 内建 466944 B（cnc-ddraw 已替换）；`system32/d3d9.dll`、`d3d11.dll` 字节数与内建一致（wined3d 分支 `restoreBuiltinDllFiles` 生效）；日志 `wined3d_dll_init … Using the OpenGL renderer` | mean=0.11、nonblack=0.12%（全黑） | 机制**通过**；TD-09 黑屏**未变**（其根因是 quartz，与 d3d 包装无关） |
| `advhd_wined3d`（AdvHD） | 容器 data `dxwrapper=wined3d`、无 `dxwrapperConfig`（符合设计） | icu 桩注入；**无 TD-17 的 d2d1 崩溃**、无 `c0000135` | 引擎「起動チェック(警告)」对话框渲染（日文字体正常）；点 OK 后窗口+标题栏渲染，游戏自报 `Direct3D Initialize failed (6)` 中止；mean=202.16、nonblack=81.02% | 失败模式从「静默崩溃退回库页」改善为「渲染级有窗口 + 引擎可理解报错」；**仍未到游玩级** |

未覆盖格子（如实声明）：`artemis`/`cat_system2`/`cmvs` 设备无样本，未验证；
YU-RIS/SystemNNN 的 wined3d 收益被 TD-09 遮蔽，**无法判定**；AdvHD 点 OK 之后止于引擎报错。

## 4. 任务一：第二个 Wine + 按引擎切换

### 4.1 实施（机制层，已完成）

- `EnginePreset` 新增 `wineVersion` 字段（JSON 键 `wine_version`，值为 Wine identifier，如 `wine-9.2`）。
- `ImportFlow.resolveWineVersion`：预设指定非主版本 Wine 时，**先核对设备上真装好**
  （`<rootfs>/opt/installed-wine/<identifier>` 是目录 **且** `container-pattern-<fullVersion>.tzst` 存在，
  后者是 `ContainerManager.extractContainerPatternFile` 的硬前提），才写 `wineVersion`；
  三种回落（identifier 非法 / 未安装 / 安装中断缺 pattern）都写 logcat 告警并带可操作下一步，
  **不静默降级**。未装时容器不带 `wineVersion` 键 → 官方默认即主 Wine，导入不会失败。
- 高级设置页环境详情新增一行 Wine 版本（`Wine x.y（内置/另装）`），让容器实际用的 Wine 可见。
- 安装入口放行（见 4.2）。

### 4.2 安装入口是死的（本轮发现并修复）

`settings_fragment.xml` 的 `LLWineInstallation`（Wine 版本下拉 + 安装/移除按钮）与全部安装逻辑
（`GalgameSettingsFragment.loadWineVersionSpinner/selectWineFileForInstall/installWine/removeInstalledWine`）
都早已写好，但可见性条件是 `MainActivity.DEBUG_MODE`——该常量硬编码 `false`，
故本 fork 中该行**从未显示**，第二个 Wine 无从安装。
现改为 `MainActivity.DEBUG_MODE || preferences.getBoolean("galgame_dev_mode", false)`：
`galgame_dev_mode` 是设置页「开发者模式」开关（`GalgameSettingsActivity.java:131-136`）写的本 fork 自有偏好，
其副标题本就声明「已显示 WINE / BOX64 与运行环境」。真机已验证该行出现（spinner 显示 `Wine 10.10 (Custom)`）。

### 4.3 第三方现成 Wine 包起不来（字符串级根因）

按用户拍板的路线下载 Kron4ek `wine-9.22`（GitHub Release，sha256 校验通过）：

| 步骤 | 结果 |
|---|---|
| 变体选择 | 先下 `wine-9.22-amd64.tar.xz`（64,524,972 B，sha OK）；解包列表含 `lib/wine/i386-unix`（传统 multilib）。设备上的 Wine 10.10 只有 `i386-windows`/`x86_64-unix`/`x86_64-windows`（WoW64 形态），且 rootfs 只有 `box64`、**无 box86** → 32 位 unix loader 跑不了。改下 `wine-9.22-amd64-wow64.tar.xz`（64,521,648 B，sha OK） |
| 推设备 + 走真实安装 UI | 探测**成功**：确认框显示「版本 Wine 9.22 / 大小 637.57 MB」——证明 Kron4ek 二进制能经 box64 执行 |
| 生成 wineprefix | **失败**：`files/wine_exec.log` 仅两行——`wine: created the configuration directory …/home/xuser/.wine`、`wineserver: mkdir /tmp/.wine-11312: Permission denied`；随后应用按失败路径重启、`installed-wine` 为空 |
| 假设 A：`/tmp` 缺 sticky 位 | `chmod 1777` 后重跑，报错**一字不变** → 假设**错误**，已更正；权限已还原 0771 |
| 根因（二进制字符串） | 9.22 的 `wineserver`：`/tmp/.wine-%u`；内置 10.10 的 `wineserver`：`/data/data/com.winlator/files/rootfs/tmp/.wine-%u`。两者都**不含** `TMPDIR` 字符串——Wine 不读该变量，socket 目录是编译期写死的 |
| 源码复核 | `brunodev85/wine-10.10-custom` 与 `brunodev85/wine-9.2-custom` 的 `dlls/ntdll/unix/server.c` 均含该改写（10.10 在 :1288、9.2 在 :1264），并含 `symlink("/data/data/com.winlator/files/rootfs", "dosdevices/z:")`。**9.2-custom 带同样的 Winlator 补丁** |
| Android `/tmp` | `drwxrwx--x shell shell`——app（u0_a1312）非属主非属组，无写权 → `Permission denied` |

**结论**：任何未打该补丁的 Wine（Kron4ek 等一切现成包）在本 rootfs 上**不可能**完成安装；
唯一可行的第二个 Wine 是**自建 `brunodev85/wine-9.2-custom`**。

### 4.4 自建在本轮不可行（已复验，非推断）

- 本机 `wsl.exe -l -v`：「没有已安装的分发」；`docker info`：守护进程不存在。
  Wine 交叉编译需要完整 Linux 工具链，二者皆无。
- C: 盘 97% 占用（余 38 GB）；装 WSL 属系统级变更（UAC），不在本轮授权内。
- 参照：TD-05 已登记 box64 构建脚本「端到端一次未跑通」，Wine 构建面更大。

因此任务一本轮交付 = **机制 + 入口 + 根因取证 + 可执行下一步**；
「装上第二个 Wine 并验证 TD-09 是否翻盘」这一格**未完成**，原因如上，不粉饰。

### 4.5 社区预编译包路线（已试、失败、按止损跳过）

用户指示「用社区的试试，否则跳过 9.22 这个路线」。实测如下：

| 项 | 结果 |
|---|---|
| 来源 | `longjunyu2/winlator` v7.1.3 的 `wine-9.2-custom.wcp`（81,403,064 B，release 资产下载量 97,477；repo 548 star、brunodev85/winlator 的 fork、MIT）。包内 `profile.json` 自述 "Built based on brunodev85/wine-9.2-custom. For Winlator-glibc v7.1.3" |
| 格式 | xz-compressed tar（与安装入口 `Type.XZ` 同格式），顶层即 `bin/ lib/ share/` + `prefixPack.txz` + `profile.json`；**WoW64 形态**（`lib/wine/` 下无 `i386-unix`，与本机 10.10 同形）；`bin/wine` 为 ELF64 x86-64 |
| 补丁核验 | **带** socket 目录改写，但写死的是 winlator-glibc 的布局 `/data/data/com.winlator/files/imagefs/usr/tmp/.wine-%u`；本仓是 `files/rootfs/tmp` |
| 第一层 shim（成功） | 建 `files/imagefs -> files/rootfs` 符号链接 + `rootfs/usr/tmp`(1777) 后，`/tmp Permission denied` 消失——证明失败确系路径写死，且 shim 可对齐 |
| 第二层失败（止损点） | `err:winediag:nodrv_CreateWindow … no driver could be loaded`（winex11 起不来，winecfg/explorer 无法建窗）。已排除：`libX11.so.6`/`libXext.so.6` 缺失（rootfs 内有）、winex11 dlopen 报错（日志无）、socket 目录（已 shim）。该包的 X 客户端库栈与 winlator-glibc 的 rootfs 配套，与本仓 rootfs 不匹配 |
| 处置 | 结构性不兼容第二层，按用户指示**跳过 9.22 路线**；shim、`box64_logs` 偏好、设备 preinstall 残留、/sdcard 与本地的包均已还原/删除（本地仅留获取命令与 sha，见 §6） |

**结论**：9.x 第二 Wine 的可行来源仍只有自建 `brunodev85/wine-9.2-custom`（§4.4）；
社区预编译包因 rootfs 布局与 X 库栈双重不匹配而不可用，且其 `prefixPack.txz` 是
winlator-glibc 自有格式，本仓安装链路不消费。

## 5. 后续（按收益排序）

1. **交叉编译 `brunodev85/wine-9.2-custom`（wow64）**，产出 `.tar.xz` 后走现有
   「设置 → 开发者模式 → 更多设置 → Wine 版本 → 安装」入口安装，再对 TD-09 样本（`yuris_lufia`/
   `sysnnn_sainogami`）做 A/B。届时在 `engine_presets.json` 的 `yrg`/`systemnnn` 上设
   `wine_version: "wine-9.2"` 即可按引擎切换（机制已就绪）。
   捷径已排除：社区预编译包不可用（§4.5）；用户 2026-10-26 决定本轮不装 WSL/容器，故本条保持开放未启动。
2. ~~**给 wined3d 补 OpenGL 供给**~~ **已试、负、关闭（2026-10-26）**：为 `yrg` 临时配
   `graphics_driver: "turnip,zink"` 并以无片头副本真机对照——zink 的 libGL 落地（14.7MB）
   但仍无 `libEGL.so.1`，wined3d 连 adapter init 都未走到（GL 上下文建不起来，比 gladio 更差），
   画面与基线同黑（mean 0.11）。结合 §3.2 与 chain §7.4/§7.5：黑屏不随 d3d 包装、GL 供给、
   片头存在性三个轴变化，GL 供给线关闭。预设改动已回滚，未入库。
3. 更新 TD-17（wined3d 变体已测：崩溃→引擎报错对话框，仍未可玩）与 TD-09（换 Wine 路线被
   「需自建补丁 Wine」阻塞）的登记状态。

## 6. 复跑命令

```bash
# 类型门禁 + 检测回归（与 CI 同口径；Windows 上 classpath 分隔符用 ; 且用仓内相对路径）
OUT=".git/gate-out.$$"; mkdir -p "$OUT/stubs" "$OUT/check" "$OUT/hot"
javac -d "$OUT/stubs" $(find ci/stubs -name '*.java')
javac -Xlint:all -cp "$OUT/stubs" -d "$OUT/check" $(find app/src/main/java/com/winlator/galgame -name '*.java')
javac -Xlint:all -cp "$OUT/stubs;$OUT/check" -d "$OUT/hot" $(grep -vE '^[[:space:]]*(#|$)' ci/hotspot-entries.txt)
bash tools/run_detect_test.sh

# 真机：导入 + 直启（debug 包）
adb shell am start -n com.winlator/.GalgameImportActivity \
  --es galgame.path /sdcard/Download/<dir> --ez galgame.auto true
adb shell am start -n com.winlator/.GalgameDetailActivity \
  --es galgame.gameId <id> --es galgame.container galgame-<id> \
  --es galgame.label <id> --es galgame.engine <ENGINE> --ez galgame.auto_launch true
adb shell run-as com.winlator cat files/wine_exec.log   # guest 输出（ProcessHelper.java:77 落盘）
```

> 设备遗留：本轮测试容器（xuser-19/20/21）已随 2026-10-26 的**卸载**一并消失（见 §7），
> 无需清理。`/sdcard` 上的复制样本与 wine 包在卸载前已删；§4.5 的 imagefs shim、
> `box64_logs`/`wine_debug_channels` 偏好与 `installed-wine/preinstall` 残留均已还原
> （11:27 复核为空），随后随卸载清空。
>
> 复取 wine 包（如日后自建对照）：
> `curl -L -o wine-9.22-amd64-wow64.tar.xz https://github.com/Kron4ek/Wine-Builds/releases/download/9.22/wine-9.22-amd64-wow64.tar.xz`
> （sha256 `7350dcce…da2150`，见同 release `sha256sums.txt`）；社区包
> `https://github.com/longjunyu2/winlator/releases/download/v7.1.3/wine-9.2-custom.wcp`（已证不可用，仅作存档线索）。

## 7. 收口与设备交接（2026-10-26）

- 任务一/二按 §3/§4 交付，PR #50–#53 合并（main @ `e12a3c1`）；9.22 路线（§4.5）与
  zink 路线（chain §7.5）均实测关闭；TD-09 在模拟层已无剩余旋钮。
- **设备数据清空**：用户于本轮收口时卸载了 debug 测试包，应用数据随之清除——
  容器 xuser-1..21、游戏库索引、S: 盘存档（`storage/galgame-saves/`）全部不存在；
  `/sdcard/Download` 与 `/sdcard/Games` 下的游戏源文件保留，游戏需重新导入。
- **交接安装为 release 包**：`assembleRelease`（226,084,989 B，versionName 11.3，
  `flags` 无 `DEBUGGABLE` 实测确认）。仓内约定 release 沿用 debug 签名、锁 arm64-v8a、
  不捆绑正式 keystore（`app/build.gradle:41-52`）。首启 20s 内到空态库页（渲染级冒烟通过）。
- **release 的诊断面收窄**：不导出 `XServerDisplayActivity` / `GalgameImportActivity` /
  `GalgameDetailActivity`，也不可 `run-as`；后续真机取证需先换 debug 包
  （口径见 `real-device-verification.md` §5）。
- 「Wine 版本 / 安装」入口在 release 上同样受「开发者模式」开关控制（设置页默认关），
  普通用户视图不受本轮改动影响。
