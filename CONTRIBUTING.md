# 贡献指南（CONTRIBUTING）

## 快速开始

1. 克隆后先读 `README.md`（对外说明）与 `AGENTS.md`（门禁清单与零改铁律，最高优先级约定）。
2. 本仓按设计不含大二进制：clone 后不能直接 `./gradlew assembleDebug` 出完整 APK
   （按 README「获取运行时资源」补齐）；CI 门禁在无 SDK / 无二进制条件下可跑。

## 本地门禁（提交 / 推送前）

| 检查 | 命令 | 说明 |
|---|---|---|
| 类型门禁 | `sh ci/pre-push`（或按 `AGENTS.md` 的本地等价命令） | 与 CI 同口径，秒级 |
| JVM 回归测试 | `bash tools/run_detect_test.sh` | EngineDetector + LanguageDetector 双套件 |
| 许可证扫描 | `python3 scripts/check_licenses.py` | 强 copyleft（GPL/AGPL/SSPL）即阻断 |
| 密钥扫描 | pre-commit 已托管（gitleaks） | 配置见 `gitleaks.toml` |

## DCO 签署（必需）

本仓采用 DCO（Developer Certificate of Origin）：**所有提交必须带
`Signed-off-by: 姓名 <邮箱>`**——用 `git commit -s` 自动追加。
CI 在 `pull_request` 上逐提交校验（ci.yml rebase-guard job），缺签署的提交会被拦下。

缘由：v2.9 评估《开源治理与合规_Flash报告》P1-3（签署率 3/30 且无校验机制）。

## 提交约定

约定式提交（feat / fix / test / chore / docs / ci），scope 用模块名（如 mobile / deps / release）。

## 许可证

LGPL-2.1（见 `LICENSE` / `NOTICE`）。引入新依赖前跑 `python3 scripts/check_licenses.py`；
新依赖必须同步 `NOTICE` 与 `scripts/check_licenses.py` 的 KNOWN 表（双同步约定）。
上游回馈候选见 `UPSTREAM-PR-CHECKLIST.md`。
