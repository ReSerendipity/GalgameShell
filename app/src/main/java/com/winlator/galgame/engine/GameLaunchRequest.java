package com.winlator.galgame.engine;

import com.winlator.container.Container;
import com.winlator.galgame.EngineDetector;

import java.io.File;

/**
 * 一次启动请求的上下文（值对象，不可变）。
 *
 * <p>把「游戏在哪」「用什么容器」「导入时登记的引擎」三件事打包，好让
 * {@link GameEngine#launch} 只有一个参数：内置引擎只消费 {@link #gamePath}，
 * 容器引擎消费 {@link #container} / {@link #containerId}，各取所需。
 *
 * <p>{@link #engine} 是<b>导入时登记</b>的引擎，未必等于真相——游戏文件可能已被移动、
 * 或当时因 {@code detect()} 只看根目录而误判成 UNKNOWN。调用方应在必要处
 * 先重检测再构造本请求，参见 {@link GameEngineRegistry} 的使用约定。
 */
public final class GameLaunchRequest {

    /** 游戏根目录（或入口文件，视引擎而定）。 */
    public final File gamePath;

    /** galgame 容器；非容器路线为 null。 */
    public final Container container;

    /** 容器 id；无容器时为 0。 */
    public final long containerId;

    /** 导入时登记的引擎（可为空，表示未登记）。 */
    public final EngineDetector.Engine engine;

    private GameLaunchRequest(File gamePath, Container container, long containerId,
                              EngineDetector.Engine engine) {
        this.gamePath = gamePath;
        this.container = container;
        this.containerId = containerId;
        this.engine = engine;
    }

    /** 纯原生路线：只有游戏路径。 */
    public static GameLaunchRequest of(File gamePath) {
        return new GameLaunchRequest(gamePath, null, 0L, null);
    }

    /** 纯原生路线：带引擎标注（供日志 / 索引回填用）。 */
    public static GameLaunchRequest of(File gamePath, EngineDetector.Engine engine) {
        return new GameLaunchRequest(gamePath, null, 0L, engine);
    }

    /**
     * 容器路线：带容器与其登记引擎。
     *
     * @param gamePath 容器内游戏目录（{@code .wine/drive_c/galgame/<gameId>}），可为 null
     */
    public static GameLaunchRequest forContainer(Container container, File gamePath,
                                                 EngineDetector.Engine engine) {
        long id = (container == null) ? 0L : container.id;
        return new GameLaunchRequest(gamePath, container, id, engine);
    }

    /** 容器路线：只有 id（拿不到 Container 对象时的兜底）。 */
    public static GameLaunchRequest forContainerId(long containerId, EngineDetector.Engine engine) {
        return new GameLaunchRequest(null, null, containerId, engine);
    }

    /** 补上重新检测后的引擎（返回新对象，本对象不可变）。 */
    public GameLaunchRequest withEngine(EngineDetector.Engine newEngine) {
        if (newEngine == engine) return this;
        return new GameLaunchRequest(gamePath, container, containerId, newEngine);
    }

    @Override
    public String toString() {
        return "GameLaunchRequest{engine=" + engine
                + ", containerId=" + containerId
                + ", gamePath=" + (gamePath == null ? "null" : gamePath.getAbsolutePath()) + "}";
    }
}
