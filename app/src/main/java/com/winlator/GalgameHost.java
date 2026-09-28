package com.winlator;

import androidx.fragment.app.Fragment;
import android.net.Uri;

import com.winlator.core.Callback;
import com.winlator.core.PreloaderDialog;

/**
 * GalgameShell R2 宿主契约。
 *
 * <p>新的底部导航壳 {@link GalgameMainActivity} 与旧抽屉壳 {@link MainActivity} 共同实现本接口，
 * 使通用 Fragment（容器列表 / 输入控制 / 设置 / 工具入口等）无需耦合到具体 Activity 类型，
 * 从而可在两套壳下复用同一份导航逻辑。
 *
 * <p>仅声明 GalgameShell 自有能力；纯 Android 能力（{@code runOnUiThread}、
 * {@code startActivityForResult}、{@code startActivityFromFragment} 等）由调用方按
 * {@code FragmentActivity} 自行使用，避免在本接口重复声明平台方法。
 */
public interface GalgameHost {
    /** 在当前内容容器内切换显示一个 Fragment（用于 Tab 内下钻导航）。 */
    void showFragment(Fragment fragment);

    /** 设置「打开文件」结果回调，由宿主的 onActivityResult 路由。 */
    void setOpenFileCallback(Callback<Uri> callback);

    /** 取得宿主的预加载对话框（耗时操作进度提示）。 */
    PreloaderDialog getPreloaderDialog();
}
