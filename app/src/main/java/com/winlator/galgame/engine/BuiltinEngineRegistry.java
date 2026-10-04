package com.winlator.galgame.engine;

import android.content.Context;

import com.winlator.galgame.EngineDetector;

/**
 * 引擎 → 内置运行时的映射表。
 *
 * 收录判据只有一条：**许可证允许静态并入 LGPL-2.1 项目**（详见 DOCS/native-engine-integration.md §2）。
 * 目前全域只有 Kirikiri 满足，故映射表里只有一项；其余引擎继续走
 * {@link com.winlator.galgame.NativeRouteLauncher}（外部 APK 唤起）或 A 路由（wine/box64）。
 */
public final class BuiltinEngineRegistry {

    private static final BuiltinEngine[] ENGINES = { KrkrEngine.INSTANCE, RenPyEngine.INSTANCE };

    private BuiltinEngineRegistry() {}

    /**
     * 查某引擎的内置运行时。
     * @return 有则NonNull；但该运行时**是否已随包打包**要看 {@link BuiltinEngine#isAvailable}
     */
    public static BuiltinEngine resolve(EngineDetector.Engine engine) {
        if (engine == null) return null;
        switch (engine) {
            case KIRIKIRI: return KrkrEngine.INSTANCE;
            case RENPY:    return RenPyEngine.INSTANCE;
            default:       return null;   // 其余均有许可证/源码障碍，见类注释
        }
    }

    /** 全部已登记的内置引擎（供设置页 / 诊断展示）。 */
    public static BuiltinEngine[] all() { return ENGINES.clone(); }

    /** 该引擎当前是否能「直接用集成的引擎跑」（免装 APK）。 */
    public static boolean isRunnable(Context context, EngineDetector.Engine engine) {
        BuiltinEngine e = resolve(engine);
        return e != null && e.isAvailable(context);
    }
}
