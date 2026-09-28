package android.view;
public class MotionEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;
    public boolean isFromSource(int source) { return false; }
    public int getActionMasked() { return 0; }
    public int getPointerId(int pointerIndex) { return 0; }
    public float getX(int pointerIndex) { return 0f; }
    public float getY(int pointerIndex) { return 0f; }
    public int getActionIndex() { return 0; }
    public int findPointerIndex(int pointerId) { return -1; }
}
