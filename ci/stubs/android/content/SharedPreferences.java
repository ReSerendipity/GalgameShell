package android.content;
public interface SharedPreferences {
    boolean getBoolean(String key, boolean defValue);
    int getInt(String key, int defValue);
    float getFloat(String key, float defValue);
    String getString(String key, String defValue);
    Editor edit();
    interface Editor {
        Editor putBoolean(String key, boolean value);
        Editor putInt(String key, int value);
        Editor putFloat(String key, float value);
        Editor putString(String key, String value);
        Editor remove(String key);
        boolean commit();
        void apply();
    }
}
