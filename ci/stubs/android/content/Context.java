package android.content;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
public abstract class Context {
    public abstract AssetManager getAssets();
    public abstract PackageManager getPackageManager();
    public abstract String getPackageName();
    public abstract void startActivity(Intent intent);
}
