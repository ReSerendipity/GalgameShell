# 原生引擎内嵌方案（Native Engine Embedding）

> 目标：把「识别到某引擎 → 用该引擎的原生运行时直接跑」这条链路做进 **GalgameShell 自身 APK**，
> 而不是再安装一个第三方播放器 APK（用户明确否决：独立 APK 的方式不要）。
>
> 状态：**Ren'Py 引擎已端到端跑通**——APK 内嵌 `librenpython.so`（Python 3.12.8 + Ren'Py 8.5.3），
> 真机（dc57ebe3/RMX5010）直启渲染出真实游戏对话画面并截图取证（§4.6），游戏库生产路线
> （导入→识别→内置引擎一键启动）E2E 亦已通过（§4.7）。Kirikiroid2 此前已跑通 bosei2。
> 本文只记录已核实事实，猜测一律标注。

## 1. 结论先行

| 结论 | 依据 |
| --- | --- |
| **只有 Kirikiroid2 适合内嵌**（其余都有许可证硬伤） | §2 许可证矩阵 |
| Kirikiroid2 的 JNI 契约是**硬编码类名** `org/tvp/kirikiri2/KR2Activity`，我们只要在自家 APK 里提供同名类即可接管 | `src/core/environ/android/AndroidUtils.cpp:38` 及全部 `JniHelper::getStaticMethodInfo(..., "org/tvp/kirikiri2/KR2Activity", ...)` |
| 原生库名是 **`libgame.so`**（不是 libcocos2dcpp.so），另需 **`libffmpeg.so`** | `project/android/jni/Android.mk` `LOCAL_MODULE_FILENAME := libgame`；`AndroidManifest.xml` meta-data `android.app.lib_name=game`；`KR2Activity.java` static 块 `System.loadLibrary("ffmpeg")` |
| 引擎 UI 层是 **cocos2d-x**，`KR2Activity extends Cocos2dxActivity` | `KR2Activity.java:250`、Android.mk `LOCAL_WHOLE_STATIC_LIBRARIES := ... cocos2dx_static` |
| **arm64-v8a 是官方构建的一等公民**（存在 64 位产物） | Yuri 分支 `project/android/jni/Application.mk`：`APP_ABI := armeabi-v7a` + `arm64-v8a` |
| 上游 Android 侧 Java **只有 3 个文件**（不含 cocos），体量可控 | `zeas2/Kirikiroid2@master`：`project/android/src/org/tvp/kirikiri2/{KR2Activity,Kirikiroid2,MediaStoreHack}.java` |
| 从源码完整构建依赖极重（cocos2d-x 3.17.2 + FFmpeg + OpenCV + OpenAL + oboe + libarchive + lz4 + breakpad + …） | YuriSizuku/Kirikiroid2Yuri README §2 Build |

## 2. 许可证矩阵（决定「谁能集成」）

本仓库是 **LGPL-2.1**（根目录 `LICENSE` + `NOTICE`）。把第三方运行时**静态并入我们自己的 APK** 会构成衍生作品，
因此许可证是硬约束：

| 运行时 | 许可证 | 能否内嵌 | 说明 |
| --- | --- | --- | --- |
| **Kirikiroid2**（含 Kirikiri2/Z 本体） | **BSD 3-Clause** | ✅ 可以 | 无 copyleft，不要求开源自身代码；只需保留版权声明 + 不用作者名义背书 |
| EasyRPG Player | GPLv3 | ❌ | 并入会把整个项目拖成 GPLv3 |
| ONScripter | GPLv2 | ❌ | 与 LGPL-2.1 组合存在冲突/升级压力 |
| PPSSPP | GPLv2+ | ❌ | 同上 |
| JoiPlay / Tyranor | 闭源 | ❌ | 无源码；只能外部唤起（且需免责） |
| Ren'Py | MIT + 部分 LGPL | 🟡 可内嵌（**未做**） | 2026-10-04 核实：多数代码 MIT，另有部分 LGPL 组件。LGPL 与本仓 LGPL-2.1 **兼容**，可并入（须提供 LGPL 源码链接）。但需集成 Python 运行时（renpy-build/python-for-android），工作量远大于 Kirikiroid2。覆盖 8000+ VN，是下一个最有价值的内嵌候选。 |

> Kirikiroid2 的 LICENSE 文件本身是「主许可证 + 第三方声明」的合集，
> 主许可证为 BSD 3-Clause（W.Dee & contributors），其余为 libjpeg-turbo / libpng / zlib /
> oniguruma / FreeType / picojson / AOSP(Apache-2.0) / Xiph 等，**全部为宽松许可证，无 GPL/LGPL/AGPL**。

⚠️ **注意**：上游 build 会链入自建 FFmpeg（`zeas2/FFmpeg`）。若该构建开了 GPL 组件，
分发 `.so` 会引入 GPL 义务。**Phase 2 用我们自己的构建替换二进制时必须核对 FFmpeg 配置。**

## 3. JNI 契约（移植要点）

`libgame.so` 通过 JNI 回调以下**静态方法**（Java 侧必须是 `org.tvp.kirikiri2.KR2Activity`）：

```
ShowMessageBox(String,String,String[])V      ShowInputBox(String,String,String,String[])V
MessageController(III)V                      requireLEXA(String)V
isWritableNormal(String)Z                    CreateFolders(String)Z
WriteFile(String,[B)Z                        getLocaleName()Ljava/lang/String;
exit()V                                      hideTextInput()V
showTextInput(IIII)V                         DeleteFile(String)Z
RenameFile(String,String)Z
```

反向（Java 调原生，符号在 libgame.so 内）：

```
Java_org_tvp_kirikiri2_KR2Activity_nativeTouches{Begin,End,Move,Cancel}
Java_org_tvp_kirikiri2_KR2Activity_nativeKeyAction / nativeCharInput / nativeCommitText
Java_org_tvp_kirikiri2_KR2Activity_native{InsertText,DeleteBackward}
Java_org_tvp_kirikiri2_KR2Activity_native{HoverMoved,MouseScrolled}
Java_org_tvp_kirikiri2_KR2Activity_nativeGetHideSystemButton / nativeOnLowMemory
Java_org_tvp_kirikiri2_KR2Activity_initDump / onMessageBoxOK / onMessageBoxText
```

→ **集成方式**：在自家 APK 中以 `org.tvp.kirikiri2` 包名提供 KR2Activity（BSD 源码移植），
再提供 `org.cocos2dx.lib.*`（cocos2d-x 的 Android Java 支持层，MIT），
最后把 `libgame.so` / `libffmpeg.so` 放进 `jniLibs/arm64-v8a/`。
JNI 找不到方法只会静默失败（`getStaticMethodInfo` 返回 false），不会崩，
因此缺失方法可接受，但**必须尽量对齐版本**，否则文件写入/弹框等行为会失效。

