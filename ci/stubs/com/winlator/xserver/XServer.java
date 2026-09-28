package com.winlator.xserver;
import com.winlator.renderer.GLRenderer;
public class XServer {
    public static class ScreenInfo {
        public int width;
        public int height;
    }
    public final ScreenInfo screenInfo = new ScreenInfo();
    public GLRenderer getRenderer() { return new GLRenderer(); }
    public void injectPointerMove(int x, int y) {}
    public void injectPointerButtonPress(com.winlator.xserver.Pointer.Button button) {}
    public void injectPointerButtonRelease(com.winlator.xserver.Pointer.Button button) {}
}
