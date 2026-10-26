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

        System.out.println("\n==== " + pass + " passed, " + fail + " failed ====");
        if (fail > 0) System.exit(1);
    }
}
