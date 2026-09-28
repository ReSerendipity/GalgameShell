package android.content;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public abstract AssetManager getAssets();
    public abstract PackageManager getPackageManager();
    public abstract String getPackageName();
    public abstract void startActivity(Intent intent);
    public String getString(int resId) { return ""; }
    public String getString(int resId, Object... formatArgs) { return ""; }
    public SharedPreferences getSharedPreferences(String name, int mode) { return null; }
    public Context getApplicationContext() { return this; }
}
