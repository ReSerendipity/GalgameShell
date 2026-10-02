package com.winlator.galgame.engine;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Kirikiri2 / KirikiriZ 的**内置**运行时（Kirikiroid2，BSD 3-Clause）。
 *
 * 集成要点（已核实，见 DOCS/native-engine-integration.md §3）：
 * <ul>
 *   <li>原生库名是 <b>libgame.so</b>（上游 Android.mk 的 {@code LOCAL_MODULE_FILENAME}），
 *       外加 <b>libffmpeg.so</b>（{@code KR2Activity} 静态块里显式 load）。</li>
 *   <li>Java 层必须落在包 <b>{@code org.tvp.kirikiri2}</b> 下，
 *       因为 libgame.so 里的 JNI 是按硬编码字符串 {@code org/tvp/kirikiri2/KR2Activity} 反射调用的。</li>
 *   <li>UI 宿主的具体 Activity 由 {@link #ACTIVITY_CLASS} 指定，属本 fork 自己的类。</li>
 * </ul>
 *
 * 未打包运行时时 {@link #isAvailable} 返回 false，调用方应回落到
 * {@link com.winlator.galgame.NativeRouteLauncher}（外部 APK）或 A 路由（wine/box64）。
 */
public final class KrkrEngine extends BuiltinEngine {

    public static final String ID = "krkr";

    /** 本 fork 自己的宿主 Activity（Package-private 名字写成常量，避免被这里反向编译依赖）。 */
    public static final String ACTIVITY_CLASS = "com.winlator.galgame.engine.krkr.KrkrGameActivity";

    /** 传给宿主 Activity 的游戏路径 extra。 */
    public static final String EXTRA_GAME_PATH = "com.winlator.galgame.extra.GAME_PATH";

    /** 内置引擎所需的原生库（顺序即加载顺序约束：ffmpeg 由 KR2Activity 自己 load）。 */
    private static final String[] NATIVE_LIBS = { "libffmpeg.so", "libgame.so" };

    public static final KrkrEngine INSTANCE = new KrkrEngine();

    private KrkrEngine() {
        super(ID, "Kirikiroid2（内置）", "https://github.com/zeas2/Kirikiroid2", "BSD 3-Clause");
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
        File entry = gamePath.isDirectory() ? new File(gamePath, "data.xp3") : gamePath;

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(context.getPackageName(), ACTIVITY_CLASS));
        intent.putExtra(EXTRA_GAME_PATH, entry.getAbsolutePath());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            // Activity 未在 manifest 注册 / 类缺失
            return false;
        }
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
