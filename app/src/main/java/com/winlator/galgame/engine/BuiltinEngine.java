package com.winlator.galgame.engine;

import android.content.Context;

import java.io.File;

/**
 * **内置**原生引擎（区别于 {@link com.winlator.galgame.NativeRouteLauncher} 的「唤起外部 APK」）。
 *
 * 用户要求（明确否决外部 APK 方案）：检测到对应引擎后，**用集成在项目里的运行时直接跑**，
 * 不再安装第二个 App、不在第三方 App 里手选目录。本类即这条链路的抽象。
 *
 * 许可证硬约束（见 DOCS/native-engine-integration.md §2）：本仓库为 LGPL-2.1，
 * 静态并入的运行时**必须是宽松许可证**——目前只有 Kirikiroid2（BSD 3-Clause）达标；
 * EasyRPG(GPLv3) / ONScripter(GPLv2) / PPSSPP(GPLv2+) 会把整个项目拖成 GPL，故不提供内置实现，
 * JoiPlay / Tyranor 闭源，连源码都没有。
 *
 * <p><b>等价化改造（2026-10-05）</b>：本类同时实现 {@link GameEngine}，使内置原生引擎与
 * {@link WinlatorContainerEngine} 在同一个抽象下被选路。这里只多了几个把 final 字段暴露成
 * 接口方法的桥接方法，外加一个把 {@link GameLaunchRequest} 拆回 {@code (Context, File)}
 * 的委派，<b>既有的 {@code launch(Context, File)} 语义零改动</b>。
 */
public abstract class BuiltinEngine implements GameEngine {

    public final String id;
    public final String displayName;
    /** 上游项目地址（用于「关于 / 源码提供」义务）。 */
    public final String upstreamUrl;
    public final String license;

    protected BuiltinEngine(String id, String displayName, String upstreamUrl, String license) {
        this.id = id;
        this.displayName = displayName;
        this.upstreamUrl = upstreamUrl;
        this.license = license;
    }

    /**
     * 运行时是否已随本 APK 打包（native 库 + Java 层齐备）。
     * 必须**纯探测**，不得触发 {@code System.loadLibrary}（那是不可逆副作用）。
     */
    public abstract boolean isAvailable(Context context);

    /** 不可用时的原因（展示给使用者）。仅当 {@link #isAvailable} 为 false 时有意义。 */
    public abstract String unavailableReason(Context context);

    /**
     * 用本引擎启动游戏。
     * @param gamePath 游戏根目录（或入口文件，视引擎而定）
     * @return true 表示已成功拉起
     */
    public abstract boolean launch(Context context, File gamePath);

    // ---- GameEngine 桥接（字段 ↔ 接口方法同名不冲突，Java 二者命名空间独立）----

    @Override public String id() { return id; }
    @Override public String displayName() { return displayName; }
    @Override public String upstreamUrl() { return upstreamUrl; }
    @Override public String license() { return license; }

    /** 内置引擎不依赖容器，只取请求里的游戏路径。 */
    @Override
    public boolean launch(Context context, GameLaunchRequest request) {
        return request != null && launch(context, request.gamePath);
    }

    @Override
    public String toString() { return id; }
}
