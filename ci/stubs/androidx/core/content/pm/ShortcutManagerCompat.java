package androidx.core.content.pm;
public abstract class ShortcutManagerCompat {
    public static boolean isRequestPinShortcutSupported(android.content.Context context) { return false; }
    public static boolean requestPinShortcut(android.content.Context context, ShortcutInfoCompat info, Object callback) { return false; }
}
