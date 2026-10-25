package com.winlator.galgame;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.winlator.container.Container;
import com.winlator.core.FileUtils;
import com.winlator.GalgameMainActivity;

import org.json.JSONObject;

import java.io.File;

/**
 * Galgame 启动接线：把 galgame_overlay.json 的 exe 接进**所有**容器启动入口。
 *
 * 背景（R4）：XServerDisplayActivity 无 exec_path/shortcut 时 fallback 到文件管理器
 * 桌面（wfm.exe），用户点「启动」会先进类 Windows 桌面、再手动找游戏 exe —— 这正是
 * 「点击启动却进了 Windows 模拟页面」的原因。导入时 overlay 已锁定启动 exe
 * （见 ImportFlow.stageGameFiles / writeOverlay），启动时直接注入 exec_path 即可
 * boot 走 nogui 一键进游戏。
 *
 * 对标 GameHub / GameNative 的「点封面即玩」范式：启动不再经过 Windows 桌面。
 * 对无 overlay 的普通 Winlator 容器零影响（helper 静默跳过，行为不变）。
 */
public final class GalgameLaunchHelper {

    private GalgameLaunchHelper() {}

    /**
     * 若容器带 galgame_overlay.json 且含 exe 字段，则向 intent 注入 exec_path。
     * 无 overlay / 字段缺失 / 解析失败均静默跳过。
     */
    public static void injectExecPath(Container container, Intent intent) {
        String exe = readOverlayExe(container);
        if (exe != null) {
            // 非 ASCII exe 名的启动一律走 ASCII 别名（ensureEngineRuntime 已建好硬链接）
            exe = resolveLaunchExe(container, exe);
            intent.putExtra("exec_path", exe);
            // 日文 exe 名直接作为宿主路径交给 wine 会打不开（loader 按 ANSI 打开宿主路径），
            // 另带一份 C:\ 路径供启动命令使用；宿主路径仍用于工作目录。
            String winePath = toWindowsPath(container, exe);
            if (winePath != null) intent.putExtra(EXTRA_EXEC_PATH_WINE, escapeSpaces(winePath));
        }
    }

    public static final String EXTRA_EXEC_PATH_WINE = "exec_path_wine";

    /** 容器内 drive_c 下的宿主路径转 Windows 路径；不在 drive_c 下返回 null。 */
    public static String toWindowsPath(Container container, String hostPath) {
        try {
            String prefix = new File(container.getRootDir(), ".wine/drive_c").getAbsolutePath();
            if (hostPath.startsWith(prefix + "/")) {
                return "C:\\" + hostPath.substring(prefix.length() + 1).replace('/', '\\');
            }
        }
        catch (Exception ignored) {}
        return null;
    }

    /** splitCommand 用反斜杠转义空格，且会把引号原样保留，故只转义不引号。 */
    private static String escapeSpaces(String s) {
        return s.replace(" ", "\\ ");
    }