## 4. 分阶段路线

- **Phase 1（进行中）**：二进制级内嵌 —— 预编译 `libgame.so` + `libffmpeg.so` + 自带的 Resources（字体/UI），
  Java 层用上游 BSD 源码 + cocos2d-x MIT 源码落进工程；我们自己的路由检测到 Kirikiri 就拉起**进程内** Activity。
  *解决的问题：不再装第二个 APK、不再在第三方 App 里手选目录。*
- **Phase 2（后续）**：源码级构建 —— 把 Kirikiroid2 源码 + `thirdparty_build`（预编译第三方静态库）
  以 NDK（`ndk-build`，非 CMake）纳入工程，产出自有 `.so`，消除 GPL/来源不明风险。
- **Phase 3（可选）**：把 Kirikiroid2 自带的 cocos 文件浏览器 UI 剥掉，由我们的游戏库直接传路径启动。

## 4.1 Phase 1 落地实录（2026-10-03）

### 已落地的东西

| 项 | 落点 |
| --- | --- |
| 原生库 | `app/src/main/jniLibs/arm64-v8a/{libgame.so, libffmpeg.so, libSDL2.so}`（`.gitignore` 忽略 `jniLibs/**/*.so`，入库需 `git add -f`） |
| 引擎 Java | `app/src/main/java/org/tvp/kirikiri2/KR2Activity.java`（823 行，BSD，包名类名不可改） |
| cocos Java | `app/src/main/java/org/cocos2dx/lib/`（28 个文件，MIT；原版依赖 `com.loopj.android.http` + `cz.msebera.httpclient` 的部分已重写，见下） |
| 引擎资源 | `app/src/main/assets/{ui/**.csb, locale/*.xml, img/**, Default/**, DroidSansFallback.ttf, default.cur}` 全量并入（与 Winlator 自有 assets 合并，共 158 文件） |
| manifest | `<meta-data android:name="android.app.lib_name" android:value="game" />`（主库靠它 `System.loadLibrary`）+ KR2Activity 声明 |
| 主题 | `@style/GalgameKrkrTheme`（parent `@android:style/Theme.NoTitleBar.Fullscreen`，对齐上游 `@0x01030007`） |
| 启动器 | `com.winlator.galgame.engine.KrkrEngine`：先写 `recentpath.xml`，再 `startActivity` 直启 `org.tvp.kirikiri2.KR2Activity`（**不经过任何跳板 Activity**） |

**路径注入契约**：引擎在 `cocos2dx FileUtils::getWritablePath() + "/.preference/recentpath.xml"` 读最近路径，
根元素 `<RecentPathList>`、子项 `<Item Path="..."/>`。`getWritablePath()` 落内/外不确定，
所以 **内部 `getFilesDir()` 与 `getExternalFilesDir(null)` 各写一份**。
真机已确认引擎读到的是内部那份：`/data/data/com.winlator/files/.preference/recentpath.xml`。

`KrkrEngine.resolveEntry()` 的入口解析顺序：`data.xp3` → 目录内**体积最大**的 `*.xp3` → 都没有则直接写目录让引擎扫描
（应对 `data.bin` 这类“内容实为 XP3、只是改了名”的移植包）。

### 两次致命崩溃与修复（都是裁剪 cocos2dx 的后遗症）

1. **SIGSEGV 栈溢出（GL 线程）**：`Fatal signal 11 SEGV_ACCERR`，`Cause: stack pointer is not in a rw map`，
   back trace 512 帧全是 `LocaleConfigManager::GetFilePath()` ↔ `cocos2d::FileUtilsAndroid::isFileExist` 互相递归。
   → **根因：缺 `assets/locale/*.xml`**（只拷了 `default.cur` + 字体）。补齐全部 assets 后消失。
   教训：`unzip k2.apk 'assets/*'` 只解出 3 个文件，**必须整包解压**再拷。
2. **`ClassNotFoundException: org.cocos2dx.lib.Cocos2dxDownloader`**：`Cocos2dxRenderer.nativeInit` 内部对
   Downloader 做 `Class.forName`，类不存在即炸。libgame.so 引用了 3 个我们曾删掉的类：
   `Cocos2dxDownloader` / `Cocos2dxHttpURLConnection` / `GameControllerHelper`。
   → `Cocos2dxHttpURLConnection` 无第三方依赖直接拷回；`Cocos2dxDownloader` **重写**为纯 `HttpURLConnection` 实现。

> **硬约束（别再裁）**：`libgame.so` 反射调这 3 个静态方法，**签名改一字就崩**：
> ```
> static Cocos2dxDownloader createDownloader(int, int, String, int)
> static void createTask(Cocos2dxDownloader, int, String, String)
> static void cancelAllRequests(Cocos2dxDownloader)
> ```
> 对应 so 里的签名串：`(IILjava/lang/String;I)Lorg/cocos2dx/lib/Cocos2dxDownloader;`、
> `(Lorg/cocos2dx/lib/Cocos2dxDownloader;ILjava/lang/String;Ljava/lang/String;)V`、
> `(Lorg/cocos2dx/lib/Cocos2dxDownloader;)V`。JNI 走 **RegisterNatives**（so 里只有短名
> `nativeOnProgress` / `nativeOnFinish`，没有 `Java_org_cocos2dx_lib_*` 前缀）。

### 真机验证状态（dc57ebe3 / Android 15）

- ✅ 进程**不再崩溃**：无 `Fatal signal` / `ClassNotFoundException` / `Force finishing`，
  KR2Activity 稳定 `ResumedActivity`，SurfaceView 持续出帧（`BLASTBufferQueue ... 1264x2640` 每 10s 更新）。
- ✅ `libgame.so` 加载、`Cocos2dxHelper` 初始化、`applicationDidFinishLaunching` 全部通过。
- ✅ recentpath 注入生效：引擎识别出游戏并显示 **bosei2 加载页**（白底 + 绿色标题栏 `bosei2`）。
- ✅ **游戏画面已拿到（2026-10-04，见 4.3）**：此前「加载页 10+ 分钟不动」的根因是
  `isWritableNormal` JNI 方法缺失（可写性检查恒败）+ `resolveEntry` 误选 patch2.xp3 当主包，
  两者修复后 splash → 年龄确认页 → 主标题菜单全部正常渲染。
