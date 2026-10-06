package android.provider;

/**
 * CI 桩：android.provider.Settings。仅保留 galgame 包引用到的 action 常量。
 */
public final class Settings {
    private Settings() {}

    /** 跳到「所有文件访问」页，需配 package: Uri data。 */
    public static final String ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION =
            "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION";

    /** 通用入口（不带包名），作为 fallback。 */
    public static final String ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION =
            "android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION";
}
