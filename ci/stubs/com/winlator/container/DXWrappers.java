package com.winlator.container;
public abstract class DXWrappers {
    public static final String WINED3D = "wined3d";
    public static final String DXVK = "dxvk";
    public static final String VKD3D = "vkd3d";
    public static final String CNC_DDRAW = "cnc-ddraw";
    public static final String D7VK = "d7vk";
    public static String getName(String identifier) { return ""; }
    public static String parseIdentifier(String dxwrapper) { return ""; }
}
