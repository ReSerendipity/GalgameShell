package com.winlator.galgame;

import android.content.Context;

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
        if (sourceFolder == null || !sourceFolder.isDirectory()) {
            throw new IllegalArgumentException("sourceFolder 必须是一个存在的目录");
        }

        EngineDetector.Engine engine = EngineDetector.detect(sourceFolder);
        EngineDetector.Route route = engine.route;
        List<String> markers = EngineDetector.encryptionMarkers(sourceFolder);
        boolean encrypted = !markers.isEmpty();   // A5：仅检测，不破解

        ImportResult r = new ImportResult();
        r.engine = engine;
        r.route = route;
        r.gameId = gameId;
        r.encrypted = encrypted;
        r.encryptionMarkers = markers.isEmpty() ? Collections.<String>emptyList()
                                                 : new ArrayList<>(markers);

        if (route == EngineDetector.Route.B) {
            // B 路由：原生播放器唤起（A4 Tier-2 闭源聚合器，仅按需唤起，风险自负）
            r.nativePlayer = engine.nativePlayer;
            r.preset = null;
            r.containerData = null;
            r.saveDir = null;
            return r;
        }

        // ---- A 路由：Winlator 模拟兜底 ----
        EnginePreset preset = loadPreset(context, engine);
        r.preset = preset;

        // B4：确保常驻 S: 盘目录（容器外，存活于容器重置/重装）
        GalgameSaveManager saveManager = new GalgameSaveManager(gameId);
        saveManager.ensureSaveDir();
        r.saveDir = new File(saveManager.savePath());

        // 构造官方 ContainerManager 所需的 data JSONObject
        r.containerData = buildContainerData(engine, gameId, preset, r.saveDir, encrypted, markers);
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

        // P2：日文化注入（LC_ALL/LANG + 区域注册表 + 日文字体）；缺失字体只告警不阻断
        if (context != null) {
            result.localeReport = GalgameLocaleInjector.apply(container, result.preset, gameDir, context);
        }

        // 写 galgame_overlay.json（不修改官方 .container，供前端/启动器读取）
        writeOverlay(root, result, gameDir);

        return gameDir;
    }

    // ---- 容器 data 构造（官方 Container.loadData 契约）----

    private static JSONObject buildContainerData(EngineDetector.Engine engine, String gameId,
                                                  EnginePreset preset, File saveDir,
                                                  boolean encrypted, List<String> markers) {
        try {
            JSONObject data = new JSONObject();
            data.put("name", "galgame-" + gameId);

            // envVars：Container 默认 + 预设区域变量（A4 日文化）
            String env = Container.DEFAULT_ENV_VARS;
            if (preset != null) {
                String append = preset.envVarsAppend();
                if (!append.isEmpty()) env += " " + append;
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
                // graphicsDriver: "default" 表示沿用 Container 默认，不覆盖
                if (preset.graphicsDriver != null
                        && !"default".equalsIgnoreCase(preset.graphicsDriver)) {
                    data.put("graphicsDriver", preset.graphicsDriver);
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

    private static File findExecutable(File dir, EngineDetector.Engine engine) {
        if (dir == null || !dir.isDirectory()) return null;
        String preferred = null;
        switch (engine) {
            case SIGLUS:    preferred = "SiglusEngine.exe"; break;
            case YURIS:     preferred = "YurisEngine.exe"; break;
            case ARTEMIS:   preferred = "REALLIVE.exe"; break;
            case CATSYSTEM2:preferred = "CatSystem2.exe"; break;
            default: break;
        }
        File[] files = dir.listFiles();
        if (files == null) return null;
        if (preferred != null) {
            for (File f : files) if (preferred.equalsIgnoreCase(f.getName())) return f;
        }
        for (File f : files) if (f.getName().toLowerCase().endsWith(".exe")) return f;
        return null;
    }

    private static void writeOverlay(File root, ImportResult result, File gameDir) {
        try {
            JSONObject o = new JSONObject();
            o.put("game_id", result.gameId);
            o.put("engine", result.engine.name());
            o.put("route", result.route.name());
            o.put("encrypted", result.encrypted);
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
