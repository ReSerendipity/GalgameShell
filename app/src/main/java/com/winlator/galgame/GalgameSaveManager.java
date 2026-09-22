package com.winlator.galgame;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * B4 存档持久化（Plan Part V）。全新模块，绝不改 Container.DEFAULT_DRIVES。
 *
 * 方案（B4-P1）：
 *   - 常驻存档盘 S: → INTERNAL_STORAGE/galgame-saves/<game>
 *       = /data/data/com.winlator/storage/galgame-saves/<game>
 *     容器外 → 容器重置 / 重装存活（AC-12 / AC-13）。
 *   - Shell-Folder 类（Documents/AppData）：写 Wine User Shell Folders 注册表 Personal/AppData → S:（P4）
 *   - 便携存档类：游戏目录存档子目录符号链接 → S:（每次导入重建，P4）
 *   - 导出（卸载/换机安全）：tar/复制 S: → Downloads/galgame-backup/<game>-<ts>（AC-14，P4 落地为目录复制）
 *   - 「退出自动备份」开关，默认开。
 *
 * RK-09：个别引擎硬编码绝对路径 → 复制 + 整目录符号链接兜底（P4）。
 */
public final class GalgameSaveManager {

    /** 容器外存档根（对应 S: 盘）。 */
    public static final String SAVE_ROOT =
            "/data/data/com.winlator/storage/galgame-saves";

    private final File gameSaveDir;
    private boolean autoBackup = true;

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

    /** S: 盘驱动器描述串（"S:<绝对路径>"），拼进 Container.drives。 */
    public String saveDriveSpec() {
        return "S:" + savePath();
    }

    public boolean isAutoBackup() {
        return autoBackup;
    }

    public void setAutoBackup(boolean on) {
        this.autoBackup = on;
    }

    /**
     * 导出存档备份（AC-14）。P4 落地为「目录复制」到 destDir/<gameId>-<ts>/，
     * 后续可替换为 tar（设备上 `tar` 二进制或 java 归档）。不抛异常，失败返回 false。
     */
    public boolean exportBackup(File destDir) {
        if (!ensureSaveDir() || destDir == null) return false;
        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        File target = new File(destDir, gameSaveDir.getName() + "-" + ts);
        try {
            copyDirectory(gameSaveDir, target);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 从备份目录恢复存档到 S: 盘（覆盖式）。 */
    public boolean restoreBackup(File backupDir) {
        if (backupDir == null || !backupDir.isDirectory()) return false;
        ensureSaveDir();
        try {
            copyDirectory(backupDir, gameSaveDir);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // P4 TODO（需 Wine 注册表 / root 符号链接，本环境无法验证，保留接缝）：
    //   - redirectShellFolders(Container c)：写 Wine User Shell Folders 注册表
    //     Personal/AppData/SavedGames → S:，使文档类存档落到常驻盘。
    //   - symlinkPortableSaves(File gameDir)：把游戏目录内存档子目录符号链接 → S:
    //     （RK-09 兜底：对硬编码绝对路径的引擎）。
    //   - setAutoBackup 的「退出自动备份」由 Container 退出钩子调用 exportBackup。
    // 这些方法不在此实现，避免引入未经官方验证的注册表/root 操作。
    // ------------------------------------------------------------------

    private static void copyDirectory(File src, File dst) throws IOException {
        if (!dst.exists() && !dst.mkdirs()) throw new IOException("无法创建 " + dst);
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            File t = new File(dst, f.getName());
            if (f.isDirectory()) copyDirectory(f, t);
            else copyFile(f, t);
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private GalgameSaveManager() { throw new AssertionError(); }
}
