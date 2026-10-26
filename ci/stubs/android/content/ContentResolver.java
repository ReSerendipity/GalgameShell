package android.content;

import android.database.Cursor;
import android.net.Uri;

import java.io.InputStream;

/**
 * CI 桩：ContentResolver（android.content）。
 * 仅保留 galgame 子树引用到的符号。勿用于运行。
 */
public abstract class ContentResolver {
    public abstract Cursor query(Uri uri, String[] projection, String selection,
                                 String[] selectionArgs, String sortOrder);

    public abstract InputStream openInputStream(Uri uri);

    public abstract void takePersistableUriPermission(Uri uri, int modeFlags);
}
