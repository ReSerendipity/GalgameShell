package org.tvp.kirikiri2;

/**
 * Kirikiroid2（Kirikiri2 / KirikiriZ）引擎 Activity —— 已集成进 GalgameShell 本 APK。
 *
 * <p>本文件改写自上游 <a href="https://github.com/zeas2/Kirikiroid2">zeas2/Kirikiroid2</a>
 * 的 {@code project/android/src/org/tvp/kirikiri2/KR2Activity.java}，
 * 许可证为 <b>BSD 3-Clause</b>（见上游 LICENSE）：可静态并入本项目，保留版权与免责声明即可。
 *
 * <p><b>包名与类名不得改动</b>：libgame.so 的 JNI 表按硬编码字符串
 * {@code org/tvp/kirikiri2/KR2Activity} 绑定，改名会导致 UnsatisfiedLinkError。
 * 与之配对的原生库（随本 APK 打包在 jniLibs/arm64-v8a）导出的方法见下方 16 个 native 声明。
 *
 * <p>相对上游的本地化改动（为适配本项目 / 现代 Android，均为等价删减，不改变引擎行为）：
 * <ul>
 *   <li>去掉 {@code android.support.v4} 的 Storage Access Framework 分支（本项目无该依赖，
 *       相关方法保留签名但不再走 SAF）。</li>
 *   <li>去掉 {@code MediaStoreHack} / {@code MediaStoreUtil}（KitKat 外置卡补丁，API 35 下永不命中）。</li>
 *   <li>去掉 {@code TelephonyManager} 取设备号（READ_PHONE_STATE 已不可靠且本项目不申请该权限）。</li>
 *   <li>{@code hideSystemUI} 不再覆盖 cocos2d-x 的方法（3.17.2 无同名方法），改为自身方法。</li>
 *   <li>加载 {@code libgame.so}：上游依赖 manifest 的 {@code android.app.lib_name} meta-data，
 *       本项目不设该 meta-data，故这里显式载入 ffmpeg / SDL2 / game 三个库（顺序不可颠倒，
 *       因为 libgame.so 在装载期就要解析 ffmpeg 与 SDL 的符号）。</li>
 * </ul>
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.cocos2dx.lib.Cocos2dxActivity;
import org.cocos2dx.lib.Cocos2dxGLSurfaceView;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings.Secure;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public class KR2Activity extends Cocos2dxActivity {

    /* ============================================================
     * 原生库加载（顺序：ffmpeg / SDL2 先于 game，libgame.so 装载期要解析它们的符号）
     * ============================================================ */
    static {
        try {
            System.loadLibrary("ffmpeg");
        } catch (Throwable e) {
            Log.w("KR2Activity", "load libffmpeg.so failed", e);
        }
        try {
            System.loadLibrary("SDL2");
        } catch (Throwable e) {
            Log.w("KR2Activity", "load libSDL2.so failed", e);
        }
        try {
            System.loadLibrary("game");
        } catch (Throwable e) {
            Log.w("KR2Activity", "load libgame.so failed", e);
        }
    }

    static ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
    static ActivityManager mAcitivityManager = null;
    static Debug.MemoryInfo mDbgMemoryInfo = new Debug.MemoryInfo();

    public static void updateMemoryInfo() {
        if (mAcitivityManager == null) {
            mAcitivityManager = (ActivityManager) sInstance.getSystemService(Activity.ACTIVITY_SERVICE);
        }
        mAcitivityManager.getMemoryInfo(memoryInfo);
        Debug.getMemoryInfo(mDbgMemoryInfo);
    }

    public static long getAvailMemory() {
        return memoryInfo.availMem;
    }

    public static long getUsedMemory() {
        return mDbgMemoryInfo.getTotalPss(); // in kB
    }

    // GalgameShell：Android 框架的 Context.getDeviceId(Context) 是静态方法（API 14+），
    // 子类里再声明同名无参方法会报「无法覆盖 ContextWrapper 中的 getDeviceId（覆盖的方法为 static）」。
    // 上游 Kirikiroid2 侧 native 并未 dlsym 该符号（已对 libgame.so 取证：26 个
    // Java_org_tvp_kirikiri2_KR2Activity_* 里不含 getDeviceId），因此改名是安全的。
    // 上游取 TelephonyManager.getDeviceId()；本项目不申请 READ_PHONE_STATE，
    // 退化为 ANDROID_ID / SERIAL 兜底（仅用于 About 页展示）。
    static public String getDeviceIdString() {
        try {
            String androidId = Secure.getString(GetInstance().getContentResolver(), Secure.ANDROID_ID);
            if (null != androidId && androidId.length() > 8 && !"9774d56d682e549c".equals(androidId)) {
                return "AndroidID:" + androidId;
            }
        } catch (Exception e) {
            Log.w("KR2Activity", "getDeviceIdString failed", e);
        }
        if (null != Build.SERIAL && Build.SERIAL.length() > 3) {
            return "AndroidID:" + Build.SERIAL;
        }
        return "";
    }

    static public KR2Activity sInstance;
    static public KR2Activity GetInstance() { return sInstance; }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        sInstance = this;
        Sp = PreferenceManagerCompat.getDefaultSharedPreferences(this);
        super.onCreate(savedInstanceState);
        try {
            initDump(this.getFilesDir().getAbsolutePath() + "/dump");
        } catch (Throwable e) {
            Log.w("KR2Activity", "initDump failed", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        System.exit(0);
    }

    @Override
    public void onLowMemory() {
        nativeOnLowMemory();
    }

    static class DialogMessage {
        public String Title;
        public String Text;
        public String[] Buttons;
        public EditText TextEditor = null;

        public DialogMessage() {
        }

        public void Init(final String title, final String text, final String[] buttons) {
            this.Title = title;
            this.Text = text;
            this.Buttons = buttons;
        }

        void onButtonClick(int n) {
            if (TextEditor != null) {
                onMessageBoxText(TextEditor.getText().toString());
            }
            onMessageBoxOK(n);
        }

        public AlertDialog.Builder CreateBuilder() {
            AlertDialog.Builder builder = new AlertDialog.Builder(sInstance).setTitle(Title)
                    .setMessage(Text).setCancelable(false);
            if (Buttons.length >= 1) {
                builder = builder.setPositiveButton(Buttons[0], new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onButtonClick(0);
                    }
                });
            }
            if (Buttons.length >= 2) {
                builder = builder.setNeutralButton(Buttons[1], new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onButtonClick(1);
                    }
                });
            }
            if (Buttons.length >= 3) {
                builder = builder.setNegativeButton(Buttons[2], new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onButtonClick(2);
                    }
                });
            }
            return builder;
        }

        public void ShowMessageBox() {
            CreateBuilder().create().show();
        }

        public void ShowInputBox(final String text) {
            AlertDialog.Builder builder = CreateBuilder();
            TextEditor = new EditText(sInstance);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            TextEditor.setLayoutParams(lp);
            TextEditor.setText(text);
            builder.setView(TextEditor);
            AlertDialog ad = builder.create();
            ad.show();
            TextEditor.requestFocus();
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(TextEditor, 0);
        }
    }

    static DialogMessage mDialogMessage = new DialogMessage();

    protected static View mTextEdit = null;
    SharedPreferences Sp;

    static Handler msgHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            sInstance.handleMessage(msg);
        }
    };

    public void handleMessage(Message msg) {
    }

    static public void ShowMessageBox(final String title, final String text, final String[] Buttons) {
        mDialogMessage.Init(title, text, Buttons);
        msgHandler.post(new Runnable() {
            @Override
            public void run() {
                mDialogMessage.ShowMessageBox();
            }
        });
    }

    static public void ShowInputBox(final String title, final String prompt, final String text, final String[] Buttons) {
        mDialogMessage.Init(title, prompt, Buttons);
        msgHandler.post(new Runnable() {
            @Override
            public void run() {
                mDialogMessage.ShowInputBox(text);
            }
        });
    }

    static class ShowTextInputTask implements Runnable {
        static final int HEIGHT_PADDING = 15;

        public int x, y, w, h;

        public ShowTextInputTask(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        @Override
        public void run() {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(w, h + HEIGHT_PADDING);
            params.leftMargin = x;
            params.topMargin = y;

            if (mTextEdit == null) {
                mTextEdit = new DummyEdit(getContext());
                sInstance.mFrameLayout.addView(mTextEdit, params);
            } else {
                mTextEdit.setLayoutParams(params);
            }

            mTextEdit.setVisibility(View.VISIBLE);
            mTextEdit.requestFocus();

            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(mTextEdit, 0);
        }
    }

    static public void showTextInput(int x, int y, int w, int h) {
        msgHandler.post(new ShowTextInputTask(x, y, w, h));
    }

    static public void hideTextInput() {
        msgHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mTextEdit != null) {
                    mTextEdit.setVisibility(View.GONE);
                    InputMethodManager imm = (InputMethodManager) sInstance.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(mTextEdit.getWindowToken(), 0);
                }
            }
        });
    }

    /* ---- 与 libgame.so 绑定的原生方法（16 个，签名不得改） ---- */
    static private native void onMessageBoxOK(int nButton);
    static private native void onMessageBoxText(String text);
    static private native void onNativeExit(); // 上游保留；debloated 未导出，永不调用
    static public native void onNativeInit();  // 上游保留；debloated 未导出，永不调用
    static public native void onBannerSizeChanged(int w, int h);
    static private native void initDump(String path);
    static private native void nativeOnLowMemory();

    static private native void nativeTouchesBegin(final int id, final float x, final float y);
    static private native void nativeTouchesEnd(final int id, final float x, final float y);
    static private native void nativeTouchesMove(final int[] ids, final float[] xs, final float[] ys);
    static private native void nativeTouchesCancel(final int[] ids, final float[] xs, final float[] ys);
    public static native boolean nativeKeyAction(final int keyCode, final boolean isPress);
    public static native void nativeCharInput(final int keyCode);
    public static native void nativeCommitText(String text, int newCursorPosition);
    private static native void nativeInsertText(final String text);
    public static native void nativeDeleteBackward();
    // 出厂 dex 取证（dexdump 对 org/tvp/kirikiri2/KR2Activity）：nativeGetContentText 返回 String。
    // 返回类型写错会在运行期被 JVM 判为 native 方法签名不匹配（UnsatisfiedLinkError），必须对齐。
    private static native String nativeGetContentText();
    private static native void nativeHoverMoved(final float x, final float y);
    private static native void nativeMouseScrolled(final float scroll);
    private static native boolean nativeGetHideSystemButton();

    static public void MessageController(int what, int arg1, int arg2) {
        Message msg = msgHandler.obtainMessage();
        msg.what = what;
        msg.arg1 = arg1;
        msg.arg2 = arg2;
        msgHandler.sendMessage(msg);
    }

    static public String GetVersion() {
        String verstr = null;
        try {
            verstr = sInstance.getPackageManager().getPackageInfo(sInstance.getPackageName(), 0).versionName;
        } catch (NameNotFoundException e1) {
        }
        return verstr;
    }

    /** 上游用 StorageManager 的隐藏 API 列外置卡；API 30+ 该方法不存在，这里静默降级。 */
    StorageManagerCompat mStorageManagerCompat = null;

    public String[] getStoragePath() {
        if (mStorageManagerCompat == null) {
            mStorageManagerCompat = new StorageManagerCompat();
        }
        return mStorageManagerCompat.getVolumePaths();
    }

    /* ---- 视图 ---- */
    class KR2GLSurfaceView extends Cocos2dxGLSurfaceView {

        public KR2GLSurfaceView(final Context context) {
            super(context);
        }

        public KR2GLSurfaceView(final Context context, final AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public void insertText(final String pText) {
            nativeInsertText(pText);
        }

        @Override
        public void deleteBackward() {
            nativeDeleteBackward();
        }

        @Override
        public boolean onKeyDown(final int pKeyCode, final KeyEvent pKeyEvent) {
            switch (pKeyCode) {
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    nativeKeyAction(pKeyCode, true);
                    return true;
                default:
                    return super.onKeyDown(pKeyCode, pKeyEvent);
            }
        }

        @Override
        public boolean onKeyUp(final int pKeyCode, final KeyEvent pKeyEvent) {
            switch (pKeyCode) {
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    nativeKeyAction(pKeyCode, false);
                    return true;
                default:
                    return super.onKeyUp(pKeyCode, pKeyEvent);
            }
        }

        @Override
        public boolean onHoverEvent(final MotionEvent pMotionEvent) {
            final int pointerNumber = pMotionEvent.getPointerCount();
            final float[] xs = new float[pointerNumber];
            final float[] ys = new float[pointerNumber];
            for (int i = 0; i < pointerNumber; i++) {
                xs[i] = pMotionEvent.getX(i);
                ys[i] = pMotionEvent.getY(i);
            }
            switch (pMotionEvent.getActionMasked()) {
                case MotionEvent.ACTION_HOVER_MOVE:
                    nativeHoverMoved(xs[0], ys[0]);
                    break;
                default:
                    break;
            }
            return true;
        }

        @Override
        public boolean onTouchEvent(final MotionEvent pMotionEvent) {
            final int pointerNumber = pMotionEvent.getPointerCount();
            final int[] ids = new int[pointerNumber];
            final float[] xs = new float[pointerNumber];
            final float[] ys = new float[pointerNumber];

            for (int i = 0; i < pointerNumber; i++) {
                ids[i] = pMotionEvent.getPointerId(i);
                xs[i] = pMotionEvent.getX(i);
                ys[i] = pMotionEvent.getY(i);
            }

            switch (pMotionEvent.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    final int indexPointerDown = pMotionEvent.getAction() >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                    nativeTouchesBegin(pMotionEvent.getPointerId(indexPointerDown),
                            pMotionEvent.getX(indexPointerDown), pMotionEvent.getY(indexPointerDown));
                    break;
                case MotionEvent.ACTION_DOWN:
                    nativeTouchesBegin(pMotionEvent.getPointerId(0), xs[0], ys[0]);
                    break;
                case MotionEvent.ACTION_MOVE:
                    nativeTouchesMove(ids, xs, ys);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    final int indexPointUp = pMotionEvent.getAction() >> MotionEvent.ACTION_POINTER_INDEX_SHIFT;
                    nativeTouchesEnd(pMotionEvent.getPointerId(indexPointUp),
                            pMotionEvent.getX(indexPointUp), pMotionEvent.getY(indexPointUp));
                    break;
                case MotionEvent.ACTION_UP:
                    nativeTouchesEnd(pMotionEvent.getPointerId(0), xs[0], ys[0]);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    nativeTouchesCancel(ids, xs, ys);
                    break;
            }
            return true;
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
        @Override
        public boolean onGenericMotionEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_SCROLL:
                    float v = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    nativeMouseScrolled(-v);
                    return true;
                default:
                    break;
            }
            return super.onGenericMotionEvent(event);
        }
    }

    // GalgameShell：Cocos2dxActivity.mGLContextAttrs 是 private 字段，子类无法直接访问，
    // 改为反射读取；读不到就按 null 处理（等价于不设 TRANSLUCENT 格式）。
    private int[] readGLContextAttrs() {
        try {
            java.lang.reflect.Field field = Cocos2dxActivity.class.getDeclaredField("mGLContextAttrs");
            field.setAccessible(true);
            Object value = field.get(this);
            return value instanceof int[] ? (int[]) value : null;
        } catch (Exception e) {
            Log.w("KR2Activity", "read mGLContextAttrs failed", e);
            return null;
        }
    }

    @Override
    public Cocos2dxGLSurfaceView onCreateView() {
        KR2GLSurfaceView glSurfaceView = new KR2GLSurfaceView(this);
        // 这行在指定 alpha bits 时某些设备必须，行为与 cocos2d-x 基类的默认实现一致
        int[] glAttrs = readGLContextAttrs();
        if (glAttrs != null && glAttrs.length > 3 && glAttrs[3] > 0) {
            glSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        }
        return glSurfaceView;
    }

    public int get_res_sd_operate_step() {
        return -1;
    }

    static final boolean isWritable(final File file) {
        if (file == null) return false;
        boolean isExisting = file.exists();
        try {
            FileOutputStream output = new FileOutputStream(file, true);
            try {
                output.close();
            } catch (IOException e) {
            }
        } catch (FileNotFoundException e) {
            return false;
        }
        boolean result = file.canWrite();
        if (!isExisting) file.delete();
        return result;
    }

    static final boolean isWritableNormalOrSaf(final String path) {
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory()) return false;
        int i = 0;
        File file;
        do {
            String fileName = "AugendiagnoseDummyFile" + (++i);
            file = new File(folder, fileName);
        } while (file.exists());
        return isWritable(file);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static String[] getExtSdCardPaths(Context context) {
        List<String> paths = new ArrayList<String>();
        for (File file : context.getExternalFilesDirs("external")) {
            if (file != null && !file.equals(context.getExternalFilesDir("external"))) {
                int index = file.getAbsolutePath().lastIndexOf("/Android/data");
                if (index < 0) {
                    Log.w("FileUtils", "Unexpected external file dir: " + file.getAbsolutePath());
                } else {
                    String path = file.getAbsolutePath().substring(0, index);
                    try {
                        path = new File(path).getCanonicalPath();
                    } catch (IOException e) {
                        // Keep non-canonical path.
                    }
                    paths.add(path);
                }
            }
        }
        return paths.toArray(new String[0]);
    }

    static String[] _extSdPaths;

    public static String getExtSdCardFolder(final File file, Context context) {
        if (_extSdPaths == null) _extSdPaths = getExtSdCardPaths(context);
        try {
            for (int i = 0; i < _extSdPaths.length; i++) {
                if (file.getCanonicalPath().startsWith(_extSdPaths[i])) return _extSdPaths[i];
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    public static boolean isOnExtSdCard(final File file, Context c) {
        return getExtSdCardFolder(file, c) != null;
    }

    /** 上游走 SAF DocumentFile；本项目无支持库依赖，统一返回 null（普通写入路径优先）。 */
    public static Object getDocumentFile(final File file, final boolean isDirectory, Context context) {
        return null;
    }

    static public boolean DeleteFile(String path) {
        File file = new File(path);
        boolean fileDelete = deleteFilesInFolder(file);
        return file.delete() || fileDelete;
    }

    public static final boolean deleteFilesInFolder(final File folder) {
        boolean totalSuccess = true;
        if (folder == null) return false;
        if (folder.isDirectory()) {
            for (File child : folder.listFiles()) {
                deleteFilesInFolder(child);
            }
            if (!folder.delete()) totalSuccess = false;
        } else {
            if (!folder.delete()) totalSuccess = false;
        }
        return totalSuccess;
    }

    public static OutputStream getOutputStream(final File target, Context context) throws Exception {
        if (isWritable(target)) return new FileOutputStream(target);
        return null;
    }

    static public boolean WriteFile(String path, byte data[]) {
        File target = new File(path);
        if (target.exists()) {
            DeleteFile(target.getAbsolutePath());
        } else {
            File parent = target.getParentFile();
            if (!parent.exists()) CreateFolders(parent.getAbsolutePath());
        }
        try {
            OutputStream os = new FileOutputStream(target);
            os.write(data);
            os.close();
            return true;
        } catch (Exception e) {
            Log.e("FileUtils", "Error when writing " + target.getAbsolutePath(), e);
            return false;
        }
    }

    static public boolean CreateFolders(String path) {
        return new File(path).mkdirs();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    void doSetSystemUiVisibility() {
        int uiOpts = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(uiOpts);
    }

    void hideSystemUI() {
        if (nativeGetHideSystemButton() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            doSetSystemUiVisibility();
        }
    }

    static public String getLocaleName() {
        Locale defloc = Locale.getDefault();
        String lang = defloc.getLanguage();
        String country = defloc.getCountry();
        if (!country.isEmpty()) {
            lang += "_";
            lang += country.toLowerCase();
        }
        return lang;
    }

    static public void exit() {
        System.exit(0);
    }

    static final int ORIENT_VERTICAL = 1;
    static final int ORIENT_HORIZONTAL = 2;

    static public void setOrientation(int orient) {
        if (orient == ORIENT_VERTICAL) {
            sInstance.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (orient == ORIENT_HORIZONTAL) {
            sInstance.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    /** 上游 StorageManager.getVolumePaths 隐藏 API 的兼容封装（反射失败时返回空数组）。 */
    private static class StorageManagerCompat {
        Object storageManager;
        Method methodGetPaths;
        Method getVolumeState;

        String[] getVolumePaths() {
            try {
                storageManager = sInstance.getSystemService(Context.STORAGE_SERVICE);
                if (storageManager == null) return new String[0];
                methodGetPaths = storageManager.getClass().getMethod("getVolumePaths");
                getVolumeState = storageManager.getClass().getMethod("getVolumeState", String.class);
                Object result = methodGetPaths.invoke(storageManager);
                String[] paths = (String[]) result;
                for (int i = 0; i < paths.length; ++i) {
                    String status = (String) getVolumeState.invoke(storageManager, paths[i]);
                    if (!Environment.MEDIA_MOUNTED.equals(status) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(status)) {
                        paths[i] = null;
                    }
                }
                return paths;
            } catch (Exception e) {
                Log.w("KR2Activity", "getVolumePaths unavailable", e);
                return new String[0];
            }
        }
    }

    /* ============================================================
     * 软键盘输入桥（与上游一致）
     * ============================================================ */
    static class DummyEdit extends View implements View.OnKeyListener {
        InputConnection ic;

        public DummyEdit(Context context) {
            super(context);
            setFocusableInTouchMode(true);
            setFocusable(true);
            setOnKeyListener(this);
        }

        @Override
        public boolean onCheckIsTextEditor() {
            return true;
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.isPrintingKey()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    ic.commitText(String.valueOf((char) event.getUnicodeChar()), 1);
                }
                return true;
            }
            return false;
        }

        @Override
        public boolean onKeyPreIme(int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                if (KR2Activity.mTextEdit != null && KR2Activity.mTextEdit.getVisibility() == View.VISIBLE) {
                    KR2Activity.hideTextInput();
                }
            }
            return super.onKeyPreIme(keyCode, event);
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            ic = new SDLInputConnection(this, true);
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | 33554432;
            return ic;
        }
    }

    static class SDLInputConnection extends BaseInputConnection {
        public SDLInputConnection(View targetView, boolean fullEditor) {
            super(targetView, fullEditor);
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            int keyCode = event.getKeyCode();
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.isPrintingKey()) {
                    commitText(String.valueOf((char) event.getUnicodeChar()), 1);
                    nativeCharInput(keyCode);
                } else if (keyCode == KeyEvent.KEYCODE_DEL) {
                    nativeKeyAction(keyCode, true);
                }
                return true;
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    nativeKeyAction(keyCode, false);
                }
                return true;
            }
            return super.sendKeyEvent(event);
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            nativeCommitText(text.toString(), newCursorPosition);
            return super.commitText(text, newCursorPosition);
        }
    }

    /** 上游用 PreferenceManager.getDefaultSharedPreferences；这里内联等价实现，避免额外引用。 */
    private static class PreferenceManagerCompat {
        static SharedPreferences getDefaultSharedPreferences(Context context) {
            return context.getSharedPreferences("org.tvp.kirikiri2_preferences", Context.MODE_PRIVATE);
        }
    }
}
