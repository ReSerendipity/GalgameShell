package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.StorageInfoDialog;
import com.winlator.core.PreloaderDialog;
import com.winlator.xenvironment.RootFS;

import com.winlator.GalgameHost;

import java.util.ArrayList;
import java.util.List;

public class ContainersFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private ContainerManager manager;
    private PreloaderDialog preloaderDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        manager = new ContainerManager(getContext());
        loadContainersList();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.containers);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout frameLayout = (FrameLayout)inflater.inflate(R.layout.containers_fragment, container, false);
        recyclerView = frameLayout.findViewById(R.id.RecyclerView);
        Context context = recyclerView.getContext();
        emptyTextView = frameLayout.findViewById(R.id.TVEmptyText);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        // GalgameShell：列表项已卡片化（自带上/下间距），不再需要分隔线
        return frameLayout;
    }

    private void loadContainersList() {
        ArrayList<Container> containers = manager.getContainers();
        recyclerView.setAdapter(new ContainersAdapter(containers));
        if (containers.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.containers_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.menu_item_add) {
            if (!RootFS.find(getContext()).isValid()) return false;
            // GalgameShell R4：经 GalgameHost 路由到正确 Fragment 容器（新壳 galgameFragmentContainer /
            // 旧壳 FLFragmentContainer），避免硬编码 R.id.FLFragmentContainer 在新壳布局中不存在而崩溃
            ((GalgameHost)getActivity()).showFragment(new ContainerDetailFragment());
            return true;
        }
        else if (menuItem.getItemId() == R.id.menu_item_galgame_library) {
            // GalgameShell 入口（新增价值层，不改动上游既有逻辑）
            startActivity(new Intent(getContext(), com.winlator.galgame.ui.GalgameLibraryActivity.class));
            return true;
        }
        else return super.onOptionsItemSelected(menuItem);
    }

    private class ContainersAdapter extends RecyclerView.Adapter<ContainersAdapter.ViewHolder> {
        private final List<Container> data;

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView runButton;
            private final ImageView menuButton;
            private final ImageView imageView;
            private final TextView title;
            private final TextView subtitle;
            private final TextView badge;

            private ViewHolder(View view) {
                super(view);
                this.imageView = view.findViewById(R.id.ImageView);
                this.title = view.findViewById(R.id.TVTitle);
                this.subtitle = view.findViewById(R.id.TVSubtitle);
                this.badge = view.findViewById(R.id.TVBadge);
                this.runButton = view.findViewById(R.id.BTRun);
                this.menuButton = view.findViewById(R.id.BTMenu);
            }
        }

        public ContainersAdapter(List<Container> data) {
            this.data = data;
        }

        @Override
        public final ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.container_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final Container item = data.get(position);
            holder.imageView.setImageResource(R.drawable.icon_container);
            holder.title.setText(item.getName());

            // GalgameShell：副标题显示图形驱动 + 分辨率，让卡片信息更完整（消除简陋感）
            String driver = item.getGraphicsDriver();
            if (driver != null && driver.contains(",")) driver = driver.substring(0, driver.indexOf(','));
            String screen = item.getScreenSize();
            if (screen != null) screen = screen.replace('x', '×');
            holder.subtitle.setText(((driver != null ? driver : "") + "  ·  " + (screen != null ? screen : "")).trim());

            // 引擎 badge（从容器 extraData 读取 galgame_engine；非 galgame 容器隐藏）
            String engine = item.getExtra("galgame_engine", "");
            if (engine != null && !engine.isEmpty()) {
                holder.badge.setVisibility(View.VISIBLE);
                holder.badge.setText(engine);
            }
            else {
                holder.badge.setVisibility(View.GONE);
            }

            holder.runButton.setOnClickListener((view) -> runContainer(item));
            holder.menuButton.setOnClickListener((view) -> showListItemMenu(view, item));
        }

        @Override
        public final int getItemCount() {
            return data.size();
        }

        private void showListItemMenu(View anchorView, Container container) {
            // GalgameShell R2：宿主契约化（抽屉壳 / 新底部导航壳共用）
            GalgameHost activity = (GalgameHost)getActivity();
            PopupMenu listItemMenu = new PopupMenu(getActivity(), anchorView);
            listItemMenu.inflate(R.menu.container_popup_menu);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listItemMenu.setForceShowIcon(true);

            listItemMenu.setOnMenuItemClickListener((menuItem) -> {
                switch (menuItem.getItemId()) {
                    case R.id.menu_item_file_manager:
                        activity.showFragment(new ContainerFileManagerFragment(container.id));
                        break;
                    case R.id.menu_item_edit:
                        activity.showFragment(new ContainerDetailFragment(container.id));
                        break;
                    case R.id.menu_item_duplicate:
                        ContentDialog.confirm(getContext(), R.string.do_you_want_to_duplicate_this_container, () -> {
                            preloaderDialog.show(R.string.duplicating_container);
                            manager.duplicateContainerAsync(container, () -> {
                                preloaderDialog.close();
                                loadContainersList();
                            });
                        });
                        break;
                    case R.id.menu_item_remove:
                        ContentDialog.confirm(getContext(), R.string.do_you_want_to_remove_this_container, () -> {
                            preloaderDialog.show(R.string.removing_container);
                            manager.removeContainerAsync(container, () -> {
                                preloaderDialog.close();
                                loadContainersList();
                            });
                        });
                        break;
                    case R.id.menu_item_info:
                        (new StorageInfoDialog(getActivity(), container)).show();
                        break;
                }
                return true;
            });
            listItemMenu.show();
        }

        private void runContainer(Container container) {
            // B 修复（2026-09-28）：galgame 容器若启动 exe 缺失，提示并取消启动，
            // 避免 wine 跑空路径立即退出后静默弹回游戏库（「打开游戏进不去」根因）。
            // 非 galgame 容器（无 overlay）不拦截，照常走文件管理器。
            if (com.winlator.galgame.GalgameLaunchHelper.isExecMissing(container)) {
                Toast.makeText(getActivity(), getString(R.string.galgame_exe_missing), Toast.LENGTH_LONG).show();
                return;
            }

            Activity activity = getActivity();
            Intent intent = new Intent(activity, XServerDisplayActivity.class);
            intent.putExtra("container_id", container.id);
            // GalgameShell：容器带 galgame_overlay.json 时直接运行游戏（免进 Windows 桌面找 exe）
            com.winlator.galgame.GalgameLaunchHelper.injectExecPath(container, intent);
            activity.startActivity(intent);
        }
    }
}
