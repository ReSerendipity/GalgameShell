package com.winlator;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

        container = findContainer(containerName);
        if (container != null) {
            item = GalgameLibraryAdapter.fromContainer(container, preferred);
        } else {
            GalgameLibraryIndex.Entry e = GalgameLibraryIndex.find(this, gameId);
            if (e != null) item = GalgameLibraryAdapter.fromIndexEntry(e);
            else item = new GalgameLibraryAdapter.Item(null, gameId, label != null ? label : gameId,
                    engine != null ? engine : "?", null, null, engine, preferred);
        }

        bindHeader();
        bindActions();
    }

    private Container findContainer(String name) {
        if (name == null) return null;
        ContainerManager cm = new ContainerManager(this);
        List<Container> containers = cm.getContainers();
        if (containers == null) return null;
        for (Container c : containers) if (name.equals(c.getName())) return c;
        return null;
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
