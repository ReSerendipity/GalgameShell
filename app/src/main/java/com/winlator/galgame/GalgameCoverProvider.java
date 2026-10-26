package com.winlator.galgame;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.winlator.core.AppUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * M1 游戏库封面（借鉴 GameNative 的封面机制：扫描目录图 + exe 图标提取；游戏自带封面优先）。
 *
 * <p>取封面优先级（均容错，任何一步失败自动降级，最终无图返回 null 由 UI 占位）：
 * <ol>
 *   <li>扫描游戏目录的图像文件：优先命中 cover/icon/logo/background/背景/封面 等关键字，否则取第一张图；</li>
 *   <li>从游戏 exe 提取 PE 图标（RT_GROUP_ICON → RT_ICON → PNG / ICO→Bitmap），写入缓存目录；</li>
 *   <li>兜底返回 null。</li>
 * </ol>
 *
 * 用户也可在游戏目录放名为 {@code cover.png/jpg/...} 或 {@code 封面.*} 的文件，扫描会优先命中。
 *
 * 零改铁律：纯新增工具类，不依赖任何官方组件；exe 图标解析为自实现 PE 资源读取，无外部库。
 */
public final class GalgameCoverProvider {

    private GalgameCoverProvider() {}

    private static final String[] IMAGE_EXT = {"png", "jpg", "jpeg", "bmp", "webp", "gif", "ico"};
    // 优先级关键字（命中即优先，忽略大小写）
    private static final String[] PRIORITY_HINTS = {
            "cover", "icon", "logo", "background", "bg", "封面", "图标", "背景", "title", "banner"
    };

