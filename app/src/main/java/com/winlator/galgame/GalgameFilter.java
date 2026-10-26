package com.winlator.galgame;

import java.util.List;

/**
 * 分类过滤与标签规范化的<b>纯逻辑</b>（无 Android 依赖，供 UI 与 JVM 回归共用）。
 *
 * <p>三个维度可叠加（AND）：引擎（自动派生）/ 品类（预置单值）/ 标签（自定义多值）。
 * 任何维度为 null 表示「该维度不筛」。抽出来是为了能在 {@code tools/detect-test}
 * 里直接跑——UI 里的过滤条件错一次就是「游戏明明在库里却看不见」，比崩溃更难察觉。
 */
public final class GalgameFilter {

    /** 标签上限：卡片与 chips 都要放得下，12 字以内。 */
    public static final int MAX_TAG_LENGTH = 12;

    private GalgameFilter() {}

    /** 单个标签的规范化：去空白、换行改空格、截断到 12 字；空串返回 null。 */
    public static String normalizeTag(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;
        t = t.replace('\n', ' ').replace('\r', ' ');
        return t.length() > MAX_TAG_LENGTH ? t.substring(0, MAX_TAG_LENGTH) : t;
    }

    /**
     * 一条游戏是否命中当前过滤。
     *
     * @param engine       该游戏的引擎名（{@link EngineDetector.Engine#name()}），未知传 null 或 "?"
     * @param genre        品类 id（见 {@code ui/GalgameCategoryPicker.GENRE_IDS}），未设置传 null
     * @param tags         标签；null 视为空
     * @param engineFilter 引擎筛选值；null = 不筛
     * @param genreFilter  品类筛选值；null = 不筛
     * @param tagFilter    标签筛选值；null = 不筛
     */
    public static boolean matches(String engine, String genre, List<String> tags,
                                  String engineFilter, String genreFilter, String tagFilter) {
        if (engineFilter != null && !engineFilter.equals(engine == null ? "?" : engine)) return false;
        if (genreFilter != null && !genreFilter.equals(genre)) return false;
        if (tagFilter != null && (tags == null || !tags.contains(tagFilter))) return false;
        return true;
    }
}
