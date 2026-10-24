package com.winlator;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.galgame.EngineDetector;
import com.winlator.galgame.GalgameCoverProvider;
import com.winlator.galgame.engine.GameEngineRegistry;
import com.winlator.galgame.GalgameLibraryIndex;
import com.winlator.galgame.GalgameLocaleInjector;
import com.winlator.galgame.GalgameLogs;
import com.winlator.galgame.GalgameSaveManager;
import com.winlator.galgame.ImportFlow;
import com.winlator.galgame.GameLauncher;
import com.winlator.galgame.ui.GalgameLibraryAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 游戏详情（Swiss）：启动主按钮 + 维护（诊断/存档/导出/导入/日志）+ 折叠高级（路由/运行环境/重注入）。
 */
public class GalgameDetailActivity extends AppCompatActivity {

    public static final String EXTRA_AUTO_LAUNCH = "galgame.auto_launch";

    public static final String EXTRA_CONTAINER = "galgame.container";
    public static final String EXTRA_GAME_ID = "galgame.gameId";
    public static final String EXTRA_LABEL = "galgame.label";
    public static final String EXTRA_ENGINE = "galgame.engine";
    public static final String EXTRA_PREFERRED = "galgame.preferred";

    private Container container;
    private GalgameLibraryAdapter.Item item;
    private boolean advancedOpen;
    private LinearLayout advancedView;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_galgame_detail);

        String containerName = getIntent().getStringExtra(EXTRA_CONTAINER);
        String gameId = getIntent().getStringExtra(EXTRA_GAME_ID);
        String label = getIntent().getStringExtra(EXTRA_LABEL);
        String engine = getIntent().getStringExtra(EXTRA_ENGINE);
        String preferred = getIntent().getStringExtra(EXTRA_PREFERRED);
        if (gameId == null) gameId = "";

        // 转换后本页 intent 里既没有容器名也没有运行方式；两者都按库索引兜底，
        // 否则转换完仍会按旧状态显示并继续走内置引擎。
        GalgameLibraryIndex.Entry indexEntry = GalgameLibraryIndex.find(this, gameId);
        if (preferred == null && indexEntry != null) preferred = indexEntry.preferredEngine;

        container = findContainer(containerName);
        if (container == null) container = findContainerByGameId(gameId);
        if (container != null) {
            item = GalgameLibraryAdapter.fromContainer(container, preferred);
        } else {
            if (indexEntry != null) item = GalgameLibraryAdapter.fromIndexEntry(indexEntry);
            else item = new GalgameLibraryAdapter.Item(null, gameId, label != null ? label : gameId,
                    engine != null ? engine : "?", null, null, engine, preferred);
        }

        bindHeader();
        bindActions();
        if (getIntent().getBooleanExtra(EXTRA_AUTO_LAUNCH, false)) {
            main.postDelayed(this::launch, 600);
        }
    }

    private Container findContainer(String name) {
        if (name == null) return null;
        ContainerManager cm = new ContainerManager(this);
        List<Container> containers = cm.getContainers();
        if (containers == null) return null;
        for (Container c : containers) if (name.equals(c.getName())) return c;
        return null;
    }

    /** 「改用 Wine 容器运行」转换完成后，本页的 intent 里没有容器名；按命名约定兜底找回。 */
    private Container findContainerByGameId(String gameId) {
        if (gameId == null || gameId.isEmpty()) return null;
        return findContainer("galgame-" + gameId);
    }

    private void bindHeader() {
        findViewById(R.id.BTBack).setOnClickListener(v -> finish());

        TextView metaTop = findViewById(R.id.TVMetaTop);
        String engineDisplay = EngineDetector.displayName(GameEngineRegistry.parseEngine(item.engineName));
        metaTop.setText(getString(R.string.galgame_detail_meta, engineDisplay));

        TextView eyebrow = findViewById(R.id.TVEyebrow);
        eyebrow.setText("GALGAME / " + engineDisplay);

        ((TextView) findViewById(R.id.TVTitle)).setText(item.label);
        ((TextView) findViewById(R.id.TVPlayed)).setText(container != null ? "已就绪" : "原生目录");
        ((TextView) findViewById(R.id.TVMeta)).setText(
                engineDisplay + "  ·  " +
                (container != null ? "运行环境已就绪" : "无容器 · 内嵌引擎"));

        ImageView cover = findViewById(R.id.IVCover);
        loadCover(cover);
    }

    private void bindActions() {
        findViewById(R.id.BTLaunch).setOnClickListener(v -> launch());
        findViewById(R.id.BTDiagnose).setOnClickListener(v -> {
            if (container == null) { toast("该游戏没有 Winlator 容器，诊断不可用"); return; }
            Intent i = new Intent(this, GalgameDiagnosticsActivity.class);
            i.putExtra(GalgameDiagnosticsActivity.EXTRA_CONTAINER, container.getName());
            i.putExtra(GalgameDiagnosticsActivity.EXTRA_TITLE, item.label);
            startActivity(i);
        });
        findViewById(R.id.BTSaves).setOnClickListener(v ->
                toast("存档已重定向到 S: 盘：" + new GalgameSaveManager(item.gameId).savePath()));
        findViewById(R.id.BTExport).setOnClickListener(v -> exportSave());
        findViewById(R.id.BTImportSave).setOnClickListener(v -> importSave());
        findViewById(R.id.BTLogs).setOnClickListener(v -> showLogs());
        findViewById(R.id.BTRemove).setOnClickListener(v -> removeFromLibrary());

        advancedView = findViewById(R.id.LLAdvanced);
        findViewById(R.id.BTAdvanced).setOnClickListener(v -> toggleAdvanced());
        findViewById(R.id.BTRoute).setOnClickListener(v -> showEnginePicker());
        findViewById(R.id.BTReinject).setOnClickListener(v -> {
            if (container == null) { toast("该游戏没有容器，无需重注入"); return; }
            GalgameLocaleInjector.Report re = GalgameLocaleInjector.reapply(container, this);
            toast(getString(re != null ? R.string.galgame_diag_reinject_done : R.string.galgame_diag_reinject_fail));
        });

        ((TextView) findViewById(R.id.TVRouteValue)).setText(
                item.preferredEngine == null ? "自动裁决" : item.preferredEngine);
        ((TextView) findViewById(R.id.TVEnvValue)).setText(
                container != null ? container.getName() : getString(R.string.galgame_env_embedded));
        ((TextView) findViewById(R.id.TVDiagMeta)).setText(
                container != null ? "逐项检查 GPU / 日文 / 视频 / 位宽 / 音频 / 存档" : "需要 Winlator 容器");
        TextView diagTag = findViewById(R.id.TVDiagTag);
        diagTag.setText(container != null ? "可检查" : "不可用");
        ((TextView) findViewById(R.id.TVSavesMeta)).setText("S: 盘 · 已重定向");
        bindForceContainer();
        bindResolution();
    }

    /** 可选虚拟屏尺寸：默认 720p 对多数 galgame 是"正好铺满"，越大越不容易裁切但小窗感越强。 */
    private static final String[] RESOLUTIONS = {"1280x720", "1600x900", "1920x1080"};

    /**
     * 「运行环境分辨率」：改容器的虚拟屏尺寸，下次启动生效（无需重建容器）。
     *
     * <p>存在理由：X 虚拟屏先裁切、再等比缩放到设备视图（{@code ViewTransformation}）。
     * 游戏内选的分辨率超过虚拟屏时，超出部分在缩放前就被裁掉，表现为"能渲染但点不动"。
     */
    private void bindResolution() {
        View row = findViewById(R.id.BTResolution);
        if (row == null) return;
        if (container == null) {
            row.setVisibility(View.GONE);
            return;
        }
        row.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.TVResolutionValue)).setText(
                getString(R.string.galgame_resolution_value, container.getScreenSize()));
        row.setOnClickListener(v -> showResolutionPicker());
    }

    private void showResolutionPicker() {
        if (container == null) return;
        int current = 0;
        String cur = container.getScreenSize();
        for (int i = 0; i < RESOLUTIONS.length; i++) if (RESOLUTIONS[i].equals(cur)) current = i;

        // 用 setItems 而不是 setSingleChoiceItems：与 setMessage 同时用时列表会被挤掉，
        // 本主题下实测只剩标题+提示+取消。当前值改为标在标签里。
        String[] labels = new String[RESOLUTIONS.length];
        for (int i = 0; i < RESOLUTIONS.length; i++) {
            labels[i] = RESOLUTIONS[i] + (i == current ? "（当前）" : "");
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.galgame_resolution_title)
                .setItems(labels, (dialog, which) -> {
                    String picked = RESOLUTIONS[which];
                    container.setScreenSize(picked);
                    container.saveData();
                    ((TextView) findViewById(R.id.TVResolutionValue)).setText(
                            getString(R.string.galgame_resolution_value, picked));
                    toast(getString(R.string.galgame_resolution_done, picked));
                })
                .setNegativeButton(R.string.galgame_cancel, null)
                .show();
    }

    /**
     * 「改用 Wine 容器运行」：只对**原生引擎且尚无容器**的条目显示。
     *
     * <p>动机：部分 Kirikiri 游戏在内置 Kirikiroid2 下报脚本错误
     * （{@code Member … does not exist}），但它们自带 Windows 原版引擎，改由 Wine 容器跑就能玩。
     * 真机已验证（docs/real-device-verification.md §8.2）。
     */
    private void bindForceContainer() {
        View row = findViewById(R.id.BTForceContainer);
        if (row == null) return;

        boolean nativeEngine = item != null
                && (EngineDetector.Engine.KIRIKIRI.name().equals(item.engineName)
                 || EngineDetector.Engine.RENPY.name().equals(item.engineName));
        boolean canConvert = container == null && nativeEngine
                && item.gameDir != null && item.gameDir.isDirectory();

        row.setVisibility(canConvert ? View.VISIBLE : View.GONE);
        if (canConvert) row.setOnClickListener(v -> confirmForceContainer());
    }

    private void confirmForceContainer() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.galgame_force_container_title)
                .setMessage(getString(R.string.galgame_force_container_confirm, item.label))
                .setNegativeButton(R.string.galgame_cancel, null)
                .setPositiveButton("开始", (d, w) -> startForceContainer())
                .show();
    }

    private void startForceContainer() {
        final File src = item.gameDir;
        Log.i("GalgameDetail", "forceContainer: src=" + src + " isDir=" + (src != null && src.isDirectory()));
        if (src == null || !src.isDirectory()) {
            toast(getString(R.string.galgame_force_container_no_source));
            return;
        }
        final Context appCtx = getApplicationContext();
        toast(getString(R.string.galgame_force_container_working));

        executor.execute(() -> {
            try {
                ImportFlow.ImportResult result = ImportFlow.run(src, item.gameId, appCtx, true);
                if (result == null || result.containerData == null) {
                    runOnUiThread(() -> toast(getString(R.string.galgame_force_container_failed)));
                    return;
                }
                // 与 GalgameImportActivity 同口径：createContainerAsync 在主线程发起
                runOnUiThread(() -> new ContainerManager(this).createContainerAsync(result.containerData, created -> {
                    if (created == null) {
                        toast(getString(R.string.galgame_force_container_failed));
                        return;
                    }
                    executor.execute(() -> {
                        try {
                            ImportFlow.stageGameFiles(created, result, src, appCtx);
                            // 让路由直接落到容器引擎：resolveOverride 会把它的 id 还原成
                            // UNKNOWN → GameEngineRegistry.resolve → WinlatorContainerEngine
                            GalgameLibraryIndex.setPreferredEngine(this, item.gameId, "winlator-container");
                            runOnUiThread(() -> {
                                toast(getString(R.string.galgame_force_container_done));
                                recreate();
                            });
                        }
                        catch (Exception e) {
                            Log.e("GalgameDetail", "forceContainer stage failed", e);
                            runOnUiThread(() -> toast(getString(R.string.galgame_force_container_failed)));
                        }
                    });
                }));
            }
            catch (Exception e) {
                Log.e("GalgameDetail", "forceContainer failed", e);
                runOnUiThread(() -> toast(getString(R.string.galgame_force_container_failed)));
            }
        });
    }

    private void toggleAdvanced() {
        advancedOpen = !advancedOpen;
        advancedView.setVisibility(advancedOpen ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.TVAdvancedTitle)).setText(
                advancedOpen ? R.string.galgame_advanced_collapse : R.string.galgame_advanced_expand);
        ((TextView) findViewById(R.id.TVAdvancedAction)).setText(advancedOpen ? "−" : "+");
    }

    private void launch() {
        if (item == null) return;
        if (!item.hasContainer() && (item.gameDir == null || !item.gameDir.isDirectory())) {
            toast(getString(R.string.galgame_source_missing));
            return;
        }
        GameLauncher.Result result = GameLauncher.launch(this, item.gameDir, item.container,
                item.engineName, item.preferredEngine);
        if (!result.launched) {
            toast(result.message != null ? result.message : getString(R.string.galgame_import_failed));
            return;
        }
        GalgameLibraryIndex.touchPlayedAsync(this, item.gameId);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void showEnginePicker() {
        final List<GameLauncher.Option> options =
                GameLauncher.options(item.hasContainer(), getString(R.string.galgame_switch_engine_auto));
        String[] labels = new String[options.size()];
        int current = 0;
        for (int i = 0; i < options.size(); i++) {
            labels[i] = options.get(i).label;
            String id = options.get(i).id;
            if (id != null && id.equals(item.preferredEngine)) current = i;
            if (id == null && item.preferredEngine == null) current = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.galgame_switch_engine_title, item.label))
                .setSingleChoiceItems(labels, current, (dialog, which) -> {
                    GameLauncher.Option picked = options.get(which);
                    GalgameLibraryIndex.setPreferredEngine(this, item.gameId, picked.id);
                    item = new GalgameLibraryAdapter.Item(item.container, item.gameId, item.label, item.sub,
                            item.gameDir, item.exe, item.engineName, picked.id);
                    ((TextView) findViewById(R.id.TVRouteValue)).setText(picked.id == null ? "自动裁决" : picked.id);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.galgame_cancel, null)
                .show();
    }

    /** 从库中移除：仅删索引记录；有容器的游戏需先在运行环境里清理容器。 */
    private void removeFromLibrary() {
        if (container != null) {
            toast("该游戏有运行环境，请先在「高级设置 → 运行环境」中清理容器后再移除");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("从库中移除")
                .setMessage("仅移除「" + item.label + "」的库记录，不删除手机中的游戏文件。")
                .setNegativeButton(R.string.galgame_cancel, null)
                .setPositiveButton("移除", (d, w) -> {
                    GalgameLibraryIndex.remove(this, item.gameId);
                    toast("已从游戏库移除");
                    finish();
                })
                .show();
    }

    private void exportSave() {
        GalgameSaveManager manager = new GalgameSaveManager(item.gameId);
        boolean ok = manager.exportBackup(GalgameSaveManager.defaultBackupDir());
        toast(getString(ok ? R.string.galgame_save_export_done : R.string.galgame_save_export_fail));
    }

    private void importSave() {
        GalgameSaveManager manager = new GalgameSaveManager(item.gameId);
        File root = GalgameSaveManager.defaultBackupDir();
        File[] all = root.listFiles();
        if (all == null) { toast(getString(R.string.galgame_save_no_backup)); return; }
        List<File> candidates = new ArrayList<>();
        for (File f : all) {
            if (f.isDirectory() && f.getName().startsWith(item.gameId + "-")) candidates.add(f);
        }
        if (candidates.isEmpty()) { toast(getString(R.string.galgame_save_no_backup)); return; }
        candidates.sort((a, b) -> b.getName().compareTo(a.getName()));
        String[] names = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) names[i] = candidates.get(i).getName();
        new AlertDialog.Builder(this)
                .setTitle(R.string.galgame_action_import_save)
                .setItems(names, (d, which) -> {
                    boolean ok = manager.restoreBackup(candidates.get(which));
                    toast(getString(ok ? R.string.galgame_save_restored : R.string.galgame_save_export_fail));
                })
                .setNegativeButton(R.string.galgame_cancel, null)
                .show();
    }

    private void showLogs() {
        if (container == null) { toast("该游戏没有容器，暂无运行日志"); return; }
        List<File> files = GalgameLogs.candidates(container);
        List<File> crashes = GalgameLogs.crashReports(this);
        if (files.isEmpty() && crashes.isEmpty()) { toast(getString(R.string.galgame_logs_none)); return; }
        File target = files.isEmpty() ? crashes.get(0) : files.get(0);
        List<String> lines = files.isEmpty() ? GalgameLogs.tail(target, 40) : GalgameLogs.highlights(target, 40);
        if (lines.isEmpty()) lines = GalgameLogs.tail(target, 40);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append("\n");
        new AlertDialog.Builder(this)
                .setTitle(target.getName())
                .setMessage(sb.toString())
                .setPositiveButton(R.string.galgame_ok, null)
                .show();
    }

    private void loadCover(final ImageView iv) {
        if (item == null) return;
        executor.execute(() -> {
            Bitmap bmp = null;
            try {
                File coverFile = item.cover != null ? item.cover
                        : GalgameCoverProvider.coverFor(item.gameDir, item.exe);
                if (coverFile != null && coverFile.isFile()) bmp = decode(coverFile, 560);
            } catch (Throwable ignored) {}
            final Bitmap b = bmp;
            main.post(() -> { if (b != null) iv.setImageBitmap(b); });
        });
    }

    private static Bitmap decode(File f, int target) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= target || o.outHeight / (sample * 2) >= target) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
