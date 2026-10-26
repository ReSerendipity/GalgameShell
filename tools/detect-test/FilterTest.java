import com.winlator.galgame.GalgameFilter;
import com.winlator.galgame.GalgameIds;

import java.util.Arrays;

/**
 * 分类 / ID 纯逻辑回归（无 Android 依赖，直接 JVM 跑）。
 *
 * <p>为什么单独一套：gameId 冲突与「过滤条件写错」都不会崩、只会让游戏卡片凭空消失
 * （2026-10-26 真机事故里已经吃过一次同名容器的亏）。这两块逻辑都是纯函数，适合钉住。
 */
public class FilterTest {

    static int pass = 0;
    static int fail = 0;

    static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("  PASS  " + name);
        } else {
            fail++;
            System.out.println("  FAIL  " + name);
        }
    }

    public static void main(String[] args) {
        // ---- gameId ----
        check("id-ascii-unchanged", GalgameIds.gameId("MyGame", null).equals("MyGame"));
        String cjk = GalgameIds.gameId("椎名真穂のヒミツ", null);
        check("id-cjk-hash-suffix", cjk.matches("_+-[0-9a-f]{4}"));
        check("id-cjk-stable", cjk.equals(GalgameIds.gameId("椎名真穂のヒミツ", null)));
        check("id-cjk-distinct",
                !cjk.equals(GalgameIds.gameId("別のゲーム", null)));
        check("id-empty-fallback", GalgameIds.gameId("", null).equals("game"));
        check("id-null-fallback", GalgameIds.gameId(null, null).equals("game"));
        check("id-single-cjk-fallback",
                GalgameIds.gameId("あ", null).matches("game-[0-9a-f]{4}"));
        check("id-dedupe-2",
                GalgameIds.gameId("MyGame", Arrays.asList("MyGame")).equals("MyGame-2"));
        check("id-dedupe-3",
                GalgameIds.gameId("MyGame", Arrays.asList("MyGame", "MyGame-2")).equals("MyGame-3"));
        check("id-ascii-with-dots",
                GalgameIds.gameId("game.v1_2", null).equals("game.v1_2"));

        // ---- 标签规范化 ----
        check("tag-trim", "汉化".equals(GalgameFilter.normalizeTag("  汉化  ")));
        check("tag-empty-null", GalgameFilter.normalizeTag("   ") == null);
        check("tag-null", GalgameFilter.normalizeTag(null) == null);
        check("tag-truncate-12",
                GalgameFilter.normalizeTag("123456789012345678").equals("123456789012"));
        check("tag-newline-space", "a b".equals(GalgameFilter.normalizeTag("a\nb")));

        // ---- 过滤 ----
        check("filter-none", GalgameFilter.matches("UNITY", "vn", Arrays.asList("汉化"),
                null, null, null));
        check("filter-engine-hit", GalgameFilter.matches("UNITY", null, null,
                "UNITY", null, null));
        check("filter-engine-miss", !GalgameFilter.matches("RENPY", null, null,
                "UNITY", null, null));
        check("filter-engine-unknown-key", GalgameFilter.matches(null, null, null,
                "?", null, null));
        check("filter-genre-hit", GalgameFilter.matches("UNITY", "vn", null,
                null, "vn", null));
        check("filter-genre-miss", !GalgameFilter.matches("UNITY", "adv", null,
                null, "vn", null));
        check("filter-tag-hit", GalgameFilter.matches("UNITY", null, Arrays.asList("汉化"),
                null, null, "汉化"));
        check("filter-tag-no-tags", !GalgameFilter.matches("UNITY", null, null,
                null, null, "汉化"));
        check("filter-tag-null-list", !GalgameFilter.matches("UNITY", null, null,
                null, null, "汉化"));
        check("filter-combo-hit", GalgameFilter.matches("UNITY", "vn", Arrays.asList("汉化"),
                "UNITY", "vn", "汉化"));
        check("filter-combo-miss-genre", !GalgameFilter.matches("UNITY", "adv", Arrays.asList("汉化"),
                "UNITY", "vn", "汉化"));
        check("filter-combo-miss-tag", !GalgameFilter.matches("UNITY", "vn", Arrays.asList("生肉"),
                "UNITY", "vn", "汉化"));

        // ---- 图标 DIB → BMP（档5：让 exe 图标能真正解码）----
        byte[] dib = new byte[40 + 4 * 4 * 4];
        writeLE32(dib, 0, 40);        // biSize
        writeLE32(dib, 4, 4);         // biWidth
        writeLE32(dib, 8, 8);         // biHeight = 显示高 4 ×2（含 AND 掩码）
        writeLE16(dib, 14, 32);       // biBitCount
        byte[] bmp = com.winlator.galgame.IconCodec.dibToBmp(dib);
        check("icon-bmp-header", bmp != null && bmp[0] == 'B' && bmp[1] == 'M');
        check("icon-bmp-offbits", bmp != null && readLE32(bmp, 10) == 14 + 40);
        check("icon-bmp-half-height", bmp != null && readLE32(bmp, 14 + 8) == 4);
        check("icon-bmp-reject-short",
                com.winlator.galgame.IconCodec.dibToBmp(new byte[10]) == null);
        byte[] odd = dib.clone();
        writeLE32(odd, 8, 7);
        check("icon-bmp-reject-odd-height",
                com.winlator.galgame.IconCodec.dibToBmp(odd) == null);
        byte[] dib8 = new byte[40 + 1024 + 4];
        writeLE32(dib8, 0, 40);
        writeLE32(dib8, 4, 4);
        writeLE32(dib8, 8, 8);
        writeLE16(dib8, 14, 8);       // 8bpp：默认 256 色调色板
        byte[] bmp8 = com.winlator.galgame.IconCodec.dibToBmp(dib8);
        check("icon-bmp-8bpp-palette", bmp8 != null && readLE32(bmp8, 10) == 14 + 40 + 1024);

        System.out.println("\n==== " + pass + " passed, " + fail + " failed ====");
        if (fail > 0) System.exit(1);
    }

    private static int readLE32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static void writeLE32(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >>> 8) & 0xFF);
        b[off + 2] = (byte) ((v >>> 16) & 0xFF);
        b[off + 3] = (byte) ((v >>> 24) & 0xFF);
    }

    private static void writeLE16(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >>> 8) & 0xFF);
    }
}
