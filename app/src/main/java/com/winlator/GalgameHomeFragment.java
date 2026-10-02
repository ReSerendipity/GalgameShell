package com.winlator;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.galgame.EngineDetector;
import com.winlator.galgame.GalgameDiagnostics;
import com.winlator.galgame.GalgameLaunchHelper;
import com.winlator.galgame.GalgameLocaleInjector;
import com.winlator.galgame.GalgameLogs;
import com.winlator.galgame.GalgameSaveManager;
import com.winlator.galgame.ui.GalgameSettings;
import com.winlator.galgame.ImportFlow;
import com.winlator.galgame.NativeRouteLauncher;
import com.winlator.galgame.ui.GalgameLibraryAdapter;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.winlator.galgame.engine.BuiltinEngine;
import com.winlator.galgame.engine.BuiltinEngineRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GalgameShell R2 游戏库首页 Fragment（移植自 {@code GalgameLibraryActivity} 的数据层）。
 *
 * <p>列出本外壳导入的游戏（容器名以 {@code galgame-} 前缀标识），提供：启动（复用官方
 * {@link XServerDisplayActivity}）、诊断向导、存档导出/导入、日志查看，以及「导入游戏」入口。
 * 复用 {@link GalgameLibraryAdapter} 与 {@code galgame_progress_dialog} 等既有资源。
 */
public class GalgameHomeFragment extends Fragment {

    private static final String CONTAINER_PREFIX = "galgame-";
    private static final String DEFAULT_IMPORT_PATH = "/sdcard/Download";

    private ContainerManager containerManager;
    private GalgameLibraryAdapter adapter;
    private FloatingActionButton fabImport;
    private View rootView;

    private final List<GalgameLibraryAdapter.Item> items = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable android.view.ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_galgame_home, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        containerManager = new ContainerManager(requireContext());

        adapter = new GalgameLibraryAdapter(requireContext(), items);
        // 点封面即玩（对标 GameNative）；长按弹操作菜单。监听挂在 item view 自身（适配器内）。
        adapter.setOnGameActionListener(new GalgameLibraryAdapter.OnGameActionListener() {
            @Override
            public void onGameClick(Container container) { launchGame(container); }

            @Override
            public void onGameLongClick(Container container) { showGameActions(container); }
        });
        GridView grid = view.findViewById(R.id.GVGalgames);
        grid.setAdapter(adapter);

        // 导入 FAB（现代主行动）+ 空态行动按钮
        fabImport = view.findViewById(R.id.FABImport);
        fabImport.setOnClickListener(v -> showImportDialog());
        // FAB 入场：缩放淡入
        fabImport.setScaleX(0f);
        fabImport.setScaleY(0f);
        fabImport.setAlpha(0f);
        fabImport.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(180).setDuration(260).start();

        view.findViewById(R.id.BTAbout).setOnClickListener(v -> showAbout());
        view.findViewById(R.id.BTImportEmpty).setOnClickListener(v -> showImportDialog());

        refresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    // ---- 列表 ----

