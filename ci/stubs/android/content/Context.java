package android.content;
import java.io.File;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public abstract AssetManager getAssets();
    public abstract PackageManager getPackageManager();
    public abstract String getPackageName();
    public abstract ApplicationInfo getApplicationInfo();
    public abstract void startActivity(Intent intent);
    public String getString(int resId) { return ""; }
    public String getString(int resId, Object... formatArgs) { return ""; }
    public SharedPreferences getSharedPreferences(String name, int mode) { return null; }
    public Context getApplicationContext() { return this; }
    public static final String ACTIVITY_SERVICE = "activity";
    public static final String BATTERY_SERVICE = "batterymanager";
    public Object getSystemService(String name) { return null; }
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) { return null; }
    public File getFilesDir() { return null; }
    public File getExternalFilesDir(String type) { return null; }
}
