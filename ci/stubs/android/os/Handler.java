package android.os;
public class Handler {
    public Handler() {}
    public Handler(Looper looper) {}
    public boolean post(Runnable r) { return true; }
    public void removeCallbacks(Runnable r) {}
}
