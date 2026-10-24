# box64 / wine 启动链修复记录（GT7 Pro，39-bit VA + ColorOS）

> 对内文档。目标现象：点游戏后永远停在“正在启动”，wine 起不来或起完就退。

## 1. 结论速览

Windows 程序要在这台设备上跑起来，需要同时满足六件事（缺一即失败）：

| # | 层 | 症状 | 修复 |
|---|----|------|------|
| 1 | 内核 exec-mprotect | `noexec filesystem?`（ntdll .text 加 EXEC 返回 EACCES） | `libmpshim.so` 重映射治愈（raw syscall） |
| 2 | box64 本体 | 治愈后 glibc 堆损坏 / SEH `invalid frame` | 用上游 main 自建（含 09-15 custommem race fix） |
| 3 | prefix stub | `could not load kernel32.dll, status c0000135` | 补齐 builtin stub（system32 + syswow64） |
| 4 | 32 位支持 | 32 位 exe 走 WOW64，`kernel32` 又找不到 | box64 `BOX32=ON` + syswow64 32 位 stub |
| 5 | 注册表 | `nodrv_CreateWindow ... graphics driver is missing` | `HKCU\Software\Wine\Drivers` `Graphics=x11` |
| 6 | 驱动 stub | `open_mapping(winex11.drv) = OBJECT_NAME_NOT_FOUND` | stub 补齐要含 **全部后缀**（`.drv/.cpl/.sys`…） |

六层全通后，32 位 SIGLUS 引擎游戏（《花咲》HANA9cn.EXE）可完整进入运行画面。

## 2. 构建 box64（可复现配方）

入口脚本：`tools/box64/build_box64_android.sh`（生成工具链文件 → configure → 链接 → strip → 打 tzst）。
它不覆盖既有产物，新版本另出一个 `box64-<ver>.tzst`，应用里可按容器回退。

关键参数（**注意：不是 NDK 的 `android.toolchain.cmake`**，rootfs 是 glibc 的，产物必须是
`aarch64-linux-gnu` 的 glibc ELF，用 bionic 目标编出来的跑不起来）：

```cmake
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_C_COMPILER <NDK>/toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe)
set(CMAKE_C_COMPILER_TARGET aarch64-linux-gnu)   # ASM 同理
set(CMAKE_SYSROOT <Debian arm64 glibc sysroot>)  # lib/{libc.so.6,ld-linux-aarch64.so.1,libm.so.6…} + usr/lib/{crt1.o,libc.so}
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
```

```bash
cmake -G Ninja -DARM_DYNAREC=ON -DBOX32=ON -DWINLATOR_GLIBC=ON -DCMAKE_BUILD_TYPE=RelWithDebInfo …
# 关键：BOX32=ON（galgame exe 绝大多数是 32 位，走 WOW64）
```

sysroot 的来源：Debian/Ubuntu arm64 的 `libc6` + `libc6-dev` 解包（`dpkg-deb -x`）后拼成
`lib/` + `usr/{include,lib}`。此来源为事后追记，未逐字复现过，重建时以能链通为准。

**链接期三处必坑（2026-10-23 实测，脚本已内置）**：

1. sysroot 不带 gcc 的 `crtbegin.o`/`crtend.o`/`libgcc.a`。clang 对 `aarch64-linux-gnu`
   是把 crt 对象按**裸文件名**交给 lld 的，`-L` 不生效 —— 必须把它们放进链接的工作目录。
   `libgcc.a` 用 NDK 的 `libclang_rt.builtins.a` 顶替，`libgcc_s.a` 建空归档即可。
2. **rootfs 的 glibc 不导出 `atexit` / `pthread_atfork`**（3070 个动态符号里只有
   `__cxa_atexit` / `__register_atfork`），而 box64 main 自 2026-09 起在
   `core.c` / `custommem.c` / `signals.c` 里直接引用前者。用
   `tools/box64/linkshim_glibc_atexit.c` 转发到 glibc 自己的内部入口补上，语义不变。
