package com.winlator.galgame.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

/**
 * M1 游戏库封面适配器：每行显示封面缩略图 + 名称。
 * 封面由 {@link GalgameCoverProvider} 提供（目录图扫描优先，exe 图标兜底），结果按项缓存避免重复解析。
 */
public final class GalgameLibraryAdapter extends BaseAdapter {

    /** 单项数据（封面卡片：主标题 + 副标题）。 */
    public static final class Item {
        public final Container container;
        public final String label;
        public final String sub;   // 副标题（引擎等元信息）
        public final File gameDir;
        public final File exe;
        public File cover;   // 懒计算并缓存

        Item(Container container, String label, String sub, File gameDir, File exe) {
            this.container = container;
            this.label = label;
            this.sub = sub;
            this.gameDir = gameDir;
            this.exe = exe;
        }
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final List<Item> items;

    public GalgameLibraryAdapter(Context context, List<Item> items) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        this.items = items;
    }

    @Override
    public int getCount() { return items.size(); }

    @Override
    public Object getItem(int position) { return items.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = inflater.inflate(R.layout.item_galgame, parent, false);
        }

        Item item = items.get(position);
        TextView tv = view.findViewById(R.id.TVLabel);
        TextView sub = view.findViewById(R.id.TVSub);
        ImageView iv = view.findViewById(R.id.IVCover);
        tv.setText(item.label);
        sub.setText(item.sub);

        // 懒加载封面（缓存）
        if (item.cover == null) {
            try {
                item.cover = GalgameCoverProvider.coverFor(item.gameDir, item.exe);
            } catch (Throwable ignored) {
                item.cover = null;
            }
        }

        if (item.cover != null && item.cover.isFile()) {
            Bitmap bmp = decodeSampled(item.cover.getAbsolutePath(), 512);
            if (bmp != null) {
                iv.setImageBitmap(bmp);
            } else {
                iv.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            iv.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        return view;
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
