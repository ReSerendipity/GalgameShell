package android.app;
import android.content.Context;
public class ActivityManager {
    public static class MemoryInfo {
        public long totalMem;
        public long availMem;
    }
    public void getMemoryInfo(MemoryInfo outInfo) {}
    public static Object getSystemService(Context ctx) { return null; }
}
