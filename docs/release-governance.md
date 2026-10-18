# 发布 / 回滚 规范（GalgameShell）

> **来源与权威**：公共条款见 `ReSerendipity/.github` 的 `REPOSITORY_GUIDELINES.md` §10（发布 / 分发统一规范）。
> 本文件由 `ReSerendipity/.github` 的 `RELEASE_GOVERNANCE_TEMPLATE.md` 本地化而来，只填本仓特有内容，不重写、不抵触 §10 公共条款。
> 生成日期：2026-10-18（阶段 D-01）。未确定项一律标注「待定（负责人 + 触发条件）」，不留空。

---

## 0. 远端优先（根原则，不可改写）

发布链的构建、打包、扫描、上传、推送**全部在 GitHub Actions 远端完成**；本地只做三件事：改版本位、落 CHANGELOG、`git tag` + `git push`。本地不执行打包 / 镜像构建 / 发布上传。

本仓落地：构建与打包在 `ubuntu-latest` 远端 runner 完成（见 `.github/workflows/release.yml`）；本机不产出任何 Release 资产。

## 1. 版本号规范

- 遵循 **SemVer** `MAJOR.MINOR.PATCH`；预发布用 `-alpha.N` / `-beta.N` / `-rc.N`。
- **唯一权威版本位**：`app/build.gradle` 中 `android.defaultConfig.versionName`（当前 **`11.2`**）；配套整数位 `android.defaultConfig.versionCode`（当前 **`33`**）随 versionName 同步递增。
- 必须同步的其它版本位：**无**（仓内跟踪文件树中仅 `app/build.gradle` 一处声明版本；`.workbuddy/`、`.qoder/`、`build/` 等本地忽略目录不计入）。
- 版本位漂移校验：**待定（负责人：仓库所有者；触发条件：下一次发版前补一个版本一致性校验——或将 `versionName` 与 git tag 的比对挂入 release.yml 的 `validate-tag`，或新增类似 `tests/test_version_consistency.py` 的钉位测试）**。当前仓内无自动化钉位，漂一处不会自动判红。
- 自动化方式：**手动 tag（人工）**；仓内无 release-please。流程：本地改 `app/build.gradle` 的 `versionName` / `versionCode` → 打 tag → push。

## 2. CHANGELOG 管理

- 口径遵循 **Keep a Changelog**：`Added / Changed / Fixed / Deprecated / Removed / Security`；type 映射 `feat→Added`、`fix→Fixed`、`refactor→Changed`；发版时 `[Unreleased]` → `[VERSION] - YYYY-MM-DD`。
- **现状（2026-10-18 更新）**：根级 `CHANGELOG.md` 已建立（Keep a Changelog），`[Unreleased]` 随提交累积；`v11.2` 的 Release notes 仍由 `gh release create --generate-notes` 生成。
- **维护人**：仓库所有者；每次合并 `main` 在 `[Unreleased]` 追加条目，发版时改 `[VERSION] - YYYY-MM-DD`。

## 3. 发布流程（远端）

1. 确认发版条目完整（当前为自动 notes；后续见 §2 决策）。
2. 同步版本位：改 `app/build.gradle` 的 `versionName` / `versionCode`（见 §1 清单）。
3. 本地打 tag 并推送：`git tag v<X.Y.Z>` → `git push origin v<X.Y.Z>`。tag 须匹配 `^v[0-9]+(\.[0-9]+){1,2}$`，由远端 `validate-tag` 作业钉死（示例 `v11.2`）。
4. 远端凭 tag 触发 `.github/workflows/release.yml`：`validate-tag` → 复用 `ci.yml`（rebase-guard / 资产校验 / 许可证扫描 / galgame-compile / JVM 回归测试）→ `publish`（`gh release create --generate-notes`）→ `sbom`（syft 生成 CycloneDX 并上传资产）。
5. 远端挂资产：`sbom.cdx.json` + `SHA256SUMS.txt`。
6. 盯到终态：`gh run list` 确认绿；红了当场修或 revert 止损。

> **已知差异（本次未改，避免重构）**：§10.2 标准流程是「远端建 draft Release → 人工审核后 published」；本仓现状 `publish` 作业用 `gh release create` 直接发布正式 Release（非 draft）。是否改为 draft 审核制：待定（负责人：仓库所有者；触发条件：下次发布链评审时决策）。

## 4. 分发产物与完整性

| 产物 | 构建位置 | 标签 / 命名 | 完整性手段 |
|---|---|---|---|
| 源码 Release（GitHub 自动 Source code zip/tar.gz）+ `sbom.cdx.json`（CycloneDX，syft 生成） | **远端**（`ubuntu-latest`） | 不可变 semver tag `vX.Y.Z`；**禁 floating `latest`** | `SHA256SUMS.txt`（覆盖上传资产 `sbom.cdx.json`） |

