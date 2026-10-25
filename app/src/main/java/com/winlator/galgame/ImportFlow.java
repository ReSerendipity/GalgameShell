package com.winlator.galgame;

import android.content.Context;
import android.util.Log;

import com.winlator.container.Container;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * P1 导入即玩核心（Plan Part VIII §VIII.3 / A2+A3 / B4 / A5）。
 *
 * 流程：选文件夹 → EngineDetector.detect（B1 路由裁决）
 *      → A 路由：engine_presets.json 注入 → 构造 Container data JSONObject
 *        →（UI 调用 ContainerManager.createContainerAsync(data)）→ stageGameFiles 复制游戏 + 挂 S: 盘
 *      → B 路由：记录原生播放器，交由 UI 唤起
 *
 * 与官方隔离（零改铁律 §VIII.4）：
 *   - 绝不直接改 Container.java / LocaleHelper / strings.xml。
 *   - 容器创建交给官方 ContainerManager.createContainerAsync(JSONObject)，本类只「构造 data」。
 *   - windows_version / locale / font / encrypted 等 galgame 专属项放进 data.extraData，
 *     官方 loadData 会原样保存，由本 fork 前端/启动器读取，不触碰任何官方常量。
 *   - S: 盘通过拼接 Container.DEFAULT_DRIVES（不修改该常量）实现。
 *
 * A5 加密：仅检测 + 标记（encrypted / encryption_markers），绝不破解。
 */
public final class ImportFlow {

    private ImportFlow() {}

    /**
     * 执行一次导入分析（不创建容器、不复制文件）。
     * @param sourceFolder 用户选中的游戏源文件夹
     * @param gameId       游戏唯一 ID（容器命名 + S: 盘子目录，A2 兜底）
     * @param context      用于读取 assets/engine_presets.json
     * @return 导入结果；A 路由含 {@link #containerData}，由 UI 交给 ContainerManager
     */
    public static ImportResult run(File sourceFolder, String gameId, Context context) {
        return run(sourceFolder, gameId, context, false);
    }

    /**
     * 同上，但可强制按 A 路由（容器）处理。
     *
     * <p>用于「改用 Wine 容器运行」：内置 Kirikiroid2 跑不起来的游戏（脚本报
     * {@code Member … does not exist}）改由游戏自带的 Windows 原版引擎跑。这类游戏检测结果
     * 仍是 {@link EngineDetector.Route#B}，强制容器时把 route 改写成 A，并照常构造
     * {@code containerData} 与 S: 盘目录。
     */
    public static ImportResult run(File sourceFolder, String gameId, Context context,
                                   boolean forceContainer) {
        if (sourceFolder == null || !sourceFolder.isDirectory()) {
            throw new IllegalArgumentException("sourceFolder 必须是一个存在的目录");
        }

        EngineDetector.Engine engine = EngineDetector.detect(sourceFolder);
        EngineDetector.Route route = engine.route;
        String[] children = sourceFolder.list();
        Log.i("GalgameShell: import", "source=" + sourceFolder.getAbsolutePath()
                + " gameId=" + gameId + " engine=" + engine + " route=" + route
                + " files=" + (children == null ? 0 : children.length));
        List<String> markers = EngineDetector.encryptionMarkers(sourceFolder);
        boolean encrypted = !markers.isEmpty();   // A5：仅检测，不破解

        ImportResult r = new ImportResult();
        r.engine = engine;
        r.route = route;
        r.gameId = gameId;
        // 语言检测（中文游戏 → zh_CN.UTF-8，否则维持预设 ja_JP）
        r.language = GalgameLanguageDetector.detectLocale(sourceFolder, null);
        r.encrypted = encrypted;
        r.encryptionMarkers = markers.isEmpty() ? Collections.<String>emptyList()
                                                 : new ArrayList<>(markers);

        if (route == EngineDetector.Route.B && !forceContainer) {
            // B 路由：原生播放器唤起（A4 Tier-2 闭源聚合器，仅按需唤起，风险自负）
            r.nativePlayer = engine.nativePlayer;
            r.preset = null;
            r.containerData = null;
            r.saveDir = null;
            return r;
        }
        if (forceContainer) r.route = EngineDetector.Route.A;

        // ---- A 路由：Winlator 模拟兜底 ----
        EnginePreset preset = loadPreset(context, engine);
        r.preset = preset;

        // B4：确保常驻 S: 盘目录（容器外，存活于容器重置/重装）
        GalgameSaveManager saveManager = new GalgameSaveManager(gameId);
        saveManager.ensureSaveDir();
        r.saveDir = new File(saveManager.savePath());

        // 构造官方 ContainerManager 所需的 data JSONObject
        r.containerData = buildContainerData(engine, gameId, preset, r.saveDir, encrypted, markers, r.language, context);
        return r;
    }

