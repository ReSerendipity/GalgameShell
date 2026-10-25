package com.winlator;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * GalgameShell 桌面快捷方式直启转发（第六轮）。
 *
 * <p>桌面 pinned shortcut 的 intent 目标必须是 exported 组件，而
 * {@link GalgameDetailActivity}（含完整启动链与自动启动支持
 * {@link GalgameDetailActivity#EXTRA_AUTO_LAUNCH}）不对外暴露——本 Activity 只做
 * 一层透明转发：带 {@code game_id} 跳转详情页并自动启动，随后立即 finish。
 * 游戏不存在（已被删除）时提示并退出。
 */
public class GalgameShortcutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String gameId = getIntent().getStringExtra("game_id");
        if (gameId == null || gameId.isEmpty()
                || com.winlator.galgame.GalgameLibraryIndex.find(this, gameId) == null) {
            android.widget.Toast.makeText(this, "游戏不存在或已被删除", android.widget.Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Intent intent = new Intent(this, GalgameDetailActivity.class)
                .putExtra(GalgameDetailActivity.EXTRA_GAME_ID, gameId)
                .putExtra(GalgameDetailActivity.EXTRA_AUTO_LAUNCH, true);
        startActivity(intent);
        finish();
    }
}
