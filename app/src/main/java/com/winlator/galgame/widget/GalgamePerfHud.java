package com.winlator.galgame.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.winlator.container.Container;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * GalgameShell 性能浮窗（2026-10-25 第五轮）：上游 FrameRating 只有三档且行项固定，
 * 本类提供<b>逐项可选</b>的系统指标浮窗——用户在游戏内抽屉「性能显示」多选：
 * FPS / CPU 使用率 / CPU 频率 / 系统内存 / 游戏进程内存 / GPU 型号 / 负载 / 整机功耗。
 *
 * <p>数据源全部免 root：FPS 由宿主按帧回调 {@link #onFrame()}（XServerDisplayActivity
 * 的 frameRatingWindow 帧链，与上游 FrameRating 同一来源）；GPU 型号由宿主注入
 * {@link #setGPUInfo(String)}（_NET_WM_GPU_INFO 窗口属性）；其余自行采样
 * （/proc/stat、cpufreq sysfs、ActivityManager、BatteryManager、/proc loadavg、
 * /proc/&lt;pid&gt;/status 的 box64/wine 进程 RSS）。零改上游文件，与组件升级解耦。
 *
 * <p>选择持久化在容器 extra {@link #EXTRA_ITEMS}（逗号分隔 key）；空 = 不显示。
 */
public final class GalgamePerfHud {
    public static final String EXTRA_ITEMS = "galgame_perf_items";

    public static final String KEY_FPS = "fps";
    public static final String KEY_CPU = "cpu";
    public static final String KEY_CPU_FREQ = "cpuFreq";
    public static final String KEY_RAM = "ram";
    public static final String KEY_PROC = "proc";
    public static final String KEY_GPU = "gpu";
    public static final String KEY_LOAD = "load";
    public static final String KEY_POWER = "power";
    public static final String KEY_CORES = "cores";
    public static final String KEY_TEMP = "temp";
    public static final String KEY_BATTERY = "battery";
    public static final String KEY_PROC_CPU = "procCpu";
    public static final String KEY_NET = "net";

    /** 多选顺序 = 展示顺序（与抽屉多选对话框一致）。 */
    public static final String[] ALL_KEYS = {
            KEY_FPS, KEY_CPU, KEY_CPU_FREQ, KEY_CORES, KEY_RAM, KEY_PROC, KEY_PROC_CPU,
            KEY_GPU, KEY_LOAD, KEY_POWER, KEY_TEMP, KEY_BATTERY, KEY_NET};

    private static final long TICK_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout panel;
    private final Set<String> selected;
    private final List<TextView> rows = new ArrayList<>();

    private long lastTime;
    private short frameCount;
    private float lastFPS;
    private String gpuInfo = "…";
    private long prevCpuBusy, prevCpuTotal;
    private long[] prevCoreBusy, prevCoreTotal;
    private long prevProcJiffies, prevProcWallMs;
    private long prevNetRx, prevNetTx;
    private boolean running;

    private GalgamePerfHud(LinearLayout panel, Set<String> selected) {
        this.panel = panel;
        this.selected = selected;
        for (String key : ALL_KEYS) {
            if (!selected.contains(key)) continue;
            TextView row = new TextView(panel.getContext());
            row.setTypeface(android.graphics.Typeface.MONOSPACE);
            row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
            row.setTextColor(0xffffffff);
            row.setTag(key);
            panel.addView(row);
            rows.add(row);
        }
    }

    /** 读取容器已选项；空集返回 null（不显示浮窗）。 */
    public static Set<String> selectedKeys(Container container) {
        Set<String> keys = new LinkedHashSet<>();
        if (container == null) return keys;
        String raw = container.getExtra(EXTRA_ITEMS);
        if (raw == null || raw.isEmpty()) return keys;
        for (String k : raw.split(",")) {
            String t = k.trim();
            for (String cand : ALL_KEYS) if (cand.equals(t)) keys.add(t);
        }
        return keys;
    }

    /** 写回选择（不 saveData，由调用方决定持久化时机）。 */
    public static void saveSelectedKeys(Container container, Set<String> keys) {
        if (container == null) return;
        StringBuilder sb = new StringBuilder();
        for (String k : ALL_KEYS) if (keys.contains(k)) {
            if (sb.length() > 0) sb.append(',');
            sb.append(k);
        }
        container.putExtra(EXTRA_ITEMS, sb.length() > 0 ? sb.toString() : null);
    }

    /** 挂载并启动采样；无选择项返回 null。 */
    public static GalgamePerfHud attach(FrameLayout root, Container container) {
        Set<String> keys = selectedKeys(container);
        if (keys.isEmpty() || root == null) return null;
        LinearLayout panel = new LinearLayout(root.getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0x66000000);
        int pad = Math.round(4 * root.getResources().getDisplayMetrics().density);
        panel.setPadding(pad, pad, pad, pad);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Math.round(10 * root.getResources().getDisplayMetrics().density);
        lp.topMargin = Math.round(4 * root.getResources().getDisplayMetrics().density);
        panel.setLayoutParams(lp);
        root.addView(panel);

        GalgamePerfHud hud = new GalgamePerfHud(panel, keys);
        hud.start();
        return hud;
    }

    public void start() {
        if (running) return;
        running = true;
        prevCpuTotal = prevCpuBusy = 0;
        prevCoreBusy = prevCoreTotal = null;
        prevProcJiffies = 0;
        prevProcWallMs = android.os.SystemClock.elapsedRealtime();
        prevNetRx = prevNetTx = -1;
        lastTime = android.os.SystemClock.elapsedRealtime();
        frameCount = 0;
        handler.post(tick);
    }

    public void destroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        if (panel.getParent() instanceof ViewGroup) ((ViewGroup) panel.getParent()).removeView(panel);
    }

    public boolean isVisible() {
        return panel.getVisibility() == View.VISIBLE;
    }

    public void setGPUInfo(String info) {
        if (info != null && !info.isEmpty()) gpuInfo = info;
    }

    /** 宿主按帧回调（与上游 FrameRating 同一帧链）。 */
    public void onFrame() {
        frameCount++;
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = android.os.SystemClock.elapsedRealtime();
            long elapsed = now - lastTime;
            if (elapsed >= 500) {
                lastFPS = frameCount * 1000f / elapsed;
                frameCount = 0;
                lastTime = now;
            }
            Context ctx = panel.getContext();
            for (TextView row : rows) {
                String key = (String) row.getTag();
                row.setText(format(ctx, key));
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    private String format(Context ctx, String key) {
        switch (key) {
            case KEY_FPS:
                return String.format(Locale.ENGLISH, "FPS   %5.1f", lastFPS);
            case KEY_CPU: {
                long[] c = readProcStat();
                if (c == null || prevCpuTotal <= 0) { snapshotCpu(c); return "CPU   …"; }
                float pct = Math.min(100f, (c[0] - prevCpuBusy) * 100f / (c[1] - prevCpuTotal));
                snapshotCpu(c);
                return String.format(Locale.ENGLISH, "CPU   %5.1f%%", pct);
            }
            case KEY_CORES: {
                int[] per = perCoreUsage();
                if (per == null) return "CORES N/A";
                StringBuilder sb = new StringBuilder("CORES");
                for (int v : per) sb.append(String.format(Locale.ENGLISH, " %2d", v));
                return sb.toString();
            }
            case KEY_TEMP: {
                float cpu = readThermal(true), bat = readThermal(false);
                return "TEMP  cpu " + (cpu >= 0 ? String.format(Locale.ENGLISH, "%.1f", cpu) : "N/A")
                        + "°C · bat " + (bat >= 0 ? String.format(Locale.ENGLISH, "%.1f", bat) : "N/A") + "°C";
            }
            case KEY_BATTERY: {
                android.content.Intent b = stickyBattery(ctx);
                if (b == null) return "BAT   N/A";
                int level = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                int status = b.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
                if (level < 0 || scale <= 0) return "BAT   N/A";
                String st = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ? "充电"
                        : status == android.os.BatteryManager.BATTERY_STATUS_FULL ? "满电" : "放电";
                return String.format(Locale.ENGLISH, "BAT   %d%% %s", level * 100 / scale, st);
            }
            case KEY_PROC_CPU: {
                long now = android.os.SystemClock.elapsedRealtime();
                long j = gameProcessJiffies();
                String out;
                if (j < 0 || prevProcWallMs <= 0 || now <= prevProcWallMs) {
                    out = "PCPU  …";
                }
                else {
                    // utime+stime jiffies 差 ÷ 时间(10ms 单位) = 占用核数；×100 = 多核百分比
                    // （SC_CLK_TCK=100，绝大多数 Linux 内核成立）
                    out = String.format(Locale.ENGLISH, "PCPU  %.0f%%",
                            (j - prevProcJiffies) * 100f / ((now - prevProcWallMs) / 10f));
                }
                prevProcJiffies = j;
                prevProcWallMs = now;
                return out;
            }
            case KEY_NET: {
                long[] n = readNetBytes();
                if (n == null) return "NET   N/A";
                if (prevNetRx < 0 || n[2] <= 0) {
                    prevNetRx = n[0];
                    prevNetTx = n[1];
                    n[2] = 0;
                    return "NET   …";
                }
                long rx = Math.max(0, (n[0] - prevNetRx) / n[2]);
                long tx = Math.max(0, (n[1] - prevNetTx) / n[2]);
                prevNetRx = n[0];
                prevNetTx = n[1];
                return "NET   ↓" + formatBytes(rx) + "/s ↑" + formatBytes(tx) + "/s";
            }
            case KEY_CPU_FREQ: {
                long[] clocks = readCoreClocks();
                if (clocks == null || clocks.length == 0) return "FREQ  N/A";
                long min = Long.MAX_VALUE, max = 0, sum = 0;
                for (long c : clocks) {
                    if (c <= 0) continue;
                    min = Math.min(min, c);
                    max = Math.max(max, c);
                    sum += c;
                }
                int active = 0;
                for (long c : clocks) if (c > 0) active++;
                if (active == 0) return "FREQ  N/A";
                return String.format(Locale.ENGLISH, "FREQ  avg %.2f · max %.2f GHz",
                        (sum / (double) active) / 1e6, max / 1e6);
            }
            case KEY_RAM: {
                ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
                if (am == null) return "RAM   N/A";
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                long used = mi.totalMem - mi.availMem;
                return "RAM   " + formatBytes(used) + " / " + formatBytes(mi.totalMem);
            }
            case KEY_PROC: {
                long rss = gameProcessRss();
                return "PROC  " + (rss >= 0 ? formatBytes(rss) : "N/A");
            }
            case KEY_GPU:
                return "GPU   " + gpuInfo;
            case KEY_LOAD: {
                String la = readLoadavg();
                return "LOAD  " + (la != null ? la : "N/A");
            }
            case KEY_POWER: {
                Float mw = readPowerMw(ctx);
                return mw == null ? "PWR   N/A"
                        : String.format(Locale.ENGLISH, "PWR   %.2f W", mw / 1000f);
            }
            default:
                return key;
        }
    }

    private void snapshotCpu(long[] c) {
        if (c != null) { prevCpuBusy = c[0]; prevCpuTotal = c[1]; }
    }

    /** /proc/stat 首行 → {busy, total}（idle/iowait 视为空闲）。 */
    private static long[] readProcStat() {
        String line = readFirstLine("/proc/stat");
        if (line == null || !line.startsWith("cpu ")) return null;
        try {
            String[] parts = line.trim().split("\\s+");
            long total = 0, idle = 0;
            for (int i = 1; i < parts.length; i++) {
                long v = Long.parseLong(parts[i]);
                total += v;
                if (i == 4 || i == 5) idle += v; // idle, iowait
            }
            return new long[]{total - idle, total};
        }
            catch (NumberFormatException e) {
            return null;
        }
    }

    /** 各核当前频率（kHz），越狱设备无 cpufreq 节点时返回空。 */
    private static long[] readCoreClocks() {
        File cpus = new File("/sys/devices/system/cpu");
        File[] list = cpus.listFiles((d, n) -> n.startsWith("cpu"));
        if (list == null) return new long[0];
        List<Long> out = new ArrayList<>();
        for (File cpu : list) {
            if (!cpu.isDirectory() || !cpu.getName().matches("cpu\\d+")) continue;
            File freq = new File(cpu, "cpufreq/scaling_cur_frequency");
            String v = readFirstLine(freq.getAbsolutePath());
            if (v == null) continue;
            try {
                out.add(Long.parseLong(v.trim()));
            }
            catch (NumberFormatException ignored) {
            }
        }
        long[] arr = new long[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    private static String readLoadavg() {
        String line = readFirstLine("/proc/loadavg");
        if (line == null) return null;
        String[] parts = line.trim().split("\\s+");
        return parts.length >= 2 ? parts[0] + " / " + parts[1] : null;
    }

    /** BatteryManager 电流（μA）× sticky 电池广播电压（mV）→ 毫瓦（取绝对值，方向设备实现不一）。 */
    private static Float readPowerMw(Context ctx) {
        try {
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm == null) return null;
            long ua = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (ua == Long.MIN_VALUE || ua == 0) return null;
            android.content.Intent battery = ctx.registerReceiver(null,
                    new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return null;
            int mv = battery.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1);
            if (mv <= 0) return null;
            return Math.abs(ua * mv / 1_000_000f); // μA·mV/1e6 = mW
        }
        catch (Exception e) {
            return null;
        }
    }

    /** 本进程 + box64/wine 子进程 RSS 合计（同 uid、名字含 box64/wine/wineserver）。 */
    private static long gameProcessRss() {
        long total = readRssKb("/proc/self/status");
        if (total < 0) return -1;
        File proc = new File("/proc");
        File[] pids = proc.listFiles((d, n) -> n.matches("\\d+"));
        if (pids == null) return total;
        for (File pid : pids) {
            String comm = readFirstLine(new File(pid, "comm").getAbsolutePath());
            if (comm == null) continue;
            String name = comm.trim().toLowerCase();
            if (!name.contains("box64") && !name.contains("wine")) continue;
            long rss = readRssKb(new File(pid, "status").getAbsolutePath());
            if (rss > 0) total += rss;
        }
        return total;
    }

    private static long readRssKb(String statusPath) {
        try (BufferedReader r = new BufferedReader(new FileReader(statusPath))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("VmRSS:")) {
                    String[] parts = line.trim().split("\\s+");
                    return Long.parseLong(parts[1]); // kB
                }
            }
        }
        catch (Exception ignored) {
        }
        return -1;
    }

    private static String readFirstLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return r.readLine();
        }
        catch (Exception e) {
            return null;
        }
    }

    /** 各核占用率 %（/proc/stat cpuN 行差分）。 */
    private int[] perCoreUsage() {
        File f = new File("/proc/stat");
        List<long[]> now = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("cpu") || line.startsWith("cpu ")) continue;
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 5) continue;
                long total = 0, idle = 0;
                try {
                    for (int i = 1; i < parts.length; i++) {
                        long v = Long.parseLong(parts[i]);
                        total += v;
                        if (i == 4 || i == 5) idle += v;
                    }
                    now.add(new long[]{total - idle, total});
                }
                catch (NumberFormatException ignored) {
                }
            }
        }
        catch (Exception e) {
            return null;
        }
        if (now.isEmpty()) return null;
        if (prevCoreBusy == null || prevCoreBusy.length != now.size()) {
            prevCoreBusy = new long[now.size()];
            prevCoreTotal = new long[now.size()];
            for (int i = 0; i < now.size(); i++) {
                prevCoreBusy[i] = now.get(i)[0];
                prevCoreTotal[i] = now.get(i)[1];
            }
            return null;
        }
        int[] pct = new int[now.size()];
        for (int i = 0; i < now.size(); i++) {
            long dBusy = now.get(i)[0] - prevCoreBusy[i];
            long dTotal = now.get(i)[1] - prevCoreTotal[i];
            pct[i] = dTotal > 0 ? (int) Math.min(100, dBusy * 100 / dTotal) : 0;
            prevCoreBusy[i] = now.get(i)[0];
            prevCoreTotal[i] = now.get(i)[1];
        }
        return pct;
    }

    /** thermal zone 温度：wantCpu=true 取 type 含 cpu 的最大值，否则取 battery。 */
    private static float readThermal(boolean wantCpu) {
        File base = new File("/sys/class/thermal");
        File[] zones = base.listFiles((d, n) -> n.startsWith("thermal_zone"));
        if (zones == null) return -1;
        float best = -1;
        for (File z : zones) {
            String type = readFirstLine(new File(z, "type").getAbsolutePath());
            if (type == null) continue;
            String t = type.trim().toLowerCase();
            boolean match = wantCpu ? (t.contains("cpu") || t.contains("soc")) : t.contains("batt");
            if (!match) continue;
            String raw = readFirstLine(new File(z, "temp").getAbsolutePath());
            if (raw == null) continue;
            try {
                float v = Long.parseLong(raw.trim()) / 1000f;
                if (v > 0 && v < 120) best = Math.max(best, v);
            }
            catch (NumberFormatException ignored) {
            }
        }
        return best;
    }

    private static android.content.Intent stickyBattery(Context ctx) {
        try {
            return ctx.registerReceiver(null,
                    new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
        }
        catch (Exception e) {
            return null;
        }
    }

    /** 游戏进程（本进程 + box64/wine 系）jiffies 总和（/proc/<pid>/stat utime+stime，字段 14/15）。 */
    private static long gameProcessJiffies() {
        long total = 0;
        boolean any = false;
        total += pidJiffies("/proc/self/stat");
        any = true;
        File proc = new File("/proc");
        File[] pids = proc.listFiles((d, n) -> n.matches("\\d+"));
        if (pids != null) {
            for (File pid : pids) {
                String comm = readFirstLine(new File(pid, "comm").getAbsolutePath());
                if (comm == null) continue;
                String name = comm.trim().toLowerCase();
                if (!name.contains("box64") && !name.contains("wine")) continue;
                long j = pidJiffies(new File(pid, "stat").getAbsolutePath());
                if (j >= 0) total += j;
            }
        }
        return any ? total : -1;
    }

    private static long pidJiffies(String statPath) {
        String line = readFirstLine(statPath);
        if (line == null) return -1;
        int close = line.lastIndexOf(')');
        if (close < 0) return -1;
        String[] fields = line.substring(close + 2).trim().split("\\s+");
        // stat 字段 3 起（state）；utime=字段14 → 索引 11，stime=字段15 → 索引 12
        if (fields.length < 13) return -1;
        try {
            return Long.parseLong(fields[11]) + Long.parseLong(fields[12]);
        }
        catch (NumberFormatException e) {
            return -1;
        }
    }

    /** /proc/net/dev 全接口（除 lo）{rxBytes, txBytes, 采样间隔秒}。 */
    private static long[] readNetBytes() {
        long rx = -1, tx = -1, start = System.currentTimeMillis();
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/net/dev"))) {
            String line;
            r.readLine();
            r.readLine();
            long rxSum = 0, txSum = 0;
            while ((line = r.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String ifname = line.substring(0, colon).trim();
                if (ifname.equals("lo")) continue;
                String[] parts = line.substring(colon + 1).trim().split("\\s+");
                if (parts.length < 10) continue;
                try {
                    rxSum += Long.parseLong(parts[0]);
                    txSum += Long.parseLong(parts[8]);
                }
                catch (NumberFormatException ignored) {
                }
            }
            long dt = (System.currentTimeMillis() - start) / 1000;
            rx = rxSum;
            tx = txSum;
            return new long[]{rx, tx, Math.max(1, dt)};
        }
        catch (Exception e) {
            return null;
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "N/A";
        if (bytes < 1024 * 1024) return (bytes / 1024) + "KB";
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ENGLISH, "%.1fMB", bytes / 1048576f);
        return String.format(Locale.ENGLISH, "%.1fGB", bytes / 1073741824f);
    }

    /** 调试/复选用：当前选择集。 */
    @Override
    public String toString() {
        return "GalgamePerfHud" + Arrays.toString(selected.toArray());
    }
}
