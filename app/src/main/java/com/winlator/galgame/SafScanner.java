package com.winlator.galgame;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量导入的候选扫描（SAF 目录树，<b>只做名字级预筛</b>）。
 *
 * <p>为什么只按名字筛：SAF 只给 Cursor，{@link EngineDetector} 认的是 {@code File}；
 * 而「先整目录拷进来再判断」对 Download 这种混合目录代价不可接受（一个非游戏目录可能是
 * 好几 GB）。所以这里只按「顶层有没有 .exe / .xp3 …」或「有没有 Ren'Py 的 game/」圈出
 * 候选，真正的引擎裁决仍由导入管线在暂存后的真实文件上做——误报的代价只是白拷一次，
 * 漏报才是问题，因此标记表按扩展名取宽。
 *
 * <p>套娃：解压出来的游戏常常是 {@code 游戏名/游戏名/exe}。本扫描允许下探一层：
 * 某目录若只有唯一子目录且自身没有标记，就把子目录当候选。
 */
public final class SafScanner {

    public static final class Candidate {
        public final Uri uri;
        public final String name;
        /** 命中依据（".exe" / "game/" …），展示给用户看。 */
        public final String hint;

        Candidate(Uri uri, String name, String hint) {
            this.uri = uri;
            this.name = name;
            this.hint = hint;
        }
    }

    /** 文件级标记：命中任一即认为「像游戏目录」。 */
    private static final String[] FILE_MARKERS = {
            ".exe", ".rpy", ".rpyc", ".xp3", ".rpa", ".ypf", ".wolf", ".rgssad", ".nsa"
    };
    /** 目录级标记：Ren'Py 的 game/（目录里才看得出脚本，先按名字收）。 */
    private static final String[] DIR_MARKERS = {"game"};

    private SafScanner() {}

    /** 扫描 treeUri 的下一层（容忍一层套娃）；无候选返回空列表。 */
    public static List<Candidate> scan(Context context, Uri treeUri) throws IOException {
        List<Candidate> out = new ArrayList<>();
        if (context == null || treeUri == null) return out;

        List<Entry> rootKids = children(context, treeUri);
        String rootHint = markerOf(rootKids);
        if (rootHint != null) {                       // 选中的就是单个游戏目录
            out.add(new Candidate(treeUri, displayName(context, treeUri), rootHint));
            return out;
        }

        for (Entry kid : rootKids) {
            if (!kid.dir || kid.name.startsWith(".")) continue;
            List<Entry> kids = children(context, kid.uri);
            String hint = markerOf(kids);
            if (hint != null) {
                out.add(new Candidate(kid.uri, kid.name, hint));
                continue;
            }
            // 一层套娃：唯一子目录且名字不像运行时目录
            if (kids.size() == 1 && kids.get(0).dir && !isRuntimeDir(kids.get(0).name)) {
                Entry inner = kids.get(0);
                List<Entry> innerKids = children(context, inner.uri);
                String innerHint = markerOf(innerKids);
                if (innerHint != null) out.add(new Candidate(inner.uri, inner.name, innerHint));
            }
        }
        return out;
    }

    private static boolean isRuntimeDir(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        return n.equals("windows") || n.equals("program files") || n.equals("users")
                || n.equals("android") || n.equals("dcim") || n.startsWith(".");
    }

    private static String markerOf(List<Entry> kids) {
        String other = null;
        for (Entry e : kids) {
            String n = e.name.toLowerCase(java.util.Locale.ROOT);
            if (e.dir) {
                for (String d : DIR_MARKERS) {
                    if (n.equals(d)) return d + "/";
                }
                continue;
            }
            for (String m : FILE_MARKERS) {
                if (n.endsWith(m)) {
                    if (".exe".equals(m)) return m;   // exe 最可信，优先
                    if (other == null) other = m;
                }
            }
        }
        return other;
    }

    private static final class Entry {
        final Uri uri;
        final String name;
        final boolean dir;

        Entry(Uri uri, String name, boolean dir) {
            this.uri = uri;
            this.name = name;
            this.dir = dir;
        }
    }

    private static List<Entry> children(Context context, Uri dirUri) throws IOException {
        List<Entry> out = new ArrayList<>();
        Uri kidsUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                dirUri, SafStager.documentIdOf(dirUri));
        Cursor cursor = context.getContentResolver().query(kidsUri,
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null);
        if (cursor == null) return out;
        try {
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String mime = cursor.getString(1);
                String name = cursor.getString(2);
                if (id == null || name == null || name.isEmpty()) continue;
                boolean dir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                out.add(new Entry(DocumentsContract.buildDocumentUriUsingTree(dirUri, id), name, dir));
            }
        } finally {
            cursor.close();
        }
        return out;
    }

    private static String displayName(Context context, Uri treeUri) {
        try {
            Uri self = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri));
            try (Cursor c = context.getContentResolver().query(self,
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null)) {
                if (c != null && c.moveToNext()) {
                    String n = c.getString(0);
                    if (n != null && !n.isEmpty()) return n;
                }
            }
        } catch (Exception ignored) { /* 退回目录名 */ }
        String path = treeUri != null ? treeUri.getLastPathSegment() : null;
        return path != null ? path : "game";
    }
}
