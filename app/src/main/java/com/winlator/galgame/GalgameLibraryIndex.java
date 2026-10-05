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

        Entry(String gameId, String title, String engineName, String sourcePath,
              String preferredEngine, boolean containerized, long addedAt, long lastPlayedAt) {
            this.gameId = gameId;
            this.title = title;
            this.engineName = engineName;
            this.sourcePath = sourcePath;
            this.preferredEngine = preferredEngine;
            this.containerized = containerized;
            this.addedAt = addedAt;
            this.lastPlayedAt = lastPlayedAt;
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
                    o.optLong("last_played_at", 0L));
        }

        Entry withPreferredEngine(String engine) {
            return new Entry(gameId, title, engineName, sourcePath, engine, containerized,
                    addedAt, lastPlayedAt);
        }

        Entry withContainerized(boolean flag) {
            return new Entry(gameId, title, engineName, sourcePath, preferredEngine, flag,
                    addedAt, lastPlayedAt);
        }

        Entry touched(long when) {
            return new Entry(gameId, title, engineName, sourcePath, preferredEngine, containerized,
                    addedAt, when);
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
        upsert(context, gameId, new Mutator() {
            @Override
            public Entry apply(Entry existing) {
                if (existing == null) {
                    return new Entry(gameId, gameId, null, null, null, true,
                            System.currentTimeMillis(), 0L);
                }
                return existing.withContainerized(true);
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
            List<Entry> all = load(readRaw(f));
            List<Entry> kept = new ArrayList<>();
            for (Entry e : all) {
                if (!gameId.equals(e.gameId)) kept.add(e);
            }
            writeQuietly(f, kept);
        }
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
            List<Entry> all = load(readRaw(f));
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

    /** 解析索引文本；空/损坏返回空列表。 */
    private static List<Entry> load(String raw) {
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
            Log.w(TAG, "索引解析失败，按空索引处理", e);
            return Collections.emptyList();
        }
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
            try (FileOutputStream out = new FileOutputStream(f, false)) {
                out.write(bytes);
            }
        } catch (Exception e) {
            // 索引写失败不该阻断启动流程
            Log.w(TAG, "写入索引失败：" + f, e);
        }
    }
}
