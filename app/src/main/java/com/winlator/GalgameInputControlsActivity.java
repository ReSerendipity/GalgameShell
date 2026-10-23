package com.winlator;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

/**
 * 输入控制宿主（Swiss）：把上游 {@link InputControlsFragment}（配置档 / 指针 / 外部控制器 /
 * 控件编辑器入口）挂进新壳，替代此前「设置 → 输入控制」直接跳高级设置的死路。
 *
 * <p>Fragment 依赖 {@code getSupportActionBar()} 与 {@code onActivityResult} 转发
 * （导入配置档走系统文件选择器），故宿主必须提供 ActionBar 并把结果回灌给 Fragment。
 */
public class GalgameInputControlsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_galgame_input_controls);
        setSupportActionBar(findViewById(R.id.inputControlsToolbar));
        findViewById(R.id.BTBack).setOnClickListener(v -> finish());

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.inputControlsContainer, new InputControlsFragment(0))
                    .commit();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.inputControlsContainer);
        if (fragment != null) fragment.onActivityResult(requestCode, resultCode, data);
    }
}
