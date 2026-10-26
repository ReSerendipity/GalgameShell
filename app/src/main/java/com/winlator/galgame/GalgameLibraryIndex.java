package com.winlator.galgame;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 游戏条目索引（{@code galgame_library.json}，落在内部 filesDir）。
 *
 * <p><b>为什么需要它</b>：游戏库一直以来<b>只有容器一个数据源</b>——列表 = 名字以
 * {@code galgame-} 开头的 {@code Container}。而 Kirikiri / Ren'Py 走的是 Route.B：
 * 导入时直接拉起内置引擎、<b>不建容器</b>（见 {@code ImportFlow.run} 与两侧
 * {@code startImport} 的 Route.B 早退分支），于是这些游戏永远进不了游戏库——
 * 没有封面、没有历史，退出后想再玩必须重新手打那串路径。
 *
 * <p>本索引即补上这块缺失的人口：把「无容器的原生游戏」记成条目；顺带承担
 * <b>per-game 运行方式 override</b>（用户手工指定的引擎）的唯一存放位置。
 *
 * <p>设计约束：
 * <ul>
 *   <li><b>零改铁律</b>：只写自有 JSON，绝不碰 {@code Container.java} / {@code Container} 表；</li>
 *   <li><b>稀疏</b>：只为需要的 key 记条目（有 override 或无可容器化的游戏），
 *       读不到条目 = 走自动裁决，不报错；</li>
 *   <li><b>容错优先</b>：文件缺失/损坏/字段异常一律降级为空或部分可用，绝不让 UI 崩。</li>
 * </ul>
 *
 * 存放位置说明：内部 filesDir 意味着卸载 App 即清除（与 {@code KrkrEngine} 写的
 * {@code .preference/recentpath.xml} 同层），不占用用户可见存储空间。
 */
public final class GalgameLibraryIndex {

    public static final String FILE_NAME = "galgame_library.json";

    private static final String TAG = "GalgameLibraryIndex";
    private static final int VERSION = 1;

    private static final Object LOCK = new Object();

    /** 供异步写法复用的单线程 IO 池（索引体量极小，一个线程足够，无需构造开销）。 */
    private static final java.util.concurrent.ExecutorService IO =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private GalgameLibraryIndex() {}

    /** 一条游戏记录（不可变）。 */
    public static final class Entry {
        /** 游戏唯一标识（也是容器名后缀 / S: 盘子目录名）。 */
        public final String gameId;
        /** 展示标题。 */
        public final String title;
        /** 导入时检测到的引擎名（{@code EngineDetector.Engine.name()}），可为 null。 */
        public final String engineName;
        /**
         * 原生游戏的源目录绝对路径（没有容器、本体仍在原地的游戏才有值）。
         * 容器游戏的本体已在容器内，此处为 null。
         */
        public final String sourcePath;
        /** 用户指定的运行方式（{@code GameEngine#id()} 或引擎枚举名）；null = 自动裁决。 */
        public final String preferredEngine;
        /** 是否已容器化（有对应 galgame- 容器）。 */
        public final boolean containerized;
        public final long addedAt;
        public final long lastPlayedAt;
        /** 预置内容品类 id（见 {@code ui/GalgameCategoryPicker.GENRE_IDS}）；null = 未设置。 */
        public final String genre;
        /** 用户自定义标签（不可变快照，去重、去空、≤12 字）。 */
        public final List<String> tags;

        Entry(String gameId, String title, String engineName, String sourcePath,
              String preferredEngine, boolean containerized, long addedAt, long lastPlayedAt) {
            this(gameId, title, engineName, sourcePath, preferredEngine, containerized,
                    addedAt, lastPlayedAt, null, Collections.<String>emptyList());
        }

        Entry(String gameId, String title, String engineName, String sourcePath,
              String preferredEngine, boolean containerized, long addedAt, long lastPlayedAt,
              String genre, List<String> tags) {
            this.gameId = gameId;
            this.title = title;
            this.engineName = engineName;
            this.sourcePath = sourcePath;
            this.preferredEngine = preferredEngine;
            this.containerized = containerized;
            this.addedAt = addedAt;
            this.lastPlayedAt = lastPlayedAt;
            this.genre = genre;
            this.tags = tags == null ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(tags));
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("game_id", gameId);
            o.put("title", title);
            if (engineName != null) o.put("engine", engineName);
            if (sourcePath != null) o.put("source_path", sourcePath);
            if (preferredEngine != null) o.put("preferred_engine", preferredEngine);
            o.put("containerized", containerized);
            o.put("added_at", addedAt);
            o.put("last_played_at", lastPlayedAt);
            if (genre != null && !genre.isEmpty()) o.put("genre", genre);
            if (!tags.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (String t : tags) arr.put(t);
                o.put("tags", arr);
            }
            return o;
        }