- 备注：`/sdcard/Games/bosei2` 里 `data.bin`（97 MB）头 4 字节就是 `XP3\r\n`，是改名的 XP3；
  该游戏自带 `xp3filter.tjs`（逐字节 XOR 过滤器）。~~曾在真机复制出 `data.xp3`（97 MB 副本），需清理~~
  （已清理，2026-10-04）。

### 真机复测手册（设备连上后）

```bash
export MSYS_NO_PATHCONV=1
# 1) 注入路径（内部私有目录，引擎读的就是这份）
printf '<?xml version="1.0" encoding="UTF-8"?>\n<RecentPathList>\n  <Item Path="/sdcard/Games/bosei2/data.xp3"/>\n</RecentPathList>\n' > C:/tmp/rp.xml
adb -s dc57ebe3 shell "run-as com.winlator sh -c 'cat > /data/data/com.winlator/files/.preference/recentpath.xml'" < C:/tmp/rp.xml
# 2) 启动（Activity 已收回 exported=false，直启壳会失败 → 走游戏库点开关，或临时改 exported=true）
adb -s dc57ebe3 shell am start -n com.winlator/org.tvp.kirikiri2.KR2Activity
# 3) 观察：日志要一条命令跑完，后台 logcat 会随对话轮次被回收
adb -s dc57ebe3 logcat -v time -d | grep -E "Fatal signal|ClassNotFound|Force finishing|cocos2d"
adb -s dc57ebe3 shell top -n 1 -b | grep com.winlator
adb -s dc57ebe3 exec-out screencap -p > C:/tmp/krkr.png
```

## 4.2 加载卡死根因分析（静态，2026-10-04）

**现象**：引擎识别出 bosei2 并显示其加载页（白底 + 绿标题栏），之后 CPU 持续 20–27% 在忙，
但 10+ 分钟画面一帧不变、不生成缓存目录；换 `patch.xp3`（535 KB，无过滤器）同样复现。
用户硬要求：**必须拿到真实游戏画面截图**，进程起来 / 渲染帧都不算完成。

### 已排除的集成侧嫌疑（逐项静态取证）

| 嫌疑 | 结论 | 证据 |
| --- | --- | --- |
| 原生库加载顺序错（ffmpeg/SDL2 须在 game 之前） | ❌ 排除 | `KR2Activity` static 块先 load ffmpeg→SDL2→game；`Cocos2dxActivity.onCreate` 经 `android.app.lib_name` 再 load game 是冗余 no-op，顺序已正确 |
| `Cocos2dxDownloader` 回调 NPE（`Cocos2dxHelper.getActivity()` 为 null） | ❌ 排除 | `Cocos2dxHelper.init(this)` 在 `onCreate` 内、`sActivity` 于引擎运行前已赋值 |
| Android 15 分区存储挡住读游戏目录 | ❌ 排除 | `targetSdkVersion = 28`，按旧存储模型运行，外部存储读自动放行 |
| 引擎资源缺失（`assets/locale/*.xml`、`ui/*.csb`、`DroidSansFallback.ttf`） | ❌ 排除 | 均已并入 `app/src/main/assets/` 根目录且 git 跟踪（与 Winlator 自有 assets 合并） |
| Downloader JNI 签名不匹配 | ❌ 排除 | 三签名 `(IILjava/lang/String;I)L…`、`(L…;ILjava/lang/String;Ljava/lang/String;)V`、`(L…;)V` 与 `libgame.so` 逐字一致 |
| `android.app.lib_name` 导致 game 被双重/提前加载 | ❌ 排除 | 同上，static 块已先行加载，meta-data 仅为冗余保险 |

### 关键 reinterpretation：23% 的 CPU 不一定是「自旋」

cocos2d-x 的 GL 线程以 ~60fps 持续 `nativeRender`，**即便画面是静态加载页也会稳定占用一部分 CPU**。
所以「CPU 20–27% 在忙」更可能是**正常渲染/事件循环**，而非某线程死循环。
→ 卡死的本质更可能是：**游戏启动脚本在等一个永远不会到来的事件**（而非算力卡死）。

### 两个 leading hypothesis（待现场取证区分）

1. **在等用户点一下**：不少 galge 的「loading」实为「读取完成，请按开始」界面；之前 `input tap`
   坐标换算打不中 cocos 自绘按钮。→ 若中心点击能推进，则不是 bug，只是缺交互。
2. **在等一个从未 fire 的异步回调**：游戏启动经由 `Cocos2dxDownloader`（引擎内嵌
   `https://zeas2.github.io/Kirikiroid2_patch/patch` 自检 URL）或 cocos 网络栈发起加载，
   回调若因路径/线程问题没回到原生侧，游戏会一直等。→ 已在 `Cocos2dxDownloader.createTask/onFinish`
   加诊断日志；现场 `logcat` 看是否有 `createTask` 而无配对的 `onFinish`。
3. **TJS 启动脚本自身在某步空转/未推进**：需 `logcat` 末行 + `kill -3` 线程栈定位。

### 现场取证脚本（已就绪）

`.workbuddy/verify/krkr_diag.sh` —— 在**设备端**跑完整个观察循环（规避后台 logcat 被会话回收）：
清空并放大 logcat 缓冲 → 落 recentpath.xml（run-as）→ 周期截图 + `top -H` 线程快照 →
**启动 60s 后做一次屏幕中心 `input tap`**（旋转不变，专治「等点击」假设）→ 结束 dump logcat + SIGQUIT 栈。

```bash
SER=dc57ebe3
adb -s $SER push .workbuddy/verify/krkr_diag.sh /data/local/tmp/
adb -s $SER shell sh /data/local/tmp/krkr_diag.sh /sdcard/Games/bosei2 480
adb -s $SER pull /sdcard/krkr_diag ./krkr_diag_out
```

> 重要：KR2Activity 是 `exported=false`，**不能**用 `am start` 直启，必须由「GalgameShell 游戏库点开游戏」
> 触发（KrkrEngine.launch 写 recentpath.xml + startActivity）。脚本的 run-as 写入是兜底。

## 4.3 端到端打通实录（2026-10-04，真机 dc57ebe3）✅

**结论：内置 Kirikiroid2 引擎已在真机完整跑通 bosei2——主标题菜单截图确认，全部在本 APK 进程内。**

### 根因链条（三层，逐层剥开）

1. **`resolveEntry` 误选补丁当主包**（已修，0ae9204 后补）：目录里有 111MB 的 `patch2.xp3`，
   按「最大的 .xp3」裁决会误选它 → 挂载后无 `startup.tjs`。修复后裁决顺序：
   `data.xp3` → `data.bin`（过 XP3 魔数校验）→ 排除 patch/update/补丁 命名的最大 .xp3 → 目录。
