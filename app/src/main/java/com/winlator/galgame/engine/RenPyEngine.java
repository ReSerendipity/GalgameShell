package com.winlator.galgame.engine;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Ren'Py 内置运行时（Ren'Py 引擎本体 LGPL-2.1，详见 DOCS/native-engine-integration.md §2）。
 *
 * <p>集成要点（已核实，见 DOCS/native-engine-integration.md §4.5）：
 * <ul>
 *   <li>原生库为合并库 <b>librenpython.so</b>（内含 CPython 3.12 + SDL2 + jnius 桥，
 *       以 {@code SDL_main} 为入口）；Java 侧 SDLActivity 版本须为 SDL2（已核对一致）。</li>
 *   <li>宿主 Activity 为本 fork 自己的 {@code com.winlator.renpy.RenPyActivity}
 *       （继承 org.renpy.android.PythonSDLActivity），把引擎从 assets 解包到
 *       getFilesDir()，并以 {@code game_dir.txt} 告知启动器外部游戏根目录。</li>
 *   <li>游戏按 Ren'Py 惯例是「含 game/ 子目录的项目根」；引擎据此推导 gamedir。</li>
 * </ul>
 *
 * <p>类名/Activity 以字符串 {@link #ACTIVITY_CLASS} 间接引用（与 {@link KrkrEngine}
 * 同风格），使本类不 import 引擎宿主、从而能被 CI 的 galgame-compile 桩门禁编译。
 */
public final class RenPyEngine extends BuiltinEngine {

    public static final String ID = "renpy";

    /** 内置 Ren'Py 引擎宿主 Activity。 */
    public static final String ACTIVITY_CLASS = "com.winlator.renpy.RenPyActivity";

    /** 传给引擎 Activity 的游戏根目录 extra（与 RenPyActivity.EXTRA_GAME_DIR 同值）。 */
    public static final String EXTRA_GAME_DIR = "com.winlator.galgame.extra.RENPY_GAME_DIR";

    /** 内置引擎所需的原生库。 */
    private static final String[] NATIVE_LIBS = { "librenpython.so" };

    public static final RenPyEngine INSTANCE = new RenPyEngine();

    private RenPyEngine() {
        super(ID, "Ren'Py（内置）", "https://github.com/renpy/renpy", "LGPL-2.1");
    }

    @Override
    public boolean isAvailable(Context context) {
        return missingNativeLibs(context).isEmpty();
    }

    @Override
    public String unavailableReason(Context context) {
        List<String> missing = missingNativeLibs(context);
        if (missing.isEmpty()) return "已就绪";
        return "内置运行时未随包打包，缺少：" + missing;
    }

    @Override
    public boolean launch(Context context, File gamePath) {
        if (gamePath == null || !gamePath.exists()) return false;

        if (!isAvailable(context)) {
            Log.w("RenPyEngine", "内置运行时未就绪：" + unavailableReason(context));
            return false;
        }

        File root = resolveGameRoot(gamePath);
        if (root == null || !looksLikeRenPyGame(root)) {
            Log.w("RenPyEngine", "未找到 Ren'Py 游戏结构：" + gamePath);
            return false;
        }
        String absPath = root.getAbsolutePath();

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(context.getPackageName(), ACTIVITY_CLASS));
        intent.putExtra(EXTRA_GAME_DIR, absPath);
        // 从游戏库 Activity 起时留在同一 task，返回键才能退回游戏库。
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            Log.w("RenPyEngine", "拉起内置引擎失败", e);
            return false;
        }
    }

    /**
     * 定位 Ren'Py 项目根目录（即含 {@code game/} 子目录的那一层）。
     *
     * <p>用户既可能选中项目根目录，也可能选中 {@code game/} 内的 .rpa/.rpy。
     * 后者应向上取「名为 game 的目录」的父级作为 basedir——这样 bootstrap 里
     * {@code basedir/game} 天然存在，不会多出一个空的 {@code game/} 子目录。
     */
    private static File resolveGameRoot(File selected) {
        File dir = selected.isDirectory() ? selected : selected.getParentFile();
        if (dir == null) return null;
        if ("game".equals(dir.getName()) && dir.getParentFile() != null) {
            return dir.getParentFile();
        }
        return dir;
    }

    /** 粗判目录是否为 Ren'Py 项目：含 game/ 且其中有脚本，或目录本身就有脚本/归档。 */
    private static boolean looksLikeRenPyGame(File root) {
        File game = new File(root, "game");
        if (game.isDirectory() && hasRenPyContent(game)) return true;
        return hasRenPyContent(root);
    }

    private static boolean hasRenPyContent(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return false;
        for (File f : fs) {
            if (!f.isFile()) continue;
            String n = f.getName().toLowerCase();
            if (n.endsWith(".rpy") || n.endsWith(".rpym") || n.endsWith(".rpyc")
                    || n.endsWith(".rpa") || n.endsWith(".rpyb")) {
                return true;
            }
        }
        return false;
    }

    /** 缺失的原生库清单。 */
    private static List<String> missingNativeLibs(Context context) {
        List<String> missing = new ArrayList<>();
        ApplicationInfo ai = context.getApplicationInfo();
        if (ai == null || ai.nativeLibraryDir == null) {
            for (String lib : NATIVE_LIBS) missing.add(lib);
            return missing;
        }
        File dir = new File(ai.nativeLibraryDir);
        for (String lib : NATIVE_LIBS) {
            if (!new File(dir, lib).isFile()) missing.add(lib);
        }
        return missing;
    }
}
