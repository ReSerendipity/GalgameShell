# 组件版本登记表（Component Version Registry）

> 定位：三个引擎/基座组件「内嵌版本 vs 上游最新稳定版」的**单一事实源**。
> 三个组件的升级路径完全不同：前两条是**换预编译产物**（与仓库内其他代码零冲突），
> 第三条是**按需逐笔移植**（冲突面见 [`ci/zero-mod-exceptions.md`](../ci/zero-mod-exceptions.md)）。
>
> 核查节奏：每次发版（CHANGELOG 版本号变更）前核查一次；结果更新本表「最近核查」列
> 并在 CHANGELOG 记一笔；升级操作单独开批次提交。

## 登记表

| 组件 | 进入本仓的形态 | 内嵌版本 | 上游仓库 | 上游最新稳定版 | 最近核查 | 升级路径 |
|---|---|---|---|---|---|---|
| Ren'Py 原生引擎 | 预编译产物 `jniLibs/arm64-v8a/librenpython.so` + `assets/renpy/**`（引擎源码不在仓库） | **8.5.3**（RAPT 构建：CPython 3.12.8 + Ren'Py 8.5.3 + pygame_sdl2/SDL2 静态合并为单一 so） | [renpy/renpy](https://github.com/renpy/renpy)（构建系统 [renpy/renpy-build](https://github.com/renpy/renpy-build)） | **8.5.3**（2026-05-15 "We Can Go to the Moon"）＝内嵌版，无需动作；master 活跃（nightly） | 2026-10-25 | 官方 RAPT 重跑构建，`librenpython.so` + `assets/renpy/**` **一起换**（`__pycache__/*.cpython-312.pyc` 必须与 so 的 CPython 同源）；Java 宿主依赖的 JNI 契约（`PythonSDLActivity` 接口、`start_python()` 固定执行 `getFilesDir()/main.py`）上游多年未变，大版本变更时才需动壳 |
| Kirikiroid2 | 预编译产物 `jniLibs/arm64-v8a/libgame.so`（+libffmpeg）+ 改写自上游的 `org/tvp/kirikiri2/KR2Activity.java`（唯一有上游血缘的 Java 文件，BSD-3） | master 快照（上游不给发行 tag，无法对齐版本号） | [zeas2/Kirikiroid2](https://github.com/zeas2/Kirikiroid2) | **无发行节奏**：master 最后推送 **2024-06-05**，最新 tag 1.3.9，HEAD d1c2b12 | 2026-10-25 | 上游已停更 ~16 个月，**无同步对象**（本仓实际自维护这条线）；若换新版 so，对照其 JNI 表核对 KR2Activity 的 native 声明——类名被 so 硬编码（`org/tvp/kirikiri2/KR2Activity`），不可改 |
| Winlator 源组件 | **源码级内嵌**（决策 A8），本仓直接改上游文件 | 嵌入点 **a030f55**（2026-09-14，versionCode 33）；上游基线 **8077f37**（2026-10-16） | [brunodev85/winlator-app](https://github.com/brunodev85/winlator-app) | **无 tag/release**（作者站外分发，以 main HEAD 计）：8077f37 | 2026-10-25（`git ls-remote` 复核：自 10-17 起 8 天无新提交） | **按需逐笔移植**，当前落后 5 笔：vsync 默认关（4f539f0）、native_resolution（34df94f，strings 冲突已预登记）、外接鼠标焦点修复（a544910）、local ref 泄漏修复（8077f37）。移植前核对 `ci/zero-mod-exceptions.md` 冲突面清单 |

容器运行时附属件（随源组件/资产分发，不单独立跟踪）：**Wine 10.10**（rootfs 内，随 Winlator
rootfs 更新）、**box64 0.4.5**（本仓自建 `assets/box64/box64-*.tzst`，版本真源是
`core/DefaultVersion.java` 的 `BOX64` 常量，属零改例外登记文件，见
`ci/zero-mod-exceptions.md` 受守卫路径表）。

## 核查方法（复验命令）

```bash
# Winlator：对比登记的 HEAD（8077f37）
git ls-remote https://github.com/brunodev85/winlator-app HEAD

# Kirikiroid2：tag 列表 + HEAD（tag 排序取最新）
git ls-remote --tags --refs https://github.com/zeas2/Kirikiroid2 | tail -5
git ls-remote https://github.com/zeas2/Kirikiroid2 HEAD

# Ren'Py：仓库 tags 不标发行版，稳定版以官网为准
#   https://www.renpy.org/latest.html   （最新稳定版）
#   https://www.renpy.org/release_list.html （全历史）
# 仓库活跃度（当日有无提交）：
#   https://github.com/renpy/renpy/commits/master.atom
```

## 登记要求

1. **发版前必查**：更新「最近核查」列；若任一组件落后于最新稳定版，在 CHANGELOG
   `Changed/Upgraded` 里明确写「落后于 X，本版未跟进」或跟进的动作。
2. **升级即换件**（前两条路线）：替换产物后必须真机跑一轮
   [`real-device-verification.md`](real-device-verification.md) 对应引擎的冒烟用例。
3. **不追 nightly**：只跟官方稳定版；Winlator 组件只按需移植（修复型提交优先，
   功能型提交评估后再定）。
