package android.os;
public class Bundle {
    public String getString(String key) { return null; }
    public String getString(String key, String defaultValue) { return defaultValue; }
    public int getInt(String key) { return 0; }
    public int getInt(String key, int defaultValue) { return defaultValue; }
    public boolean getBoolean(String key, boolean defaultValue) { return defaultValue; }
    public float getFloat(String key, float defaultValue) { return defaultValue; }
    public void putString(String key, String value) {}
    public void putInt(String key, int value) {}
    public void putBoolean(String key, boolean value) {}
    public void putFloat(String key, float value) {}
}
