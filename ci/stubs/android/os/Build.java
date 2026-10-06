package android.os;

/**
 * CI 桩：android.os.Build。仅保留 galgame 包引用到的符号。
 */
public class Build {
    public static final String BOARD = "";
    public static final String HARDWARE = "";
    public static final String MANUFACTURER = "";
    public static final String SOC_MODEL = "";

    public static class VERSION {
        public static final int SDK_INT = 30;
    }

    public static class VERSION_CODES {
        public static final int R = 30;
    }
}
