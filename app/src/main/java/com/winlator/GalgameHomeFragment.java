package com.winlator;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.galgame.GalgameCoverProvider;
import com.winlator.galgame.GalgameLibraryIndex;
import com.winlator.galgame.ui.GalgameLibraryAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 游戏库（Swiss 单首页）：报头 + 继续游玩 + 全部游戏索引网格。
 *
 * <p>点击卡片进入详情页（不再长按弹菜单）；齿轮进设置；FAB 进导入向导。
 */
public class GalgameHomeFragment extends Fragment {

    private static final String CONTAINER_PREFIX = "galgame-";

    private ContainerManager containerManager;
    private GalgameLibraryAdapter adapter;
    private final List<GalgameLibraryAdapter.Item> master = new ArrayList<>();
    private final List<GalgameLibraryAdapter.Item> shown = new ArrayList<>();

    private View rootView;
    private View emptyView;
    private GridView grid;
    private EditText search;
    private TextView countView;
    private TextView allCountView;
    private LinearLayout recentRow;
    private View recentContainer;

    private final ExecutorService coverExecutor = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_galgame_home, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        containerManager = new ContainerManager(requireContext());

        adapter = new GalgameLibraryAdapter(requireContext(), shown);
        adapter.setOnGameActionListener(new GalgameLibraryAdapter.OnGameActionListener() {
            @Override
            public void onGameClick(GalgameLibraryAdapter.Item item) { openDetail(item); }

            @Override
            public void onGameLongClick(GalgameLibraryAdapter.Item item) { enterSelectMode(item); }

            @Override
            public void onGameSelectionChanged() { updateSelectionBar(); }
        });

        grid = view.findViewById(R.id.GVGalgames);
        grid.setAdapter(adapter);
        emptyView = view.findViewById(R.id.LEmpty);
        search = view.findViewById(R.id.ETSearch);
        countView = view.findViewById(R.id.TVCount);
        allCountView = view.findViewById(R.id.TVAllCount);
        recentRow = view.findViewById(R.id.LLRecent);
        recentContainer = view.findViewById(R.id.HSVRecent);