        static Entry fromJson(JSONObject o) {
            if (o == null) return null;
            String id = o.optString("game_id", null);
            if (id == null || id.isEmpty()) return null;
            return new Entry(
                    id,
                    o.optString("title", id),
                    o.optString("engine", null),
                    o.optString("source_path", null),
                    o.optString("preferred_engine", null),
                    o.optBoolean("containerized", false),
                    o.optLong("added_at", 0L),
                    o.optLong("last_played_at", 0L),
                    o.optString("genre", null),
                    readTags(o.optJSONArray("tags")));
        }

        Entry withPreferredEngine(String engine) {
            return new Entry(gameId, title, engineName, sourcePath, engine, containerized,
                    addedAt, lastPlayedAt, genre, tags);
        }

        /** 补登记身份（标题/引擎）：A 路由导入旧路径只写 containerized，留下 null 引擎，
         *  详情页因此显示「未识别」（2026-10-26）。已有值不覆盖。 */
        Entry withIdentity(String newTitle, String newEngine) {
            String t = (title == null || title.isEmpty() || title.equals(gameId)) && newTitle != null
                    ? newTitle : title;
            String e = engineName == null ? newEngine : engineName;
            return new Entry(gameId, t, e, sourcePath, preferredEngine, containerized,
                    addedAt, lastPlayedAt, genre, tags);
        }

        Entry withContainerized(boolean flag) {
            return new Entry(gameId, title, engineName, sourcePath, preferredEngine, flag,
                    addedAt, lastPlayedAt, genre, tags);
        }

        Entry touched(long when) {
            return new Entry(gameId, title, engineName, sourcePath, preferredEngine, containerized,
                    addedAt, when, genre, tags);
        }

        Entry withGenre(String newGenre) {
            return new Entry(gameId, title, engineName, sourcePath, preferredEngine, containerized,
                    addedAt, lastPlayedAt, newGenre, tags);
        }

