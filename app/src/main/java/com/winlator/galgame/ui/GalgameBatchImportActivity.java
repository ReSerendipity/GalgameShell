package com.winlator.galgame.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.R;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.galgame.EngineDetector;
import com.winlator.galgame.GalgameIds;
import com.winlator.galgame.GalgameLibraryIndex;
import com.winlator.galgame.GalgameNativeStore;
import com.winlator.galgame.GalgameSaveManager;
import com.winlator.galgame.ImportFlow;
import com.winlator.galgame.SafScanner;
import com.winlator.galgame.SafStager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 批量导入：选一个目录 → 扫描出候选游戏 → 勾选 → 串行导入。
 *
 * <p>与单个导入向导的分工：向导管「一次一个、可以人工确认引擎/标题」；这里管「一批，
 * 默认全收」。真正的判定与落地全部复用 {@link ImportFlow} / {@link SafStager} /
 * {@link GalgameNativeStore}，本类只做排队与状态汇报，避免又出现两份导入实现。
 *
 * <p>串行而非并发：容器创建是主线程异步回调、复制是重 IO，并发会让进度与失败归因
 * 混在一起；一批通常也就几个到十几个，串行足够。
 *
 * <p>批量不走「内置引擎立即启动」：如果这里挨个拉起 KR2/Ren'Py，用户会被连续弹窗打断。
 * 导入完成后统一回游戏库，由用户自己点。
 */
public class GalgameBatchImportActivity extends AppCompatActivity {

    private static final int REQ_PICK_FOLDER = 72;

    private static final class Row {
        final SafScanner.Candidate candidate;
        boolean checked = true;
        CheckBox box;
        TextView state;

        Row(SafScanner.Candidate candidate) {
            this.candidate = candidate;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Set<String> usedIds = new HashSet<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private Button pickBtn;
    private Button startBtn;
    private Button selectAllBtn;
    private TextView statusText;
    private TextView progressText;
    private LinearLayout list;
    private ContainerManager containerManager;

    private boolean running;
    private boolean allSelected = true;
    private int total;
    private int failed;
    private int succeeded;
    private Uri treeUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_galgame_batch_import);
        containerManager = new ContainerManager(this);
        pickBtn = findViewById(R.id.BTPickScanFolder);
        startBtn = findViewById(R.id.BTBatchStart);
        selectAllBtn = findViewById(R.id.BTSelectAll);
        statusText = findViewById(R.id.TVScanStatus);
        progressText = findViewById(R.id.TVBatchProgress);
        list = findViewById(R.id.LLScanResults);

