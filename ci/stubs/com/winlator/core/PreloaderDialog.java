package com.winlator.core;
import android.app.Activity;

/**
 * CI 桩：PreloaderDialog（com.winlator.core，真实类见 app/src/main/java/com/winlator/core/）。
 * 门禁只编译热点清单内的 com.winlator 顶层类，此处提供最小符号。勿用于运行。
 */
public class PreloaderDialog {
    public PreloaderDialog(Activity activity) {}
    public synchronized void show(int textResId) {}
    public void showOnUiThread(int textResId) {}
    public synchronized void close() {}
    public void closeOnUiThread() {}
    public boolean isShowing() { return false; }
}
