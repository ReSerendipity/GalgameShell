package com.winlator.galgame.engine;

import android.content.Context;

import com.winlator.galgame.EngineDetector;

/**
 * 引擎 → {@link GameEngine} 的<b>统一映射表</b>（替换原先只覆盖内置引擎的
 * {@link BuiltinEngineRegistry} 语义）。
 *
 * <p>三类去向：
 * <ol>
 *   <li><b>内置原生</b>：KIRIKIRI → Kirikiroid2，RENPY → Ren'Py 内置运行时；</li>
 *   <li><b>容器 / Wine</b>：SIGLUS / YURIS / ARTEMIS / CATSYSTEM2 / UNKNOWN
 *       → {@link WinlatorContainerEngine}（本 fork 的万能兜底）；</li>
 *   <li><b>外部 APK</b>：TYRANO / ONSCRIPTER / RPGMAKER* / WOLFRPG / PSP
 *       → 返回 null，仍由 {@link com.winlator.galgame.NativeRouteLauncher} 处理
 *       （已拍板：第三方 App 不纳入本抽象）。</li>
 * </ol>
 *
 * <p>{@code EngineDetector.Route} 枚举自本次起<b>只作检测优先级注释</b>，不再是分发开关：
 * 分发一律走本注册表。
 */
public final class GameEngineRegistry {

    private static final GameEngine[] ENGINES = {
            KrkrEngine.INSTANCE,
            RenPyEngine.INSTANCE,
            WinlatorContainerEngine.INSTANCE,
    };

    private GameEngineRegistry() {}

    /**
     * 查某引擎该用什么运行时。
     *
     * @return 有则非 null；返回值只说明「谁负责」，是否<b>当下就能跑</b>还要看
     *         {@link GameEngine#isAvailable}。第三方播放器路线返回 null。
     */
    public static GameEngine resolve(EngineDetector.Engine engine) {
        if (engine == null) return null;
        switch (engine) {
            case KIRIKIRI:    return KrkrEngine.INSTANCE;
            case RENPY:       return RenPyEngine.INSTANCE;
            case SIGLUS:
            case YURIS:
            case ARTEMIS:
            case CATSYSTEM2:
            case UNKNOWN:     return WinlatorContainerEngine.INSTANCE;
            default:          return null;   // 外部播放器路线，见类注释
        }
    }

    /** 全部一等引擎（供设置页 / 诊断展示）。 */
    public static GameEngine[] all() { return ENGINES.clone(); }

    /** 容器 / Wine 兜底引擎。需要「至少有个能跑的办法」时用它。 */
    public static GameEngine containerEngine() { return WinlatorContainerEngine.INSTANCE; }

    /** 该引擎当前是否能在自家 APK 里跑起来（无需第三方 App）。 */
    public static boolean isRunnable(Context context, EngineDetector.Engine engine) {
        GameEngine e = resolve(engine);
        return e != null && e.isAvailable(context);
    }

    /**
     * 把持久化下来的引擎名还原成引擎枚举。
     *
     * <p>持久化值来自 {@code galgame_overlay.json} / 容器 extra，可能是老版本写下的、
     * 也可能被手工改坏。越界值一律当作 UNKNOWN（走容器兜底），不要让 UI 崩。
     */
    public static EngineDetector.Engine parseEngine(String name) {
        if (name == null || name.isEmpty()) return EngineDetector.Engine.UNKNOWN;
        try {
            return EngineDetector.Engine.valueOf(name.trim().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            return EngineDetector.Engine.UNKNOWN;
        }
    }
}
