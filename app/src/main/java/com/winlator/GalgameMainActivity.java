package com.winlator;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.LocaleHelper;
import com.winlator.core.PreloaderDialog;
import com.winlator.xenvironment.RootFSInstaller;

/**
 * GalgameShell Swiss：单首页壳。
 *
 * <p>启动即游戏库（{@link GalgameHomeFragment}），取消底部导航与顶部工具栏；
 * 设置经游戏库报头的齿轮进入 {@code GalgameSettingsActivity}。
 * 仍实现 {@link GalgameHost}，供通用 Fragment（容器/文件管理器等）复用。
 */
public class GalgameMainActivity extends AppCompatActivity implements GalgameHost {

    private static final int PERMISSION_REQUEST_CODE = 1;

    private final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private Callback<Uri> openFileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_galgame_main);

        com.winlator.galgame.GalgameCrashHandler.install(this);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.galgameFragmentContainer, new GalgameHomeFragment())
                    .commit();
        }

        if (!requestAppPermissions()) {
            RootFSInstaller.installIfNeeded(this);
        }
    }

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                RootFSInstaller.installIfNeeded(this);
            }
            else finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (openFileCallback != null) {
                openFileCallback.call(data != null ? data.getData() : null);
                openFileCallback = null;
            }
        }
    }

    @Override
    public void onBackPressed() {
        androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) fm.popBackStack();
        else super.onBackPressed();
    }

    // ---- GalgameHost ----

    @Override
    public void showFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                .replace(R.id.galgameFragmentContainer, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void setOpenFileCallback(Callback<Uri> callback) {
        this.openFileCallback = callback;
    }

    @Override
    public PreloaderDialog getPreloaderDialog() {
        return preloaderDialog;
    }

    private boolean requestAppPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return false;

        ActivityCompat.requestPermissions(this, new String[] {
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE
        }, PERMISSION_REQUEST_CODE);
        return true;
    }
}
