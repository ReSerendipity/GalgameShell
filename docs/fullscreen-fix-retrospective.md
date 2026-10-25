# 全屏问题修复复盘：经验与教训（2026-10-25）

> 对象：「Wine 路线游戏画面只占屏幕一小块 / 无法全屏」的定位与修复全程
> （PR #36，含通知落地页修复）。本文目的不是记流水账，而是把**可复用的判断**
> 与**踩过的坑**沉淀下来，供后续轮次（含 UX 改造、启动链排查）直接引用。

## 1. 一句话结论

根因是两层叠加：galgame 直启路线从未启用上游窗口铺满机制（forceWindowsFullscreen
唯一入口在 shortcut），而 Wine 在"不重定父的极简 WM"下会给顶层窗口**自绘标题栏**，
整窗带装饰位（实测 `_MOTIF_WM_HINTS deco=58`），即便开了开关也进不了
`FullscreenTransformation`。修复 = 默认启用开关 + 注册表 `X11 Driver → Decorated=N`
去装饰 + `inBounds` 上界 `< → <=`，真机 5 用例占屏 12%–45% → 79%–90%。

## 2. 弯路与教训

### 2.1 方向反了的修法：虚拟屏尺寸是反向杠杆

前轮（2026-10-24，TD-14）把"画面小"往"虚拟屏不够大"方向修：KIRIKIRI 预设
1920x1080 + 手动分辨率档位。实际数据证明**窗口小于虚拟屏时，虚拟屏越大画面越小**
（caste 在 1920 容器 31.8% vs siglus 在 720 容器 45.2%）。

> **教训**：渲染链是「游戏窗口 → 虚拟屏 → 物理屏」两级缩放，任何"画面大小"问题
> 必须先搞清病灶在哪一级。虚拟屏尺寸只解决"窗口**大于**虚拟屏被裁"（TD-14 本体），
> 对"窗口**小于**虚拟屏"是加剧。

### 2.2 开关开了 ≠ 机制生效：要有窗口级证据

本轮第一版修复（只启用 forceWindowsFullscreen）真机复测**完全无变化**。如果当时
只看"代码逻辑对"就提交，会产出一次无效修复。靠在 `collectRenderableWindows` 加
临时日志拿到窗口属性（`deco=58 noDeco=false`），才发现装饰位把窗口挡在变换之外，
补上 `Decorated=N` 才真正生效。

> **教训**：机制类修复必须取"机制真的走到"的证据（本例：窗口属性日志；更早的
> §7.2 是消息级 trace）。**开关状态与效果差都不是证据**。临时日志用完即删，
> 不入库。

### 2.3 测试脚本自己会骗人：每轮先验前台 Activity

首轮 5 个用例全部"100% 覆盖"——其实是 Git Bash 的 MSYS 路径转换把 `/data/...`
改写成 `Files/Git/data/...`，`am start` 报 Activity 不存在，截的全是**桌面启动器**。
若直接拿截图亮度分析下结论，会把"没测到"当"测过了"。

> **教训**：① Git Bash 下 adb 传 unix 路径必须 `export MSYS_NO_PATHCONV=1`；
> ② 每个用例先 `dumpsys activity | grep topResumedActivity` 确认被测 Activity
> 真在前台，再解读截图；③ 管道后 `$?` 是 tail 的退出码，判断成败要看命令本身
> （README 已有此条，本轮再犯一次，可见"文档写了"不等于"每次都会做"——
> 脚本里要写死，不靠人记）。

### 2.4 裸 bbox 会被边缘杂散像素骗：用密度法

截图四边常有杂散亮像素（打孔屏、光标、拖影），裸"非黑像素包围盒"会把 2247 宽的
letterbox 测成 2507、把左黑边测成 0。按列/行**亮度密度阈值**（>0.5% 采样点）取
连续块，才能得到真实内容矩形。

### 2.5 "点了没反应"≠"输入坏了"：事件级取证再定罪

