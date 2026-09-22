package org.json;
public class JSONObject {
    public JSONObject() {}
    public JSONObject(String s) throws JSONException {}
    public JSONObject put(String k, Object v) { return this; }
    public String getString(String k) { return null; }
    public int getInt(String k) { return 0; }
    public JSONObject getJSONObject(String k) { return null; }
    public JSONArray getJSONArray(String k) { return null; }
    public boolean has(String k) { return false; }
    public String toString() { return ""; }
    public String toString(int i) { return ""; }
}
