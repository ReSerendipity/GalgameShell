package com.winlator.galgame;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 原生引擎游戏的「收纳库」（2026-10-25 第七轮，健壮性决策）。
 *
 * <p>背景：B 路由（Kirikiroid2 / Ren'Py 原生引擎）此前直接<b>引用</b>用户选中的源目录，
 * 用户在库外的任何改动（改名/移动/部分删除/被相册类工具清理）都会让游戏损坏且无从恢复。
 * 现改为导入时把源目录完整复制进应用私有目录，游戏本体与容器路线一样由本程序全权管理。
 *
 * <p>存放位置：{@code getFilesDir()/galgame-native/<gameId>}——应用私有空间，
 * 卸载即随应用清除，其他 App 与用户文件管理器均不可见不可写。
 *
 * <p>同时收敛了此前的一个健壮性隐患：KR2 的 {@code getWritablePath()} 会跟随
 * {@code .preference} 配置在<b>游戏目录</b>里创建 {@code files/}、{@code .preference/}
 * 运行时目录（第五轮已证它们会被再次导入误判成游戏）。收纳库同样持有这两个目录的
 * 创建权，彻底杜绝运行时产物写进用户可见空间。
 */
public final class GalgameNativeStore {

    private static final String STORE_DIR = "galgame-native";

    private GalgameNativeStore() {}

    /** 收纳库根（应用私有）。 */
    public static File root(Context context) {
        return new File(context.getFilesDir(), STORE_DIR);
    }

    /** 某游戏的收纳目录。 */
    public static File gameDir(Context context, String gameId) {
        return new File(root(context), gameId);
    }

    /**
     * 收纳（复制）一个游戏目录。目标已存在时视为已收纳，直接返回现有目录
     * （幂等；重复导入同一游戏不会重复占空间——调用方应先查 {@link #isStored}）。
     *
     * @return 收纳后的游戏目录
     * @throws IOException 复制失败（磁盘满/源不可读）；已复制的部分保留，便于重试续拷
     */
    public static File store(Context context, String gameId, File sourceDir,
                             ImportFlow.ProgressListener progress) throws IOException {
        File target = gameDir(context, gameId);
        if (target.isDirectory() && target.list() != null && target.list().length > 0) {
            return target;
        }
        if (sourceDir == null || !sourceDir.isDirectory()) {
            throw new IOException("源目录不存在: " + sourceDir);
        }
        if (!target.exists() && !target.mkdirs()) {
            throw new IOException("无法创建 " + target);
        }
        long total = countBytes(sourceDir);
        long[] state = {0, System.currentTimeMillis()};
        copyDir(sourceDir, target, total, state, progress);
        if (progress != null) progress.onProgress(total, total);
        return target;
    }

    public static boolean isStored(Context context, String gameId) {
        File dir = gameDir(context, gameId);
        return dir.isDirectory() && dir.list() != null && dir.list().length > 0;
    }

    /** 递归删除收纳目录（彻底删除用；应用私有空间内，无需权限）。 */
    public static boolean remove(Context context, String gameId) {
        return com.winlator.core.FileUtils.delete(gameDir(context, gameId));
    }

    private static long countBytes(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return 0;
        long sum = 0;
        for (File f : files) {
            if (f.isDirectory()) sum += countBytes(f);
            else sum += Math.max(0, f.length());
        }
        return sum;
    }

    private static void copyDir(File src, File dst, long total, long[] state,
                                ImportFlow.ProgressListener progress) throws IOException {
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            File t = new File(dst, f.getName());
            if (f.isDirectory()) {
                if (!t.exists() && !t.mkdirs()) throw new IOException("无法创建 " + t);
                copyDir(f, t, total, state, progress);
            }
            else copyFile(f, t, total, state, progress);
        }
    }

    private static void copyFile(File src, File dst, long total, long[] state,
                                 ImportFlow.ProgressListener progress) throws IOException {
        try (InputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (progress != null && total > 0) {
                    state[0] += n;
                    long now = System.currentTimeMillis();
                    if (now - state[1] >= 200) {
                        state[1] = now;
                        progress.onProgress(state[0], total);
                    }
                }
            }
        }
    }
}