2. **「只读的外部存储器」对话框恒现**：libgame.so 原生层按硬编码 JNI 名
   `isWritableNormal (Ljava/lang/String;)Z` 反射调用 Java 层，而 `KR2Activity` 只有
   `isWritableNormalOrSaf`（上游裁剪 SAF 时把 normal 变体一并裁掉了）→ `GetStaticMethodID`
   失败 → 可写性探测恒失败，无论实际权限（All-Files-Access 已授、游戏在应用专属目录）都报只读。
   **修复：补回 `isWritableNormal(String)`**（临时探测文件法，等价上游 normal 分支；
   `isWritableNormalOrSaf` 改为委托调用——本项目 `getDocumentFile` 恒返回 null，SAF 无实际分支）。
3. **Android 11+ 整库读写 /sdcard**：manifest 补 `MANAGE_EXTERNAL_STORAGE`，
   设置页手动授权「所有文件访问」（ColorOS 禁止 adb `appops set`，必须 UI 手授）。

### 真机验证证据链

- 引擎日志（`cocos2d-x debug info`）：
  - `Program started on Android` → `Trying to read XP3 ... bosei2/data.bin` → `Done. (contains 639 file(s))`
  - `Loading startup script : ...data.bin>startup.tjs`（此前卡死时永远到不了这行）
  - `arc/voice.xp3`、`evimage.xp3`、`bgimage.xp3` 等全部挂载成功
- 截图（`.workbuddy/verify/krkr_verify2/`）：
  - `tap_s1.png` — AkabeiSoft2 LOGO splash（灰→白背景动画中）
  - `after_center_tap.png` — 18 禁年龄确认页（日文全文渲染清晰）
  - `next_1.png` — **《母性カジョ2》主标题菜单**（LOGO/八按钮/立绘/版本号全部正确）
- 全程零 crash、零 `UnsatisfiedLinkError`、零 `NoSuchMethodError`。

### 验证手法备忘（临时 exported + 直启）

最终版 KR2Activity 必须 `exported=false`；为隔离验证引擎修复，临时改 `exported=true`
重建 → `am start` 直启（recentpath.xml 已指向 data.bin）→ 截图取证 → **收回 `exported=false`
重建安装**，并用 `am start` 被拒（SecurityException: not exported）实证安全设置已恢复。
生产启动路径不变：游戏库 → KrkrEngine.launch（写 recentpath.xml + 同 APK 内 startActivity）。

### 工具链坑（新增）

- **`adb pull` 目标路径必须用 Windows 形式**（`C:/Users/...`）：Git Bash 下传 `/c/...`
  给原生 adb.exe 会报 `cannot create file/directory`（MSYS_NO_PATHCONV=1 时 MSYS 不转换，
  adb.exe 自己也不认 `/c/` 形式）。
- `adb input tap` 坐标随当前屏幕旋转（横屏下 screencap 2780x1264 与 tap 同一坐标系）。

### 遗留小问题（不阻塞）

- 引擎浏览器左面板（▶ 播放按钮 + 最近路径列表）**渲染不可见但点击有效**——cocos UI 皮肤
  渲染问题，靠坐标点击绕过；不影响游戏本体画面。
- recentpath.xml 指向 `data.bin` 时引擎浏览器显示 bosei2 目录条目，点 ▶ 直接启动——正常路径。

## 4.4 Ren'Py 内嵌方案（2026-10-04 spike 完成，待铺开）

用户 2026-10-04 拍板：下一个内嵌引擎选 **Ren'Py**（覆盖 8000+ VN，许可证兼容）。本节为
可行性 spike 结论 + 实施方案。

### Spike 结论：可嵌入 ✅

Ren'Py Android 运行时的构成（据 renpy-build 源码 + Ren'Py Android 运行时逆向文档）：

| 层 | 组件 | 说明 |
| --- | --- | --- |
| 原生 | `librenpython.so`（arm64-v8a, 35MB） | **单一合并库**：内嵌 CPython 解释器 + Ren'Py 原生加速模块 + pygame_sdl2 + SDL2（全部静态链入）。**没有**独立的 librenpy.so / libSDL2.so |
| 资源 | `assets/renpy/**` | Ren'Py 引擎 Python 字节码（`__pycache__/*.cpython-312.pyc`，CPython 3.12 与 librenpython.so 同版本，可移植）+ `common/` 主题字体 + 15 个子包 |
| 资源 | `assets/game/*.rpa`, `*.rpyc` | 游戏数据（.rpyc = pickled AST，**非** CPython 字节码） |
| Java 壳 | `org.renpy.android.PythonSDLActivity` | 继承 SDL `SDLActivity`；`mActivity` 静态引用供 pyjnius 回访 |

> **关键修正（2026-10-04 续）**：此前按 renpy-build 文档假设 native 层是 `libpython*.so` + `librenpy.so` + `libSDL2*.so` 三件套。
> 实测 RAPT 产物是**单一 `librenpython.so`**（`DT_NEEDED` 仅 = Android 系统库：`libGLESv2` / `libOpenSLES` /
> `libandroid` / `liblog` / `libc` / `libm` / `libdl`）。CPython + Ren'Py C 扩展 + pygame_sdl2 + SDL2 全部静态链入该库。
> 因此本仓只需 vendor 一个 35MB 的 `librenpython.so`，**无额外 .so 依赖**。

- **入口类名**：`org.renpy.android.PythonSDLActivity`（**注意不是** `org.kivy.android.PythonActivity`——
  论坛常见踩坑）。Python 侧经 pyjnius `autoclass('org.renpy.android.PythonSDLActivity').mActivity` 拿上下文。
- **游戏加载**：Ren'Py 支持从**外部存储**加载项目（`renpy.check_permission` / All-Files-Access），
  正契合本项目「导入游戏 → 内置引擎跑」模型，无需把游戏塞进 APK。
- **不需从源码编译 Python**：官方 RAPT（`renpy-8.5.3-rapt.zip`，65MB）+ SDK（155MB）能**构建一棵
  参考 APK，从里面摘出运行时**（native 库 + 引擎字节码 + `org.renpy.android` 类）vendor 进我们的 APK。

### 实施路径（分阶段）

1. **取参考运行时**：装 SDK + RAPT，构建一棵最小 Ren'Py Android APK（arm64），`unzip` 摘出
   `libpython*.so` / `librenpy.so` / `libSDL2*.so` / `assets/renpy/**` 与 Java 壳源码。
2. **移植 Java 壳**：把 `PythonSDLActivity` 移植成我们的 `com.winlator.galgame.engine.renpy.RenPyActivity`
   （继承同一 SDL `SDLActivity`），并把 `mActivity` 指向本壳。
