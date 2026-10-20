# 引擎等价化 + 前端 UI 个性化 —— 落地记录（2026-10-05）

> 来源：`DOCS/plan-engine-equivalence-ui.md`（前置简报）。本文件是对**实际落地结果**的记录：
> 做了什么、和简报哪里不一样、验证到什么程度、真机复测怎么跑。
> 读法建议：先看 §1 勘误，再看 §2 提交清单，§5 是真机复测手册。

---

## 1. 简报勘误（两处事实修正，都已按修正后的口径实施）

### 1.1 §2.6「已导入的 Krkr/RenPy 从库里启动会走 wine」——前提不成立

代码取证（2026-10-05 快照）：

| 位置 | 事实 |
| --- | --- |
| `ImportFlow.run()` L71-78 | `Route.B` 直接 `return`，`containerData = null` |
| `GalgameHomeFragment.startImport` / `GalgameLibraryActivity.startImport` | `Route.B` → `showNativeRouteDialog()` 后 **return，不建容器** |
| `EngineDetector` L24-25 | `KIRIKIRI(Route.B)`、`RENPY(Route.B)` |
| 两处 `refresh()` | 游戏库唯一数据源 = 名字以 `galgame-` 开头的 `Container` |
| `ImportFlow.buildContainerData` L199 | `galgame_engine` 只由 **A 路由**写入 |

⇒ Krkr/Ren'Py **根本不会进入游戏库**（没有容器就没有条目），所以「入库后误走 wine」这条路不存在。
按简报字面实施阶段 B1（按容器 extra 的 engine 分流）会是一次**零行为变化**的重构。

### 1.2 真正的两个缺口（本轮的改造对象）

1. **原生游戏没有库入口**：Route.B 导入即拉起、退出即失忆，没有封面/历史，下次要重新手打路径。
   用户真实库里 Kirikiri 游戏 ≥8 个、Ren'Py 0 个（见 `native-engine-integration.md` §4.7）。
2. **误分流**：`EngineDetector.detect()` 只扫**根目录直接子项**，`.xp3`/`.rpa` 藏在子目录时判 UNKNOWN
   → 走 A 路由被复制进 wine 容器、用 wine 跑。这才是「该原生却走了 wine」的真实链路，而它
   **无法靠信任导入时登记的字符串修复**（存的就是 UNKNOWN）。

---

## 2. 落地清单（5 个提交，均未 push）

| commit | 范围 | 要点 |
| --- | --- | --- |
| `fcc0c7d` | 阶段 A 抽象 | `GameEngine` / `GameLaunchRequest` / `WinlatorContainerEngine` / `GameEngineRegistry`；`BuiltinEngine implements GameEngine`。纯新增，零行为变化 |
| `4b3358a` | 索引 | `GalgameLibraryIndex`（filesDir/galgame_library.json）；`ci/stubs` 补 `JSONObject.optLong`、`JSONArray.optJSONObject/put` |
| `5c885b6` | 阶段 B 分发 | `GameLauncher` 统一裁决；游戏库双数据源（容器 + 索引）；长按菜单「切换运行方式」；适配器 `Item` 化 |
| `00e806c` | C1 + C2 | 品牌 token 化（去 22 处硬编码）+ 主题继承链修复；`tools/gen_brand_icons.py` 重制图标；双档闪屏 |
| `da9cbb3` | C3 + C4 | 导航图标自有矢量 + chip pill；容器屏改名「容器管理」并降级末位 |

---

## 3. 三条启动路线的最终形态

| 策略 | 实现 | 是否一等 `GameEngine` |
| --- | --- | --- |
| 内置原生 | `KrkrEngine` / `RenPyEngine`（`BuiltinEngine implements GameEngine`） | ✅ |
| 容器 / Wine | `WinlatorContainerEngine`（与内置引擎等价，可参与选路） | ✅ |
| 外部 APK | `NativeRouteLauncher`（照旧，**不纳入**抽象——第三方 App 我方只有 Intent，谈可用性/许可证无意义） | ❌ 有意保留 |

`EngineDetector.Route` 自此**只作检测优先级注释**，不再是分发开关（分发全走 `GameEngineRegistry`）。

---

## 4. 启动裁决规则（`GameLauncher.launch`）

