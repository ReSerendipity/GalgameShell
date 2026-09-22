package com.winlator.galgame;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A-route 引擎预设（engine_presets.json 单条目，Plan Part IV §IV.6）。
 *
 * 仅描述「A 路由模拟兜底」时的可信默认值；不含任何对官方 Container.java 的改动。
 * 字段与 assets/engine_presets.json 的键一一对应（snake_case → camelCase 在此类内）。
 */
public final class EnginePreset {

    public final String locale;          // ja_JP.UTF-8 等（日文化，A4）
    public final String font;            // 注入字体文件名（otf-ipafont.ttf 等）
    public final String fontFace;        // 注册到 Windows Fonts 键的字体名（IPAexGothic 等）
    public final String graphicsDriver;  // "default" 表示沿用 Container 默认
    public final String dxwrapper;       // dxvk / d8vk / ...
    public final String box64Preset;     // compatibility / performance / ...
    public final String wincomponents;   // direct3d=1,directshow=1,...
    public final String windowsVersion;  // win7 / winxp（落地到 Container.extraData）
    public final int startupSelection;   // 0 正常 / 1 必要 / 2 激进
    public final String env;             // 追加到 Container.envVars 的区域环境变量

    private EnginePreset(String locale, String font, String fontFace, String graphicsDriver,
                         String dxwrapper, String box64Preset, String wincomponents,
                         String windowsVersion, int startupSelection, String env) {
        this.locale = locale;
        this.font = font;
        this.fontFace = fontFace;
        this.graphicsDriver = graphicsDriver;
        this.dxwrapper = dxwrapper;
        this.box64Preset = box64Preset;
        this.wincomponents = wincomponents;
        this.windowsVersion = windowsVersion;
        this.startupSelection = startupSelection;
        this.env = env;
    }

    /** 从 engine_presets.json 的单个条目解析。缺失字段留 null（调用方按 Container 默认处理）。 */
    static EnginePreset fromJson(JSONObject o) throws JSONException {
        String locale = o.has("locale") ? o.getString("locale") : null;
        String font = o.has("font") ? o.getString("font") : null;
        String fontFace = o.has("font_face") ? o.getString("font_face") : null;
        String graphicsDriver = o.has("graphics_driver") ? o.getString("graphics_driver") : null;
        String dxwrapper = o.has("dxwrapper") ? o.getString("dxwrapper") : null;
        String box64Preset = o.has("box64_preset") ? o.getString("box64_preset") : null;
        String wincomponents = o.has("wincomponents") ? o.getString("wincomponents") : null;
        String windowsVersion = o.has("windows_version") ? o.getString("windows_version") : null;
        int startupSelection = o.has("startup_selection") ? o.getInt("startup_selection") : 1;
        String env = o.has("env") ? o.getString("env") : null;
        return new EnginePreset(locale, font, fontFace, graphicsDriver, dxwrapper, box64Preset,
                wincomponents, windowsVersion, startupSelection, env);
    }

    /** 追加到 Container.envVars 的区域环境变量串（为空返回空串）。 */
    public String envVarsAppend() {
        return (env == null) ? "" : env;
    }
}
