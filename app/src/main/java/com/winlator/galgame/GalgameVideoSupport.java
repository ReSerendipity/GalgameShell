package com.winlator.galgame;

import com.winlator.container.Container;
import com.winlator.core.KeyValueSet;

/**
 * P3 视频解码（Plan Part II §II.2.3 / Part IV §IV.3）。
 *
 * 背景：官方 {@code Container.DEFAULT_WINCOMPONENTS} 里 {@code directshow=0}，而大量 galgame
 * 的 OP/ED 走 DirectShow / wmdecoder → 不开就是黑屏。A 路由容器统一强制开启：
 * <pre>
 *   direct3d=1    // DXVK 路径所需
 *   directshow=1  // OP/ED（DirectShow 滤镜链）
 *   wmdecoder=1   // WMV/WMA 解码
 *   xaudio=1      // 音频
 * </pre>
 *
 * 仅做「字符串级」合并，不触碰 Container 常量（零改铁律 §VIII.4）。
 */
public final class GalgameVideoSupport {

    private GalgameVideoSupport() {}

    /** 强制开启的视频相关 wincomponents。 */
    private static final String[][] REQUIRED = {
            {"direct3d", "1"},
            {"directshow", "1"},
            {"wmdecoder", "1"},
            {"xaudio", "1"},
    };

    /**
     * 在给定 wincomponents 基础上强制开启视频相关组件。
     *
     * @param base 预设或 {@code Container.DEFAULT_WINCOMPONENTS}；null/空则从默认起步
     * @return 合并后的 wincomponents 串
     */
    public static String ensureVideoComponents(String base) {
        String start = (base == null || base.trim().isEmpty())
                ? Container.DEFAULT_WINCOMPONENTS : base;
        KeyValueSet set = new KeyValueSet(start);
        for (String[] kv : REQUIRED) set.put(kv[0], kv[1]);
        return set.toString();
    }

    /** 是否已开启 DirectShow（OP/ED 的关键开关）。 */
    public static boolean isDirectShowEnabled(String wincomponents) {
        return new KeyValueSet(wincomponents).getBoolean("directshow", false);
    }

    /** 是否已开启 wmdecoder（WMV/WMA 解码）。 */
    public static boolean isWmDecoderEnabled(String wincomponents) {
        return new KeyValueSet(wincomponents).getBoolean("wmdecoder", false);
    }
}