优先级从高到低：

1. **用户 override**（`GalgameLibraryIndex.preferredEngine`，长按菜单「切换运行方式」写入）；
2. **启动前重检测**：对真正落盘的游戏目录再跑 `EngineDetector.detect()`；命中非 UNKNOWN 且该内置引擎
   可用 → 升级为原生。**只升级不降级**（用户可能出于兼容性就是要用 wine 跑）；
3. **登记值回落**：容器 extra / overlay 的 `galgame_engine`；
4. **容器兜底**：有容器就用容器，没有才报错（`该游戏当前没有可用的 Winlator 容器，请用「导入游戏」重新导入`）。

降级原则：选中的引擎不可用时，**有容器就退回容器**，否则把 `unavailableReason` 抛给 UI 展示。

---

## 5. 数据落点

`getFilesDir()/galgame_library.json`（卸载即清，与 `KrkrEngine` 写的 `.preference/recentpath.xml` 同层）：

```json
{ "version": 1,
  "games": [ { "game_id": "...", "title": "...", "engine": "KIRIKIRI",
               "source_path": "/sdcard/...", "preferred_engine": null,
               "containerized": false, "added_at": 0, "last_played_at": 0 } ] }
```

- 稀疏索引：读不到条目 = 走自动裁决，不报错；文件缺失/损坏/字段异常一律降级为空。
- 写入点：内置引擎成功拉起 → `putNative`；A 路由容器创建成功 → `markContainerized`；启动成功 → `touchPlayedAsync`。

---

## 6. 资源与主题契约（C1/C2/C4）

新增品牌 token（`attrs.xml`，亮/暗两档均在 `styles.xml` 给值）：

| token | 亮档 | 深档 | 用途 |
| --- | --- | --- | --- |
| `galgameBrandStart` / `galgameBrandEnd` | `#FF6A5AE0` / `#FF9B5DE8` | `#FF32305A` / `#FF4A3F7A` | 库顶栏渐变 |
| `galgameChipBackground` / `galgameChipText` | `#FFEAF0FF` / `#FF5A6ACF` | `#FF2B2A44` / `#FFA9B4FF` | 引擎 chip |
| `colorOnAccentSubtle` | `#D9FFFFFF` | `#B3F2F3F7` | 主色上的次要字 |
| `galgameCoverPlaceholder` | `#FFEDEFF2` | `#FF2A2938` | 封面占位底 |

**必须知道的继承链**：游戏库 Activity 走的是 `GalgameLibraryTheme`（**不是** `GalgameMainTheme`）。
布局已全面改用 `?attr`，主题缺 token 会在 inflate 时抛 `Resources.NotFoundException`。
为此抽了基座 `GalgameBrandTokensLight`，`GalgameLibraryTheme`（`values/` 与 `values-v29/` 两处）
都挂在它上面。

其他新增资源：

- `drawable/galgame_splash.xml` / `galgame_splash_dark.xml` + `values/galgame_colors.xml`
  （冷启动闪屏，接入两档主题的 `android:windowBackground`；刻意用字面量色——上游
  `AppThemeBase` 的 `windowBackground` 引用主题属性实测解析失败、窗口退化为纯黑）
- `drawable/ic_galgame_library.xml`、`ic_galgame_containers.xml`（底导自有矢量）
- `tools/gen_brand_icons.py`：本机无 Pillow，纯标准库（有符号距离场 + 4×4 超采样 + 手写 PNG 编码）
  重绘「窗口 + 酒杯」母题，生成 `mipmap-*/ic_launcher{,_round,_foreground}.png` 与
  `drawable/icon_notification.png`。改图标重跑即可；原图均为 git 跟踪文件，可 `git checkout -- <path>` 回退。
- 新文案一律进 `values/galgame_strings.xml` 或新增资源文件，**绝不改 `values/strings.xml`**（CI rebase-guard 红线）。

---

## 7. 验证状态

已通过（本机可复现）：

