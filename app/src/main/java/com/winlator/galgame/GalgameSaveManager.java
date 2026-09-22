package com.winlator.galgame;

import com.winlator.container.Container;
import com.winlator.core.FileUtils;
import com.winlator.core.WineRegistryEditor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * B4 / P4 存档持久化（Plan Part V）。
 * 全新模块，**绝不改 Container.DEFAULT_DRIVES**（零改铁律 §VIII.4）。
 *
 * <ul>
 *   <li><b>S: 常驻存档盘</b> → {@code INTERNAL_STORAGE/galgame-saves/<game>}
 *       = {@code /data/data/com.winlator/storage/galgame-saves/<game>}
 *       容器外 → 容器重置 / 重装存活（AC-12 / AC-13）。</li>
 *   <li><b>Shell-Folder 类</b>（Documents / AppData / Saved Games）：写 Wine
 *       {@code User Shell Folders} 注册表 → S:（P4）。</li>
 *   <li><b>便携存档类</b>：游戏目录存档子目录「先搬后链」→ S:（每次导入重建，P4；
 *       RK-09 对硬编码绝对路径引擎的兜底）。</li>
 *   <li><b>导出</b>（卸载/换机安全）：目录复制 S: → {@code Downloads/galgame-backup/<game>-<ts>}
 *       （AC-14）。</li>
 * </ul>
 *
 * 所有注册表/符号链接操作仅用官方公开 API（{@link WineRegistryEditor} / {@link FileUtils}）。
 */
public final class GalgameSaveManager {

    /** 容器外存档根（对应 S: 盘）。 */
    public static final String SAVE_ROOT =
            "/data/data/com.winlator/storage/galgame-saves";

    /** 常见引擎的「便携存档」子目录名（大小写/命名不一，逐一匹配）。 */
    private static final String[] PORTABLE_SAVE_DIRS = {
            "savedata", "save", "saves", "Savedata", "sav", "SaveData",
            "ysp_save", "save data", "savedata_", "Save"
    };

    private static final String SHELL_FOLDERS_KEY =
            "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders";

    private final String gameId;
    private final File gameSaveDir;
    private boolean autoBackup = true;

    public GalgameSaveManager(String gameId) {
        this.gameId = gameId;
        this.gameSaveDir = new File(SAVE_ROOT, gameId);
    }

    // ---- 基础 ----

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

    public File gameSaveDir() {
        return gameSaveDir;
    }

    public boolean isAutoBackup() {
        return autoBackup;
    }

    public void setAutoBackup(boolean on) {
        this.autoBackup = on;
    }

    // ---- P4-1：Shell Folder 重定向（Documents / AppData / Saved Games → S:）----

    /**
     * 把 Wine 的 User Shell Folders 重定向到 S:，使「文档类」存档落到常驻盘。
     * 同时建好 S: 上的对应子目录。返回是否成功。
     */
    public boolean redirectShellFolders(Container container) {
        if (container == null) return false;
        File userReg = new File(container.getRootDir(), ".wine/user.reg");

        String[][] folders = {
                {"Personal", "Documents"},
                {"My Documents", "Documents"},
                {"AppData", "AppData\\Roaming"},
                {"Saved Games", "SavedGames"},
        };

        try {
            if (!ensureSaveDir()) return false;
            for (String[] f : folders) {
                File sub = new File(gameSaveDir, f[1].replace("\\", "/"));
                if (!sub.isDirectory()) sub.mkdirs();
            }

            try (WineRegistryEditor reg = new WineRegistryEditor(userReg)) {
                for (String[] f : folders) {
                    reg.setStringValue(SHELL_FOLDERS_KEY, f[0], "S:\\" + f[1]);
                }
            }
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    // ---- P4-2：便携存档「先搬后链」 ----

    /**
     * 扫描游戏目录下的常见存档子目录，把内容搬到 S: 后，用符号链接指回 S:。
     * 采用「先搬后链」以避免丢档（对比直接替换为空链接）。
     *
     * @return 成功建立的符号链接数
     */
    public int symlinkPortableSaves(File gameDir) {
        if (gameDir == null || !gameDir.isDirectory() || !ensureSaveDir()) return 0;

        int linked = 0;
        for (String name : PORTABLE_SAVE_DIRS) {
            File dir = new File(gameDir, name);
            if (!dir.isDirectory()) continue;
            if (FileUtils.isSymlink(dir)) continue;   // 已链，跳过

            File target = new File(gameSaveDir, name);
            if (!target.isDirectory()) target.mkdirs();

            try {
                // 先搬（合并到 S:，不覆盖已有同名文件）
                mergeInto(dir, target);
                // 再链
                FileUtils.delete(dir);
                FileUtils.symlink(target, dir);
                linked++;
            }
            catch (Exception e) {
                // 单个目录失败不影响其它
            }
        }
        return linked;
    }

    // ---- 备份 / 恢复 ----

    /** 默认导出目录：Download/galgame-backup。 */
    public static File defaultBackupDir() {
        return new File(com.winlator.core.AppUtils.DIRECTORY_DOWNLOADS, "galgame-backup");
    }

    /**
     * 导出存档备份（AC-14）。落地为「目录复制」到 destDir/<gameId>-<ts>/。
     * 不抛异常，失败返回 false。（tar 需设备端二进制，留作后续增强。）
     */
    public boolean exportBackup(File destDir) {
        if (!ensureSaveDir() || destDir == null) return false;
        if (!destDir.isDirectory() && !destDir.mkdirs()) return false;

        String ts = String.valueOf(System.currentTimeMillis() / 1000);
        File target = new File(destDir, gameId + "-" + ts);
        try {
            copyDirectory(gameSaveDir, target);
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    /** 从备份目录恢复存档到 S: 盘（覆盖式合并）。 */
    public boolean restoreBackup(File backupDir) {
        if (backupDir == null || !backupDir.isDirectory()) return false;
        ensureSaveDir();
        try {
            copyDirectory(backupDir, gameSaveDir);
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }

    // ---- 文件工具 ----

    /** 把 src 内容合并进 dst（不覆盖 dst 已存在的同名文件）。 */
    private static void mergeInto(File src, File dst) throws IOException {
        if (!dst.isDirectory() && !dst.mkdirs()) throw new IOException("无法创建 " + dst);
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            File t = new File(dst, f.getName());
            if (f.isDirectory()) mergeInto(f, t);
            else if (!t.exists()) copyFile(f, t);
        }
    }

    private static void copyDirectory(File src, File dst) throws IOException {
        if (!dst.isDirectory() && !dst.mkdirs()) throw new IOException("无法创建 " + dst);
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
