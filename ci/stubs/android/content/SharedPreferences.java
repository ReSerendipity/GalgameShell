package android.content;
public interface SharedPreferences {
    boolean getBoolean(String key, boolean defValue);
    int getInt(String key, int defValue);
    String getString(String key, String defValue);
    Editor edit();
    interface Editor {
        Editor putBoolean(String key, boolean value);
        Editor putInt(String key, int value);
        Editor putString(String key, String value);
        boolean commit();
        void apply();
    }
}
