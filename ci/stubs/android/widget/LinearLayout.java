package android.widget;
import android.content.Context;
import android.view.ViewGroup;
public class LinearLayout extends ViewGroup {
    public LinearLayout(Context context) { super(); }
    public static final int VERTICAL = 1;
    public void setOrientation(int orientation) {}
    public void setPadding(int left, int top, int right, int bottom) {}
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public int leftMargin, topMargin, rightMargin, bottomMargin;
        public LayoutParams(int width, int height) { super(width, height); }
    }
}
