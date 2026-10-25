package androidx.activity;
public abstract class OnBackPressedCallback {
    public OnBackPressedCallback(boolean enabled) {}
    public void setEnabled(boolean enabled) {}
    public boolean isEnabled() { return false; }
    public abstract void handleOnBackPressed();
}
