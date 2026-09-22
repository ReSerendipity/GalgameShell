package org.json;
import java.util.Collection;
public class JSONArray {
    public JSONArray() {}
    public JSONArray(Collection<?> c) {}
    public int length() { return 0; }
    public String getString(int index) { return null; }
    public String optString(int index, String fallback) { return fallback; }
    public JSONObject getJSONObject(int index) { return null; }
}
