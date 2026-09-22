package android.content;
public class Intent {
    public static final String ACTION_MAIN = "android.intent.action.MAIN";
    public static final String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER";
    public static final int FLAG_ACTIVITY_NEW_TASK = 0x10000000;
    public static final int FLAG_GRANT_READ_URI_PERMISSION = 0x1;
    public Intent() {}
    public Intent(String action) {}
    public Intent(String action, android.net.Uri uri) {}
    public Intent setPackage(String packageName) { return this; }
    public Intent addFlags(int flags) { return this; }
    public Intent setAction(String action) { return this; }
    public Intent setData(android.net.Uri data) { return this; }
    public Intent putExtra(String name, String value) { return this; }
    public Intent putExtra(String name, boolean value) { return this; }
    public String getAction() { return null; }
}