    /** 读取容器根目录 galgame_overlay.json 的 exe 字段（unix 绝对路径），无则返回 null。 */
    public static String readOverlayExe(Container container) {
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

    /** 容器是否为 galgame 容器（以 galgame_overlay.json 存在与否判定）。 */
    public static boolean isGalgameContainer(Container container) {
        return container != null
                && new File(container.getRootDir(), "galgame_overlay.json").isFile();
    }

    /**
     * 游戏退出（进程结束 / 菜单「退出」）后重启应用并**直接落回游戏库**。
     *
     * 背景（2026-09-25 用户反馈）：上游 exit() 走 {@code AppUtils.restartApplication}，
     * 固定重启到 MainActivity（带 exec_path 时还会落到容器文件管理器），玩家打完一局
     * 回不到游戏库。此处复用同一重启原语（makeRestartActivityTask + exit），仅把任务根
     * 组件换成 R 新壳 GalgameMainActivity（其 onCreate 默认选中游戏库 Tab）；非 galgame
     * 容器不受影响。
     */
    public static void restartToLibrary(Activity activity) {
        Intent library = new Intent(activity, GalgameMainActivity.class);
        activity.startActivity(Intent.makeRestartActivityTask(library.getComponent()));
        Runtime.getRuntime().exit(0);
    }

    /**
     * 校验 galgame 容器的启动 exe 是否真实落盘（overlay 记录的 exe 文件存在）。
     * overlay 存的是宿主绝对路径（见 {@link #readOverlayExe}），故直接按本地文件判定。
     * 用于启动前拦截「exe 缺失」场景，避免静默弹回游戏库（见 {@link #injectExecPath}）。
     */
    public static boolean isExecPathPresent(Container container) {
        String exe = readOverlayExe(container);
        return exe != null && new File(exe).exists();
    }

    /**
     * 启动前拦截判定：容器是 galgame 容器（带 galgame_overlay.json），但其记录的启动 exe
     * 未落盘。此时若强行启动，wine 会跑空路径立即退出并经 {@link #restartToLibrary} 静默弹回
     * 游戏库（这正是「打开游戏进不去」的根因）。true 表示应拦截并提示用户重新导入。
     *
     * <p>非 galgame 容器（无 overlay）一律返回 false —— 不拦截，普通 Winlator 容器照常走
     * 文件管理器桌面，行为不变。
     */
    public static boolean isExecMissing(Container container) {
        return isGalgameContainer(container) && !isExecPathPresent(container);
    }

    /** 容器 extra：galgame 直启的窗口铺满开关。"1"/缺省 = 铺满，"0" = 原始窗口尺寸。 */
    public static final String EXTRA_WINDOW_FIT = "galgame_window_fit";

    /**
     * galgame 容器直启（exec_path 路线）是否启用上游 forceWindowsFullscreen：
     * 把游戏窗口等比放大居中到虚拟屏、不渲染装饰框架，输入坐标由 InputDeviceManager
     * 自动换算（无拉伸变形）。
     *
     * <p>缺省开启。Wine 下 galgame 几乎都以小于虚拟屏的窗口运行（800x600 / 960x540 /
     * 1024x768 常态），虚拟屏整体等比缩放到物理屏后画面只占一小块——2026-10-25 真机
     * 实测 12%–45%。上游该开关只有 shortcut 一个入口，galgame 直启路线永远拿不到，
     * 故此处按容器 extra 判定（用户可在详情页「高级设置 → 窗口画面铺满」关闭）。
     *
     * <p>shortcut 路线与普通（无 overlay 的）Winlator 容器不受影响，沿用上游行为。
     */
    public static boolean shouldForceWindowFullscreen(Container container) {
        return container != null
                && isGalgameContainer(container)
                && !"0".equals(container.getExtra(EXTRA_WINDOW_FIT));
    }

    /** 非 ASCII 主程序别名（同目录硬链接）：绕过 wine 启动器早期按 ANSI 码页解析主程序
     *  路径导致的非 ASCII 文件名打不开（真机 BGI 实测，见 docs/fullscreen-fix-retrospective.md）。 */
    private static final String LAUNCH_ALIAS_NAME = "gs_launch.exe";

    /**
     * 启动前运行环境自愈（幂等，WinlatorContainerEngine.launch 调用，毫秒级）：
     * <ol>
     *   <li>AdvHD 引擎：游戏目录注入 assets 内的 {@code icu.dll} 最小桩——该引擎 exe
     *       静态导入 icu.dll（实际只用到 uloc_getDefault），缺它则加载器 c0000135
     *       直接退出（真机实测两个样本同病）。</li>
     *   <li>主程序文件名非 ASCII：建同目录硬链接 {@link #LAUNCH_ALIAS_NAME}，启动一律
     *       走别名（见 {@link #resolveLaunchExe}）。</li>
     *   <li>存档重定向 S: 盘目标目录自愈（appdata/roaming、appdata/local）——目录缺失
     *       时 PlayDRM 等写 S: 的组件直接 PATH_NOT_FOUND。</li>
     * </ol>
     */
    public static void ensureEngineRuntime(Context context, Container container) {
        if (!isGalgameContainer(container)) return;
        try {
            String exe = readOverlayExe(container);
            if (exe == null) return;
            File exeFile = new File(exe);
            File dir = exeFile.getParentFile();
            if (dir == null || !dir.isDirectory()) return;

            String engine = readOverlayField(container, "engine");
            if ("ADVHD".equals(engine)) {
                File icu = new File(dir, "icu.dll");
                if (!icu.isFile()) FileUtils.copy(context, "galgame/icu.dll", icu);
            }

            File alias = new File(dir, LAUNCH_ALIAS_NAME);
            if (!isAscii(exeFile.getName()) && exeFile.isFile() && !alias.isFile()) {
                try {
                    java.nio.file.Files.createLink(alias.toPath(), exeFile.toPath());
                }
                catch (Exception linkFailed) {
                    try {
                        java.nio.file.Files.copy(exeFile.toPath(), alias.toPath());
                    }
                    catch (Exception copyFailed) { /* 下次启动再试 */ }
                }
            }

            File sDrive = new File(container.getRootDir(), ".wine/dosdevices/s:");
            if (sDrive.exists()) {
                File target = sDrive.getCanonicalFile();
                new File(target, "appdata/roaming").mkdirs();
                new File(target, "appdata/local").mkdirs();
            }
        }
        catch (Exception ignored) {
            // 自愈失败不阻断启动，按现状继续
        }
    }

    /** 主程序解析：存在 ASCII 别名（且原文件名非 ASCII）时优先别名。 */
    private static String resolveLaunchExe(Container container, String exe) {
        File exeFile = new File(exe);
        if (isAscii(exeFile.getName())) return exe;
        File alias = new File(exeFile.getParentFile(), LAUNCH_ALIAS_NAME);
        return alias.isFile() ? alias.getPath() : exe;
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7f) return false;
        }
        return true;
    }

    /** 读取容器 overlay 的任意字符串字段（engine 等）；overlay 缺失返回空串。 */
    private static String readOverlayField(Container container, String field) {
        try {
            File f = new File(container.getRootDir(), "galgame_overlay.json");
            if (!f.exists()) return "";
            JSONObject o = new JSONObject(FileUtils.readString(f));
            return o.optString(field, "");
        }
        catch (Exception ignored) {
            return "";
        }
    }
}