    /**
     * 容器创建后调用：A3 复制游戏到容器可写位置 + B4 确认 S: 盘 + 写 galgame_overlay.json。
     * @param container    已创建的 Container（来自 ContainerManager 回调）
     * @param result       {@link #run} 的返回值
     * @param sourceFolder 原始游戏源文件夹（A3 源）
     * @return 容器内游戏目录（drive_c/galgame/<gameId>）
     */
    public static File stageGameFiles(Container container, ImportResult result, File sourceFolder) {
        return stageGameFiles(container, result, sourceFolder, null);
    }

    /**
     * 容器创建后调用：A3 复制游戏到容器可写位置 + B4 确认 S: 盘 + P2 日文化注入 + 写 galgame_overlay.json。
     * @param container    已创建的 Container（来自 ContainerManager 回调）
     * @param result       {@link #run} 的返回值
     * @param sourceFolder 原始游戏源文件夹（A3 源）
     * @param context      用于 P2 日文化注入；为 null 则跳过注入（便于无 UI 的单测）
     * @return 容器内游戏目录（drive_c/galgame/<gameId>）
     */
    public static File stageGameFiles(Container container, ImportResult result, File sourceFolder,
                                      Context context) {
        if (result.route != EngineDetector.Route.A || result.containerData == null) {
            throw new IllegalStateException("仅 A 路由需要 stage（B 路由由原生播放器处理）");
        }
        File root = container.getRootDir();

        // A3 默认复制(可写)：sourceFolder 内容 → 容器 drive_c/galgame/<gameId>
        // 规避「引用挂载符号链接」死结（RK-06），直接拷贝为可写副本。
        File gameDir = new File(root, ".wine/drive_c/galgame/" + result.gameId);
        try {
            copyDirectory(sourceFolder, gameDir);
        } catch (IOException e) {
            throw new IllegalStateException("复制游戏文件失败: " + e.getMessage(), e);
        }

        // B4：S: 盘目录再确认一次（run 中已 ensure，重装/重置后此处兜底）
        new GalgameSaveManager(result.gameId).ensureSaveDir();

        result.gameExe = findExecutable(gameDir, result.engine);

        // P2：日/中文化注入（locale 由检测结果决定；中文时自动选 CJK 字体并回退 /system/fonts）
        if (context != null) {
            // 复检语言（结合 exe 名，提升中文识别率）
            if (result.language == null || !result.language.startsWith("zh")) {
                String reDetect = GalgameLanguageDetector.detectLocale(
                        gameDir, result.gameExe != null ? result.gameExe.getName() : null);
                if (reDetect.startsWith("zh")) result.language = reDetect;
            }
            boolean chinese = result.language != null && result.language.startsWith("zh");
            result.localeReport = GalgameLocaleInjector.apply(
                    container, result.preset, gameDir, context, result.language, chinese);
        }

        // 写 galgame_overlay.json（不修改官方 .container，供前端/启动器读取）
        writeOverlay(root, result, gameDir);

        return gameDir;
    }