3. **运行时打包策略**（体积 vs 复杂度）：把引擎字节码+资源放 APK `assets/renpy/`（或首启解压到内部目录），
   运行时 native 库进 `jniLibs/arm64-v8a/`。
4. **接进现有架构**：实现 `RenPyEngine extends BuiltinEngine`（探测/启动/游戏定位），在
   `BuiltinEngineRegistry.resolve()` 注册 `case RENPY`。
5. **许可证合规**：NOTICE + 随附 LGPL 副本 + 指向 renpy/renpy、renpy/renpy-build 源码链接（LGPL 组件：
   Pygame_sdl2 / chardet / FFmpeg(LGPL build) / Fribidi / libusb 等）。

### 体积与合规小结

- **体积**：运行时估 **40–70MB**（Python+SDL2+librenpy+引擎字节码）。叠加到现有 184MB APK → 约 **230–250MB**。
  可选「运行时随包 + 首启解压」或「assets 压缩」缓解。
- **合规**：MIT + 部分 LGPL，与本仓 LGPL-2.1 **兼容**。义务=附许可证副本 + 提供 LGPL 组件源码可得性。

### 4.4.1 运行时已落地（2026-10-04 续）✅

**重大发现**：RAPT 包（`renpy-8.5.3-rapt.zip`）的 `prototype/renpyandroid/src/main/jniLibs/arm64-v8a/librenpython.so`
是**预构建、可直接 vendored** 的 Android arm64 运行时——**不需要** renpy-build 本地编译 Python，也**不需要**
额外的 support-package 下载（renpy.org/dl 上的 `renpy-android-support.*.zip` 经核实为 404；运行时就在 RAPT 原型里）。
所以「构建参考 APK」这一步被大幅简化：运行时本体已就在手，真正需要的只是把它 + 引擎字节码 + Java 壳搬进本仓。

**已 vendored 到本仓（commit 见 git log，未 push）**：

| 类型 | 落点 | 体积 / 数量 |
| --- | --- | --- |
| 原生库 | `app/src/main/jniLibs/arm64-v8a/librenpython.so` | 35MB（自包含，仅依赖系统库） |
| 引擎字节码 | `app/src/main/assets/renpy-engine/renpy/**` | 16MB / 679 文件（`__pycache__` + `common/` + 15 子包：gl2/text/display/ui/sl2/…） |
| Java 壳 | `app/src/main/java/org/renpy/android/` `org/libsdl/app/` `org/jnius/` `org/kamranzafar/jtar/` | 53 个 `.java` |

**Java 壳处理**：
- **剥离 Google Play Asset Delivery + IAP**：`PythonSDLActivity` 原 `implements AssetPackStateUpdateListener`，
  依赖 `com.google.android.play.core.assetpacks.*` / `com.google.android.gms.tasks.*` 与 `Constants` / `StoreInterface`。
  这些仅在 `Constants.assetPacks` 非空时才激活；本仓直接把引擎/资源打进 APK、首启解压，无 Play 资源包、无 IAP，故整段移除。
  顺带删除 `Constants.java` / `StoreInterface.java`（删前已 grep 确认无其它引用）。**未引入任何新 gradle 依赖**。
- `RenPyFileProvider` 补 `import com.winlator.R;`（原壳 namespace = `org.renpy.android`，与本仓 `com.winlator` 不符，裸 `R` 解析不到）。
- `getLibraries()` 仍返回 `{"renpython"}` → 加载 `librenpython.so`；`nativeSetEnv` / `AssetExtract` / SDL 契约**原样保留**（JNI 契约不可改，与 Kirikiroid2 同理）。

**编译验证**：`compileDebugJavaWithJavac --offline` **BUILD SUCCESSFUL**（仅项目既有 deprecation 警告），
确认 53 个上游 Java 在本仓 `compileSdk 35` 下全部编过。

**体积实测**：35 + 16 = **51MB**（落在 40–70MB 预估区间内），叠加现有 184MB → 约 **235MB**，符合预期。

**下一步**：集成层（启动器 / Activity / 引擎注册 / manifest）与许可证合规已全部完成，见 **§4.5**；
余下仅真机运行期取证（§4.5.1）。

### 4.5 集成层已落地（2026-10-04 续）✅ 构建 + 离线布局验证通过

在 §4.4.1 运行时之上补齐「集成层」：启动器、Activity 宿主、引擎注册、manifest 注册。

#### 启动契约（逐条对源码核实，非猜测）

- `librenpython.so` 的 `SDL_main` → `start_python()` 固定执行 `getenv("ANDROID_PRIVATE")/main.py`，
  并 `chdir` 到该目录；`sys.path[0]` 与 `PYTHONHOME` 都指向它。
- `PythonSDLActivity.preparePython()` 已把 `ANDROID_PRIVATE` 设为 `getFilesDir()`，且 `renpy/__init__.py`
  以 `"ANDROID_PRIVATE" in os.environ` 判定 `renpy.android=True`（自动成立，无需额外代码）。
- 引擎须解包到 `getFilesDir()/renpy/`；`renpy.main.main()` 用 `renpy.config.renpy_base` 调
  `renpy.__main__.path_to_common()` → `renpy_base/renpy/common`。
- **SDL 版本无冲突**：`.so` 内实测为 **SDL2**（`Java_org_libsdl_app_SDLActivity`×29、`SDL_SetMainReady`、
  `SDL2_` 前缀），与仓内 vendored SDL2 `SDLActivity`（`getLibraries()` 返回 `{"renpython"}`）一致；
  SDL2 静态链入 `librenpython.so`，无需独立 `libSDL2.so`。
- **jnius 无需独立 so**：`librenpython.so` 内含 jnius（369 处引用），C 扩展为内建模块；磁盘上只需
  `jnius/`、`android/` 的 `.pyc`。`renpy/loader.py` 会 `import android.apk`，故 `apk.pyc` 必需。

#### 补齐的引擎缺失件（§4.4.1 提取遗漏）

| 缺失件 | 来源 | 原因 |
| --- | --- | --- |
| `renpy/__main__.py` | SDK 根 `renpy.py` | SDK 把 `renpy.__main__` 放在包外的根 `renpy.py`，原提取只取 `renpy/*` 子树而漏掉；缺它 `renpy.main.main()` 会 AttributeError |
| `lib/android/{__init__,apk}.pyc` | SDK `lib/python3.12/android/` | `renpy.android` 分支需 `import android` / `import android.apk` |
| `lib/jnius/{__init__,env,reflect,signatures}.pyc` | SDK `lib/python3.12/jnius/` | `from jnius import autoclass` |

