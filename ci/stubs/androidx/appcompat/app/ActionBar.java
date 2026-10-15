package androidx.appcompat.app;
public abstract class ActionBar {
    public abstract void setTitle(CharSequence title);
    public void setTitle(int titleId) {}
    public abstract void setDisplayHomeAsUpEnabled(boolean enabled);
}
