package com.winlator.galgame.ui;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * galgame 外壳设置（MVP）。
 *
 * 目前只有一项：<b>Tier-2 闭源原生播放器开关</b>（Plan Part III §III.4 / A4）。
 * 默认关闭 —— 闭源聚合器（JoiPlay / Tyranor）必须由用户显式开启并同意免责。
 */
public final class GalgameSettings {

    private static final String PREFS = "galgame_settings";
    private static final String KEY_TIER2 = "broute_tier2_enabled";

    private GalgameSettings() {}

    public static boolean isTier2Enabled(Context context) {
        return prefs(context).getBoolean(KEY_TIER2, false);
    }

    public static void setTier2Enabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_TIER2, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