        findViewById(R.id.BTBack).setOnClickListener(v -> {
            if (!running) finish();
        });
        pickBtn.setOnClickListener(v ->
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_PICK_FOLDER));
        selectAllBtn.setOnClickListener(v -> toggleAll());
        startBtn.setOnClickListener(v -> startBatch());
        startBtn.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_FOLDER || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
            // 本次会话内仍可读；扫描是即时的，不需要跨进程保留
        }
        treeUri = uri;
        scan(uri);
    }

    // ---- 扫描 ----

    private void scan(final Uri uri) {
        rows.clear();
        list.removeAllViews();
        startBtn.setEnabled(false);
        statusText.setText(R.string.galgame_batch_scanning);
        io.execute(() -> {
            List<SafScanner.Candidate> found;
            String error = null;
            try {
                found = SafScanner.scan(getApplicationContext(), uri);
            } catch (Exception e) {
                found = new ArrayList<>();
                error = String.valueOf(e.getMessage());
            }
            final List<SafScanner.Candidate> f = found;
            final String err = error;
            main.post(() -> render(f, err));
        });
    }

    private void render(List<SafScanner.Candidate> found, String error) {
        rows.clear();
        list.removeAllViews();
        for (SafScanner.Candidate c : found) rows.add(new Row(c));
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final Row row : rows) {
            View v = inflater.inflate(R.layout.item_batch_candidate, list, false);
            row.box = v.findViewById(R.id.CBCandidate);
            row.state = v.findViewById(R.id.TVCandidateHint);
            TextView name = v.findViewById(R.id.TVCandidateName);
            name.setText(row.candidate.name);
            row.state.setText(row.candidate.hint + " · " + getString(R.string.galgame_batch_state_wait));
            row.box.setChecked(true);
            row.box.setOnCheckedChangeListener((b, checked) -> row.checked = checked);
            v.setOnClickListener(x -> row.box.setChecked(!row.box.isChecked()));
            list.addView(v);
        }
        allSelected = true;
        selectAllBtn.setText(R.string.galgame_batch_clear);
        if (error != null) {
            statusText.setText(getString(R.string.galgame_batch_scan_failed, error));
        } else if (found.isEmpty()) {
            statusText.setText(R.string.galgame_batch_none);
        } else {
            statusText.setText(getString(R.string.galgame_batch_found, found.size()));
        }
        startBtn.setEnabled(!rows.isEmpty());
    }

    private void toggleAll() {
        allSelected = !allSelected;
        for (Row row : rows) if (row.box != null) row.box.setChecked(allSelected);
        selectAllBtn.setText(allSelected
                ? R.string.galgame_batch_clear : R.string.galgame_batch_select_all);
    }

    // ---- 串行导入 ----

    private void startBatch() {
        final List<Row> queue = new ArrayList<>();
        for (Row row : rows) if (row.checked) queue.add(row);
        if (queue.isEmpty()) {
            toast(getString(R.string.galgame_batch_pick_none));
            return;
        }
        running = true;
        total = queue.size();
        failed = 0;
        succeeded = 0;
        pickBtn.setEnabled(false);
        startBtn.setEnabled(false);
        selectAllBtn.setEnabled(false);
        progressText.setText(getString(R.string.galgame_batch_running, 0, total));
        usedIds.clear();
        usedIds.addAll(ImportFlow.existingGameIds(getApplicationContext()));
        importNext(queue, 0);
    }

    private void importNext(final List<Row> queue, final int index) {
        if (index >= queue.size()) {
            finishBatch();
            return;
        }
        final Row row = queue.get(index);
        setState(row, getString(R.string.galgame_batch_state_stage));
        progressText.setText(getString(R.string.galgame_batch_running, index + 1, total));

        io.execute(() -> {
            File staged = null;
            try {
                staged = SafStager.stageTree(getApplicationContext(), row.candidate.uri,
                        row.candidate.name, null);
                final String title = row.candidate.name;
                final String gameId = GalgameIds.gameId(title, usedIds);
                usedIds.add(gameId);

                final ImportFlow.ImportResult result =
                        ImportFlow.run(staged, gameId, getApplicationContext());
                final File stagedDir = staged;

                if (result.engine == EngineDetector.Engine.UNKNOWN) {
                    SafStager.deleteRecursively(stagedDir);
                    main.post(() -> {
                        failRow(row, getString(R.string.galgame_batch_unknown_engine));
                        importNext(queue, index + 1);
                    });
                    return;
                }

                setStateOnMain(row, getString(R.string.galgame_batch_state_import));

                if (result.route == EngineDetector.Route.B) {
                    try {
                        File stored = GalgameNativeStore.store(getApplicationContext(),
                                result.gameId, stagedDir, null);
                        GalgameLibraryIndex.putNative(getApplicationContext(), result.gameId,
                                title, result.engine, stored);
                        SafStager.deleteRecursively(stagedDir);
                        main.post(() -> {
                            okRow(row);
                            importNext(queue, index + 1);
                        });
                    } catch (Exception e) {
                        SafStager.deleteRecursively(stagedDir);
                        final String msg = firstLine(e);
                        main.post(() -> {
                            failRow(row, msg);
                            importNext(queue, index + 1);
                        });
                    }
                    return;
                }

                // A 路由：容器创建必须在有 Looper 的线程发起
                main.post(() -> containerManager.createContainerAsync(result.containerData,
                        (Callback<Container>) container -> {
                            if (container == null) {
                                SafStager.deleteRecursively(stagedDir);
                                failRow(row, getString(R.string.galgame_batch_no_container));
                                importNext(queue, index + 1);
                                return;
                            }
                            io.execute(() -> stageIntoContainer(row, container, result, stagedDir,
                                    title, queue, index));
                        }));
            } catch (Exception e) {
                if (staged != null) SafStager.deleteRecursively(staged);
                final String msg = firstLine(e);
                main.post(() -> {
                    failRow(row, msg);
                    importNext(queue, index + 1);
                });
            }
        });
    }

    private void stageIntoContainer(Row row, Container container, ImportFlow.ImportResult result,
                                    File stagedDir, String title, List<Row> queue, int index) {
        try {
            ImportFlow.stageGameFiles(container, result, stagedDir, getApplicationContext(), null);
            GalgameSaveManager saveManager = new GalgameSaveManager(result.gameId);
            saveManager.redirectShellFolders(container);
            GalgameLibraryIndex.markContainerized(getApplicationContext(), result.gameId,
                    title, result.engine.name());
            File stagedGame = new File(container.getRootDir(),
                    ".wine/drive_c/galgame/" + result.gameId);
            saveManager.symlinkPortableSaves(stagedGame);
            SafStager.deleteRecursively(stagedDir);
            final boolean exeFound = result.gameExe != null;
            main.post(() -> {
                okRow(row);
                if (!exeFound) setState(row, getString(R.string.galgame_batch_state_no_exe));
                importNext(queue, index + 1);
            });
        } catch (Exception e) {
            // 与单个导入同口径：失败连容器一起删，别留孤儿
            com.winlator.core.FileUtils.delete(container.getRootDir());
            SafStager.deleteRecursively(stagedDir);
            final String msg = firstLine(e);
            main.post(() -> {
                failRow(row, msg);
                importNext(queue, index + 1);
            });
        }
    }

    private void finishBatch() {
        running = false;
        pickBtn.setEnabled(true);
        selectAllBtn.setEnabled(true);
        startBtn.setEnabled(!rows.isEmpty());
        int skipped = total - succeeded;
        progressText.setText(getString(R.string.galgame_batch_done, succeeded, skipped));
        toast(getString(R.string.galgame_batch_done, succeeded, skipped));
    }

    // ---- 状态 ----

    private void setStateOnMain(Row row, String text) {
        main.post(() -> setState(row, text));
    }

    private void setState(Row row, String text) {
        if (row.state == null) return;
        row.state.setText(row.candidate.hint + " · " + text);
    }

    private void okRow(Row row) {
        succeeded++;
        setState(row, getString(R.string.galgame_batch_state_ok));
    }

    private void failRow(Row row, String reason) {
        failed++;
        setState(row, getString(R.string.galgame_batch_state_fail, reason));
    }

    private static String firstLine(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) m = e.getClass().getSimpleName();
        int nl = m.indexOf('\n');
        return nl > 0 ? m.substring(0, nl) : m;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
