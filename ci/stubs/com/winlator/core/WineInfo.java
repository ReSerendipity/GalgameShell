package com.winlator.core;

import android.content.Context;

/**
 * CI 桩：WineInfo（com.winlator.core，真实类见 app/src/main/java/com/winlator/core/WineInfo.java）。
 * 仅保留 galgame 子树引用到的符号；真实类还实现 Parcelable，桩不需要。勿用于运行。
 */
public class WineInfo {
    public static final String MAIN_WINE_VERSION = "10.10";
    public static final WineInfo MAIN_WINE_INFO = new WineInfo();

    private WineInfo() {}

    public String identifier() { return ""; }

    public String fullVersion() { return ""; }

    public static WineInfo fromIdentifier(Context context, String identifier) { return null; }

    public static boolean isMainWineVersion(String wineVersion) { return false; }
}
