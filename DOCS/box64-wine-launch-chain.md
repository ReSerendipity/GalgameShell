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

```bash
cmake -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_HOME/ndk/27.0.12077973/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-34 \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DARM_DYNAREC=ON -DBOX32=ON -DWINLATOR_GLIBC=ON \
  <box64src>
# 关键：BOX32=ON（galgame exe 绝大多数是 32 位）
# ninja 的默认链接命令会缺 crt/libgcc，需自行链接（绕过 ninja 的 link 步骤）
```

产物经 `llvm-strip --strip-all` 后打成 `app/src/main/assets/box64/box64-<ver>.tzst`
（内部结构对齐官方：`./usr/local/bin/box64`，zstd -19）。

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