| 项 | 命令 | 结果 |
| --- | --- | --- |
| CI 等价类型门禁 | `javac` 编译 `ci/stubs` → `javac -Xlint:all -cp <stubs> $(find app/src/main/java/com/winlator/galgame -name '*.java')` | 通过，无告警 |
| 真实 Java 编译 | `gradle-8.14.3/bin/gradle compileDebugJavaWithJavac --offline` | 通过（仅存量 deprecation） |
| 资源链接 | `gradle ... processDebugResources --offline` | 通过，无新增告警 |
| 完整打包 | `gradle ... assembleDebug --offline` | BUILD SUCCESSFUL（`app/build/outputs/apk/debug/app-debug.apk` ≈209MB） |
| 图标视觉 | 模型直接读生成的 PNG 核对 | 「窗口 + 酒杯」母题成立，白字形/紫底盘正确 |
| 通知图标可读性 | `.workbuddy/verify/analyze_png.py` 统计字形覆盖率 | 实心酒杯剪影 39.3%（此前镂空版 10.5%，24dp 下只剩轮廓+洞），24px 预览可辨认 |

**未完成：真机回归**。`adb devices` 当前为空（`dc57ebe3` 未连接），阶段 D 的三条路径回归待设备接入。

需要真机确认的三个点（本机无法验证）：

1. shape drawable 里的 `?attr` 渐变（`galgame_header_bg.xml`）在运行时是否正常解析；
2. 闪屏作为 `windowBackground` 与后续内容布局叠加是否无残留；
3. 原生游戏条目（无容器）的封面：`GalgameCoverProvider.coverFor(dir, null)` 会扫源目录图片，
   命中随机游戏 CG 属预期，未命中则走品牌占位图。

---

## 8. 真机复测手册（设备接上后）

```bash
export MSYS_NO_PATHCONV=1
cd /c/Users/Doro/GalgameShell
# 1) 安装
adb -s dc57ebe3 install -r app/build/outputs/apk/debug/app-debug.apk

# 2) 三条路径回归
#    a. Kirikiri（Route.B，原生）→ 导入后应直接进 KR2Activity，且游戏库出现该条目
#    b. Ren'Py（Route.B，原生）→ tools/renpy-testgame 或设备上已有测试游戏
#    c. Siglus（Route.A，容器）→ 库内启动应仍走 XServerDisplayActivity，不得退化
adb -s dc57ebe3 shell am start -n com.winlator/com.winlator.GalgameMainActivity
adb -s dc57ebe3 logcat -d | grep -E "GameLauncher|GalgameShell: import"

# 3) 取证 + 客观像素分析（模型不"看"图，用统计替代主观描述）
adb -s dc57ebe3 shell screencap -p /sdcard/ui.png
adb -s dc57ebe3 pull /sdcard/ui.png C:/Users/Doro/Desktop/GalgameShell验证截图/
python .workbuddy/verify/analyze_png.py C:/Users/Doro/Desktop/GalgameShell验证截图/ui.png
```

验收判据：

- 库内点击 Kirikiri 条目 → logcat 出现 `GameLauncher: launch via krkr`，前台为 `org.tvp.kirikiri2.KR2Activity`；
- 库内点击容器游戏 → `GameLauncher: launch via winlator-container`，前台为 `XServerDisplayActivity`；
- 覆盖 `EngineDetector` 误判场景：把 xp3 放进子目录再导入，应仍能在启动时被重检测救回原生路线
  （logcat 出现 `重检测升级：登记=UNKNOWN 实测=KIRIKIRI`）；
- 亮/暗两档主题下截图色板正确（顶栏渐变、chip、卡片、封面占位均随主题切换）。

---

## 9. 已知取舍

- **外部播放器不纳入 `GameEngine`**：保持 `NativeRouteLauncher` 现状（已拍板）。若日后要统一，
  需要先解决「第三方 App 无运行时，可用性只能靠 `PackageManager` 探测」的语义问题。
- **索引与容器是两套真值**：容器游戏以容器为准，索引只承担 override 与原生条目；
  删除容器不会自动清索引条目（`containerized=true` 的条目不会重复出现在列表，影响可控）。
- **`GalgameLibraryActivity` 与 `GalgameHomeFragment` 仍是两份平行实现**（历史遗留），
  本轮已把条目构造与启动裁决抽到 `GalgameLibraryAdapter.fromContainer/fromIndexEntry` 与
  `GameLauncher`，UI 侧只剩接线。彻底合并成一个 Fragment 不在本轮范围。