3. **`PT_INTERP` 必须是 rootfs 内的绝对路径**
   `/data/data/com.winlator/files/rootfs/lib/ld-linux-aarch64.so.1`。
   用默认 `-lc` 链出来的会是 `/lib/ld-linux-aarch64.so.1`（27B vs 63B），Android linker
   解析不了，症状就是本文档开头那句——**永远停在「正在启动…」且 wine_exec.log 一行都不出**。
   用 `-Wl,--dynamic-linker=…` 指定，链接后用 `readelf -l | grep INTERP` 自检。

前两条是历史上「手工补链接」的全部内容；第三条是 2026-10-23 重建时新踩出来的，
只比对 ELF 段布局看不出来（EXEC/AArch64/base 0x34800000 全一致），必须读 INTERP 字符串。

### 基线与私有改动（2026-10-23 追记，补 TD-05）

| 项 | 值 |
|---|---|
| 在用的 `box64-0.4.5.tzst` 上游基线 | `2a0623116931c391819277f462dfb5b47a18fcb9`（2026-09-30，main） |
| 依据 | 源码 checkout 的 HEAD；私有改动 mtime 均为 09-30，早于出厂 tzst 的 10-01 19:10 |
| 私有改动 | `tools/box64/box64-galgameshell.patch`（3 文件 28 行，已双向校验：能在干净基线上重放，也能反向还原当前工作树） |

补丁内容对应本文档 §1 表的第 1、2 层，**丢了就会在这台设备上死循环或 SIGSEGV**：

- `src/elfs/elfhacks.c` / `src/elfs/elfloader.c`：动态段扫描加 `DT_NULL` 之外的迭代上界
  （39-bit VA 主机 + lld 链接的 box64 会退化成用户态死循环）
- `src/libtools/libdl.c`：新增 `find_real_in_libc()`，把 `dlopen/dlclose/dlsym` 的 real 解析
  限定在 `libc.so.6`，避开 box64 自身导出的 `dl*` 形成 interpose 环（见 §3）

> 生成物 `src/wrapped/generated/functions_list.txt` 当时也带一行本地差异（`wrappedandroidshmem:`，
> 对应源文件已不存在），属陈旧生成残留，**不随补丁携带**，需要时由 `rebuild_wrappers.py` 重生成。

> **可复现约定（2026-10-11，评估整改 P2-8）**：每次自建 box64 必须在此记录上游 commit hash。
> 0.4.5 当时未记录（提交 `e80db63` 仅记「上游 main 自建」），上表的 hash 是 2026-10-23 从残留
> 源码树里恢复的；此后每次构建由脚本末尾提示回填。TD-05 的「hash 丢失」部分已关闭。

## 2b. 三方产物状态（2026-10-23 收口，读这里别看别处猜）

实际进 APK 并执行的 box64 是 `jniLibs/arm64-v8a/libbox64.so`（W^X 决定的，见 §6 与
`GuestProgramLauncherComponent` 的 nativeLibraryDir 分支），**不是** assets 里的 tzst。
tzst 只是它的来源包。三件东西必须分清：

| 物件 | git 状态 | 内容 | 作用 |
|---|---|---|---|
| `assets/box64/box64-0.4.5.tzst` | 已跟踪（force-add 8f86684） | 上游 main `2a062311`，无 gstfix | **构建默认**（`DefaultVersion.BOX64 = "0.4.5"`） |
| `assets/box64/box64-0.4.5-gstfix.tzst` | 已跟踪（force-add，本轮） | `abfb8c3`（#4528）+ 私有补丁 + 链接三步；5,045,155B；内层二进制 sha 前缀 `3e000661` | 实验产物，真机验证过锚点无回归 |
| `jniLibs/arm64-v8a/libbox64.so` | **未跟踪**（`.gitignore:30`） | 当前 = gstfix 那份，21,420,104B，sha 前缀 `3e000661` | 下一次构建真正进包的那份 |

