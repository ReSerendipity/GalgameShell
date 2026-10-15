#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""直接依赖许可证扫描（v2.9 评估整改，对应《开源治理与合规_Flash报告》P1-2）。

扫描 gradle/libs.versions.toml 的 module 坐标，对照本仓台账（NOTICE §3/§8）的
坐标→许可证映射：
  - 许可证命中禁用清单（强 copyleft / SSPL）→ 非零退出，CI 阻断；
  - 坐标未在台账登记 → 警告「待评审」（提醒同步 NOTICE），不阻断；
  - 其余通过。
零第三方依赖（纯标准库，遵守「不引入报告未提及的工具」）；CI 于 rebase-guard job 执行。
台账同步约定：gradle/libs.versions.toml 新增依赖时，必须同步 NOTICE 与本文件 KNOWN 表。

用法： python3 scripts/check_licenses.py [path/to/libs.versions.toml]
退出码：0=通过（可有警告）；1=命中禁用许可证或扫描失败。
"""
import re
import sys

# 禁用许可证前缀（强 copyleft / 专有服务条款）：一旦出现即阻断。
# LGPL-2.1 不在禁用列——本仓自身即 LGPL-2.1（A6），同源合规（native-engine-integration.md §2）。
DENY_PREFIX = ("GPL-", "AGPL-", "SSPL-")

# 坐标 → 许可证。来源：NOTICE §3「上游随附的第三方组件」（单一事实源镜像，改动须双同步）。
KNOWN = {
    "androidx.appcompat:appcompat": "Apache-2.0",
    "androidx.preference:preference": "Apache-2.0",
    "androidx.lifecycle:lifecycle-process": "Apache-2.0",
    "com.google.android.material:material": "Apache-2.0",
    "com.github.luben:zstd-jni": "BSD-2-Clause",
    "org.tukaani:xz": "0BSD",
    "org.apache.commons:commons-compress": "Apache-2.0",
}


def main() -> int:
    path = sys.argv[1] if len(sys.argv) > 1 else "gradle/libs.versions.toml"
    try:
        with open(path, encoding="utf-8") as f:
            text = f.read()
    except OSError as e:
        print("[license-check] FAIL: 无法读取 %s: %s" % (path, e))
        return 1

    coords = sorted(set(re.findall(r'module\s*=\s*"([^"]+)"', text)))
    if not coords:
        print("[license-check] FAIL: 未从 %s 解析到任何 module 坐标" % path)
        return 1

    denied, unknown = [], []
    for coord in coords:
        lic = KNOWN.get(coord)
        if lic is None:
            unknown.append(coord)
        elif any(lic.startswith(p) for p in DENY_PREFIX):
            denied.append((coord, lic))

    print("[license-check] 扫描 %d 个直接依赖（来源 %s）" % (len(coords), path))
    for coord, lic in denied:
        print("[license-check] FAIL: %s 许可证 %s 命中禁用清单（强 copyleft/SSPL）" % (coord, lic))
    for coord in unknown:
        print("[license-check] WARN: %s 未登记许可证（待评审）：核对后同步 NOTICE 与 scripts/check_licenses.py" % coord)

    if denied:
        print("[license-check] 结论：阻断（禁用许可证 %d 个）" % len(denied))
        return 1
    print("[license-check] 结论：通过（待评审 %d 个，仅警告不阻断）" % len(unknown))
    return 0


if __name__ == "__main__":
    sys.exit(main())
