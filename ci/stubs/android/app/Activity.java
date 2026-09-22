package android.app;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.view.View;
public class Activity extends Context {
    protected void onCreate(Bundle savedInstanceState) {}
    protected void onResume() {}
    protected void onPause() {}
    protected void onDestroy() {}
    public void setContentView(int layoutResID) {}
    public <T extends View> T findViewById(int id) { return null; }
    public void startActivity(Intent intent) {}
    public void startActivityForResult(Intent intent, int requestCode) {}
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {}
    public void finish() {}
    public Intent getIntent() { return null; }
    public void runOnUiThread(Runnable action) {}
    public AssetManager getAssets() { return null; }
    public PackageManager getPackageManager() { return null; }
    public String getPackageName() { return ""; }
}