    // ---- 容器 data 构造（官方 Container.loadData 契约）----

    private static JSONObject buildContainerData(EngineDetector.Engine engine, String gameId,
                                                  EnginePreset preset, File saveDir,
                                                  boolean encrypted, List<String> markers,
                                                  String detectedLocale, Context context) {
        try {
            JSONObject data = new JSONObject();
            data.put("name", "galgame-" + gameId);

            // envVars：Container 默认 + 预设区域变量（A4 日文化）
            String env = Container.DEFAULT_ENV_VARS;
            if (preset != null) {
                String append = preset.envVarsAppend();
                if (!append.isEmpty()) env += " " + append;
            }
            // 中文覆盖：把预设写死的 ja_JP.UTF-8 替换为检测到的 locale（避免日语代码页误读 GBK 出问号）
            if (detectedLocale != null && detectedLocale.startsWith("zh")) {
                env = env.replace("ja_JP.UTF-8", detectedLocale);
            }
            data.put("envVars", env);

            // drives：Container 默认 + S: 常驻存档盘（B4，不修改 DEFAULT_DRIVES 常量）
            data.put("drives", Container.DEFAULT_DRIVES + "S:" + saveDir.getAbsolutePath());

            // wincomponents：预设/默认 基础上强制开启视频解码组件（P3，OP/ED 不黑屏）
            String wincomponents = GalgameVideoSupport.ensureVideoComponents(
                    preset != null ? preset.wincomponents : null);
            data.put("wincomponents", wincomponents);

            if (preset != null) {
                if (preset.dxwrapper != null) data.put("dxwrapper", preset.dxwrapper);
                if (preset.box64Preset != null) data.put("box64Preset", preset.box64Preset);
                data.put("startupSelection", preset.startupSelection);
                // TD-14：部分引擎（KiriKiri）把主窗口固定在虚拟屏 (152,152)，窗口尺寸等于容器分辨率时
                // 画布右下会被裁掉，靠右/靠下的 UI 看不见也点不到。预设里给一档更大的虚拟屏，
                // 让游戏自选的窗口尺寸（≤1440x810）都能完整落在屏内。
                if (preset.screenSize != null) data.put("screenSize", preset.screenSize);
                // graphicsDriver: "default"/null 时按设备 GPU 自动选（M2：Mali→Vortek），
                // 显式指定则沿用预设（不覆盖用户/预设选择）
                String resolvedDriver = GalgameGpuProfiler.resolveGraphicsDriver(
                        preset.graphicsDriver, GalgameGpuProfiler.detect(context));
                if (resolvedDriver != null
                        && !"default".equalsIgnoreCase(resolvedDriver)) {
                    data.put("graphicsDriver", resolvedDriver);
                }
            }

            // extraData：galgame 专属，官方 loadData 会原样保存，由本 fork 读取
            JSONObject extra = new JSONObject();
            extra.put("galgame_engine", engine.name());
            extra.put("galgame_game_id", gameId);
            extra.put("encrypted", encrypted);
            if (preset != null) {
                if (preset.windowsVersion != null) extra.put("windows_version", preset.windowsVersion);
                if (preset.locale != null) extra.put("locale", preset.locale);
                if (preset.font != null) extra.put("font", preset.font);
            }
            if (!markers.isEmpty()) extra.put("encryption_markers", new JSONArray(markers));
            data.put("extraData", extra);

            return data;
        } catch (Exception e) {
            throw new IllegalStateException("构造容器 data 失败", e);
        }
    }

    // ---- 预设加载 ----

    private static EnginePreset loadPreset(Context context, EngineDetector.Engine engine) {
        try {
            JSONObject root = readAssetsJson(context, "engine_presets.json");
            String key = presetKey(engine);
            if (root.has(key)) return EnginePreset.fromJson(root.getJSONObject(key));
            if (root.has("unknown")) return EnginePreset.fromJson(root.getJSONObject("unknown"));
            return null;
        } catch (Exception e) {
            // 预设缺失不阻断导入，沿用 Container 默认
            return null;
        }
    }

