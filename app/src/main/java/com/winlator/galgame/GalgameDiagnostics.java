package com.winlator.galgame;

import android.content.Context;

import com.winlator.container.AudioDrivers;
import com.winlator.container.Container;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.GPUHelper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 诊断向导（Plan Part I §I.6 / §I.7 闪退排查）。
 *
 * 按「命中率」排序的检查项，全部为**只读检查 + 修复建议**，不擅自改配置：
 *   1) GPU 驱动（头号闪退原因：Adreno 应走 Turnip，Mali/麒麟应走 Vortek/VirGL）
 *   2) 日文区域 / 字体（乱码、豆腐块）
 *   3) 视频解码（OP/ED 黑屏）
 *   4) 32/64 位（读 PE 头 Machine 字段）
 *   5) 音频后端
 *   6) 加密包（A5：仅提示，不破解）
 *
 * 读取 P1/P2 写入的 galgame_overlay.json + Container 现配置，产出有序检查表。
 */
public final class GalgameDiagnostics {

    private GalgameDiagnostics() {}

    public enum Severity { OK, INFO, WARN, ERROR }

    /** 单条检查结果。 */
    public static final class Check {
        public final String id;
        public final Severity severity;
        public final String title;
        public final String detail;
        public final String fix;

        Check(String id, Severity severity, String title, String detail, String fix) {
            this.id = id;
            this.severity = severity;
            this.title = title;
            this.detail = detail;
            this.fix = fix;
        }
    }

    /** 检查报告。 */
    public static final class Report {
        public final List<Check> checks = new ArrayList<>();

        public int count(Severity s) {
            int c = 0;
            for (Check ch : checks) if (ch.severity == s) c++;
            return c;
        }

        public boolean hasProblems() {
            return count(Severity.WARN) + count(Severity.ERROR) > 0;
        }

        public String summary() {
            return "OK=" + count(Severity.OK) + " INFO=" + count(Severity.INFO)
                    + " WARN=" + count(Severity.WARN) + " ERROR=" + count(Severity.ERROR);
        }
    }

    /** 执行全部检查。 */
    public static Report run(Context context, Container container) {
        Report report = new Report();
        JSONObject overlay = readOverlay(container);

        checkGpu(context, container, report);
        checkLocaleFont(container, overlay, report);
        checkVideo(container, report);
        checkBitness(overlay, report);
        checkAudio(container, report);
        checkEncryption(overlay, report);

        return report;
    }

    // ---- 1) GPU 驱动 ----

    private static void checkGpu(Context context, Container container, Report r) {
        String configured = container != null ? container.getGraphicsDriver() : null;

        String soc;
        String recommended;
        try {
            short adreno = GPUHelper.getAdrenoModelId(context);
            String renderer = GPUHelper.glGetRenderer(context);
            if (adreno > 0) soc = "Adreno " + adreno;
            else if (renderer != null && !renderer.isEmpty()) soc = renderer;
            else soc = "未知 GPU";
            recommended = GraphicsDrivers.getDefaultDriver(context);
        }
        catch (Throwable t) {
            soc = "未知 GPU";
            recommended = GraphicsDrivers.VORTEK + "," + GraphicsDrivers.GLADIO;
        }

        if (configured == null || configured.isEmpty()) {
            r.checks.add(new Check("gpu_driver", Severity.OK, "GPU 驱动",
                    "GPU=" + soc + "；走官方自动选择（建议 " + recommended + "）",
                    ""));
            return;
        }

        String[] ids = GraphicsDrivers.parseIdentifiers(configured);
        String vk = ids.length > 0 ? GraphicsDrivers.getName(ids[0]) : "?";
        String gl = ids.length > 1 ? GraphicsDrivers.getName(ids[1]) : "?";

        r.checks.add(new Check("gpu_driver", Severity.INFO, "GPU 驱动",
                "GPU=" + soc + "；当前 Vulkan=" + vk + ", OpenGL=" + gl + "；建议=" + recommended,
                "若启动即黑屏/秒退，优先尝试把 Vulkan 切到 " + GraphicsDrivers.getName(GraphicsDrivers.TURNIP)
                        + "（Adreno）或 " + GraphicsDrivers.getName(GraphicsDrivers.VORTEK) + "（Mali/麒麟）"));
    }

    // ---- 2) 日文区域 / 字体 ----

    private static void checkLocaleFont(Container container, JSONObject overlay, Report r) {
        JSONObject loc = overlay != null ? overlay.optJSONObject("locale_injection") : null;

        String locale = container != null ? container.getExtra("galgame_locale", null) : null;
        if (locale == null && loc != null) locale = opt(loc, "locale");

        if (locale == null || !locale.startsWith("ja")) {
            r.checks.add(new Check("locale", Severity.WARN, "日文区域",
                    "未检测到 ja_JP 注入（当前：" + (locale == null ? "无" : locale) + "）",
                    "重新导入以注入 ja_JP.UTF-8；或在容器 EnvVars 手加 LC_ALL=ja_JP.UTF-8 LANG=ja_JP.UTF-8"));
        }
        else {
            r.checks.add(new Check("locale", Severity.OK, "日文区域", "已注入 " + locale, ""));
        }

        String font = container != null ? container.getExtra("galgame_font", null) : null;
        if ((font == null || font.isEmpty()) && loc != null) font = opt(loc, "font_face");

        if (font == null || font.isEmpty()) {
            r.checks.add(new Check("font", Severity.WARN, "日文字体",
                    "未注入日文字体，可能出现豆腐块（□□□）",
                    "把 .ttf/.otf 放入 " + GalgameFonts.dropInDir().getAbsolutePath() + " 后重新导入"));
        }
        else {
            r.checks.add(new Check("font", Severity.OK, "日文字体", "已注册字体：" + font, ""));
        }
    }

