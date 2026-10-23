package com.winlator.galgame;

import android.content.Context;
import android.os.Environment;

import com.winlator.container.Container;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 日志查看（Plan Part I §I.6.7）。
 *
 * 定位并聚合运行日志：官方 {@code Documents/Winlator/logs.txt}（由官方 LogView 写入）
 * + 容器目录下常见日志（stderr/stdout/game_log/box64/dxvk/d3d9 等）
 * + 本地崩溃记录（v2.9 评估 P1-1：GalgameCrashHandler crashes/&lt;versionCode&gt;/）。
 * 提供 {@link #tail} 与 {@link #highlights}（按关键词挑要害行）供诊断向导展示。
 */
public final class GalgameLogs {

    private GalgameLogs() {}

    /** 官方日志落点（复刻 LogView.getLogFile()，避免依赖 UI 组件）。 */
    public static File defaultLogFile() {
        File parent = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Winlator");
        return new File(parent, "logs.txt");
    }

    /**
     * 崩溃归档目录（v2.9 评估 P1-1 接入）：与 {@link GalgameCrashHandler#crashDir} 同一落点，
     * 单一事实源避免双写路径漂移。
     */
    public static File crashDir(Context context) {
        return GalgameCrashHandler.crashDir(context);
    }

    /**
     * 崩溃记录文件（crashes/&lt;versionCode&gt;/crash-*.txt）；文件名含时间戳，按名倒序即新→旧；
     * 最多返回 20 份（诊断查看场景足够，避免整目录加载）。
     */
    public static List<File> crashReports(Context context) {
        List<File> out = new ArrayList<>();
        File versionRoot = crashDir(context);
        File[] versionDirs = versionRoot == null ? null : versionRoot.listFiles();
        if (versionDirs != null) {
            for (File versionDir : versionDirs) {
                File[] files = versionDir.listFiles();
                if (files == null) continue;
                for (File f : files) {
                    if (f.isFile() && f.getName().endsWith(".txt")) out.add(f);
                }
            }
        }
        out.sort((a, b) -> b.getName().compareTo(a.getName()));
        if (out.size() > 20) out.subList(20, out.size()).clear();
        return out;
    }

    /** 候选日志文件（按重要性排序）。 */
    public static List<File> candidates(Container container) {
        List<File> out = new ArrayList<>();

        File def = defaultLogFile();
        if (def.isFile()) out.add(def);

        if (container != null) {
            File root = container.getRootDir();
            String[] names = {
                    "logs.txt", "stderr.txt", "stdout.txt", "game_log.txt",
                    "box64.log", "dxvk.log", "d3d9.log", "wine.log", "wine_exec.log"
            };
            for (String n : names) {
                File f = new File(root, n);
                if (f.isFile()) out.add(f);
            }
            // ProcessHelper 的 wine 输出固定落在应用私有目录（见 core/ProcessHelper）。
            File appWineLog = new File("/data/data/com.winlator/files/wine_exec.log");
            if (appWineLog.isFile()) out.add(appWineLog);
            // fork 的 ProcessHelper 把 wine 输出落在游戏工作目录（workingDirOverride）下。
            File galgameRoot = new File(root, ".wine/drive_c/galgame");
            File[] gameDirs = galgameRoot.listFiles();
            if (gameDirs != null) {
                for (File gd : gameDirs) {
                    if (!gd.isDirectory()) continue;
                    File w = new File(gd, "wine_exec.log");
                    if (w.isFile()) out.add(w);
                }
            }
            File driveC = new File(root, ".wine/drive_c");
            File[] files = driveC.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".log")) out.add(f);
                }
            }
        }
        return out;
    }

    /** 取文件末尾 n 行（文件不存在返回空表）。 */
    public static List<String> tail(File file, int n) {
        List<String> lines = new ArrayList<>();
        if (file == null || !file.isFile() || n <= 0) return lines;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() > n) lines.remove(0);
            }
        }
        catch (Exception ignored) {
            // 读取失败按空处理
        }
        return lines;
    }

    /** 按关键词挑出「要害行」（错误/异常/缺失/拒绝等），最多 max 条。 */
    public static List<String> highlights(File file, int max) {
        List<String> out = new ArrayList<>();
        if (file == null || !file.isFile() || max <= 0) return out;

        String[] keys = {
                "err:", "error", "failed", "failure", "exception", "crash",
                "not found", "cannot", "unable", "missing", "denied",
                "segmentation", "abort", "no such file"
        };

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase();
                for (String k : keys) {
                    if (lower.contains(k)) {
                        out.add(line);
                        break;
                    }
                }
                if (out.size() >= max) break;
            }
        }
        catch (Exception ignored) {
            // 读取失败按空处理
        }
        return out;
    }
}