    private static String presetKey(EngineDetector.Engine e) {
        switch (e) {
            case SIGLUS:    return "siglus";
            case YURIS:     return "yrg";
            case ARTEMIS:   return "artemis";
            case CATSYSTEM2:return "cat_system2";
            case ADVHD:     return "advhd";
            case SYSTEMNNN: return "systemnnn";
            case BGI:       return "bgi";
            case CMVS:      return "cmvs";
            case UNITY:     return "unity";
            case KIRIKIRI:  return "kirikiri";
            default:        return "unknown";
        }
    }

    private static JSONObject readAssetsJson(Context context, String name)
            throws IOException, JSONException {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1 << 14];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    // ---- 文件操作 ----

    private static void copyDirectory(File src, File dst) throws IOException {
        if (!dst.exists() && !dst.mkdirs()) throw new IOException("无法创建 " + dst);
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            File t = new File(dst, f.getName());
            if (f.isDirectory()) copyDirectory(f, t);
            else copyFile(f, t);
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    /** 工具/安装程序类 exe 词根：命中后基本不可能是游戏主程序。 */
    private static final String[] TOOL_EXE_MARKERS = {
            "unins", "uninstall", "setup", "installer", "config", "hookfont",
            "bhvc", "crashhandler", "vcredist", "dxsetup", "dxwebsetup", "oalinst",
            "dotnet", "update", "patch", "crack", "savedata",
            "ファイル破損", "エンジン設定", "アンインストール", "セットアップ", "削除"
    };

    /** 汉化/区服后缀：同目录同时存在原版与汉化版时优先原版。 */
    private static final String[] LOCALE_SUFFIXES = {"_cn", "_chs", "_cht", "_tw", "_jp", "_zh"};

    /**
     * 选启动 exe：优先引擎固定名，其次与游戏目录同名，排除工具程序（BHVC / nnnConfig /
     * HookFont / 卸载器…），控制台子系统降权，同分取体积大者。
     *
     * <p>此前只取「固定名，否则目录里第一个 .exe」，目录按字母序时会把 BHVC.exe、
     * nnnConfig2.exe、HookFont.exe 当成游戏启动，表现为黑屏/卡启动/立刻退出。
     */
    private static File findExecutable(File dir, EngineDetector.Engine engine) {
        if (dir == null || !dir.isDirectory()) return null;
        String preferred = null;
        switch (engine) {
            case SIGLUS:    preferred = "SiglusEngine.exe"; break;
            case YURIS:     preferred = "YurisEngine.exe"; break;
            case ARTEMIS:   preferred = "REALLIVE.exe"; break;
            case CATSYSTEM2:preferred = "CatSystem2.exe"; break;
            case ADVHD:      preferred = "AdvHD.exe"; break;
            case BGI:        preferred = "BGI.exe"; break;
            case CMVS:       preferred = "cmvs.exe"; break;
            default: break;
        }
        File[] files = dir.listFiles();
        if (files == null) return null;

        File best = null;
        int bestScore = Integer.MIN_VALUE;
        String dirKey = normalizeName(dir.getName());
        for (File f : files) {
            if (!f.isFile()) continue;
            String name = f.getName();
            if (!name.toLowerCase().endsWith(".exe")) continue;
            String stem = name.substring(0, name.length() - 4);
            String lowerStem = stem.toLowerCase();
            String key = normalizeName(stem);

            int score = 0;
            if (preferred != null && preferred.equalsIgnoreCase(name)) score += 10000;
            if (!dirKey.isEmpty() && !key.isEmpty()) {
                if (key.equals(dirKey)) score += 4000;
                else if (dirKey.contains(key)) score += 1500;
            }
            boolean tool = false;
            for (String marker : TOOL_EXE_MARKERS) {
                if (lowerStem.contains(marker)) { tool = true; break; }
            }
            if (tool) score -= 100000;
            if (isConsoleSubsystem(f)) score -= 2000;
            for (String suffix : LOCALE_SUFFIXES) {
                if (lowerStem.endsWith(suffix)) { score -= 500; break; }
            }
            score += (int)Math.min(f.length() / (1024 * 1024), 100);

            if (score > bestScore) {
                bestScore = score;
                best = f;
            }
        }
        return best;
    }

    private static String normalizeName(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /** PE 可选头 Subsystem == 3（CUI），用于把命令行工具降权；解析失败按 GUI 处理。 */
    private static boolean isConsoleSubsystem(File f) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r")) {
            if (raf.length() < 0x40) return false;
            raf.seek(0x3C);
            int peOffset = Integer.reverseBytes(raf.readInt());
            if (peOffset <= 0 || peOffset + 0x5E > raf.length()) return false;
            raf.seek(peOffset);
            if (raf.read() != 'P' || raf.read() != 'E') return false;
            raf.seek(peOffset + 0x5C);
            return raf.readUnsignedShort() == 3;
        }
        catch (Exception e) {
            return false;
        }
    }

