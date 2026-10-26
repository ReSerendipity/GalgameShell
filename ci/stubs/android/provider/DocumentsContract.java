package android.provider;

import android.net.Uri;

/**
 * CI 桩：DocumentsContract（android.provider，SAF 树遍历入口）。
 * 仅保留 galgame 子树引用到的符号。勿用于运行。
 */
public final class DocumentsContract {
    private DocumentsContract() {}

    public static String getDocumentId(Uri uri) { return ""; }

    public static String getTreeDocumentId(Uri treeUri) { return ""; }

    public static Uri buildChildDocumentsUriUsingTree(Uri treeUri, String parentDocumentId) { return null; }

    public static Uri buildDocumentUriUsingTree(Uri treeUri, String documentId) { return null; }

    public static final class Document {
        public static final String COLUMN_DOCUMENT_ID = "document_id";
        public static final String COLUMN_MIME_TYPE = "mime_type";
        public static final String COLUMN_DISPLAY_NAME = "_display_name";
        public static final String MIME_TYPE_DIR = "vnd.android.document/directory";
    }
}
