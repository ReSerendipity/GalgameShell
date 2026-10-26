package android.app;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.View;
public class Activity extends Context {
    public static final int RESULT_OK = -1;
    public static final int RESULT_CANCELED = 0;
    protected void onCreate(Bundle savedInstanceState) {}
    protected void onResume() {}
    protected void onPause() {}
    protected void onDestroy() {}
    protected void onSaveInstanceState(Bundle outState) {}
    public void setContentView(int layoutResID) {}
    public <T extends View> T findViewById(int id) { return null; }
    public void startActivity(Intent intent) {}
    public void startActivityForResult(Intent intent, int requestCode) {}
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {}
    public void finish() {}
    public void recreate() {}
    public Intent getIntent() { return null; }
    public void runOnUiThread(Runnable action) {}
    public AssetManager getAssets() { return null; }
    public ContentResolver getContentResolver() { return null; }
    public PackageManager getPackageManager() { return null; }
    public String getPackageName() { return ""; }
    public ApplicationInfo getApplicationInfo() { return null; }
    public Context getApplicationContext() { return null; }
    public void overridePendingTransition(int enterAnim, int exitAnim) {}
    public void addContentView(android.view.View view, android.view.ViewGroup.LayoutParams params) {}
    public androidx.activity.OnBackPressedDispatcher getOnBackPressedDispatcher() { return null; }
}
