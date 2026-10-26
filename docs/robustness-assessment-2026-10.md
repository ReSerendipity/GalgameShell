# GalgameShell 健壮性完整性评估（2026-10-26）

> 触发：用户以普通用户身份真机复现「导入流程跑不完」——在 Download 解压
> 「椎名真穂のヒミツ ～ネームはHで思いつきます～」后手输路径导入，检测 UNKNOWN，
> 导入完成后启动报「导入文件缺失或不存在」。用户随后要求：① 导入不再手输路径、改用系统
> 文件夹选择器；② 对程序健壮性做一次完整性评估。本文是 ② 的结论，① 的落地见 §3。
>
> 方法：真机取证（Realme RMX5010 / Android 16 / `com.winlator` debug 包，adb + run-as +
> 截图回读）＋ JVM 回归（`tools/run_detect_test.sh`，EngineDetector 21 例 / LanguageDetector
> 15 例）＋ CI 类型门禁三步（`ci/stubs` → `com.winlator.galgame` 全树 → 热点入口清单，
> `-Xlint:all`）＋ 对 `com.winlator.galgame/**` 与 `com/winlator/Galgame*.java` 的只读审计
> （上游 Winlator 子树只记录不修改，零改铁律）。
> 严重度口径：P0 数据丢失/整条功能不可用/启动错游戏；P1 功能静默坏且用户无法自救；
> P2 体验困惑、泄漏、可维护性。

## 1 事故链（真机取证，逐环有证据）

| # | 环节 | 证据 | 处置 |
|---|---|---|---|
| D1 | 卸载重装后「所有文件访问」被系统重置：`isDirectory()` 仍真、`listFiles()` 返回 null，检测判 UNKNOWN、复制 0 文件，一路假成功到启动才报错 | 真机 `appops`/设置页状态；导入索引与容器取证见 D4 | §3 R3：step0 fail-fast 并直接带用户去系统授权页；SAF 路线根本不依赖该权限 |
| D2 | 解压产物是套娃目录（`Download/游戏名/游戏名/…`），检测与复制都落在外层 | 真机 `ls /sdcard/Download/椎名真穂…/` 只有一项同名子目录；容器 xuser-1 的 `drive_c/galgame/<id>/` 里也只有一层套娃目录、无 exe | §3 R2：`EngineDetector.resolveGameRoot`（EngineDetector.java:131）在 run/stage 两端归一，E15–E21 回归 |
| D3 | 库条目只有 gameId、没有 title/engine，卡片显示一串下划线 + 「未识别」 | 真机 `files/galgame_library.json`：`game_id` 与 `title` 同为 `______________H________`、无 engine 字段；截图 `h01_library_before.png` 卡片 01「未识别」 | §3 R4：`GalgameLibraryIndex.withIdentity`（:125）回填 + `markContainerized` 三参重载（:212） |
| D4 | CJK 目录名被 `[^A-Za-z0-9._-]→_` 塌成同形下划线串，两次导入得到**同名容器**；按名解析命中先建的坏容器，好容器被遮住，启动被 `isExecMissing` 误拒 | 真机 xuser-1 与 xuser-2 的 `.container` 里 `name` 完全相同（`galgame-______________H________`）；xuser-1 游戏目录只有套娃层、xuser-2 有 `ShiinaMahoNoHimitsu.exe` 等 5 项 | §3 R4+R5：`GalgameIds.gameId`（GalgameIds.java:24）剥离非 ASCII 时附 4 位哈希；容器名恒等 `galgame-<gameId>`（ImportFlow.java:211），避让上移到 id 派生（ImportFlow.java:330 同时收索引与容器名） |
| D5 | 导入失败不回滚：`stageGameFiles` 抛异常只 toast，已建容器留下成为孤儿，下次导入被避碰成 `-2`，库里多一张点不开的卡 | 同 D4 的 xuser-1 即坏导入残留 | §3 R6：wizard 的 catch 里连容器一起删（GalgameImportActivity.java:492） |

截图证据：`h01_library_before.png`（清理前：同一游戏两张卡，01「未识别」/02 UNITY）。

## 2 评估范围外的已知前提

- 上游 Winlator 子树（container/、core/、xserver/ 等）受零改铁律约束：`ContainerManager.loadContainers`
  对坏配置 `catch (JSONException) {}` 静默丢容器、`removeContainer` 为 private 等上游形态**只记录不改**；
  本 fork 的补偿做在 galgame 侧（R6 直接删容器根目录）。
- 运维事实（写进发布说明）：**卸载重装 / 清数据会重置全部文件权限**（含「所有文件访问」），
  ColorOS/Android 16 上 shell 无法代授（`appops set`、`pm grant` 全被 SecurityException 拒），
  只能用户在设置页手点；SAF 的 persistable 授权跟目录走、不随重装重置，这是本次改 SAF 的核心理由之一。

## 3 本轮已修（R1–R12）与验证状态

