package android.content.pm;
import android.content.Intent;
public class PackageManager {
    public static final int GET_ACTIVITIES = 0x00000001;
    public Intent getLaunchIntentForPackage(String packageName) { return null; }
    public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException { return null; }
    public static class NameNotFoundException extends Exception {}
}