    private void refresh() {
        items.clear();

        List<Container> containers = containerManager.getContainers();
        if (containers != null) {
            for (Container c : containers) {
                String name = c.getName();
                if (name != null && name.startsWith(CONTAINER_PREFIX)) {
                    String gameId = name.substring(CONTAINER_PREFIX.length());
                    File gameDir = new File(c.getRootDir(), ".wine/drive_c/galgame/" + gameId);
                    File exe = overlayExe(c);
                    String engine = c.getExtra("galgame_engine", "?");
                    items.add(new GalgameLibraryAdapter.Item(c, gameId, engine, gameDir, exe));
                }
            }
        }
        adapter.notifyDataSetChanged();

        TextView subtitle = rootView.findViewById(R.id.TVSubtitle);
        if (subtitle != null && !items.isEmpty()) {
            subtitle.setText(getString(R.string.galgame_library_count, items.size()));
        }

        View empty = rootView.findViewById(R.id.LEmpty);
        GridView grid = rootView.findViewById(R.id.GVGalgames);
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

    private void showGameActions(Container container) {
        if (container == null) return;

        String[] actionItems = {
                getString(R.string.galgame_action_launch),
                getString(R.string.galgame_action_diagnose),
                getString(R.string.galgame_action_export_save),
                getString(R.string.galgame_action_import_save),
                getString(R.string.galgame_action_logs),
        };

        new AlertDialog.Builder(requireContext())
                .setTitle(container.getName())
                .setItems(actionItems, (dialog, which) -> {
                    switch (which) {
                        case 0: launchGame(container); break;
                        case 1: showDiagnostics(container); break;
                        case 2: exportSave(container); break;
                        case 3: importSave(container); break;
                        case 4: showLogs(container); break;
                        default: break;
                    }
                })
                .setNegativeButton(R.string.galgame_cancel, null)
                .show();
    }

    /** 启动游戏：复用官方 XServerDisplayActivity（A 路由容器）。 */
    private void launchGame(Container container) {
        // B 修复（2026-09-28）：启动前校验启动 exe 是否落盘；缺失则提示并取消启动，
        // 避免 wine 跑空路径立即退出后静默弹回游戏库（「打开游戏进不去」根因）。
        if (GalgameLaunchHelper.isExecMissing(container)) {
            snack(getString(R.string.galgame_exe_missing));
            return;
        }

        snack(getString(R.string.galgame_launching, gameIdOf(container)));

        Intent intent = new Intent(requireActivity(), XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        // A 路由：注入 overlay 的 exe，boot 直接运行游戏（与容器页启动共用同一接线）
        GalgameLaunchHelper.injectExecPath(container, intent);
        startActivity(intent);
        // 界面切换过渡（淡入淡出，配合主题 windowAnimationStyle）
        requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ---- 诊断 / 日志 / 存档 ----

    private void showDiagnostics(Container container) {
        GalgameDiagnostics.Report report = GalgameDiagnostics.run(requireContext(), container);

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

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.galgame_diag_title)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.galgame_ok, null);

        if (needsReinject) {
            builder.setNeutralButton(R.string.galgame_diag_reinject, (dialog, which) -> {
                GalgameLocaleInjector.Report re = GalgameLocaleInjector.reapply(container, requireActivity());
                snack(getString(re != null ? R.string.galgame_diag_reinject_done
                        : R.string.galgame_diag_reinject_fail));
                showDiagnostics(container);
            });
        }

        builder.show();
    }

    private void showLogs(Container container) {
        List<File> files = GalgameLogs.candidates(container);
        if (files.isEmpty()) {
            snack(getString(R.string.galgame_logs_none));
            return;
        }

        File logFile = files.get(0);
        List<String> lines = GalgameLogs.highlights(logFile, 40);
        if (lines.isEmpty()) lines = GalgameLogs.tail(logFile, 40);

        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append("\n");

        new AlertDialog.Builder(requireContext())
                .setTitle(logFile.getName())
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

        new AlertDialog.Builder(requireContext())
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

    private void showImportDialog() {
        final EditText input = new EditText(requireContext());
        input.setHint(R.string.galgame_import_hint);
        input.setText(DEFAULT_IMPORT_PATH);

        new AlertDialog.Builder(requireContext())
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
            snack(getString(R.string.galgame_import_invalid));
            return;
        }

        // 后台线程内复用同一宿主引用，避免反复 requireActivity()（脱离时抛异常）
        final android.app.Activity host = requireActivity();
        final String gameId = sanitize(source.getName());
        showProgressDialog(getString(R.string.galgame_import_step_detect));

        // ImportFlow.run（引擎检测）与 stageGameFiles（复制游戏文件）都是重 IO，移到后台线程执行，
        // 主线程只做对话框/Toast/refresh（此前会卡死主线程）。
        final ExecutorService exec = Executors.newSingleThreadExecutor();
        final android.content.Context appCtx = requireContext().getApplicationContext();

        exec.execute(() -> {
            final ImportFlow.ImportResult result;
            try {
                result = ImportFlow.run(source, gameId, appCtx);
                Log.d("GalgameShell", "import engine=" + result.engine + " route=" + result.route + " gameId=" + gameId);
            }
            catch (Exception e) {
                host.runOnUiThread(() -> {
                    dismissProgressDialog();
                    snackLong(getString(R.string.galgame_import_failed) + ": " + e.getMessage());
                });
                return;
            }

            // B 路由：原生播放器唤起
            if (result.route == EngineDetector.Route.B) {
                host.runOnUiThread(() -> {
                    dismissProgressDialog();
                    showNativeRouteDialog(result, source);
                });
                return;
            }

            host.runOnUiThread(() -> setProgress(40, getString(R.string.galgame_import_step_create)));

            // A 路由：建每游戏容器 → stage → P4 存档重定向
            containerManager.createContainerAsync(result.containerData, (container) -> {
                if (container == null) {
                    host.runOnUiThread(() -> {
                        dismissProgressDialog();
                        snackLong(getString(R.string.galgame_import_failed));
                    });
                    return;
                }
                exec.execute(() -> {
                    try {
                        host.runOnUiThread(() -> setProgress(70, getString(R.string.galgame_import_step_copy)));
                        ImportFlow.stageGameFiles(container, result, source, appCtx);

                        GalgameSaveManager saveManager = new GalgameSaveManager(gameId);
                        saveManager.redirectShellFolders(container);
                        File stagedGameDir = new File(container.getRootDir(),
                                ".wine/drive_c/galgame/" + gameId);
                        saveManager.symlinkPortableSaves(stagedGameDir);

                        // C 修复（2026-09-28）：拷贝完成但找不到启动 exe 时，明确告警而非假成功。
                        // 否则 overlay 无 exe → 启动静默弹回文件管理器（或经 B 修复拦截），用户无从知晓。
                        final boolean exeFound = result.gameExe != null;
                        host.runOnUiThread(() -> {
                            setProgress(100, getString(R.string.galgame_import_step_done));
                            dismissProgressDialog();
                            snackLong(exeFound ? getString(R.string.galgame_import_done)
                                              : getString(R.string.galgame_import_no_exe));
                            refresh();
                        });
                    }
                    catch (Exception e) {
                        host.runOnUiThread(() -> {
                            dismissProgressDialog();
                            snackLong(getString(R.string.galgame_import_failed) + ": " + e.getMessage());
                        });
                    }
                });
            });
        });
    }

    // ---- 导入进度对话框（确定进度条 + 百分比 + 平滑推进）----

    private AlertDialog progressDialog;
    private TextView progressTextView;
    private TextView progressPercentView;
    private ProgressBar progressBar;

    private void showProgressDialog(String text) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.galgame_progress_dialog, null);
        progressTextView = view.findViewById(R.id.TVProgressText);
        progressPercentView = view.findViewById(R.id.TVProgressPercent);
        progressBar = view.findViewById(R.id.PBImport);
        progressTextView.setText(text);
        if (progressBar != null) progressBar.setProgress(0);
        if (progressPercentView != null) progressPercentView.setText("0%");

