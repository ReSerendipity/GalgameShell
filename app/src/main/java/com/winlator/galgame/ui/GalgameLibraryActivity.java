package com.winlator.galgame.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.FileUtils;
import com.winlator.galgame.EngineDetector;
import com.winlator.galgame.GalgameDiagnostics;
import com.winlator.galgame.GalgameLogs;
import com.winlator.galgame.GalgameSaveManager;
import com.winlator.galgame.ImportFlow;
import com.winlator.galgame.NativeRouteLauncher;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 游戏库 UI（Plan Part VIII §VIII.1「库 UI」）。
 *
 * 列出本外壳导入的游戏（容器名以 {@code galgame-} 前缀标识），提供：
 * 启动（复用官方 {@link XServerDisplayActivity}）、诊断向导、存档导出、日志查看；
 * 以及「导入游戏」入口（走 P1/P2/P4 流水线）。
 *
 * 完全新增，不改官方 Activity；文案走 galgame_strings.xml（AC-19）。
 */
public class GalgameLibraryActivity extends AppCompatActivity {

    private static final String CONTAINER_PREFIX = "galgame-";
    private static final String DEFAULT_IMPORT_PATH = "/sdcard/Download";

    private ContainerManager containerManager;
    private ArrayAdapter<String> adapter;

    private final List<Container> games = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_galgame_library);

        containerManager = new ContainerManager(this);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
        ListView list = findViewById(R.id.LVGalgames);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> showGameActions(position));

        Button importButton = findViewById(R.id.BTImport);
        importButton.setOnClickListener(v -> showImportDialog());

        Button aboutButton = findViewById(R.id.BTAbout);
        aboutButton.setOnClickListener(v -> showAbout());

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ---- 列表 ----

    private void refresh() {
        games.clear();
        labels.clear();

        List<Container> containers = containerManager.getContainers();
        if (containers != null) {
            for (Container c : containers) {
                String name = c.getName();
                if (name != null && name.startsWith(CONTAINER_PREFIX)) {
                    games.add(c);
                    String engine = c.getExtra("galgame_engine", "?");
                    labels.add(name.substring(CONTAINER_PREFIX.length()) + "  ·  " + engine);
                }
            }
        }
        adapter.notifyDataSetChanged();

        if (labels.isEmpty()) {
            Toast.makeText(this, R.string.galgame_library_empty, Toast.LENGTH_SHORT).show();
        }
    }

    private void showGameActions(int position) {
        if (position < 0 || position >= games.size()) return;
        final Container container = games.get(position);

        String[] items = {
                getString(R.string.galgame_action_launch),
                getString(R.string.galgame_action_diagnose),
                getString(R.string.galgame_action_export_save),
                getString(R.string.galgame_action_logs),
        };

        new AlertDialog.Builder(this)
                .setTitle(container.getName())
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: launchGame(container); break;
                        case 1: showDiagnostics(container); break;
                        case 2: exportSave(container); break;
                        case 3: showLogs(container); break;
                        default: break;
                    }
                })
                .setNegativeButton(R.string.galgame_cancel, null)
                .show();
    }

    /** 启动游戏：复用官方 XServerDisplayActivity（A 路由容器）。 */
    private void launchGame(Container container) {
        android.content.Intent intent = new android.content.Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        // A 路由：从 galgame_overlay.json 取启动 exe，注入 exec_path，
        // 让 XServerDisplayActivity boot 直接运行游戏（而非 fallback 到文件管理器桌面）。
        String exe = readOverlayExe(container);
        if (exe != null) intent.putExtra("exec_path", exe);
        startActivity(intent);
    }

    /** 读取容器根目录 galgame_overlay.json 的 exe 字段（unix 绝对路径），无则返回 null。 */
    private static String readOverlayExe(Container container) {
        try {
            File f = new File(container.getRootDir(), "galgame_overlay.json");
            if (!f.exists()) return null;
            JSONObject o = new JSONObject(FileUtils.readString(f));
            if (o.has("exe")) {
                String exe = o.getString("exe");
                return (exe != null && !exe.isEmpty()) ? exe : null;
            }
        } catch (Exception ignored) {
            // overlay 缺失/损坏不阻断启动，按默认（文件管理器桌面）处理
        }
        return null;
    }

    // ---- 诊断 / 日志 / 存档 ----

    private void showDiagnostics(Container container) {
        GalgameDiagnostics.Report report = GalgameDiagnostics.run(this, container);

        StringBuilder sb = new StringBuilder();
        for (GalgameDiagnostics.Check check : report.checks) {
            sb.append("[").append(check.severity).append("] ").append(check.title).append("\n");
            sb.append("  ").append(check.detail).append("\n");
            if (check.fix != null && !check.fix.isEmpty()) sb.append("  → ").append(check.fix).append("\n");
            sb.append("\n");
        }
        sb.append(report.summary());

        new AlertDialog.Builder(this)
                .setTitle(R.string.galgame_diag_title)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.galgame_ok, null)
                .show();
    }

    private void showLogs(Container container) {
        List<File> files = GalgameLogs.candidates(container);
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.galgame_logs_none, Toast.LENGTH_SHORT).show();
            return;
        }

        File logFile = files.get(0);
        List<String> lines = GalgameLogs.highlights(logFile, 40);
        if (lines.isEmpty()) lines = GalgameLogs.tail(logFile, 40);

        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append("\n");

        new AlertDialog.Builder(this)
                .setTitle(logFile.getName())
                .setMessage(sb.toString())
                .setPositiveButton(R.string.galgame_ok, null)
                .show();
    }

    private void exportSave(Container container) {
        String gameId = gameIdOf(container);
        GalgameSaveManager manager = new GalgameSaveManager(gameId);
        boolean ok = manager.exportBackup(GalgameSaveManager.defaultBackupDir());
        Toast.makeText(this,
                ok ? R.string.galgame_save_export_done : R.string.galgame_save_export_fail,
                Toast.LENGTH_SHORT).show();
    }

    // ---- 导入 ----

    private void showImportDialog() {
        final EditText input = new EditText(this);
        input.setHint(R.string.galgame_import_hint);
        input.setText(DEFAULT_IMPORT_PATH);

        new AlertDialog.Builder(this)
                .setTitle(R.string.galgame_import_title)
                .setView(input)
                .setPositiveButton(R.string.galgame_ok, (dialog, which) -> {
                    String path = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!path.isEmpty()) startImport(new File(path));
                })
                .setNegativeButton(R.string.galgame_cancel, null)
                .show();
    }

    private void startImport(final File source) {
        if (!source.isDirectory()) {
            Toast.makeText(this, R.string.galgame_import_invalid, Toast.LENGTH_SHORT).show();
            return;
        }

        final String gameId = sanitize(source.getName());

        final ImportFlow.ImportResult result;
        try {
            result = ImportFlow.run(source, gameId, this);
            Log.d("GalgameShell", "import engine=" + result.engine + " route=" + result.route + " gameId=" + gameId);
        }
        catch (Exception e) {
            Toast.makeText(this, getString(R.string.galgame_import_failed) + ": " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return;
        }

        // B 路由：原生播放器唤起
        if (result.route == EngineDetector.Route.B) {
            showNativeRouteDialog(result, source);
            return;
        }

        // A 路由：建每游戏容器 → stage（A3 复制 + B4 S: + P2 日文化）→ P4 存档重定向
        containerManager.createContainerAsync(result.containerData, (container) -> {
            if (container == null) {
                Toast.makeText(this, R.string.galgame_import_failed, Toast.LENGTH_LONG).show();
                return;
            }
            try {
                ImportFlow.stageGameFiles(container, result, source, this);

                GalgameSaveManager saveManager = new GalgameSaveManager(gameId);
                saveManager.redirectShellFolders(container);
                File stagedGameDir = new File(container.getRootDir(),
                        ".wine/drive_c/galgame/" + gameId);
                saveManager.symlinkPortableSaves(stagedGameDir);

                Toast.makeText(this, R.string.galgame_import_done, Toast.LENGTH_LONG).show();
                refresh();
            }
            catch (Exception e) {
                Toast.makeText(this, getString(R.string.galgame_import_failed) + ": " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---- B 路由对话框（Tier 分层 + 免责）----

    private void showNativeRouteDialog(final ImportFlow.ImportResult result, final File source) {
        final NativeRouteLauncher.Plan plan =
                NativeRouteLauncher.plan(this, result.engine, GalgameSettings.isTier2Enabled(this));

        if (plan.target == null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.galgame_route_b)
                    .setMessage(plan.message)
                    .setPositiveButton(R.string.galgame_ok, null)
                    .show();
            return;
        }

        String message = plan.message;
        if (plan.needsDisclaimer) {
            message += "\n\n" + getString(R.string.galgame_broute_disclaimer);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.galgame_route_b) + " · " + plan.target.displayName)
                .setMessage(message)
                .setNegativeButton(R.string.galgame_cancel, null);

        if (plan.ready()) {
            builder.setPositiveButton(R.string.galgame_action_launch, (dialog, which) -> {
                if (plan.scanDir != null) {
                    NativeRouteLauncher.prepareScanDir(source, plan.scanDir, result.gameId);
                }
                NativeRouteLauncher.launch(GalgameLibraryActivity.this, plan.target);
            });
        }
        else if (plan.needsDisclaimer && !plan.allowed) {
            builder.setPositiveButton(R.string.galgame_broute_enable, (dialog, which) -> {
                GalgameSettings.setTier2Enabled(GalgameLibraryActivity.this, true);
                showNativeRouteDialog(result, source);
            });
        }
        else {
            builder.setPositiveButton(R.string.galgame_ok, null);
        }

        builder.show();
    }

    // ---- 工具 ----

    /** 关于 / 源码获取（LGPL-2.1 源码提供义务，A6 / AC-18）。 */
    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.galgame_about)
                .setMessage(R.string.galgame_about_message)
                .setPositiveButton(R.string.galgame_ok, null)
                .show();
    }

    private static String gameIdOf(Container container) {
        String name = container.getName();
        if (name == null) return "";
        return name.startsWith(CONTAINER_PREFIX) ? name.substring(CONTAINER_PREFIX.length()) : name;
    }

    private static String sanitize(String name) {
        if (name == null || name.isEmpty()) return "game";
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
