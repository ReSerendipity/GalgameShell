package com.winlator.galgame;

import android.content.Context;

import com.winlator.core.AppUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * P2 日文字体定位（Plan Part IV §IV.4 日文化）。
 *
 * 设计原则（与项目「发框架、用户自取模型/资源」一致）：
 *   - 框架**不分发**任何字体二进制，也不联网下载；
 *   - 只在「用户放置目录」和「游戏目录」里找可用日文字体；
 *   - 找不到只告警、不阻断（{@link GalgameLocaleInjector} 仍会注入区域设置）。
 *
 * 用户放置目录：{@code /data/data/com.winlator/storage/galgame-fonts}（卸载容器不丢）。
 * 认字规则与别名/扩展名来自 assets/galgame_fonts.json，可改而无需重编译。
 */
public final class GalgameFonts {

    private GalgameFonts() {}

    private static final String MANIFEST = "galgame_fonts.json";

    private static final String[] DEFAULT_HINTS = {
            "ipa", "gothic", "mincho", "noto", "meiryo", "yugoth", "msgothic",
            "kochi", "takao", "sourcehan", "source-han", "japan",
            "simsun", "yahei", "songti", "simhei", "notosanscjk", "notoserifcjk",
            "sourcehansans", "sourcehanserif", "msyh", "microsoft yahei",
            "fangsong", "kaiti", "cjk"
    };
    private static final String[] DEFAULT_ALIASES = {
            "MS Gothic", "MS PGothic", "MS UI Gothic", "Yu Gothic", "Meiryo",
            "SimSun", "NSimSun", "宋体", "Microsoft YaHei", "微软雅黑",
            "SimHei", "黑体", "KaiTi", "楷体", "FangSong", "仿宋"
    };
    private static final String[] DEFAULT_EXTENSIONS = {"ttf", "otf", "ttc"};

    /** 用户字体放置目录（框架外）。 */
    public static File dropInDir() {
        return new File(AppUtils.INTERNAL_STORAGE, "galgame-fonts");
    }

    /**
     * 按优先级定位一个可用日文字体。
     * 顺序：指定文件名（preset.font）→ drop-in 目录命中关键字 → 游戏目录命中关键字 → drop-in 目录任意字体。
     *
     * @param gameDir       游戏源目录（很多 galgame 自带字体）
     * @param preferredName preset.font，可为 null
     * @return 字体文件；找不到返回 null（调用方告警，不阻断）
     */
    public static File locate(Context context, File gameDir, String preferredName) {
        return locate(context, gameDir, preferredName, false);
    }

    /**
     * 定位字体。
     * allowSystemFonts=true 时额外回退 {@code /system/fonts}（设备 CJK 字体世界可读，
     * 如 NotoSansCJK-Regular.ttc），供中文游戏在 drop-in/游戏目录均无 CJK 字体时取系统字体
     * （此前靠 run-as 手动补丁，现产品化）。
     *
     * @param allowSystemFonts 是否允许回退系统字体（中文游戏为 true，日语游戏保持 false 以零影响）
     */
    public static File locate(Context context, File gameDir, String preferredName,
                              boolean allowSystemFonts) {
        List<File> dirs = new ArrayList<>();
        dirs.add(dropInDir());
        if (gameDir != null) dirs.add(gameDir);
        if (allowSystemFonts) {
            File sf = new File("/system/fonts");
            if (sf.isDirectory()) dirs.add(sf);
        }

        List<String> extensions = extensions(context);

        if (preferredName != null && !preferredName.isEmpty()) {
            for (File dir : dirs) {
                File f = new File(dir, preferredName);
                if (f.isFile() && hasExtension(f.getName(), extensions)) return f;
            }
        }

        List<String> hints = hints(context);
        for (File dir : dirs) {
            File f = bestMatch(dir, extensions, hints, true);
            if (f != null) return f;
        }

        // 兜底：仅 drop-in 目录取任意字体，避免误抓游戏目录里的西文字体
        File dropAny = bestMatch(dropInDir(), extensions, hints, false);
        if (dropAny != null) return dropAny;

        // 再兜底：系统字体目录用 CJK 专属关键字精确匹配（NotoSansCJK / NotoSerifCJK / SourceHan 等）
        if (allowSystemFonts) {
            File sf = new File("/system/fonts");
            if (sf.isDirectory()) {
                List<String> cjkHints = Arrays.asList(
                        "notosanscjk", "notoserifcjk", "cjk", "sourcehansans", "sourcehanserif");
                File f = bestMatch(sf, extensions, cjkHints, true);
                if (f != null) return f;
            }
        }
        return null;
    }

    /** 需要注册为别名的常用日文字体名（让按名取字体的游戏也能命中）。 */
    public static List<String> aliases(Context context) {
        return readStringArray(context, "aliases", DEFAULT_ALIASES);
    }

    static List<String> hints(Context context) {
        return readStringArray(context, "japanese_hints", DEFAULT_HINTS);
    }

    static List<String> extensions(Context context) {
        return readStringArray(context, "extensions", DEFAULT_EXTENSIONS);
    }

    // ---- 内部 ----

    private static File bestMatch(File dir, List<String> extensions, List<String> hints, boolean requireHint) {
        if (dir == null || !dir.isDirectory()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;

        File best = null;
        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (!hasExtension(name, extensions)) continue;

            if (requireHint) {
                boolean hit = false;
                for (String hint : hints) {
                    if (name.contains(hint)) { hit = true; break; }
                }
                if (!hit) continue;
            }

            if (best == null || name.compareTo(best.getName().toLowerCase(Locale.ROOT)) < 0) best = f;
        }
        return best;
    }

    private static boolean hasExtension(String name, List<String> extensions) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : extensions) {
            if (lower.endsWith("." + ext)) return true;
        }
        return false;
    }

    private static List<String> readStringArray(Context context, String key, String[] fallback) {
        try {
            JSONObject root = readManifest(context);
            if (root != null && root.has(key)) {
                JSONArray arr = root.getJSONArray(key);
                List<String> out = new ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
                if (!out.isEmpty()) return out;
            }
        } catch (Exception ignored) {
            // 清单损坏 → 用内置默认
        }
        List<String> out = new ArrayList<>(fallback.length);
        for (String s : fallback) out.add(s);
        return out;
    }

    private static JSONObject readManifest(Context context) {
        if (context == null) return null;
        try (InputStream in = context.getAssets().open(MANIFEST)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1 << 13];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }
}
