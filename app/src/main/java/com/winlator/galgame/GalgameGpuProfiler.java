package com.winlator.galgame;

import android.content.Context;
import android.os.Build;

import com.winlator.container.GraphicsDrivers;
import com.winlator.core.GPUHelper;

import java.util.Locale;

/**
 * M2 GPU 自适应调优（借鉴 WinlatorMali / GameNative Mali 优化思路，产品化）。
 *
 * 在导入阶段**自动**为 Mali（联发科天玑 / 麒麟 Xclipse）设备选择 Vortek 图形驱动，
 * 因为 Turnip 是 Adreno 专用、在 Mali 上会导致黑屏/秒退（见 learning-projects.md
 * 「WinlatorMali 条目」与 GalgameDiagnostics 闪退排查）；**同时**为 Adreno 设备选择
 * Turnip（即 Winlator 官方 Adreno 默认驱动 {@code turnip,gladio}），避免沿用 stock
 * 默认 {@code vortek,gladio}（Mali 驱动）在 Adreno 上误用导致渲染失败。其它（未知）
 * GPU 不强制，沿用官方默认。
 *
 * 检测用**多信号启发式**，不依赖 GL 上下文（导入期不一定有），失败也有兜底：
 *   1) {@link GPUHelper#getAdrenoModelId}（Adreno 专用）
 *   2) {@link GPUHelper#glGetRenderer}（若可用）
 *   3) {@link Build} 的 HARDWARE / SOC_MODEL / BOARD / MANUFACTURER 关键词
 *
 * 零改铁律：只读系统信息，不改任何官方类；仅向 ImportFlow 提供「推荐图形驱动」字符串。
 */
public final class GalgameGpuProfiler {

    private GalgameGpuProfiler() {}

    public enum Vendor { ADRENO, MALI, OTHER }

    /** 检测结果。 */
    public static final class Profile {
        public final Vendor vendor;
        public final String model;
        /** 推荐给本设备的图形驱动串（Vulkan,OpenGL）；为 null 表示沿用预设/官方默认。 */
        public final String recommendedGraphicsDriver;

        Profile(Vendor vendor, String model, String recommendedGraphicsDriver) {
            this.vendor = vendor;
            this.model = model;
            this.recommendedGraphicsDriver = recommendedGraphicsDriver;
        }

        public boolean isMali() { return vendor == Vendor.MALI; }
    }

    /**
     * 检测当前设备 GPU。
     * @param context 可为 null（仅用 Build 线索，跳过 GPUHelper）
     */
    public static Profile detect(Context context) {
        Vendor vendor = Vendor.OTHER;
        String model = "";

        try {
            // 1) Adreno 专用 id
            if (context != null) {
                short adreno = GPUHelper.getAdrenoModelId(context);
                if (adreno > 0) {
                    vendor = Vendor.ADRENO;
                    model = "Adreno " + adreno;
                }
            }
        } catch (Throwable ignored) { /* GPUHelper 可能需 GL 上下文，失败忽略 */ }

        // 2) renderer 字符串（若 1) 没命中）
        if (vendor == Vendor.OTHER && context != null) {
            try {
                String renderer = GPUHelper.glGetRenderer(context);
                if (renderer != null && !renderer.isEmpty()) {
                    model = renderer;
                    String r = renderer.toLowerCase(Locale.ROOT);
                    if (r.contains("adreno") || r.contains("qualcomm")) vendor = Vendor.ADRENO;
                    else if (r.contains("mali") || r.contains("panfrost")) vendor = Vendor.MALI;
                }
            } catch (Throwable ignored) { /* 同上 */ }
        }

        // 3) Build 线索兜底（无需 GL 上下文）
        if (vendor == Vendor.OTHER) {
            String hw = (Build.HARDWARE + " " + Build.SOC_MODEL + " " + Build.BOARD
                    + " " + Build.MANUFACTURER).toLowerCase(Locale.ROOT);
            model = Build.SOC_MODEL;
            if (hw.contains("qcom") || hw.contains("snapdragon") || hw.contains("adreno")) {
                vendor = Vendor.ADRENO;
            } else if (hw.contains("mt") || hw.contains("mediatek") || hw.contains("dimensity")
                    || hw.contains("exynos") || hw.contains("kirin") || hw.contains("hi")) {
                // 麒麟/Exynos Xclipse 的 GPU 均为 Mali 架构
                vendor = Vendor.MALI;
            }
        }

        // 推荐驱动：
        //  - Mali    → Vortek（Mali/MediaTek/Xclipse 优化，禁 Turnip）
        //  - Adreno  → Turnip（Winlator 官方 Adreno 默认，禁在 Adreno 上误用 Mali 的 Vortek）
        //  - 其它    → 不强制，沿用官方默认（stock 为 vortek,gladio）
        String driver = null;
        if (vendor == Vendor.MALI) {
            driver = GraphicsDrivers.VORTEK + "," + GraphicsDrivers.GLADIO;
        } else if (vendor == Vendor.ADRENO) {
            driver = GraphicsDrivers.TURNIP + "," + GraphicsDrivers.GLADIO;
        }

        return new Profile(vendor, model, driver);
    }

    /**
     * 决定导入时实际使用的图形驱动：
     * 若 preset 已显式指定（非 default/null）则沿用；否则 Mali 设备套用推荐 Vortek。
     *
     * @param presetDriver 来自 engine_presets.json 的 graphics_driver（可能为 null/"default"）
     * @param profile      设备检测结果
     * @return 最终图形驱动串（可能为 null，交由官方默认）
     */
    public static String resolveGraphicsDriver(String presetDriver, Profile profile) {
        if (presetDriver != null && !presetDriver.isEmpty()
                && !"default".equalsIgnoreCase(presetDriver)) {
            return presetDriver;
        }
        return profile != null ? profile.recommendedGraphicsDriver : null;
    }
}