        view.findViewById(R.id.BTSettings).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), GalgameSettingsActivity.class)));
        view.findViewById(R.id.FABImport).setOnClickListener(v -> openImport());
        view.findViewById(R.id.BTImportEmpty).setOnClickListener(v -> openImport());

        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onDestroyView() {
        // 选择条挂在 Activity 内容层上，Fragment 视图销毁前必须摘除，避免泄漏/遮挡
        if (adapter != null && adapter.isSelectionMode()) exitSelectMode();
        super.onDestroyView();
    }

    private void openImport() {
        startActivity(new Intent(requireContext(), GalgameImportActivity.class));
    }

    private void openDetail(GalgameLibraryAdapter.Item item) {
        Intent i = new Intent(requireContext(), GalgameDetailActivity.class);
        i.putExtra(GalgameDetailActivity.EXTRA_CONTAINER, item.container != null ? item.container.getName() : null);
        i.putExtra(GalgameDetailActivity.EXTRA_GAME_ID, item.gameId);
        i.putExtra(GalgameDetailActivity.EXTRA_LABEL, item.label);
        i.putExtra(GalgameDetailActivity.EXTRA_ENGINE, item.engineName);
        i.putExtra(GalgameDetailActivity.EXTRA_PREFERRED, item.preferredEngine);
        startActivity(i);
        requireActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // ---- 多选模式（第八轮：长按进入；批量彻底删除/仅移除记录）----

    private android.widget.LinearLayout selectionBar;
    private androidx.activity.OnBackPressedCallback selectBackCallback;

    private void enterSelectMode(GalgameLibraryAdapter.Item item) {
        adapter.enterSelectionMode(item != null ? item.gameId : null);
        showSelectionBar();
    }

    private void exitSelectMode() {
        adapter.exitSelectionMode();
        if (selectionBar != null && selectionBar.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) selectionBar.getParent()).removeView(selectionBar);
        }
        selectionBar = null;
        if (selectBackCallback != null) selectBackCallback.setEnabled(false);
    }

    private void showSelectionBar() {
        if (selectionBar == null) {
            float density = getResources().getDisplayMetrics().density;
            selectionBar = new android.widget.LinearLayout(requireActivity());
            selectionBar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            selectionBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
            // Swiss 纸面操作栏：白底 + 顶部 2dp 墨色分隔线（与全站纸面语言一致）
            android.graphics.drawable.GradientDrawable bg =
                    new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFFFFFFFF);
            bg.setStroke(Math.round(2 * density), 0xFF111111);
            selectionBar.setBackground(bg);
            int padH = Math.round(16 * density);
            int padV = Math.round(10 * density);
            selectionBar.setPadding(padH, padV, padH, padV);

            android.widget.TextView count = new android.widget.TextView(requireActivity());
            count.setTextColor(0xFF111111);
            count.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            count.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            count.setTag("count");
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            count.setLayoutParams(lp);
            selectionBar.addView(count);

            // 无框文字按钮：黑色大写字距；删除动作用 Swiss 红
            String[] labels = {"全选", "彻底删除", "仅移除", "完成"};
            String[] tags = {"all", "purge", "records", "exit"};
            for (int i = 0; i < labels.length; i++) {
                android.widget.TextView b = new android.widget.TextView(requireActivity());
                b.setText(labels[i]);
                b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
                b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                b.setLetterSpacing(0.06f);
                b.setAllCaps(true);
                b.setTextColor("purge".equals(tags[i]) || "records".equals(tags[i])
                        ? 0xFFB3261E : 0xFF111111);
                int margin = Math.round(12 * density);
                android.widget.LinearLayout.LayoutParams blp = new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.leftMargin = margin;
                b.setLayoutParams(blp);
                b.setPadding(Math.round(6 * density), Math.round(8 * density),
                        Math.round(6 * density), Math.round(8 * density));
                final String tag = tags[i];
                b.setOnClickListener(v -> onSelectionAction(tag));
                selectionBar.addView(b);
            }

            android.widget.FrameLayout.LayoutParams barLp = new android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.BOTTOM);
            requireActivity().addContentView(selectionBar, barLp);

            if (selectBackCallback == null) {
                selectBackCallback = new androidx.activity.OnBackPressedCallback(false) {
                    @Override public void handleOnBackPressed() { exitSelectMode(); }
                };
                requireActivity().getOnBackPressedDispatcher().addCallback(
                        getViewLifecycleOwner(), selectBackCallback);
            }
            selectBackCallback.setEnabled(true);
        }
        updateSelectionBar();
    }

    private void updateSelectionBar() {
        if (selectionBar == null) return;
        android.widget.TextView count = null;
        for (int i = 0; i < selectionBar.getChildCount(); i++) {
            android.view.View c = selectionBar.getChildAt(i);
            if ("count".equals(c.getTag())) { count = (android.widget.TextView) c; break; }
        }
        if (count != null) {
            count.setText("已选 " + adapter.getSelectedCount() + " 项");
        }
    }

    private void onSelectionAction(String action) {
        if ("exit".equals(action)) { exitSelectMode(); return; }
        if ("all".equals(action)) {
            // 已全选时再点 = 清空选择（保留多选模式）
            if (adapter.isAllSelected()) adapter.clearSelection();
            else adapter.selectAll();
            showSelectionBar();
            return;
        }
        final java.util.List<GalgameLibraryAdapter.Item> sel = adapter.getSelectedItems();
        if (sel.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "未选择任何游戏", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean purge = "purge".equals(action);
        int withEnv = 0;
        for (GalgameLibraryAdapter.Item it : sel) if (it.hasContainer()) withEnv++;
        String msg = purge
                ? "将删除 " + sel.size() + " 个游戏的库记录" + (withEnv > 0
                    ? "，其中 " + withEnv + " 个连同游戏文件、独立存档与运行环境容器一并删除" : "")
                  + "。\n导入源不受影响；此操作不可恢复。"
                : "仅移除 " + sel.size() + " 个游戏的库记录，不删除任何文件。";
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle(purge ? "批量彻底删除" : "批量移除记录")
                .setMessage(msg)
                .setNegativeButton(com.winlator.R.string.galgame_cancel, null)
                .setPositiveButton("删除", (d, w) -> batchDelete(new java.util.ArrayList<>(sel), purge, 0))
                .show();
    }

    /** 顺序批删：容器删除是异步的，逐个排队避免并发重建容器目录。 */
    private void batchDelete(final java.util.List<GalgameLibraryAdapter.Item> rest, final boolean purge, final int done) {
        if (rest.isEmpty()) {
            refresh();
            exitSelectMode();
            android.widget.Toast.makeText(requireContext(),
                    "已删除 " + done + " 个游戏", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        final GalgameLibraryAdapter.Item it = rest.remove(0);
        final android.content.Context ctx = requireContext();
        Runnable next = () -> {
            if (purge && it.hasContainer()) {
                com.winlator.core.FileUtils.delete(
                        new File(new com.winlator.galgame.GalgameSaveManager(it.gameId).savePath()));
            }
            GalgameLibraryIndex.remove(ctx, it.gameId);
            batchDelete(rest, purge, done + 1);
        };
        if (purge && it.hasContainer()) {
            new ContainerManager(ctx).removeContainerAsync(it.container, next);
        }
        else next.run();
    }

    private void refresh() {
        // 导入会在本 Fragment 存活期间新建容器；ContainerManager 是构造时快照，
        // 不重载的话新游戏要重启应用才出现。
        containerManager = new ContainerManager(requireContext());
        master.clear();

        java.util.Map<String, GalgameLibraryIndex.Entry> index = new java.util.HashMap<>();
        for (GalgameLibraryIndex.Entry e : GalgameLibraryIndex.load(requireContext())) {
            index.put(e.gameId, e);
        }

        List<Container> containers = containerManager.getContainers();
        if (containers != null) {
            for (Container c : containers) {
                String name = c.getName();
                if (name == null || !name.startsWith(CONTAINER_PREFIX)) continue;
                String gameId = name.substring(CONTAINER_PREFIX.length());
                GalgameLibraryIndex.Entry e = index.get(gameId);
                master.add(GalgameLibraryAdapter.fromContainer(c,
                        e != null ? e.preferredEngine : null, e != null ? e.title : null));
            }
        }
        for (GalgameLibraryIndex.Entry e : index.values()) {
            if (e.containerized) continue;
            boolean listed = false;
            for (GalgameLibraryAdapter.Item it : master) {
                if (it.gameId.equals(e.gameId)) { listed = true; break; }
            }
            if (!listed) master.add(GalgameLibraryAdapter.fromIndexEntry(requireContext(), e));
        }

        if (countView != null) countView.setText(String.format(Locale.US, "%02d", master.size()));
        populateRecent();
        applyFilter();
    }

    private void applyFilter() {
        String q = search != null && search.getText() != null
                ? search.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
        shown.clear();
        for (GalgameLibraryAdapter.Item it : master) {
            if (q.isEmpty() || (it.label != null && it.label.toLowerCase(Locale.ROOT).contains(q))) shown.add(it);
        }
        adapter.notifyDataSetChanged();
        if (allCountView != null) allCountView.setText(getString(R.string.galgame_index_count, shown.size()));

        boolean empty = shown.isEmpty();
        if (emptyView != null) emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (grid != null) grid.setVisibility(empty ? View.GONE : View.VISIBLE);
        View fab = rootView != null ? rootView.findViewById(R.id.FABImport) : null;
        if (fab != null) fab.setVisibility(master.isEmpty() ? View.GONE : View.VISIBLE);
        if (!empty && grid != null) grid.startLayoutAnimation();
    }

    private void populateRecent() {
        if (recentRow == null || recentContainer == null) return;
        recentRow.removeAllViews();
        if (master.isEmpty()) {
            recentContainer.setVisibility(View.GONE);
            return;
        }
        recentContainer.setVisibility(View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int n = Math.min(3, master.size());
        for (int i = 0; i < n; i++) {
            final GalgameLibraryAdapter.Item item = master.get(i);
            View v = inflater.inflate(R.layout.item_recent_swiss, recentRow, false);
            TextView title = v.findViewById(R.id.TVRecentTitle);
            TextView meta = v.findViewById(R.id.TVRecentMeta);
            ImageView cover = v.findViewById(R.id.IVCoverSmall);
            title.setText(item.label);
            meta.setText(item.sub);
            cover.setTag(R.id.IVCoverSmall, item);
            v.setOnClickListener(x -> openDetail(item));
            recentRow.addView(v);
            loadCover(item, cover);
        }
    }

    private void loadCover(final GalgameLibraryAdapter.Item item, final ImageView iv) {
        if (item.coverBitmap != null) {
            iv.setImageBitmap(item.coverBitmap);
            return;
        }
        coverExecutor.execute(() -> {
            try {
                if (item.cover == null) item.cover = GalgameCoverProvider.coverFor(item.gameDir, item.exe);
                if (item.cover != null && item.cover.isFile()) item.coverBitmap = decodeSampled(item.cover, 256);
            } catch (Throwable ignored) {}
            main.post(() -> {
                if (iv.getTag(R.id.IVCoverSmall) != item) return;
                if (item.coverBitmap != null) iv.setImageBitmap(item.coverBitmap);
            });
        });
    }

    private static Bitmap decodeSampled(File f, int target) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int sample = 1;
        while (o.outWidth / (sample * 2) >= target || o.outHeight / (sample * 2) >= target) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
    }
}
