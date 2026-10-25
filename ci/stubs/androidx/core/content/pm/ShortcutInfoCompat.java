package androidx.core.content.pm;
public abstract class ShortcutInfoCompat {
    public static class Builder {
        public Builder(android.content.Context context, String id) {}
        public Builder setShortLabel(CharSequence label) { return this; }
        public Builder setLongLabel(CharSequence label) { return this; }
        public Builder setIntent(android.content.Intent intent) { return this; }
        public Builder setIcon(androidx.core.graphics.drawable.IconCompat icon) { return this; }
        public ShortcutInfoCompat build() { return null; }
    }
}
