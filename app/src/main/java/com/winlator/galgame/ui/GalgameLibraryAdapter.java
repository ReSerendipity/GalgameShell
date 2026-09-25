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

    /** 单行数据。 */
    public static final class Item {
        public final Container container;
        public final String label;
        public final File gameDir;
        public final File exe;
        public File cover;   // 懒计算并缓存

        Item(Container container, String label, File gameDir, File exe) {
            this.container = container;
            this.label = label;
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
        ImageView iv = view.findViewById(R.id.IVCover);
        tv.setText(item.label);

        // 懒加载封面（缓存）
        if (item.cover == null) {
            try {
                item.cover = GalgameCoverProvider.coverFor(item.gameDir, item.exe);
            } catch (Throwable ignored) {
                item.cover = null;
            }
        }

        if (item.cover != null && item.cover.isFile()) {
            Bitmap bmp = BitmapFactory.decodeFile(item.cover.getAbsolutePath());
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
}
