package com.winlator.xenvironment;
import android.content.Context;
import java.io.File;

/**
 * CI 桩：RootFS（com.winlator.xenvironment，真实类见 app/src/main/java/com/winlator/xenvironment/）。
 * 仅保留热点入口类引用到的符号。勿用于运行。
 */
public class RootFS {
    public static RootFS find(Context context) { return null; }
    public File getRootDir() { return null; }
    public File getInstalledWineDir() { return null; }
    public boolean isValid() { return false; }
}
