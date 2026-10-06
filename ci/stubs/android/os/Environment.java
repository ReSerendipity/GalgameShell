package android.os;

import java.io.File;

/**
 * CI 桩：Environment。仅保留 galgame 包实际引用到的符号。
 */
public class Environment {
    public static final String DIRECTORY_DOCUMENTS = "Documents";
    public static final String DIRECTORY_DOWNLOADS = "Download";
    public static File getExternalStoragePublicDirectory(String type) { return null; }

    /** API 30+：是否持有 MANAGE_EXTERNAL_STORAGE。GalgameStoragePermission 用。 */
    public static boolean isExternalStorageManager() { return false; }
}