`.pyc` 魔数 3531（CPython 3.12），与 `.so` 内嵌解释器 3.12.8 一致。

#### 新增 / 改动文件

| 文件 | 作用 |
| --- | --- |
| `assets/renpy-engine/main.py` | 启动器：读 `game_dir.txt` → 设 `sys.argv=[py, game_root, "run"]`（`renpy.arguments` 的 basedir 是**位置参数** nargs="?"）→ `renpy.bootstrap.bootstrap(renpy_base)` |
| `java/com/winlator/renpy/RenPyActivity.java` | 引擎宿主：版本化解包 `assets/renpy-engine/**`→`getFilesDir()`，写 `game_dir.txt`，复用父类 env 设置 |
| `java/com/winlator/galgame/engine/RenPyEngine.java` | `extends BuiltinEngine`：`isAvailable` 探 `librenpython.so`；`launch` 解析游戏根目录 + 启 Activity |
| `BuiltinEngineRegistry` | 注册 `case RENPY`（`ENGINES` 数组加 `RenPyEngine.INSTANCE`） |
| `AndroidManifest.xml` | 加 `com.winlator.renpy.RenPyActivity`(exported=false) + `RenPyFileProvider`(authority `com.winlator.fileprovider`) |

**放置决策**：`RenPyActivity` 放 `com.winlator.renpy`（**不在** `com.winlator.galgame`）——CI 的
`galgame-compile` 门禁只对 `com/winlator/galgame` 用桩编译，而本类依赖真实 Android/SDL 类，放进去会编不过。
`RenPyEngine` 只用桩安全 API（Context/Intent/ComponentName/File/Log/ApplicationInfo）且以字符串引用
Activity（同 `KrkrEngine` 风格），故留在门禁内。

**游戏根目录解析**（`RenPyEngine.resolveGameRoot`）：用户可能选项目根目录，也可能选 `game/` 内的 `.rpa/.rpy`。
后者向上取「名为 `game` 的目录」的父级作 basedir——使 `basedir/game` 天然存在，bootstrap 不会多建空 `game/`。

#### 验证结果

| 项 | 结果 |
| --- | --- |
| CI 桩门禁（`com/winlator/galgame` 对 `ci/stubs`，`-Xlint:all`） | ✅ rc=0 零警告 |
| `compileDebugJavaWithJavac --offline` | ✅ BUILD SUCCESSFUL |
| `assembleDebug --offline`（arm64） | ✅ BUILD SUCCESSFUL，APK **199MB** |
| APK 内容 | `lib/arm64-v8a/librenpython.so` + 305 个 `renpy-engine` 资源齐备 |
| 离线布局契约（用真实 APK 资源模拟解包，`.workbuddy/verify/verify_renpy_layout.py`） | ✅ `path_to_common`→`filesdir/renpy/common`；`path_to_gamedir`→`game_root/game`；`lib/android`+`lib/jnius`+`main.py`+`renpy/__main__.py` 齐备；launcher `_read_game_dir()` 正确解析 |
| 许可证合规（根 `NOTICE` §8 + 随 APK 分发 `assets/licenses/LICENSE_LGPL-2.1.txt` 与 `REN_PY_THIRD_PARTY.txt`） | ✅ Ren'Py MIT + LGPL 组件清单与源码链接（`renpy/renpy`、`renpy/renpy-build`、`renpy/pygame_sdl2`）已写入；LGPL-2.1 全文随 APK 附带 |

**真机运行期**：✅ 已完成（2026-10-04），取证过程与四个 root cause 见 **§4.6**。

### 4.6 真机运行期取证已通过（2026-10-04）✅ 真实游戏画面截图达成

按 §4.5.1 路线 A（临时 `exported=true` 直启 → 取证 → 收回 `exported=false`）执行。
测试游戏：`.workbuddy/verify/renpy_game/`（无 `.rpa` 极简项目，仅适用直启路线）。

#### 逐个 root cause（全部实证修复，按暴露顺序）

| # | 现象 | 根因 | 修复 | 实证 |
| --- | --- | --- | --- | --- |
| 1 | `SDL: Failed to register methods of org/libsdl/app/SDLActivity` + native crash | R8 `minifyEnabled true`（debug 亦生效）的 **shrinking** 删掉「未被 Java 引用」的方法，`JNI_OnLoad` 整表 `RegisterNatives` 失败（`-dontobfuscate` 只关改名不关裁剪） | `proguard-rules.pro` 补 keep：`org.libsdl.app`/`org.renpy.android`/`org.jnius`/`org.kamranzafar.jtar`/`com.winlator.renpy` 五包 + native 成员 | 重建后 `nativeSetupJNI()` 成功、C 侧 `preparePython` 回调通 |
| 2 | `files/main.py` 不存在/是空目录，整树只剩空目录 | **`AssetManager.list()` 对文件返回空数组而非 null**；以 `null` 判文件把顶层文件误当空目录 `mkdir` | `copyAssetTree` 改为「子项数>0 判目录；空数组先试按文件复制，失败再按空目录建」+ `ENGINE_VERSION` bump 强制重解包 | 设备 `files/main.py` 2679B 真实文件 |
| 3 | `SDL_main` ~4ms 静默退出、logcat **零 Python 输出** | **CPython 标准库缺失**：`PYTHONHOME`=`filesDir` 时解释器要 `filesDir/lib/python3.12/`；解包树里只有 `renpy/`、`lib/{android,jnius}`、`main.py` → `import os` 即 ImportError（stderr 未接 logcat 故无输出） | 从 `renpy-sdk.zip` 抽 `lib/python3.12/**`（833 个 `.pyc`）进 `assets/renpy-engine/lib/python3.12/`；黑名单只排 `android`/`jnius`（已在 `lib/` 顶层）与 Windows 版 `lib-dynload`；每个 `.pyc` 的 flags 由 1（hash-checked，需源文件）翻成 **2（hash-unchecked，免源校验）**——与真实 Ren'Py Android 产物一致 | Python 回溯开始出现在 logcat |
| 4 | `AttributeError: module '__main__' has no attribute 'path_to_gamedir'`（bootstrap.py:334） | **启动器契约缺失**：`renpy.bootstrap`/`renpy.main` 经 `renpy.__main__` 回调 6 个 `path_to_*` 函数；SDK 官方启动器 `renpy.py` 在 `main()` 里显式 `renpy.__main__ = sys.modules[__name__]`，我们的 main.py 既没定义函数也没绑定 | 重写 `assets/renpy-engine/main.py` 为官方启动器同构：定义 `path_to_gamedir`/`path_to_common`/`path_to_saves`/`path_to_logdir`/`predefined_searchpath`/`path_to_renpy_base` + bootstrap 前 `renpy.__main__ = sys.modules[__name__]` | bootstrap 推进到 `renpy.import_all()` |
| 5 | `ModuleNotFoundError: No module named 'zipfile._path'`（APK 里 457 缺 2） | **aapt2 默认 ignore 模式含 `<dir>_*`**，assets 里下划线开头的**目录**被剔除（下划线开头的**文件**不受影响）——`zipfile/_path/` 整目录进不了 APK | `app/build.gradle` 覆盖 `androidResources.ignoreAssetsPattern`（照抄默认项、仅去掉 `<dir>_*`，`// GalgameShell:` 标记） | APK stdlib 条目 455→457，import 通过 |
| 6 | `No module named 'ecdsa'`（savetoken.py:26）、`No module named 'renpy.test'`（`__init__.py:572`） | 抽取黑名单误伤 Ren'Py 自带依赖（ecdsa/requests 等在 SDK 的 `lib/python3.12/` 内）；引擎提取时漏 `renpy/test/` 全包（20 文件） | 黑名单收窄为 `android`/`jnius`/`lib-dynload` 三项；按 SDK↔本地全量对账补齐 `renpy/test/` | 依赖全部解析，进入 `renpy.main.main()` |

