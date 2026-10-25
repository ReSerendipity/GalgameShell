package android.widget;
import android.content.Context;
import android.view.ViewGroup;
public class FrameLayout extends ViewGroup {
    public FrameLayout() {}
    public FrameLayout(Context context) { super(); }
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public int gravity;
        public int leftMargin, topMargin, rightMargin, bottomMargin;
        public LayoutParams(int width, int height) { super(width, height); }
        public LayoutParams(int width, int height, int gravity) {
            super(width, height);
            this.gravity = gravity;
        }
    }
}
