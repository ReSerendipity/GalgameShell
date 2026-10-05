package com.winlator;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.winlator.MainActivity;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.LocaleHelper;
import com.winlator.core.PreloaderDialog;
import com.winlator.xenvironment.RootFSInstaller;

/**
 * GalgameShell R2 底部导航壳：启动即游戏库，4 Tab（游戏库 / 容器 / 工具 / 设置）替代原抽屉。
 *
 * <p>复刻 {@link MainActivity} 的主题 / 语言 / 权限 / RootFS 安装链路，并实现 {@link GalgameHost}
 * 使通用 Fragment 可经宿主导航。游戏库 Tab 隐藏顶部 AppBar（库自带品牌头部），
 * 其余 Tab 显示顶部 AppBar 承载分区标题。
 */
public class GalgameMainActivity extends AppCompatActivity implements GalgameHost, BottomNavigationView.OnNavigationItemSelectedListener {

    private static final int PERMISSION_REQUEST_CODE = 1;

    private final PreloaderDialog preloaderDialog = new PreloaderDialog(this);
    private Callback<Uri> openFileCallback;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_galgame_main);

        Toolbar toolbar = findViewById(R.id.galgameToolbar);
        setSupportActionBar(toolbar);

        bottomNav = findViewById(R.id.galgameBottomNav);
        bottomNav.setOnNavigationItemSelectedListener(this);

        if (!requestAppPermissions()) {
            RootFSInstaller.installIfNeeded(this);
        }

        // 启动即游戏库：选中首页 Tab 会触发 onNavigationItemSelected 装载首页 Fragment
        bottomNav.setSelectedItemId(R.id.nav_galgame_library);
    }

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                RootFSInstaller.installIfNeeded(this);
            }
            else finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 与 MainActivity 共用文件选择请求码常量，路由到 openFileCallback
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (openFileCallback != null) {
                openFileCallback.call(data != null ? data.getData() : null);
                openFileCallback = null;
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        int itemId = item.getItemId();
        if (itemId == R.id.nav_galgame_library) {
            showTabFragment(new GalgameHomeFragment(), false, 0);
        }
        else if (itemId == R.id.nav_galgame_containers) {
            // C4：上游容器屏降级为末位 Tab，改名「容器管理」（文案在 galgame_strings.xml）
            showTabFragment(new ContainersFragment(), true, R.string.galgame_containers_title);
        }
        else if (itemId == R.id.nav_galgame_tools) {
            showTabFragment(new GalgameToolsFragment(), true, R.string.galgame_tools_title);
        }
        else if (itemId == R.id.nav_galgame_settings) {
            // GalgameShell R3：设置 Tab 改用自有 GalgameSettingsFragment（解耦上游抽屉 IA）
            showTabFragment(new GalgameSettingsFragment(), true, R.string.settings);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        }
        else finish();
    }

    /** 装载某一 Tab 的根 Fragment（清空下钻回退栈，不计入回退栈）。 */
    private void showTabFragment(Fragment fragment, boolean showAppBar, int titleRes) {
        getSupportFragmentManager().beginTransaction()
            // GalgameShell：Fragment 切换淡入淡出（复用游戏的现代过渡动画）
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.galgameFragmentContainer, fragment)
            .commit();

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            if (showAppBar) {
                ab.show();
                if (titleRes != 0) ab.setTitle(titleRes);
            }
            else {
                ab.hide();
            }
        }
    }

    // ---- GalgameHost 契约 ----

    @Override
    public void showFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .replace(R.id.galgameFragmentContainer, fragment)
            .addToBackStack(null)
            .commit();
    }

    @Override
    public void setOpenFileCallback(Callback<Uri> callback) {
        this.openFileCallback = callback;
    }

    @Override
    public PreloaderDialog getPreloaderDialog() {
        return preloaderDialog;
    }

    private boolean requestAppPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return false;

        ActivityCompat.requestPermissions(this, new String[] {
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE
        }, PERMISSION_REQUEST_CODE);
        return true;
    }
}