    private static void writeOverlay(File root, ImportResult result, File gameDir) {
        try {
            JSONObject o = new JSONObject();
            o.put("game_id", result.gameId);
            o.put("engine", result.engine.name());
            o.put("route", result.route.name());
            o.put("encrypted", result.encrypted);
            o.put("language", result.language != null ? result.language : "");
            o.put("game_dir", gameDir.getAbsolutePath());
            if (result.saveDir != null) o.put("save_dir", result.saveDir.getAbsolutePath());
            if (result.gameExe != null) o.put("exe", result.gameExe.getAbsolutePath());
            if (!result.encryptionMarkers.isEmpty()) {
                o.put("encryption_markers", new JSONArray(result.encryptionMarkers));
            }
            if (result.localeReport != null) {
                JSONObject loc = new JSONObject();
                loc.put("locale", result.localeReport.locale);
                loc.put("env_applied", result.localeReport.envApplied);
                if (result.localeReport.fontFile != null) loc.put("font_file", result.localeReport.fontFile);
                if (result.localeReport.fontFace != null) loc.put("font_face", result.localeReport.fontFace);
                if (!result.localeReport.faces.isEmpty()) {
                    loc.put("faces", new JSONArray(result.localeReport.faces));
                }
                if (!result.localeReport.warnings.isEmpty()) {
                    loc.put("warnings", new JSONArray(result.localeReport.warnings));
                }
                o.put("locale_injection", loc);
            }
            File f = new File(root, "galgame_overlay.json");
            try (Writer w = new java.io.FileWriter(f)) { w.write(o.toString(2)); }
        } catch (Exception ignored) {
            // 覆盖文件写入失败不阻断导入
        }
    }

    /** 导入结果。A 路由含 {@link #containerData}（交给 ContainerManager）；B 路由含 {@link #nativePlayer}。 */
    public static final class ImportResult {
        public EngineDetector.Engine engine;
        public EngineDetector.Route route;
        public String language;         // 检测到的 locale（zh_CN.UTF-8 / ja_JP.UTF-8），run 阶段填
        public String gameId;
        public boolean encrypted;
        public List<String> encryptionMarkers = Collections.emptyList();
        public String nativePlayer;     // B 路由：原生播放器包名/标识
        public EnginePreset preset;     // A 路由：注入预设
        public JSONObject containerData;// A 路由：ContainerManager.createContainerAsync(data)
        public File saveDir;            // S: 盘路径（A 路由）
        public File gameExe;            // 启动用 exe（stageGameFiles 后填）
        public GalgameLocaleInjector.Report localeReport; // P2 日文化注入结果（stageGameFiles 后填）
    }
}
