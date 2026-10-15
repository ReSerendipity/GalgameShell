package com.winlator.galgame;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 轻量崩溃采集（第二轮移动端评估 T3 · 稳定性可观测补强）。
 *
 * <p>报告差距：无崩溃上报与 ANR 防护 → 崩溃黑盒。本类以<b>无新依赖</b>方式提供进程级兜底：
 * {@link Thread#setDefaultUncaughtExceptionHandler} 捕获未处理异常，将时间戳 / 线程 / 堆栈写入
 * 应用专属外部目录 {@code getExternalFilesDir(null)/crashes/}（免权限），随后<b>交还原默认
 * handler</b>（不吞崩溃，保持进程终止语义）。幂等安装（重复调用不覆盖）。
 *
 * <p>决策：不引入崩溃 SDK（报告未提及新依赖，遵守落地铁律 1）；崩溃文件不做自动上报
 * （无后端/无凭据，超范围）；ANR 采集属系统级能力（ANR-WatchDog 类依赖），记为后续建议。
 *
 * <p>v2.9 评估整改（任务 GalgameShell-v29-remediate，对应移动端工程体系报告 P1-1）：
 * 崩溃文件按 versionCode 分目录归档（crashes/&lt;versionCode&gt;/，见 {@link #crashDir}），
 * 并经 {@link GalgameLogs#crashReports} 接入诊断日志查看；自动上报仍不做（零遥测决策不变）。
 */
public final class GalgameCrashHandler implements Thread.UncaughtExceptionHandler {

    private static volatile boolean installed = false;

    private final Thread.UncaughtExceptionHandler fallback;
    private final File crashDir;

    private GalgameCrashHandler(Thread.UncaughtExceptionHandler fallback, File crashDir) {
        this.fallback = fallback;
        this.crashDir = crashDir;
    }

    /** 安装进程级未捕获异常兜底；幂等（重复调用不覆盖已装实例）。 */
    public static void install(Context context) {
        if (installed) return;
        GalgameCrashHandler handler = new GalgameCrashHandler(
                Thread.getDefaultUncaughtExceptionHandler(), crashDir(context));
        Thread.setDefaultUncaughtExceptionHandler(handler);
        installed = true;
    }

    /**
     * 崩溃归档目录（安装侧与查看侧共用同一落点）：
     * {@code getExternalFilesDir(null)/crashes/<versionCode>/}——按版本归档（v2.9 评估 P1-1），
     * 免权限，应用卸载随系统清理。
     */
    public static File crashDir(Context context) {
        File root = new File(context.getExternalFilesDir(null), "crashes");
        return new File(root, appVersion(context));
    }

    /** 版本号目录名（versionCode；解析失败退回 unknown，尽力而为不改变崩溃语义）。 */
    private static String appVersion(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm == null ? null : pm.getPackageInfo(context.getPackageName(), 0);
            return info == null ? "unknown" : String.valueOf(info.versionCode);
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        try {
            if (!crashDir.isDirectory() && !crashDir.mkdirs()) return;
            File file = new File(crashDir,
                    "crash-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date())
                            + "-" + thread.getName() + ".txt");
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println("thread: " + thread.getName());
                pw.println("time: " + new Date());
                pw.println("exception: " + throwable);
                throwable.printStackTrace(pw);
            }
        } catch (IOException ignored) {
            // 崩溃采集本身失败不改变崩溃语义（尽力而为）
        } finally {
            if (fallback != null) {
                fallback.uncaughtException(thread, throwable);
            }
        }
    }
}