**当前不一致这个事实本身**：构建默认声明的是 0.4.5，而工作区 `libbox64.so` 放的是
0.4.5-gstfix。也就是说此刻直接 `assembleRelease`，产物里跑的是 gstfix，而 `DefaultVersion`
仍写着 0.4.5。这是**故意的**——gstfix 的用户可见收益目前为 0（SystemNNN 仍黑屏），
不设为默认；但留在工作区继续测。任何要出正式包的人，先跑一遍
`tools/prepare_box64_lib.sh`（无参数 = 按 `DefaultVersion.BOX64` 取，即回到 0.4.5）再打。

回滚 / 切换（两条都是实测过的）：

```bash
bash tools/prepare_box64_lib.sh               # → 0.4.5（sha 9391c9c4，21,412,896B）
bash tools/prepare_box64_lib.sh 0.4.5-gstfix  # → gstfix（sha 3e000661，21,420,104B）
bash tools/prepare_box64_lib.sh --list        # 看有哪些版本
```

`tools/prepare_box64_lib.sh` 的缺省版本从 `DefaultVersion.java` 读，不再写死，
所以「构建默认」只有一处定义。它同时受 `tools/make_runtime_checksums.sh` 覆盖——
该脚本原先 `find assets -maxdepth 1`，`assets/box64/` 整批漏出 SHA-256 清单
（修复前 34 个文件、box64 条目 0 条；修复后 57 个、3 条）。

## 3. 垫片 libmpshim.so

- 拦截 `mprotect`；对 EXEC 请求收到 EACCES 时，按 `/proc/self/maps` 找到 backing
  file 与偏移，保存内容 → `munmap` + 带 EXEC 重映射 → 回写 → 返回 0。
- **必须用裸 syscall 发 mprotect**：box64（main 版）导出了 `mprotect` 等包装符号，
  用 `dlsym(RTLD_NEXT, "mprotect")` 会命中 box64 自身形成 interpose 环 → SIGSEGV。
- 通过 `GuestProgramLauncherComponent` 条件注入 `LD_PRELOAD`
  （仅当 `nativeLibraryDir/libmpshim.so` 存在时才注入，无此问题的设备不受影响）。

## 4. prefix 修复（脚本）

```bash
adb push scripts/galgame-fix-prefix.sh /data/local/tmp/
adb shell run-as com.winlator cp /data/local/tmp/galgame-fix-prefix.sh files/
adb shell run-as com.winlator sh files/galgame-fix-prefix.sh \
  /data/data/com.winlator/files/rootfs/home/xuser-N/.wine
```

脚本做两件事：
1. 把 `opt/wine/lib/wine/{x86_64-windows,i386-windows}/*`（**全部后缀**）补齐到
   `drive_c/windows/{system32,syswow64}`（只补不覆盖）。
2. `wine reg add "HKCU\Software\Wine\Drivers" /v Graphics /t REG_SZ /d x11 /f`。

> 每个容器（xuser-N）都要跑一次。

## 5. 诊断手法（反复用到）

- **wine 输出**：`ProcessHelper.exec` 已改为落盘 `files/wine_exec.log`（原为 `/dev/null`
  且异常被静默吞掉）。
- **`WINEDEBUG=+server,+loaddll`**：注意 `new_process` 行内嵌完整 env（单行 24KB+），
  grep 时必须锚定进程号行首（`^00ec:`）并 `grep -av "env=L"`。
- **垫片钩子**：mprotect 记录 + connect（AF_UNIX，抓 X11 socket 实际连接路径）。
- **ptrace PC 采样器**（`psample.c`）：对自旋进程 attach→GETREGSET→detach 采样，
  取 PC 直方图定位死循环函数。曾用它定位到 `libdl.c` 的 dlsym 自咬尾调用环。
