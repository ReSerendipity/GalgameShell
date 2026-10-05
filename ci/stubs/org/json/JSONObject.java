package org.json;
public class JSONObject {
    public JSONObject() {}
    public JSONObject(String s) throws JSONException {}
    public JSONObject put(String k, Object v) { return this; }
    public String getString(String k) { return null; }
    public int getInt(String k) { return 0; }
    public JSONObject getJSONObject(String k) { return null; }
    public JSONArray getJSONArray(String k) { return null; }
    public JSONObject optJSONObject(String k) { return null; }
    public JSONArray optJSONArray(String k) { return null; }
    public String optString(String k) { return null; }
    public String optString(String k, String fallback) { return fallback; }
    public boolean optBoolean(String k, boolean fallback) { return fallback; }
    // GalgameShell：游戏条目索引 GalgameLibraryIndex 用到 long 字段（added_at / last_played_at）。
    // 真实 org.json.JSONObject 有此重载，此前桩缺签导致 CI galgame-compile 红灯，按 ci/README.md 补桩。
    public long optLong(String k, long fallback) { return fallback; }
    public boolean has(String k) { return false; }
    public String toString() { return ""; }
    public String toString(int i) { return ""; }
}
