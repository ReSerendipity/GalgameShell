package com.winlator;

import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
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
import com.winlator.galgame.GalgameIds;
import com.winlator.galgame.GalgameLibraryIndex;
import com.winlator.galgame.GalgameSaveManager;
import com.winlator.galgame.ImportFlow;
import com.winlator.galgame.SafStager;
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
    private static final int REQ_PICK_FOLDER = 71;
    /** 暂存残骸判定阈值：正常复制以分钟计，超过 6 小时的目录一定是上次没清干净的。 */
    private static final long STALE_STAGE_MILLIS = 6 * 60 * 60 * 1000L;


    private int step = 0;
    private File source;
    private EngineDetector.Engine detected;
    private boolean encrypted;
    private boolean busy;

    private EditText nameInput;
    private Button pickFolder;
    private TextView pickedView;
    private android.net.Uri treeUri;
    private String pickedName;
    private String originName;
    private File stagedDir;
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

        pickFolder = findViewById(R.id.BTPickFolder);
        pickedView = findViewById(R.id.TVPicked);
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
        pickFolder.setOnClickListener(v -> {
            // 系统文件夹选择器（SAF）：授权只针对所选目录，且不依赖 MANAGE_EXTERNAL_STORAGE
            // （该特殊权限在卸载重装后会被系统重置，是 2026-10-26 导入失败的根因之一）。
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_PICK_FOLDER);
        });

        if (savedInstanceState != null) {
            step = savedInstanceState.getInt("step", 0);
            String src = savedInstanceState.getString("source");
            if (src != null) {
                source = new File(src);
                // 旋转/重建后 stagedDir 字段会丢；不在这里认回来，done()/fail() 的
                // cleanupStaging() 就删不掉暂存副本，files/import-stage 会越堆越多。
                if (src.startsWith(SafStager.stageDir(getApplicationContext()).getAbsolutePath())) {
                    stagedDir = source;
                }
            }
            String tree = savedInstanceState.getString("treeUri");
            if (tree != null) treeUri = android.net.Uri.parse(tree);
            pickedName = savedInstanceState.getString("pickedName");
            if (pickedName != null) pickedView.setText(pickedName);
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
            } else if (source == null || !source.isDirectory()) {
                // 进程被系统回收后暂存副本可能已不在：退回第一步（还留着 treeUri 就重新暂存
                // 一次，否则让用户重选）。拿不存在的目录去检测只会显示 UNKNOWN，把人带进死路。
                step = 0;
                detected = null;
                source = null;
                stagedDir = null;
            }
        } else {
            prefs.edit().remove(KEY_DONE_NAME).apply();
            SafStager.sweepStale(getApplicationContext(), STALE_STAGE_MILLIS);
        }

        renderStep();

        // 仅调试自动化：adb 指定 path + auto=true 时自动走完 目录→检测→导入。
        // 用户界面不再提供手输路径（输错/输不全是历史失败诱因），此 extra 仅 debug 构建可用。
        if (getIntent().hasExtra(EXTRA_PATH) && getIntent().getBooleanExtra(EXTRA_AUTO, false)) {
            source = new File(getIntent().getStringExtra(EXTRA_PATH));
            originName = source.getName();
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

    private void enterDetect() {
        if (originName == null) originName = source.getName();
        step = 1;
        renderStep();
        detect();
    }

    /** SAF 树 → 应用私有暂存目录；之后整条管线与 File 路径完全同构。 */
    private void stageFromTree() {
        setBusy(true);
        importProgressPanel.setVisibility(View.VISIBLE);
        importProgressText.setText(getString(R.string.galgame_import_staging));
        final android.net.Uri uri = treeUri;
        final String name = pickedName;
        executor.execute(() -> {
            try {
                File staged = SafStager.stageTree(getApplicationContext(), uri, name,
                        bytes -> main.post(() -> importProgressText.setText(
                                getString(R.string.galgame_import_staging) + " " + formatBytes(bytes))));
                main.post(() -> {
                    stagedDir = staged;
                    source = staged;
                    originName = name != null ? name : staged.getName();
                    setBusy(false);
                    enterDetect();
                });
            } catch (Exception e) {
                final String why = e instanceof SecurityException
                        ? "所选文件夹的读取授权已失效，请重新选择文件夹"
                        : String.valueOf(e.getMessage());
                main.post(() -> {
                    setBusy(false);
                    importProgressPanel.setVisibility(View.GONE);
                    fail("暂存游戏文件失败：" + why);
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_FOLDER || resultCode != RESULT_OK || data == null) return;
        android.net.Uri uri = data.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // 拿不到持久授权时本次会话内仍可读；重启后需重选，属可接受降级
        }
        treeUri = uri;
        pickedName = queryDisplayName(uri);
        pickedView.setText(pickedName != null ? pickedName : uri.toString());
        // 改选目录必须作废旧的暂存副本与检测结果：否则 onNext 见 source != null 就直接
        // 拿上一次选的文件夹去导入，界面上显示的却是新选的名字。
        cleanupStaging();
        source = null;
        detected = null;
    }

    private String queryDisplayName(android.net.Uri tree) {
        // 直接 query 树 URI 得到的是「子条目列表」，不是这个目录本身的元数据——
        // 真机实测那样查 DISPLAY_NAME 返回空，界面上就只剩一长串 content:// URI。
        // 要先用 buildDocumentUriUsingTree 换出树根自己的 document URI。
        android.net.Uri self;
        try {
            // 树 URI（/tree/…）必须用 getTreeDocumentId：getDocumentId 只认 /document/…，
            // 传树 URI 会直接抛 IllegalArgumentException（真机实测：标签退回整串 URI）。
            self = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                    tree, android.provider.DocumentsContract.getTreeDocumentId(tree));
        } catch (Exception e) {
            return null;
        }
        try (android.database.Cursor c = getContentResolver().query(self,
                new String[]{android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToNext()) return c.getString(0);
        } catch (Exception ignored) {}
        return null;
    }

    private String titleFor() {
        String typed = nameInput != null ? String.valueOf(nameInput.getText()).trim() : "";
        if (!typed.isEmpty()) return typed;
        return originName != null ? originName : (source != null ? source.getName() : null);
    }

    private void cleanupStaging() {
        if (stagedDir != null) {
            SafStager.deleteRecursively(stagedDir);
            stagedDir = null;
        }
    }

    private void onBack() {
        if (busy) return;
        if (step == 0) { cleanupStaging(); finish(); return; }
        step--;
        renderStep();
    }

    private void onPrev() {
        if (busy) return;
        if (step == 0) { cleanupStaging(); finish(); }
        else { step--; renderStep(); }
    }

    private void onNext() {
        if (busy) return;
        if (step == 0) {
            if (source == null && treeUri == null) {
                toast(getString(R.string.galgame_import_pick_first));
                return;
            }
            if (source == null) {
                stageFromTree();
                return;
            }
            // 调试后门（File 路径）仍做可读性 fail-fast：重装后 MANAGE_EXTERNAL_STORAGE
            // 被重置时 isDirectory() 仍真但 listFiles() 为 null，旧行为会一路走到
            // "导入完成"才报缺 exe（2026-10-26 真机）。SAF 路径不依赖该权限。
            if (!source.isDirectory() || source.listFiles() == null) {
                toast(getString(R.string.galgame_import_unreadable));
                openAllFilesAccessSettings();
                return;
            }
            enterDetect();
        } else if (step == 1) {
            if (detected == null) return;
            step = 2;
            nameInput.setText(originName != null ? originName : source.getName());
            summaryPath.setText(originName != null ? originName : source.getAbsolutePath());
            summaryEngine.setText(detected.name());
            summaryRoute.setText(detected.route == EngineDetector.Route.B ? "原生路由" : "模拟路由");
            renderStep();
        } else if (step == 2) {
            // 同名游戏已在库/容器里时先问一句：直接导会多出一张副本卡，而用户往往
            // 以为自己在「修复上次失败的导入」。避让后缀由 gameId 派生统一负责。
            String base = GalgameIds.gameId(
                    originName != null ? originName : source.getName(), null);
            if (ImportFlow.existingGameIds(getApplicationContext()).contains(base)) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.galgame_import_dup_title)
                        .setMessage(getString(R.string.galgame_import_dup_message, titleFor()))
                        .setNegativeButton(R.string.galgame_cancel, (d, w) -> {
                            // 取消＝放弃这次导入：暂存副本必须一起删（750MB 级的泄漏，
                            // 2026-10-26 真机复验时实测残留）。treeUri 保留，重试只需再按下一步。
                            cleanupStaging();
                            source = null;
                            detected = null;
                        })
                        .setPositiveButton(R.string.galgame_import_dup_go, (d, w) -> startImport())
                        .show();
            } else {
                startImport();
            }
        } else {
            // COMPLETED → 返回游戏库。不再询问「删除源目录」：SAF 流程下 source 是应用私有的
            // 暂存副本（done() 里已删），而用户的原目录我们只有读权限——删不掉，也不该删。
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_DONE_NAME).apply();
            finish();
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
                // 文件数按归一后的游戏根数：套娃目录下数外层只会得 1，与检测出的
                // 引擎和真实内容对不上（2026-10-26 真机：step2 显示 1 个文件）。
                final File gameRoot = EngineDetector.resolveGameRoot(source);
                enc = EngineDetector.isEncrypted(gameRoot);
                String[] children = gameRoot.list();
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
        final String gameId = GalgameIds.gameId(
                originName != null ? originName : source.getName(),
                ImportFlow.existingGameIds(getApplicationContext()));
        // 标题在主线程取一次：nameInput.getText() 不能在后台线程碰，且 SAF 流程下
        // source 的名字是暂存目录名（<时间戳>-<净化名>），不能拿它当游戏名。
        final String title = titleFor();
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
                    GalgameLibraryIndex.putNative(appCtx, result.gameId, title, result.engine, stored);
                    main.post(() -> done(null));
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
                        GalgameLibraryIndex.markContainerized(appCtx, gameId, title,
                                result.engine.name());
                        File staged = new File(container.getRootDir(), ".wine/drive_c/galgame/" + gameId);
                        saveManager.symlinkPortableSaves(staged);
                        // 没定位到启动 exe 就当场讲清楚。等到点「启动」才报「启动文件缺失」
                        // 是 2026-10-26 用户复现的形态：那时已无从判断是导入坏了还是启动坏了。
                        final String warning = result.gameExe == null
                                ? getString(R.string.galgame_import_no_exe) : null;
                        main.post(() -> done(warning));
                    } catch (Exception e) {
                        // 容器已建、文件没拷完：连容器一起删。留着就是孤儿容器，下次导入
                        // 同名游戏会被避碰成 -2，库里多出一张点不开的卡
                        // （2026-10-26 真机链条的第一环）。
                        com.winlator.core.FileUtils.delete(container.getRootDir());
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

    private static String formatBytes(long bytes) {
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0fKB", bytes / 1024f);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1fMB", bytes / 1048576f);
        return String.format(Locale.US, "%.2fGB", bytes / 1073741824f);
    }

    private void done(String warning) {
        setBusy(false);
        importProgressPanel.setVisibility(View.GONE);
        cleanupStaging();
        String name = String.valueOf(nameInput.getText());
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_DONE_NAME, name).apply();
        step = 3;
        String text = "「" + name + "」已加入游戏库，运行环境已就绪。";
        if (warning != null) text += "\n\n" + warning;
        doneName.setText(text);
        renderStep();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("step", step);
        if (source != null) outState.putString("source", source.getAbsolutePath());
        outState.putString("treeUri", treeUri != null ? treeUri.toString() : null);
        outState.putString("pickedName", pickedName);
        outState.putString("name", String.valueOf(nameInput.getText()));
        outState.putString("engine", detected != null ? detected.name() : null);
        outState.putBoolean("encrypted", encrypted);
        String files = String.valueOf(filesView.getText());
        try { outState.putInt("files", Integer.parseInt(files)); } catch (Throwable ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 只在真正退出时清暂存：旋转/重建（isFinishing()==false）后 source 还要继续用，
        // 删了就得把几 GB 重新拷一遍。未完成且被系统回收的残骸由下次打开时的 sweep 兜底。
        if (isFinishing() && step < 3) cleanupStaging();
    }

    private void fail(String message) {
        setBusy(false);
        cleanupStaging();
        // 暂存副本已被删：source 必须一起作废，否则用户在确认页再按「开始导入」会拿
        // 不存在的目录继续，step0 的 fail-fast 还会误报「缺少所有文件访问权限」。
        source = null;
        detected = null;
        next.setText(getString(R.string.galgame_import_start));
        toast(message);
    }

    private void setBusy(boolean b) {
        busy = b;
        next.setEnabled(!b);
        prev.setEnabled(!b);
        if (pickFolder != null) pickFolder.setEnabled(!b);
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

    private void openAllFilesAccessSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }
}
