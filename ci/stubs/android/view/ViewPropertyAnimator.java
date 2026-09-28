package android.view;
/** CI 桩：最小属性动画链。 */
public class ViewPropertyAnimator {
    public ViewPropertyAnimator alpha(float value) { return this; }
    public ViewPropertyAnimator scaleX(float value) { return this; }
    public ViewPropertyAnimator scaleY(float value) { return this; }
    public ViewPropertyAnimator setStartDelay(long startDelay) { return this; }
    public ViewPropertyAnimator setDuration(long duration) { return this; }
    public ViewPropertyAnimator withEndAction(Runnable endAction) { return this; }
    public void start() {}
}