| # | 修复 | 位置 | 验证 |
|---|---|---|---|
| R1 | 导入改系统文件夹选择器（`ACTION_OPEN_DOCUMENT_TREE` + persistable 授权），删除手输路径框与快速路径按钮 | GalgameImportActivity.java:140；layout `activity_galgame_import.xml`（BTPickFolder/TVPicked） | 真机走完「选择器→Download→游戏目录→使用此文件夹」，回到向导 step1 |
| R2 | 套娃目录归一（深度 ≤4、遇游戏特征/运行时目录即停） | EngineDetector.java:131 | JVM E15–E21（21/0） |
| R3 | 权限重置 fail-fast：`listFiles()==null` 即提示并跳系统授权页 | GalgameImportActivity.java:309-313 | 真机（此前 D1 取证） |
| R4 | gameId 哈希后缀防塌缩 + 索引 title/engine 回填 | GalgameIds.java:24；GalgameLibraryIndex.java:125,212 | 门禁 + 待真机复验（见 §6） |
| R5 | 容器名恒等 `galgame-<gameId>`；占用集 = 索引 ∪ 现存容器名，避让在 id 派生处完成 | ImportFlow.java:211,330 | 门禁；待真机复验 |
| R6 | 导入失败回滚已建容器 | GalgameImportActivity.java:492 | 门禁；待真机复验 |
| R7 | 同名游戏重复导入先确认（避免「以为在修复」却多出副本卡） | GalgameImportActivity.java:352-366 | 门禁；待真机复验 |
| R8 | SAF 树 API 修正：树 URI 取 id 用 `getTreeDocumentId`、取自身元数据用 `buildDocumentUriUsingTree`（`getDocumentId` 遇 `/tree/…` 抛 IllegalArgumentException） | GalgameImportActivity.java:279-296；SafStager.java `documentIdOf` | **真机实测发现并修复**：修复前 TVPicked 显示整串 content:// URI、复制会在第一层失败 |
| R9 | 暂存治理：失败清半成品、6h 残骸清扫、重建后认回 stagedDir、可用空间 <128MB 硬失败（报剩余量） | SafStager.java:31,48,76；GalgameImportActivity.java:178-186 | 门禁；空间硬失败按「资源不足要硬失败」口径实现 |
| R10 | 导入完成但没定位到启动 exe 时当场告警（不再等启动才报「启动文件缺失」） | GalgameImportActivity.java:481-485,542 | 门禁；待真机复验 |
| R11 | 标题在主线程取一次（后台线程不碰 EditText）；SAF 流程不再用暂存目录名当游戏名；改选目录作废旧暂存与旧检测结果 | GalgameImportActivity.java:404-407,246-252 | 门禁 |
| R12 | 库页删除 254 行「手输路径 + 自跑一遍 ImportFlow」的重复实现，导入只留向导一条入口；随之删除 8 条死字符串 | GalgameLibraryActivity.java:393；galgame_strings.xml | 门禁三步全绿 |
| R13 | **真机复验发现**：卡片/详情标题仍取容器名（ASCII gameId）→ 显示一串下划线；改为优先取索引 title（`fromContainer` 新增 title 参数，库/首页/详情三处调用点一起改） | GalgameLibraryAdapter.java `fromContainer(..., title)` | 真机：卡片与「继续游玩」均显示 CJK 原名 |
| R14 | **真机复验发现**：确认页文件数按暂存根目录数（套娃场景恒为 1），改为按归一后的游戏根 | GalgameImportActivity.java `detect()` | 真机：同一目录由 1 变为 5，与容器内顶层条目一致 |
| R15 | **真机复验发现**：在确认页取消/退出会留下整份暂存副本（实测 750MB 级）；取消对话框、`fail()`、step0 退出、`onDestroy`（仅 `isFinishing()`，不误伤旋转重建）都清暂存，并作废 `source`（避免再按「开始导入」时拿已删目录误报「缺少所有文件访问权限」） | GalgameImportActivity.java `fail()`/`onDestroy()`/dup 取消 | 真机：取消后 `files/import-stage` 为空 |

## 4 仍开放（已登记 TD-18…TD-24，见 tech-debt-register.md）

| 编号 | 缺陷 | 触发 | 静默? |
|---|---|---|---|
| TD-18 | `GalgameDetailActivity.startForceContainer` 失败不回滚、成功不 `markContainerized` → 库中容器卡+原生卡重复 | 「改用 Wine 容器运行」转换 | 半静默（有 toast 无原因） |
| TD-19 | `GalgameNativeStore.store`「目标非空即返回」：半拷贝被当成品；`ImportFlow.copyDirectory` 对 `listFiles()==null` 静默返回 0 文件 | 磁盘满/进程被杀/导入中途权限被收 | 静默 |
| TD-20 | 「彻底删除」不删 `files/galgame-native/<id>` 收纳副本（数 GB）；`GalgameNativeStore.remove` 零调用；删除结果不校验 | 库中删除原生游戏 | 静默泄漏 |
| TD-21 | 索引非原子写（直接覆写）；读失败按空处理 → 下一次 upsert 用单条覆盖全文件 | 写一半被杀 / 文件损坏 | 静默丢条目 |
| TD-22 | 点卡片在主线程同步跑 `detectDeep`（深度 3、预算 4000 目录项 + 逐文件读魔数） | 大游戏目录 | ANR 风险 |
| TD-23 | 语言检测弱信号「文件夹名含任意汉字 → zh_CN」：日文标题几乎必含汉字 → 936 代码页乱码；`reapply` 还会把正确值「纠正」错 | 日文游戏导入 | 静默 |
| TD-24 | `GalgameSaveManager` gameId 空串时存档目录指向 SAVE_ROOT 根（跨游戏串档）；`symlinkPortableSaves` 先 delete 再 symlink、失败即存档目录消失 | gameId 为 ""（DetailActivity 可达） | 静默 |

