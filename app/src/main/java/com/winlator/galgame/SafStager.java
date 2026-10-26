package com.winlator.galgame;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 把 SAF（{@code ACTION_OPEN_DOCUMENT_TREE}）授权的目录树复制进应用私有空间，
 * 变成普通 File 树后再交给既有导入管线（{@code EngineDetector.detect} 与
 * {@code ImportFlow.stageGameFiles} 只认 File）。
 *
 * <p>为什么必须有这一步：导入是全站唯一需要读外部存储的地方。走
 * {@code MANAGE_EXTERNAL_STORAGE} 会在卸载重装/清数据后被系统重置——2026-10-26 真机实测：
 * 重置后 {@code listFiles()} 返回 null，检测判 UNKNOWN、复制 0 个文件、最后才报
 * 「启动文件缺失或不完整」，用户完全无法自救。SAF 的 persistable 授权由用户在系统选择器里
 * 逐次授予、跟目录走，不依赖该特殊权限；同时导入第一步改为系统选择器后，
 * 也不再存在"手输路径输错/输不全"这一整类失败。
 *
 * <p>复制完成后调用方应删除 {@link #stageDir(Context)} 下的对应子目录（见 ImportFlow 调用点）。
 */
public final class SafStager {

    /** 低于这个可用空间就不再往下复制（见 copyFile 的硬失败说明）。 */
    private static final long MIN_FREE_BYTES = 128L * 1024 * 1024;

    public interface ProgressListener {
        void onBytes(long done);
    }

    private SafStager() {}

    /** 暂存根目录（应用私有，与容器同处 /data，后续复制为同盘操作）。 */
    public static File stageDir(Context context) {
        return new File(context.getFilesDir(), "import-stage");
    }

    /**
     * 复制整棵 SAF 树到 {@code stageDir/<ts>-<safeName>}。
     * @throws IOException 任一文件读取/写入失败（调用方负责清理半成品目录）
     */
    public static File stageTree(Context context, Uri treeUri, String displayName,
                                 ProgressListener progress) throws IOException {
        // displayName 来自选择器的 DISPLAY_NAME 查询，个别 provider 会返回 null；
        // 不兜底就是 NPE，用户只看到一句「暂存失败: null」。
        String safe = displayName != null ? displayName : "folder";
        File dest = new File(stageDir(context),
                System.currentTimeMillis() + "-" + safeDirName(safe));
        long[] done = {0};
        try {
            copyDir(context, treeUri, dest, progress, done);
        } catch (IOException e) {
            deleteRecursively(dest);
            throw e;
        }
        return dest;
    }

    public static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        File[] children = f.listFiles();
        if (children != null) for (File c : children) deleteRecursively(c);
        f.delete();
    }

    /**
     * 清掉上一次导入留下的暂存残骸（进程被杀 / 用户中途退出时 cleanup 来不及跑）。
     * 只删早于 {@code maxAgeMillis} 的条目，避免误删另一个实例正在写入的副本。
     */
    public static void sweepStale(Context context, long maxAgeMillis) {
        File[] entries = stageDir(context).listFiles();
        if (entries == null) return;
        long cutoff = System.currentTimeMillis() - maxAgeMillis;
        for (File e : entries) {
            if (e.lastModified() < cutoff) deleteRecursively(e);
        }
    }

    private static void copyDir(Context context, Uri dirUri, File destDir,
                                ProgressListener progress, long[] done) throws IOException {
        if (!destDir.isDirectory() && !destDir.mkdirs()) {
            throw new IOException("无法创建暂存目录: " + destDir);
        }
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                dirUri, documentIdOf(dirUri));
        Cursor cursor = context.getContentResolver().query(children,
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null);
        if (cursor == null) throw new IOException("选择器未返回子条目: " + dirUri);
        try {
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String mime = cursor.getString(1);
                String name = cursor.getString(2);
                if (id == null || name == null || name.isEmpty()) continue;
                Uri doc = DocumentsContract.buildDocumentUriUsingTree(dirUri, id);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    copyDir(context, doc, new File(destDir, safeDirName(name)), progress, done);
                } else {
                    copyFile(context, doc, new File(destDir, safeDirName(name)), progress, done);
                }
            }
        } finally {
            cursor.close();
        }
    }

    private static void copyFile(Context context, Uri doc, File dest,
                                 ProgressListener progress, long[] done) throws IOException {
        // /data 写满的表现是 ENOSPC，报出来是一句看不懂的 IOException；galgame 动辄数 GB，
        // 先量可用空间硬失败，把「还剩多少」直接讲给用户（宁可早停，不要复制半小时后失败）。
        long free = stageDir(context).getUsableSpace();
        if (free < MIN_FREE_BYTES) {
            throw new IOException("手机存储空间不足：剩余 " + (free / 1048576) + "MB，低于 "
                    + (MIN_FREE_BYTES / 1048576) + "MB。请清理存储后重新导入");
        }
        try (InputStream in = context.getContentResolver().openInputStream(doc);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IOException("无法打开: " + doc);
            byte[] buf = new byte[256 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done[0] += n;
                if (progress != null) progress.onBytes(done[0]);
            }
        }
    }

    /**
     * 树根 URI（/tree/…）与子文档 URI（/document/…）取 documentId 的 API 不同：
     * getDocumentId 遇到树 URI 抛 IllegalArgumentException。递归里两种都会出现，
     * 这里按形状兜底，避免整棵复制在第一层就失败。
     */
    static String documentIdOf(Uri uri) {
        try {
            return DocumentsContract.getDocumentId(uri);
        } catch (Exception e) {
            return DocumentsContract.getTreeDocumentId(uri);
        }
    }

    private static String safeDirName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
