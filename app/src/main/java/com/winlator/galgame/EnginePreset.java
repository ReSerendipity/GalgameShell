package com.winlator.galgame;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A-route 引擎预设（engine_presets.json 单条目，Plan Part IV §IV.6）。
 *
 * 仅描述「A 路由模拟兜底」时的可信默认值；不含任何对官方 Container.java 的改动。
 * 字段与 assets/engine_presets.json 的键一一对应（snake_case → camelCase 在此类内）。
 *
 * {@link #screenSize} 对应 {@code screen_size}：容器虚拟屏分辨率。留空即沿用官方
 * {@code Container.DEFAULT_SCREEN_SIZE}。给某引擎配大虚拟屏的原因是 TD-14——
 * KiriKiri 会把主窗口固定在 (152,152)，窗口尺寸撞上容器分辨率时画布会被裁掉一块。
 *
 * {@link #dxwrapper} 只接受 {@code wined3d} / {@code dxvk} 两个值：官方
 * {@code DXWrappers.parseIdentifier} 在容器创建与启动两处都会把其它值一律回落成
 * {@code Container.DEFAULT_DXWRAPPER}（=dxvk），写别的等于没写。
 * {@link #dxwrapperConfig} 对应 {@code dxwrapper_config}：官方 {@code DXWrappers.parseConfigs}
 * 按 {@code |} 切成两段（[0]=主 wrapper 配置，[1]=vkd3d 配置），段内是 {@code k=v,k=v}。
 * DDraw 时代的引擎靠 [0] 里的 {@code ddrawWrapper=cnc-ddraw} 换掉 ddraw.dll
 * （见 {@code XServerDisplayActivity.extractDXWrapperFiles}），所以尾部的 {@code |} 不能省。
 *
 * {@link #wineVersion} 对应 {@code wine_version}：非主版本 Wine 的 identifier
 * （如 {@code wine-9.22}）。只在设备上真装好该版本时才生效，否则回落主 Wine，
 * 判定见 {@link ImportFlow#resolveWineVersion}。
 */
public final class EnginePreset {

    public final String locale;          // ja_JP.UTF-8 等（日文化，A4）
    public final String font;            // 注入字体文件名（otf-ipafont.ttf 等）
    public final String fontFace;        // 注册到 Windows Fonts 键的字体名（IPAexGothic 等）
    public final String graphicsDriver;  // "default" 表示沿用 Container 默认
    public final String dxwrapper;       // wined3d / dxvk（仅此二值有效，见类注释）
    public final String dxwrapperConfig; // 主 wrapper 的配置段，如 ddrawWrapper=cnc-ddraw|
    public final String box64Preset;     // compatibility / performance / ...
    public final String wincomponents;   // direct3d=1,directshow=1,...
    public final String windowsVersion;  // win7 / winxp（落地到 Container.extraData）
    public final int startupSelection;   // 0 正常 / 1 必要 / 2 激进
    public final String env;             // 追加到 Container.envVars 的区域环境变量
    public final String screenSize;      // 容器虚拟屏，如 1920x1080；null 表示沿用 Container 默认
    public final String wineVersion;     // 非主版本 Wine 的 identifier，如 wine-9.22；null 表示用主 Wine

    private EnginePreset(String locale, String font, String fontFace, String graphicsDriver,
                         String dxwrapper, String dxwrapperConfig, String box64Preset,
                         String wincomponents, String windowsVersion, int startupSelection,
                         String env, String screenSize, String wineVersion) {
        this.locale = locale;
        this.font = font;
        this.fontFace = fontFace;
        this.graphicsDriver = graphicsDriver;
        this.dxwrapper = dxwrapper;
        this.dxwrapperConfig = dxwrapperConfig;
        this.box64Preset = box64Preset;
        this.wincomponents = wincomponents;
        this.windowsVersion = windowsVersion;
        this.startupSelection = startupSelection;
        this.env = env;
        this.screenSize = screenSize;
        this.wineVersion = wineVersion;
    }

    /** 从 engine_presets.json 的单个条目解析。缺失字段留 null（调用方按 Container 默认处理）。 */
    static EnginePreset fromJson(JSONObject o) throws JSONException {
        String locale = o.has("locale") ? o.getString("locale") : null;
        String font = o.has("font") ? o.getString("font") : null;
        String fontFace = o.has("font_face") ? o.getString("font_face") : null;
        String graphicsDriver = o.has("graphics_driver") ? o.getString("graphics_driver") : null;
        String dxwrapper = o.has("dxwrapper") ? o.getString("dxwrapper") : null;
        String dxwrapperConfig = o.has("dxwrapper_config") ? o.getString("dxwrapper_config") : null;
        String box64Preset = o.has("box64_preset") ? o.getString("box64_preset") : null;
        String wincomponents = o.has("wincomponents") ? o.getString("wincomponents") : null;
        String windowsVersion = o.has("windows_version") ? o.getString("windows_version") : null;
        int startupSelection = o.has("startup_selection") ? o.getInt("startup_selection") : 1;
        String env = o.has("env") ? o.getString("env") : null;
        String screenSize = o.has("screen_size") ? o.getString("screen_size") : null;
        String wineVersion = o.has("wine_version") ? o.getString("wine_version") : null;
        return new EnginePreset(locale, font, fontFace, graphicsDriver, dxwrapper, dxwrapperConfig,
                box64Preset, wincomponents, windowsVersion, startupSelection, env, screenSize,
                wineVersion);
    }

    /** 追加到 Container.envVars 的区域环境变量串（为空返回空串）。 */
    public String envVarsAppend() {
        return (env == null) ? "" : env;
    }
}
