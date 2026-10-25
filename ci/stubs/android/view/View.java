package android.view;
import android.content.Context;
public class View {
    public interface OnClickListener { void onClick(View v); }
    public interface OnLongClickListener { boolean onLongClick(View v); }
    public View() {}
    public View(Context context) {}
    public void setOnClickListener(OnClickListener l) {}
    public void setOnLongClickListener(OnLongClickListener l) {}
    public int getId() { return 0; }
    public Context getContext() { return null; }
    public android.content.res.Resources getResources() { return null; }
    public void setLayoutParams(ViewGroup.LayoutParams params) {}
    public ViewParent getParent() { return null; }
    public Object getTag() { return null; }
    public Object getTag(int key) { return null; }
    public void setTag(Object tag) {}
    public void setTag(int key, Object tag) {}
    public void setAlpha(float alpha) {}
    public void startLayoutAnimation() {}
    public ViewPropertyAnimator animate() { return new ViewPropertyAnimator(); }
    public void setEnabled(boolean enabled) {}
    public boolean isEnabled() { return true; }
    public void setScaleX(float scaleX) {}
    public void setScaleY(float scaleY) {}
    public void setVisibility(int visibility) {}
    public int getVisibility() { return VISIBLE; }
    public void setBackgroundColor(int color) {}
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {}
    public boolean onTouchEvent(MotionEvent event) { return false; }
    public void postDelayed(Runnable action, long delayMillis) {}
    public <T extends View> T findViewById(int id) { return null; }
    public static final int VISIBLE = 0;
    public static final int GONE = 8;
}
