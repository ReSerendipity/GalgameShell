package com.winlator.contentdialog;
import android.content.Context;

/**
 * CI 桩：ContentDialog（com.winlator.contentdialog，真实类见 app/src/main/java/com/winlator/contentdialog/）。
 * 仅保留热点入口类引用到的符号。勿用于运行。
 */
public class ContentDialog {
    public ContentDialog(Context context) {}
    public ContentDialog(Context context, int layoutResId) {}
    public void setTitle(int titleResId) {}
    public void setTitle(String title) {}
    public void setMessage(int msgResId) {}
    public void setMessage(String message) {}
    public void setOnConfirmCallback(Runnable onConfirmCallback) {}
    public void setOnCancelCallback(Runnable onCancelCallback) {}
    public void show() {}
    public void dismiss() {}
    public static void alert(Context context, int msgResId, Runnable callback) {}
    public static void confirm(Context context, int msgResId, Runnable callback) {}
}