        progressDialog = new AlertDialog.Builder(requireContext())
                .setView(view)
                .setCancelable(false)
                .create();
        progressDialog.show();
        // 去掉 AlertDialog 默认的方角背景，露出自定义圆角
        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        setProgressSmooth(12);
    }

    /** 推进到指定阶段：更新文案并平滑推进进度条（避免生硬跳变）。 */
    private void setProgress(int percent, String text) {
        if (progressTextView != null && text != null) progressTextView.setText(text);
        setProgressSmooth(percent);
    }

    private void setProgressText(String text) {
        if (progressTextView != null) progressTextView.setText(text);
    }

    /** 进度条平滑动画：从当前值补间到目标值，同步刷新百分比文本。 */
    private void setProgressSmooth(int target) {
        if (progressBar == null) return;
        int from = progressBar.getProgress();
        ValueAnimator animator = ValueAnimator.ofInt(from, target);
        animator.setDuration(450);
        animator.addUpdateListener(a -> {
            int v = (Integer) a.getAnimatedValue();
            progressBar.setProgress(v);
            if (progressPercentView != null) progressPercentView.setText(v + "%");
        });
        animator.start();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        progressDialog = null;
        progressTextView = null;
        progressPercentView = null;
        progressBar = null;
    }

    /** 友好提示条（现代 Snackbar，替代 Toast）。 */
    private void snack(String message) {
        View root = requireActivity().findViewById(android.R.id.content);
        if (root == null) return;
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show();
    }

    private void snackLong(String message) {
        View root = requireActivity().findViewById(android.R.id.content);
        if (root == null) return;
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
    }

    // ---- B 路由对话框（Tier 分层 + 免责）----

    private void showNativeRouteDialog(final ImportFlow.ImportResult result, final File source) {
        // GalgameShell：内置原生引擎优先——检测到对应引擎且运行时已随包打包时，
        // 直接用集成在我们自己 APK 里的引擎跑，不再唤起/安装第三方播放器。
        BuiltinEngine builtin = BuiltinEngineRegistry.resolve(result.engine);
        if (builtin != null && builtin.isAvailable(requireContext())) {
            if (builtin.launch(requireContext(), source)) return;
            Snackbar.make(requireView(), "内置引擎启动失败：" + builtin.displayName,
                    Snackbar.LENGTH_LONG).show();
            return;
        }

        final NativeRouteLauncher.Plan plan =
                NativeRouteLauncher.plan(requireContext(), result.engine, GalgameSettings.isTier2Enabled(requireContext()));

        if (plan.target == null) {
            new AlertDialog.Builder(requireContext())
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

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.galgame_route_b) + " · " + plan.target.displayName)
                .setMessage(message)
                .setNegativeButton(R.string.galgame_cancel, null);

        if (plan.ready()) {
            builder.setPositiveButton(R.string.galgame_action_launch, (dialog, which) -> {
                if (plan.scanDir != null) {
                    NativeRouteLauncher.prepareScanDir(source, plan.scanDir, result.gameId);
                }
                NativeRouteLauncher.launch(requireActivity(), plan.target);
            });
        }
        else if (plan.needsDisclaimer && !plan.allowed) {
            builder.setPositiveButton(R.string.galgame_broute_enable, (dialog, which) -> {
                GalgameSettings.setTier2Enabled(requireContext(), true);
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
        new AlertDialog.Builder(requireContext())
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

    /** 读容器 galgame_overlay.json 的 exe 路径（供封面适配器提取 exe 图标）。 */
    private static File overlayExe(Container container) {
        File f = new File(container.getRootDir(), "galgame_overlay.json");
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1 << 13];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            JSONObject o = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
            String exe = o.optString("exe", null);
            return (exe != null && !exe.isEmpty()) ? new File(exe) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitize(String name) {
        if (name == null || name.isEmpty()) return "game";
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