- **App 直启**（debug 构建）：`app/src/debug/AndroidManifest.xml` 导出
  `XServerDisplayActivity`，可
  `am start -n com.winlator/.XServerDisplayActivity --ei container_id N --es exec_path <unix路径>`
  绕过 UI 自动化。

## 6. 已知坑

- **nogui 直跑**：启动链 `explorer → winhandler.exe → CreateProcess` 在 32 位 WOW64
  下会挂住，代码里已改为 `wine <exe>` 直跑（并以游戏目录为 cwd）。
- **esync**：`WINEESYNC=1` 本身无害（手工对照验证过），不是失败原因。
- **X socket**：rootfs 的 `libxcb.so.1` 是 glibc 定制版，socket 路径硬编码为
  `/data/data/com.winlator/files/rootfs/tmp/.X11-unix/X`，与内嵌 X server 对齐，
  无需额外处理。
- **手工脚本**：必须先 `unset LD_LIBRARY_PATH` 再 `env LD_LIBRARY_PATH=...`，
  否则 bionic 的 `timeout` 会去加载 rootfs 里那个 glibc 文本 `libc.so` 而失败。

## 7. DirectShow 视频链：已证到 Wine 能力边界，止损（2026-10-23）

> **先读 §7.1**：本节把黑屏归因于"MPEG-2 被拒"，同日追加的普查与反证已推翻该因果归属
> （全库 0 个 MPEG-2；一条零报错的 MPEG-1 链路同样全黑）。本节对 ①② 的排除仍有效，
> 对"黑屏原因"的结论以 §7.1 为准。

**结论：分支 ③ —— 滤镜已加载、已实例化，但 Wine 内建 quartz 的 MPEG-I splitter 不接
MPEG-2 子类型，Intelligent Connect 建图即拆、拆完重试。越过它要改 Wine 源码，而 rootfs
是上游预编译产物，不在本 fork 可改范围内。此线到此为止，不再试参数。**

### 取证：A/B 差分（同一容器 xuser-15 / sysnnn_sainogami，只动 quartz 覆盖，
### 通道 `quartz,ole,module`，box64 均为含 #4528 的 gstfix 构建）

| | A：`quartz=builtin` | B：`quartz=native,builtin`（出厂态） |
|---|---|---|
| `[BOX64]` GStreamer 桥报错 | **0 条**（#4528 生效） | 0 条 |
| `winegstreamer.dll` 加载 | 是（syswow64 映射进 0x7a970000） | 是 |
| `wg_parser_create` 实例化 | **18 次** | 6 次 |
| 走到 `mpeg_splitter` | 是，拒绝 MPEG-2 **4 次** | **0 次**（更早就失败） |
| 首个失败点 | `mpeg_splitter_sink_query_accept` 拒 `{e436eb85-…}`(MPEG2_VIDEO) / `{e436eb86-…}`(MPEG2_PROGRAM) | `com_get_class_object class {7d8aa343-6e63-4663-be90-6b80f66540a3} not registered`（CLSID_DirectSound8） |
| 建图结果 | 两张图各自建完即拆（`GraphConfig_RemoveFilterEx … stub!`） | 未进入建图 |
| 画面 | 客户区全黑 | 客户区全黑 |

三条判据的落点：**① 排除**（有 load 行）、**② 排除**（`wg_parser_create` 18 次说明
滤镜真的被创建）、**③ 成立**。B 列同时说明出厂态比 builtin 更差——失败点更靠前。

### 关键反证（别被"没有解码器"误导）

rootfs 的 GStreamer 插件里 **`mpeg2dec` 与 `libav`（3.9MB 静态 ffmpeg）都在**，
MPEG-2 软解码能力不缺。缺的是 Wine 侧把流交给它的通路：内建
`dlls/quartz/mpeg_splitter.c` 的 sink pin 只认 MPEG-1 系（`MEDIASUBTYPE_MPEG1VideoCD`
等在 `enum_media_types_Next` 里能过），MPEG-2 两个子类型直接 `Unsupported subtype`。
所以这不是配置问题、不是环境变量问题，是 Wine 的实现边界。

