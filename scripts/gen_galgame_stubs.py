#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 ci/stubs 下的 R 桩（com.winlator.R 与 android.R）。

CI 门禁（.github/workflows/ci.yml 的 galgame-compile）只对 com.winlator.galgame 包做
javac 类型检查，没有 Android SDK/资源管道，因此 R 符号由本脚本扫描源码引用后
机械生成。改资源或新增界面后重跑：
    python scripts/gen_galgame_stubs.py
"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "java", "com", "winlator", "galgame")
OUT_APP = os.path.join(ROOT, "ci", "stubs", "com", "winlator", "R.java")
OUT_ANDROID = os.path.join(ROOT, "ci", "stubs", "android", "R.java")

REF_RE = re.compile(r"\b(R|android\.R)\.(layout|id|string|drawable|menu|anim|color|dimen|style)\.([A-Za-z0-9_]+)")

HEADER_APP = """package com.winlator;

/**
 * CI 桩：真实构建时由资源管道生成 R；此文件由 scripts/gen_galgame_stubs.py 自动生成，
 * 覆盖 com.winlator.galgame 包引用的全部符号。勿手改，改完资源后重跑生成脚本。
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
    for dirpath, _, files in os.walk(SRC):
        for name in files:
            if not name.endswith(".java"):
                continue
            path = os.path.join(dirpath, name)
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
