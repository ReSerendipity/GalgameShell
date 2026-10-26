package com.winlator.galgame;

/**
 * 图标字节层面的纯编解码（无 Android 依赖，便于 JVM 回归）。
 *
 * <p>为什么需要它：Windows exe 的资源图标（RT_ICON）绝大多数是 <b>裸 DIB</b>——
 * BITMAPINFOHEADER + 像素 + AND 掩码，没有 BMP 的文件头，也没有 ICO 目录；
 * Android 的 {@code BitmapFactory} 既解不了裸 DIB 也解不了 ICO，于是此前
 * 「从 exe 提取图标」在真机上一直静默失败（库页永远占位图）。
 *
 * <p>{@link #dibToBmp(byte[])} 只做两件事：补 14 字节 BITMAPFILEHEADER（按位深算出调色板
 * 偏移），并把 {@code biHeight} 减半——图标 DIB 的高度是 XOR 层 + AND 掩码层两倍，
 * 不减半会把掩码层也画出来。
 */
public final class IconCodec {

    private IconCodec() {}

    /**
     * @param dib RT_ICON 原始数据（BITMAPINFOHEADER 起）
     * @return 可直接交给 BitmapFactory 的 BMP 文件字节；不是合法图标 DIB 时返回 null
     */
    public static byte[] dibToBmp(byte[] dib) {
        if (dib == null || dib.length < 40) return null;
        int biSize = read32(dib, 0);
        if (biSize < 40 || biSize > dib.length) return null;
        int width = read32(dib, 4);
        int height = read32(dib, 8);
        int bpp = read16(dib, 14) & 0xFFFF;
        long clrUsed = read32(dib, 32) & 0xFFFFFFFFL;
        if (width <= 0 || width > 1024 || height == 0 || height == Integer.MIN_VALUE) return null;
        int absHeight = Math.abs(height);
        // 图标 DIB 的 biHeight = 显示高度 × 2（XOR 位图 + AND 掩码）
        if (absHeight < 2 || (absHeight & 1) != 0) return null;

        int palette = 0;
        if (bpp <= 8) {
            long entries = clrUsed > 0 ? clrUsed : (1L << bpp);
            palette = (int) (entries * 4);
        } else if (clrUsed > 0) {
            palette = (int) (clrUsed * 4);
        }
        int offBits = 14 + biSize + palette;
        // offBits 是 BMP 文件内的偏移（含 14 字节文件头）；调色板必须落在 DIB 内，
        // 且后面还要有像素数据（+14 别漏，否则 8bpp 这类大调色板会被误判成非法）
        if (offBits < 14 + biSize || offBits >= 14 + dib.length) return null;

        byte[] out = new byte[14 + dib.length];
        out[0] = 'B';
        out[1] = 'M';
        write32(out, 2, out.length);       // bfSize
        write32(out, 10, offBits);         // bfOffBits
        System.arraycopy(dib, 0, out, 14, dib.length);
        write32(out, 14 + 8, absHeight / 2);   // biHeight → 只保留 XOR 位图层
        return out;
    }

    private static int read16(byte[] b, int off) {
        if (off + 2 > b.length) return 0;
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int read32(byte[] b, int off) {
        if (off + 4 > b.length) return 0;
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static void write32(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >>> 8) & 0xFF);
        b[off + 2] = (byte) ((v >>> 16) & 0xFF);
        b[off + 3] = (byte) ((v >>> 24) & 0xFF);
    }
}