### 任何后续配置改动落地前必须过的三格

1. **SystemNNN 出帧**（当前：否）
2. **Unity 不崩** —— 全局把 dsound/quartz 改 builtin 会让 test_unity
   `err:seh:NtRaiseException c0000005`（已实测，原生态同局不崩）
3. **锚点《花瓣！》HANA9 不退化**（六层启动链的回归锚）

第 2 格决定了最终形态大概率是**按引擎分流**而非一个全局开关。

### 本轮未测到的格子

- YU-RIS / AdvHD / BGI / CMVS 在 gstfix box64 下的状态（只测了 SystemNNN 与两个锚点）
- 非 MPEG-2 编码的片头（如纯 WMV/AVI）能否经 winegstreamer 出帧——若某引擎用 MPEG-1
  或 GStreamer 直连路径，可能已经能用，这是唯一还没探的方向
- 分支 ③ 之下的 `+strmbase` 级 trace（用于确认 Intelligent Connect 的候选枚举顺序）

### 7.1 普查与反证：§7 的因果归属不成立（2026-10-23 同日追加）

**普查**（静态，按实际编码不按扩展名）：14 个源目录 + `/sdcard/Games`，86 个候选文件，
取每个文件头 64B 判 magic，`.mpg` 另取 512KB 扫 MPEG 起始码并用 ffprobe 9.0.1 定名。

| 实际编码 | 数 | 归类 |
|---|---|---|
| ASF / WMV（magic `30 26 B2 75 8E 66 CF 11`） | 69 | (a) 内建 quartz 可试 |
| MPEG-1（ffprobe `mpeg1video` + `mp2`；有 `00 00 01 B3`，**无** `B5`/`BC`） | 5 | (a) |
| 非视频（`unins000.dat`、`yscfg.dat`、`Gameexe.dat`、984B 的 `scene.dat` 等） | 12 | 排除 |
| **MPEG-2** | **0** | (b) 空集 |
| Bink / CRIWARE | 0 | (c) 空集 |

全库没有 MPEG-2 片头。SystemNNN 的 OP 是 `movie/sainogami_op.wmv`（179,983,768B，ASF），
AdvHD 全部 `.dat` 实为 ASF，YU-RIS 的 `.mpg` 是 MPEG-1。

**反证实验**（一局，条件 = gstfix box64 + `quartz=builtin` + 通道 `quartz,ole,module`，
容器 xuser-10 / yuris_lufia，媒体属 (a) 中"内建 splitter 明确接受"的那一族）：

- `[BOX64]` 报错 **0 条**（#4528 生效）
- `wg_parser_create` 3 次（winegstreamer 滤镜被实例化）
- **`fixme:quartz` / `err:quartz` / `err:ole` 全为零** —— DirectShow 层没有任何失败
- `MEDIASUBTYPE_MPEG1System` / `MPEG1Payload` / `MPEG1AudioPayload` 正常枚举
- `quartz:VideoWindow_put_WindowStyle` + `BaseControlWindowImpl_put_Owner window … owner 0x20036`
  → 视频渲染窗口已创建并挂上父窗口
- `ddraw` / `wined3d` 相关行 175 条 → 引擎确实进了渲染路径
- **画面：全黑**

**因此 §7 正文那条"卡在 MPEG-2 被拒"的因果归属作废**：那些
`mpeg_splitter_sink_query_accept Unsupported subtype {e436eb85}/{e436eb86}` 是
Intelligent Connect 枚举候选滤镜时的正常探测噪声，不是阻塞点。一条零报错的
MPEG-1 链路同样全黑，说明真因在渲染面/窗口面，不在视频解码链。
①②的排除仍然成立（winegstreamer 确实加载、滤镜确实注册），错的只是我把
"分支 ③"当成了"黑屏的原因"。

