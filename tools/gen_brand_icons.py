#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 GalgameShell 品牌图标位图（无需 Pillow，纯标准库光栅化 + PNG 编码）。

为什么需要它：
- 上游 Winlator 的 `mipmap-*/ic_launcher.png`（蓝色窗口图标）与 `drawable/icon_notification.png`
  仍留在仓库里；虽 minSdk 26 起实际走 `mipmap-anydpi-v26/ic_launcher.xml`（自适应图标，
  前景已是自有 `galgame_launcher_foreground`），但这些位图会被「关于」对话框等直接引用，
  也是仓库里最后的 Winlator 图标辨识残留。
- 本机无 Pillow，故用 zlib + struct 手写 PNG 编码，用有符号距离场做 4×4 超采样光栅化，
  把 `galgame_launcher_foreground.xml` 的「窗口 + 酒杯」母题按同一套坐标（108 空间）重绘。

用法：
    python tools/gen_brand_icons.py            # 覆盖写出全部位图
    python tools/gen_brand_icons.py --check    # 只校验目标文件是否已存在

产物（原图均为 git 跟踪文件，`git checkout -- <path>` 即可回退）：
    app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png
    app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_round.png
    app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png
    app/src/main/res/drawable/icon_notification.png
"""
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

BRAND = (0x6A, 0x5A, 0xE0)          # 品牌紫（与 values/ic_launcher_background.xml 一致）
WHITE = (0xFF, 0xFF, 0xFF)

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

GLYPH_SPAN = 0.68                   # 字形占图标边长比例（留出安全边距）
ROUND_SPAN = 0.66

# ---- 几何基元（以 108 空间描述，与 galgame_launcher_foreground.xml 的 pathData 同尺度）----


def rrect(x0, y0, x1, y1, r):
    def inside(x, y):
        dx = max(x0 + r - x, x - (x1 - r), 0.0)
        dy = max(y0 + r - y, y - (y1 - r), 0.0)
        return dx * dx + dy * dy <= r * r
    return inside


def rect(x0, y0, x1, y1):
    def inside(x, y):
        return x0 <= x <= x1 and y0 <= y <= y1
    return inside


def half_ellipse(cx, cy, rx, ry):
    def inside(x, y):
        if y < cy:
            return False
        return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0
    return inside


def circle(cx, cy, r):
    def inside(x, y):
        return (x - cx) ** 2 + (y - cy) ** 2 <= r * r
    return inside


def union(*fs):
    return lambda x, y: any(f(x, y) for f in fs)


def subtract(a, b):
    return lambda x, y: a(x, y) and not b(x, y)


# 「窗口 + 酒杯」：外框挖出标题栏横条，下方高脚杯（杯身、杯梗、杯座）
WINDOW = rrect(37, 32, 71, 76, 7)
TITLE_BAR = rect(36, 38.5, 72, 42.5)
BOWL = union(rect(43, 47.5, 65, 51.5), half_ellipse(54, 51.5, 11, 9))
STEM = rect(52.5, 51.5, 55.5, 69)
FOOT = rrect(47.5, 69, 60.5, 72.8, 1.9)

# 原矢量用 evenOdd 填充：「酒杯」是窗口里的**镂空**（紫底透出），而非叠加另一个白形。
# 这里照同一意图实现：白窗口挖掉标题栏横条，再挖掉酒杯。
CUP_REGION = union(BOWL, STEM, FOOT)

GLYPH = subtract(subtract(WINDOW, TITLE_BAR), CUP_REGION)
# 通知栏图标要小到 24dp 也认得出，去掉细横条（1px 级别会被降采样成噪点）
GLYPH_SIMPLE = subtract(WINDOW, CUP_REGION)


# ---- 光栅化 ----


def render(size, glyph, fg, bg_shape, bg_color, span, ss=4):
    """把 108 空间的 glyph 映射到 size×size 画布中心（占 span 比例）。"""
    box = size * span
    off = (size - box) / 2.0
    scale = box / 108.0
    step = 1.0 / ss
    rows = []
    for py in range(size):
        row = bytearray()
        for px in range(size):
            fg_hits = 0
            bg_hits = 0
            for sy in range(ss):
                y = py + (sy + 0.5) * step
                gy = (y - off) / scale
                for sx in range(ss):
                    x = px + (sx + 0.5) * step
                    gx = (x - off) / scale
                    if glyph(gx, gy):
                        fg_hits += 1
                    if bg_shape(x / size * 108.0, y / size * 108.0):
                        bg_hits += 1
            total = ss * ss
            a_fg = fg_hits / total
            if fg_hits:
                r, g, b = fg
                a = 255
            elif bg_hits:
                r, g, b = bg_color
                a = 255
            else:
                r, g, b, a = 0, 0, 0, 0
            if bg_hits and fg_hits and fg_hits < total:
                # 边缘：按覆盖率混合前景与底色，避免锯齿发白
                mix = a_fg
                r = int(fg[0] * mix + bg_color[0] * (1 - mix))
                g = int(fg[1] * mix + bg_color[1] * (1 - mix))
                b = int(fg[2] * mix + bg_color[2] * (1 - mix))
                a = 255
            row += struct.pack("BBBB", r, g, b, a)
        rows.append(bytes(row))
    return rows


def write_png(path, size, rows):
    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    raw = b"".join(b"\x00" + r for r in rows)
    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    blob = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(blob)


def write_notification(path, size=48):
    """通知小图标：纯白字形 + 透明底（系统只取 alpha 做遮罩并自行着色）。"""
    rows = render(size, GLYPH_SIMPLE, WHITE, lambda x, y: False, WHITE, 0.94)
    write_png(path, size, rows)


def main():
    check = "--check" in sys.argv
    made, missing = [], []
    for density, size in DENSITIES.items():
        d = os.path.join(RES, "mipmap-%s" % density)
        if not os.path.isdir(d):
            print("跳过（目录不存在）：%s" % d)
            continue
        targets = {
            # (字形, 前景色, 底盘形状, 字形占比)
            "ic_launcher.png": (GLYPH, WHITE, rrect(0, 0, 108, 108, 20), GLYPH_SPAN),
            "ic_launcher_round.png": (GLYPH, WHITE, circle(54, 54, 54), ROUND_SPAN),
            "ic_launcher_foreground.png": (GLYPH, WHITE, None, 0.66),
        }
        for name, (glyph, fg, bg_shape, span) in targets.items():
            path = os.path.join(d, name)
            if check:
                (made if os.path.isfile(path) else missing).append(path)
                continue
            if bg_shape is None:
                rows = render(size, glyph, fg, lambda x, y: False, WHITE, span)
            else:
                rows = render(size, glyph, fg, bg_shape, BRAND, span)
            write_png(path, size, rows)
            made.append(path)

    notif = os.path.join(RES, "drawable", "icon_notification.png")
    if check:
        (made if os.path.isfile(notif) else missing).append(notif)
    else:
        write_notification(notif)
        made.append(notif)

    if check:
        print("已存在 %d / 缺失 %d" % (len(made), len(missing)))
        for p in missing:
            print("  缺失：%s" % p)
        return 1 if missing else 0

    print("已生成 %d 个位图：" % len(made))
    for p in made:
        print("  %s" % os.path.relpath(p, ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
