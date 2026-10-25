package com.winlator.xserver;

public class WindowManager {
    public final Window rootWindow = new Window();

    public interface OnWindowModificationListener {
        default void onMapWindow(Window window) {}
        default void onUnmapWindow(Window window) {}
    }

    public void addOnWindowModificationListener(OnWindowModificationListener listener) {}
}
