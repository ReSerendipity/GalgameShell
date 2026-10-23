package com.winlator;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;

import com.winlator.core.AppUtils;

import java.util.Locale;

/**
 * 设置（Swiss）：常用项即时生效；开发者模式下开放原有完整设置。
 */
public class GalgameSettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_galgame_settings);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        findViewById(R.id.BTBack).setOnClickListener(v -> finish());

        // 主题
        boolean dark = prefs.getInt("app_theme", GalgameSettingsFragment.APP_THEME_LIGHT) == GalgameSettingsFragment.APP_THEME_DARK;
        TextView themeValue = findViewById(R.id.TVTheme);
        themeValue.setText(dark ? "深色" : "浅色");
        findViewById(R.id.rowTheme).setOnClickListener(v -> {
            boolean nowDark = prefs.getInt("app_theme", GalgameSettingsFragment.APP_THEME_LIGHT) == GalgameSettingsFragment.APP_THEME_DARK;
            // 必须同步落盘：restartApplication 会 Runtime.exit，apply() 的异步写在杀进程时会丢
            prefs.edit().putInt("app_theme", nowDark ? GalgameSettingsFragment.APP_THEME_LIGHT : GalgameSettingsFragment.APP_THEME_DARK).commit();
            // 主题影响返回栈里的库/详情/诊断等所有页面，必须整任务重启，不能只重启设置页
            AppUtils.restartApplication(this);
        });

        findViewById(R.id.rowLanguage).setOnClickListener(v -> openAdvanced());

        SeekBar speed = findViewById(R.id.SBCursorSpeed);
        TextView speedValue = findViewById(R.id.TVSpeed);
        int speedProgress = Math.round(prefs.getFloat("cursor_speed", 1.0f) * 100);
        speed.setProgress(Math.max(0, Math.min(200, speedProgress)));
        speedValue.setText(String.format(Locale.US, "%d%%", speedProgress));
        speed.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speedValue.setText(String.format(Locale.US, "%d%%", progress));
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putFloat("cursor_speed", seekBar.getProgress() / 100.0f).apply();
            }
        });

        SeekBar size = findViewById(R.id.SBCursorSize);
        TextView sizeValue = findViewById(R.id.TVSize);
        int sizeProgress = Math.round(prefs.getFloat("cursor_scale", 1.0f) * 100);
        size.setProgress(Math.max(0, Math.min(200, sizeProgress)));
        sizeValue.setText(String.format(Locale.US, "%d%%", sizeProgress));
        size.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sizeValue.setText(String.format(Locale.US, "%d%%", progress));
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putFloat("cursor_scale", seekBar.getProgress() / 100.0f).apply();
            }
        });

        SwitchCompat follow = findViewById(R.id.SWFollowTouch);
        follow.setChecked(prefs.getBoolean("move_cursor_to_touchpoint", false));
        follow.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean("move_cursor_to_touchpoint", checked).apply());

        SwitchCompat capture = findViewById(R.id.SWCapture);
        capture.setChecked(prefs.getBoolean("capture_pointer_on_external_mouse", true));
        capture.setOnCheckedChangeListener((b, checked) ->
                prefs.edit().putBoolean("capture_pointer_on_external_mouse", checked).apply());

        findViewById(R.id.rowInputControls).setOnClickListener(v -> openAdvanced());
        findViewById(R.id.rowController).setOnClickListener(v ->
                startActivity(new Intent(this, ExternalControllerBindingsActivity.class)));
        findViewById(R.id.rowShortcuts).setOnClickListener(v -> openAdvanced());

        findViewById(R.id.rowAbout).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.galgame_about)
                        .setMessage(getString(R.string.galgame_about_version) + "\n\n" + getString(R.string.galgame_about_message))
                        .setPositiveButton(R.string.galgame_ok, null)
                        .show());

        SwitchCompat dev = findViewById(R.id.SWDevMode);
        boolean devOn = prefs.getBoolean("galgame_dev_mode", false);
        dev.setChecked(devOn);
        applyDev(devOn);
        dev.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean("galgame_dev_mode", checked).apply();
            applyDev(checked);
        });
        findViewById(R.id.rowAdvanced).setOnClickListener(v -> openAdvanced());
    }

    private void applyDev(boolean on) {
        findViewById(R.id.rowAdvanced).setVisibility(on ? android.view.View.VISIBLE : android.view.View.GONE);
        ((TextView) findViewById(R.id.TVDevMeta)).setText(
                on ? R.string.galgame_settings_dev_mode_on : R.string.galgame_settings_dev_mode_off);
        findViewById(R.id.TVDevMeta).setAlpha(on ? 1f : 0.75f);
    }

    private void openAdvanced() {
        startActivity(new Intent(this, GalgameAdvancedSettingsActivity.class));
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    }
}
