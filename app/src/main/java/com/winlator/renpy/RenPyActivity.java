package com.winlator.renpy;

import android.content.Intent;
import android.content.res.AssetManager;
import android.util.Log;

import org.renpy.android.PythonSDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * GalgameShell 内置 Ren'Py 引擎宿主（继承上游 org.renpy.android.PythonSDLActivity）。
 *
 * <p>运行契约（已对 librenpython.so 的 start_python() 与 renpy 源码核实，详见
 * DOCS/native-engine-integration.md §4.5）：
 * <ul>
 *   <li>{@code start_python()} 固定执行 {@code getFilesDir()/main.py}；父类
 *       {@code preparePython()} 已把 {@code ANDROID_PRIVATE} 设为 getFilesDir()。</li>
 *   <li>引擎需解包到 {@code getFilesDir()/renpy/}；python-for-android 的
 *       {@code android} / {@code jnius} 包解包到 {@code getFilesDir()/lib/}，
 *       启动器会把 lib 加进 sys.path。</li>
 *   <li>导入的外部游戏根目录通过 {@link #EXTRA_GAME_DIR} 传入，写入
 *       {@code getFilesDir()/game_dir.txt}，由 main.py 读作 renpy 的 basedir。</li>
 * </ul>
 *
 * <p>本类放在 {@code com.winlator.renpy}（而非 {@code com.winlator.galgame}）是有意为之：
 * CI 的 galgame-compile 门禁只对 {@code com/winlator/galgame} 用桩编译，而本类依赖真实
 * Android/SDL 类（PythonSDLActivity、AssetManager），放进门禁范围会编译失败。
 */
public class RenPyActivity extends PythonSDLActivity {

    private static final String TAG = "RenPyActivity";

    /** assets 中的引擎根目录（整体解包到 getFilesDir()）。 */
    private static final String ENGINE_ASSET_DIR = "renpy-engine";

    /** 解包版本号：引擎资产变更时 +1，触发重新解包（getFilesDir 跨升级保留）。 */
    private static final String ENGINE_VERSION = "7";

    private static final String VERSION_FILE = "renpy_engine.version";

    /** 启动器 main.py 读取的游戏根目录标记文件。 */
    private static final String GAME_DIR_FILE = "game_dir.txt";

    /** Intent extra：外部 Ren'Py 项目根目录（其下含 game/ 子目录）。 */
    public static final String EXTRA_GAME_DIR = "com.winlator.galgame.extra.RENPY_GAME_DIR";

    @Override
    public void preparePython() {
        // 复用父类：设置 mActivity / resourceManager 与 ANDROID_PRIVATE / ANDROID_PUBLIC /
        // ANDROID_OLD_PUBLIC / ANDROID_APK 环境变量（renpy.android 与存档路径依赖它们）。
        super.preparePython();

        // GalgameShell：把内置引擎从 assets 解包到 getFilesDir()。
        unpackEngine();

        // GalgameShell：把外部游戏根目录写入 main.py 读取的标记文件。
        writeGameDir(getIntent());
    }

    /**
     * 版本化解包内置引擎到 getFilesDir()。
     * 布局：assets/renpy-engine/{main.py,renpy/**,lib/**} → getFilesDir()/同名路径。
     */
    private void unpackEngine() {
        File filesDir = getFilesDir();

        String current = readAll(new File(filesDir, VERSION_FILE));
        if (ENGINE_VERSION.equals(current) && new File(filesDir, "renpy").isDirectory()) {
            return; // 已是当前版本
        }

        Log.i(TAG, "unpacking engine v" + ENGINE_VERSION + " to " + filesDir);

        // 清掉旧引擎，避免残留。
        deleteRecursive(new File(filesDir, "renpy"));
        deleteRecursive(new File(filesDir, "lib"));
        deleteRecursive(new File(filesDir, "main.py"));

        try {
            copyAssetTree(ENGINE_ASSET_DIR, filesDir);
            writeAll(new File(filesDir, VERSION_FILE), ENGINE_VERSION);
            Log.i(TAG, "engine unpacked");
        } catch (IOException e) {
            Log.e(TAG, "engine unpack failed", e);
            toastError("内置 Ren'Py 引擎解包失败：" + e);
        }
    }

    /**
     * 递归把 assets 下的目录树复制到 {@code dest}。
     *
     * <p>判别陷阱（真机实证）：{@code AssetManager.list()} 对<b>文件</b>返回的是
     * <b>空数组而非 null</b>——若以 null 判别文件，顶层文件会被误当空目录 mkdir，
     * 整棵树只会得到一串空目录（main.py 缺失 → start_python 静默退出）。
     * 故以「子项数 &gt; 0」判目录；空数组时先尝试按文件复制，失败再按空目录建。
     */
    private void copyAssetTree(String assetPath, File dest) throws IOException {
        String[] children = getAssets().list(assetPath);

        if (children != null && children.length > 0) {
            // 目录：递归复制。
            if (!dest.isDirectory() && !dest.mkdirs()) {
                throw new IOException("mkdir failed: " + dest);
            }
            for (String child : children) {
                copyAssetTree(assetPath + "/" + child, new File(dest, child));
            }
            return;
        }

        // 空数组：文件或空目录。能 open 即文件。
        try {
            copyAssetFile(assetPath, dest);
        } catch (IOException e) {
            // 空目录。
            if (!dest.isDirectory() && !dest.mkdirs()) {
                throw new IOException("mkdir failed: " + dest);
            }
        }
    }

    private void copyAssetFile(String assetPath, File dest) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("mkdir failed: " + parent);
        }
        try (InputStream in = getAssets().open(assetPath, AssetManager.ACCESS_STREAMING);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    /** 记录外部游戏根目录，供启动器 main.py 读作 renpy basedir。 */
    private void writeGameDir(Intent intent) {
        String dir = intent != null ? intent.getStringExtra(EXTRA_GAME_DIR) : null;
        File target = new File(getFilesDir(), GAME_DIR_FILE);

        if (dir == null || dir.trim().isEmpty()) {
            target.delete();
            return;
        }
        writeAll(target, dir.trim());
        Log.i(TAG, "game dir = " + dir.trim());
    }

    // ---- 小工具 ----------------------------------------------------------

    private static String readAll(File f) {
        if (!f.isFile()) return null;
        try (InputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), "UTF-8").trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeAll(File f, String content) {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes("UTF-8"));
        } catch (IOException e) {
            Log.w(TAG, "write failed: " + f, e);
        }
    }

    private static void deleteRecursive(File f) {
        if (!f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File kid : kids) deleteRecursive(kid);
            }
        }
        f.delete();
    }
}
