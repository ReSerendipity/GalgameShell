package com.winlator.galgame.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.EditText;

import com.winlator.R;
import com.winlator.galgame.GalgameLibraryIndex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 分类编辑对话框（品类单选 / 标签多选 + 新建）。
 *
 * <p>分类口径（2026-10-26 与用户确认）：引擎由卡片自动派生、不参与编辑；这里只管两样
 * <b>用户可写</b>的维度——预置内容品类（单值，id 落盘、显示名走资源，改名不影响数据）
 * 与自定义标签（多值，去重、≤12 字）。库页顶部 chips 的三类过滤各自对应一个维度。
 *
 * <p>批量语义：对话框拿到的是一组 gameId。品类 = 整组覆盖成所选值；标签 = 勾选状态相对
 * 「整组共有」的差量（勾上=给整组加，取消=从整组删）。这样对新导入的一批游戏一次挂完，
 * 比逐个改卡片快得多。
 */
public final class GalgameCategoryPicker {

    /** 预置内容品类的落盘 id（稳定；改显示名不动这里）。 */
    public static final String[] GENRE_IDS = {"vn", "adv", "other"};

    /** 应用完成回调（调用方据此刷新列表 / 退出多选）。 */
    public interface OnApplied {
        void onApplied();
    }

    private GalgameCategoryPicker() {}

    public static String genreLabel(Context c, String id) {
        if (id == null) return null;
        if ("vn".equals(id)) return c.getString(R.string.galgame_genre_vn);
        if ("adv".equals(id)) return c.getString(R.string.galgame_genre_adv);
        if ("other".equals(id)) return c.getString(R.string.galgame_genre_other);
        return null;
    }

    // ---- 品类 ----

    public static void pickGenre(final Context context, final List<String> gameIds,
                                 final OnApplied callback) {
        if (context == null || gameIds == null || gameIds.isEmpty()) return;
        final String common = commonGenre(context, gameIds);
        CharSequence[] items = new CharSequence[GENRE_IDS.length + 1];
        items[0] = context.getString(R.string.galgame_genre_none);
        int checked = 0;
        for (int i = 0; i < GENRE_IDS.length; i++) {
            items[i + 1] = genreLabel(context, GENRE_IDS[i]);
            if (GENRE_IDS[i].equals(common)) checked = i + 1;
        }
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.galgame_category_genre_title, gameIds.size()))
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    String id = which == 0 ? null : GENRE_IDS[which - 1];
                    for (String gameId : gameIds) {
                        GalgameLibraryIndex.setGenre(context, gameId, id);
                    }
                    dialog.dismiss();
                    if (callback != null) callback.onApplied();
                })
                .setNegativeButton(R.string.galgame_cancel, null)
                .show();
    }

    /** 整组共有的品类；不一致或为空返回 null（对话框里表现为「未设置」）。 */
    private static String commonGenre(Context context, List<String> gameIds) {
        String common = null;
        boolean first = true;
        for (String gameId : gameIds) {
            GalgameLibraryIndex.Entry e = GalgameLibraryIndex.find(context, gameId);
            String g = e != null ? e.genre : null;
            if (first) {
                common = g;
                first = false;
            } else if (g == null ? common != null : !g.equals(common)) {
                return null;
            }
        }
        return common;
    }

    // ---- 标签 ----

    public static void editTags(final Context context, final List<String> gameIds,
                                final OnApplied callback) {
        if (context == null || gameIds == null || gameIds.isEmpty()) return;
        final List<String> all = GalgameLibraryIndex.allTags(context);
        final Set<String> common = commonTags(context, gameIds);

        if (all.isEmpty()) {
            new AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.galgame_category_tag_title, gameIds.size()))
                    .setMessage(R.string.galgame_tags_empty)
                    .setNeutralButton(R.string.galgame_tag_new,
                            (dialog, which) -> createTag(context, gameIds, callback))
                    .setNegativeButton(R.string.galgame_cancel, null)
                    .show();
            return;
        }

        final CharSequence[] items = all.toArray(new CharSequence[0]);
        final boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) checked[i] = common.contains(all.get(i));

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.galgame_category_tag_title, gameIds.size()))
                .setMultiChoiceItems(items, checked.clone(),
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNeutralButton(R.string.galgame_tag_new,
                        (dialog, which) -> createTag(context, gameIds, callback))
                .setNegativeButton(R.string.galgame_cancel, null)
                .setPositiveButton(R.string.galgame_apply, (dialog, which) -> {
                    List<String> add = new ArrayList<>();
                    List<String> remove = new ArrayList<>();
                    for (int i = 0; i < all.size(); i++) {
                        String tag = all.get(i);
                        if (checked[i] && !common.contains(tag)) add.add(tag);
                        else if (!checked[i] && common.contains(tag)) remove.add(tag);
                    }
                    if (add.isEmpty() && remove.isEmpty()) return;
                    for (String gameId : gameIds) {
                        GalgameLibraryIndex.applyTagChanges(context, gameId, add, remove);
                    }
                    if (callback != null) callback.onApplied();
                })
                .show();
    }

    /** 整组共有的标签（批量时用作勾选初值）。 */
    private static Set<String> commonTags(Context context, List<String> gameIds) {
        Set<String> common = null;
        for (String gameId : gameIds) {
            GalgameLibraryIndex.Entry e = GalgameLibraryIndex.find(context, gameId);
            Set<String> tags = new HashSet<>(e != null ? e.tags : Collections.<String>emptyList());
            if (common == null) common = tags;
            else common.retainAll(tags);
        }
        return common != null ? common : Collections.<String>emptySet();
    }

    private static void createTag(final Context context, final List<String> gameIds,
                                  final OnApplied callback) {
        final EditText input = new EditText(context);
        input.setHint(R.string.galgame_tag_hint);
        new AlertDialog.Builder(context)
                .setTitle(R.string.galgame_tag_new)
                .setView(input)
                .setNegativeButton(R.string.galgame_cancel, null)
                .setPositiveButton(R.string.galgame_ok, (dialog, which) -> {
                    String tag = GalgameLibraryIndex.normalizeTag(
                            input.getText() != null ? input.getText().toString() : null);
                    if (tag == null) return;
                    List<String> one = new ArrayList<>();
                    one.add(tag);
                    for (String gameId : gameIds) {
                        GalgameLibraryIndex.applyTagChanges(context, gameId, one, null);
                    }
                    if (callback != null) callback.onApplied();
                    // 回到标签列表，让新标签以「已勾选」出现，方便继续加减
                    editTags(context, gameIds, callback);
                })
                .show();
    }
}