#### 最终证据链（round 9+）

- logcat（`python:I`，**零异常**）：`Opening APK ...`（p4a android 模块初始化）→
  `Interface start took 153 ms` → `Total time until interface ready: 1.10s` → `Hid presplash.`
- `topResumedActivity = com.winlator/.renpy.RenPyActivity`（前台）
- 截图 `.workbuddy/verify/renpy_final.png`：真实 Ren'Py 8.5.3 游戏画面——测试游戏
  `script.rpy` 首句对话 **"GalgameShell built-in Ren'Py engine is running."** 渲染于自绘
  `screen say` 对话框（另有 `renpy_shot5.png` 为引擎自带异常界面，同样证明 SDL 渲染链路通）。
- 版本角标 `1.0 / 8.5.3.26051504 / Android`（Ren'Py 渲染，证明字体/样式/文本布局链路全通）。

#### 测试游戏自身的两个坑（游戏侧，非引擎）

- `screen say` **必须含 `id "what"`（who 可选 `id "who"`）的 Text 控件**，否则
  `display_say` 报 `The say screen (or show_function) must return a Text object.`
- `padding 20` 简写在样式展开时按下标取 tuple → `TypeError: 'int' object is not subscriptable`；
  用显式 `left_padding`/`right_padding`/`top_padding`/`bottom_padding`。

#### 收尾状态

- manifest `RenPyActivity` 已收回 `exported="false"`，最终 APK 重建重装，主壳（GalgameMainActivity）冒烟正常。
- ~~已知待办：走游戏库路线（`RenPyEngine` 识别 `.rpa` → 自动 launch）的 E2E 未测~~ → **已通过，见 §4.7**。

### 4.7 游戏库路线 E2E 已通过（2026-10-05）✅ 生产路径闭环 + 两个集成 bug 实证修复

用户游戏库（`D:\need load` 80 个 rar，用 UnRAR 列表全量扫描）中 **无任何 Ren'Py 游戏**
（零 `.rpa`/`.rpyc`/`.rpy`），但发现 ≥8 个 Kirikiri 游戏（`data.xp3` 等，可供 Kirikiroid2 路线后续用样）。
故 E2E 用设备上已有的 `game/*.rpy` 标准布局测试游戏验证——识别、启动、渲染链路与 `.rpa` 完全同一套
（`detect → BuiltinEngineRegistry.resolve → RenPyEngine.launch → bootstrap`），差异仅在引擎内部资源加载（Ren'Py 本体行为，与集成层无关）。

#### 期间发现并修复的两个集成 bug（真机实证）

| # | 现象 | 根因 | 修复 | commit |
| --- | --- | --- | --- | --- |
| 1 | 标准布局 Ren'Py 游戏导入后 `import engine=UNKNOWN`，内置引擎永不选中 | `EngineDetector.detect()` 只扫游戏根目录**直接子项**且只认带 magic 的 `.rpa`；真实 Ren'Py 游戏 `.rpa/.rpyc` 都在 `game/` 子目录，根目录只有启动器 `.exe` → 一律判 UNKNOWN → `resolve(UNKNOWN)=null` → 走 JoiPlay/A 路由 | detect() 增 1b 段：根下 `game/` 含 `.rpy/.rpym/.rpyc/.rpa/.rpyb` → RENPY（判据与 `RenPyEngine.looksLikeRenPyGame` 对齐，置于 KiriKiri 字节级检查后不破坏优先级） | 57f2bca |
| 2 | 导入 Ren'Py 游戏**必崩主进程**：`FATAL EXCEPTION ... ProgressBar.setProgress on a null object reference` @ `GalgameHomeFragment.setProgressSmooth` | `ValueAnimator` 为局部变量无取消机制；小游戏导入快，`dismissProgressDialog()` 把 `progressBar` 置 null 后 450ms 补间窗口内余下帧回调直接 NPE。`GalgameLibraryActivity` 为同款复制代码 | animator 提升字段 + dismiss 前 cancel + 回调判空双保险，两文件同步修 | b650acd |

（bug 2 中游戏本体因独立 task 存活并正常渲染，崩的是主壳进程——返回时才会暴露。）

#### E2E 证据链（修复后回归，dc57ebe3）

1. 主壳游戏库点 FAB 导入 → 输入 `/sdcard/Android/data/com.winlator/files/Games/testrenpy` → 确定
2. `logcat`: `GalgameShell: import engine=RENPY route=B gameId=testrenpy`（修复前 UNKNOWN）
3. 内置引擎自动拉起：`SDL onCreate → nativeSetupJNI → Running main function SDL_main from .../librenpython.so`
4. `python: Interface start took 420 ms` → `Hid presplash.`
5. `topResumedActivity=com.winlator/.renpy.RenPyActivity`（前台）
6. 全程 `FATAL EXCEPTION` 计数 = **0**
7. 截图 `.workbuddy/verify/renpy_library_e2e_regression.png`：真实对话画面（与 §4.6 直启取证一致，横屏）

#### 附：导入路径的 UI 自动化脚本要点（复测用）

- 主壳首页自带 `FABImport`（id `com.winlator:id/FABImport`），无需导航；
- 导入对话框为 EditText 预填 `/sdcard/Download`：`input keyevent 67`×25 清空 → `input text <路径>`；
- 确定按钮 `android:id/button1` 在软键盘弹出后位于约 (1022,1114)，弹前在 (1022,1644)——**以 uiautomator dump 实时坐标为准**；
- `RenPyActivity`/`GalgameLibraryActivity` 均 `exported=false`（ColorOS retail 拒绝 shell 直启），须走主壳 UI。

