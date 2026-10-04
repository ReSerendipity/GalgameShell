# 原生引擎内嵌方案（Native Engine Embedding）

> 目标：把「识别到某引擎 → 用该引擎的原生运行时直接跑」这条链路做进 **GalgameShell 自身 APK**，
> 而不是再安装一个第三方播放器 APK（用户明确否决：独立 APK 的方式不要）。
>
> 状态：**Phase 1 二进制级内嵌已完成并通过真机启动验证；游戏画面卡死（加载页后无推进）待设备重连后现场取证**。本文只记录已核实事实，猜测一律标注。

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

**下一步（尚未做）**：
1. 实现 `com.winlator.galgame.engine.renpy.RenPyActivity`（继承 `org.renpy.android.PythonSDLActivity`，`mActivity` 指向本壳），
   改写 `preparePython`/游戏定位逻辑，从**外部存储**加载 Ren'Py 项目（`assets/renpy-engine/renpy` 首启解压到内部目录并加进 sys.path）。
2. 实现 `RenPyEngine extends BuiltinEngine`（探测/启动/游戏定位），在 `BuiltinEngineRegistry.resolve()` 注册 `case RENPY`。
3. manifest 加 `RenPyActivity` + `RenPyFileProvider`（`authorities=com.winlator.fileprovider`，注意与 Winlator 既有 FileProvider 权威名冲突风险，需核对）。
4. 真机验证：导入 Ren'Py 游戏 → 内置引擎跑 → 截图确认画面（用户硬要求）。
5. 许可证合规：NOTICE + 随附 LGPL 副本 + 指向 renpy/renpy、renpy/renpy-build 源码链接。

## 5. 参考资料

| 内容 | 地址 |
| --- | --- |
| 上游本体 | https://github.com/zeas2/Kirikiroid2 |
| 安卓高版本适配分支 | https://github.com/YuriSizuku/Kirikiroid2Yuri |
| 去广告/瘦身二进制发行 | https://github.com/enaix/Kirikiroid2-debloated |
| Android 端 JNI 实现 | `src/core/environ/android/AndroidUtils.cpp` |
