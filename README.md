# GalgameShell — GalGame Android Shell

基于官方 [`brunodev85/winlator-app`](https://github.com/brunodev85/winlator-app)（`main`，LGPL-2.1）的 galgame 专用外壳。
路线 1：以 Winlator 为模拟层基座，做「引擎识别 + 原生(B)/模拟(A) 智能路由」的装修层。

> 完整计划见 `Desktop/galgame-plan-complete.md`（Plan Part I–VIII，2026-09-20 收口）。

## 当前状态（P0 脚手架）

✅ 已完成（不依赖上游即可产出）：

- `app/src/main/assets/engine_presets.json` — A 路由可信默认（Part IV §IV.6 定稿）
- `app/src/main/res/values/galgame_strings.xml` — 外壳文案，全新文件不碰官方 strings.xml（AC-19）
- `app/src/main/java/com/winlator/galgame/` — 新增专属包骨架
  - `EngineDetector.java` — B1 引擎指纹 + A/B 路由（magic bytes 占位，待 P1 字节级）
  - `GalgameSaveManager.java` — B4 存档 S: 盘逻辑
  - `ImportFlow.java` — P1 导入即玩编排（A2 每游戏容器 + A3 复制）
- `.gitignore` / `.github/workflows/ci.yml`（零改铁律校验 + 构建占位）

⏳ 待补齐：官方 `winlator-app` 源码（见下方「拉取上游」）。本仓库目前是 **galgame 增量层**，
尚未包含可构建的 Android 工程；Java 桩类需上游 `app/` 模块与 Android SDK 才能编译。

## 拉取上游（网络恢复后执行）

> 注：本机沙箱环境对 GitHub 大包（git packfile）传输返回 502 / TLS 吊销失败，无法自动 clone。
> 在可直连 GitHub 的网络下运行：

```bash
# 方案 A：干净 fork 工作流（推荐，保证 rebase 硬化 C3 可用）
git clone --depth 1 --branch main https://github.com/brunodev85/winlator-app _upstream_tmp
# 把上游 _upstream_tmp/app 等内容并入本仓库，再提交本仓库的 galgame 增量层
# （或：直接在本仓库 git remote add origin <你的fork> 后 rebase）

# 方案 B：直接把本仓库转为 winlator-app fork
git remote add origin <你的 GitHub fork URL>
git fetch upstream
git rebase upstream/main        # 冲突应仅 3–4 文件小 diff
git push --force-with-lease origin galgame
```

## rebase 运维流（C3，Plan §VIII.4）

```bash
git remote add upstream https://github.com/brunodev85/winlator-app
git fetch upstream
git rebase upstream/main
```

**零改铁律**：冲突面锁 3–4 文件，以下文件绝不直接改：

- `Container.java`（`DEFAULT_DRIVES` / `DEFAULT_ENV_VARS` / `DEFAULT_WINCOMPONENTS`）
- `LocaleHelper.supportedLocales`
- `res/values/strings.xml` 现有条目

所有 galgame 价值放新包 `com.winlator.galgame` + 新文件 `galgame_strings.xml`。

## 决策锁定（A1–A6）

- A1 字体：仅开源 CJK（IPAex Gothic+Mincho，OFL 备选 Noto），禁商业字体
- A2 容器：每游戏独立容器
- A3 导入：默认复制（可写），非引用挂载
- A4 B 路由：默认唤起不内嵌；Tier-1 开源默认 / Tier-2 闭源显式开启+免责
- A5 加密：仅检测+提示，不破解
- A6 许可：LGPL-2.1，fork 公开 + LICENSE/NOTICE + 应用内源码获取

## 阶段路线图（P0–P7）

P0 脚手架 → P1 导入即玩 → (P2 日文化, P3 视频解码 并行) → P4 存档 → P5 B 路由 → P6 真机 → P7 上线

## 许可证

LGPL-2.1（继承自 winlator-app）。本增量层同样以 LGPL-2.1 发布；字体许可文件随包。
