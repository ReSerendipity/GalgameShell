# GalgameShell — GalGame Android Shell

基于官方 [`brunodev85/winlator-app`](https://github.com/brunodev85/winlator-app)（LGPL-2.1）的 galgame 专用外壳：以 Winlator 为模拟层基座，自动识别游戏引擎，在「原生播放器 (B) / 模拟 (A)」之间智能路由，让 galgame 在 Android 上导入即玩。

## 功能亮点

- **导入即玩**：选定游戏目录后，自动创建专属容器、复制游戏文件、注入运行环境，导入完成即可从库中启动，无需手工配置 Wine 容器。
- **引擎识别与 A/B 双路由**：通过引擎指纹判断游戏引擎并自动选择运行方式——适合原生运行的游戏唤起原生播放器（开源原生引擎默认可用；闭源原生播放器默认关闭，需在设置中显式开启），其余游戏走 Winlator 模拟层；检测到加密仅作提示，不破解。
- **一键日文化**：自动注入日文区域环境（`LC_ALL` / `LANG` 环境变量与区域注册表）并配置日文字体，解决日文游戏乱码问题（字体文件不随应用捆绑）。
- **游戏内视频正常播放**：自动启用 DirectShow / Windows Media 解码，老游戏的开场动画与过场视频可正常播放。
- **存档重定向**：存档统一重定向到 S: 盘与 Shell Folder，便携存档自动「先搬后链」，并支持存档导出与恢复。
- **诊断向导与日志**：GPU、日文显示、视频解码、位宽、音频、加密逐项检查，配合日志查看（`logs.txt` / `stderr` / DXVK，自动提取要害行），启动失败时按向导逐项排查。
- **统一的游戏库界面**：导入、启动、诊断、日志、存档、原生路由开关集中在一处。

## 获取运行时资源

本仓库**按设计不附带大二进制运行时资源**：Wine / Box64 运行时（`app/src/main/assets/**/*.tzst`）与各架构原生库（`app/src/main/jniLibs/**/*.so`）不随源码仓分发（为控制仓库体积，运行时资源由使用者自行获取）。

因此 clone 之后**不能直接 `./gradlew assembleDebug` 出完整可用的 APK**，需先从上游补齐这部分资源：

```bash
# 从上游取回被排除的运行时资源与 native 库
git remote add upstream https://github.com/brunodev85/winlator-app   # 若尚未添加
git fetch upstream
git checkout upstream/main -- app/src/main/assets app/src/main/jniLibs
```

## 系统要求与免责声明

### 系统要求

- Android 11（API 30）或更高版本
- ARM64 设备，需支持 Vulkan 1.1+
- 存储空间预留 8–16 GB；建议 12 GB 及以上 RAM
- 不支持无 Vulkan 能力的设备（如 Kirin 平台）；Mali GPU 可走 Vortek / VirGL 兜底

### 免责声明

- 本应用仅为 galgame 运行外壳，**不含任何游戏本体，不提供破解**；请自备合法获得的游戏副本。
- 检测到加密的游戏仅作提示，不做破解。
- 第三方原生播放器为可选唤起功能，使用风险自负。
- 开源许可与源码获取方式见应用内「关于」及 [`NOTICE`](NOTICE)。

## 构建

1. 配置 Android SDK：在项目根目录 `local.properties` 中设置 `sdk.dir`。
2. 编译调试包：

   ```bash
   ./gradlew assembleDebug
   ```

## 许可证

LGPL-2.1（继承自 winlator-app）。本增量层同样以 LGPL-2.1 发布。
第三方组件与字体政策详见 [`NOTICE`](NOTICE)。
