package com.winlator.galgame;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.winlator.container.Container;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.Window;
import com.winlator.xserver.WindowManager;
import com.winlator.xserver.XServer;

/**
 * 启动对话框自动应答（教学-回放，2026-10-25）。
 *
 * <p>动机：不少引擎/游戏每次启动都弹「窗口尺寸确认」「年龄确认」等对话框（如 KiriKiri
 * 的尺寸确认，TD-15：引擎不落盘选择结果），每次都要手动点一次。
 *
 * <p>方案：**不盲点**。首次启动时用户手动点击对话框（直接触控层），本类记录落点在该
 * 对话框内的相对坐标（按窗口尺寸区分），存容器 extra {@link #EXTRA_DIALOG_CLICK}
 * （"w,h,rx,ry"）；之后每次启动检测到**同尺寸**（±24px）的启动期对话框，自动重放该
 * 点击（xServer.injectPointer*）。教学生效期内（启动后 {@link #ACTIVE_WINDOW_MS} 内）
 * 且该尺寸尚无记录时才记录，避免污染。
 *
 * <p>对话框识别（保守启发式）：启动 45 秒内映射的顶层 NORMAL 窗口、尺寸 320–720 ×
 * 160–480、大致居中、类名/标题非空——KiriKiri 尺寸确认实测 480x280 居中、class=游戏
 * exe 名。识别过宽只会导致"多教一个窗口"，回放有尺寸匹配门槛，误点风险受控。
 *
 * <p>接线：{@link GalgameInputHelper#attach} 注册（监听 X 窗口映射）；教学点采集在
 * {@link com.winlator.galgame.widget.GalgameDirectTouchView}（直接触控按下时）。
 * 非 galgame 容器不挂载，零上游改动。
 */
public final class GalgameDialogAutoClick implements WindowManager.OnWindowModificationListener {
    private static final String TAG = "GalgameAutoClick";

    /** 容器 extra："w,h,rx,ry"（教学时的对话框尺寸 + 点击相对坐标）。 */
    static final String EXTRA_DIALOG_CLICK = "galgame_dialog_click";

    /** 启动期窗口（教学与回放只在游戏启动后这段时间内生效）。 */
    private static final long ACTIVE_WINDOW_MS = 45_000;
    /** 回放延迟：等对话框完成首次绘制，避免点击丢失。 */
    private static final long REPLAY_DELAY_MS = 600;
    /** 按下保持时长：与直接触控层 MIN_CLICK_DURATION_MS 对齐，保证引擎识别为点击。 */
    private static final long REPLAY_HOLD_MS = 60;
    /** 尺寸匹配容差（对话框每次由引擎绘制，同游戏可能有 ±几 px 抖动）。 */
    private static final int SIZE_TOLERANCE = 24;
    /** 单次会话最多回放次数（多级对话框场景），防误点扩散。 */
    private static final int MAX_REPLAYS_PER_SESSION = 3;

    private final XServer xServer;
    private final Container container;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long startedAt = SystemClock.uptimeMillis();

    private Window candidate;
    private int replays = 0;

    private GalgameDialogAutoClick(XServer xServer, Container container) {
        this.xServer = xServer;
        this.container = container;
    }

    /** galgame 容器挂载；其它容器返回 null（调用方不持有）。 */
    static GalgameDialogAutoClick attach(XServer xServer, Container container) {
        if (!GalgameLaunchHelper.isGalgameContainer(container)) return null;
        GalgameDialogAutoClick tracker = new GalgameDialogAutoClick(xServer, container);
        xServer.windowManager.addOnWindowModificationListener(tracker);
        return tracker;
    }

    @Override
    public void onMapWindow(Window window) {
        if (SystemClock.uptimeMillis() - startedAt > ACTIVE_WINDOW_MS) return;
        if (window.getParent() != xServer.windowManager.rootWindow) return;

        short w = window.getWidth();
        short h = window.getHeight();
        if (w < 320 || w > 720 || h < 160 || h > 480) return;

        int centerX = window.getRootX() + w / 2;
        int centerY = window.getRootY() + h / 2;
        int screenWidth = xServer.screenInfo.width;
        int screenHeight = xServer.screenInfo.height;
        if (Math.abs(centerX - screenWidth / 2) > screenWidth / 5) return;
        if (Math.abs(centerY - screenHeight / 2) > screenHeight / 3) return;
        if (window.getClassName().isEmpty() && window.getName().isEmpty()) return;

        candidate = window;

        int[] taught = parse(container.getExtra(EXTRA_DIALOG_CLICK));
        if (taught == null || replays >= MAX_REPLAYS_PER_SESSION) return;
        if (Math.abs(taught[0] - w) > SIZE_TOLERANCE || Math.abs(taught[1] - h) > SIZE_TOLERANCE) return;

        replays++;
        final int x = window.getRootX() + taught[2];
        final int y = window.getRootY() + taught[3];
        handler.postDelayed(() -> {
            xServer.injectPointerMove(x, y);
            xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT);
            handler.postDelayed(() -> xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT), REPLAY_HOLD_MS);
        }, REPLAY_DELAY_MS);
        Log.d(TAG, "replay dialog click at virtual (" + x + "," + y + ") for " + w + "x" + h);
    }

    @Override
    public void onUnmapWindow(Window window) {
        if (window == candidate) candidate = null;
    }

    /**
     * 直接触控层按下时回调（X 虚拟屏坐标）：落点在当前候选对话框内且该尺寸尚未教学
     * → 记录教学点。窗口不在启动期或落点在对话框外时不动作。
     */
    public void onTouchDown(int x, int y) {
        Window window = candidate;
        if (window == null) return;
        if (SystemClock.uptimeMillis() - startedAt > ACTIVE_WINDOW_MS) return;

        short w = window.getWidth();
        short h = window.getHeight();
        int relX = x - window.getRootX();
        int relY = y - window.getRootY();
        if (relX < 0 || relY < 0 || relX >= w || relY >= h) return;

        int[] taught = parse(container.getExtra(EXTRA_DIALOG_CLICK));
        if (taught != null && Math.abs(taught[0] - w) <= SIZE_TOLERANCE && Math.abs(taught[1] - h) <= SIZE_TOLERANCE) return;

        container.putExtra(EXTRA_DIALOG_CLICK, w + "," + h + "," + relX + "," + relY);
        container.saveData();
        Log.d(TAG, "taught dialog click " + w + "x" + h + " rel=(" + relX + "," + relY + ")");
    }

    /** 解析 "w,h,rx,ry"；格式不符返回 null。 */
    private static int[] parse(String value) {
        if (value == null || value.isEmpty()) return null;
        String[] parts = value.split(",");
        if (parts.length != 4) return null;
        try {
            int[] result = new int[4];
            for (int i = 0; i < 4; i++) result[i] = Integer.parseInt(parts[i].trim());
            return result;
        }
        catch (NumberFormatException e) {
            return null;
        }
    }
    // 其余窗口监听回调（内容/几何/属性/层序）用接口 default 空实现，与本功能无关。
}
