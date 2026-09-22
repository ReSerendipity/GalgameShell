# ci/ — CI 专用辅助文件（**不属于 Android 构建**）

## `stubs/`
给 CI 用的「离线类型门禁」桩代码：`com.winlator.galgame` 包引用了 Android SDK /
androidx / 官方 `com.winlator.*` 的部分 API，而本 fork 按设计**不含大二进制**、
CI 也不装 Android SDK。因此用一组最小签名桩顶替这些外部符号，让
`javac -Xlint:all` 能在秒级内抓出真正的编译错误（签名不符、不可达 catch、缺 import 等）。

> 这些桩**只在 CI 里编译**，位于 `app/src/main/java` 之外，Gradle 不会把它们打进 APK，
> 也不会与真实类冲突。

本地等价命令：

```bash
mkdir -p /tmp/stubs-out /tmp/check
javac -d /tmp/stubs-out $(find ci/stubs -name '*.java')
javac -Xlint:all -cp /tmp/stubs-out -d /tmp/check app/src/main/java/com/winlator/galgame/*.java
```

## 为什么不做完整 APK 构建
本 fork 是「框架-only」：`app/src/main/assets/**/*.tzst`（wine/box64 运行时）与
`app/src/main/jniLibs/**/*.so` 未随仓分发（体积/代理限制，且计划 §VIII 的口径是
「发框架、用户自取资源」）。要出完整 APK，需先按根目录 README 的说明补齐这些二进制，
再 `./gradlew assembleDebug`。CI 的 `galgame-compile` 保证**我们新增的代码**始终可编译。
