# 生成 Ren'Py 测试游戏的画面素材（手写 PNG 编码，本机无 Pillow）
# 产物直接落到 game/images/，可随测试游戏一起推送到设备。
# 用法：python tools/renpy-testgame/make_art.py
import zlib, struct, os, math, random

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "game", "images")
os.makedirs(OUT, exist_ok=True)


def png(path, w, h, get_px):
    """get_px(x,y) -> (r,g,b,a)"""
    raw = bytearray()
    for y in range(h):
        row = bytearray()
        for x in range(w):
            row += bytes(get_px(x, y))
        raw.append(0)
        raw += row

    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    hdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", hdr) \
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    open(path, "wb").write(data)
    print("wrote", path, len(data), "bytes")


def mix(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(3))


# ---------- 背景：黄昏天空 1280x720 ----------
W, H = 1280, 720
random.seed(20261005)
STARS = [(random.randint(0, W - 1), random.randint(0, 260), random.choice([1, 1, 2]))
         for _ in range(150)]


def bg_px(x, y):
    # 天空渐变：顶部深蓝 -> 紫 -> 地平线橙
    if y < 430:
        t = y / 430.0
        if t < 0.55:
            c = mix((26, 32, 74), (84, 58, 118), t / 0.55)
        else:
            c = mix((84, 58, 118), (255, 150, 84), (t - 0.55) / 0.45)
    else:
        c = mix((255, 150, 84), (120, 62, 70), min(1.0, (y - 430) / 120.0))
    # 星星
    for sx, sy, sr in STARS:
        if sy < 280 and abs(sx - x) <= sr and abs(sy - y) <= sr:
            fade = 1.0 - (sy / 280.0) * 0.65
            return (int(255 * fade), int(255 * fade), int(240 * fade), 255)
    # 夕阳圆盘
    dx, dy = x - 880.0, y - 430.0
    if dx * dx + dy * dy < 62 * 62:
        return (255, 236, 190, 255)
    # 远山剪影（两层）
    m1 = 430 - 70 * math.sin(x / 190.0) - 30 * math.sin(x / 61.0 + 1.3)
    if y > m1:
        return (44, 34, 62, 255)
    m2 = 470 + 26 * math.sin(x / 130.0 + 2.1)
    if y > m2:
        return (26, 20, 40, 255)
    # 地面 / 湖面
    if y > 585:
        t = (y - 585) / 135.0
        base = mix((22, 18, 38), (12, 10, 22), t)
        # 水面横向微波
        ripple = 12 * math.sin(x / 26.0 + 0.5 * math.sin(y / 9.0))
        base = tuple(max(0, min(255, base[i] + int(ripple))) for i in range(3))
        # 夕阳倒影
        if abs(x - 880) < 90 and (y - 585) < 90:
            base = mix(base, (255, 170, 110), 0.35 * (1 - (y - 585) / 90.0))
        return (base[0], base[1], base[2], 255)
    return (c[0], c[1], c[2], 255)


png(os.path.join(OUT, "bg_dusk.png"), W, H, bg_px)

# ---------- 角色立绘：半身 anime 少女 600x900 RGBA ----------
CW, CHH = 600, 900
CX = 300.0


def ell(x, y, cx, cy, rx, ry):
    return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1.0


def hero_px(x, y):
    a = 0
    # 身体 / 校服（梯形）
    if 520 <= y <= CHH:
        half = 96 + (y - 520) * 0.42
        if abs(x - CX) <= half:
            jacket = (44, 56, 106)
            shirt = (238, 242, 250)
            if abs(x - CX) < 40 and y < 640:
                # 衬衫 V 领
                return (shirt[0], shirt[1], shirt[2], 255)
            return (jacket[0], jacket[1], jacket[2], 255)
    # 领结
    if ell(x, y, CX, 600, 30, 20):
        return (200, 62, 74, 255)
    # 脖子
    if ell(x, y, CX, 500, 34, 52) and y > 470:
        return (252, 216, 196, 255)
    a = 0
    # 脸
    if ell(x, y, CX, 380, 118, 138):
        return (255, 226, 205, 255)
    # 耳朵边发 + 后发
    if ell(x, y, CX, 368, 150, 172):
        return (92, 62, 48, 255)
    # 刘海（覆盖额头）
    if ell(x, y, CX, 300, 132, 104):
        return (110, 74, 56, 255)
    # 侧边垂发
    if ell(x, y, CX - 120, 430, 34, 150) or ell(x, y, CX + 120, 430, 34, 150):
        return (110, 74, 56, 255)
    # 眼睛
    for ex in (CX - 52, CX + 52):
        if ell(x, y, ex, 396, 26, 34):
            return (255, 255, 255, 255)
        if ell(x, y, ex, 396, 18, 27):
            return (58, 84, 140, 255)
        if ell(x, y, ex + 6, 388, 7, 9):
            return (255, 255, 255, 255)
    # 眉
    for ex in (CX - 52, CX + 52):
        if ell(x, y, ex, 358, 24, 5):
            return (86, 58, 44, 255)
    # 嘴
    if ell(x, y, CX, 448, 14, 5) and y > 448:
        return (196, 96, 96, 255)
    # 腮红
    for ex in (CX - 84, CX + 84):
        if ell(x, y, ex, 428, 26, 12):
            return (250, 186, 178, 255)
    return (0, 0, 0, 0)


png(os.path.join(OUT, "heroine.png"), CW, CHH, hero_px)
print("done")