    /** 封面缓存目录（应用私有，卸载即清）。 */
    private static File cacheDir() {
        File d = new File(AppUtils.INTERNAL_STORAGE, "galgame-covers");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    /**
     * 取得游戏封面文件（可直接给 ImageView 解码）。无图返回 null。
     * @param gameDir 容器内游戏目录（drive_c/galgame/<id>）或原始游戏目录
     * @param exe     启动 exe（用于提取图标），可为 null
     */
    /**
     * 带自定义优先级的入口：<b>自定义封面 &gt; 目录图 &gt; exe 图标</b>。
     * 自定义封面由 {@link #setCustomCover} 拷进应用私有目录并登记在索引里。
     */
    public static File coverFor(Context context, String gameId, File gameDir, File exe) {
        File custom = customCover(context, gameId);
        if (custom != null) return custom;
        return coverFor(gameDir, exe);
    }

    /** 自定义封面文件（索引里登记且文件仍在才返回）。 */
    public static File customCover(Context context, String gameId) {
        if (context == null || gameId == null || gameId.isEmpty()) return null;
        GalgameLibraryIndex.Entry e = GalgameLibraryIndex.find(context, gameId);
        if (e == null || e.cover == null || e.cover.isEmpty()) return null;
        File f = new File(e.cover);
        return f.isFile() && f.length() > 0 ? f : null;
    }

    /**
     * 把用户选的图片拷成该游戏的自定义封面。
     * 先写 .tmp 并实际解码一次，解不开的（非图片/损坏文件）不落盘。
     */
    public static boolean setCustomCover(Context context, String gameId, Uri uri) {
        if (context == null || gameId == null || uri == null) return false;
        File dest = new File(cacheDir(), "custom-" + gameId + ".img");
        File tmp = new File(dest.getAbsolutePath() + ".tmp");
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) return false;
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            tmp.delete();
            return false;
        }
        Bitmap probe = BitmapFactory.decodeFile(tmp.getAbsolutePath());
        if (probe == null) {
            tmp.delete();
            return false;
        }
        probe.recycle();
        if (!tmp.renameTo(dest)) {
            tmp.delete();
            return false;
        }
        GalgameLibraryIndex.setCover(context, gameId, dest.getAbsolutePath());
        return true;
    }

    /** 恢复自动封面（删掉自定义文件并清除登记）。 */
    public static void clearCustomCover(Context context, String gameId) {
        File f = customCover(context, gameId);
        if (f != null) f.delete();
        GalgameLibraryIndex.setCover(context, gameId, null);
    }

    public static File coverFor(File gameDir, File exe) {
        if (gameDir == null || !gameDir.isDirectory()) return null;
        try {
            // 1) 目录图像扫描
            File img = scanImage(gameDir);
            if (img != null) return img;

            // 2) exe 图标提取（仅当目录无图且提供了 exe）
            if (exe != null && exe.isFile()) {
                File cached = extractExeIcon(exe);
                if (cached != null && cached.isFile()) return cached;
            }
        } catch (Throwable ignored) { /* 任何异常都降级为无封面 */ }
        return null;
    }

    // ---- 1) 目录图像扫描 ----

    private static File scanImage(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        List<File> images = new ArrayList<>();
        for (File f : files) {
            if (!f.isFile()) continue;
            if (hasImageExt(f.getName())) images.add(f);
        }
        if (images.isEmpty()) return null;

        // 优先级：命中关键字的排前
        File best = null;
        for (String hint : PRIORITY_HINTS) {
            for (File f : images) {
                if (f.getName().toLowerCase(Locale.ROOT).contains(hint)) { best = f; break; }
            }
            if (best != null) break;
        }
        return best != null ? best : images.get(0);
    }

    private static boolean hasImageExt(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : IMAGE_EXT) {
            if (lower.endsWith("." + ext)) return true;
        }
        return false;
    }

    // ---- 2) exe 图标提取 ----

    private static File extractExeIcon(File exe) {
        File cache = new File(cacheDir(), hash(exe.getAbsolutePath()) + ".png");
        if (cache.isFile() && cache.length() > 0) return cache;   // 已缓存

        // 组内候选按字节数降序逐个试：最大的那个未必能解码（DIB/调色板/掩码），
        // 以前「取第一个可用」遇到第一张解不开就直接放弃 → 真机永远占位图。
        for (byte[] icon : readIcons(exe)) {
            byte[] png = toPng(icon);
            if (png == null) continue;
            try {
                write(cache, png);
                return cache;
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    /** RT_ICON 原始数据 → PNG 字节。PNG 原样保留；DIB 补 BMP 文件头后交给 BitmapFactory。 */
    private static byte[] toPng(byte[] icon) {
        if (icon == null || icon.length < 4) return null;
        if (isPng(icon)) return icon;
        try {
            byte[] bmp = IconCodec.dibToBmp(icon);
            if (bmp == null) return null;
            Bitmap decoded = BitmapFactory.decodeByteArray(bmp, 0, bmp.length);
            if (decoded == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            boolean ok = decoded.compress(Bitmap.CompressFormat.PNG, 100, bos);
            decoded.recycle();
            return ok ? bos.toByteArray() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void write(File f, byte[] data) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        }
    }

    private static boolean isPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0x89 && (b[1] & 0xFF) == 0x50
                && (b[2] & 0xFF) == 0x4E && (b[3] & 0xFF) == 0x47;
    }

    /**
     * 解析 PE 资源，按 GRPICONDIR 里的字节数降序返回若干候选图标的原始数据（最多 4 个）。
     * 失败返回空列表。
     */
    private static java.util.List<byte[]> readIcons(File exe) {
        java.util.List<byte[]> out = new java.util.ArrayList<>();
        try (InputStream in = new FileInputStream(exe)) {
            byte[] all = readAll(in);
            if (all.length < 64 || all[0] != 'M' || all[1] != 'Z') return out;

            int peOff = readLE32(all, 0x3C);
            if (peOff + 24 >= all.length) return out;
            if (all[peOff] != 'P' || all[peOff + 1] != 'E' || all[peOff + 2] != 0 || all[peOff + 3] != 0) {
                return out;
            }

            int magic = readLE16(all, peOff + 24);
            int optSize = readLE16(all, peOff + 20);
            boolean pe32plus = (magic == 0x20b);
            int ddOff = peOff + 24 + (pe32plus ? 112 : 96) + 2 * 8;
            if (ddOff + 8 > all.length) return out;
            int resRva = readLE32(all, ddOff);
            if (resRva == 0) return out;

            int numSections = readLE16(all, peOff + 6);
            int secOff = peOff + 24 + optSize;
            int resRaw = rvaToRaw(all, secOff, numSections, resRva);
            if (resRaw < 0 || resRaw + 16 > all.length) return out;

            int groupDir = findResourceEntry(all, resRaw, resRaw, 14);
            if (groupDir < 0) return out;

            int gCount = readLE16(all, groupDir + 12) + readLE16(all, groupDir + 14);
            for (int i = 0; i < gCount && out.size() < 4; i++) {
                int entry = groupDir + 16 + i * 8;
                int offset = readLE32(all, entry + 4);
                int child = (offset & 0x7FFFFFFF) + resRaw;
                if (child + 16 > all.length) continue;
                int childCount = readLE16(all, child + 12) + readLE16(all, child + 14);
                for (int j = 0; j < childCount; j++) {
                    int centry = child + 16 + j * 8;
                    int dataOffset = readLE32(all, centry + 4);
                    int dataEntry = (dataOffset & 0x7FFFFFFF) + resRaw;
                    out.addAll(readIconCandidates(all, resRaw, secOff, numSections, dataEntry));
                    if (out.size() >= 4) break;
                }
            }
            return out;
        } catch (Throwable ignored) {
            return out;
        }
    }

    /** 在某资源目录下找指定 Id 的条目，返回其 OffsetToData（高位清=数据入口；高位置=子目录偏移，相对资源基）。 */
    private static int findResourceEntry(byte[] all, int dirBase, int resRaw, int id) {
        int count = readLE16(all, dirBase + 12) + readLE16(all, dirBase + 14);
        for (int i = 0; i < count; i++) {
            int entry = dirBase + 16 + i * 8;
            int nameOrId = readLE32(all, entry);
            if ((nameOrId & 0x80000000) == 0 && (nameOrId & 0x7FFFFFFF) == id) {
                int off = readLE32(all, entry + 4);
                return (off & 0x7FFFFFFF) + resRaw;   // 返回子目录/数据的绝对偏移（相对资源基）
            }
        }
        return -1;
    }

    /**
     * 读 GRPICONDIR 指向的 RT_ICON 数据，按 BytesInRes 降序返回各候选的原始字节。
     * 组内某一条读不出来不影响其它条（不再「第一条失败就整组放弃」）。
     */
    private static java.util.List<byte[]> readIconCandidates(byte[] all, int resRaw, int secOff,
                                                              int numSections, int dataEntry) {
        java.util.List<byte[]> out = new java.util.ArrayList<>();
        int dataRva = readLE32(all, dataEntry);
        int size = readLE32(all, dataEntry + 4);
        int raw = rvaToRaw(all, secOff, numSections, dataRva);
        if (raw < 0 || size <= 0 || raw + size > all.length) return out;

        // GRPICONDIR: Reserved(2)+Type(2)+Count(2)+ entries(每个 14)
        int count = readLE16(all, raw + 4);
        if (count <= 0) return out;
        int[] idRefs = new int[count];
        int[] sizes = new int[count];
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            int e = raw + 6 + i * 14;
            sizes[i] = readLE32(all, e + 8);
            idRefs[i] = readLE16(all, e + 12);
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(sizes[b], sizes[a]));

        int iconDir = findResourceEntry(all, resRaw, resRaw, 3);
        if (iconDir < 0) return out;
        int iconCount = readLE16(all, iconDir + 12) + readLE16(all, iconDir + 14);

        for (int oi : order) {
            if (out.size() >= 4) break;
            int wantId = idRefs[oi];
            for (int i = 0; i < iconCount; i++) {
                int e = iconDir + 16 + i * 8;
                int id = readLE32(all, e) & 0x7FFFFFFF;
                if (id != wantId) continue;
                int off = readLE32(all, e + 4);
                int langDir = (off & 0x7FFFFFFF) + resRaw;
                int langCount = readLE16(all, langDir + 12) + readLE16(all, langDir + 14);
                for (int L = 0; L < langCount; L++) {
                    int le = langDir + 16 + L * 8;
                    int dataOff = readLE32(all, le + 4);
                    int dataEntry2 = (dataOff & 0x7FFFFFFF) + resRaw;
                    int rva = readLE32(all, dataEntry2);
                    int sz = readLE32(all, dataEntry2 + 4);
                    int r = rvaToRaw(all, secOff, numSections, rva);
                    if (r < 0 || sz <= 0 || r + sz > all.length) continue;
                    byte[] one = new byte[sz];
                    System.arraycopy(all, r, one, 0, sz);
                    out.add(one);
                    break;
                }
                break;
            }
        }
        return out;
    }

    private static int rvaToRaw(byte[] all, int secOff, int numSections, int rva) {
        for (int i = 0; i < numSections; i++) {
            int s = secOff + i * 40;
            if (s + 20 > all.length) break;
            int vaddr = readLE32(all, s + 12);
            int vsize = readLE32(all, s + 8);
            int raw = readLE32(all, s + 20);
            if (rva >= vaddr && rva < vaddr + Math.max(vsize, 1)) {
                return raw + (rva - vaddr);
            }
        }
        return -1;
    }

    private static int readLE16(byte[] b, int off) {
        if (off + 2 > b.length) return 0;
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int readLE32(byte[] b, int off) {
        if (off + 4 > b.length) return 0;
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.substring(0, 16);
        } catch (Exception e) {
            return "x" + Integer.toHexString(s.hashCode());
        }
    }
}
