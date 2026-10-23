package com.winlator;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.AppUtils;
import com.winlator.galgame.GalgameDiagnostics;
import com.winlator.galgame.GalgameLogs;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 诊断页（Swiss）：汇总 + 逐项状态 + 重新检查 + 完整日志。
 */
public class GalgameDiagnosticsActivity extends AppCompatActivity {

    public static final String EXTRA_CONTAINER = "galgame.container";
    public static final String EXTRA_TITLE = "galgame.title";

    private Container container;
    private LinearLayout checksView;
    private TextView numberView;
    private TextView labelView;
    private boolean running;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_galgame_diagnostics);

        container = findContainer(getIntent().getStringExtra(EXTRA_CONTAINER));
        String title = getIntent().getStringExtra(EXTRA_TITLE);

        ((TextView) findViewById(R.id.TVTopMeta)).setText(title != null ? title : "");
        findViewById(R.id.BTBack).setOnClickListener(v -> finish());
        checksView = findViewById(R.id.LLChecks);
        numberView = findViewById(R.id.TVSummaryNumber);
        labelView = findViewById(R.id.TVSummaryLabel);
        ((TextView) findViewById(R.id.TVSummaryMeta)).setText("GPU · 日文 · 视频 · 位宽 · 音频 · 存档 · 加密");

        View recheck = findViewById(R.id.BTRecheck);
        recheck.setOnClickListener(v -> { if (!running) runDiagnostics(); });
        findViewById(R.id.BTFullLogs).setOnClickListener(v -> showLogs());

        if (container == null) {
            numberView.setText("—");
            labelView.setText("需要 Winlator 容器才能诊断");
            findViewById(R.id.BTRecheck).setEnabled(false);
        } else {
            runDiagnostics();
        }
    }

    private Container findContainer(String name) {
        if (name == null) return null;
        List<Container> containers = new ContainerManager(this).getContainers();
        if (containers == null) return null;
        for (Container c : containers) if (name.equals(c.getName())) return c;
        return null;
    }

    private void runDiagnostics() {
        if (container == null) return;
        running = true;
        numberView.setText("…");
        labelView.setText("正在检查…");
        executor.execute(() -> {
            final GalgameDiagnostics.Report report;
            try {
                report = GalgameDiagnostics.run(getApplicationContext(), container);
            } catch (Throwable t) {
                main.post(() -> {
                    running = false;
                    labelView.setText("诊断执行失败：" + t.getMessage());
                });
                return;
            }
            main.post(() -> render(report));
        });
    }

    private void render(GalgameDiagnostics.Report report) {
        running = false;
        int problems = 0;
        for (GalgameDiagnostics.Check c : report.checks) {
            if (c.severity == GalgameDiagnostics.Severity.WARN || c.severity == GalgameDiagnostics.Severity.ERROR) problems++;
        }
        numberView.setText(String.format(Locale.US, "%02d", problems));
        numberView.setTextColor(ContextCompat.getColor(this,
                problems > 0 ? R.color.swiss_red : R.color.swiss_ink));
        labelView.setText(problems > 0 ? getString(R.string.galgame_diag_need_attention, problems)
                : getString(R.string.galgame_diag_all_pass));

        checksView.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (GalgameDiagnostics.Check c : report.checks) {
            View row = inflater.inflate(R.layout.swiss_check_row, checksView, false);
            ((TextView) row.findViewById(R.id.TVCheckTitle)).setText(c.title);
            ((TextView) row.findViewById(R.id.TVCheckMeta)).setText(c.detail != null ? c.detail : "");
            TextView status = row.findViewById(R.id.TVCheckStatus);
            View dot = row.findViewById(R.id.VStatusDot);

            boolean bad = c.severity == GalgameDiagnostics.Severity.WARN || c.severity == GalgameDiagnostics.Severity.ERROR;
            int color = ContextCompat.getColor(this, bad ? R.color.swiss_red : R.color.swiss_muted);
            status.setTextColor(color);
            dot.setBackgroundColor(ContextCompat.getColor(this, bad ? R.color.swiss_red : R.color.swiss_ink));

            switch (c.severity) {
                case OK: status.setText("通过"); break;
                case INFO: status.setText("提示"); break;
                case WARN: status.setText("警告"); break;
                case ERROR: status.setText("失败"); break;
            }
            checksView.addView(row);
        }
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

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
