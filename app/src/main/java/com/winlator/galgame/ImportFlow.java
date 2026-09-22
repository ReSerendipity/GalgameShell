package com.winlator.galgame;

import java.io.File;

/**
 * P1 导入即玩核心（Plan Part VIII §VIII.3 / A2+A3）。
 *
 * 流程：选文件夹 → EngineDetector.detect → 建「每游戏独立容器」(A2)
 *      → 默认「复制(可写)」(A3) → 用 engine_presets.json 注入预设 → 进阅读。
 *
 * 与官方隔离：容器创建/编辑走 ContainerDetailFragment（拼 drives/预设，保留上游 + 回调追加），
 * 本类只负责「galgame 专属编排」，不直接改 Container.java 常量。
 *
 * RK-11 加密检测并入此流程：detect 返回 isEncrypted → 标记暂不支持，不破解（A5）。
 */
public final class ImportFlow {

    private ImportFlow() {}

    /**
     * 执行一次导入。
     * @param sourceFolder 用户选中的游戏源文件夹
     * @param gameId       游戏唯一 ID（用于容器命名 + S: 盘子目录，A2 兜底）
     * @return 导入结果（容器路径 / 路由 / 是否加密）
     */
    public static ImportResult run(File sourceFolder, String gameId) {
        EngineDetector.Engine engine = EngineDetector.detect(sourceFolder);

        // A2 每游戏容器：rootDir = homeDir/<USER>-<id>，<game> 子目录命名兜底
        // A3 默认复制（可写）：把 sourceFolder 拷入容器可写位置（规避引用挂载符号链接死结，RK-06）
        // B4 挂 S: 盘：GalgameSaveManager.savePath() 拼进 drives 字段（不碰 DEFAULT_DRIVES）
        // 预设注入：读 assets/engine_presets.json 对应 engine 条目

        // TODO P1: 实现上述编排；返回 ImportResult
        return new ImportResult(engine, gameId, /* encrypted= */ false);
    }

    public static final class ImportResult {
        public final EngineDetector.Engine engine;
        public final String gameId;
        public final boolean encrypted;

        ImportResult(EngineDetector.Engine engine, String gameId, boolean encrypted) {
            this.engine = engine;
            this.gameId = gameId;
            this.encrypted = encrypted;
        }
    }
}
