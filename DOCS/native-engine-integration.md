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
- ⏸ **未拿到游戏画面**：加载页 CPU 持续 20–27% 在忙（**不是死锁**），但 10+ 分钟画面一帧不变，
  也未生成任何缓存目录。换 `patch.xp3`（535 KB，无过滤器）复现同样现象 → **与包体积/解密量无关**，
  卡在初始化之后的阶段。原版 APK 对照未能完成（它停在“最近路径”列表，cocos 自定义按钮的坐标换算导致
  `input tap` 命中不了播放键）。
- 备注：`/sdcard/Games/bosei2` 里 `data.bin`（97 MB）头 4 字节就是 `XP3\r\n`，是改名的 XP3；
  该游戏自带 `xp3filter.tjs`（逐字节 XOR 过滤器）。**曾在真机复制出 `data.xp3`（97 MB 副本），需清理**。

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

## 5. 参考资料

| 内容 | 地址 |
| --- | --- |
| 上游本体 | https://github.com/zeas2/Kirikiroid2 |
| 安卓高版本适配分支 | https://github.com/YuriSizuku/Kirikiroid2Yuri |
| 去广告/瘦身二进制发行 | https://github.com/enaix/Kirikiroid2-debloated |
| Android 端 JNI 实现 | `src/core/environ/android/AndroidUtils.cpp` |
