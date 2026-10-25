package com.winlator;

import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.galgame.EngineDetector;
import com.winlator.galgame.GalgameLibraryIndex;
import com.winlator.galgame.GalgameSaveManager;
import com.winlator.galgame.ImportFlow;
import com.winlator.galgame.engine.BuiltinEngine;
import com.winlator.galgame.engine.BuiltinEngineRegistry;
import com.winlator.galgame.NativeRouteLauncher;
import com.winlator.galgame.ui.GalgameSettings;

import org.json.JSONObject;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 导入向导（Swiss）：选目录 → 自动检测引擎 → 确认导入。
 * 复用 {@link EngineDetector} 与 {@link ImportFlow}，行为与原导入一致。
 */
public class GalgameImportActivity extends AppCompatActivity {

    public static final String EXTRA_PATH = "galgame.path";
    public static final String EXTRA_AUTO = "galgame.auto";

    private static final String PREFS = "galgame_import_ui";
    private static final String KEY_DONE_NAME = "pending_done_name";


    private int step = 0;
    private File source;
    private EngineDetector.Engine detected;
    private boolean encrypted;
    private boolean busy;

    private EditText pathInput;
    private EditText nameInput;
    private View stepSource;
    private View stepDetect;
    private View stepConfirm;
    private View stepDone;
    private View scanning;
    private View detectedPanel;
    private TextView engineView;
    private TextView routeView;
    private TextView routeHint;
    private TextView filesView;
    private TextView encryptedView;
    private TextView summaryPath;
    private TextView summaryEngine;
    private TextView summaryRoute;
    private TextView doneName;
    private TextView step1;
    private TextView step2;
    private TextView step3;
    private Button prev;
    private Button next;
    private android.widget.LinearLayout importProgressPanel;
    private android.widget.ProgressBar importProgressBar;
    private android.widget.TextView importProgressText;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ContainerManager containerManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_galgame_import);
        containerManager = new ContainerManager(this);

        pathInput = findViewById(R.id.ETImportPath);
        nameInput = findViewById(R.id.ETName);
        stepSource = findViewById(R.id.LLStepSource);
        stepDetect = findViewById(R.id.LLStepDetect);
        stepConfirm = findViewById(R.id.LLStepConfirm);
        stepDone = findViewById(R.id.LLDone);
        scanning = findViewById(R.id.LLScanning);
        detectedPanel = findViewById(R.id.LLDetected);
        engineView = findViewById(R.id.TVEngine);
        routeView = findViewById(R.id.TVRoute);
        routeHint = findViewById(R.id.TVRouteHint);
        filesView = findViewById(R.id.TVFiles);
        encryptedView = findViewById(R.id.TVEncrypted);
        summaryPath = findViewById(R.id.TVSummaryPath);
        summaryEngine = findViewById(R.id.TVSummaryEngine);
        summaryRoute = findViewById(R.id.TVSummaryRoute);
        doneName = findViewById(R.id.TVDoneName);
        step1 = findViewById(R.id.TVStep1);
        step2 = findViewById(R.id.TVStep2);
        step3 = findViewById(R.id.TVStep3);
        prev = findViewById(R.id.BTPrev);
        next = findViewById(R.id.BTNext);
        importProgressPanel = findViewById(R.id.LLImportProgress);
        importProgressBar = findViewById(R.id.PBImport);
        importProgressText = findViewById(R.id.TVImportProgress);

        findViewById(R.id.BTBack).setOnClickListener(v -> onBack());
        prev.setOnClickListener(v -> onPrev());
        next.setOnClickListener(v -> onNext());
        bindQuick(R.id.BTQuick1, "/sdcard/Download");
        bindQuick(R.id.BTQuick2, "/sdcard/Games");
        bindQuick(R.id.BTQuick3, "/sdcard/Documents");

        if (savedInstanceState != null) {
            step = savedInstanceState.getInt("step", 0);
            String src = savedInstanceState.getString("source");
            if (src != null) source = new File(src);  // 重建后「删除源目录」询问仍可用
            pathInput.setText(savedInstanceState.getString("path", "/sdcard/Download"));
            nameInput.setText(savedInstanceState.getString("name", ""));
            String engine = savedInstanceState.getString("engine");
            if (engine != null) {
                try { detected = EngineDetector.Engine.valueOf(engine); } catch (Throwable ignored) {}
            }
            encrypted = savedInstanceState.getBoolean("encrypted", false);
            int files = savedInstanceState.getInt("files", 0);
            if (detected != null) {
                engineView.setText(EngineDetector.displayName(detected));
                boolean nativeRoute = detected.route == EngineDetector.Route.B;
                routeView.setText(nativeRoute ? "原生路由 · 内嵌引擎优先" : "模拟路由 · Winlator 容器");
                filesView.setText(String.format(Locale.US, "%d", files));
                encryptedView.setText(encrypted ? "检测到" : "未检测");
                scanning.setVisibility(View.GONE);
                detectedPanel.setVisibility(View.VISIBLE);
            }
        }

        // 导入过程中引擎会以横屏启动 → 本 Activity 重建；完成态落盘，重建后直接展示。
        // 仅在重建（savedInstanceState != null）时消费该标记；全新打开导入页必须先清掉，
        // 否则上一次导入的「已导入」会劫持下一次导入。
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (savedInstanceState != null) {
            String pendingDone = prefs.getString(KEY_DONE_NAME, null);
            if (pendingDone != null) {
                prefs.edit().remove(KEY_DONE_NAME).apply();
                step = 3;
                doneName.setText("「" + pendingDone + "」已加入游戏库，运行环境已就绪。");
            }
        } else {
            prefs.edit().remove(KEY_DONE_NAME).apply();
        }

        renderStep();

        // 仅调试自动化：adb 指定 path + auto=true 时自动走完 目录→检测→导入
        if (getIntent().hasExtra(EXTRA_PATH) && getIntent().getBooleanExtra(EXTRA_AUTO, false)) {
            pathInput.setText(getIntent().getStringExtra(EXTRA_PATH));
            main.postDelayed(this::autoStep, 800);
        }
    }

    private void autoStep() {
        if (step == 0) { onNext(); main.postDelayed(this::autoStep, 1200); }
        else if (step == 1) {
            if (detected != null) { onNext(); main.postDelayed(this::autoStep, 800); }
            else main.postDelayed(this::autoStep, 1200);
        }
        else if (step == 2 && !busy && detected != null) { onNext(); }
    }

    private void bindQuick(int id, final String path) {
        findViewById(id).setOnClickListener(v -> pathInput.setText(path));
    }

    private void onBack() {
        if (busy) return;
        if (step == 0) { finish(); return; }
        step--;
        renderStep();
    }

    private void onPrev() {
        if (busy) return;
        if (step == 0) finish();
        else { step--; renderStep(); }
    }

    private void onNext() {
        if (busy) return;
        if (step == 0) {
            File dir = new File(pathInput.getText().toString().trim());
            if (!dir.isDirectory()) { toast(getString(R.string.galgame_import_invalid)); return; }
            source = dir;
            step = 1;
            renderStep();
            detect();
        } else if (step == 1) {
            if (detected == null) return;
            step = 2;
            nameInput.setText(source.getName());
            summaryPath.setText(source.getAbsolutePath());
            summaryEngine.setText(detected.name());
            summaryRoute.setText(detected.route == EngineDetector.Route.B ? "原生路由" : "模拟路由");
            renderStep();
        } else if (step == 2) {
            startImport();
        } else {
            // COMPLETED → 返回游戏库：先问源目录清理（副本已在应用内部，删除不回滚）
            maybeOfferSourceCleanup(() -> {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_DONE_NAME).apply();
                finish();
            });
        }
    }

    private void detect() {
        scanning.setVisibility(View.VISIBLE);
        detectedPanel.setVisibility(View.GONE);
        setBusy(true);
        next.setText("检测中…");
        executor.execute(() -> {
            EngineDetector.Engine engine = EngineDetector.Engine.UNKNOWN;
            boolean enc = false;
            int count = 0;
            try {
                engine = EngineDetector.detect(source);
                enc = EngineDetector.isEncrypted(source);
                String[] children = source.list();
                count = children != null ? children.length : 0;
            } catch (Throwable ignored) {}
            final EngineDetector.Engine e = engine;
            final boolean en = enc;
            final int c = count;
            main.post(() -> {
                setBusy(false);
                detected = e;
                encrypted = en;
                next.setText(getString(R.string.galgame_import_next));
                scanning.setVisibility(View.GONE);
                detectedPanel.setVisibility(View.VISIBLE);
                engineView.setText(e == EngineDetector.Engine.UNKNOWN ? getString(R.string.galgame_import_engine_unknown) : EngineDetector.displayName(e));
                boolean nativeRoute = e.route == EngineDetector.Route.B;
                routeView.setText(nativeRoute ? "原生路由 · 内嵌引擎优先" : "模拟路由 · Winlator 容器");
                routeHint.setText(nativeRoute
                        ? "检测到可原生运行的引擎，导入后可直接启动。"
                        : "将创建专属 Winlator 容器，自动注入日文区域与存档重定向。");
                filesView.setText(String.format(Locale.US, "%d", c));
                encryptedView.setText(en ? "检测到" : "未检测");
                encryptedView.setTextColor(ContextCompat.getColor(this, en ? R.color.swiss_red : R.color.swiss_ink));
            });
        });
    }

    private void startImport() {
        if (source == null) return;
        final String gameId = sanitize(source.getName());
        setBusy(true);
        next.setText("导入中…");
        final android.content.Context appCtx = getApplicationContext();
        executor.execute(() -> {
            final ImportFlow.ImportResult result;
            try {
                result = ImportFlow.run(source, gameId, appCtx);
            } catch (Exception e) {
                main.post(() -> fail(getString(R.string.galgame_import_failed) + ": " + e.getMessage()));
                return;
            }
            if (result.route == EngineDetector.Route.B) {
                // GalgameShell 第七轮：原生引擎游戏先「收纳」进应用私有目录
                // （files/galgame-native/<gameId>，完整复制）——不再引用源目录，
                // 用户在库外改名/移动/误删不再损坏游戏；KR2 运行时产物也留在私有空间。
                final File stored;
                try {
                    stored = com.winlator.galgame.GalgameNativeStore.store(appCtx, result.gameId, source,
                            (done, total) -> main.post(() -> {
                                if (total <= 0) return;
                                importProgressPanel.setVisibility(View.VISIBLE);
                                importProgressBar.setProgress((int) (done * 100 / total));
                                importProgressText.setText(String.format(Locale.US,
                                        "COPY %d%% · %s / %s", done * 100 / total,
                                        formatBytes(done), formatBytes(total)));
                            }));
                } catch (Exception e) {
                    main.post(() -> fail("收纳游戏文件失败: " + e.getMessage()));
                    return;
                }
                BuiltinEngine builtin = BuiltinEngineRegistry.resolve(result.engine);
                if (builtin != null && builtin.isAvailable(appCtx) && builtin.launch(appCtx, stored)) {
                    GalgameLibraryIndex.putNative(appCtx, result.gameId, source.getName(), result.engine, stored);
                    main.post(this::done);
                } else {
                    // 内置引擎不可用 → 回退外部原生播放器（Tier2 免责门禁与原导入流程一致）
                    main.post(() -> showExternalRouteDialog(result));
                }
                return;
            }

            main.post(() -> containerManager.createContainerAsync(result.containerData, (Callback<Container>) container -> {
                if (container == null) { fail(getString(R.string.galgame_import_failed)); return; }
                executor.execute(() -> {
                    try {
                        ImportFlow.stageGameFiles(container, result, source, appCtx,
                                (done, total) -> main.post(() -> {
                                    if (total <= 0) return;
                                    importProgressPanel.setVisibility(View.VISIBLE);
                                    importProgressBar.setProgress((int) (done * 100 / total));
                                    importProgressText.setText(String.format(Locale.US,
                                            "COPY %d%% · %s / %s", done * 100 / total,
                                            formatBytes(done), formatBytes(total)));
                                }));
                        GalgameSaveManager saveManager = new GalgameSaveManager(gameId);
                        saveManager.redirectShellFolders(container);
                        GalgameLibraryIndex.markContainerized(appCtx, gameId);
                        File staged = new File(container.getRootDir(), ".wine/drive_c/galgame/" + gameId);
                        saveManager.symlinkPortableSaves(staged);
                        main.post(this::done);
                    } catch (Exception e) {
                        main.post(() -> fail(getString(R.string.galgame_import_failed) + ": " + e.getMessage()));
                    }
                });
            }));
        });
    }

    /** B 路由回退：外部原生播放器（plan → 免责 → 扫描目录 → 唤起）。 */
    private void showExternalRouteDialog(ImportFlow.ImportResult result) {
        setBusy(false);
        final NativeRouteLauncher.Plan plan =
                NativeRouteLauncher.plan(this, result.engine, GalgameSettings.isTier2Enabled(this));
        if (plan.target == null) {
            fail(plan.message);
            return;
        }
        String message = plan.message != null ? plan.message : "";
        if (plan.needsDisclaimer) message += System.lineSeparator() + System.lineSeparator() + getString(R.string.galgame_broute_disclaimer);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.galgame_route_b) + " · " + plan.target.displayName)
                .setMessage(message)
                .setNegativeButton(R.string.galgame_cancel, null);

        if (plan.ready()) {
            builder.setPositiveButton(R.string.galgame_action_launch, (dialog, which) -> {
                if (plan.scanDir != null) {
                    NativeRouteLauncher.prepareScanDir(source, plan.scanDir, result.gameId);
                }
                NativeRouteLauncher.launch(this, plan.target);
                toast("已交由外部播放器启动");
            });
        } else if (plan.needsDisclaimer && !plan.allowed) {
            builder.setPositiveButton(R.string.galgame_broute_enable, (dialog, which) -> {
                GalgameSettings.setTier2Enabled(this, true);
                showExternalRouteDialog(result);
            });
        } else {
            builder.setPositiveButton(R.string.galgame_ok, null);
        }
        builder.show();
    }

    /**
     * 导入完成（游戏文件已在应用内部留有完整副本）后，询问是否删除用户选中的源目录。
     * 只对「应用外部目录」询问；删除失败不回滚导入（副本已完整），仅提示。
     */
    private void maybeOfferSourceCleanup(Runnable onDone) {
        if (source == null || !source.isDirectory()) { onDone.run(); return; }
        File filesDir = getFilesDir();
        String srcPath = source.getAbsolutePath();
        if (srcPath.startsWith(filesDir.getParentFile() != null
                ? filesDir.getParentFile().getAbsolutePath() : filesDir.getAbsolutePath())) {
            return; // 应用私有空间内部（重复导入已收纳的游戏）不询问
        }
        new AlertDialog.Builder(this)
                .setTitle("删除源目录？")
                .setMessage("「" + source.getName() + "」已完整导入应用内部。\n是否删除原位置的源文件以释放空间？")
                .setNegativeButton("保留", (d, w) -> onDone.run())
                .setPositiveButton("删除源目录", (d, w) -> {
                    if (com.winlator.core.FileUtils.delete(source)) {
                        toast("源目录已删除");
                    } else {
                        toast("部分文件未能删除，可手动清理");
                    }
                    onDone.run();
                })
                .show();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0fKB", bytes / 1024f);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1fMB", bytes / 1048576f);
        return String.format(Locale.US, "%.2fGB", bytes / 1073741824f);
    }

    private void done() {
        setBusy(false);
        importProgressPanel.setVisibility(View.GONE);
        String name = String.valueOf(nameInput.getText());
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_DONE_NAME, name).apply();
        step = 3;
        doneName.setText("「" + name + "」已加入游戏库，运行环境已就绪。");
        renderStep();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("step", step);
        if (source != null) outState.putString("source", source.getAbsolutePath());
        outState.putString("path", String.valueOf(pathInput.getText()));
        outState.putString("name", String.valueOf(nameInput.getText()));
        outState.putString("engine", detected != null ? detected.name() : null);
        outState.putBoolean("encrypted", encrypted);
        String files = String.valueOf(filesView.getText());
        try { outState.putInt("files", Integer.parseInt(files)); } catch (Throwable ignored) {}
    }

    private void fail(String message) {
        setBusy(false);
        next.setText(getString(R.string.galgame_import_start));
        toast(message);
    }

    private void setBusy(boolean b) {
        busy = b;
        next.setEnabled(!b);
        prev.setEnabled(!b);
        pathInput.setEnabled(!b);
    }

    private void renderStep() {
        stepSource.setVisibility(step == 0 ? View.VISIBLE : View.GONE);
        stepDetect.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        stepConfirm.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        stepDone.setVisibility(step == 3 ? View.VISIBLE : View.GONE);

        int ink = ContextCompat.getColor(this, R.color.swiss_ink);
        int paper = ContextCompat.getColor(this, R.color.swiss_paper);
        styleStep(step1, step >= 0, step == 0, ink, paper);
        styleStep(step2, step >= 2, step == 1, ink, paper);
        styleStep(step3, step >= 3, step == 2, ink, paper);

        ((TextView) findViewById(R.id.TVMetaTop)).setText(
                String.format(Locale.US, "IMPORT / %02d–03", Math.min(step + 1, 3)));

        if (step == 0) {
            prev.setText(getString(R.string.galgame_import_prev));
            next.setText(getString(R.string.galgame_import_next));
        } else if (step == 1) {
            prev.setText(getString(R.string.galgame_import_prev));
            next.setText(getString(R.string.galgame_import_next));
        } else if (step == 2) {
            prev.setText(getString(R.string.galgame_import_prev));
            next.setText(getString(R.string.galgame_import_start));
        } else {
            prev.setText(getString(R.string.galgame_import_prev));
            next.setText(getString(R.string.galgame_import_back_library));
        }
    }

    private void styleStep(TextView v, boolean done, boolean active, int ink, int paper) {
        if (active) {
            v.setBackgroundColor(ink);
            v.setTextColor(paper);
        } else {
            v.setBackgroundColor(paper);
            v.setTextColor(done ? ink : ContextCompat.getColor(this, R.color.swiss_faint));
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private static String sanitize(String name) {
        if (name == null || name.isEmpty()) return "game";
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
