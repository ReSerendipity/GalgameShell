package android.animation;
public class ValueAnimator {
    public interface AnimatorUpdateListener { void onAnimationUpdate(ValueAnimator animation); }
    public static ValueAnimator ofInt(int... values) { return new ValueAnimator(); }
    public void setDuration(long duration) {}
    public void addUpdateListener(AnimatorUpdateListener listener) {}
    public void start() {}
    public void cancel() {}
    public Object getAnimatedValue() { return 0; }
}