**收益判定**：gstfix 修的是一个真实缺陷（GStreamer 桥加载失败），但用户可见收益 = 0。
按约定**不设为构建默认**；工作区 `libbox64.so` 已用 `tools/prepare_box64_lib.sh`（无参数）
退回 0.4.5，§2b 记录的"构建默认与工作区不一致"状态已消除。gstfix 产物留在仓内作可复现资产。

本线终止，登记为 TD-09（不计划偿还）。

> 边界提醒：Ren'Py 的 `.ogv` / `.webm` 走内嵌原生引擎，不经 Wine，不在本普查范围内。

### 7.2 黑屏真因：`put_WindowStyle` 的跨线程 `SetWindowPos` 死锁（2026-10-24）

§7.1 把问题定位到"渲染面/窗口面"之后，本轮在真机上把卡点坐实到一条具体的窗口消息。

**环境**：容器 xuser-10 / `yuris_lufia`，`quartz=builtin`，`WINEDEBUG=quartz,msg`，
媒体为 MPEG-1 1920x1080（`ffprobe` 定名）。

**图建得成、解码也在配**：

- `FilterGraph2_RenderFile Returning 0`，链路完整：
  Reader → MPEG-I Stream Splitter → {MPEG Video Decoder → Video Renderer,
  MPEG Audio Decoder → Wave Audio Renderer}
- `wg_transform_create … video/mpeg width=1920 height=1080 mpegversion=1 → NV12`
- `wg_transform_create … audio/mpeg rate=44100 channels=1 → S16LE`
- `vmr7_create`、`filter_config_SetRenderingMode mode 1`、
  `surface_allocator_AllocateSurface`、`windowless_control_SetVideoClippingWindow(0x10050)`

**卡点**：视频窗口 `0x10050` 属于 Wine 线程 0114（1280x720 那次）/ 0128（1920x1080 那次），
而 DirectShow 调用方是 00bc / 00c0 —— 每一次窗口操作都是跨线程同步 `SendMessage`。

```
put_Owner  → WM_STYLECHANGING  → retrieve_reply 0     ✓
           → WM_STYLECHANGED   → retrieve_reply 0     ✓
           → WM_WINE_SETPARENT → retrieve_reply 10020 ✓
put_WindowStyle(0x56000000)
           → WM_STYLECHANGING  → retrieve_reply 0     ✓
           → WM_STYLECHANGED   → retrieve_reply 0     ✓
           → SetWindowPos
           → WM_WINE_SETWINDOWPOS → peek_message 收到  ✗ 再无 retrieve_reply
```

对照：**视频窗口建立之前**同一 hwnd 上的一次 `SetWindowPos` 正常回了 `reply 1`，
说明消息通道本身是通的；卡死只发生在视频窗口自己的那次。

**后果**：DirectShow 线程永久阻塞在同步 `SendMessage`，`IMediaControl::Run` 从未被调用。
7 份日志（含本轮 2 份）里 `Run` 命中数 **0**；而 `diagA` 里 `MediaControl_Stop` 命中 4 次，
证明该方法族有 trace、不是"没开通道"。所以片头不是"播了看不见"，是**从来没开始播**。

**排除项（逐条实测）**：

| 假设 | 判据 |
|---|---|
| 解码链不通 | 排除：transform 已按 MPEG-1 → NV12 / S16LE 建好 |
| 片头是 MPEG-2 被拒 | 排除：§7.1 全库 0 个 MPEG-2 |
| 视频被游戏画面遮挡 | 排除：视频子窗口从未进入运行态 |
| 容器尺寸不匹配（1280x720 vs 1920x1080 片头） | 排除：把 `screenSize` 改成 1920x1080，死锁位置**完全一致** |
| 应用侧 X server 卡在写 socket | 排除：卡死期间应用线程全在 `do_epoll_wait` / `futex_wait_queue`，无 socket 写 |

