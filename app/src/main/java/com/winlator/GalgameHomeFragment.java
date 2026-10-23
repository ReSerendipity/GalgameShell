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
            public void onGameLongClick(GalgameLibraryAdapter.Item item) { openDetail(item); }
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

    private void refresh() {
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
                master.add(GalgameLibraryAdapter.fromContainer(c, e != null ? e.preferredEngine : null));
            }
        }
        for (GalgameLibraryIndex.Entry e : index.values()) {
            if (e.containerized) continue;
            boolean listed = false;
            for (GalgameLibraryAdapter.Item it : master) {
                if (it.gameId.equals(e.gameId)) { listed = true; break; }
            }
            if (!listed) master.add(GalgameLibraryAdapter.fromIndexEntry(e));
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
