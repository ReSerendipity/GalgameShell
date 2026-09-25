package com.winlator.galgame;

import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.winlator.container.Container;
import com.winlator.galgame.widget.GalgameDirectTouchView;
import com.winlator.xserver.XServer;

import java.lang.ref.WeakReference;

/**
 * Galgame 触控接线（2026-09-25 用户反馈「触控仍是手指拖动鼠标」的产品化修复）。
 *
 * 上游只有 TouchpadView（触摸板语义：手指相对移动＝移动光标）。
 * 本 helper 为 galgame 容器接入 {@link GalgameDirectTouchView}（直接触控：
 * 点按＝点击、拖动＝拖拽、双指＝右键），并支持游戏内抽屉菜单运行时切换。
 *
 * 模式持久化在容器 extra {@code touchMode}（"direct"/"touchpad"）；
 * galgame 容器缺省 direct（手机/平板习惯），普通 Winlator 容器零影响。
 */
public final class GalgameInputHelper {

    public static final String EXTRA_TOUCH_MODE = "touchMode";
    public static final String MODE_DIRECT = "direct";
    public static final String MODE_TOUCHPAD = "touchpad";

    private static WeakReference<FrameLayout> rootRef = new WeakReference<>(null);
    private static WeakReference<XServer> xServerRef = new WeakReference<>(null);
    private static WeakReference<GalgameDirectTouchView> attachedRef = new WeakReference<>(null);
    @Nullable
    private static volatile Container currentContainer;

    private GalgameInputHelper() {}

    /** 在 setupUI 末尾调用：galgame 容器且模式为 direct 时挂直接触控层（最顶层）。 */
    public static void attach(FrameLayout rootView, XServer xServer, @Nullable Container container) {
        rootRef = new WeakReference<>(rootView);
        xServerRef = new WeakReference<>(xServer);
        currentContainer = container;
        attachedRef = new WeakReference<>(null);
        if (!GalgameLaunchHelper.isGalgameContainer(container)) return;
        if (!MODE_DIRECT.equals(container.getExtra(EXTRA_TOUCH_MODE, MODE_DIRECT))) return;
        GalgameDirectTouchView view = new GalgameDirectTouchView(rootView.getContext(), xServer);
        rootView.addView(view);
        attachedRef = new WeakReference<>(view);
    }

    /**
     * 运行时切换 direct/touchpad（游戏内抽屉菜单触发），持久化到容器 extra。
     * @return 新模式；非 galgame 容器返回 null（调用方提示不支持）
     */
    @Nullable
    public static String toggle() {
        Container container = currentContainer;
        if (container == null || !GalgameLaunchHelper.isGalgameContainer(container)) return null;

        boolean toDirect = !MODE_DIRECT.equals(container.getExtra(EXTRA_TOUCH_MODE, MODE_DIRECT));
        String mode = toDirect ? MODE_DIRECT : MODE_TOUCHPAD;
        container.putExtra(EXTRA_TOUCH_MODE, mode);
        container.saveData();

        GalgameDirectTouchView attached = attachedRef.get();
        if (attached != null) {
            attached.setVisibility(toDirect ? View.VISIBLE : View.GONE);
        }
        else if (toDirect) {
            FrameLayout root = rootRef.get();
            XServer xServer = xServerRef.get();
            if (root != null && xServer != null) {
                GalgameDirectTouchView view = new GalgameDirectTouchView(root.getContext(), xServer);
                root.addView(view);
                attachedRef = new WeakReference<>(view);
            }
        }
        return mode;
    }
}
