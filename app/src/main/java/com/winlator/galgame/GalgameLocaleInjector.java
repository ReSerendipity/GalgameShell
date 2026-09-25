package com.winlator.galgame;

import android.content.Context;

import com.winlator.container.Container;
import com.winlator.core.FileUtils;
import com.winlator.core.WineRegistryEditor;
import com.winlator.core.WineUtils;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * P2 日文化注入（Plan Part IV §IV.4 / A4）。
 *
 * 三层注入，全部**只调用官方公开 API**（{@link WineRegistryEditor} / {@link WineUtils}），
 * 不修改任何官方常量或文件（零改铁律 §VIII.4）：
 *   1) env 级：把 {@code LC_ALL}/{@code LANG} 合入 container.envVars。
 *      官方启动时 {@code XServerDisplayActivity} 会 {@code envVars.putAll(container.getEnvVars())}，
 *      且发生在 {@code LocaleHelper.setEnvVars()} **之后**，故能覆盖其 en/pt/ru 默认（见 GOTCHAS）。
 *   2) 注册表区域级：HKCU\Control Panel\International → ja-JP（Locale/sLanguage/sCountry/iCountry）。
 *      对非 Unicode（Shift-JIS）老 galgame 决定 ANSI 代码页至关重要。
 *   3) 字体级：把找到的日文字体拷进容器 {@code drive_c/windows/Fonts/}，注册到 Fonts 键（含常用别名），
 *      再用 {@link WineUtils#setSystemFont} 设为系统字体 —— 解决豆腐块（□□□）。
 *
 * 字体缺失只记 {@link Report#warnings}，不抛异常、不阻断导入。
 */
public final class GalgameLocaleInjector {

    private GalgameLocaleInjector() {}

    public static final String DEFAULT_LOCALE = "ja_JP.UTF-8";

    private static final String FONT_KEY_NT = "Software\\Microsoft\\Windows NT\\CurrentVersion\\Fonts";
    private static final String FONT_KEY_WIN = "Software\\Microsoft\\Windows\\CurrentVersion\\Fonts";
    private static final String INTL_KEY = "Control Panel\\International";

    /**
     * 对一个已创建的容器施加日文化（幂等，可重复调用）。
     *
     * @param container 已创建容器（来自 ContainerManager 回调）
     * @param preset    A 路由预设，可为 null（用默认 ja_JP）
     * @param gameDir   容器内游戏目录（用于发现游戏自带字体），可为 null
     * @param context   读取 assets 清单用
     * @return 注入报告（供诊断向导 / overlay 记录）
     */
    /** 兼容旧调用：委托到增强版（无 locale 覆盖、非中文自动字体）。 */
    public static Report apply(Container container, EnginePreset preset, File gameDir, Context context) {
        return apply(container, preset, gameDir, context, null, false);
    }

    /**
     * 增强版：支持显式 locale 覆盖（中文检测）与中文自动字体选择。
     * @param overrideLocale 非空则取代 preset.locale/默认 ja_JP（如 zh_CN.UTF-8）；真正生效的是注册表 Locale（00000804）
     * @param chineseFont    为 true 时忽略 preset 的日文 preferredName，按 hints 自动选 CJK 字体并回退 /system/fonts
     */
    public static Report apply(Container container, EnginePreset preset, File gameDir, Context context,
                               String overrideLocale, boolean chineseFont) {
        Report report = new Report();
        String locale = (overrideLocale != null && !overrideLocale.isEmpty())
                ? overrideLocale
                : (preset != null && preset.locale != null && !preset.locale.isEmpty())
                    ? preset.locale : DEFAULT_LOCALE;
        report.locale = locale;

        // 1) env 级
        report.envApplied = mergeEnv(container, locale);

        // 2) 注册表：区域 + 字体
        File userReg = new File(container.getRootDir(), ".wine/user.reg");
        try (WineRegistryEditor reg = new WineRegistryEditor(userReg)) {
            writeInternational(reg, locale);

            // 中文时忽略 preset 的日文 preferredName，强制按 hints 自动选 CJK 字体
            String preferredName = (chineseFont || preset == null) ? null : preset.font;
            File font = GalgameFonts.locate(context, gameDir, preferredName, chineseFont);
            if (font != null) {
                File installed = installFont(container, font, report);
                if (installed != null) {
                    String face = faceName(preset, font, chineseFont);
                    registerFont(reg, face, installed);
                    WineUtils.setSystemFont(reg, face);

                    report.fontFile = installed.getName();
                    report.fontFace = face;
                    report.faces.add(face);

                    for (String alias : GalgameFonts.aliases(context)) {
                        if (alias.equals(face)) continue;
                        registerFont(reg, alias, installed);
                        report.faces.add(alias);
                    }
                }
            }
            else {
                report.warnings.add("未找到" + (chineseFont ? "中文" : "日文") + "字体，请将 .ttf/.otf/.ttc 放入 "
                        + GalgameFonts.dropInDir().getAbsolutePath() + "（框架不分发字体）");
            }
        }
        catch (Exception e) {
            report.warnings.add("注册表注入失败: " + e.getMessage());
        }

        // 3) 标记到 container extra（供诊断向导读取）
        container.putExtra("galgame_locale", locale);
        container.putExtra("galgame_font", report.fontFace != null ? report.fontFace : "");
        container.saveData();

        return report;
    }

    /**
     * 重新注入（M4 诊断向导一键修复）：读容器 {@code galgame_overlay.json} 的语言，
     * 对容器再次施加 locale/字体注入（幂等）。用于「重导入后丢语言/字体」或用户手动改坏后的补救。
     *
     * @return 注入报告；overlay 缺失或游戏目录不存在时返回 null（调用方提示）
     */
    public static Report reapply(Container container, Context context) {
        if (container == null) return null;
        JSONObject overlay = readOverlay(container);
        if (overlay == null) return null;

        String language = overlay.optString("language", "");
        if (language == null || language.isEmpty()) {
            JSONObject loc = overlay.optJSONObject("locale_injection");
            if (loc != null) language = opt(loc, "locale");
        }

        String gameId = overlay.optString("game_id", "");
        if (gameId == null || gameId.isEmpty()) {
            String name = container.getName();
            if (name != null && name.startsWith("galgame-")) gameId = name.substring("galgame-".length());
        }

        File gameDir = new File(container.getRootDir(),
                ".wine/drive_c/galgame/" + (gameId != null ? gameId : ""));
        if (!gameDir.isDirectory()) return null;

        // 旧容器（中文产品化 d798f16 之前导入）overlay 记录的 locale 可能是错的
        //（如中文游戏被记为 ja_JP）。现场用 GalgameLanguageDetector 重检纠正：
        // 保守判定下真日语游戏结果不变，中文游戏则被改为 zh_CN。
        String exePath = overlay.optString("exe", "");
        String detected = GalgameLanguageDetector.detectLocale(gameDir,
                exePath == null ? "" : new File(exePath).getName());
        if (detected != null && !detected.isEmpty()) language = detected;

        boolean chinese = language != null && language.startsWith("zh");
        Report report = apply(container, null, gameDir, context, language, chinese);
        // 回写 overlay：诊断向导读 galgame_overlay.json 判定状态，
        // 不回写则重注入成功后诊断仍显示旧值（2026-09-25 真机回归发现）。
        writeOverlayReport(container, language, report);
        return report;
    }

    /** 把重注入结果回写 {@code galgame_overlay.json} 的 language / locale_injection。 */
    private static void writeOverlayReport(Container container, String language, Report report) {
        if (report == null) return;
        File f = new File(container.getRootDir(), "galgame_overlay.json");
        try {
            JSONObject overlay = f.isFile() ? new JSONObject(
                    new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8))
                    : new JSONObject();
            if (language != null && !language.isEmpty()) overlay.put("language", language);
            JSONObject inj = new JSONObject();
            inj.put("locale", report.locale);
            inj.put("env_applied", true); // mergeEnv 幂等：已正确即视为已应用
            if (report.fontFile != null) inj.put("font", report.fontFile);
            if (report.fontFace != null) inj.put("font_face", report.fontFace);
            if (report.warnings != null && !report.warnings.isEmpty()) {
                inj.put("warnings", new org.json.JSONArray(report.warnings));
            }
            overlay.put("locale_injection", inj);
            java.nio.file.Files.write(f.toPath(),
                    overlay.toString().getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e) {
            // overlay 回写失败不影响注入本身
        }
    }

    private static JSONObject readOverlay(Container container) {
        File f = new File(container.getRootDir(), "galgame_overlay.json");
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1 << 13];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        }
        catch (Exception e) {
            return null;
        }
    }

    private static String opt(JSONObject o, String key) {
        try {
            return o != null && o.has(key) ? o.getString(key) : null;
        }
        catch (Exception e) {
            return null;
        }
    }

    // ---- env ----

    private static boolean mergeEnv(Container container, String locale) {
        String env = container.getEnvVars();
        if (env == null) env = "";
        boolean changed = false;

        // 键缺失则追加；键已存在但值≠目标 locale 则改值（旧容器残留 ja_JP 时重注入 zh 需覆盖）
        if (!containsKey(env, "LC_ALL")) { env = append(env, "LC_ALL=" + locale); changed = true; }
        else if (!containsValue(env, "LC_ALL", locale)) { env = replaceValue(env, "LC_ALL", locale); changed = true; }
        if (!containsKey(env, "LANG"))   { env = append(env, "LANG=" + locale);   changed = true; }
        else if (!containsValue(env, "LANG", locale)) { env = replaceValue(env, "LANG", locale); changed = true; }

        if (changed) container.setEnvVars(env);
        return changed;
    }

    private static boolean containsKey(String env, String name) {
        return env.matches("(?s).*(^|\\s)" + name + "=.*");
    }

    /** env 串中 name 键的值是否恰为 value。 */
    private static boolean containsValue(String env, String name, String value) {
        String quoted = java.util.regex.Pattern.quote(value);
        return env.matches("(?s).*(^|\\s)" + name + "=" + quoted + "(\\s|$).*");
    }

    /** 把 env 串中 name 键的值改为 value（键存在前提下的就地改写）。 */
    private static String replaceValue(String env, String name, String value) {
        return env.replaceAll("(?<=^|\\s)" + name + "=[^\\s]*",
                java.util.regex.Matcher.quoteReplacement(name + "=" + value));
    }

    private static String append(String env, String kv) {
        return env.isEmpty() ? kv : env + " " + kv;
    }

    // ---- 注册表：区域 ----

    private static void writeInternational(WineRegistryEditor reg, String locale) {
        String tag = localeTag(locale);          // ja_JP
        reg.setStringValue(INTL_KEY, "Locale", lcidOf(tag));      // 00000411
        reg.setStringValue(INTL_KEY, "sLanguage", langOf(tag));   // JPN
        reg.setStringValue(INTL_KEY, "sCountry", countryOf(tag)); // Japan
        reg.setDwordValue(INTL_KEY, "iCountry", countryCodeOf(tag));
    }

    /** 取 {@code ja_JP.UTF-8} 的 {@code ja_JP} 段。 */
    static String localeTag(String locale) {
        if (locale == null || locale.isEmpty()) return "ja_JP";
        String s = locale;
        int dot = s.indexOf('.');
        if (dot != -1) s = s.substring(0, dot);
        return s.isEmpty() ? "ja_JP" : s;
    }

    /** LCID 十六进制（Wine 用 HKCU\Control Panel\International\Locale）。 */
    static String lcidOf(String tag) {
        if (tag.startsWith("ja")) return "00000411";
        if (tag.startsWith("zh_TW") || tag.startsWith("zh-Hant")) return "00000404";
        if (tag.startsWith("zh")) return "00000804";
        if (tag.startsWith("ko")) return "00000412";
        return "00000409"; // en-US 兜底
    }

    static String langOf(String tag) {
        if (tag.startsWith("ja")) return "JPN";
        if (tag.startsWith("zh")) return "CHS";
        if (tag.startsWith("ko")) return "KOR";
        return "ENU";
    }

    static String countryOf(String tag) {
        if (tag.startsWith("ja")) return "Japan";
        if (tag.startsWith("zh_TW")) return "Taiwan, China";
        if (tag.startsWith("zh")) return "China";
        if (tag.startsWith("ko")) return "Korea";
        return "United States";
    }

    static int countryCodeOf(String tag) {
        if (tag.startsWith("ja")) return 81;
        if (tag.startsWith("zh_TW")) return 886;
        if (tag.startsWith("zh")) return 86;
        if (tag.startsWith("ko")) return 82;
        return 1;
    }

    // ---- 注册表 + 文件：字体 ----

    private static File installFont(Container container, File font, Report report) {
        File fontsDir = new File(container.getRootDir(), ".wine/drive_c/windows/Fonts");
        if (!fontsDir.isDirectory() && !fontsDir.mkdirs()) {
            report.warnings.add("无法创建 Fonts 目录: " + fontsDir.getAbsolutePath());
            return null;
        }

        File dst = new File(fontsDir, font.getName());
        try {
            if (!dst.isFile() || dst.length() != font.length()) {
                if (!FileUtils.copy(font, dst)) {
                    report.warnings.add("字体复制失败: " + font.getAbsolutePath());
                    return null;
                }
                FileUtils.chmod(dst, 0644);
            }
        }
        catch (Exception e) {
            report.warnings.add("字体复制异常: " + e.getMessage());
            return null;
        }
        return dst;
    }

    private static void registerFont(WineRegistryEditor reg, String face, File installedFont) {
        String value = "C:\\windows\\Fonts\\" + installedFont.getName();
        reg.setStringValue(FONT_KEY_NT, face + " (TrueType)", value);
        reg.setStringValue(FONT_KEY_WIN, face + " (TrueType)", value);
    }

    private static String faceName(EnginePreset preset, File font, boolean chineseFont) {
        // 中文时不用 preset 的日文 fontFace，改取字体文件名（如 NotoSansCJK-Regular）
        if (!chineseFont && preset != null && preset.fontFace != null && !preset.fontFace.isEmpty()) {
            return preset.fontFace;
        }
        String name = font.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** 日文化注入结果（供 overlay / 诊断向导）。 */
    public static final class Report {
        public String locale;
        public boolean envApplied;
        public String fontFile;
        public String fontFace;
        public final List<String> faces = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();

        public boolean fontInstalled() {
            return fontFile != null;
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("locale=").append(locale);
            sb.append(" env=").append(envApplied ? "applied" : "kept");
            sb.append(" font=").append(fontInstalled() ? fontFace : "(none)");
            if (!warnings.isEmpty()) sb.append(" warnings=").append(warnings.size());
            return sb.toString();
        }
    }
}
