package com.winlator.galgame.engine;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
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

    /**
     * 引擎自身的 Activity。libgame.so 的 JNI 按硬编码字符串 {@code org/tvp/kirikiri2/KR2Activity}
     * 回调，包名与类名都不能改，因此这里直接指向它，不再经中间宿主转手。
     */
    public static final String ACTIVITY_CLASS = "org.tvp.kirikiri2.KR2Activity";

    /** 传给引擎 Activity 的游戏路径 extra（引擎暂未消费，保留供日后对齐）。 */
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

        File entry = gamePath.isDirectory() ? resolveEntry(gamePath) : gamePath;
        if (entry == null) return false;
        String absPath = entry.getAbsolutePath();
        if (!entry.exists()) {
            // 引擎会自己扫描目录，路径不存在时直接放弃，免得弹出空壳
            Log.w("KrkrEngine", "解析出的游戏入口不存在：" + absPath);
            return false;
        }

        if (!isAvailable(context)) {
            Log.w("KrkrEngine", "内置运行时未就绪：" + unavailableReason(context));
            return false;
        }

        // 关键顺序：先写 recentpath.xml，再拉起引擎。
        // 引擎一进 onCreate 就会读该文件定位游戏目录，晚写等于没写。
        int written = writeRecentPath(context, absPath);
        Log.i("KrkrEngine", "recentpath.xml written=" + written + " path=" + absPath);

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(context.getPackageName(), ACTIVITY_CLASS));
        intent.putExtra(EXTRA_GAME_PATH, absPath);
        // 仅在非 Activity 上下文（Application / Service / 通知点击）下才另起 task；
        // 从游戏库 Activity 起时留在同一个 task，返回键才能退回游戏库而不是回桌面。
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            // Activity 未在 manifest 注册 / 类缺失
            Log.w("KrkrEngine", "拉起内置引擎失败", e);
            return false;
        }
    }

    /**
     * 定位游戏入口文件。
     *
     * <p>Kirikiroid2 的惯例主包名是 {@code data.xp3}；但很多移植包把主包改名成
     * {@code data.bin}（内容仍是 {@code "XP3\r\n"} 魔数，靠 {@code xp3filter.tjs} 之类的
     * 逐字节过滤器解密）。这类包没有可推断的入口文件，回落到把<em>目录</em>写进
     * recentpath，让引擎自己扫描整个目录。
     */
    private static File resolveEntry(File dir) {
        File data = new File(dir, "data.xp3");
        if (data.isFile()) return data;

        File[] xp3s = dir.listFiles((f) -> f.isFile() && f.getName().toLowerCase().endsWith(".xp3"));
        if (xp3s != null && xp3s.length > 0) {
            // 取最大的一个当主包（补丁包通常远小于主包）
            File best = xp3s[0];
            for (File f : xp3s) if (f.length() > best.length()) best = f;
            Log.i("KrkrEngine", "目录内发现 " + xp3s.length + " 个 xp3，选用主包 " + best.getName());
            return best;
        }

        Log.i("KrkrEngine", "目录内没有 data.xp3 / *.xp3，回落到整个目录让引擎扫描：" + dir);
        return dir;
    }

    /**
     * 把游戏绝对路径写进引擎能读到的 {@code recentpath.xml}，返回成功写入的份数。
     *
     * <p>该文件位于 {@code TVPGetInternalPreferencePath() = cocos2dx FileUtils::getWritablePath()
     * + ".preference/"}。getWritablePath() 的具体落点由编译进 libgame.so 的 cocos2d-x 决定
     * （内部或外部），所以两份都写，成本极低但覆盖面全。
     */
    private static int writeRecentPath(Context context, String absPath) {
        int written = 0;
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<RecentPathList>\n  <Item Path=\"" + absPath + "\"/>\n</RecentPathList>\n";
        byte[] bytes;
        try {
            bytes = xml.getBytes("UTF-8");
        } catch (Exception e) {
            bytes = xml.getBytes();
        }

        File[] roots = new File[]{
                context.getFilesDir(),          // .../files
                context.getExternalFilesDir(null) // .../files
        };
        for (File root : roots) {
            if (root == null) continue;
            File pref = new File(root, ".preference");
            if (!pref.exists() && !pref.mkdirs()) continue;
            File target = new File(pref, "recentpath.xml");
            try {
                OutputStream out = new FileOutputStream(target, false);
                try {
                    out.write(bytes);
                } finally {
                    out.close();
                }
                written++;
            } catch (Exception e) {
                Log.w("KrkrEngine", "写 recentpath.xml 失败：" + target.getAbsolutePath(), e);
            }
        }
        return written;
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
