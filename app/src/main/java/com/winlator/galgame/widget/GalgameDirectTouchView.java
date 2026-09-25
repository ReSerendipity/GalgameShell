package com.winlator.galgame.widget;

import android.content.Context;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import com.winlator.core.AppUtils;
import com.winlator.math.XForm;
import com.winlator.renderer.ViewTransformation;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.XServer;

/**
 * 直接触控层（手机/平板式，区别于上游 {@link com.winlator.widget.TouchpadView} 的
 * 「手指拖动鼠标」触摸板语义）。
 *
 * 交互（对标 GameNative / 手机端点按习惯）：
 * <ul>
 *   <li>单指按下＝光标移到指尖并按下左键；拖动＝按住拖拽；抬起＝松开左键。</li>
 *   <li>快速轻点＝左键单击；双击＝两次单击（自然形成）。</li>
 *   <li>第二根手指按下＝临时切右键（拖动＝右拖，快速抬起＝右键单击），抬起后恢复左键。</li>
 * </ul>
 *
 * 层级与放行约定（零改上游）：
 * 本视图由 {@link com.winlator.galgame.GalgameInputHelper} 加到 rootView 最顶层；
 * 触控模式非 direct、虚拟控件 overlay 可见或事件来自外接鼠标时，
 * {@link #onTouchEvent} 返回 false，由 FrameLayout 依 z 序交给
 * InputControlsView / TouchpadView——与上游行为完全一致。
 */
public final class GalgameDirectTouchView extends View {

    private final XServer xServer;
    private final float[] xform = XForm.getInstance();
    private int primaryId = -1;
    private int secondaryId = -1;
    private boolean leftDown = false;
    private boolean rightDown = false;
    /** 本次手势开始时间与代号（代号用于让延迟释放回调在新手势到来时失效）。 */
    private long primaryDownTime = 0;
    private int gestureGen = 0;

    /** 最小按下时长：过短（如 adb input tap 的 ~0ms）部分 galgame 引擎不识别为点击。 */
    private static final long MIN_CLICK_DURATION_MS = 30;

    public GalgameDirectTouchView(Context context, XServer xServer) {
        super(context);
        this.xServer = xServer;
        updateXform(AppUtils.getScreenWidth(), AppUtils.getScreenHeight());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateXform(w, h);
    }

    /** 屏幕坐标 → XServer 画布坐标（与 TouchpadView 同一套变换逻辑）。 */
    private void updateXform(int outerWidth, int outerHeight) {
        try {
            ViewTransformation vt = new ViewTransformation();
            vt.update(outerWidth, outerHeight, xServer.screenInfo.width, xServer.screenInfo.height);
            float invAspect = 1.0f / vt.aspect;
            boolean fullscreen;
            try {
                fullscreen = xServer.getRenderer().isFullscreen();
            } catch (Exception ignored) {
                fullscreen = true;
            }
            if (!fullscreen) {
                XForm.makeTranslation(xform, -vt.viewOffsetX, -vt.viewOffsetY);
                XForm.scale(xform, invAspect, invAspect);
            }
            else XForm.makeScale(xform, invAspect, invAspect);
        }
        catch (Exception ignored) {
            XForm.makeScale(xform, 1.0f, 1.0f);
        }
    }

    private int[] transform(float x, float y) {
        float[] p = XForm.transformPoint(xform, x, y);
        return new int[]{(int) p[0], (int) p[1]};
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // 外接鼠标：交给上游链（TouchpadView 的绝对定位 / InputControlsView）
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                releaseAll();
                gestureGen++;
                primaryDownTime = SystemClock.uptimeMillis();
                primaryId = event.getPointerId(0);
                int[] p = transform(event.getX(0), event.getY(0));
                xServer.injectPointerMove(p[0], p[1]);
                xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT);
                leftDown = true;
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                int id = event.getPointerId(event.getActionIndex());
                if (id != primaryId && secondaryId == -1) {
                    secondaryId = id;
                    // 左→右切换：拖动＝右拖，快速抬起＝右键单击
                    if (leftDown) {
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT);
                        leftDown = false;
                    }
                    xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT);
                    rightDown = true;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (primaryId >= 0) {
                    int idx = event.findPointerIndex(primaryId);
                    if (idx >= 0) {
                        int[] p = transform(event.getX(idx), event.getY(idx));
                        xServer.injectPointerMove(p[0], p[1]);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int id = event.getPointerId(event.getActionIndex());
                if (id == secondaryId) {
                    secondaryId = -1;
                    if (rightDown) {
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT);
                        rightDown = false;
                    }
                    // 主指仍按着则恢复左键（右键拖完继续左拖）
                    if (primaryId >= 0 && event.findPointerIndex(primaryId) >= 0) {
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT);
                        leftDown = true;
                    }
                }
                else if (id == primaryId) {
                    primaryId = -1;
                    if (leftDown) {
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT);
                        leftDown = false;
                    }
                    // 副指还在则升为主指，继续拖动
                    if (secondaryId >= 0) {
                        primaryId = secondaryId;
                        secondaryId = -1;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                // 按下过短则延迟释放，保证引擎能收到一次完整点击（真机手指不受影响）
                final int gen = gestureGen;
                long held = SystemClock.uptimeMillis() - primaryDownTime;
                if (leftDown && held < MIN_CLICK_DURATION_MS) {
                    postDelayed(() -> {
                        if (gen == gestureGen) releaseAll();
                    }, MIN_CLICK_DURATION_MS - held + 5);
                }
                else releaseAll();
                return true;
            }
        }
        return true;
    }

    /** 兜底释放所有按键并复位指针状态（防按键卡死）。 */
    private void releaseAll() {
        if (rightDown) {
            xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT);
            rightDown = false;
        }
        if (leftDown) {
            xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT);
            leftDown = false;
        }
        primaryId = -1;
        secondaryId = -1;
    }
}
