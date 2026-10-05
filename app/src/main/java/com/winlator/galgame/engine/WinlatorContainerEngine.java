package com.winlator.galgame.engine;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.winlator.galgame.GalgameLaunchHelper;

/**
 * 容器 / Wine 路线（即「Winlator 本体」）作为一个普通 {@link GameEngine} 实现。
 *
 * <p>在此之前，这条路线只是散落在 UI 里的三行 {@code startActivity(XServerDisplayActivity)}，
 * 既没有可用性概念、也没有许可证/来源登记、更无法参与「按引擎选路」的逻辑，
 * 于是 Kirikiri / Ren'Py 这类能原生跑的游戏从游戏库启动时会被一刀切送进 wine。
 * 本类把它补齐为与 {@link KrkrEngine} / {@link RenPyEngine} 等价的一等引擎。
 *
 * <p><b>零改铁律</b>：绝不 {@code import} 也不修改 {@code XServerDisplayActivity} /
 * {@code Container.java}。宿主 Activity 以字符串 {@link #ACTIVITY_CLASS} 引用
 * （与 {@link KrkrEngine} 同手法，顺带让本类能过 CI 的 galgame-compile 桩门禁），
 * 启动参数与既有 {@code GalgameLibraryActivity.launchGame} 完全一致：
 * {@code putExtra("container_id", id)} + overlay 的 {@code exec_path} 注入。
 */
public final class WinlatorContainerEngine implements GameEngine {

    public static final String ID = "winlator-container";

    /** Winlator 容器显示 Activity；以字符串引用，避免把上游庞杂依赖拉进本类。 */
    public static final String ACTIVITY_CLASS = "com.winlator.XServerDisplayActivity";

    /** 与上游一致的容器 id extra 名（改了就断链，勿动）。 */
    public static final String EXTRA_CONTAINER_ID = "container_id";

    private static final String TAG = "WinlatorContainerEngine";

    public static final WinlatorContainerEngine INSTANCE = new WinlatorContainerEngine();

    private WinlatorContainerEngine() {}

    @Override
    public String id() { return ID; }

    @Override
    public String displayName() { return "Winlator 容器（Wine / Box64）"; }

    @Override
    public String upstreamUrl() { return "https://github.com/brunodev85/winlator-app"; }

    @Override
    public String license() { return "LGPL-2.1"; }

    @Override
    public boolean requiresContainer() { return true; }

    /**
     * box64 / wine 运行时随 APK 打包（assets 内的 .tzst 与 jniLibs），故恒为可用。
     * 真正的失败点在运行时（rootfs 未展开等），由 {@link #launch} 兜底返回 false。
     */
    @Override
    public boolean isAvailable(Context context) { return true; }

    @Override
    public String unavailableReason(Context context) { return "已就绪"; }

    @Override
    public boolean launch(Context context, GameLaunchRequest request) {
        if (context == null || request == null) return false;

        long containerId = request.containerId;
        if (containerId <= 0) {
            Log.w(TAG, "缺少 container_id，无法启动容器：" + request);
            return false;
        }

        // 与 UI 既有行为一致：galgame 容器的启动 exe 未落盘时直接拒绝，
        // 免得 wine 跑空路径后立刻退出、又静默弹回游戏库（「打开游戏进不去」的老病）。
        if (request.container != null && GalgameLaunchHelper.isExecMissing(request.container)) {
            Log.w(TAG, "overlay 记录的启动 exe 不存在，拒绝启动：" + request);
            return false;
        }

        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(context.getPackageName(), ACTIVITY_CLASS));
            intent.putExtra(EXTRA_CONTAINER_ID, (int) containerId);
            if (request.container != null) {
                GalgameLaunchHelper.injectExecPath(request.container, intent);
            }
            // 仅在非 Activity 上下文（Application / Service / 通知）才另起 task；
            // 从游戏库起时留在同一 task，返回键才能退回游戏库而不是回桌面。
            if (!(context instanceof android.app.Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            return true;
        }
        catch (RuntimeException e) {
            // Activity 未注册 / 意向不可解析
            Log.w(TAG, "拉起容器屏失败", e);
            return false;
        }
    }

    @Override
    public String toString() { return ID; }
}
