package com.winlator.galgame.debug;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.galgame.GalgameLocaleInjector;
import com.winlator.galgame.GalgameSaveManager;
import com.winlator.galgame.ui.GalgameLibraryActivity;

import java.io.File;

/**
 * 调试启动桥（仅用于真机无头回归，如 `adb shell am start` 拉起游戏容器/触发调试动作）。
 *
 * 设备约束：{@link XServerDisplayActivity} 等核心 Activity 为 {@code exported=false}，
 * 在 ColorOS / Android 16 上 `run-as` 拉起会被安全策略拒绝
 * （package=com.android.shell 与 app uid 不匹配）。本活动为新增类、{@code exported=true}，
 * 从外部（adb shell，uid 2000）启动本活动后，由应用自身 uid 内部转发/执行
 * （同 uid 允许），从而无头触发真机回归路径。
 *
 * 用法（全部需显式组件 Intent + `--ei container_id <N>`）：
 * <ul>
 *   <li>默认（无 action）：转发到 {@link XServerDisplayActivity}；带 `--es exec_path <exe>`
 *       时直接进游戏（R8「点启动直接进游戏」路径）。</li>
 *   <li>`--es action library`：转发到 {@link GalgameLibraryActivity}（M1 封面墙，配合
 *       `adb shell screencap` 目视验证）。</li>
 *   <li>`--es action reapply`：对容器执行 {@link GalgameLocaleInjector#reapply}
 *       （M4 诊断重注入，结果打 logcat，tag=GalgameDebug）。</li>
 *   <li>`--es action savesync`：对容器执行 {@link GalgameSaveManager#symlinkPortableSaves}
 *       （R14 存档软链端到端，建立链接数打 logcat）。</li>
 * </ul>
 *
 * 零改铁律：不修改任何 Winlator 本体类；仅新增此类并转发/调用 fork 自有类。
 * 仅接收显式组件 Intent，不做任何 UI。
 */
public final class GalgameDebugLaunchActivity extends Activity {

    private static final String TAG = "GalgameDebug";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent src = getIntent();
        int containerId = src.getIntExtra("container_id", 0);
        String action = src.getStringExtra("action");

        if ("library".equals(action)) {
            startActivity(new Intent(this, GalgameLibraryActivity.class));
            finish();
            return;
        }

        if ("reapply".equals(action) || "savesync".equals(action)) {
            runDebugAction(action, containerId);
            finish();
            return;
        }

        // 默认：转发到 XServerDisplayActivity（带 exec_path 则直进游戏）
        String execPath = src.getStringExtra("exec_path");
        Intent forward = new Intent(this, XServerDisplayActivity.class);
        forward.putExtra("container_id", containerId);
        if (execPath != null && !execPath.isEmpty()) {
            forward.putExtra("exec_path", execPath);
        }
        startActivity(forward);
        finish();
    }

    /** 后台线程执行 reapply / savesync，结果打 logcat（活动立即 finish，进程存活）。 */
    private void runDebugAction(String action, int containerId) {
        final String act = action;
        final int cid = containerId;
        new Thread(() -> {
            try {
                Container container = new ContainerManager(this).getContainerById(cid);
                if (container == null) {
                    Log.e(TAG, act + ": container not found id=" + cid);
                    return;
                }
                if ("reapply".equals(act)) {
                    GalgameLocaleInjector.Report r = GalgameLocaleInjector.reapply(container, this);
                    Log.i(TAG, "reapply: " + (r != null ? r.summary() : "null（overlay/游戏目录缺失）"));
                }
                else { // savesync
                    String gameId = container.getExtra("galgame_game_id");
                    if (gameId == null || gameId.isEmpty()) {
                        String name = container.getName();
                        gameId = name != null && name.startsWith("galgame-")
                                ? name.substring("galgame-".length()) : name;
                    }
                    File gameDir = new File(container.getRootDir(),
                            ".wine/drive_c/galgame/" + gameId);
                    int linked = new GalgameSaveManager(gameId).symlinkPortableSaves(gameDir);
                    Log.i(TAG, "savesync: gameId=" + gameId + " linked=" + linked
                            + " gameDirExists=" + gameDir.isDirectory());
                }
            }
            catch (Throwable t) {
                Log.e(TAG, act + ": failed", t);
            }
        }).start();
    }
}