        Entry withTags(List<String> newTags) {
            return new Entry(gameId, title, engineName, sourcePath, preferredEngine, containerized,
                    addedAt, lastPlayedAt, genre, newTags);
        }
    }

    // ---- 读 ----

    public static File indexFile(Context context) {
        if (context == null) return null;
        File dir = context.getFilesDir();
        return (dir == null) ? null : new File(dir, FILE_NAME);
    }

    /** 全部条目；文件缺失/损坏返回空列表。 */
    public static List<Entry> load(Context context) {
        return load(readRaw(context));
    }

    /** 按 gameId 查条目，无则 null。 */
    public static Entry find(Context context, String gameId) {
        if (gameId == null || gameId.isEmpty()) return null;
        for (Entry e : load(context)) {
            if (gameId.equals(e.gameId)) return e;
        }
        return null;
    }

    /** 该游戏是否被用户指定了运行方式。 */
    public static String preferredEngineOf(Context context, String gameId) {
        Entry e = find(context, gameId);
        return (e == null) ? null : e.preferredEngine;
    }

    // ---- 写 ----

    /**
     * 登记 / 更新一条原生游戏（无容器）。
     *
     * @param gamePath 玩家选中的游戏源目录（会被记下来供下次直接启动）
     */
    public static void putNative(Context context, String gameId, String title,
                                 EngineDetector.Engine engine, File gamePath) {
        if (context == null || gameId == null || gameId.isEmpty()) return;
        Entry fresh = new Entry(
                gameId,
                (title == null || title.isEmpty()) ? gameId : title,
                engine != null ? engine.name() : null,
                gamePath != null ? gamePath.getAbsolutePath() : null,
                null,
                false,
                System.currentTimeMillis(),
                System.currentTimeMillis());
        upsert(context, gameId, new Mutator() {
            @Override
            public Entry apply(Entry existing) {
                if (existing == null) return fresh;
                return new Entry(existing.gameId, fresh.title, fresh.engineName, fresh.sourcePath,
                        existing.preferredEngine, existing.containerized,
                        existing.addedAt, fresh.lastPlayedAt);
            }
        });
    }

    /** 标记某个游戏已有容器（导入成容器的口味再直观不过：本体归位了）。 */
    public static void markContainerized(Context context, String gameId) {
        markContainerized(context, gameId, null, null);
    }

    /**
     * 登记/更新「已容器化」条目，并补上标题与引擎名。
     * 旧签名只写 containerized，A 路由条目因此 engine=null → 详情页「未识别」、
     * 启动裁决少一层依据（2026-10-26 真机）。
     */
    public static void markContainerized(Context context, String gameId, String title, String engineName) {
        upsert(context, gameId, new Mutator() {
            @Override
            public Entry apply(Entry existing) {
                if (existing == null) {
                    return new Entry(gameId, title != null ? title : gameId, engineName, null, null, true,
                            System.currentTimeMillis(), 0L);
                }
                return existing.withIdentity(title, engineName).withContainerized(true);
            }
        });
    }

    /** 记录一次成功启动（刷新 lastPlayedAt）。 */
    public static void touchPlayed(Context context, String gameId) {
        final long now = System.currentTimeMillis();
        upsert(context, gameId, new Mutator() {
            @Override
            public Entry apply(Entry existing) {
                if (existing == null) return null;   // 没有本条就别硬造
                return existing.touched(now);
            }
        });
    }

    /** 手工指定运行方式；传 null 表示恢复自动裁决。 */
    public static void setPreferredEngine(Context context, String gameId, String engineId) {
        upsert(context, gameId, new Mutator() {
            @Override
            public Entry apply(Entry existing) {
                if (existing == null) {
                    if (engineId == null) return null;
                    return new Entry(gameId, gameId, null, null, engineId, false,
                            System.currentTimeMillis(), 0L);
                }
                return existing.withPreferredEngine(engineId);
            }
        });
    }

    /**
     * 异步版 {@link #touchPlayed}：给 UI 线程调用，避免在主线程做文件 IO。
     * 索引只用于「最近游玩」展示，晚几百毫秒落盘无副作用。
     */
    public static void touchPlayedAsync(final Context context, final String gameId) {
        IO.execute(new Runnable() {
            @Override
            public void run() {
                touchPlayed(context.getApplicationContext(), gameId);
            }
        });
    }

    /** 删除条目（游戏被移除时）。 */
    public static void remove(Context context, String gameId) {
        if (context == null || gameId == null || gameId.isEmpty()) return;
        synchronized (LOCK) {
            File f = indexFile(context);
            if (f == null) return;
            List<Entry> all = parse(readRaw(f));
            if (all == null) {
                quarantine(f);
                all = Collections.emptyList();
            }
            List<Entry> kept = new ArrayList<>();
            for (Entry e : all) {
                if (!gameId.equals(e.gameId)) kept.add(e);
            }
            writeQuietly(f, kept);
        }
    }

    // ---- 分类（引擎自动派生不落盘；品类 / 标签落盘）----

    /** 单个标签的规范化：去空白、截断到 12 字；空串返回 null。 */
    public static String normalizeTag(String raw) {
        return GalgameFilter.normalizeTag(raw);
    }

    /** 设置预置品类 id；null/空 = 清除。条目不存在时不做任何事（先 {@link #ensureEntry}）。 */
    public static void setGenre(Context context, String gameId, String genre) {
        final String g = (genre == null || genre.isEmpty()) ? null : genre;
        upsert(context, gameId, new Mutator() {
            @Override
            public Entry apply(Entry existing) {
                return existing == null ? null : existing.withGenre(g);
            }
        });
    }

    /** 给某游戏的标签做增删；批量归类时对每个 gameId 调一次。 */
    public static void applyTagChanges(Context context, String gameId,
                                       java.util.Collection<String> add,
                                       java.util.Collection<String> remove) {
        upsert(context, gameId, new Mutator() {
            @Override
            public Entry apply(Entry existing) {
                if (existing == null) return null;
                java.util.LinkedHashSet<String> next = new java.util.LinkedHashSet<>(existing.tags);
                if (remove != null) next.removeAll(remove);
                if (add != null) {
                    for (String raw : add) {
                        String t = normalizeTag(raw);
                        if (t != null) next.add(t);
                    }
                }
                return existing.withTags(new ArrayList<>(next));
            }
        });
    }

    /** 库里出现过的全部标签（升序）。 */
    public static List<String> allTags(Context context) {
        java.util.TreeSet<String> set = new java.util.TreeSet<>();
        for (Entry e : load(context)) set.addAll(e.tags);
        return new ArrayList<>(set);
    }

    /**
     * 确保条目存在（容器卡在索引里没有记录时也要能挂分类）。
     * 与 {@link #setPreferredEngine} 同口径：只为「用户显式动作」建条目，不批量补。
     */
    public static void ensureEntry(Context context, final String gameId,
                                   final String title, final String engineName) {
        upsert(context, gameId, new Mutator() {
            @Override
            public Entry apply(Entry existing) {
                if (existing != null) return existing;
                return new Entry(gameId, (title == null || title.isEmpty()) ? gameId : title,
                        engineName, null, null, false, System.currentTimeMillis(), 0L);
            }
        });
    }

    private static List<String> readTags(JSONArray arr) {
        if (arr == null) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            String t = arr.optString(i, null);
            if (t != null && !t.isEmpty()) out.add(t);
        }
        return out;
    }

    // ---- 内部 ----

    private interface Mutator {
        Entry apply(Entry existing);
    }

    private static void upsert(Context context, String gameId, Mutator mutator) {
        if (context == null || gameId == null || gameId.isEmpty()) return;
        synchronized (LOCK) {
            File f = indexFile(context);
            if (f == null) return;
            String raw = readRaw(f);
            List<Entry> all = parse(raw);
            if (all == null) {
                quarantine(f);
                all = Collections.emptyList();
            }
            Entry existing = null;
            for (Entry e : all) {
                if (gameId.equals(e.gameId)) { existing = e; break; }
            }
            Entry next = mutator.apply(existing);
            if (next == null) return;

            List<Entry> out = new ArrayList<>();
            boolean replaced = false;
            for (Entry e : all) {
                if (gameId.equals(e.gameId)) { out.add(next); replaced = true; }
                else out.add(e);
            }
            if (!replaced) out.add(next);
            writeQuietly(f, out);
        }
    }

    private static String readRaw(Context context) {
        File f = indexFile(context);
        return (f == null) ? null : readRaw(f);
    }

    private static String readRaw(File f) {
        if (f == null || !f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1 << 13];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "读取索引失败，按空索引处理：" + f, e);
            return null;
        }
    }

    /** 解析索引文本；空返回空列表，<b>损坏返回 null</b>（调用方据此隔离旧文件）。 */
    private static List<Entry> parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray arr = root.optJSONArray("games");
            if (arr == null) return Collections.emptyList();
            List<Entry> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                Entry e = Entry.fromJson(arr.optJSONObject(i));
                if (e != null) out.add(e);
            }
            return out;
        } catch (Exception e) {
            Log.w(TAG, "索引解析失败，准备隔离旧文件", e);
            return null;
        }
    }

    private static List<Entry> load(String raw) {
        List<Entry> parsed = parse(raw);
        return parsed == null ? Collections.<Entry>emptyList() : parsed;
    }

    /**
     * 下标损坏时把文件改名留档，再按空索引继续写。
     * 不隔离的话，下一次 upsert 会用单条记录覆盖整个文件，其余条目永久丢失。
     */
    private static void quarantine(File f) {
        if (f == null) return;
        File bak = new File(f.getAbsolutePath() + ".corrupt-" + System.currentTimeMillis());
        if (f.renameTo(bak)) Log.w(TAG, "索引已损坏，旧文件留档: " + bak);
        else Log.w(TAG, "索引已损坏且无法留档: " + f);
    }

    private static void writeQuietly(File f, List<Entry> entries) {
        if (f == null) return;
        try {
            JSONObject root = new JSONObject();
            root.put("version", VERSION);
            JSONArray arr = new JSONArray();
            for (Entry e : entries) arr.put(e.toJson());
            root.put("games", arr);

            File parent = f.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
            byte[] bytes = root.toString(2).getBytes(StandardCharsets.UTF_8);
            // 原子写：先落 .tmp 再 rename 覆盖。写一半被杀只会留下 .tmp，
            // 旧索引保持完整（此前直接覆写，写到一半就是全库索引损坏）。
            File tmp = new File(f.getAbsolutePath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp, false)) {
                out.write(bytes);
                out.getFD().sync();
            }
            if (tmp.renameTo(f)) return;
            // 少数文件系统 rename 不覆盖既有文件：删旧再改名（此时 tmp 已完整落盘）
            if (f.exists() && !f.delete()) {
                Log.w(TAG, "索引替换失败（旧文件删不掉）：" + f);
                return;
            }
            if (!tmp.renameTo(f)) Log.w(TAG, "索引替换失败：" + f);
        } catch (Exception e) {
            // 索引写失败不该阻断启动流程
            Log.w(TAG, "写入索引失败：" + f, e);
        }
    }
}