铺满变换打开后，点击画面无推进。若据此断言"坐标映射坏了"，会去改正确的代码。
`WINEDEBUG=+event` 显示 `MotionNotify / ButtonPress / ButtonRelease` 完整到达游戏
窗口——输入链是好的，只是估的落点不在可交互元素上。这与 TD-12 的教训（"点不动"
实为验收脚本估点打偏）**同型再现**。

> **教训**：输入问题的定罪顺序 = 事件到达游戏（X11/消息级）→ 坐标数值对不对 →
> 才轮到怀疑变换代码。"没反应"永远排最后。

## 3. 机理沉淀（防止下次误判）

1. **装饰位挡门**：上游 `FullscreenTransformation` 只接手 `hasNoDecorations()`
   的窗口；本 app 的 X server 是不重定父的极简 WM，Wine 自绘标题栏 → 整窗带
   MWM 装饰位 → 变换静默跳过。`Decorated=N` 已进注入链（`GalgameLocaleInjector`），
   **旧容器必须「重新注入运行环境」补齐**，否则铺满不生效。
2. **DIALOG 类窗口不参与铺满**（设计如此）：SoftDenchi DRM 对话框保持原生尺寸
   居中——合理，不要当缺陷修。
3. **16:9 内容在 19.5:9 物理屏上的等比上限 ≈ 78%**（两侧各 ~266px 黑边是保比例
   的代价）；要 100% 填充只有抽屉「切换全屏」的拉伸模式（变形），不默认开。
4. **通知是最后一个 WINLATOR 页入口**：前台服务通知非会话态点击曾落在上游
   `MainActivity`；已统一到 `GalgameMainActivity`。抽屉「退出」回库正常（实测）。
   以后再报"看到 WINLATOR 页"，先查还有没有新的 `MainActivity` 启动点。

## 4. 对上游同步的影响面（本轮实际改动）

| 文件 | 性质 | 登记 |
|---|---|---|
| `com.winlator.galgame/*`（Helper/Injector）、`GalgameDetailActivity`、布局/strings | fork 自有 | 无需 |
| `XServerDisplayActivity`（1 处接线）、`ForegroundService`/`NotificationUtils`（通知落地页） | 上游文件、单点钩子 | zero-mod-exceptions「守卫外」表 |
| `GLRenderer.java`（`inBounds` 边界 1 处） | 上游文件、语义改动 | 同上，已登记 |

> **纪律（延续既有模式）**：新逻辑一律进 `com.winlator.galgame`（或新包），上游
> 文件只加**单点接线**并登记；rebase 冲突面就保持在"少量人工合并"级别。本轮
> PR #36 rebase-guard / galgame-compile / gitleaks 三项检查全绿，证明该模式可持。

## 5. 遗留与下一步（按可改性分类）

| 项 | 类别 | 可否在本 fork 解决 |
|---|---|---|
| TD-09（YU-RIS/SystemNNN 黑屏） | Wine 内建 quartz 跨线程死锁（rootfs 预编译） | **基本只能等上游**（或自建 rootfs，成本已评估为高、暂不做）；应用层只能把黑屏变明确报错 |
| AdvHD / BGI 启动即退 | 本 fork 启动链/环境（icu.dll 前置阻塞等线索） | **可以**，下一轮排查（BGI 已从"未定"更新为"确认启动失败"） |
| TD-13 键盘事件到不了 Wine 客户程序 | xserver 未实现焦点事件 | 部分可以（app 侧补 FocusIn/FocusOut 投递，属上游包改动需登记） |
| TD-15 KiriKiri 每次弹分辨率对话框 | 引擎持久化行为 | UX 层可绕（自动选择/模拟点击，见下） |
| 启动页 IPAA 警告、年龄确认等重复点击 | 游戏侧固定流程 | 同上 |

**UX 改造候选（模拟点击等）**：技术路径 = 在 XServerDisplayActivity 既有的
`onMapWindow` 监听里按窗口类名/尺寸识别目标（分辨率对话框、警告页），经
`xServer.inputDeviceManager` / winhandler 合成输入或直接 `WM_CLOSE`/默认按钮。
全部可用现有公开对象实现，放 galgame 包零改上游，与窗口铺满同一模式。
