package com.winlator.galgame.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.winlator.R;
import com.winlator.container.Container;
import com.winlator.galgame.GalgameCoverProvider;
import com.winlator.galgame.GalgameLibraryIndex;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * M1 游戏库封面适配器：封面墙卡片（大封面 + 名称 + 引擎 chip）。
 *
 * <p>封面由 {@link GalgameCoverProvider} 提供（目录图扫描优先，exe 图标兜底）。
 * 解码为**后台异步 + 淡入**：避免主线程解码造成滚动卡顿，并给出现代加载观感；
 * 结果按项缓存（{@link Item#coverBitmap}）避免重复解码；用 tag 校验防止列表回收错图。
 *
 * <p><b>双数据源（2026-10-05）</b>：游戏库不再只有容器一种条目。Kirikiri / Ren'Py 这类
 * Route.B 原生游戏导入时不建容器（历史行为），由 {@link GalgameLibraryIndex} 单独记档，
 * 故 {@link Item#container} 允许为 null；工厂方法 {@link #fromContainer} / {@link #fromIndexEntry}
 * 统一负责构造，避免在 {@code GalgameHomeFragment} 与 {@code GalgameLibraryActivity}
 * 两处各写一遍、最终必然走样的字段拼装。
 */
public final class GalgameLibraryAdapter extends BaseAdapter {

    private static final String CONTAINER_PREFIX = "galgame-";

    /** 卡片交互回调：单击＝启动，长按＝操作菜单（挂在 item view 自身，见 getView）。 */
    public interface OnGameActionListener {
        void onGameClick(Item item);
        void onGameLongClick(Item item);
    }

    /** 单项数据（封面卡片：主标题 + 副标题 + 懒加载封面）。 */
    public static final class Item {
        public final Container container;       // 可为 null：无容器的原生游戏
        public final String gameId;
        public final String label;              // 卡片标题
        public final String sub;                // 副标题（引擎等元信息）
        public final File gameDir;
        public final File exe;                  // 启动 exe（供封面提图标），可为 null
        public final String engineName;         // 登记的引擎名（容器 extra / overlay 或索引）
        public final String preferredEngine;    // 用户 override，可 null
        public File cover;          // 封面文件（懒解析并缓存）
        public Bitmap coverBitmap;  // 解码后的位图缓存
        public boolean coverResolved;

        public Item(Container container, String gameId, String label, String sub, File gameDir,
                    File exe, String engineName, String preferredEngine) {
            this.container = container;
            this.gameId = gameId;
            this.label = label;
            this.sub = sub;
            this.gameDir = gameDir;
            this.exe = exe;
            this.engineName = engineName;
            this.preferredEngine = preferredEngine;
        }

        /** 是否有对应容器（决定诊断/存档等容器专属操作能否用）。 */
        public boolean hasContainer() { return container != null; }
    }

    /**
     * 由容器构造条目：容器名去掉 {@code galgame-} 前缀即为 gameId，游戏目录按既有约定推
     * （{@code .wine/drive_c/galgame/<gameId>}），登记引擎优先取容器 extra。
     */
    public static Item fromContainer(Container container, String preferredEngine) {
        String name = container != null ? container.getName() : null;
        String gameId = (name != null && name.startsWith(CONTAINER_PREFIX))
                ? name.substring(CONTAINER_PREFIX.length())
                : (name == null ? "" : name);
        File gameDir = (container == null) ? null
                : new File(container.getRootDir(), ".wine/drive_c/galgame/" + gameId);
        File exe = overlayExe(container);
        String engine = (container == null) ? null
                : container.getExtra("galgame_engine", null);
        return new Item(container, gameId, gameId, (engine == null || engine.isEmpty()) ? "?" : engine,
                gameDir, exe, engine, preferredEngine);
    }

    /** 由索引条目构造（无容器的原生游戏）。 */
    public static Item fromIndexEntry(GalgameLibraryIndex.Entry entry) {
        File dir = (entry.sourcePath == null || entry.sourcePath.isEmpty())
                ? null : new File(entry.sourcePath);
        String title = (entry.title == null || entry.title.isEmpty()) ? entry.gameId : entry.title;
        String sub = entry.engineName == null ? "?" : entry.engineName;
        return new Item(null, entry.gameId, title, sub, dir, null,
                entry.engineName, entry.preferredEngine);
    }

    /** 容器前缀名 → gameId（两者保持一致的 sanitary helper）。 */
    public static String gameIdOf(String containerName) {
        if (containerName == null) return "";
        return containerName.startsWith(CONTAINER_PREFIX)
                ? containerName.substring(CONTAINER_PREFIX.length()) : containerName;
    }

    /** 读容器 galgame_overlay.json 的 exe 路径（供封面适配器提取 exe 图标）。 */
    private static File overlayExe(Container container) {
        if (container == null) return null;
        File f = new File(container.getRootDir(), "galgame_overlay.json");
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1 << 13];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            JSONObject o = new JSONObject(new String(bos.toByteArray(), StandardCharsets.UTF_8));
            String exe = o.optString("exe", null);
            return (exe != null && !exe.isEmpty()) ? new File(exe) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private final LayoutInflater inflater;
    private final List<Item> items;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private OnGameActionListener actionListener;

    public GalgameLibraryAdapter(Context context, List<Item> items) {
        this.inflater = LayoutInflater.from(context);
        this.items = items;
    }

    public void setOnGameActionListener(OnGameActionListener l) { this.actionListener = l; }

    @Override
    public int getCount() { return items.size(); }

    @Override
    public Object getItem(int position) { return items.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView != null
                ? convertView : inflater.inflate(R.layout.item_galgame, parent, false);

        Item item = items.get(position);
        TextView tv = view.findViewById(R.id.TVLabel);
        TextView sub = view.findViewById(R.id.TVSub);
        ImageView iv = view.findViewById(R.id.IVCover);
        tv.setText(item.label);
        sub.setText(item.sub);

        // 交互必须挂在 item view 自身：卡片根为 clickable（涟漪需要），
        // 会吞掉触摸事件，GridView 的 onItemClick/onItemLongClick 不会触发。
        view.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onGameClick(item);
        });
        view.setOnLongClickListener(v -> {
            if (actionListener != null) actionListener.onGameLongClick(item);
            return true;
        });

        // 绑定标记：用于异步回调时校验视图未被回收复用
        iv.setTag(R.id.IVCover, item);

        if (item.coverBitmap != null) {
            iv.setImageBitmap(item.coverBitmap);
            iv.setAlpha(1f);
        } else {
            iv.setImageResource(R.drawable.galgame_placeholder); // GalgameShell：品牌占位插画
            iv.setAlpha(0.55f);
            loadCoverAsync(item, iv);
        }
        return view;
    }

    /** 后台解析 + 解码封面，完成后淡入。 */
    private void loadCoverAsync(final Item item, final ImageView iv) {
        if (item.coverResolved) return;
        item.coverResolved = true;

        executor.execute(() -> {
            try {
                if (item.cover == null) {
                    item.cover = GalgameCoverProvider.coverFor(item.gameDir, item.exe);
                }
                if (item.cover != null && item.cover.isFile()) {
                    item.coverBitmap = decodeSampled(item.cover.getAbsolutePath(), 512);
                }
            } catch (Throwable ignored) { /* 降级为占位图 */ }

            main.post(() -> {
                if (iv.getTag(R.id.IVCover) != item) return;   // 已被回收给其它项
                if (item.coverBitmap != null) {
                    iv.setImageBitmap(item.coverBitmap);
                    iv.setAlpha(0f);
                    iv.animate().alpha(1f).setDuration(220).start();
                }
            });
        });
    }

    /** 按目标边长采样解码，避免大图整幅载入内存。 */
    private static Bitmap decodeSampled(String path, int target) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        int sample = 1;
        while ((o.outWidth / (sample * 2) >= target) || (o.outHeight / (sample * 2) >= target)) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, o2);
    }
}