#### 回归防护：EngineDetector 纯 JVM 验证（`tools/run_detect_test.sh`）

`detect()` 是不含 Android 依赖的纯 `java.io.File` 逻辑，可直接用 JDK 编译运行，**无需设备/模拟器/SDK**。
为防本次修复静默回归，建立 14 个用例（2026-10-05，全部通过，commit `b0b612b`）：

| 类别 | 用例 | 期望 |
| --- | --- | --- |
| 本次修复点 | 项目根 `game/` 下 `.rpy` / `.rpyc` / `.rpa` / `.rpym` / `.rpyb` | RENPY |
| 本次修复点 | 目录名大写 `GAME/`（忽略大小写） | RENPY |
| 不退化 | 根目录直接放 `.rpa`（RPA-3.0 / ARC-3.0 加密变体） | RENPY |
| **优先级守门** | 根目录 `data.xp3`(magic) 与 `game/*.rpy` **同时存在** | **KIRIKIRI**（不得被 1b 段抢占） |
| 负例 | 仅启动器 `.exe`、无 `game/` | UNKNOWN |
| 负例 | 有 `game/` 但无 Ren'Py 内容 | UNKNOWN |
| 负例 | `game` 是**文件**而非目录 | UNKNOWN |
| 负例 | `SiglusEngine.exe` | SIGLUS |

脚本每次从真实源码复制并重新编译（测的是当前代码而非快照），失败返回非零退出码。

#### 测试游戏成套脚本（`tools/renpy-testgame/`）

真机复验可直接使用，已修掉两个此前踩过的坑：

- `screens.rpy`：`say` 屏的 text **必须带 `id "who"` / `id "what"`**（否则 `display_say` 报
  `The say screen must return a Text object`）；`padding` **不能用简写**（样式展开时按下标取 tuple
  → `TypeError: 'int' object is not subscriptable`），须写显式 `left_padding` 等四边属性。
- `script.rpy`：用**显式 `image` 定义**而非自动命名——Ren'Py 自动 image 命名**不把下划线转成空格**
  （`bg_dusk.png` → 名 `bg_dusk` 而非 `bg dusk`），写 `scene bg dusk` 会落到 `rgb(170,170,170)` 灰色占位图。
- `make_art.py`：本机无 Pillow，手写 PNG 编码器生成黄昏背景与带 alpha 的立绘，
  用于验证引擎的图片解码/缩放/透明合成链路（2026-10-05 实测逐色吻合，见下表）。

#### 图片子系统已验证（排除集成缺陷）

给纯文本测试游戏补上图片素材后复测，立绘区域采样像素与生成素材**逐项精确吻合**：

| 画面位置 | 真实像素 | 素材对应项 |
| --- | --- | --- |
| 头部 | `(255,226,205)` | 脸部肤色 |
| 颈部 | `(252,216,196)` | 脖子肤色 |
| 胸前 | `(238,242,250)` | 衬衫白 |
| 身体 | `(44,56,106)` | 校服蓝 |

→ PNG 解码、缩放、**alpha 透明合成**全部正常，集成层无缺陷。纯黑占比 93.33% → 39.63%，颜色数 33 → 93。

#### 剩余待办（真机 dc57ebe3 需重新连线）

验证到一半真机掉线（adb devices 中消失），以下未完成：

1. 用显式 image 定义的 `script.rpy` 重跑，取「渐变背景 + 立绘 + 对话框」完整画面截图
   （届时第二步 TOP ACTIVITY 应为 `RenPyActivity`，且 `FATAL EXCEPTION` = 0）。
2. 背景此前显示为 `(170,170,170)` 占位灰，系上述 image 命名问题所致，修正后的脚本待推送。
3. 注：`app-debug.apk` 当前已是 **arm64-v8a** 默认产物（曾为模拟器出过 x86_64 UI 包，已重建覆盖回来）。

### 4.5.1 真机复测手册（设备 dc57ebe3 连上后）

1. 安装：`adb -s dc57ebe3 install -r app/build/outputs/apk/debug/app-debug.apk`
2. 导入测试游戏：把含 `game/` 的项目推到 `/sdcard/Android/data/com.winlator/files/Games/testrenpy/`。
3. 直启（`RenPyActivity` exported=false，需临时改 true 或走游戏库）：
   `adb -s dc57ebe3 shell am start -n com.winlator/com.winlator.renpy.RenPyActivity --es com.winlator.galgame.extra.RENPY_GAME_DIR /sdcard/Android/data/com.winlator/files/Games/testrenpy`
4. 观察：`adb -s dc57ebe3 logcat -s python:* RenPyActivity:* SDL:* `；首启会有一次引擎解包日志。
5. 截图：`adb -s dc57ebe3 exec-out screencap -p > shot.png`（Windows 目标用 `C:/...`）。

**关于 `exported`**：第 3 步的 `am start` 直启在本机（ColorOS retail）会被拒——shell 无权拉起
他包 non-exported Activity（Kirikiroid2 阶段已实证）。两条路：
- **A 直启取证（确定性高，推荐首测）**：临时把 manifest 里 `RenPyActivity` 的
  `android:exported="false"` 改成 `true`，`assembleDebug --offline` 重装，测完改回并重建。
- **B 走游戏库（免重建，但要过 UI 与引擎识别）**：把游戏导入游戏库并被 `EngineDetector`
  识别为 RENPY（靠 `.rpa` 魔数），再点开关；由 `GalgameLibraryActivity.showNativeRouteDialog`
  自动 `resolve→isAvailable→launch`，无需改 manifest。测试目录 `.workbuddy/verify/renpy_game/`
  是无 `.rpa` 的极简项目，**仅适用 A**；走 B 需另备带合法 `.rpa` 的游戏。

**测试游戏**（已就绪，gitignored）：`.workbuddy/verify/renpy_game/game/{options,screens,script}.rpy`
——自带 `say`/`main_menu` 屏（引擎不提供默认屏），`main_menu` 1.2s 后自动 `Start()`，便于直截对话画面。

## 5. 参考资料

| 内容 | 地址 |
| --- | --- |
| 上游本体 | https://github.com/zeas2/Kirikiroid2 |
| 安卓高版本适配分支 | https://github.com/YuriSizuku/Kirikiroid2Yuri |
| 去广告/瘦身二进制发行 | https://github.com/enaix/Kirikiroid2-debloated |
| Android 端 JNI 实现 | `src/core/environ/android/AndroidUtils.cpp` |
