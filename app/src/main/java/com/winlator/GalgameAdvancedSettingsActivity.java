package com.winlator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.LocaleHelper;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.WineInfo;
import com.winlator.galgame.GalgameLibraryIndex;
import com.winlator.galgame.GalgameLocaleInjector;
import com.winlator.xenvironment.RootFSInstaller;

import java.util.List;

/**
 * 高级设置（Swiss 结构）：语言与主题 / 运行环境 / Wine·Box64 / 兼容入口。
 *
 * <p>兼容入口承载原有 {@link GalgameSettingsFragment}（全部旧选项，已套 Swiss 主题）。
 */
public class GalgameAdvancedSettingsActivity extends AppCompatActivity implements GalgameHost {

    private static final String[] LOCALE_LABELS = {"English", "Português", "Русский", "中文"};

    private final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private Callback<Uri> openFileCallback;
    private SharedPreferences prefs;
    private View scrollView;
    private View legacyContainer;
    private boolean legacyShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_galgame_advanced_settings);
        setSupportActionBar(findViewById(R.id.advancedToolbar));
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        scrollView = findViewById(R.id.advancedScroll);
        legacyContainer = findViewById(R.id.advancedContainer);
        findViewById(R.id.BTBack).setOnClickListener(v -> onBack());

        bindLanguageTheme();
        bindEnvironments();
        bindWineBox64();
        bindLegacy();
    }

    private void bindLanguageTheme() {
        TextView language = findViewById(R.id.TVLanguage);
        int index = prefs.getInt("lc_index", LocaleHelper.getLocaleIndex(this));
        language.setText(index >= 0 && index < LOCALE_LABELS.length ? LOCALE_LABELS[index] : "跟随系统");
        findViewById(R.id.rowLanguage).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.galgame_settings_language)
                .setSingleChoiceItems(LOCALE_LABELS, index, (dialog, which) -> {
                    prefs.edit().putInt("lc_index", which).apply();
                    dialog.dismiss();
                    AppUtils.restartApplication(this);
                })
                .setNegativeButton(R.string.galgame_cancel, null)
                .show());

        boolean dark = prefs.getInt("app_theme", GalgameSettingsFragment.APP_THEME_LIGHT) == GalgameSettingsFragment.APP_THEME_DARK;
        ((TextView) findViewById(R.id.TVTheme)).setText(dark ? "深色" : "浅色");
        findViewById(R.id.rowTheme).setOnClickListener(v -> {
            boolean nowDark = prefs.getInt("app_theme", GalgameSettingsFragment.APP_THEME_LIGHT) == GalgameSettingsFragment.APP_THEME_DARK;
            prefs.edit().putInt("app_theme", nowDark ? GalgameSettingsFragment.APP_THEME_LIGHT : GalgameSettingsFragment.APP_THEME_DARK).apply();
            AppUtils.restartApplication(this);
        });
    }

    private void bindEnvironments() {
        LinearLayout list = findViewById(R.id.LLEnvironments);
        list.removeAllViews();
        List<Container> containers = new ContainerManager(this).getContainers();
        int count = 0;
        if (containers != null) {
            for (Container c : containers) {
                String name = c.getName();
                if (name == null || !name.startsWith("galgame-")) continue;
                count++;
                list.addView(envRow(c));
            }
        }
        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("暂无运行环境 · 导入游戏时自动创建");
            empty.setTextSize(10f);
            empty.setTextColor(ContextCompat.getColor(this, R.color.swiss_muted));
            empty.setPadding(dp(20), dp(12), dp(20), dp(12));
            empty.setBackgroundResource(R.drawable.swiss_row_line);
            list.addView(empty);
        }

        findViewById(R.id.rowReinstall).setOnClickListener(v ->
                ContentDialog.confirm(this, R.string.do_you_want_to_reinstall_system_files,
                        () -> RootFSInstaller.install(this)));
    }

    private View envRow(Container container) {
        String name = container.getName();
        String engine = container.getExtra("galgame_engine", "?");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(12), dp(20), dp(12));
        row.setBackgroundResource(R.drawable.swiss_row_line);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> showEnvDetail(container));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(name);
        title.setTextSize(13f);
        title.setTextColor(ContextCompat.getColor(this, R.color.swiss_ink));
        col.addView(title);

        TextView meta = new TextView(this);
        meta.setText((engine == null ? "?" : engine) + " · 点击查看 / 管理");
        meta.setTextSize(9f);
        meta.setAllCaps(true);
        meta.setLetterSpacing(0.1f);
        meta.setTextColor(ContextCompat.getColor(this, R.color.swiss_muted));
        col.addView(meta);
        row.addView(col);

        TextView tag = new TextView(this);
        tag.setText("就绪");
        tag.setTextSize(9f);
        tag.setAllCaps(true);
        tag.setLetterSpacing(0.1f);
        tag.setTextColor(ContextCompat.getColor(this, R.color.swiss_muted));
        row.addView(tag);
        return row;
    }

    /** 运行环境详情：事实 + 重新注入（确认键）+ 删除（危险行）。 */
    private void showEnvDetail(final Container container) {
        final ContentDialog dialog = new ContentDialog(this, R.layout.galgame_env_detail_dialog);
        dialog.setTitle(container.getName());

        String gameId = gameIdOf(container);
        ((TextView) dialog.findViewById(R.id.TVEnvRuntime)).setText(
                "引擎 " + nv(container.getExtra("galgame_engine"))
                        + "  ·  Windows " + nv(container.getExtra("windows_version")) + "\n"
                        + "图形 " + nv(container.getDXWrapper())
                        + "  ·  Box64 " + nv(container.getBox64Preset()) + "\n"
                        + "Wine " + WineInfo.fromIdentifier(this, container.getWineVersion()).fullVersion()
                        + (WineInfo.isMainWineVersion(container.getWineVersion()) ? "（内置）" : "（另装）"));
        ((TextView) dialog.findViewById(R.id.TVEnvPath)).setText(
                "环境目录  " + container.getRootDir().getAbsolutePath());

        Button reinject = dialog.findViewById(R.id.BTConfirm);
        reinject.setText("重新注入语言/字体");
        reinject.setOnClickListener(v -> { dialog.dismiss(); reinjectLanguage(container); });
        Button close = dialog.findViewById(R.id.BTCancel);
        close.setText(R.string.galgame_cancel);

        dialog.findViewById(R.id.RowEnvRemove).setOnClickListener(v -> {
            dialog.dismiss();
            ContentDialog.confirm(this, R.string.do_you_want_to_remove_this_container, () ->
                    new ContainerManager(this).removeContainerAsync(container, () -> {
                        GalgameLibraryIndex.remove(this, gameId);
                        runOnUiThread(this::bindEnvironments);
                    }));
        });
        dialog.show();
    }

    private void reinjectLanguage(final Container container) {
        preloaderDialog.showOnUiThread(R.string.loading);
        new Thread(() -> {
            final GalgameLocaleInjector.Report report =
                    GalgameLocaleInjector.reapply(container, getApplicationContext());
            runOnUiThread(() -> {
                preloaderDialog.closeOnUiThread();
                AppUtils.showToast(this, report != null ? R.string.galgame_diag_reinject_done
                        : R.string.galgame_diag_reinject_fail);
            });
        }).start();
    }

    private static String gameIdOf(Container container) {
        String name = container.getName();
        return name != null && name.startsWith("galgame-") ? name.substring("galgame-".length()) : "";
    }

    private static String nv(String v) {
        return v == null || v.isEmpty() ? "默认" : v;
    }

    private void bindWineBox64() {
        ((TextView) findViewById(R.id.TVBox64Version)).setText(prefs.getString("box64_version", "默认"));
        ((TextView) findViewById(R.id.TVBox64Preset)).setText(prefs.getString("box64_preset", "默认"));
        // 版本/预设依赖上游下拉与预设管理器，统一从兼容入口调整
        findViewById(R.id.rowBox64Version).setOnClickListener(v -> showLegacy());
        findViewById(R.id.rowBox64Preset).setOnClickListener(v -> showLegacy());

        SwitchCompat wineDebug = findViewById(R.id.SWWineDebug);
        wineDebug.setChecked(prefs.getBoolean("enable_wine_debug", false));
        wineDebug.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean("enable_wine_debug", checked).apply());

        SwitchCompat saveLogs = findViewById(R.id.SWSaveLogs);
        saveLogs.setChecked(prefs.getBoolean("save_logs_to_file", false));
        saveLogs.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean("save_logs_to_file", checked).apply());
    }

    private void bindLegacy() {
        findViewById(R.id.rowLegacy).setOnClickListener(v -> showLegacy());
    }

    private void showLegacy() {
        if (legacyShown) return;
        legacyShown = true;
        scrollView.setVisibility(View.GONE);
        legacyContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.advancedContainer, new GalgameSettingsFragment())
                .commit();
    }

    private void onBack() {
        if (legacyShown) {
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.advancedContainer);
            if (current != null) {
                getSupportFragmentManager().beginTransaction().remove(current).commit();
            }
            legacyShown = false;
            legacyContainer.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        onBack();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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
