# 原生引擎内嵌方案（Native Engine Embedding）

> 目标：把「识别到某引擎 → 用该引擎的原生运行时直接跑」这条链路做进 **GalgameShell 自身 APK**，
> 而不是再安装一个第三方播放器 APK（用户明确否决：独立 APK 的方式不要）。
>
> 状态：**调研完成 / 集成进行中**。本文只记录已核实事实，猜测一律标注。

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

## 5. 参考资料

| 内容 | 地址 |
| --- | --- |
| 上游本体 | https://github.com/zeas2/Kirikiroid2 |
| 安卓高版本适配分支 | https://github.com/YuriSizuku/Kirikiroid2Yuri |
| 去广告/瘦身二进制发行 | https://github.com/enaix/Kirikiroid2-debloated |
| Android 端 JNI 实现 | `src/core/environ/android/AndroidUtils.cpp` |
