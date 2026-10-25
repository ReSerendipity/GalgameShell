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

        findViewById(R.id.rowInputControls).setOnClickListener(v ->
                startActivity(new Intent(this, GalgameInputControlsActivity.class)));
        findViewById(R.id.rowController).setOnClickListener(v -> {
            // GalgameShell：上游 ExternalControllerBindingsActivity 必须带 profile_id/
            // controller_id 进入；此前无参跳入 → loadProfile(file(0)) 得 null → NPE
            // 闪退回页面（用户反馈「点击外接控制器直接回到页面」）。取第一个输入控制
            // 配置进入；一个配置都没有时引导先去创建。
            com.winlator.inputcontrols.InputControlsManager icm =
                    new com.winlator.inputcontrols.InputControlsManager(this);
            java.util.ArrayList<com.winlator.inputcontrols.ControlsProfile> profiles = icm.getProfiles(true);
            if (profiles.isEmpty()) {
                android.widget.Toast.makeText(this,
                        "请先在「输入控制」里创建一个配置，再绑定外接控制器按键",
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            com.winlator.inputcontrols.ControlsProfile profile = profiles.get(0);
            // 首个配置通常无已绑定控制器：直接进 default（上游 addController 幂等）
            String controllerId = "default";
            startActivity(new Intent(this, ExternalControllerBindingsActivity.class)
                    .putExtra("profile_id", profile.id)
                    .putExtra("controller_id", controllerId));
        });
        findViewById(R.id.rowShortcuts).setOnClickListener(v -> openAdvanced());

        // GalgameShell 第六轮：游戏内菜单（右上角☰）高级项显隐——默认隐藏，按需打开。
        // 永显项（触控模式/性能显示/键盘/退出）不设开关。
        bindMenuSwitch(R.id.SWMenuInputControls, "galgame_menu_input_controls");
        bindMenuSwitch(R.id.SWMenuFullscreen, "galgame_menu_fullscreen");
        bindMenuSwitch(R.id.SWMenuTaskManager, "galgame_menu_task_manager");
        bindMenuSwitch(R.id.SWMenuActiveWindows, "galgame_menu_active_windows");
        bindMenuSwitch(R.id.SWMenuMagnifier, "galgame_menu_magnifier");
        bindMenuSwitch(R.id.SWMenuScreenEffect, "galgame_menu_screen_effect");
        bindMenuSwitch(R.id.SWMenuPip, "galgame_menu_pip");
        bindMenuSwitch(R.id.SWMenuTouchpadHelp, "galgame_menu_touchpad_help");

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
    /** 游戏内菜单项显隐开关：默认 false（隐藏），改动即时生效（下次进入游戏应用）。 */
    private void bindMenuSwitch(int switchResId, String key) {
        SwitchCompat sw = findViewById(switchResId);
        sw.setChecked(prefs.getBoolean(key, false));
        sw.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(key, checked).apply());
    }
}
