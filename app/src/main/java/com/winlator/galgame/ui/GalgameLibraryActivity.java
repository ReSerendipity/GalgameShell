package com.winlator.galgame.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import android.os.Bundle;
import android.util.Log;
import android.widget.GridView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import com.winlator.GalgameImportActivity;
import com.winlator.R;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.galgame.GalgameDiagnostics;
import com.winlator.galgame.GalgameLibraryIndex;
import com.winlator.galgame.GalgameLocaleInjector;
import com.winlator.galgame.GalgameLogs;
import com.winlator.galgame.GalgameSaveManager;
import com.winlator.galgame.GalgameStoragePermission;
import com.winlator.galgame.GameLauncher;

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

    private ContainerManager containerManager;
    private GalgameLibraryAdapter adapter;
    private FloatingActionButton fabImport;

    private final List<GalgameLibraryAdapter.Item> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_galgame_library);

        containerManager = new ContainerManager(this);

        adapter = new GalgameLibraryAdapter(this, items);
        // 点封面即玩（对标 GameNative）；长按弹操作菜单。
        // 监听挂在 item view 自身（适配器内）——卡片根 clickable（涟漪）会吞掉
        // GridView 级别的 onItemClick/onItemLongClick，故不能挂在 GridView 上。
        adapter.setOnGameActionListener(new GalgameLibraryAdapter.OnGameActionListener() {
            @Override
            public void onGameClick(GalgameLibraryAdapter.Item item) { launchGame(item); }

            @Override
            public void onGameLongClick(GalgameLibraryAdapter.Item item) { showGameActions(item); }
        });
        GridView grid = findViewById(R.id.GVGalgames);
        grid.setAdapter(adapter);

        // 导入 FAB（现代主行动）+ 空态行动按钮
        fabImport = findViewById(R.id.FABImport);
        fabImport.setOnClickListener(v -> openImportWizard());
        // FAB 入场：缩放淡入
        fabImport.setScaleX(0f);
        fabImport.setScaleY(0f);
        fabImport.setAlpha(0f);
        fabImport.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(180).setDuration(260).start();

        View aboutButton = findViewById(R.id.BTAbout);
        aboutButton.setOnClickListener(v -> showAbout());

        View importEmpty = findViewById(R.id.BTImportEmpty);
        importEmpty.setOnClickListener(v -> openImportWizard());

        refresh();

        // 评估报告 #3：MANAGE_EXTERNAL_STORAGE 运行时引导（首次进入未授权时弹一次）
        GalgameStoragePermission.ensure(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ---- 列表 ----

    /**
     * 列表 = 容器条目 + 自有索引里的原生条目（与 {@code GalgameHomeFragment} 同口径）。
     *
     * <p>原生游戏走 Route.B，导入时不建容器（历史行为），以前因此永远进不了库；
     * 现在由 {@link GalgameLibraryIndex} 记档，这里合并进来一并展示。
     */
    private void refresh() {
        items.clear();

        java.util.Map<String, GalgameLibraryIndex.Entry> index = new java.util.HashMap<>();
        for (GalgameLibraryIndex.Entry e : GalgameLibraryIndex.load(this)) {
            index.put(e.gameId, e);
        }

        List<Container> containers = containerManager.getContainers();
        if (containers != null) {
            for (Container c : containers) {
                String name = c.getName();
                if (name == null || !name.startsWith(CONTAINER_PREFIX)) continue;
                String gameId = name.substring(CONTAINER_PREFIX.length());
                GalgameLibraryIndex.Entry e = index.get(gameId);
                items.add(GalgameLibraryAdapter.fromContainer(c, e != null ? e.preferredEngine : null));
            }
        }

        // 只补「没有容器、也没被上面覆盖」的原生条目
        for (GalgameLibraryIndex.Entry e : index.values()) {
            if (e.containerized) continue;
            boolean alreadyListed = false;
            for (GalgameLibraryAdapter.Item it : items) {
                if (it.gameId.equals(e.gameId)) { alreadyListed = true; break; }
            }
            if (alreadyListed) continue;
            items.add(GalgameLibraryAdapter.fromIndexEntry(getApplicationContext(), e));
        }

        adapter.notifyDataSetChanged();

        TextView subtitle = findViewById(R.id.TVSubtitle);
        if (subtitle != null && !items.isEmpty()) {
            subtitle.setText(getString(R.string.galgame_library_count, items.size()));
        }

        View empty = findViewById(R.id.LEmpty);
        GridView grid = findViewById(R.id.GVGalgames);
        boolean isEmpty = items.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        grid.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        // 空态已有「导入游戏」按钮，FAB 隐藏避免重复行动点
        if (fabImport != null) {
            fabImport.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        if (!isEmpty) {
            // 每次刷新重放错峰入场动画（衔接感）
            grid.startLayoutAnimation();
        }
    }

    /**
     * 长按菜单。容器专属操作（诊断 / 存档 / 日志）只对有容器的条目开放——
     * 原生游戏没有 wine 容器，这些按钮点了也只会报错。
     */
    private void showGameActions(final GalgameLibraryAdapter.Item item) {
        if (item == null) return;

        java.util.List<String> actions = new ArrayList<>();
        java.util.List<Integer> codes = new ArrayList<>();   // 0=启动 1=诊断 2=导出存档 3=导入存档 4=日志 5=切换运行方式

        actions.add(getString(R.string.galgame_action_launch));         codes.add(0);
        actions.add(getString(R.string.galgame_action_switch_engine));  codes.add(5);
        if (item.hasContainer()) {
            actions.add(getString(R.string.galgame_action_diagnose));       codes.add(1);
            actions.add(getString(R.string.galgame_action_export_save));    codes.add(2);
            actions.add(getString(R.string.galgame_action_import_save));    codes.add(3);
            actions.add(getString(R.string.galgame_action_logs));           codes.add(4);
        }

        String[] actionItems = actions.toArray(new String[0]);
        final java.util.List<Integer> finalCodes = codes;

        new AlertDialog.Builder(this)
                .setTitle(item.label)
                .setItems(actionItems, (dialog, which) -> {
                    if (which < 0 || which >= finalCodes.size()) return;
                    Container container = item.container;
                    switch (finalCodes.get(which)) {
                        case 0: launchGame(item); break;
                        case 1: showDiagnostics(container); break;
                        case 2: exportSave(container); break;
                        case 3: importSave(container); break;
                        case 4: showLogs(container); break;
                        case 5: showEnginePicker(item); break;
                        default: break;
                    }
                })
                .setNegativeButton(R.string.galgame_cancel, null)
                .show();
    }

    /** 「切换运行方式」：把玩家的偏好记进自有索引，下次启动按它裁决。 */
    private void showEnginePicker(final GalgameLibraryAdapter.Item item) {
        final java.util.List<GameLauncher.Option> options =
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
                    snack(picked.id == null
                            ? getString(R.string.galgame_switch_engine_reset)
                            : getString(R.string.galgame_switch_engine_done, picked.label));
                    dialog.dismiss();
                    refresh();
                })
                .setNegativeButton(R.string.galgame_cancel, null)
                .show();
    }

    /**
     * 启动游戏：走统一裁决入口 {@link GameLauncher}。
     *
     * <p>此前这里无条件 {@code startActivity(XServerDisplayActivity)}，已容器化的 Kirikiri /
     * Ren'Py 从库里启动会被一刀切送进 wine。现在由 {@link GameLauncher} 按
     * 「用户 override → 启动前重检测 → 登记值 → 容器兜底」裁决。
     */
    private void launchGame(final GalgameLibraryAdapter.Item item) {
        if (item == null) return;

        // 原生游戏本体仍在原地：目录没了就得重新导入，不能让引擎自己去猜
        if (!item.hasContainer() && (item.gameDir == null || !item.gameDir.isDirectory())) {
            snackLong(getString(R.string.galgame_source_missing));
            return;
        }

        snack(getString(R.string.galgame_launching, item.label));

        GameLauncher.Result result = GameLauncher.launch(
                this, item.gameDir, item.container, item.engineName, item.preferredEngine);

        if (!result.launched) {
            snackLong(result.message != null ? result.message : getString(R.string.galgame_import_failed));
            return;
        }

        GalgameLibraryIndex.touchPlayedAsync(getApplicationContext(), item.gameId);
        // 界面切换过渡（淡入淡出，配合主题 windowAnimationStyle）
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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

        boolean needsReinject = false;
        for (GalgameDiagnostics.Check check : report.checks) {
            if (("locale".equals(check.id) || "font".equals(check.id))
                    && (check.severity == GalgameDiagnostics.Severity.WARN
                        || check.severity == GalgameDiagnostics.Severity.ERROR)) {
                needsReinject = true;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.galgame_diag_title)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.galgame_ok, null);

        if (needsReinject) {
            builder.setNeutralButton(R.string.galgame_diag_reinject, (dialog, which) -> {
                GalgameLocaleInjector.Report re = GalgameLocaleInjector.reapply(container, GalgameLibraryActivity.this);
                snack(getString(re != null ? R.string.galgame_diag_reinject_done
                        : R.string.galgame_diag_reinject_fail));
                showDiagnostics(container);
            });
        }

        builder.show();
    }

    private void showLogs(Container container) {
        List<File> files = GalgameLogs.candidates(container);
        List<File> crashes = GalgameLogs.crashReports(this);   // v2.9 评估 P1-1：崩溃记录接入
        if (files.isEmpty() && crashes.isEmpty()) {
            snack(getString(R.string.galgame_logs_none));
            return;
        }
        if (files.isEmpty()) {            // 无运行日志但有崩溃记录：直接展示最新一份
            showCrashReport(crashes.get(0));
            return;
        }

        File logFile = files.get(0);
        List<String> lines = GalgameLogs.highlights(logFile, 40);
        if (lines.isEmpty()) lines = GalgameLogs.tail(logFile, 40);

        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append("\n");

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(logFile.getName())
                .setMessage(sb.toString())
                .setPositiveButton(R.string.galgame_ok, null);
        if (!crashes.isEmpty()) {         // 有崩溃记录时提供直达入口（最新一份）
            builder.setNeutralButton(R.string.galgame_logs_crashes, (d, w) -> showCrashReport(crashes.get(0)));
        }
        builder.show();
    }

    /** 展示单份崩溃记录（v2.9 评估 P1-1：本地崩溃采集 crashes/<versionCode>/ 的查看入口）。 */
    private void showCrashReport(File crashFile) {
        List<String> lines = GalgameLogs.tail(crashFile, 40);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append("\n");
        new AlertDialog.Builder(this)
                .setTitle(crashFile.getName())
                .setMessage(sb.toString())
                .setPositiveButton(R.string.galgame_ok, null)
                .show();
    }

    private void exportSave(Container container) {
        String gameId = gameIdOf(container);
        GalgameSaveManager manager = new GalgameSaveManager(gameId);
        boolean ok = manager.exportBackup(GalgameSaveManager.defaultBackupDir());
        snack(getString(ok ? R.string.galgame_save_export_done : R.string.galgame_save_export_fail));
    }

    /** 从备份目录恢复存档（M3）：列出本游戏的历史备份，选一则恢复到 S: 盘。 */
    private void importSave(Container container) {
        String gameId = gameIdOf(container);
        GalgameSaveManager manager = new GalgameSaveManager(gameId);

        File backupRoot = GalgameSaveManager.defaultBackupDir();
        File[] all = backupRoot.listFiles();
        if (all == null) {
            snack(getString(R.string.galgame_save_no_backup));
            return;
        }

        List<File> candidates = new ArrayList<>();
        for (File f : all) {
            if (f.isDirectory() && f.getName().startsWith(gameId + "-")) candidates.add(f);
        }
        if (candidates.isEmpty()) {
            snack(getString(R.string.galgame_save_no_backup));
            return;
        }

        // 按时间戳倒序（最新的在前）
        candidates.sort((a, b) -> b.getName().compareTo(a.getName()));
        String[] names = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) names[i] = candidates.get(i).getName();

        new AlertDialog.Builder(this)
                .setTitle(R.string.galgame_action_import_save)
                .setItems(names, (dialog, which) -> {
                    boolean ok = manager.restoreBackup(candidates.get(which));
                    snack(getString(ok ? R.string.galgame_save_restored
                            : R.string.galgame_save_export_fail));
                })
                .setNegativeButton(R.string.galgame_cancel, null)
                .show();
    }

    // ---- 导入 ----

    /**
     * 导入只有一条入口：{@link GalgameImportActivity} 向导（系统文件夹选择器 → 检测 → 确认）。
     * 库内不再自带一份「手输路径 + 自己跑 ImportFlow」的实现——两套入口会让同一批导入修复
     * 只落到其中一套（2026-10-26 真机：手输路径把套娃目录填错 → UNKNOWN → 报「启动文件缺失」）。
     */
    private void openImportWizard() {
        startActivity(new Intent(this, GalgameImportActivity.class));
    }

    /** 友好提示条（现代 Snackbar，替代 Toast）。 */
    private void snack(String message) {
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show();
    }

    private void snackLong(String message) {
        View root = findViewById(android.R.id.content);
        if (root == null) return;
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
    }

    // ---- 工具 ----

    /** 关于 / 源码获取（LGPL-2.1 源码提供义务，A6 / AC-18）。 */
    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.galgame_about)
                .setMessage(R.string.galgame_about_message)
                .setPositiveButton(R.string.galgame_ok, null)
                .setNeutralButton(R.string.galgame_privacy_title, (dialog, which) -> showPrivacy())
                .show();
    }

    /** 隐私说明（评估报告 #3）：本地运行、零采集的一句话声明。 */
    private void showPrivacy() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.galgame_privacy_title)
                .setMessage(R.string.galgame_privacy_message)
                .setPositiveButton(R.string.galgame_ok, null)
                .show();
    }

    private static String gameIdOf(Container container) {
        String name = container.getName();
        if (name == null) return "";
        return name.startsWith(CONTAINER_PREFIX) ? name.substring(CONTAINER_PREFIX.length()) : name;
    }

}
