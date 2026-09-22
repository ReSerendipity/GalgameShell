package com.winlator.galgame;

import java.io.File;

/**
 * B4 存档持久化（Plan Part V）。全新模块，绝不改 Container.DEFAULT_DRIVES。
 *
 * 方案（B4-P1）：
 *   - 常驻存档盘 S: → INTERNAL_STORAGE/galgame-saves/<game>
 *       = /data/data/com.winlator/storage/galgame-saves/<game>
 *     容器外 → 容器重置 / 重装存活（AC-12 / AC-13）。
 *   - Shell-Folder 类（Documents/AppData）：写 Wine User Shell Folders 注册表 Personal/AppData → S:
 *   - 便携存档类：游戏目录存档子目录符号链接 → S:（每次导入重建，映射存游戏元数据）
 *   - 导出（卸载/换机安全）：tar S: → Downloads/galgame-backup/<game>-<ts>.tar（AC-14）
 *   - 「退出自动备份」开关，默认开。
 *
 * RK-09：个别引擎硬编码绝对路径 → 复制 + 整目录符号链接兜底。
 */
public final class GalgameSaveManager {

    /** 容器外存档根（对应 S: 盘）。 */
    public static final String SAVE_ROOT =
            "/data/data/com.winlator/storage/galgame-saves";

    private final File gameSaveDir;

    public GalgameSaveManager(String gameId) {
        this.gameSaveDir = new File(SAVE_ROOT, gameId);
    }

    /** 确保 S: 盘目录存在（导入流调用）。 */
    public boolean ensureSaveDir() {
        return gameSaveDir.exists() || gameSaveDir.mkdirs();
    }

    /** 取得该游戏的 S: 盘路径（供导入流拼 drives 字段用）。 */
    public String savePath() {
        return gameSaveDir.getAbsolutePath();
    }

    // TODO P4:
    //  - redirectShellFolders(container, gameSaveDir)  // 写 Wine 注册表
    //  - symlinkPortableSaves(gameDir, gameSaveDir)    // 符号链接重建
    //  - exportBackup(): tar → Downloads/galgame-backup/<game>-<ts>.tar
    //  - restoreBackup(tar): 解包回 S:
    //  - setAutoBackup(boolean)

    private GalgameSaveManager() { throw new AssertionError(); }
}
