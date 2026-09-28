package android.content;
public final class ComponentName {
    public ComponentName(String pkg, String cls) {}
    public ComponentName(Context pkg, Class<?> cls) {}
    public String getPackageName() { return ""; }
    public String getClassName() { return ""; }
}
