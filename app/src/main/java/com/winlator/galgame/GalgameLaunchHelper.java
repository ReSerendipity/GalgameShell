package com.winlator.galgame;

import android.app.Activity;
import android.content.Intent;

import com.winlator.container.Container;
import com.winlator.core.FileUtils;
import com.winlator.galgame.ui.GalgameLibraryActivity;

import org.json.JSONObject;

import java.io.File;

/**
 * Galgame 启动接线：把 galgame_overlay.json 的 exe 接进**所有**容器启动入口。
 *
 * 背景（R4）：XServerDisplayActivity 无 exec_path/shortcut 时 fallback 到文件管理器
 * 桌面（wfm.exe），用户点「启动」会先进类 Windows 桌面、再手动找游戏 exe —— 这正是
 * 「点击启动却进了 Windows 模拟页面」的原因。导入时 overlay 已锁定启动 exe
 * （见 ImportFlow.stageGameFiles / writeOverlay），启动时直接注入 exec_path 即可
 * boot 走 nogui 一键进游戏。
 *
 * 对标 GameHub / GameNative 的「点封面即玩」范式：启动不再经过 Windows 桌面。
 * 对无 overlay 的普通 Winlator 容器零影响（helper 静默跳过，行为不变）。
 */
public final class GalgameLaunchHelper {

    private GalgameLaunchHelper() {}

    /**
     * 若容器带 galgame_overlay.json 且含 exe 字段，则向 intent 注入 exec_path。
     * 无 overlay / 字段缺失 / 解析失败均静默跳过。
     */
    public static void injectExecPath(Container container, Intent intent) {
        String exe = readOverlayExe(container);
        if (exe != null) intent.putExtra("exec_path", exe);
    }

    /** 读取容器根目录 galgame_overlay.json 的 exe 字段（unix 绝对路径），无则返回 null。 */
    public static String readOverlayExe(Container container) {
        try {
            File f = new File(container.getRootDir(), "galgame_overlay.json");
            if (!f.exists()) return null;
            JSONObject o = new JSONObject(FileUtils.readString(f));
            if (o.has("exe")) {
                String exe = o.getString("exe");
                return (exe != null && !exe.isEmpty()) ? exe : null;
            }
        } catch (Exception ignored) {
            // overlay 缺失/损坏不阻断启动，按默认（文件管理器桌面）处理
        }
        return null;
    }

    /** 容器是否为 galgame 容器（以 galgame_overlay.json 存在与否判定）。 */
    public static boolean isGalgameContainer(Container container) {
        return container != null
                && new File(container.getRootDir(), "galgame_overlay.json").isFile();
    }

    /**
     * 游戏退出（进程结束 / 菜单「退出」）后重启应用并**直接落回游戏库**。
     *
     * 背景（2026-09-25 用户反馈）：上游 exit() 走 {@code AppUtils.restartApplication}，
     * 固定重启到 MainActivity（带 exec_path 时还会落到容器文件管理器），玩家打完一局
     * 回不到游戏库。此处复用同一重启原语（makeRestartActivityTask + exit），仅把
     * 任务根组件换成 GalgameLibraryActivity；非 galgame 容器不受影响。
     */
    public static void restartToLibrary(Activity activity) {
        Intent library = new Intent(activity, GalgameLibraryActivity.class);
        activity.startActivity(Intent.makeRestartActivityTask(library.getComponent()));
        Runtime.getRuntime().exit(0);
    }
}
