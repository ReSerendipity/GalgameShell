package com.winlator.container;
import android.content.Context;
public abstract class GraphicsDrivers {
    public static final String TURNIP = "turnip";
    public static final String VORTEK = "vortek";
    public static final String ZINK = "zink";
    public static final String VIRGL = "virgl";
    public static final String GLADIO = "gladio";
    public static final String DEFAULT_VULKAN_DRIVER = VORTEK;
    public static final String DEFAULT_OPENGL_DRIVER = GLADIO;
    public static String getName(String identifier) { return ""; }
    public static boolean isVulkanDriver(String identifier) { return false; }
    public static boolean isOpenGLDriver(String identifier) { return false; }
    public static String[] parseIdentifiers(String graphicsDriver) { return new String[0]; }
    public static String getDefaultDriver(Context context) { return ""; }
}