- **本仓不构建 APK**：按设计，本 fork 不含 wine/box64 运行时 `.tzst` 与 `jniLibs` 大二进制，完整 APK 需本地按 README 补齐二进制后构建（见 `ci.yml` 头注）。APK 作为发布产物属后续事项：待定（负责人：仓库所有者；触发条件：补齐大二进制分发方案后，将远端 APK 构建与挂载补入本表）。
- **不使用容器镜像**：无 ghcr 推送、无 `docker/build-push-action`；§10.3/§10.4 的镜像 tag 小写化、Trivy `CRITICAL,HIGH` 阻断、`provenance/sbom` 镜像条款本仓暂不适用。
- **SBOM**：`sbom` 作业用 syft 生成 CycloneDX JSON（排除 `.git`），随 Release 挂载。
- **不使用 GPG 分离签名**（2026-10-18 裁决）：本仓历史上从无 `gpg-signed-release.yml`，符合裁决。
- **无 Tauri ed25519**：本仓为 Android/Gradle 项目，非 Tauri 桌面壳；`SHA256SUMS.txt` 仅用于防传输损坏。

## 5. 回滚

- **判定阈值**：待定（负责人：仓库所有者；触发条件：出现 Release 资产损坏 / 错误版本发布 / 安装包不可用时，由所有者判定并发起回滚）。
- **执行**：`git revert <bad-commit>`（保历史、可再 forward）；不可变产物按上一版 tag 重发。
- **破坏性动作**：重指向 / 删除 tag、移动任何远端 ref → **须仓库所有者单独授权**。`release.yml` 由 `push: tags` 触发，移动已发布 tag 会当场重跑发布链。
- **回滚后验证**：重跑 `ci.yml`（rebase-guard、JSON/XML 资产校验、`scripts/check_licenses.py`、`galgame-compile`、`tools/run_detect_test.sh`）至全绿。

## 6. SLA / 错误预算

- 本仓为源码分发型 Android 项目，**无运行中的远端服务 / 在线可用性承诺**。目标可用性：不适用；错误预算：不适用。
- 若未来引入托管服务：待定（负责人：仓库所有者；触发条件：上线首个面向用户的远端服务时补 SLA 目标与错误预算）。

## 7. 发布前检查清单

- [ ] `app/build.gradle` 的 `versionName` / `versionCode` 已同步（见 §1 清单）。
- [ ] tag 匹配 `^v[0-9]+(\.[0-9]+){1,2}$`（validate-tag 作业钉死）。
- [ ] `ci.yml` 全绿：rebase-guard（非 tag 事件）、JSON/XML 资产校验、`scripts/check_licenses.py`、`galgame-compile`（`javac -Xlint:all`）、`tools/run_detect_test.sh`。
- [ ] DCO 逐提交 `Signed-off-by:`（PR 事件由 ci.yml 校验）。
- [ ] 本仓非镜像类仓：Trivy 阻断、`provenance/sbom` 镜像条款不适用。
- [ ] **本地未产出任何发布交付物**（全部远端构建）。

---

## 本仓发布链校准记录（2026-10-18，阶段 D-01）

依据 §10 与 `check-release-standard.sh` 对 `.github/workflows/release.yml` 逐项核对，决定如下：

| 核对项 | 现状 | 决定 |
|---|---|---|
| tag 驱动（`push: tags: v*`） | 已对齐，`on.push.tags: ["v*"]`，`publish`/`sbom` 均 `if: startsWith(github.ref,'refs/tags/v')` | 不改 |
| Release 资产附 `SHA256SUMS.txt` | **缺失**：v11.2 仅挂 `sbom.cdx.json` | **最小修正**：在 `sbom` 作业新增生成 `SHA256SUMS.txt`（`sha256sum sbom.cdx.json`）并与 SBOM 一并 `gh release upload` |
| GPG 分离签名 | 本仓从无 `gpg-signed-release.yml` | 不改，符合 2026-10-18 裁决 |
| floating `latest` | workflows 中无 `value=latest`；GitHub Release 的 "Latest" 标记为 UI 指定，非 floating tag | 不改，符合 §10.3 |
| Trivy 报告模式 / 镜像链 | 无镜像构建 | 不适用 |
| draft → 人工审核 → published | 现状直接 `gh release create` 正式发布 | 本次按「最小修正、不重构」不改，登记为 §3 已知差异 |

> 说明：`check-release-standard.sh` 在本仓会因「缺 `CHANGELOG.md`」报 FAIL——该文件已随本单建立（见 §2）；其余 §10 机器可判项本仓均无 FAIL。