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
 *                            → stageGameFiles 做 A3 复制 + B4 S: 盘 + 写 galgame_overlay.json
 *   - GalgameSaveManager    B4 存档持久化（S: 盘 + 导出/恢复；Shell Folder/符号链接留 P4 接缝）
 *   - （B 路由唤起 / 诊断向导 后续 Phase 追加）
 */
package com.winlator.galgame;
