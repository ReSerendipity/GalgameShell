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

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * M1 游戏库封面适配器：封面墙卡片（大封面 + 名称 + 引擎 chip）。
 *
 * <p>封面由 {@link GalgameCoverProvider} 提供（目录图扫描优先，exe 图标兜底）。
 * 解码为**后台异步 + 淡入**：避免主线程解码造成滚动卡顿，并给出现代加载观感；
 * 结果按项缓存（{@link Item#coverBitmap}）避免重复解码；用 tag 校验防止列表回收错图。
 */
public final class GalgameLibraryAdapter extends BaseAdapter {

    /** 卡片交互回调：单击＝启动，长按＝操作菜单（挂在 item view 自身，见 getView）。 */
    public interface OnGameActionListener {
        void onGameClick(Container container);
        void onGameLongClick(Container container);
    }

    /** 单项数据（封面卡片：主标题 + 副标题 + 懒加载封面）。 */
    public static final class Item {
        public final Container container;
        public final String label;
        public final String sub;   // 副标题（引擎等元信息）
        public final File gameDir;
        public final File exe;
        public File cover;          // 封面文件（懒解析并缓存）
        public Bitmap coverBitmap;  // 解码后的位图缓存
        public boolean coverResolved;

        Item(Container container, String label, String sub, File gameDir, File exe) {
            this.container = container;
            this.label = label;
            this.sub = sub;
            this.gameDir = gameDir;
            this.exe = exe;
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
            if (actionListener != null) actionListener.onGameClick(item.container);
        });
        view.setOnLongClickListener(v -> {
            if (actionListener != null) actionListener.onGameLongClick(item.container);
            return true;
        });

        // 绑定标记：用于异步回调时校验视图未被回收复用
        iv.setTag(R.id.IVCover, item);

        if (item.coverBitmap != null) {
            iv.setImageBitmap(item.coverBitmap);
            iv.setAlpha(1f);
        } else {
            iv.setImageResource(android.R.drawable.ic_menu_gallery);
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
