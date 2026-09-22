/**
 * com.winlator.galgame — GalgameShell 外壳专属包（Plan Part I §I.5 / P0）。
 *
 * 设计铁律（Plan §VIII.4 rebase 硬化）：本包内所有代码为「新增价值层」，
 * 对官方 winlator-app 前端零侵入 —— 绝不修改
 *   - Container.java（含 DEFAULT_DRIVES / DEFAULT_ENV_VARS / DEFAULT_WINCOMPONENTS）
 *   - LocaleHelper.supportedLocales
 *   - res/values/strings.xml 现有条目
 * 所有 galgame 文案走 res/values/galgame_strings.xml（AC-19）。
 *
 * 模块：
 *   - EngineDetector        B1 引擎指纹（magic bytes 级）+ A/B 路由决策 + A5 加密检测
 *   - EnginePreset         A-route 引擎预设（engine_presets.json 单条目解析）
 *   - ImportFlow            P1 导入即玩：detect → 构造 Container data JSONObject
 *                           （envVars / drives 含 S: / wincomponents / dxwrapper / box64Preset
 *                            + extraData 承载 galgame 专属项）→ UI 调 ContainerManager 创建
 *                            → stageGameFiles 做 A3 复制 + B4 S: 盘 + P2 日文化 + 写 galgame_overlay.json
 *   - GalgameSaveManager    B4 存档持久化（S: 盘 + 导出/恢复；Shell Folder/符号链接留 P4 接缝）
 *   - GalgameLocaleInjector P2 日文化注入（env LC_ALL/LANG + 区域注册表 + 日文字体；
 *                           仅调用官方 WineRegistryEditor/WineUtils 公开 API）
 *   - GalgameFonts          P2 日文字体定位（drop-in 目录 / 游戏目录；框架不分发字体）
 *   - GalgameVideoSupport   P3 视频解码（强制 directshow=1/wmdecoder=1，OP/ED 不黑屏）
 *   - NativeRouteLauncher   P5 B 路由唤起（Tier-1 开源默认 / Tier-2 闭源需显式开启+免责）
 *   - GalgameDiagnostics    诊断向导（GPU 驱动 / 日文 / 视频 / 位宽 / 音频 / 加密 检查表）
 *   - GalgameLogs           日志查看（定位 logs.txt / stderr / dxvk 等 + 要害行提取）
 *   - （库 UI / 导入流 UI / 诊断 UI 见 com.winlator.galgame.ui，后续 Phase 追加）
 */
package com.winlator.galgame;
