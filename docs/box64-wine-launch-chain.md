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
