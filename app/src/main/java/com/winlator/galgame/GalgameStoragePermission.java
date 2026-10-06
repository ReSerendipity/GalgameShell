package com.winlator.galgame;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import com.winlator.R;

/**
 * MANAGE_EXTERNAL_STORAGE 运行时引导（评估报告 #3）。
 *
 * <p>Manifest 声明了「所有文件访问权」（Kirikiroid2 内置引擎需整库读写 /sdcard 游戏目录），
 * 但此前 galgame 包内零运行时申请代码，新用户首跑会在导入时静默失败。
 * 本类在库 Activity 启动时检测权限状态，未授权则弹说明对话框，
 * 一键跳到系统的「所有文件访问」设置页。
 *
 * <p>只在「未授权」时弹一次（用 SharedPreferences 记录已提示过），避免每次进库都骚扰；
 * 用户在设置页授权后返回 onResume 自然生效。
 */
public final class GalgameStoragePermission {

    private static final String PREFS = "galgame_storage_perm";
    private static final String KEY_PROMPTED = "prompted";

    private GalgameStoragePermission() {}

    /** 当前是否已获得所有文件访问权。API &lt; 30 走旧版外部存储，本检查不适用。 */
    public static boolean hasAllFilesAccess(Activity activity) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            return true; // 旧系统无需 MANAGE_EXTERNAL_STORAGE
        }
        try {
            return Environment.isExternalStorageManager();
        } catch (Throwable t) {
            // 部分 ROM 反射受限，按未授权处理，走引导
            Log.w("GalgameShell", "isExternalStorageManager check failed: " + t);
            return false;
        }
    }

    /**
     * 若未授权则弹引导对话框。返回 true 表示已授权（调用方无需再处理）。
     */
    public static boolean ensure(Activity activity) {
        if (hasAllFilesAccess(activity)) {
            return true;
        }

        // 已经提示过一次就不再弹（用户可在「诊断」里再次手动触发——后续迭代接入）
        android.content.SharedPreferences sp =
                activity.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        if (sp.getBoolean(KEY_PROMPTED, false)) {
            return false;
        }

        new android.app.AlertDialog.Builder(activity)
                .setTitle(R.string.galgame_storage_perm_title)
                .setMessage(R.string.galgame_storage_perm_message)
                .setPositiveButton(R.string.galgame_storage_perm_go, (dialog, which) -> openSettings(activity))
                .setNegativeButton(R.string.galgame_cancel, null)
                .setOnDismissListener(d ->
                        sp.edit().putBoolean(KEY_PROMPTED, true).apply())
                .show();
        return false;
    }

    private static void openSettings(Activity activity) {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        } catch (Throwable t) {
            // 个别 ROM 不支持带 package data 的 intent，退回通用入口
            try {
                Intent fallback = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivity(fallback);
            } catch (Throwable t2) {
                Log.w("GalgameShell", "open all-files-access settings failed: " + t2);
            }
        }
    }
}