P2 积压（不逐条编号，评估结论：接受现状、随相关改动顺带偿还）：
权限引导只弹一次且无再入口（GalgameStoragePermission.java:52-57）；`NativeRouteLauncher.prepareScanDir`
整拷无清理且 `listFiles()==null` 当成功（:245-266）；B 路由外部播放器对话框点「取消」后既不登记也不
fail，游戏已入库外副本但无条目（GalgameImportActivity.java:455-458）；`writeOverlay` 吞异常导致
启动落进 Wine 桌面（ImportFlow.java:561-565）；「继续游玩」未按 lastPlayedAt 排序
（GalgameHomeFragment.java:350-353）；四处线程池不 shutdown（Adapter:159 / Home:58 / Detail:59 /
Diagnostics:42）；refresh/onResume 主线程读索引与容器 JSON；`GalgameToolsFragment:87` 无参启动
ExternalControllerBindingsActivity 的 NPE 路径残留；`GalgameLogs` 与 SAVE_ROOT 硬编码
`/data/data/com.winlator`、tail 全文件载入 O(n²)；`GalgameLocaleInjector.reapply` gameId 空时扫错目录；
`GalgameCrashHandler.crashDir` 的 `getExternalFilesDir(null)` 可为 null。

## 5 已核对无误（不必复审）

SafStager 的 cursor 关闭/失败清半成品/空间预检/sweepStale 防误删；GalgameIds 传入 existingIds 时的
避碰循环；`WinlatorContainerEngine` 启动前 `isExecMissing` 拦截与降级日志；GameLauncher 的 override/
容器接管裁决（:104-119）；GalgameShortcutActivity 对 exported 入口的索引白名单（挡住 game_id 路径穿越）；
GalgameSettingsActivity 主题切换正确用 `commit()`（:43）；GalgameStoragePermission.openSettings 双层回退。

## 6 真机复验结果（2026-10-26 晚，RMX5010 / Android 16）

| # | 判据 | 结果 |
|---|---|---|
| 1 | 向导 step1 标签显示 CJK 目录名（非 content:// URI） | **通过**：`椎名真穂のヒミツ ～ネームはHで思いつきます～` |
| 2 | step2 显示 UNITY、文件数 = 内层目录条目数（5） | **通过**（R14 修复后：1 → 5） |
| 3 | 容器名 == `galgame-<下划线串>-<4位哈希>`；游戏目录顶层有 exe；索引 title/engine 正确 | **通过**：`galgame-______________H________-6b0d`；顶层 `ShiinaMahoNoHimitsu.exe`/`UnityPlayer.dll`/`_Data`；索引 title=CJK、engine=UNITY。暂存目录已清 |
| 4 | 库中仅一张卡；点卡启动到游戏并可推进对白 ≥60s | **通过**：单卡（INDEX·1）→ 详情 → 启动 → logo → 18 禁警告页 → 标题菜单 → 正片；连续推进对白 100s+（约 8 条），游戏内菜单/存档栏正常。该拷贝是机翻中文版，故 `language=zh_CN.UTF-8` 判定正确（TD-23 未牵涉本例） |
| 5 | 再导一次同目录：弹「库中已有同名游戏」确认；取消零副作用 | **通过**：弹窗文案/按钮正确；取消后无新容器、索引仍 1 条、暂存为空（R15 修复前这里实测残留 750MB 暂存） |
| 6 | 人为制造 stageGameFiles 失败：确认无孤儿容器 | **通过**：把小号测试目录里的一个文件 `chmod 000` 后导入，后台 0.2s 轮询记录到 `xuser-2` 于 16:15:26.512 出现、16:15:26.956 消失（回滚删除），最终无孤儿、暂存已清 |

### 6.1 复验期间观察（不属导入链缺陷，记录备查）

- **标题菜单点击有约 70px 竖直偏移**：点视觉上的「終了/EXIT」会激活上方约 70px 的那一行（实测点 EXIT 打开了 CONFIG 的页；按 +70px 补正后「初めから」一次点中）。与 TD-14（KiriKiri 窗口 (152,152) 偏移）同属「guest 命中测试与窗口位置不一致」家族，成因在游戏/窗口层，不在导入/启动链。Adreno 830 + 该 Unity 标题实测，换标题未必复现。
- **首次启动耗时**：logo 停在画面约 4 分钟后才响应点击（期间 `ShiinaMahoNoHimitsu.exe` 持续 260% CPU，非死锁），属 box64 首次翻译 + DXVK 着色器编译的一次性成本。
- 复验用的临时目录 `/sdcard/Download/galgame_failtest` 已删除。
