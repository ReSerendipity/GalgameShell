# Changelog

本文件记录 GalgameShell 的所有重要变更。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本 SemVer](https://semver.org/lang/zh-CN/)。
分类口径：`feat → Added`、`fix → Fixed`、`refactor → Changed`（见
[REPOSITORY_GUIDELINES.md §10.2](https://github.com/ReSerendipity/.github/blob/main/REPOSITORY_GUIDELINES.md)）。

> 说明：本仓发布说明（Release notes）当前由 `gh release create --generate-notes` 自动生成。
> 下条目仅回填可从 git 历史、tag / Release 与版本位溯源的客观事实，不做推测性归因。

## [Unreleased]

### Changed

- 依赖升级（Dependabot）：`androidx.appcompat:appcompat` 1.4.0 → 1.8.0（#2）；`com.google.android.material:material`（#3）；`org.apache.commons:commons-compress`（#6）；`org.tukaani:xz` 1.7 → 1.12（#9）；`com.github.luben:zstd-jni` 1.5.2-3 → 1.5.7-23（#8）。
- CI 依赖升级（Dependabot）：`actions/setup-java` 4 → 6（#4）；`actions/checkout` 4 → 7（#5）；`actions/upload-artifact` 4 → 7（#7）。
- 新增 `.mailmap`，统一贡献者邮箱显示（`5adcca4`）。
- 新增 `docs/release-governance.md`（§10 发布治理本地化），并在 Release 附 `SHA256SUMS.txt`（#10）。

## [11.2] - 2026-10-17

- 发布 tag `v11.2`（提交 `1e47bd0`）；对应版本位 `app/build.gradle`：`versionName = "11.2"`、`versionCode = 33`。
- 本版 Release 说明由 GitHub `--generate-notes` 自动生成，未包含人工维护条目；完整提交对比见
  [v11.2 对比 main](https://github.com/ReSerendipity/GalgameShell/compare/v11.2...main)。

[Unreleased]: https://github.com/ReSerendipity/GalgameShell/compare/v11.2...HEAD
[11.2]: https://github.com/ReSerendipity/GalgameShell/releases/tag/v11.2