**一条独立的代码级缺陷（不构成本次已证因果，已修）**：
`app/src/main/cpp/winlator/src/xoutput_stream.c` 的 `XOutputStream_send` 原本是裸阻塞
`write()`，无非阻塞/EAGAIN 处理，且把部分写当成功（`bytesSent >= 0`）——部分写会截断
X11 协议流，`write()` 还会触发 SIGPIPE。既然 X server 在卡死期间是空闲的，这条不是本次
元凶，但已按缺陷单修：改为 `send(MSG_NOSIGNAL)` 循环写满，EINTR 重试，EAGAIN 时
`poll(POLLOUT)` 最多等 2 秒（把"无限期卡住并占着流锁"变成有界失败）。失败传播的缺口
（`sendEvent` 的 catch 只 `printStackTrace`、不终止连接）另记 TD-10。

**取证过程中的两次自我更正**（留痕，避免后人重蹈）：

1. 最初只看日志尾部，把"最后一条"当成"卡住的那条"，且误以为 X server 的
   `killAllConnections → … → XOutputStream.close` 是无限递归——实际是每个子窗口各打一个栈，
   不是递归。
2. 随后又误把两次运行里不同的 owner 句柄（`0x20036` / `0x40024`）读成"同一次运行调了两次
   `put_Owner`"。按行号对齐后确认：每次运行各只有一次 `put_Owner` + 一次 `put_WindowStyle`，
   卡点就是 `put_WindowStyle` 里的 `SetWindowPos`。

**未完成**：`debuggerd -b` 需要 root（真机未 root），原生栈拿不到；`winedbg` 路线未试。
因此"Wine 线程 0114 究竟阻塞在哪把锁/哪个 wndproc"仍未坐实，只坐实到消息级别。

**可修性**：Wine 侧，应用层不可改。要落地修复需改 rootfs 里的 Wine（上游预编译产物），
或由上游 Wine 修 `quartz` 的窗口路径。TD-09 已按此更新偿还条件。

### 7.3 虚拟桌面绕过路线：当前不可用（2026-10-24）

TD-09 的一个候选绕过是"把游戏跑在虚拟桌面里"——游戏窗口会变成 `explorer` 桌面的子窗口，
窗口归属线程改变，跨线程 `SendMessage` 的前提可能消失。应用本来就有这个开关：容器 extra
里 `galgame_launch_mode=desktop` 时，`XServerDisplayActivity` 会执行
`wine explorer /desktop=nogui,<res> <target>`。

实测：

| 场景 | 结果 |
|---|---|
| 不带 `exec_path` 启动容器（`explorer /desktop=<name>,<res>`，无程序参数） | ✅ 正常出桌面（Wine 桌面 + 文件管理器 + 任务栏） |
| `galgame_launch_mode=desktop` + 指定程序 | ❌ 全黑；`explorer.exe` 变僵尸，游戏进程从未启动 |

代码侧定位到一处真实缺陷并已修：桌面分支把 `target` 原样交给 explorer，而只传 `exec_path`
时 `target` 是**宿主 unix 路径**，explorer 不认。现已用
`GalgameLaunchHelper.toWindowsPath()` 补成 `C:\` 路径。

**修好路径后仍然失败**：`wineserver` 与 `winedevice.exe` 在跑，`explorer` 与游戏进程都不在。
即 `explorer /desktop=… <program>` 这个形态在当前 Wine 10.10 + box64 组合下起不了程序
（不带程序参数则正常）。

**结论**：虚拟桌面作为 TD-09 的绕过路线**当前不可用**，瓶颈在 Wine/box64 侧。
剩余候选只有"绕过片头"与"上游 Wine 修复"。
