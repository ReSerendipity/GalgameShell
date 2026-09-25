package com.winlator.galgame.debug;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.winlator.XServerDisplayActivity;

/**
 * 调试启动桥（仅用于真机无头回归，如 `adb shell am start` 拉起游戏容器）。
 *
 * 设备约束：{@link XServerDisplayActivity} 为 {@code exported=false}，在 ColorOS / Android 16
 * 上 `run-as` 拉起会被安全策略拒绝（package=com.android.shell 与 app uid 不匹配）。
 * 本活动为新增类、{@code exported=true}，从外部（adb shell，uid 2000）启动本活动后，
 * 由应用自身 uid 内部转发 Intent 到 XServerDisplayActivity（同 uid 允许），从而无头触发
 * 「点启动直接进游戏」的端到端路径，便于抓取 logcat 验证驱动/渲染。
 *
 * 零改铁律：不修改任何 Winlator 本体类；仅新增此类并转发 Intent。
 * 仅接收显式组件 Intent（需同时提供 container_id 与 exec_path），不做任何 UI。
 */
public final class GalgameDebugLaunchActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent src = getIntent();
        int containerId = src.getIntExtra("container_id", 0);
        String execPath = src.getStringExtra("exec_path");

        Intent forward = new Intent(this, XServerDisplayActivity.class);
        forward.putExtra("container_id", containerId);
        if (execPath != null && !execPath.isEmpty()) {
            forward.putExtra("exec_path", execPath);
        }

        startActivity(forward);
        finish();
    }
}
