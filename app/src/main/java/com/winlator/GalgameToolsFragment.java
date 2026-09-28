package com.winlator;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.winlator.ExternalControllerBindingsActivity;
import com.winlator.InputControlsFragment;
import com.winlator.ShortcutsFragment;
import com.winlator.contentdialog.AboutDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * GalgameShell R2 工具 Tab：聚合工具入口（快捷方式 / 输入控制 / 外部控制器 / 关于）。
 *
 * <p>点击经 {@link GalgameHost} 复用现有 Fragment / Activity / Dialog，不重复实现逻辑。
 */
public class GalgameToolsFragment extends Fragment {

    /** 工具入口：icon + 标题 + 动作类型。 */
    private static final class ToolEntry {
        final int icon;
        final String title;
        final int action; // 0=快捷方式 1=输入控制 2=外部控制器 3=关于
        ToolEntry(int icon, String title, int action) {
            this.icon = icon;
            this.title = title;
            this.action = action;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_galgame_tools, container, false);

        ListView list = view.findViewById(R.id.galgameToolsList);
        List<ToolEntry> entries = new ArrayList<>();
        entries.add(new ToolEntry(android.R.drawable.ic_menu_agenda, getString(R.string.shortcuts), 0));
        entries.add(new ToolEntry(android.R.drawable.ic_menu_edit, getString(R.string.input_controls), 1));
        entries.add(new ToolEntry(android.R.drawable.ic_menu_preferences, getString(R.string.external_controller), 2));
        entries.add(new ToolEntry(android.R.drawable.ic_menu_info_details, getString(R.string.galgame_about), 3));

        list.setAdapter(new ArrayAdapter<ToolEntry>(requireContext(), R.layout.galgame_tool_row, R.id.title, entries) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View row = super.getView(position, convertView, parent);
                ImageView icon = row.findViewById(R.id.icon);
                icon.setImageResource(entries.get(position).icon);
                return row;
            }
        });

        list.setOnItemClickListener((parent, v, position, id) -> onToolSelected(entries.get(position)));
        return view;
    }

    private void onToolSelected(ToolEntry entry) {
        // 经宿主契约导航/启动，抽屉壳与新底部导航壳共用逻辑
        GalgameHost host = (GalgameHost) requireActivity();
        switch (entry.action) {
            case 0:
                host.showFragment(new ShortcutsFragment());
                break;
            case 1:
                host.showFragment(new InputControlsFragment(0));
                break;
            case 2:
                startActivity(new Intent(requireActivity(), ExternalControllerBindingsActivity.class));
                break;
            case 3:
                new AboutDialog(requireActivity()).show();
                break;
        }
    }
}
