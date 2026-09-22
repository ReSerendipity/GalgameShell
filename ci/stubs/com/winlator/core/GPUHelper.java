package com.winlator.core;
import android.content.Context;
public abstract class GPUHelper {
    public static String glGetRenderer(Context context) { return ""; }
    public static String glGetVendor(Context context) { return ""; }
    public static String glGetVersion(Context context) { return ""; }
    public static short getAdrenoModelId(Context context) { return 0; }
}
