package com.winlator.galgame;

import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * P5 B 路由唤起（Plan Part III §III.3/§III.4 / A4）。
 *
 * 关键事实（已核实）：**无一款 B 播放器提供「传路径即一键启动」的公开 Intent**，
 * 且 Android 11+ scoped storage 会限制直读外部目录。故本类做三件事：
 * <ol>
 *   <li>按引擎解析目标播放器，并按 <b>Tier</b> 分层：
 *       Tier-1 开源（Kirikiroid2 / ONScripter）默认可用；
 *       Tier-2 闭源聚合器（JoiPlay / Tyranor）必须显式开启并同意免责。</li>
 *   <li>{@link #plan} 产出可执行计划（是否已安装 / 是否放行 / 是否需免责 / 需预置的扫描目录）。</li>
 *   <li>{@link #prepareScanDir} 为「扫描固定目录」型播放器（ONScripter / Tyranor）预置游戏副本；
 *       {@link #launch} 仅唤起 App，具体选目录由用户在 App 内完成（RK-04 已知限制）。</li>
 * </ol>
 *
 * 绝不内嵌任何第三方播放器，仅按需唤起（A4）。
 */
public final class NativeRouteLauncher {

    private NativeRouteLauncher() {}

    public static final int TIER_OPEN_SOURCE = 1;   // 开源可信，默认推荐
    public static final int TIER_CLOSED = 2;        // 闭源聚合器，需显式开启 + 免责

    /** 一个原生播放器目标。 */
    public static final class Target {
        public final String id;
        public final String packageName;
        public final String displayName;
        public final int tier;
        public final String scanDir;   // 约定扫描目录（相对 /sdcard，可为 null）
        public final String note;

        Target(String id, String packageName, String displayName, int tier, String scanDir, String note) {
            this.id = id;
            this.packageName = packageName;
            this.displayName = displayName;
            this.tier = tier;
            this.scanDir = scanDir;
            this.note = note;
        }
    }

    public static final Target KIRIKIROID2 = new Target(
            "kirikiroid2", "org.tvp.kirikiri2", "Kirikiroid2", TIER_OPEN_SOURCE, null,
            "在 App 内点选 data.xp3 启动；支持解密");
    public static final Target ONSCRIPTER = new Target(
            "onscripter", "jp.ogapee.onscripter.release", "ONScripter", TIER_OPEN_SOURCE, "ons",
            "需把游戏放 /sdcard/ons/<game>/ 且含 default.ttf");
    public static final Target JOIPLAY = new Target(
            "joiplay", "cyou.joiplay", "JoiPlay（需 Ren'Py 插件）", TIER_CLOSED, null,
            "闭源聚合器：App 内 Add Game 选择游戏目录");
    public static final Target TYRANOR = new Target(
            "tyranor", "com.akira.tyranoemu", "Tyranor", TIER_CLOSED, "Tyranor",
            "闭源聚合器：游戏放入 /sdcard/Tyranor/ 后 App 内添加");

    /** 按引擎解析目标播放器；无原生播放器返回 null（只能 A 路由）。 */
    public static Target resolve(EngineDetector.Engine engine) {
        switch (engine) {
            case KIRIKIRI:    return KIRIKIROID2;
            case ONSCRIPTER:  return ONSCRIPTER;
            case RENPY:       return JOIPLAY;
            case TYRANO:      return TYRANOR;
            case ARTEMIS:     return TYRANOR;   // 可走 Tyranor（B），亦可 A
            default:          return null;      // Siglus/YU-RIS/CatSystem2/Unknown → A
        }
    }

    /** 执行计划。 */
    public static final class Plan {
        public final Target target;
        public final boolean installed;
        public final boolean allowed;          // Tier-2 是否已放行
        public final boolean needsDisclaimer;  // 是否需要展示免责
        public final String message;
        public final File scanDir;             // 需预置的扫描目录（可为 null）

        Plan(Target target, boolean installed, boolean allowed, boolean needsDisclaimer,
             String message, File scanDir) {
            this.target = target;
            this.installed = installed;
            this.allowed = allowed;
            this.needsDisclaimer = needsDisclaimer;
            this.message = message;
            this.scanDir = scanDir;
        }

        public boolean ready() {
            return target != null && installed && allowed;
        }
    }

    /**
     * 生成执行计划。
     *
     * @param tier2Enabled 用户在设置里是否已显式开启 Tier-2 闭源播放器（并同意免责）
     */
    public static Plan plan(Context context, EngineDetector.Engine engine, boolean tier2Enabled) {
        Target t = resolve(engine);
        if (t == null) {
            return new Plan(null, false, false, false,
                    "该引擎无原生播放器，请使用 A 路由（Winlator 内核）", null);
        }

        boolean allowed = t.tier == TIER_OPEN_SOURCE || tier2Enabled;
        boolean installed = isInstalled(context, t);
        boolean needsDisclaimer = t.tier == TIER_CLOSED;

        File scanDir = (t.scanDir == null || t.scanDir.isEmpty())
                ? null : new File("/sdcard", t.scanDir);

        String message;
        if (!allowed) {
            message = "Tier-2 闭源播放器（" + t.displayName + "）需在设置中显式开启并同意免责声明";
        }
        else if (!installed) {
            message = "未检测到 " + t.displayName + "，请先安装（" + t.packageName + "）";
        }
        else if (scanDir != null) {
            message = "将把游戏复制到 " + scanDir.getAbsolutePath() + " 后唤起 " + t.displayName;
        }
        else {
            message = "将唤起 " + t.displayName + "，请在 App 内选择游戏目录";
        }

        return new Plan(t, installed, allowed, needsDisclaimer, message, scanDir);
    }

    /** 目标播放器是否已安装。 */
    public static boolean isInstalled(Context context, Target target) {
        if (context == null || target == null) return false;
        try {
            return context.getPackageManager().getLaunchIntentForPackage(target.packageName) != null;
        }
        catch (Exception e) {
            return false;
        }
    }

    /** 仅唤起目标 App（无公开一键 Intent，具体加载由用户在 App 内完成）。 */
    public static boolean launch(Context context, Target target) {
        if (context == null || target == null) return false;
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(target.packageName);
            if (intent == null) return false;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * 为「扫描固定目录」型播放器预置游戏副本（ONScripter / Tyranor）。
     *
     * @return 目标目录；未预置返回 null
     */
    public static File prepareScanDir(File gameDir, File scanDir, String gameId) {
        if (gameDir == null || !gameDir.isDirectory() || scanDir == null) return null;
        File dst = new File(scanDir, gameId);
        try {
            copyDirectory(gameDir, dst);
            return dst;
        }
        catch (Exception e) {
            return null;
        }
    }

    private static void copyDirectory(File src, File dst) throws Exception {
        if (!dst.isDirectory() && !dst.mkdirs()) throw new Exception("无法创建 " + dst);
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            File t = new File(dst, f.getName());
            if (f.isDirectory()) copyDirectory(f, t);
            else copyFile(f, t);
        }
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
}
