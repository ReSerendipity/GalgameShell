#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 ci/stubs 下的 R 桩（com.winlator.R 与 android.R）。

CI 门禁（.github/workflows/ci.yml 的 galgame-compile）对 com.winlator.galgame 全树
＋热点入口清单做 javac 类型检查，没有 Android SDK/资源管道，因此 R 符号由本脚本
扫描源码引用后机械生成。扫描范围与门禁编译范围保持一致（SRC 子树 + HOT 清单）；
改资源或新增界面后重跑：
    python scripts/gen_galgame_stubs.py
"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java", "com", "winlator", "galgame")
# 热点入口清单唯一来源：ci/hotspot-entries.txt（与 .github/workflows/ci.yml、ci/pre-push 同一份）。
# 扫描排除：GalgameHost.java 与热点清单在同一步 javac 中编译，其 R 引用已由 SRC 步覆盖，
# 故不重复扫描。排除名单是否仍然有效，由 ci/check-hotspot-sync.sh 核对。
HOTSPOT_LIST = os.path.join(ROOT, "ci", "hotspot-entries.txt")
HOT_SCAN_EXCLUDE = {"GalgameHost.java"}


def load_hotspot_entries(exclude=HOT_SCAN_EXCLUDE):
    entries = []
    with open(HOTSPOT_LIST, encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if os.path.basename(line) in exclude:
                continue
            entries.append(os.path.join(ROOT, *line.split("/")))
    if not entries:
        raise SystemExit("[gen_galgame_stubs] %s 没有可扫描的热点条目" % HOTSPOT_LIST)
    return entries


HOT = load_hotspot_entries()
OUT_APP = os.path.join(ROOT, "ci", "stubs", "com", "winlator", "R.java")
OUT_ANDROID = os.path.join(ROOT, "ci", "stubs", "android", "R.java")

REF_RE = re.compile(r"\b(R|android\.R)\.(layout|id|string|drawable|menu|anim|color|dimen|style|mipmap)\.([A-Za-z0-9_]+)")

HEADER_APP = """package com.winlator;

/**
 * CI 桩：真实构建时由资源管道生成 R；此文件由 scripts/gen_galgame_stubs.py 自动生成，
 * 覆盖 com.winlator.galgame 包与热点入口清单引用的全部符号。勿手改，改完资源后重跑生成脚本。
 */
public final class R {
"""

HEADER_ANDROID = """package android;

/**
 * CI 桩：android.R 的最小子集，由 scripts/gen_galgame_stubs.py 自动生成。勿手改。
 */
public final class R {
"""


def scan():
    refs = {"app": {}, "android": {}}
    paths = []
    for dirpath, _, files in os.walk(SRC):
        for name in files:
            if name.endswith(".java"):
                paths.append(os.path.join(dirpath, name))
    paths.extend(HOT)
    for path in paths:
        with open(path, encoding="utf-8", errors="replace") as f:
            text = f.read()
        for prefix, kind, sym in REF_RE.findall(text):
            bucket = "android" if prefix == "android.R" else "app"
            refs[bucket].setdefault(kind, set()).add(sym)
    return refs


def render(header, refs):
    out = [header]
    for kind in sorted(refs):
        out.append("    public static final class %s {\n" % kind)
        for i, sym in enumerate(sorted(refs[kind]), start=1):
            out.append("        public static final int %s = %d;\n" % (sym, i))
        out.append("    }\n")
    out.append("}\n")
    return "".join(out)


def main():
    refs = scan()
    with open(OUT_APP, "w", encoding="utf-8", newline="\n") as f:
        f.write(render(HEADER_APP, refs["app"]))
    with open(OUT_ANDROID, "w", encoding="utf-8", newline="\n") as f:
        f.write(render(HEADER_ANDROID, refs["android"]))
    for label, bucket in (("com.winlator.R", refs["app"]), ("android.R", refs["android"])):
        total = sum(len(v) for v in bucket.values())
        kinds = ", ".join("%s=%d" % (k, len(v)) for k, v in sorted(bucket.items()))
        print("generated %-14s %d symbols (%s)" % (label, total, kinds or "none"))


if __name__ == "__main__":
    main()