    // ---- 3) 视频解码 ----

    private static void checkVideo(Container container, Report r) {
        String wc = container != null ? container.getWinComponents() : null;
        boolean ds = GalgameVideoSupport.isDirectShowEnabled(wc);
        boolean wm = GalgameVideoSupport.isWmDecoderEnabled(wc);

        if (ds && wm) {
            r.checks.add(new Check("video", Severity.OK, "视频解码",
                    "DirectShow / wmdecoder 已开启（OP/ED 可播）", ""));
        }
        else {
            r.checks.add(new Check("video", Severity.WARN, "视频解码",
                    "directshow=" + (ds ? "1" : "0") + ", wmdecoder=" + (wm ? "1" : "0") + "，OP/ED 可能黑屏",
                    "重新导入以套用 P3 预设（强制 directshow=1 / wmdecoder=1）"));
        }
    }

    // ---- 4) 32/64 位 ----

    private static void checkBitness(JSONObject overlay, Report r) {
        String exePath = overlay != null ? opt(overlay, "exe") : "";
        if (exePath == null || exePath.isEmpty()) {
            r.checks.add(new Check("bitness", Severity.INFO, "32/64 位", "未记录启动 exe，跳过位宽检测", ""));
            return;
        }

        File exe = new File(exePath);
        int machine = peMachine(exe);

        String desc;
        Severity sev = Severity.OK;
        String fix = "";
        switch (machine) {
            case 0x014c: desc = "32 位 (x86)"; break;
            case 0x8664: desc = "64 位 (x64)"; break;
            case 0xaa64: desc = "ARM64"; break;
            default:
                desc = machine == -1 ? "无法读取" : "未知 (0x" + Integer.toHexString(machine) + ")";
                sev = Severity.INFO;
                fix = "无法解析 PE 头：exe 可能已被移动/删除，或不是合法 PE";
                break;
        }
        r.checks.add(new Check("bitness", sev, "32/64 位",
                "启动 exe：" + exe.getName() + " → " + desc, fix));
    }

    /** 读 PE 头 Machine 字段：0x014c=x86, 0x8664=x64, 0xaa64=ARM64；失败返回 -1。 */
    static int peMachine(File f) {
        if (f == null || !f.isFile()) return -1;
        try (InputStream in = new FileInputStream(f)) {
            byte[] dos = new byte[64];
            if (in.read(dos) < 64) return -1;
            if (dos[0] != 'M' || dos[1] != 'Z') return -1;

            int peOff = (dos[0x3C] & 0xFF) | ((dos[0x3D] & 0xFF) << 8)
                    | ((dos[0x3E] & 0xFF) << 16) | ((dos[0x3F] & 0xFF) << 24);
            if (peOff < 0) return -1;

            long skipped = 0;
            while (skipped < peOff) {
                long s = in.skip(peOff - skipped);
                if (s <= 0) break;
                skipped += s;
            }

            byte[] pe = new byte[6];
            if (in.read(pe) < 6) return -1;
            if (pe[0] != 'P' || pe[1] != 'E' || pe[2] != 0 || pe[3] != 0) return -1;
            return (pe[4] & 0xFF) | ((pe[5] & 0xFF) << 8);
        }
        catch (Exception e) {
            return -1;
        }
    }

    // ---- 5) 音频后端 ----

    private static void checkAudio(Container container, Report r) {
        String audio = container != null ? container.getAudioDriver() : null;
        String name = AudioDrivers.PULSEAUDIO.equals(audio) ? "PulseAudio"
                : (AudioDrivers.ALSA.equals(audio) ? "ALSA" : String.valueOf(audio));
        r.checks.add(new Check("audio", Severity.INFO, "音频后端",
                "当前：" + name,
                "若无声/爆音，尝试在容器设置里切换 ALSA ↔ PulseAudio"));
    }

    // ---- 6) 加密（A5） ----

    private static void checkEncryption(JSONObject overlay, Report r) {
        if (overlay == null) return;
        boolean encrypted = "true".equalsIgnoreCase(opt(overlay, "encrypted"));
        if (!encrypted) return;

        StringBuilder marks = new StringBuilder();
        org.json.JSONArray arr = overlay.optJSONArray("encryption_markers");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                if (marks.length() > 0) marks.append("; ");
                marks.append(arr.optString(i, ""));
            }
        }
        r.checks.add(new Check("encryption", Severity.WARN, "加密包（A5）",
                "检测到加密特征：" + marks,
                "本外壳仅检测提示，不提供破解（A5）；请使用正版/合法渠道"));
    }

    // ---- 工具 ----

    private static JSONObject readOverlay(Container container) {
        if (container == null) return null;
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
}
