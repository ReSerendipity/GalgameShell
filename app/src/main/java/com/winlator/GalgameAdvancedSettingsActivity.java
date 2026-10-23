package com.winlator;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.winlator.core.Callback;
import com.winlator.core.PreloaderDialog;

/**
 * 高级设置宿主：承载原有 {@link GalgameSettingsFragment}（Wine / Box64 / 语言 / 运行环境等）。
 */
public class GalgameAdvancedSettingsActivity extends AppCompatActivity implements GalgameHost {

    private final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private Callback<Uri> openFileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_galgame_advanced_settings);

        setSupportActionBar(findViewById(R.id.advancedToolbar));
        findViewById(R.id.BTBack).setOnClickListener(v -> finish());
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.advancedContainer, new GalgameSettingsFragment())
                    .commit();
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
    public void showFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.advancedContainer, fragment)
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
}
