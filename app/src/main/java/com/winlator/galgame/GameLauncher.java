package com.winlator.galgame;

import android.content.Context;
import android.util.Log;

import com.winlator.container.Container;
import com.winlator.galgame.engine.GameEngine;
import com.winlator.galgame.engine.GameEngineRegistry;
import com.winlator.galgame.engine.GameLaunchRequest;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 游戏启动的<b>统一入口</b>：把「这局到底该用什么引擎跑」的裁决从 UI 里彻底收出来。
 *
 * <p>在此之前，裁决逻辑是碎的：导入时 {@code BuiltinEngineRegistry.resolve} 优先、
 * 游戏库里却无条件 {@code startActivity(XServerDisplayActivity)}；而 GameHub/GameNative
 * 那套「点封面即玩」的体验要求<b>同一个游戏在任何入口都走同一条路</b>。
 *
 * <p>裁决顺序（越靠前越优先）：
 * <ol>
 *   <li><b>用户 override</b>：{@code preferredEngine}——手工指定的一律最大；</li>
 *   <li><b>启动前重检测</b>：对真正落盘的游戏目录再跑一次 {@link EngineDetector#detect}，
 *       命中非 UNKNOWN 且该内置引擎可用 → 升级为原生路线。这一步专治「导入时误判」：
 *       {@code detect()} 只扫根目录直接子项，xp3/rpa 藏在子目录时会被判 UNKNOWN，
 *       于是本可原生跑的游戏被塞进 wine 容器（这才是本仓库里真实发生的误分流）；</li>
 *   <li><b>登记值回落</b>：容器 extra / overlay 里记录的 {@code galgame_engine}；</li>
 *   <li><b>容器兜底</b>：有容器就用容器，没有才报错。</li>
 * </ol>
 *
 * <p>降级原则：选中的引擎不可用时，若存在容器则退回容器（有得玩胜过弹一条报错），
 * 否则才把 {@link GameEngine#unavailableReason} 抛给 UI 展示。
 */
public final class GameLauncher {

    private static final String TAG = "GameLauncher";

    private GameLauncher() {}

    /** 启动结果（{@link #message} 是可直接 Snackbar 展示的中文说明）。 */
    public static final class Result {
        public final boolean launched;
        public final String message;
        public final GameEngine engine;              // 实际生效的引擎
        public final EngineDetector.Engine kind;     // 实际生效的引擎类别

        private Result(boolean launched, String message, GameEngine engine,
                       EngineDetector.Engine kind) {
            this.launched = launched;
            this.message = message;
            this.engine = engine;
            this.kind = kind;
        }

        public static Result ok(GameEngine engine, EngineDetector.Engine kind) {
            return new Result(true, null, engine, kind);
        }

        public static Result fail(String message) {
            return new Result(false, message, null, null);
        }
    }

    /** 「运行方式」选项（id 为 null 表示自动裁决）。 */
    public static final class Option {
        public final String id;
        public final String label;

        Option(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    /**
     * 统一的启动裁决与执行。
     *
     * @param gamePath        游戏根目录（容器内或原生源目录），用于启动前重检测
     * @param container       对应容器；原生游戏为 null
     * @param declaredEngine  登记值（容器 extra / overlay 里的 galgame_engine），可为 null
     * @param preferredEngine 用户 override（{@link GameEngine#id()} 或引擎枚举名），可为 null
     */
    public static Result launch(Context context, File gamePath, Container container,
                                String declaredEngine, String preferredEngine) {
        if (context == null) return Result.fail("上下文缺失，无法启动");

        EngineDetector.Engine declared = GameEngineRegistry.parseEngine(declaredEngine);
        EngineDetector.Engine override = resolveOverride(preferredEngine);
        EngineDetector.Engine chosen = decide(gamePath, declared, override, context);

        GameEngine engine = GameEngineRegistry.resolve(chosen);
        boolean hasContainer = container != null;

        // 外部播放器路线（TYRANO/ONSCRIPTER/…）没有对应的一等引擎：有容器退回容器，
        // 否则明确告知 UI 需要走外部播放器，而不是假装原生能跑。
        if (engine == null) {
            if (hasContainer) engine = GameEngineRegistry.containerEngine();
            else return Result.fail("该引擎需要外部播放器，请从「导入游戏」入口重新唤起");
        }

        // 容器引擎但没有容器（典型：原生游戏条目被判成 UNKNOWN）→ 无法执行
        if (engine.requiresContainer() && !hasContainer) {
            return Result.fail("该游戏当前没有可用的 Winlator 容器，请用「导入游戏」重新导入");
        }

        if (!engine.isAvailable(context)) {
            String reason = engine.unavailableReason(context);
            if (hasContainer && !engine.requiresContainer()) {
                Log.w(TAG, engine.displayName() + " 不可用（" + reason + "），退回容器路线");
                engine = GameEngineRegistry.containerEngine();
            } else {
                return Result.fail(engine.displayName() + "：" + reason);
            }
        }

        GameLaunchRequest request = hasContainer
                ? GameLaunchRequest.forContainer(container, gamePath, chosen)
                : GameLaunchRequest.of(gamePath, chosen);

        boolean ok = engine.launch(context, request);
        Log.i(TAG, "launch via " + engine.id() + " kind=" + chosen + " ok=" + ok + " req=" + request);
        if (!ok) {
            // WinlatorContainerEngine 内部已把「exe 未落盘」拦掉了，这里给统一文案
            if (engine.requiresContainer()) {
                return Result.fail("启动文件缺失或不完整，请重新导入该游戏");
            }
            return Result.fail(engine.displayName() + " 启动失败");
        }
        return Result.ok(engine, chosen);
    }

    /**
     * 该游戏可选的运行方式列表（首个是「自动」）。
     *
     * @param autoLabel 「自动」项的文案（由 UI 从 galgame_strings.xml 取，本类不碰资源）
     */
    public static List<Option> options(boolean hasContainer, String autoLabel) {
        List<Option> out = new ArrayList<>();
        out.add(new Option(null, autoLabel != null ? autoLabel : "自动"));
        for (GameEngine e : GameEngineRegistry.all()) {
            if (e.requiresContainer() && !hasContainer) continue;
            out.add(new Option(e.id(), e.displayName()));
        }
        return out;
    }

    /** 把 override 值还原成引擎类别：既接受 {@link GameEngine#id()}，也接受引擎枚举名。 */
    private static EngineDetector.Engine resolveOverride(String preferredEngine) {
        if (preferredEngine == null || preferredEngine.trim().isEmpty()) return null;
        String raw = preferredEngine.trim();

        GameEngine byId = null;
        for (GameEngine e : GameEngineRegistry.all()) {
            if (e.id().equalsIgnoreCase(raw) || raw.equalsIgnoreCase(e.getClass().getSimpleName())) {
                byId = e;
                break;
            }
        }
        if (byId == null) {
            // 也可能是引擎枚举名（老数据 / 手工编辑）
            EngineDetector.Engine k = GameEngineRegistry.parseEngine(raw);
            return (k == EngineDetector.Engine.UNKNOWN) ? null : k;
        }
        EngineDetector.Engine kind = kindOf(byId);
        return (kind == EngineDetector.Engine.UNKNOWN) ? null : kind;
    }

    /** 反查某个引擎实现代表的是哪个检测类别（用于构造请求时的标注）。 */
    private static EngineDetector.Engine kindOf(GameEngine engine) {
        if (engine instanceof com.winlator.galgame.engine.KrkrEngine) return EngineDetector.Engine.KIRIKIRI;
        if (engine instanceof com.winlator.galgame.engine.RenPyEngine) return EngineDetector.Engine.RENPY;
        if (engine instanceof com.winlator.galgame.engine.WinlatorContainerEngine) return EngineDetector.Engine.UNKNOWN;
        return EngineDetector.Engine.UNKNOWN;
    }

    /**
     * 裁决最终引擎类别。
     *
     * <p>重检测只在能「升级」时生效（原登记为 UNKNOWN/Siglus 之类，却实测命中内嵌引擎），
     * 绝不反向降级——用户可能出于兼容性考虑就是想用 wine 跑。
     */
    private static EngineDetector.Engine decide(File gamePath, EngineDetector.Engine declared,
                                                EngineDetector.Engine override, Context context) {
        if (override != null) return override;

        EngineDetector.Engine detected = null;
        if (gamePath != null && gamePath.isDirectory()) {
            detected = EngineDetector.detect(gamePath);
        }

        boolean declaredIsNative = isNative(declared);
        if (detected != null && detected != EngineDetector.Engine.UNKNOWN
                && isNative(detected) && !declaredIsNative
                && GameEngineRegistry.isRunnable(context, detected)) {
            Log.i(TAG, "重检测升级：登记=" + declared + " 实测=" + detected + " dir=" + gamePath);
            return detected;
        }

        return (declared != null) ? declared : EngineDetector.Engine.UNKNOWN;
    }

    /** 是否有为首的内置原生实现（区别于容器兜底与外部播放器）。 */
    private static boolean isNative(EngineDetector.Engine engine) {
        if (engine == null) return false;
        GameEngine e = GameEngineRegistry.resolve(engine);
        return e != null && !e.requiresContainer();
    }
}
